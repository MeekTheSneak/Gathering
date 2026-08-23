package dev.gathering.block;

import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.persistence.SessionCipher;
import dev.gathering.core.game.persistence.StoredSession;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.match.MatchState;
import dev.gathering.core.table.Side;
import dev.gathering.item.DeckComponent;
import dev.gathering.server.SessionKeyring;
import java.io.IOException;
import javax.crypto.SecretKey;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private static final String SESSION_SEALED_KEY = "session_sealed";
    private static final String STARTING_LIFE_KEY = "starting_life";
    private static final String FORMAT_KEY = "format";
    private static final String BEST_OF_KEY = "best_of";
    private static final String GAME_NUMBER_KEY = "game_number";
    private static final String WINS_KEY = "wins";
    private static final String DECKS_KEY = "decks";
    private static final String DECK_SEAT_KEY = "seat";
    private static final String DECK_KEY = "deck";

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

    /** Takes a seat's deck into the table's keeping for the rest of the match. */
    public void holdDeck(SeatId seat, DeckComponent deck) {
        decks.put(seat, deck);
        setChanged();
    }

    public Optional<DeckComponent> deckOf(SeatId seat) {
        return Optional.ofNullable(decks.get(seat));
    }

    /** Every deck the table is holding, in seat order. */
    public Map<SeatId, DeckComponent> heldDecks() {
        return Map.copyOf(decks);
    }

    /** Hands the decks back and forgets them, which is what the end of a match is. */
    public Map<SeatId, DeckComponent> releaseDecks() {
        Map<SeatId, DeckComponent> released = Map.copyOf(decks);
        decks.clear();
        setChanged();
        return released;
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
        this.session = null;
        this.stored = null;
        this.match = null;
        this.restoreFailed = false;
        this.formatChosen = false;
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
        felt = tag.contains(FELT_KEY) ? DyeColor.byName(tag.getString(FELT_KEY), null) : null;
        commandZone = tag.getBoolean(COMMAND_ZONE_KEY);
        formatChosen = tag.getBoolean(FORMAT_CHOSEN_KEY);

        session = null;
        restoreFailed = false;
        startingLife = tag.getInt(STARTING_LIFE_KEY);
        stored = tag.contains(SESSION_OPEN_KEY)
                ? new StoredSession(tag.getByteArray(SESSION_OPEN_KEY), tag.getByteArray(SESSION_SEALED_KEY))
                : null;
        match = readMatch(tag);

        decks.clear();
        ListTag heldDecks = tag.getList(DECKS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < heldDecks.size(); index++) {
            CompoundTag held = heldDecks.getCompound(index);
            DeckComponent.CODEC
                    .parse(net.minecraft.nbt.NbtOps.INSTANCE, held.get(DECK_KEY))
                    .resultOrPartial(problem -> LOGGER.error(
                            "A deck held at {} will not load: {}", worldPosition, problem))
                    .ifPresent(deck -> decks.put(new SeatId(held.getInt(DECK_SEAT_KEY)), deck));
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
        ListTag seats = new ListTag();
        claims.forEach((side, player) -> {
            CompoundTag seat = new CompoundTag();
            seat.putString(SIDE_KEY, side.name());
            seat.putUUID(PLAYER_KEY, player);
            seats.add(seat);
        });
        tag.put(SEATS_KEY, seats);
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
