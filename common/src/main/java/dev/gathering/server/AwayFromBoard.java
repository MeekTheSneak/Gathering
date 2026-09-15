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
 * <p>Kept in memory, not saved: after a restart a player who was away keeps their seat as a player who left
 * the server does.
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
        AWAY.put(key(level, table, player.getUUID()), new Away(seat.index(), name, now(level) + KEPT_FOR_TICKS));
        player.sendSystemMessage(Component.translatable("message.gathering.afb.you", MINUTES));
        tellOthers(level, table, player.getUUID(), Component.translatable("message.gathering.afb.table", name, MINUTES));
        refresh(level, table);
    }

    /** The player sat back down at their seat: no longer away. */
    public static void back(ServerLevel level, BlockPos table, ServerPlayer player) {
        Away away = AWAY.remove(key(level, table, player.getUUID()));
        if (away != null) {
            tellOthers(level, table, player.getUUID(), Component.translatable("message.gathering.afb.back", away.name));
            refresh(level, table);
        }
    }

    /** The player gave their seat up some way of their own - Leave table - so nothing is kept for them. */
    public static void leftTheSeat(Level level, BlockPos table, UUID player, int seat) {
        if (level instanceof ServerLevel server) {
            AWAY.remove(key(server, table, player));
            if (seat >= 0) {
                remember(new GivenUp(dimension(server), table.immutable(), seat, player));
            }
        }
    }

    /** The player conceded: a seat kept for them while away is given up. */
    public static void conceded(ServerLevel level, BlockPos table, UUID player) {
        Key key = key(level, table, player);
        Away away = AWAY.get(key);
        if (away != null) {
            release(level, key, away, null);
        }
    }

    /** Forgets a player's seat being kept, without giving it up - they no longer hold it anyway. */
    public static void forget(Level level, BlockPos table, UUID player) {
        if (level instanceof ServerLevel server) {
            AWAY.remove(key(server, table, player));
        }
    }

    /** Whether this player is away from the board at this table. */
    public static boolean isAway(Level level, BlockPos table, UUID player) {
        return level instanceof ServerLevel server && AWAY.containsKey(key(server, table, player));
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
                && GIVEN_UP.contains(new GivenUp(dimension(server), table, seat, owner));
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
        if (AWAY.isEmpty() || clock.applyAsLong(server) % TICKS_PER_SECOND != 0) {
            return;
        }
        List<Map.Entry<Key, Away>> due = new ArrayList<>();
        for (Iterator<Map.Entry<Key, Away>> it = AWAY.entrySet().iterator(); it.hasNext(); ) {
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
            } else if (clock.applyAsLong(server) >= entry.getValue().until) {
                due.add(entry);
            }
        }
        for (Map.Entry<Key, Away> entry : due) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getKey().dimension())));
            if (level != null && AWAY.get(entry.getKey()) == entry.getValue()) {
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
    }

    /**
     * Gives up a kept seat: out of the seat, and the seat free to be taken with its board and its deck, which the
     * table goes on holding for its owner until the game is over.
     */
    private static void release(ServerLevel level, Key key, Away away, Component said) {
        AWAY.remove(key);
        BlockPos table = key.table();
        remember(new GivenUp(key.dimension(), table, away.seat, key.player()));
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
            if (!id.equals(away) && !AWAY.containsKey(key(level, table, id))) {
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
        return AWAY.entrySet().stream()
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

    private static void remember(GivenUp seat) {
        if (GIVEN_UP.size() >= MOST_REMEMBERED) {
            GIVEN_UP.clear();
        }
        GIVEN_UP.add(seat);
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

    /** Seconds as minutes and seconds: 7:05. */
    static String clock(long seconds) {
        return (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }
}
