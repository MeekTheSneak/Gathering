package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.GameState;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.network.AwayVotePayload;
import dev.gathering.network.Sending;
import dev.gathering.network.TableAwayPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Away from the board: a player who gets up in the middle of a game without conceding keeps their seat for
 * eight minutes, and nobody else can sit there.
 * <p>The owner's rule, so that somebody who stands up for a moment - or is knocked out of their chair - does
 * not come back to find a player who was waiting beside the table sitting at their cards. The seat is free
 * again when they give it up: choosing Leave table, or conceding; when the eight minutes run out; or when
 * every other player at the table votes to free it, which only a game of four or more allows - at a table of
 * two or three, one vote would decide it.
 * <p>A seat given up that way may be taken with the board on it, by whoever sits down next. A seat nobody gave
 * up still waits for its owner, as it always has (see {@link TableSessions#boardBelongsToAnother}).
 * <p>Leaving the server counts as getting up: the seat is kept for eight minutes, not for ever. Saved in the world,
 * so a restart keeps each seat for what was left of its time, and remembers which seats were given up.
 */
public final class AwayFromBoard {

    /** How long a seat is kept. */
    public static final int MINUTES = 8;

    /** The fewest players at a game in which the others may vote to free a seat. */
    public static final int FEWEST_PLAYERS_TO_VOTE = 4;

    private static final long TICKS_PER_SECOND = 20;
    private static final long KEPT_FOR_TICKS = MINUTES * 60 * TICKS_PER_SECOND;

    /** The server's tick, which a test may move on. */
    public static java.util.function.ToLongFunction<MinecraftServer> clock = MinecraftServer::getTickCount;

    private record Key(String dimension, BlockPos table, UUID player) {
    }

    private static final class Away {
        final int seat;
        final String name;
        final long until;
        final Set<UUID> votes = new LinkedHashSet<>();

        Away(int seat, String name, long until) {
            this.seat = seat;
            this.name = name;
            this.until = until;
        }
    }

    /** A seat given up, which the next player may sit down at with its board. */
    private record GivenUp(String dimension, BlockPos table, int seat, UUID owner) {
    }

    private static final Map<Key, Away> AWAY = new LinkedHashMap<>();
    private static final Set<GivenUp> GIVEN_UP = new LinkedHashSet<>();
    /** Whether what was saved has been read back since the server started. */
    private static boolean loaded;
    private static final int MOST_REMEMBERED = 1024;

    private AwayFromBoard() {
    }

    /**
     * Whether this player getting up now keeps their seat: they hold one at a game on here, have not conceded,
     * and have cards on it - or it is between games of a match and the table is holding their deck.
     */
    public static boolean keepsTheSeat(ServerLevel level, BlockPos table, ServerPlayer player) {
        SeatId seat = TableSessions.seatIdOf(level, table, player.getUUID()).orElse(null);
        if (seat == null || TableSessions.isPractice(level, table)) {
            return false;
        }
        GameSession session = TableSessions.sessionAt(level, table).orElse(null);
        if (session != null) {
            GameState state = session.state();
            if (state.ended() || !state.hasSeat(seat) || state.seatState(seat).conceded()) {
                return false;
            }
            for (Zone zone : Zone.values()) {
                if (!state.contents(seat, zone).isEmpty()) {
                    return true;
                }
            }
            return TableBlock.hasADeckDown(level, table, seat);
        }
        return TableMatch.isBetweenGames(level, table) && tableAt(level, table)
                .map(entity -> entity.heldDecks().containsKey(seat)).orElse(false);
    }

    /** This player got up without giving up their seat: it is kept for them, and the table is told. */
    public static void start(ServerLevel level, BlockPos table, ServerPlayer player) {
        SeatId seat = TableSessions.seatIdOf(level, table, player.getUUID()).orElse(null);
        if (seat == null) {
            return;
        }
        String name = player.getGameProfile().getName();
        if (away().containsKey(key(level, table, player.getUUID()))) {
            // Already away - stood up, then left the server - and the clock already running.
            return;
        }
        away().put(key(level, table, player.getUUID()), new Away(seat.index(), name, now(level) + KEPT_FOR_TICKS));
        save(level.getServer());
        if (player.hasDisconnected()) {
            tellOthers(level, table, player.getUUID(), Component.translatable("message.gathering.afb.left_server", name, MINUTES));
        } else {
            player.sendSystemMessage(Component.translatable("message.gathering.afb.you", MINUTES));
            tellOthers(level, table, player.getUUID(), Component.translatable("message.gathering.afb.table", name, MINUTES));
        }
        refresh(level, table);
    }

    /** The player sat back down at their seat: no longer away. */
    public static void back(ServerLevel level, BlockPos table, ServerPlayer player) {
        Away away = away().remove(key(level, table, player.getUUID()));
        if (away != null) {
            save(level.getServer());
            tellOthers(level, table, player.getUUID(), Component.translatable("message.gathering.afb.back", away.name));
            refresh(level, table);
        }
    }

    /** The player gave their seat up some way of their own - Leave table - so nothing is kept for them. */
    public static void leftTheSeat(Level level, BlockPos table, UUID player, int seat) {
        if (level instanceof ServerLevel server) {
            away().remove(key(server, table, player));
            if (seat >= 0) {
                remember(new GivenUp(dimension(server), table.immutable(), seat, player));
            }
            save(server.getServer());
        }
    }

    /** The player conceded: a seat kept for them while away is given up. */
    public static void conceded(ServerLevel level, BlockPos table, UUID player) {
        Key key = key(level, table, player);
        Away away = away().get(key);
        if (away != null) {
            release(level, key, away, null);
        }
    }

    /** Forgets a player's seat being kept, without giving it up - they no longer hold it anyway. */
    public static void forget(Level level, BlockPos table, UUID player) {
        if (level instanceof ServerLevel server && away().remove(key(server, table, player)) != null) {
            save(server.getServer());
        }
    }

    /** Whether this player is away from the board at this table. */
    public static boolean isAway(Level level, BlockPos table, UUID player) {
        return level instanceof ServerLevel server && away().containsKey(key(server, table, player));
    }

    /** What to tell somebody sitting down at a seat kept for a player away from it, if it is one. */
    public static Optional<Component> keptSeat(Level level, BlockPos table, int seat) {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        long now = now(server);
        return entriesAt(server, table).stream()
                .filter(entry -> entry.getValue().seat == seat)
                .findFirst()
                .map(entry -> Component.translatable("message.gathering.afb.seat_kept", entry.getValue().name,
                        clock(Math.max(0, entry.getValue().until - now) / TICKS_PER_SECOND)));
    }

    /** Whether the seat at this index, whose board is this owner's, was given up and may be taken with it. */
    public static boolean wasGivenUp(BlockGetter level, BlockPos table, int seat, UUID owner) {
        return level instanceof ServerLevel server
                && givenUp().contains(new GivenUp(dimension(server), table, seat, owner));
    }

    /** A vote to free a seat kept for a player away from the board. */
    public static void vote(ServerPlayer voter, AwayVotePayload payload) {
        BlockPos table = TableReach.originFor(voter, payload.table()).orElse(null);
        if (table == null) {
            return;
        }
        ServerLevel level = voter.serverLevel();
        Map.Entry<Key, Away> kept = entriesAt(level, table).stream()
                .filter(entry -> entry.getValue().seat == payload.seat()).findFirst().orElse(null);
        if (kept == null || kept.getKey().player().equals(voter.getUUID())) {
            return;
        }
        if (!voters(level, table, kept.getKey().player()).contains(voter.getUUID())) {
            return;
        }
        if (players(level, table) < FEWEST_PLAYERS_TO_VOTE) {
            voter.sendSystemMessage(Component.translatable("message.gathering.afb.too_few", FEWEST_PLAYERS_TO_VOTE));
            return;
        }
        Away away = kept.getValue();
        if (!away.votes.add(voter.getUUID())) {
            return;
        }
        save(level.getServer());
        Set<UUID> voters = voters(level, table, kept.getKey().player());
        long counted = away.votes.stream().filter(voters::contains).count();
        TableJoining.tellTheTable(level, table, Component.translatable("message.gathering.afb.vote",
                voter.getGameProfile().getName(), away.name, counted, voters.size()));
        if (counted >= voters.size()) {
            release(level, kept.getKey(), away, Component.translatable("message.gathering.afb.voted_free", away.name));
        } else {
            refresh(level, table);
        }
    }

    /** Once a second: seats kept past their time are given up, and seats nobody holds any more are forgotten. */
    public static void tick(MinecraftServer server) {
        if (away().isEmpty() || clock.applyAsLong(server) % TICKS_PER_SECOND != 0) {
            return;
        }
        // Written every so often while anybody is away, so what a restart reads back has lost at most that long.
        if (clock.applyAsLong(server) - lastSavedAt >= SAVE_EVERY_TICKS) {
            save(server);
        }
        List<Map.Entry<Key, Away>> due = new ArrayList<>();
        for (Iterator<Map.Entry<Key, Away>> it = away().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Away> entry = it.next();
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getKey().dimension())));
            if (level == null) {
                continue;
            }
            BlockPos table = entry.getKey().table();
            boolean stillHeld = TableSeats.seatOf(level, table, entry.getKey().player()).isPresent()
                    && (TableSessions.hasSession(level, table) || TableMatch.isBetweenGames(level, table));
            if (!stillHeld) {
                it.remove();
                lastSavedAt = Long.MIN_VALUE / 2;
            } else if (clock.applyAsLong(server) >= entry.getValue().until) {
                due.add(entry);
            }
        }
        for (Map.Entry<Key, Away> entry : due) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getKey().dimension())));
            if (level != null && away().get(entry.getKey()) == entry.getValue()) {
                release(level, entry.getKey(), entry.getValue(),
                        Component.translatable("message.gathering.afb.expired", entry.getValue().name));
            }
        }
    }

    /** The seats kept at this table, as this player is shown them beside the board. */
    public static TableAwayPayload viewFor(ServerLevel level, BlockPos table, UUID viewer) {
        List<TableAwayPayload.Away> seats = new ArrayList<>();
        long now = now(level);
        boolean voting = players(level, table) >= FEWEST_PLAYERS_TO_VOTE;
        for (Map.Entry<Key, Away> entry : entriesAt(level, table)) {
            Away away = entry.getValue();
            Set<UUID> voters = voters(level, table, entry.getKey().player());
            long counted = away.votes.stream().filter(voters::contains).count();
            boolean mayVote = voting && voters.contains(viewer) && !away.votes.contains(viewer);
            seats.add(new TableAwayPayload.Away(away.seat, (int) (Math.max(0, away.until - now) / TICKS_PER_SECOND),
                    (int) counted, voting ? voters.size() : 0, mayVote));
            if (seats.size() >= TableAwayPayload.MOST) {
                break;
            }
        }
        return new TableAwayPayload(table, seats);
    }

    /** Forgets everybody away, for a server that is stopping. */
    public static void clear() {
        AWAY.clear();
        GIVEN_UP.clear();
        loaded = false;
        lastSavedAt = Long.MIN_VALUE / 2;
    }

    /**
     * Gives up a kept seat: out of the seat, and the seat free to be taken with its board and its deck, which the
     * table goes on holding for its owner until the game is over.
     */
    private static void release(ServerLevel level, Key key, Away away, Component said) {
        away().remove(key);
        BlockPos table = key.table();
        remember(new GivenUp(key.dimension(), table, away.seat, key.player()));
        save(level.getServer());
        // The deck stays on the table: whoever takes the seat plays it, and it goes back to its owner when the
        // game is over.
        TableBlock.giveUpSeat(level, table, key.player(), true);
        if (said != null) {
            TableJoining.tellTheTable(level, table, said);
            ServerPlayer gone = level.getServer().getPlayerList().getPlayer(key.player());
            if (gone != null && !TableJoining.atTheTable(level, table).contains(gone)) {
                gone.sendSystemMessage(said);
            }
        }
        refresh(level, table);
    }

    /** Everybody who could vote to free this player's seat: seated at the table, online, and not away. */
    private static Set<UUID> voters(ServerLevel level, BlockPos table, UUID away) {
        Set<UUID> voters = new LinkedHashSet<>();
        for (TableBroadcast.Seated seated : TableBroadcast.seatedAt(level, table)) {
            UUID id = seated.player().getUUID();
            if (!id.equals(away) && !away().containsKey(key(level, table, id))) {
                voters.add(id);
            }
        }
        return voters;
    }

    /** How many players the game here has: seats with a board, or with a deck held between games. */
    private static int players(ServerLevel level, BlockPos table) {
        GameSession session = TableSessions.sessionAt(level, table).orElse(null);
        if (session != null) {
            return (int) session.state().seats().stream()
                    .filter(seat -> session.state().seatState(seat).whoseBoard().isPresent()).count();
        }
        return tableAt(level, table).map(entity -> entity.heldDecks().size()).orElse(0);
    }

    private static List<Map.Entry<Key, Away>> entriesAt(ServerLevel level, BlockPos table) {
        String dimension = dimension(level);
        return away().entrySet().stream()
                .filter(entry -> entry.getKey().dimension().equals(dimension) && entry.getKey().table().equals(table))
                .toList();
    }

    /** Sends everybody at the table the board again, with who is away beside it; watchers the seats kept. */
    private static void refresh(ServerLevel level, BlockPos table) {
        if (TableSessions.hasSession(level, table)) {
            TableBroadcast.sendToTable(level, table);
        } else {
            for (TableBroadcast.Seated seated : TableBroadcast.seatedAt(level, table)) {
                Sending.to(seated.player(), viewFor(level, table, seated.player().getUUID()));
            }
        }
        for (ServerPlayer watching : TableJoining.watchers(level, table)) {
            Sending.to(watching, viewFor(level, table, watching.getUUID()));
        }
    }

    private static void tellOthers(ServerLevel level, BlockPos table, UUID except, Component line) {
        for (ServerPlayer player : TableJoining.atTheTable(level, table)) {
            if (!player.getUUID().equals(except)) {
                player.sendSystemMessage(line);
            }
        }
    }

    /**
     * Forgets every seat given up at this table, because the game they were given up in is over.
     * <p>A give-up says "somebody may take this board", and that is only ever true of the game it was
     * made in. The records outlived their games and were saved to disk, so a seat given up on Monday
     * still said yes on Friday - and what it says yes to is another player being seated at somebody
     * else's board and sent their hand. The one rule this mod will not bend, reached through a record
     * whose lifetime nobody had defined.
     */
    public static void forgetGivenUpAt(ServerLevel level, BlockPos table) {
        BlockPos origin = table.immutable();
        String dimension = dimension(level);
        if (givenUp().removeIf(seat -> seat.seat() >= 0
                && seat.dimension().equals(dimension) && seat.table().equals(origin))) {
            save(level.getServer());
        }
    }

    /**
     * Remembers a seat given up, forgetting the oldest if there are too many.
     * <p>The oldest, not all of them. Emptying the set was the answer here, and because nothing ever
     * retired a record the cap was reached by ordinary play - at which point every live seat whose
     * player had walked away became untakeable for the rest of its game, with nothing said. This is
     * the argument {@code Owed.MOST_OWED} already makes about its own ceiling.
     */
    private static void remember(GivenUp seat) {
        java.util.Iterator<GivenUp> oldest = givenUp().iterator();
        while (givenUp().size() >= MOST_REMEMBERED && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        givenUp().add(seat);
    }

    private static Optional<TableBlockEntity> tableAt(ServerLevel level, BlockPos table) {
        return TableSessions.anchorOf(level, table).flatMap(anchor -> TableBlock.entityAt(level, anchor));
    }

    private static Key key(ServerLevel level, BlockPos table, UUID player) {
        return new Key(dimension(level), table.immutable(), player);
    }

    private static String dimension(Level level) {
        return level.dimension().location().toString();
    }

    private static long now(ServerLevel level) {
        return clock.applyAsLong(level.getServer());
    }

    // ------------------------------------------------------------------ kept through a restart

    private static final String FOLDER = "tables";
    private static final String FILE = "away_from_board.dat";
    private static final long SAVE_EVERY_TICKS = 20 * TICKS_PER_SECOND;
    private static long lastSavedAt = Long.MIN_VALUE / 2;

    private static Map<Key, Away> away() {
        load();
        return AWAY;
    }

    private static Set<GivenUp> givenUp() {
        load();
        return GIVEN_UP;
    }

    /**
     * Reads back what was kept, once a server run, the first time anything asks. Time left rather than a moment:
     * the server's tick count starts again at every launch, so a seat is kept for what was left of its eight
     * minutes when it was last written, counted from now.
     */
    private static void load() {
        if (loaded) {
            return;
        }
        MinecraftServer server = ServerRun.server().orElse(null);
        java.nio.file.Path file = ServerRun.inSave(FOLDER).map(folder -> folder.resolve(FILE)).orElse(null);
        if (server == null || file == null) {
            return;
        }
        loaded = true;
        if (!java.nio.file.Files.isRegularFile(file)) {
            return;
        }
        try {
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(file,
                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            long now = clock.applyAsLong(server);
            net.minecraft.nbt.ListTag seats = tag.getList("away", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int index = 0; index < seats.size(); index++) {
                net.minecraft.nbt.CompoundTag entry = seats.getCompound(index);
                Away kept = new Away(entry.getInt("seat"), entry.getString("name"),
                        now + Math.max(0, Math.min(KEPT_FOR_TICKS, entry.getLong("left"))));
                net.minecraft.nbt.ListTag votes = entry.getList("votes", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
                for (int vote = 0; vote < votes.size(); vote++) {
                    kept.votes.add(net.minecraft.core.UUIDUtil.uuidFromIntArray(votes.getIntArray(vote)));
                }
                AWAY.put(new Key(entry.getString("dimension"), BlockPos.of(entry.getLong("table")),
                        entry.getUUID("player")), kept);
            }
            net.minecraft.nbt.ListTag given = tag.getList("given_up", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int index = 0; index < given.size(); index++) {
                net.minecraft.nbt.CompoundTag entry = given.getCompound(index);
                GIVEN_UP.add(new GivenUp(entry.getString("dimension"), BlockPos.of(entry.getLong("table")),
                        entry.getInt("seat"), entry.getUUID("owner")));
            }
        } catch (java.io.IOException | RuntimeException unreadable) {
            LOGGER.error("The seats kept for players away from the board will not load: {}", unreadable.toString());
        }
    }

    /** Writes down who is away and which seats were given up, beside the old file and moved into place. */
    private static void save(MinecraftServer server) {
        java.nio.file.Path folder = ServerRun.inSave(FOLDER).orElse(null);
        if (server == null || folder == null || !loaded) {
            return;
        }
        long now = clock.applyAsLong(server);
        lastSavedAt = now;
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag seats = new net.minecraft.nbt.ListTag();
        AWAY.forEach((key, kept) -> {
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putString("dimension", key.dimension());
            entry.putLong("table", key.table().asLong());
            entry.putUUID("player", key.player());
            entry.putInt("seat", kept.seat);
            entry.putString("name", kept.name);
            entry.putLong("left", Math.max(0, kept.until - now));
            net.minecraft.nbt.ListTag votes = new net.minecraft.nbt.ListTag();
            kept.votes.forEach(vote -> votes.add(net.minecraft.nbt.NbtUtils.createUUID(vote)));
            entry.put("votes", votes);
            seats.add(entry);
        });
        tag.put("away", seats);
        net.minecraft.nbt.ListTag given = new net.minecraft.nbt.ListTag();
        GIVEN_UP.forEach(seat -> {
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putString("dimension", seat.dimension());
            entry.putLong("table", seat.table().asLong());
            entry.putInt("seat", seat.seat());
            entry.putUUID("owner", seat.owner());
            given.add(entry);
        });
        tag.put("given_up", given);
        try {
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Path writing = folder.resolve(FILE + ".tmp");
            net.minecraft.nbt.NbtIo.writeCompressed(tag, writing);
            java.nio.file.Files.move(writing, folder.resolve(FILE), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException | RuntimeException failed) {
            LOGGER.error("The seats kept for players away from the board could not be saved: {}", failed.toString());
        }
    }

    /** Writes what is kept now, for a test that restarts the server's memory of it. */
    public static void saveForTesting(MinecraftServer server) {
        save(server);
    }

    /** Forgets what is in memory without touching what is saved, as a restart does. */
    public static void forgetForTesting() {
        clear();
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Gathering");

    /** Seconds as minutes and seconds: 7:05. */
    static String clock(long seconds) {
        return (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }
}
