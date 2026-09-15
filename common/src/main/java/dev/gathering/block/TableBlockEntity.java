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
 * <p>One per table, on the corner that owns it, holding who has taken which edge. A seat is
 * the table plus the edge, so the claim is saved with that table and comes back with it.
 * <p>Seats are registrations rather than chairs: the design has seated players walking around
 * and heckling over a shoulder, so a claim is a name against an edge and nothing more. It
 * survives logging out, because leaving does not drop your seat.
 * <p>The session lives here too, for the reason a block entity exists: it is the one thing
 * Minecraft saves with the world at the position it belongs to.
 */
public class TableBlockEntity extends BlockEntity {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Gathering");

    public static final String ID = "table";

    private static final String FELT_KEY = "felt";
    private static final String COMMAND_ZONE_KEY = "command_zone";
    private static final String COMMANDER_DAMAGE_KEY = "commander_damage";
    private static final String FORMAT_CHOSEN_KEY = "format_chosen";
    private static final String SEATS_KEY = "seats";
    private static final String SESSION_OPEN_KEY = "session_open";
    private static final String POD_KEY = "draft_pod";
    private static final String SIGNUP_KEY = "pod_signup";
    private static final String POD_RECORD_KEY = "pod_record";
    private static final String APART_KEY = "plays_apart";
    private static final String TURNED_KEY = "turned";
    private static final String LABEL_NUMBER_KEY = "event_table";
    private static final String LABEL_LINE_KEY = "event_line";
    private static final String LABEL_ENDS_KEY = "event_ends";
    private static final String SESSION_SEALED_KEY = "session_sealed";
    private static final String STARTING_LIFE_KEY = "starting_life";
    private static final String FORMAT_KEY = "format";
    private static final String BEST_OF_KEY = "best_of";
    private static final String NEEDS_A_WINNER_KEY = "needs_a_winner";
    private static final String GAME_NUMBER_KEY = "game_number";
    private static final String LAST_WINNER_KEY = "last_winner";
    private static final String DRAWN_GAME_CHOOSER_KEY = "drawn_game_chooser";
    private static final String WINS_KEY = "wins";
    private static final String DECKS_KEY = "decks";
    private static final String DECK_SEAT_KEY = "seat";
    private static final String DECK_KEY = "deck";
    private static final String POOL_KEY = "pool";
    private static final String DECK_OWNER_KEY = "owner";
    private static final String NOT_LEGAL_IN_KEY = "not_legal_in";
    private static final String NOT_LEGAL_PROBLEMS_KEY = "not_legal_problems";
    private static final String NOT_LEGAL_MORE_KEY = "not_legal_more";
    private static final String ANTE_KEY = "ante";
    private static final String FOR_KEEPS_KEY = "for_keeps";
    private static final String PRACTICE_KEY = "practice";
    private static final String ANTE_SEAT_KEY = "seat";
    private static final String ANTE_CARDS_KEY = "cards";
    private static final String ANTE_STAKER_KEY = "staker";

    /** Two seconds. Ambience, not gameplay - moves are pushed as they happen. */
    private static final int AMBIENT_INTERVAL_TICKS = 40;

    /**
     * What the room was last told, so a board nobody has changed is not sent again.
     * <p>The public board went out every two seconds whether or not anything had happened -
     * per table, for as long as a session existed. Building one means walking every zone of
     * every seat through the visibility rules and serializing the result, once per person in
     * range: a measured 0.397 ms and 874 KB for two spectators on a sixteen-hundred-card board,
     * repeated for ever on a game nobody was playing.
     * <p>-1 means "nothing has been sent", which is also what it is reset to when the session
     * is replaced - a revision is monotonic within one session and meaningless across two.
     */
    private long lastAmbientRevision = -1;

    /**
     * Who was told, so somebody who has just walked up is not left looking at nothing.
     * <p>The audience is half the question. A board that has not changed still has to reach a
     * player who was not there for the last send, and that is the case a revision check alone
     * would get wrong - the commonest way to walk up to a table is to walk up to a quiet one.
     */
    private java.util.Set<UUID> lastAmbientAudience = java.util.Set.of();
    private static final String SIDE_KEY = "side";
    private static final String PLAYER_KEY = "player";

    private final Map<Side, UUID> claims = new EnumMap<>(Side.class);

    /** Empty for the felt's own color; a dye replaces it. */
    private DyeColor felt;

    /**
     * The game on this cluster, if there is one and this is the table holding it.
     * <p>Restored from {@link #stored} the first time somebody asks rather than at load,
     * because reading it needs the session key and a key that cannot be read must never be
     * the reason a world fails to load.
     */
    private GameSession session;

    /**
     * Whether this game is somebody learning the controls rather than a game for keeps.
     * <p>See {@link #isPractice()}. False for every ordinary game, which is what makes this
     * safe to add to a world that has never seen one.
     */
    private boolean practice;

    private StoredSession stored;
    private int startingLife;
    private boolean restoreFailed;

    private int ambientCountdown;
    /** What last went wrong in this table's tick, so it is logged once rather than every tick. */
    private String lastTickFailure;

    /**
     * The set of games this table is playing, if any.
     * <p>Kept beside the session rather than in it, and in the open rather than sealed: a
     * match outlives the game it is currently on, and who has won how many is the most public
     * fact at a table.
     */
    private MatchState match;

    /**
     * The draft running on this cluster, if any.
     * <p>Saved with the world, because a draft is twenty minutes of decisions and a server
     * restart in the middle of one must not eat it.
     */
    private DraftPod pod;

    /**
     * The decks that were put down on this table, held until the match is over.
     * <p>The table is a deckbox for the duration. It holds each seat's whole deck, sideboard
     * included - which is the only reason sideboarding between games is possible - hands it
     * back when the match ends, and is saved with the world, because a restart mid-match must
     * not eat four decks.
     * <p>Each one with the pool it was drafted from and who put it down, as one value per
     * seat. They were three maps kept in step by every method that touched any of them, and
     * two of those methods did not: ending a session cleared the decks and left the pools and
     * owners, and a saved deck that would not load still left its pool claiming the seat. One
     * value is one thing to put, take and forget.
     * <p>Ordered by when each seat first put a deck down, which is the order they are put back
     * down and handed back in - see {@link #heldDecks()}.
     */
    private final Map<SeatId, HeldDeck> held = new LinkedHashMap<>();

    /**
     * What is not legal about a held deck its player chose to play anyway, by seat: kept with the deck, so
     * everybody who comes to the table while it is down is told, and gone when the deck is handed back.
     */
    private final Map<SeatId, NotLegal> notLegal = new LinkedHashMap<>();

    /**
     * Who put each seat's stake in the pot.
     * <p>Beside the pot, exactly as each held deck keeps its owner: a seat says
     * where a card was staked from and a UUID says whose it was, and only the second is any
     * use when a pot goes back to the people who filled it.
     */
    private final Map<SeatId, UUID> stakers = new LinkedHashMap<>();

    /**
     * The pot, when this table is playing for keeps.
     * <p>Held here rather than inside the game because it outlives one: a session that dies
     * to a crash has to give its cards back, and the only thing that survives that is what
     * was written to disk. The brief is blunt about it - a pot that could be eaten by a
     * server restart is a pot nobody sensible puts a card into, and then the feature does not
     * exist. So it is escrow on the block, saved beside the decks, for the same reason.
     */
    private dev.gathering.core.ante.AntePot pot = dev.gathering.core.ante.AntePot.EMPTY;

    /**
     * Whether the game running here is being played for keeps.
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
                } catch (IOException | SessionCipher.SealedStreamException | RuntimeException e) {
                    // The table keeps the bytes: an unopenable session is still somebody's
                    // game, and overwriting it with nothing would be the one irreversible
                    // thing to do about it. RuntimeException is here for a log that will
                    // not fold - before submits were transactional a stale event could be
                    // written down mid-crash, and a save like that must not take the whole
                    // ticking block entity down with it on every launch.
                    LOGGER.error("The session at {} will not open: {}", worldPosition, e.getMessage());
                }
            });
        }
        return Optional.ofNullable(session);
    }

    public boolean hasSession() {
        return session != null || stored != null;
    }

    /**
     * Whether this table is holding a game it cannot open.
     * <p>Told apart from "holding a game", because everything else treats the two the same:
     * the table reads as occupied, right-clicking it finds no session and does nothing, and
     * crouching says a game is already running. A player is left with a table that is neither
     * playable nor clearable and nothing saying why.
     */
    public boolean sessionFailed() {
        // Asked rather than read: opening it is attempted once, lazily, and until somebody
        // has asked there is nothing to have failed.
        return session().isEmpty() && stored != null;
    }

    /**
     * Puts an unopenable game aside so the table can be used again.
     * <p>The bytes are written out first, under the mod's own data directory, and only then
     * dropped from the table. Nothing here overwrites a game with nothing: what could not be
     * read this time may be readable by a later version, or by somebody with the key, and it
     * is the only copy.
     *
     * @return where the game was put, or empty if it could not be written and so was kept
     */
    public Optional<java.nio.file.Path> setAsideTheBrokenGame() {
        StoredSession broken = stored;
        if (broken == null) {
            return Optional.empty();
        }
        java.nio.file.Path where;
        try {
            // Inside the save, because a game that broke in one world is that world's to
            // keep. See dev.gathering.server.ServerRun.
            java.nio.file.Path folder = dev.gathering.server.ServerRun.inSave("unreadable-games")
                    .orElseThrow(() -> new java.io.IOException("No server is running"));
            java.nio.file.Files.createDirectories(folder);
            where = folder.resolve(worldPosition.getX() + "_" + worldPosition.getY() + "_"
                    + worldPosition.getZ() + "-" + System.currentTimeMillis() + ".dat");
            CompoundTag holding = new CompoundTag();
            holding.putByteArray(SESSION_OPEN_KEY, broken.openPart());
            holding.putByteArray(SESSION_SEALED_KEY, broken.sealedPart());
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(where)) {
                net.minecraft.nbt.NbtIo.writeCompressed(holding, out);
            }
        } catch (java.io.IOException | RuntimeException couldNotWrite) {
            LOGGER.error("Could not set aside the unreadable game at {}: {}",
                    worldPosition, couldNotWrite.toString());
            return Optional.empty();
        }
        this.stored = null;
        this.restoreFailed = false;
        setChanged();
        return Optional.of(where);
    }

    public Optional<MatchState> match() {
        return Optional.ofNullable(match);
    }

    /**
     * The draft running on this cluster, if there is one.
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
        this.podRecord = null;
        setChanged();
    }

    /**
     * What an event's packs held and whose they were, from opening until the cards are handed
     * out. Null for a cube draft, whose cards belong to nobody.
     */
    private dev.gathering.core.draft.PodRecord podRecord;

    public Optional<dev.gathering.core.draft.PodRecord> podRecord() {
        return Optional.ofNullable(podRecord);
    }

    public void setPodRecord(dev.gathering.core.draft.PodRecord record) {
        this.podRecord = record;
        setChanged();
    }

    /**
     * Whether an event's packs are being opened right now.
     * <p>Not saved. Opening waits on the card pipeline, and nothing is taken out of the signup
     * until it has finished - so a restart in the middle simply leaves the signup as it was,
     * packs and all, and the host starts again. What this stops is anybody changing the signup
     * while the opening is under way.
     */
    private boolean opening;

    public boolean isOpening() {
        return opening;
    }

    public void setOpening(boolean now) {
        this.opening = now;
    }

    /**
     * Which draft turn the pick clock is counting and the game tick it began on. Not saved:
     * see {@link dev.gathering.server.PickClocks}.
     */
    private long clockTurn = Long.MIN_VALUE;
    private long clockStartedAt;

    public boolean isClockOnTurn(long turn) {
        return clockTurn == turn;
    }

    public void startClock(long turn, long now) {
        this.clockTurn = turn;
        this.clockStartedAt = now;
    }

    public long clockStartedAt() {
        return clockStartedAt;
    }

    /**
     * Whether this table is played on its own even when others touch it.
     * <p>Set for the whole long table at once, and only while none of it is in use - see
     * {@code TablesApart}. Sent to clients with the felt, because it is a fact about the
     * furniture that anybody looking at the tables can see.
     */
    private boolean playsApart;

    public boolean playsApart() {
        return playsApart;
    }

    /**
     * The number an event gave this table, who is playing at it, and the game time the round ends,
     * for the label drawn over it. Zero when no event is using it. Sent to clients; not saved,
     * because the event puts it back when it next changes.
     */
    private int eventTable;
    private String eventLine = "";
    private long eventEnds;

    public int eventTable() {
        return eventTable;
    }

    public String eventLine() {
        return eventLine;
    }

    public long eventEnds() {
        return eventEnds;
    }

    public void setEventLabel(int number, String line, long endsAt) {
        String cleaned = line == null ? "" : line;
        if (eventTable != number || !eventLine.equals(cleaned) || eventEnds != endsAt) {
            eventTable = number;
            eventLine = cleaned;
            eventEnds = endsAt;
            tellClients();
        }
    }

    /**
     * Whether this table, standing alone, seats across its east and west edges rather than its north
     * and south. Whoever sits down first at an empty table chooses, by the edge they sit at; a line of
     * tables seats along its long sides whatever this says. Sent to clients with the felt, because
     * the board drawn on the table turns with it.
     */
    private boolean turned;

    public boolean turned() {
        return turned;
    }

    public void setTurned(boolean turned) {
        if (this.turned != turned) {
            this.turned = turned;
            setChanged();
            tellClients();
        }
    }

    public void setPlaysApart(boolean apart) {
        if (this.playsApart != apart) {
            this.playsApart = apart;
            setChanged();
            tellClients();
        }
    }

    /**
     * A draft or sealed event being signed up for here, and the packs it is holding.
     * <p>On the anchor, beside the pod it will become, for the same reason the pod is: one
     * cluster runs one thing, and every table in it agrees where that is.
     */
    private PodSignup signup;

    /**
     * Whether a signup read back from the save could not be kept, so its packs are to be
     * handed back on the next tick - when there is a world to hand them back in.
     */
    private boolean signupToHandBack;

    public Optional<PodSignup> signup() {
        return Optional.ofNullable(signup);
    }

    public boolean hasSignup() {
        return signup != null;
    }

    /** Opens a signup here, or records a pack going in or coming out of the one open. */
    public void setSignup(PodSignup open) {
        this.signup = open;
        setChanged();
    }

    /**
     * Closes the signup and hands over every pack it was holding, for the caller to return.
     * <p>One call rather than a read and a clear: packs read off a signup that was then not
     * cleared are packs that can be handed back twice.
     */
    public java.util.List<PodSignup.Held> closeSignup() {
        java.util.List<PodSignup.Held> held = signup == null ? java.util.List.of() : signup.held();
        this.signup = null;
        this.signupToHandBack = false;
        setChanged();
        return held;
    }

    /** Whether the saved signup could not be kept and is waiting to hand its packs back. */
    public boolean signupIsToBeHandedBack() {
        return signupToHandBack && signup != null;
    }

    /**
     * Whether somebody actually asked for the format this table is playing.
     * <p>A tournament deck check happens because somebody entered a tournament. Right-clicking
     * a bare table holding a deck says "let me play", not "hold me to Commander" - the table
     * picks rules to start with, but the player never named them. So a deck that fails is told
     * what is wrong and dealt out anyway, and only a chosen format turns that into a refusal.
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
     * <p>Presentation, not a rule: nothing during play consults the format, and this does not
     * either - it asks the match what kind of game was started and stops. The server has the
     * match and works it out; a client is never sent one, so it is told the answer instead.
     */
    public boolean hasCommandZone() {
        return match != null ? match.rules().format().hasCommandZone() : commandZone;
    }

    /**
     * What a client was told about the above, because a client has no match to ask.
     * <p>Only ever read when {@code match} is absent, which on a server is only before a game
     * has started - and then it is false either way.
     */
    private boolean commandZone;

    /** Whether this game counts commander damage. Told to clients the same way as the above. */
    public boolean countsCommanderDamage() {
        return match != null ? match.rules().format().countsCommanderDamage() : commanderDamage;
    }

    /** What a client was told about the above. */
    private boolean commanderDamage;

    public void beginSession(GameSession newSession, int life, MatchState newMatch) {
        this.session = newSession;
        // A revision counts within one session and means nothing across two, so what the room
        // was last told about the old game cannot be compared against the new one.
        forgetWhatTheRoomWasTold();
        this.startingLife = life;
        this.match = newMatch;
        this.stored = null;
        this.restoreFailed = false;
        this.practice = false;
        setChanged();
        tellClients();
    }

    /**
     * Whether the game on this table is somebody learning the controls.
     * <p>The one flag that makes cards stop being property. A practice game is dealt a deck
     * the server made up on the spot, and that deck must never come back as an item: not to
     * the learner, not onto the floor, not into a trade and not into a pot. Everything that
     * hands cards to a person asks this first.
     * <p>Saved, so a world reopened halfway through a practice game is still a practice game
     * rather than becoming a real one with a free deck in it.
     */
    public boolean isPractice() {
        return practice;
    }

    /**
     * Marks the game on this table as practice, which cannot be undone while it lasts.
     * <p>One direction only, and deliberately: a practice game that could be turned into a
     * real one would be a way to mint a deck. It clears when the session ends, because the
     * next game on this table is a different game.
     */
    public void markAsPractice() {
        this.practice = true;
        // Practice and playing for keeps are the two things a table must never be at once.
        this.forKeeps = false;
        setChanged();
        tellClients();
    }

    /**
     * Unmarks a practice table, for the one caller that retires a game left over from the old
     * teaching design.
     * <p>The one-direction rule above is about a <em>running</em> practice game, and it still
     * holds: turning one into a real game would be a way to mint a deck out of stock the
     * server invented. This is the other thing - a saved game from a feature that no longer
     * exists, being taken apart. The caller clears the flag and then ends the session, in that
     * order and never the other way round, because between the two steps the table is an
     * ordinary table that is still holding whatever it was holding. Property survives an
     * interruption there; it would not survive one the other way round.
     * <p>The generated cards do not become anything. They are in the session, and ending a
     * session does not hand its cards to anybody - only a deck the table was <em>holding</em>
     * comes back, and the only thing that ever put one there is a real player committing a
     * real deck. See {@link dev.gathering.server.PracticeTable#retire}.
     */
    public void stopBeingPractice() {
        if (!practice) {
            return;
        }
        this.practice = false;
        setChanged();
        tellClients();
    }

    /**
     * Pushes what a client is told about this table out again.
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
     * <p>The pool is held with the deck rather than left on the item, because the item is
     * gone - the table takes the whole deck for a match and hands back a new stack. Without it
     * a drafted deck comes back with no pool and the limited check stops applying.
     * <p>Deliberately no two-argument version: one defaulting the pool to null drops it every
     * time somebody calls the short form out of habit, and nothing shows when it happens.
     */
    public void holdDeck(SeatId seat, DeckComponent deck, DraftedPool pool, UUID owner) {
        held.put(seat, new HeldDeck(deck, pool, owner));
        notLegal.remove(seat);
        setChanged();
    }

    /** Marks the deck held for this seat as not legal in the table's format, and played anyway. */
    public void playedAnyway(SeatId seat, NotLegal why) {
        if (held.containsKey(seat)) {
            notLegal.put(seat, why);
            setChanged();
        }
    }

    /** The held decks played though not legal, by seat, in seat order. */
    public Map<SeatId, NotLegal> notLegal() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(notLegal));
    }

    /**
     * What is not legal about a deck: the format it is not legal in, the first few problems the deck check
     * found, and how many more there were.
     */
    public record NotLegal(String format, java.util.List<String> problems, int more) {

        public NotLegal {
            problems = java.util.List.copyOf(problems);
        }
    }

    /**
     * Replaces a held deck's contents and nothing else about it: the pool it was drafted from
     * and whose it is stay as they were.
     * <p>For sideboarding, which edits a deck between games. It used to put the edited deck
     * down again as though freshly handed over, naming whoever made the edit as its owner -
     * and the person in a chair between games is not always the person whose deck is held
     * there.
     *
     * @return whether there was a deck at that seat to change
     */
    public boolean changeHeldDeck(SeatId seat, DeckComponent edited) {
        HeldDeck was = held.get(seat);
        if (was == null) {
            return false;
        }
        held.put(seat, new HeldDeck(edited, was.pool(), was.owner()));
        setChanged();
        return true;
    }

    /** Whether this game is being played for keeps. */
    public boolean playingForKeeps() {
        return forKeeps;
    }

    /**
     * Said once, when the table has agreed and the game is about to start.
     * <p>Refused on a practice table, which is the one place a stake could turn a card the
     * server invented into a card somebody keeps. Nothing about learning the controls is
     * played for keeps.
     */
    public void playForKeeps(boolean keeps) {
        if (keeps && practice) {
            LOGGER.warn("Refusing to play for keeps at {}: it is a practice game", worldPosition);
            return;
        }
        this.forKeeps = keeps;
        setChanged();
    }

    /** Puts a seat's stake into the pot, without recording whose it was. */
    public void stake(SeatId seat, java.util.List<dev.gathering.core.card.CardIdentity> cards) {
        stake(seat, cards, null);
    }

    /**
     * Puts a seat's stake into the pot, and remembers the person who put it there.
     * <p>The person, not only the chair. A pot handed back - a game voided, a table broken -
     * goes back by seat, and a staker who stood up and was replaced would have their card
     * given to whoever sat down after them. That is a card changing owner by where somebody
     * was standing, in the one feature whose point is that it really changes owner.
     * <p>Beside the pot rather than in it, where the held decks keep the same fact for the
     * same reason. The pot stays a pure record of seats and cards.
     */
    public void stake(SeatId seat, java.util.List<dev.gathering.core.card.CardIdentity> cards,
            UUID who) {
        if (seat == null || cards == null || cards.isEmpty()) {
            return;
        }
        pot = pot.with(seat, cards);
        if (who != null) {
            stakers.put(seat, who);
        }
        setChanged();
    }

    /** Who put this seat's stake in, if the table was told. */
    public Optional<UUID> stakerOf(SeatId seat) {
        return Optional.ofNullable(stakers.get(seat));
    }

    /** What is in the pot, and whose. */
    public dev.gathering.core.ante.AntePot pot() {
        return pot;
    }

    /**
     * Hands the pot over and forgets it.
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

    /**
     * Who staked, kept until the pot is settled rather than released with it.
     * <p>Released separately because the caller needs both, and needs them after the release:
     * the pot is emptied before a single card is handed anywhere, so that a settle which runs
     * twice pays out once, and the names have to outlive that by exactly one call.
     */
    public void forgetStakers() {
        stakers.clear();
        setChanged();
    }

    public Optional<DeckComponent> deckOf(SeatId seat) {
        return Optional.ofNullable(held.get(seat)).map(HeldDeck::deck);
    }

    /** The pool the deck at this seat was drafted from, if it was drafted. */
    public Optional<DraftedPool> poolOf(SeatId seat) {
        return Optional.ofNullable(held.get(seat)).map(HeldDeck::pool);
    }

    /**
     * Every deck the table is holding, in seat order.
     * <p>In seat order, which Map.copyOf would have thrown away for a hash order salted once
     * per launch. This is walked to put held decks back down between games of a set, and each
     * one puts a line in the session log - so the log's own order would have come out
     * differently every time the server started, on a record whose whole job is being the
     * thing everybody can check afterwards.
     */
    public Map<SeatId, DeckComponent> heldDecks() {
        Map<SeatId, DeckComponent> decks = new LinkedHashMap<>();
        held.forEach((seat, deck) -> decks.put(seat, deck.deck()));
        return java.util.Collections.unmodifiableMap(decks);
    }

    /**
     * Forgets what the room was last told, so the next tick sends whatever is there now.
     * <p>Called whenever the session is replaced or taken away. Left alone, a new game whose
     * revision happened to match the old one's would be silently withheld from everybody
     * standing at the table.
     */
    private void forgetWhatTheRoomWasTold() {
        this.lastAmbientRevision = -1;
        this.lastAmbientAudience = java.util.Set.of();
    }

    /**
     * Hands the decks back and forgets them, which is what the end of a match is.
     * <p>Deck and pool together in one value, because handing one back without the other is
     * the bug this shape exists to prevent - and two calls that must both happen is one call
     * somebody forgets.
     */
    public Map<SeatId, HeldDeck> releaseDecks() {
        Map<SeatId, HeldDeck> released = new LinkedHashMap<>(held);
        held.clear();
        notLegal.clear();
        setChanged();
        // Seat order, for the same reason: this decides what order decks are handed back in.
        return java.util.Collections.unmodifiableMap(released);
    }

    /**
     * A deck the table is holding, what it may be built from if it was drafted, and whose.
     * <p>All three together in one value, because handing one back without the others is the
     * bug this shape exists to prevent - and three calls that must all happen is two calls
     * somebody forgets. The owner may be null for a deck held by a world saved before decks
     * remembered whose they were; the table falls back to the chair for those.
     */
    public record HeldDeck(DeckComponent deck, DraftedPool pool, UUID owner) {

        /**
         * A deck is required; an empty pool is no pool. Saying so here rather than in each
         * place one is made is what lets "has a pool" mean one thing wherever it is asked.
         */
        public HeldDeck {
            java.util.Objects.requireNonNull(deck, "a held deck needs a deck");
            if (pool != null && pool.isEmpty()) {
                pool = null;
            }
        }
    }

    /**
     * Hands one seat's deck back and forgets it, leaving everybody else's where it is.
     * <p>For a player leaving the table, which is the moment they mean "give me my cards" and
     * which used to hand them nothing: a deck came back only when the whole match ended, and
     * ending a match is a thing the rest of the table is in the middle of.
     */
    public Optional<HeldDeck> releaseDeck(SeatId seat) {
        HeldDeck released = held.remove(seat);
        notLegal.remove(seat);
        if (released == null) {
            return Optional.empty();
        }
        setChanged();
        return Optional.of(released);
    }

    /** Records how a game went, without ending the set it belongs to. */
    public void recordMatch(MatchState updated) {
        this.match = updated;
        setChanged();
    }

    /**
     * Games of a set still being played, not yet written down for replay.
     * <p>A replay shows every card, including each library in order - and between games of a
     * set the same decks go straight back down. Written the moment a game ended, the first game
     * of a best of three showed the opponent's whole deck to anybody who opened it before the
     * second. So a set's games are held here and written when the set is over. Not saved: a
     * restart part-way through a set loses those replays, which is the safe way to be wrong.
     */
    private final java.util.List<dev.gathering.core.game.GameSession> replaysHeld = new java.util.ArrayList<>();

    /** The most games of one set held for writing; a best of five is five. */
    private static final int MOST_HELD_REPLAYS = 8;

    public void holdReplay(dev.gathering.core.game.GameSession finished) {
        if (replaysHeld.size() < MOST_HELD_REPLAYS) {
            replaysHeld.add(finished);
        }
    }

    /** The held games, handed over once and forgotten. */
    public java.util.List<dev.gathering.core.game.GameSession> releaseHeldReplays() {
        java.util.List<dev.gathering.core.game.GameSession> held = java.util.List.copyOf(replaysHeld);
        replaysHeld.clear();
        return held;
    }

    /**
     * Ends the game and the match, keeping nothing.
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
        forgetWhatTheRoomWasTold();
        this.stored = null;
        this.match = null;
        this.restoreFailed = false;
        this.formatChosen = false;
        this.forKeeps = false;
        this.practice = false;
        this.held.clear();
        setChanged();
        tellClients();
    }

    /** Ends the current game but keeps the match and the decks, for the next game of a set. */
    public void endGameKeepingMatch() {
        this.session = null;
        forgetWhatTheRoomWasTold();
        this.stored = null;
        this.restoreFailed = false;
        setChanged();
    }

    /**
     * Keeps the room's view of this table up to date.
     * <p>Moves are pushed as they happen, but somebody who walks up to a game in progress has
     * missed all of them - so the public board goes out on a slow beat as well. Slow because
     * it is ambience: a miniature that lags a second behind is a miniature, and a miniature
     * that costs a packet a tick to everyone in range is a bill nobody agreed to.
     */
    public static void serverTick(
            net.minecraft.world.level.Level level, BlockPos pos, BlockState state, TableBlockEntity table) {
        // Kept to this table. A block entity that throws in its tick crashes the whole server,
        // and a pick clock or a signup going wrong at one table is not worth every other table
        // in the world. Logged once while the same thing keeps going wrong.
        try {
            tickContained(level, pos, table);
            table.lastTickFailure = null;
        } catch (RuntimeException wentWrong) {
            String what = String.valueOf(wentWrong);
            if (!what.equals(table.lastTickFailure)) {
                table.lastTickFailure = what;
                LOGGER.error("The table at {} went wrong in its tick", pos, wentWrong);
            }
        }
    }

    private static void tickContained(
            net.minecraft.world.level.Level level, BlockPos pos, TableBlockEntity table) {
        if (table.signupIsToBeHandedBack() && level instanceof net.minecraft.server.level.ServerLevel handing) {
            dev.gathering.server.PodSignups.handBackEverything(handing, pos, table, "pod_signup_unreadable");
        }
        if (table.pod != null && table.podRecord != null && level instanceof net.minecraft.server.level.ServerLevel clocked) {
            dev.gathering.server.PickClocks.tick(clocked, pos, table);
        }
        if (++table.ambientCountdown < AMBIENT_INTERVAL_TICKS) {
            return;
        }
        table.ambientCountdown = 0;

        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        table.session().ifPresent(session -> {
            java.util.Set<UUID> seated =
                    dev.gathering.server.TableBroadcast.seatedAt(server, pos).stream()
                            .map(occupant -> occupant.player().getUUID())
                            .collect(java.util.stream.Collectors.toSet());
            // Who this push would reach: everyone in range who is not sitting at it. Scanning
            // for them is a distance check per player; building them a board is not, which is
            // why the cheap question is asked first.
            java.util.Set<UUID> audience = new java.util.LinkedHashSet<>();
            for (net.minecraft.server.level.ServerPlayer nearby
                    : dev.gathering.server.TableBroadcast.watchingNearby(server, pos)) {
                if (!seated.contains(nearby.getUUID())) {
                    audience.add(nearby.getUUID());
                }
            }
            long revision = session.revision();
            if (revision == table.lastAmbientRevision && audience.equals(table.lastAmbientAudience)) {
                // Same board, same room. Sending it again would tell nobody anything.
                return;
            }
            table.lastAmbientRevision = revision;
            table.lastAmbientAudience = java.util.Set.copyOf(audience);
            dev.gathering.server.TableBroadcast.sendAmbient(server, pos, session, seated);
        });
    }

    public Optional<DyeColor> felt() {
        return Optional.ofNullable(felt);
    }

    /**
     * Dyes the felt, and says whether anything changed.
     * <p>Color is a property of the table rather than of its blockstate, which is what keeps
     * a dyeable surface from multiplying the blockstate count by sixteen for something that
     * is never asked about except when drawing.
     */
    public boolean dye(DyeColor color) {
        if (color == felt) {
            return false;
        }
        felt = color;
        setChanged();
        if (level != null) {
            // Color lives in the block entity, so a blockstate change will not tell the
            // client about it; this is what does.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    /**
     * Asks the client to draw this table again, because its color has changed.
     * <p>The felt is a tint baked into the chunk mesh when it is built, and a block entity
     * data change does not rebuild that mesh - so a table dyed while somebody watched stayed
     * the old color until something unrelated rebuilt the chunk, which looks exactly like the
     * dye not working.
     * <p>All four quarters: one block entity owns a two-by-two table and every quarter's tint
     * asks it for the color.
     * <p>Here rather than in a packet handler, because {@code onDataPacket} is a NeoForge
     * extension and this class is loader-free. Both loaders reach {@code loadAdditional} for a
     * block entity update, so it catches the update either way.
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
     * <p>Only the felt color: seat claims are player identity and nothing on a client draws
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
        tag.putBoolean(COMMANDER_DAMAGE_KEY, countsCommanderDamage());
        tag.putBoolean(APART_KEY, playsApart);
        tag.putBoolean(TURNED_KEY, turned);
        tag.putInt(LABEL_NUMBER_KEY, eventTable);
        tag.putString(LABEL_LINE_KEY, eventLine);
        tag.putLong(LABEL_ENDS_KEY, eventEnds);
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
        loadedFromSave = tag.contains(SAVE_KEY) ? tag.getLong(SAVE_KEY) : -1;
        loadedFromTick = tag.contains(SAVE_TICK_KEY) ? tag.getLong(SAVE_TICK_KEY) : -1;
        if (tag.hasUUID(CUSTODY_KEY)) {
            custody = tag.getUUID(CUSTODY_KEY);
            if (level != null && !level.isClientSide()) {
                dev.gathering.server.TableCustody.loaded(this);
            }
        }
        DyeColor was = felt;
        felt = tag.contains(FELT_KEY) ? DyeColor.byName(tag.getString(FELT_KEY), null) : null;
        if (was != felt) {
            redrawTheFelt();
        }
        commandZone = tag.getBoolean(COMMAND_ZONE_KEY);
        commanderDamage = tag.getBoolean(COMMANDER_DAMAGE_KEY);
        formatChosen = tag.getBoolean(FORMAT_CHOSEN_KEY);
        playsApart = tag.getBoolean(APART_KEY);
        turned = tag.getBoolean(TURNED_KEY);
        if (tag.contains(LABEL_NUMBER_KEY)) {
            eventTable = tag.getInt(LABEL_NUMBER_KEY);
            eventLine = tag.getString(LABEL_LINE_KEY);
            eventEnds = tag.getLong(LABEL_ENDS_KEY);
        }

        session = null;
        forgetWhatTheRoomWasTold();
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
        podRecord = null;
        if (tag.contains(POD_RECORD_KEY)) {
            try {
                podRecord = dev.gathering.core.draft.PodRecord.read(tag.getByteArray(POD_RECORD_KEY));
            } catch (IOException broken) {
                LOGGER.error("The record of an event's packs at {} will not load: {}",
                        worldPosition, broken.getMessage());
            }
        }
        pod = null;
        if (tag.contains(POD_KEY)) {
            try {
                pod = DraftPodCodec.read(tag.getByteArray(POD_KEY));
            } catch (IOException broken) {
                LOGGER.error("The draft at {} will not load: {}", worldPosition, broken.getMessage());
            }
        }

        // Every pack that reads is kept, and a signup whose settings no longer make sense is
        // kept too, only to be handed back: the packs in it are people's packs.
        signup = null;
        signupToHandBack = false;
        if (tag.contains(SIGNUP_KEY)) {
            try {
                PodSignup.Loaded loaded = PodSignup.read(tag.getCompound(SIGNUP_KEY), registries,
                        contributor -> LOGGER.error("A pack held at {} for {} will not load",
                                worldPosition, contributor));
                signup = loaded.signup();
                signupToHandBack = loaded.settingsBroken();
            } catch (RuntimeException unreadable) {
                LOGGER.error("The signup at {} will not load: {}", worldPosition, unreadable.toString());
            }
        }

        held.clear();
        notLegal.clear();
        ListTag heldDecks = tag.getList(DECKS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < heldDecks.size(); index++) {
            CompoundTag entry = heldDecks.getCompound(index);
            SeatId seat = new SeatId(entry.getInt(DECK_SEAT_KEY));
            // The deck is the record. A deck that will not read is logged and skipped, and
            // its pool and owner with it: they describe a deck this table does not have.
            // Not written back at the next save, which is how it was before this was one
            // value too.
            DeckComponent deck = DeckComponent.CODEC
                    .parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get(DECK_KEY))
                    .resultOrPartial(problem -> LOGGER.error(
                            "A deck held at {} will not load: {}", worldPosition, problem))
                    .orElse(null);
            if (deck == null) {
                continue;
            }
            // Absent in a world saved before decks remembered whose they were. Such a deck
            // goes back to the chair - see TableSessions#returnDecks.
            UUID owner = entry.hasUUID(DECK_OWNER_KEY) ? entry.getUUID(DECK_OWNER_KEY) : null;
            // A pool that will not read leaves the deck held without one, as it always has:
            // the deck is somebody's cards, and losing the limited check is the lesser loss.
            DraftedPool pool = !entry.contains(POOL_KEY) ? null : DraftedPool.CODEC
                    .parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get(POOL_KEY))
                    .resultOrPartial(problem -> LOGGER.error(
                            "A pool held at {} will not load: {}", worldPosition, problem))
                    .orElse(null);
            held.put(seat, new HeldDeck(deck, pool, owner));
            if (entry.contains(NOT_LEGAL_IN_KEY)) {
                java.util.List<String> problems = new java.util.ArrayList<>();
                ListTag lines = entry.getList(NOT_LEGAL_PROBLEMS_KEY, Tag.TAG_STRING);
                for (int line = 0; line < lines.size(); line++) {
                    problems.add(lines.getString(line));
                }
                notLegal.put(seat, new NotLegal(entry.getString(NOT_LEGAL_IN_KEY), problems, entry.getInt(NOT_LEGAL_MORE_KEY)));
            }
        }

        forKeeps = tag.getBoolean(FOR_KEEPS_KEY);
        practice = tag.getBoolean(PRACTICE_KEY);
        pot = dev.gathering.core.ante.AntePot.EMPTY;
        stakers.clear();
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
            if (entry.hasUUID(ANTE_STAKER_KEY)) {
                stakers.put(seat, entry.getUUID(ANTE_STAKER_KEY));
            }
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

    /**
     * This table's identity, kept in its saved data so it travels with it. Unguessable rather than
     * from the level's random: it names the table, it decides nothing in play - see TableCustody.
     */
    private UUID custody;
    private static final String CUSTODY_KEY = "custody";
    /** Which of this table's saves was the latest, and in which tick - see {@link #wasCarriedTo}. */
    private long lastSave = -1;
    private long lastSaveTick = -1;
    /** Which save a table was loaded from, and in which tick that save was made. */
    private long loadedFromSave = -1;
    private long loadedFromTick = -1;
    private static final String SAVE_KEY = "custody_save";
    private static final String SAVE_TICK_KEY = "custody_tick";
    private static final java.util.concurrent.atomic.AtomicLong SAVES = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Whether {@code copy} is this table, carried: loaded from this table's own latest save, made this
     * very tick. A mover writes a table down, loads the copy and clears the old blocks in one go. A copy
     * made any other way - a creative pick with its data, /clone, a pasted structure - shares the
     * identity but not that, and must not stop the table it copied from handing back what it holds.
     */
    public boolean wasCarriedTo(TableBlockEntity copy) {
        return level != null && lastSave >= 0 && copy.loadedFromSave == lastSave
                && copy.loadedFromTick == lastSaveTick && lastSaveTick == level.getGameTime();
    }

    public UUID custody() {
        if (custody == null) {
            custody = UUID.randomUUID();
            if (level != null && !level.isClientSide()) {
                dev.gathering.server.TableCustody.loaded(this);
            }
        }
        return custody;
    }

    /**
     * Put into a level - loaded with its chunk, placed, or carried there. Counted by its identity
     * only once it has one: a mover may put a table into its new place before reading its saved data
     * into it (Sable does), and an identity made up here would be a stranger's.
     */
    @Override
    public void setLevel(net.minecraft.world.level.Level into) {
        super.setLevel(into);
        if (!into.isClientSide() && custody != null) {
            dev.gathering.server.TableCustody.loaded(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            dev.gathering.server.TableCustody.gone(this);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(CUSTODY_KEY, custody());
        lastSave = SAVES.incrementAndGet();
        lastSaveTick = level == null ? -1 : level.getGameTime();
        tag.putLong(SAVE_KEY, lastSave);
        tag.putLong(SAVE_TICK_KEY, lastSaveTick);
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
        // Written only when true, so a world that has never seen a practice game has nothing
        // about one in it. Read back below; a save from before this existed reads false,
        // which is correct for every game in it.
        if (practice) {
            tag.putBoolean(PRACTICE_KEY, true);
        }
        // In the open. A pod holds every pack in the ring, so it is exactly as secret as a
        // library - but it never leaves the server: what a drafter is sent is a view, built
        // fresh each time, and there is no path from these bytes to a client.
        if (pod != null) {
            tag.putByteArray(POD_KEY, DraftPodCodec.write(pod));
        }
        if (signup != null) {
            tag.put(SIGNUP_KEY, signup.write(registries));
        }
        if (podRecord != null) {
            tag.putByteArray(POD_RECORD_KEY, dev.gathering.core.draft.PodRecord.write(podRecord));
        }
        if (playsApart) {
            tag.putBoolean(APART_KEY, true);
        }
        if (turned) {
            tag.putBoolean(TURNED_KEY, true);
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
            UUID who = stakers.get(seat);
            if (who != null) {
                entry.putUUID(ANTE_STAKER_KEY, who);
            }
            staked.add(entry);
        });
        tag.put(ANTE_KEY, staked);
    }

    /** Writes the held decks down. Losing one to a server restart is losing somebody's deck. */
    private void writeDecks(CompoundTag tag) {
        ListTag written = new ListTag();
        held.forEach((seat, holding) -> DeckComponent.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, holding.deck())
                .resultOrPartial(problem -> LOGGER.error(
                        "A deck held at {} will not save: {}", worldPosition, problem))
                .ifPresent(encoded -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt(DECK_SEAT_KEY, seat.index());
                    UUID owner = holding.owner();
                    if (owner != null) {
                        entry.putUUID(DECK_OWNER_KEY, owner);
                    }
                    entry.put(DECK_KEY, encoded);
                    NotLegal why = notLegal.get(seat);
                    if (why != null) {
                        entry.putString(NOT_LEGAL_IN_KEY, why.format());
                        ListTag lines = new ListTag();
                        why.problems().forEach(line -> lines.add(net.minecraft.nbt.StringTag.valueOf(line)));
                        entry.put(NOT_LEGAL_PROBLEMS_KEY, lines);
                        entry.putInt(NOT_LEGAL_MORE_KEY, why.more());
                    }
                    DraftedPool pool = holding.pool();
                    if (pool != null) {
                        DraftedPool.CODEC
                                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, pool)
                                .resultOrPartial(problem -> LOGGER.error(
                                        "A pool held at {} will not save: {}",
                                        worldPosition, problem))
                                .ifPresent(encodedPool -> entry.put(POOL_KEY, encodedPool));
                    }
                    written.add(entry);
                }));
        tag.put(DECKS_KEY, written);
    }

    /**
     * Writes the game down, sealed.
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
        if (match.rules().needsAWinner()) {
            tag.putBoolean(NEEDS_A_WINNER_KEY, true);
        }
        tag.putInt(GAME_NUMBER_KEY, match.gameNumber());

        ListTag wins = new ListTag();
        match.wins().forEach((seat, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("seat", seat.index());
            entry.putInt("won", count);
            wins.add(entry);
        });
        tag.put(WINS_KEY, wins);
        if (match.lastGameWinner() != null) {
            tag.putInt(LAST_WINNER_KEY, match.lastGameWinner().index());
        }
        if (match.choseForDrawnGame() != null) {
            tag.putInt(DRAWN_GAME_CHOOSER_KEY, match.choseForDrawnGame().index());
        }
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
            MatchRules rules = new MatchRules(format.get(), tag.getInt(BEST_OF_KEY), tag.getBoolean(NEEDS_A_WINNER_KEY));
            java.util.Map<dev.gathering.core.game.SeatId, Integer> wins = new java.util.LinkedHashMap<>();
            ListTag stored = tag.getList(WINS_KEY, Tag.TAG_COMPOUND);
            for (int index = 0; index < stored.size(); index++) {
                CompoundTag entry = stored.getCompound(index);
                wins.put(new dev.gathering.core.game.SeatId(entry.getInt("seat")), entry.getInt("won"));
            }
            return new MatchState(rules, wins, Math.max(1, tag.getInt(GAME_NUMBER_KEY)),
                    tag.contains(LAST_WINNER_KEY) ? new dev.gathering.core.game.SeatId(tag.getInt(LAST_WINNER_KEY)) : null,
                    tag.contains(DRAWN_GAME_CHOOSER_KEY) ? new dev.gathering.core.game.SeatId(tag.getInt(DRAWN_GAME_CHOOSER_KEY)) : null);
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
