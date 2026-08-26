package dev.gathering.block;

import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.persistence.SessionCipher;
import dev.gathering.core.game.persistence.StoredSession;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.DraftPodCodec;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.match.MatchState;
import dev.gathering.core.table.Side;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import dev.gathering.server.SessionKeyring;
import java.io.IOException;
import javax.crypto.SecretKey;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import dev.gathering.core.table.TableCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a table remembers.
 *
 * <p>One per table, on the corner that owns it. It holds who has taken which of its edges,
 * which is a table's share of a cluster's seating: a seat is identified by the table it is at
 * and the edge it is on, so storing the claim on that table means it is saved with that table
 * and comes back with it.
 *
 * <p>Seats are registrations rather than chairs. Nobody is sitting anywhere - the design has
 * seated players walking around, tending a furnace and heckling over a shoulder - so a claim
 * is a name against an edge and nothing more. It survives logging out, because the design
 * says leaving does not drop your seat.
 *
 * <p>The session itself will live here too, for the same reason: a block entity is the one
 * thing in Minecraft saved with the world at the position it belongs to.
 */
public class TableBlockEntity extends BlockEntity {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Gathering");

    public static final String ID = "table";

    private static final String FELT_KEY = "felt";
    private static final String COMMAND_ZONE_KEY = "command_zone";
    private static final String FORMAT_CHOSEN_KEY = "format_chosen";
    private static final String SEATS_KEY = "seats";
    private static final String SESSION_OPEN_KEY = "session_open";
    private static final String POD_KEY = "draft_pod";
    private static final String SESSION_SEALED_KEY = "session_sealed";
    private static final String STARTING_LIFE_KEY = "starting_life";
    private static final String FORMAT_KEY = "format";
    private static final String BEST_OF_KEY = "best_of";
    private static final String GAME_NUMBER_KEY = "game_number";
    private static final String WINS_KEY = "wins";
    private static final String DECKS_KEY = "decks";
    private static final String DECK_SEAT_KEY = "seat";
    private static final String DECK_KEY = "deck";
    private static final String POOL_KEY = "pool";
    private static final String ANTE_KEY = "ante";
    private static final String FOR_KEEPS_KEY = "for_keeps";
    private static final String ANTE_SEAT_KEY = "seat";
    private static final String ANTE_CARDS_KEY = "cards";

    /** Two seconds. Ambience, not gameplay - moves are pushed as they happen. */
    private static final int AMBIENT_INTERVAL_TICKS = 40;
    private static final String SIDE_KEY = "side";
    private static final String PLAYER_KEY = "player";

    private final Map<Side, UUID> claims = new EnumMap<>(Side.class);

    /** Empty for the felt's own colour; a dye replaces it. */
    private DyeColor felt;

    /**
     * The game on this cluster, if there is one and this is the table holding it.
     *
     * <p>Restored from {@link #stored} the first time somebody asks rather than at load,
     * because reading it needs the session key and a key that cannot be read must never be
     * the reason a world fails to load.
     */
    private GameSession session;

    private StoredSession stored;
    private int startingLife;
    private boolean restoreFailed;

    private int ambientCountdown;

    /**
     * The set of games this table is playing, if any.
     *
     * <p>Kept beside the session rather than in it, and in the open rather than sealed: a
     * match outlives the game it is currently on, and who has won how many is the most public
     * fact at a table.
     */
    private MatchState match;

    /**
     * The draft running on this cluster, if any.
     *
     * <p>Saved with the world, because a draft is twenty minutes of decisions and a server
     * restart in the middle of one must not eat it.
     */
    private DraftPod pod;

    /**
     * The decks that were put down on this table, held until the match is over.
     *
     * <p>A deck committed to a game used to be a deck destroyed: the item was consumed, the
     * library and commanders went into the session, and the sideboard went nowhere at all.
     * Ending the game then left its owner with nothing, which is not something a table may do
     * to somebody's deck.
     *
     * <p>So the table is a deckbox for the duration. It holds each seat's whole deck -
     * sideboard included, which is the only reason sideboarding between games is possible at
     * all - hands it back when the match ends, and is saved with the world, because a server
     * restart mid-match must not eat four decks.
     */
    private final Map<SeatId, DeckComponent> decks = new LinkedHashMap<>();

    /**
     * And the pool each of those was drafted from, for the ones that were.
     *
     * <p>Beside the decks rather than inside them: a deck's contents change every time
     * somebody boards a card in and a pool never changes at all.
     */
    private final Map<SeatId, DraftedPool> pools = new LinkedHashMap<>();

    /**
     * The pot, when this table is playing for keeps.
     *
     * <p>Held here rather than inside the game because it outlives one: a session that dies
     * to a crash has to give its cards back, and the only thing that survives that is what
     * was written to disk. The brief is blunt about it - a pot that could be eaten by a
     * server restart is a pot nobody sensible puts a card into, and then the feature does not
     * exist. So it is escrow on the block, saved beside the decks, for the same reason.
     */
    private dev.gathering.core.ante.AntePot pot = dev.gathering.core.ante.AntePot.EMPTY;

    /**
     * Whether the game running here is being played for keeps.
     *
     * <p>Set when the table agreed and cleared when the session ends, so it says something
     * about this game rather than about the server. Saved, because a deck put down after a
     * restart has to be staked from on exactly the terms everyone agreed to before it.
     */
    private boolean forKeeps;

    public TableBlockEntity(BlockPos pos, BlockState state) {
        super(dev.gathering.item.GatheringContent.TABLE_ENTITY.get(), pos, state);
    }

    /**
     * The game on this table, opening it from storage if this is the first time anybody has
     * asked since the world loaded.
     */
    public Optional<GameSession> session() {
        if (session == null && stored != null && !restoreFailed) {
            restoreFailed = true;
            SessionKeyring.key().ifPresent(key -> {
                try {
                    session = stored.restore(key);
                    restoreFailed = false;
                } catch (IOException | SessionCipher.SealedStreamException e) {
                    // The table keeps the bytes: an unopenable session is still somebody's
                    // game, and overwriting it with nothing would be the one irreversible
                    // thing to do about it.
                    LOGGER.error("The session at {} will not open: {}", worldPosition, e.getMessage());
                }
            });
        }
        return Optional.ofNullable(session);
    }

    public boolean hasSession() {
        return session != null || stored != null;
    }

    public Optional<MatchState> match() {
        return Optional.ofNullable(match);
    }

    /**
     * The draft running on this cluster, if there is one.
     *
     * <p>Beside the session rather than inside it, because a pod is not a game: it forms
     * before there is anything to play, it holds no board, and the games it turns into are
     * ordinary sessions afterwards. Kept in the open rather than sealed, because unlike a
     * library nothing here is a secret from the server - and unlike a session, what each
     * drafter may see is decided when a view is built rather than stored separately.
     */
    public Optional<DraftPod> pod() {
        return Optional.ofNullable(pod);
    }

    public boolean hasPod() {
        return pod != null;
    }

    /** Opens a draft here, or replaces the one that was running with a turn of it. */
    public void setPod(DraftPod running) {
        this.pod = running;
        setChanged();
    }

    /** Ends the draft and forgets it, which is what handing the pools out means. */
    public void endPod() {
        this.pod = null;
        setChanged();
    }

    /**
     * Whether somebody actually asked for the format this table is playing.
     *
     * <p>The deck check is a tournament deck check, and a tournament deck check happens
     * because somebody entered a tournament. Walking up to a bare table holding a deck and
     * right-clicking it says "let me play", not "hold me to Commander" - the table has to
     * pick some rules to start with and it picks Commander, but the player never named it. So
     * a deck that fails there is told what is wrong and dealt out anyway, and only a table
     * somebody chose a format for turns that into a refusal.
     *
     * <p>Server-side only: no client draws anything from it, so it is not in the update tag.
     */
    public boolean formatWasChosen() {
        return formatChosen;
    }

    /** Said by the setup screen, which is the only place a format is named. */
    public void formatWasChosen(boolean chosen) {
        this.formatChosen = chosen;
        setChanged();
    }

    private boolean formatChosen;

    /**
     * Whether the game on this table has a command zone, which decides whether one is drawn.
     *
     * <p>Presentation, not a rule: nothing during play consults the format, and this does not
     * either - it asks the match what kind of game was started and stops. The server has the
     * match and works it out; a client is never sent one, so it is told the answer instead.
     */
    public boolean hasCommandZone() {
        return match != null ? match.rules().format().hasCommandZone() : commandZone;
    }

    /**
     * What a client was told about the above, because a client has no match to ask.
     *
     * <p>Only ever read when {@code match} is absent, which on a server is only before a game
     * has started - and then it is false either way.
     */
    private boolean commandZone;

    public void beginSession(GameSession newSession, int life, MatchState newMatch) {
        this.session = newSession;
        this.startingLife = life;
        this.match = newMatch;
        this.stored = null;
        this.restoreFailed = false;
        setChanged();
        tellClients();
    }

    /**
     * Pushes what a client is told about this table out again.
     *
     * <p>The block entity's own data, not the game's: whether the felt is dyed and whether the
     * game has a command zone. A blockstate never changes for either, so nothing else would.
     */
    private void tellClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Takes a seat's deck into the table's keeping for the rest of the match, and the pool it
     * was drafted from - null for a deck nobody drafted, which is every imported one.
     *
     * <p>The pool is held with the deck rather than left on the item, because the item is
     * gone: the table takes the whole deck for the length of a match and hands back a new
     * stack afterwards. Without this a drafted deck came back from its first game with no
     * pool on it, and the limited check it had been playing under quietly stopped applying.
     *
     * <p>There is deliberately no two-argument version. One that defaulted the pool to null
     * would drop a pool every time somebody called the short form out of habit - which is a
     * deck quietly stopping being a drafted deck, and nothing to see when it happens.
     */
    public void holdDeck(SeatId seat, DeckComponent deck, DraftedPool pool) {
        decks.put(seat, deck);
        if (pool == null || pool.isEmpty()) {
            pools.remove(seat);
        } else {
            pools.put(seat, pool);
        }
        setChanged();
    }

    /** Whether this game is being played for keeps. */
    public boolean playingForKeeps() {
        return forKeeps;
    }

    /** Said once, when the table has agreed and the game is about to start. */
    public void playForKeeps(boolean keeps) {
        this.forKeeps = keeps;
        setChanged();
    }

    /** Puts a seat's stake into the pot. */
    public void stake(SeatId seat, java.util.List<dev.gathering.core.card.CardIdentity> cards) {
        if (seat == null || cards == null || cards.isEmpty()) {
            return;
        }
        pot = pot.with(seat, cards);
        setChanged();
    }

    /** What is in the pot, and whose. */
    public dev.gathering.core.ante.AntePot pot() {
        return pot;
    }

    /**
     * Hands the pot over and forgets it.
     *
     * <p>Emptied here rather than by the caller, so a pot cannot be paid out twice. The one
     * arithmetic mistake this feature must not make is a card existing in two places, and a
     * pot read without being cleared is exactly how that happens.
     */
    public dev.gathering.core.ante.AntePot releasePot() {
        dev.gathering.core.ante.AntePot taken = pot;
        pot = dev.gathering.core.ante.AntePot.EMPTY;
        setChanged();
        return taken;
    }

    public Optional<DeckComponent> deckOf(SeatId seat) {
        return Optional.ofNullable(decks.get(seat));
    }

    /** The pool the deck at this seat was drafted from, if it was drafted. */
    public Optional<DraftedPool> poolOf(SeatId seat) {
        return Optional.ofNullable(pools.get(seat));
    }

    /**
     * Every deck the table is holding, in seat order.
     *
     * <p>In seat order, which Map.copyOf would have thrown away for a hash order salted once
     * per launch. This is walked to put held decks back down between games of a set, and each
     * one puts a line in the session log - so the log's own order would have come out
     * differently every time the server started, on a record whose whole job is being the
     * thing everybody can check afterwards.
     */
    public Map<SeatId, DeckComponent> heldDecks() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(decks));
    }

    /**
     * Hands the decks back and forgets them, which is what the end of a match is.
     *
     * <p>Deck and pool together in one value, because handing one back without the other is
     * the bug this shape exists to prevent - and two calls that must both happen is one call
     * somebody forgets.
     */
    public Map<SeatId, HeldDeck> releaseDecks() {
        Map<SeatId, HeldDeck> released = new LinkedHashMap<>();
        decks.forEach((seat, deck) -> released.put(seat, new HeldDeck(deck, pools.get(seat))));
        decks.clear();
        pools.clear();
        setChanged();
        // Seat order, for the same reason: this decides what order decks are handed back in.
        return java.util.Collections.unmodifiableMap(released);
    }

    /** A deck the table is holding, and what it may be built from if it was drafted. */
    public record HeldDeck(DeckComponent deck, DraftedPool pool) {
    }

    /** Records how a game went, without ending the set it belongs to. */
    public void recordMatch(MatchState updated) {
        this.match = updated;
        setChanged();
    }

    /**
     * Ends the game and the match, keeping nothing.
     *
     * <p>Does not hand the decks back on its own - the caller has players to hand them to and
     * this does not. It does drop them, so a caller that forgets loses them loudly at the next
     * save rather than quietly leaving four decks inside a table forever.
     */
    public void endSession() {
        // Loud rather than silent. Every path that ends a session settles the pot first, and
        // the pot is deliberately not cleared here - stranded cards can still be handed back,
        // whereas cards this quietly forgot are gone. A line in the log is how a future
        // caller that forgot to settle gets found before somebody loses a card.
        if (!pot.isEmpty()) {
            LOGGER.error("The game at {} ended while still holding a pot of {} card(s);"
                    + " they have not been handed out and are still on the table",
                    worldPosition, pot.size());
        }
        this.session = null;
        this.stored = null;
        this.match = null;
        this.restoreFailed = false;
        this.formatChosen = false;
        this.forKeeps = false;
        this.decks.clear();
        setChanged();
        tellClients();
    }

    /** Ends the current game but keeps the match and the decks, for the next game of a set. */
    public void endGameKeepingMatch() {
        this.session = null;
        this.stored = null;
        this.restoreFailed = false;
        setChanged();
    }

    /**
     * Keeps the room's view of this table up to date.
     *
     * <p>Moves are pushed as they happen, but somebody who walks up to a game in progress has
     * missed all of them - so the public board goes out on a slow beat as well. Slow because
     * it is ambience: a miniature that lags a second behind is a miniature, and a miniature
     * that costs a packet a tick to everyone in range is a bill nobody agreed to.
     */
    public static void serverTick(
            net.minecraft.world.level.Level level, BlockPos pos, BlockState state, TableBlockEntity table) {
        if (++table.ambientCountdown < AMBIENT_INTERVAL_TICKS) {
            return;
        }
        table.ambientCountdown = 0;

        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        table.session().ifPresent(session -> dev.gathering.server.TableBroadcast.sendAmbient(
                server, pos, session,
                dev.gathering.server.TableBroadcast.seatedAt(server, pos).stream()
                        .map(seated -> seated.player().getUUID())
                        .collect(java.util.stream.Collectors.toSet())));
    }

    public Optional<DyeColor> felt() {
        return Optional.ofNullable(felt);
    }

    /**
     * Dyes the felt, and says whether anything changed.
     *
     * <p>Colour is a property of the table rather than of its blockstate, which is what keeps
     * a dyeable surface from multiplying the blockstate count by sixteen for something that
     * is never asked about except when drawing.
     */
    public boolean dye(DyeColor colour) {
        if (colour == felt) {
            return false;
        }
        felt = colour;
        setChanged();
        if (level != null) {
            // Colour lives in the block entity, so a blockstate change will not tell the
            // client about it; this is what does.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    /**
     * Asks the client to draw this table again, because its colour has changed.
     *
     * <p>The felt is a tint on a texture rather than sixteen textures, and a tint is baked
     * into the chunk's mesh when it is built. Telling the client the block entity's data has
     * changed does not rebuild that mesh - so a table dyed while somebody was looking at it
     * stayed the old colour until something else happened nearby that rebuilt the chunk,
     * which is a fix nobody can find and looks exactly like the dye not working.
     *
     * <p>All four quarters, not just this one. One block entity owns a two-by-two table and
     * every quarter's tint asks it for the colour, so all four meshes are out of date.
     *
     * <p>Done here rather than in a packet handler because {@code onDataPacket} is a NeoForge
     * extension and this class is loader-free. Both loaders arrive at {@code loadAdditional}
     * for a block entity update, so this catches the update either way - and catches a
     * chunk-load with a colour on it, where the mesh is being built anyway and marking it
     * dirty costs nothing.
     */
    private void redrawTheFelt() {
        if (level == null || !level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        for (int acrossX = 0; acrossX < TableCell.BLOCKS_PER_TABLE; acrossX++) {
            for (int acrossZ = 0; acrossZ < TableCell.BLOCKS_PER_TABLE; acrossZ++) {
                BlockPos quarter = worldPosition.offset(acrossX, 0, acrossZ);
                level.setBlocksDirty(quarter, state, level.getBlockState(quarter));
            }
        }
    }

    public Optional<UUID> occupantOf(Side side) {
        return Optional.ofNullable(claims.get(side));
    }

    public boolean hasAnyOccupant() {
        return !claims.isEmpty();
    }

    /** Takes an edge for a player, or does nothing if somebody else already has it. */
    public boolean claim(Side side, UUID player) {
        if (claims.containsKey(side)) {
            return false;
        }
        claims.put(side, player);
        setChanged();
        return true;
    }

    /** Gives up an edge, but only for the player who took it. */
    public boolean release(Side side, UUID player) {
        if (!player.equals(claims.get(side))) {
            return false;
        }
        claims.remove(side);
        setChanged();
        return true;
    }

    /** Whichever edge of this table the player holds, if any. */
    public Optional<Side> sideHeldBy(UUID player) {
        return claims.entrySet().stream()
                .filter(entry -> entry.getValue().equals(player))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * What a joining client is told.
     *
     * <p>Only the felt colour: seat claims are player identity and nothing on a client draws
     * them yet, so sending them would be data leaving the server for no reason.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (felt != null) {
            tag.putString(FELT_KEY, felt.getSerializedName());
        }
        // And whether this game has a command zone, which is a fact about the format and not
        // about anybody's cards - the client needs it to know whether to draw the box.
        tag.putBoolean(COMMAND_ZONE_KEY, hasCommandZone());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        DyeColor was = felt;
        felt = tag.contains(FELT_KEY) ? DyeColor.byName(tag.getString(FELT_KEY), null) : null;
        if (was != felt) {
            redrawTheFelt();
        }
        commandZone = tag.getBoolean(COMMAND_ZONE_KEY);
        formatChosen = tag.getBoolean(FORMAT_CHOSEN_KEY);

        session = null;
        restoreFailed = false;
        startingLife = tag.getInt(STARTING_LIFE_KEY);
        stored = tag.contains(SESSION_OPEN_KEY)
                ? new StoredSession(tag.getByteArray(SESSION_OPEN_KEY), tag.getByteArray(SESSION_SEALED_KEY))
                : null;
        match = readMatch(tag);

        // A draft that will not load is dropped rather than kept as unreadable bytes, unlike
        // a session: a session's bytes are somebody's whole game and might open on the next
        // start with the right key, but a pod that does not add up will never add up, and
        // leaving it would leave a cluster permanently unable to start anything.
        pod = null;
        if (tag.contains(POD_KEY)) {
            try {
                pod = DraftPodCodec.read(tag.getByteArray(POD_KEY));
            } catch (IOException broken) {
                LOGGER.error("The draft at {} will not load: {}", worldPosition, broken.getMessage());
            }
        }

        decks.clear();
        pools.clear();
        ListTag heldDecks = tag.getList(DECKS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < heldDecks.size(); index++) {
            CompoundTag held = heldDecks.getCompound(index);
            SeatId seat = new SeatId(held.getInt(DECK_SEAT_KEY));
            DeckComponent.CODEC
                    .parse(net.minecraft.nbt.NbtOps.INSTANCE, held.get(DECK_KEY))
                    .resultOrPartial(problem -> LOGGER.error(
                            "A deck held at {} will not load: {}", worldPosition, problem))
                    .ifPresent(deck -> decks.put(seat, deck));
            if (held.contains(POOL_KEY)) {
                DraftedPool.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, held.get(POOL_KEY))
                        .resultOrPartial(problem -> LOGGER.error(
                                "A pool held at {} will not load: {}", worldPosition, problem))
                        .ifPresent(pool -> pools.put(seat, pool));
            }
        }

        forKeeps = tag.getBoolean(FOR_KEEPS_KEY);
        pot = dev.gathering.core.ante.AntePot.EMPTY;
        ListTag staked = tag.getList(ANTE_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < staked.size(); index++) {
            CompoundTag entry = staked.getCompound(index);
            SeatId seat = new SeatId(entry.getInt(ANTE_SEAT_KEY));
            ListTag cards = entry.getList(ANTE_CARDS_KEY, Tag.TAG_COMPOUND);
            java.util.List<dev.gathering.core.card.CardIdentity> read =
                    new java.util.ArrayList<>(cards.size());
            for (int card = 0; card < cards.size(); card++) {
                dev.gathering.item.CardComponent.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, cards.get(card))
                        .resultOrPartial(problem -> LOGGER.error(
                                "An ante card at {} will not load: {}", worldPosition, problem))
                        .ifPresent(component -> read.add(component.toIdentity()));
            }
            pot = pot.with(seat, read);
        }

        claims.clear();
        ListTag seats = tag.getList(SEATS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < seats.size(); index++) {
            CompoundTag seat = seats.getCompound(index);
            // Written by name rather than by ordinal: an ordinal is a number that means
            // something different the moment the enum gains a value, and this is a save file.
            Side side = sideNamed(seat.getString(SIDE_KEY));
            if (side != null && seat.hasUUID(PLAYER_KEY)) {
                claims.put(side, seat.getUUID(PLAYER_KEY));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (felt != null) {
            // By name, like the seat sides and for the same reason: this is a save file.
            tag.putString(FELT_KEY, felt.getSerializedName());
        }
        writeSession(tag);
        writeMatch(tag);
        writeDecks(tag);
        writePot(tag);
        if (forKeeps) {
            tag.putBoolean(FOR_KEEPS_KEY, true);
        }
        // In the open. A pod holds every pack in the ring, so it is exactly as secret as a
        // library - but it never leaves the server: what a drafter is sent is a view, built
        // fresh each time, and there is no path from these bytes to a client.
        if (pod != null) {
            tag.putByteArray(POD_KEY, DraftPodCodec.write(pod));
        }
        ListTag seats = new ListTag();
        claims.forEach((side, player) -> {
            CompoundTag seat = new CompoundTag();
            seat.putString(SIDE_KEY, side.name());
            seat.putUUID(PLAYER_KEY, player);
            seats.add(seat);
        });
        tag.put(SEATS_KEY, seats);
    }

    /**
     * Writes the pot down.
     *
     * <p>The whole reason escrow is on the block. Losing this to a restart is losing cards
     * that people agreed to play for and never got the chance to win back.
     */
    private void writePot(CompoundTag tag) {
        if (pot.isEmpty()) {
            return;
        }
        ListTag staked = new ListTag();
        pot.stakes().forEach((seat, cards) -> {
            ListTag written = new ListTag();
            for (dev.gathering.core.card.CardIdentity card : cards) {
                dev.gathering.item.CardComponent.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                                dev.gathering.item.CardComponent.of(card))
                        .resultOrPartial(problem -> LOGGER.error(
                                "An ante card at {} will not save: {}", worldPosition, problem))
                        .ifPresent(written::add);
            }
            if (written.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(ANTE_SEAT_KEY, seat.index());
            entry.put(ANTE_CARDS_KEY, written);
            staked.add(entry);
        });
        tag.put(ANTE_KEY, staked);
    }

    /** Writes the held decks down. Losing one to a server restart is losing somebody's deck. */
    private void writeDecks(CompoundTag tag) {
        ListTag held = new ListTag();
        decks.forEach((seat, deck) -> DeckComponent.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, deck)
                .resultOrPartial(problem -> LOGGER.error(
                        "A deck held at {} will not save: {}", worldPosition, problem))
                .ifPresent(encoded -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt(DECK_SEAT_KEY, seat.index());
                    entry.put(DECK_KEY, encoded);
                    DraftedPool pool = pools.get(seat);
                    if (pool != null && !pool.isEmpty()) {
                        DraftedPool.CODEC
                                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, pool)
                                .resultOrPartial(problem -> LOGGER.error(
                                        "A pool held at {} will not save: {}",
                                        worldPosition, problem))
                                .ifPresent(written -> entry.put(POOL_KEY, written));
                    }
                    held.add(entry);
                }));
        tag.put(DECKS_KEY, held);
    }

    /**
     * Writes the game down, sealed.
     *
     * <p>A session that could not be opened is written back exactly as it was found. It is
     * still somebody's game, and replacing it with nothing because this server could not read
     * it is the one irreversible thing available here.
     */
    private void writeSession(CompoundTag tag) {
        StoredSession toWrite = stored;
        if (session != null) {
            Optional<SecretKey> key = SessionKeyring.key();
            if (key.isEmpty()) {
                LOGGER.error("No session key, so the game at {} cannot be saved", worldPosition);
                return;
            }
            try {
                toWrite = StoredSession.of(session, startingLife, key.get());
            } catch (IOException e) {
                LOGGER.error("Could not write the game at {}: {}", worldPosition, e.getMessage());
                return;
            }
        }
        if (toWrite == null) {
            return;
        }
        tag.putByteArray(SESSION_OPEN_KEY, toWrite.openPart());
        tag.putByteArray(SESSION_SEALED_KEY, toWrite.sealedPart());
        tag.putInt(STARTING_LIFE_KEY, startingLife);
        tag.putBoolean(FORMAT_CHOSEN_KEY, formatChosen);
    }

    /**
     * The set of games, written in the open.
     *
     * <p>By format id rather than by anything derived from the preset, so a format whose
     * numbers change does not silently change a match already in progress into a different
     * one - it changes what the preset says, which is the honest outcome.
     */
    private void writeMatch(CompoundTag tag) {
        if (match == null) {
            return;
        }
        tag.putString(FORMAT_KEY, match.rules().format().id());
        tag.putInt(BEST_OF_KEY, match.rules().bestOf());
        tag.putInt(GAME_NUMBER_KEY, match.gameNumber());

        ListTag wins = new ListTag();
        match.wins().forEach((seat, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("seat", seat.index());
            entry.putInt("won", count);
            wins.add(entry);
        });
        tag.put(WINS_KEY, wins);
    }

    private static MatchState readMatch(CompoundTag tag) {
        if (!tag.contains(FORMAT_KEY)) {
            return null;
        }
        var format = dev.gathering.core.format.FormatPresets.byId(tag.getString(FORMAT_KEY));
        if (format.isEmpty()) {
            // A format this build does not know about. The game itself is untouched; it
            // simply stops being a match, which beats refusing to load the table.
            LOGGER.warn("Table at {} plays an unknown format {}", "?", tag.getString(FORMAT_KEY));
            return null;
        }
        try {
            MatchRules rules = new MatchRules(format.get(), tag.getInt(BEST_OF_KEY));
            java.util.Map<dev.gathering.core.game.SeatId, Integer> wins = new java.util.LinkedHashMap<>();
            ListTag stored = tag.getList(WINS_KEY, Tag.TAG_COMPOUND);
            for (int index = 0; index < stored.size(); index++) {
                CompoundTag entry = stored.getCompound(index);
                wins.put(new dev.gathering.core.game.SeatId(entry.getInt("seat")), entry.getInt("won"));
            }
            return new MatchState(rules, wins, Math.max(1, tag.getInt(GAME_NUMBER_KEY)));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Table has an unreadable match: {}", e.getMessage());
            return null;
        }
    }

    private static Side sideNamed(String name) {
        for (Side side : Side.values()) {
            if (side.name().equals(name)) {
                return side;
            }
        }
        // An unreadable seat is an empty seat, never a failed world load.
        return null;
    }
}
