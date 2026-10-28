package dev.gathering.core.game;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.event.LogEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One game at one table: the authoritative state object.
 * <p>Event-sourced. Every verb is an event, the board is the fold of the standing events,
 * and the log is append-only. Three things the design brief asks for therefore did not have
 * to be built at all - they are consequences of that one decision:
 *
 * <ul>
 *   <li><b>Undo</b> is marking entries undone and re-folding, not a parallel stack of inverse
 *       operations that can disagree with the board.</li>
 *   <li><b>Replay</b> is the log plus the seed, so a finished game reproduces exactly.</li>
 *   <li><b>Persistence</b> is the same two things, which is why a session survives a chunk
 *       unload and a server restart without a bespoke save format.</li>
 * </ul>
 * <p>Mutable at the edges, immutable at the center: this object holds a growing log and a
 * cached {@link GameState}, and the state itself is a value that is never modified.
 * <p>Not thread-safe, and not meant to be. A session belongs to the server thread; the card
 * pipeline's I/O happens elsewhere and arrives as events.
 */
public final class GameSession {

    private final SessionSeed seed;
    private final GameState initial;
    private final List<SessionRecord> records = new ArrayList<>();

    /**
     * The public log, built as events arrive.
     * <p>Built here rather than derived on demand because each line has to be described
     * against the board <em>as it was before its own event</em> - "Chris moved Sol Ring to the
     * graveyard" needs the Sol Ring to still be on the battlefield to be nameable - and that
     * board is only free at the moment the event is applied. Rebuilding it later means folding
     * the whole log again, which {@link #rebuildLog} does after a rewind and nowhere else.
     */
    private final List<LogEntry> log = new ArrayList<>();

    /**
     * The life every seat opened on. Kept beside the board rather than read back off it,
     * because a seat's life moves the moment play starts and a replay has to be able to
     * rebuild the same opening board from the log alone.
     */
    private final int startingLife;

    private UndoMode undoMode;
    private GameState state;
    private long nextSequence = 1;

    private GameSession(SessionSeed seed, GameState initial, int startingLife, UndoMode undoMode) {
        this.seed = seed;
        this.initial = initial;
        this.state = initial;
        this.startingLife = startingLife;
        this.undoMode = undoMode;
    }

    public static GameSession create(List<SeatId> seats, int startingLife, SessionSeed seed, UndoMode undoMode) {
        return new GameSession(seed, GameState.empty(seats, startingLife), startingLife, undoMode);
    }

    /**
     * A session put back together from a stored log.
     * <p>Nothing is re-run and nothing is re-authorized: the log is what happened, and the
     * board is folded from it exactly as it was. Re-authorizing would be worse than
     * pointless, because the state each decision was made against is gone.
     * <p>The next sequence number continues past the highest one restored rather than from
     * the count, so a log that has had entries undone does not hand out a number twice.
     */
    public static GameSession restore(
            List<SeatId> seats,
            int startingLife,
            SessionSeed seed,
            UndoMode undoMode,
            List<SessionRecord> records) {
        GameSession session = create(seats, startingLife, seed, undoMode);
        session.records.addAll(records);

        long highest = 0;
        for (SessionRecord record : records) {
            // Every record kind counts: an undo consumes a sequence number too, and maxing
            // over events alone handed the number a stored UndoRecord holds out again.
            highest = Math.max(highest, record.sequence());
        }
        session.nextSequence = highest + 1;
        // The log and the board out of one walk. They are folded the same way past the same
        // events, so doing them separately was folding the whole game twice - which a replay
        // pays for on every frame anybody scrubs to.
        session.state = session.rebuildLogAndFold();
        return session;
    }

    /**
     * Applies more of a stored log to a session already folded from the front of it.
     * <p>For watching a game back. A replay's frame is the board at step N, and the board at
     * step N is the board at step N-1 with one more record on it - so stepping forward costs
     * one event rather than a fold of the whole game, which at four frames a second is the
     * difference between a watcher costing a server nothing and costing it a third of a tick.
     * <p>Exact rather than approximate, because a stored record already carries whether it is
     * still standing: an undo was resolved when the game was played, and nothing here has to
     * decide it again. Folding forward past these is the same walk {@link #restore} makes,
     * stopped earlier and continued.
     * <p>Only ever the records that follow the ones already applied, in order. Nothing checks
     * that, because the only caller is a replay reading its own file front to back.
     */
    public void extendWith(List<SessionRecord> more) {
        for (SessionRecord record : more) {
            records.add(record);
            nextSequence = Math.max(nextSequence, record.sequence() + 1);
            if (record instanceof SessionRecord.UndoRecord rewind) {
                state = whereTheCountersHadGot(state, rewind);
                continue;
            }
            if (!(record instanceof SessionRecord.EventRecord event)) {
                continue;
            }
            // Described against the board as it was before its own event, exactly as the
            // whole-log walk describes it - which is why the line is written before the fold.
            log.add(LogEntry.of(event.sequence(), event.event().describe(state),
                    !event.isStanding()));
            if (event.isStanding()) {
                state = GameFold.apply(state, event.event(), seed);
            }
        }
    }

    /** A solo table. Goldfishing needs no other humans, and ships in the first playable phase. */
    public static GameSession sandbox(int startingLife) {
        return create(List.of(SeatId.of(0)), startingLife, SessionSeed.random(), UndoMode.shippedDefault());
    }

    // ------------------------------------------------------------ submitting

    /**
     * Runs a verb.
     * <p>Rejection means the action was not permitted - which, per {@link Authorization},
     * means it would have revealed hidden information to the actor - or that it named a card
     * or seat this session no longer has, which is what a lagged click on a just-removed
     * token looks like by the time it arrives. It never means the play was illegal, because
     * the mod has no opinion about that.
     */
    public Result submit(GameEvent event) {
        if (records.size() >= MOST_RECORDS && !(event instanceof GameEvent.SessionEnded)
                && !(event instanceof GameEvent.SeatTaken) && !(event instanceof GameEvent.SeatReleased)) {
            // A game this long is a client writing moves in a loop, not people playing: the
            // longest real game is a few thousand. Past this the table stops taking moves, and
            // the saved game stays one that reads back - the reader refuses anything past
            // twice this, and a table whose game would not read was a table nobody could use.
            return new Result.Rejected("This game's record is full. End the game to play on.");
        }
        Optional<String> denial = Authorization.denialFor(state, event);
        if (denial.isPresent()) {
            return new Result.Rejected(denial.get());
        }
        // Folded before anything is appended. The log is the game: an event that had already
        // been written down when its fold threw was a record nothing could ever fold again -
        // undo broke, restore broke, and the save carried the poison into the next launch.
        // Now a stale event is an answer, not a wound.
        GameState folded;
        try {
            folded = GameFold.apply(state, event, seed);
        } catch (IllegalArgumentException stale) {
            return new Result.Rejected(stale.getMessage());
        }
        SessionRecord.EventRecord record = new SessionRecord.EventRecord(nextSequence++, event, false);
        records.add(record);
        // Described against the board this event is about to change, not the one it made.
        log.add(LogEntry.of(record.sequence(), event.describe(state), false));
        state = folded;
        return new Result.Accepted(record, state);
    }

    // ----------------------------------------------------------------- undo

    /**
     * What the rules make of rewinding the last {@code actionCount} standing events.
     * <p>Asked separately from performing it so a table can be shown "this needs everyone to
     * agree" before anybody commits to anything.
     */
    public UndoDecision evaluateUndo(SeatId requester, int actionCount) {
        if (undoMode == UndoMode.OFF) {
            return UndoDecision.denied("Undo is switched off at this table.");
        }
        if (actionCount < 1) {
            return UndoDecision.denied("Nothing to undo.");
        }
        // Occupied, not merely a seat that exists here: hasSeat asks whether this is one of the
        // table's seats, and the refusal says somebody is sitting in it.
        if (!state.hasSeat(requester) || !state.seatState(requester).isOccupied()) {
            return UndoDecision.denied("Only seated players can rewind.");
        }
        // The end is the one line that is not rewound. The match has been scored, the decks
        // handed back and the pot paid by the time this could arrive; a rewind past it would
        // put a board back on a table whose game is over and whose cards have gone home.
        if (state.ended()) {
            return UndoDecision.denied("The game is over; nothing rewinds now.");
        }

        List<Integer> reaching = standingTailIndices(actionCount);
        List<SessionRecord.EventRecord> target = standingTail(reaching);
        if (target.size() < actionCount) {
            return UndoDecision.denied("There are not that many actions to undo.");
        }

        // Not a player's to take back, however many agree: the table's own setup. Each of these
        // has a second store the session cannot rewind - a seat is a chair claimed on the block, a
        // deck is the item the table is holding - or was the server's to decide. Rewinding the deck
        // left the seat with no library while the table kept the deck, refusing every deck put down
        // there for the rest of the game; rewinding a seat left a player in a chair the game said
        // was empty.
        Optional<String> setUp = theTablesOwnSetUp(reaching);
        if (setUp.isPresent()) {
            return UndoDecision.denied(setUp.get());
        }

        // A shuffle cannot be un-shuffled. Taking one back puts a library back in an order its
        // owner can know - the decklist it went down in, or the order a search has just shown them -
        // and knowing the order of your own library is not a thing anybody is entitled to. So it
        // is the same boundary as a reveal, in every mode. The cost is that a shuffle pressed by
        // mistake is one more thing a table has to agree to take back. Asked before the reveals,
        // which fold the whole game to answer, because this is answered from the range alone.
        if (target.stream().anyMatch(record -> record.event() instanceof GameEvent.LibraryShuffled)) {
            return UndoDecision.needsUnanimousConsent("That rewind takes back a shuffle, so everyone has to agree.");
        }

        // And the hard boundary, in every mode: a seen card cannot be un-seen.
        Optional<SessionRecord.EventRecord> revealing = firstRevealing(target);
        if (revealing.isPresent()) {
            return UndoDecision.needsUnanimousConsent(
                    "That rewind crosses an action that revealed information, so everyone has to agree.");
        }

        if (undoMode == UndoMode.UNANIMOUS) {
            return UndoDecision.needsUnanimousConsent("This table requires everyone to agree to a rewind.");
        }

        boolean allTheirs = target.stream().allMatch(record -> record.event().actor().equals(requester));
        return allTheirs
                ? UndoDecision.allowed()
                : UndoDecision.needsUnanimousConsent("That rewind would undo somebody else's action.");
    }

    /** The most records a game takes. Half of what a saved game may hold when it is read back. */
    public static final int MOST_RECORDS = 50_000;

    /**
     * Performs a rewind.
     *
     * @param consents the seats that have agreed, needed only when the decision asks for it
     */
    public Result undo(SeatId requester, int actionCount, Collection<SeatId> consents) {
        if (records.size() >= MOST_RECORDS) {
            return new Result.Rejected("This game's record is full. End the game to play on.");
        }
        UndoDecision decision = evaluateUndo(requester, actionCount);

        boolean unanimous = false;
        if (decision instanceof UndoDecision.Denied denied) {
            return new Result.Rejected(denied.reason());
        }
        if (decision instanceof UndoDecision.NeedsUnanimousConsent needed) {
            Set<SeatId> agreed = new LinkedHashSet<>(consents);
            // Nobody to agree is not everybody agreeing. containsAll of an empty table is vacuously
            // true, and a seat released while its board stays on the felt leaves the table occupied
            // by nobody - so a rewind across a revealed card went through unopposed in exactly the
            // window where there was no one to oppose it. The rule this guards says it is not a
            // setting; that has to include the case where the room is empty.
            if (occupiedSeats().isEmpty() || !agreed.containsAll(occupiedSeats())) {
                return new Result.Rejected(needed.reason());
            }
            unanimous = true;
        }

        for (int index : standingTailIndices(actionCount)) {
            records.set(index, ((SessionRecord.EventRecord) records.get(index)).asUndone());
        }
        // With where the counters had got to, before the fold goes back past them.
        SessionRecord.UndoRecord undoRecord = new SessionRecord.UndoRecord(nextSequence++, requester, actionCount,
                unanimous, state.nextCardId(), state.shuffleOrdinal(), state.markerOrdinal());
        records.add(undoRecord);

        // One walk, not two. Rewriting the log has to fold the game anyway - every line
        // describes the board before its own event - so this takes the board that walk
        // arrived at rather than folding the whole game a second time to ask for it.
        state = rebuildLogAndFold();
        return new Result.Accepted(undoRecord, state);
    }

    // ------------------------------------------------------------- accessors

    public GameState state() {
        return state;
    }

    public SessionSeed seed() {
        return seed;
    }

    /** The life every seat opened on, as the format asked for it. */
    public int startingLife() {
        return startingLife;
    }

    public UndoMode undoMode() {
        return undoMode;
    }

    public void setUndoMode(UndoMode mode) {
        this.undoMode = mode;
    }

    /**
     * A number that changes whenever this session does, and costs nothing to ask for.
     * <p>The next sequence number, which every submitted event and every undo consumes. So a
     * caller holding one from a moment ago can tell "nothing has happened here" from "something
     * has" without comparing states or copying the log.
     * <p>{@link #records()} would answer the same question and hands back a copy of the whole
     * log to do it, which is fine once and ruinous on a timer - the table's public board is
     * pushed to everyone nearby every two seconds, and asking that way would allocate a copy of
     * every record in the game, per table, per push.
     * <p>Monotonic within one session and meaningless across two, so a caller comparing it must
     * forget its old value when the session is replaced.
     */
    public long revision() {
        return nextSequence;
    }

    /** The whole record, undone entries included, in order. Public by default. */
    public List<SessionRecord> records() {
        return List.copyOf(records);
    }

    /**
     * The public log, oldest first, rewound entries included and marked as such.
     * <p>Rewound lines stay. A log that quietly loses entries when somebody undoes something
     * is not a record of what happened, and "what happened" is the entire job.
     */
    public List<LogEntry> log() {
        return List.copyOf(log);
    }

    /** The last {@code count} lines, which is what a table is actually shown. */
    public List<LogEntry> recentLog(int count) {
        int from = Math.max(0, log.size() - Math.max(0, count));
        return List.copyOf(log.subList(from, log.size()));
    }

    /**
     * Writes the log again and hands back the board it walked past on the way.
     * <p>The log is rewritten only after a rewind, and only because a line describes the board
     * before its own event: undoing something in the middle changes what every later line was
     * describing.
     * <p>One walk rather than two. Describing a line needs the board as it was before its own
     * event, so this fold has to happen anyway - and every caller that rebuilds the log also
     * wants the state, which used to mean folding the whole game twice. A restored session of
     * four thousand events paid for that twice on every replay frame somebody scrubbed to.
     */
    private GameState rebuildLogAndFold() {
        log.clear();
        GameState walking = initial;
        for (SessionRecord record : records) {
            if (record instanceof SessionRecord.UndoRecord rewind) {
                walking = whereTheCountersHadGot(walking, rewind);
                continue;
            }
            if (!(record instanceof SessionRecord.EventRecord eventRecord)) {
                continue;
            }
            log.add(LogEntry.of(
                    eventRecord.sequence(), eventRecord.event().describe(walking), !eventRecord.isStanding()));
            if (eventRecord.isStanding()) {
                walking = GameFold.apply(walking, eventRecord.event(), seed);
            }
        }
        return walking;
    }

    /**
     * Recomputes the board from the log.
     * <p>Cheap enough to call whenever a doubt arises: a few hundred cards folded over a few
     * thousand events is milliseconds. If this ever disagrees with the incremental state, the
     * incremental state is the one that is wrong.
     * <p>The standing events, and the rewinds between them for where the counters had got to - see
     * {@link #whereTheCountersHadGot}.
     */
    public GameState refold() {
        GameState walking = initial;
        for (SessionRecord record : records) {
            if (record instanceof SessionRecord.UndoRecord rewind) {
                walking = whereTheCountersHadGot(walking, rewind);
            } else if (record instanceof SessionRecord.EventRecord event && event.isStanding()) {
                walking = GameFold.apply(walking, event.event(), seed);
            }
        }
        return walking;
    }

    /**
     * The board after a rewind, as far as its counters go: they carry on from where they had got to.
     * <p>A rewind folds the game again from the beginning, and three counters that only ever go up are
     * part of the board, so they went back as well. The next token made was given the id of the one
     * just rewound, and a card menu still open on it, a selection holding it, a removal or a tap on its
     * way all landed on the new card. The next shuffle repeated the permutation of the one taken back,
     * so after an agreed rewind of a shuffle and a draw, shuffling again put the card just seen back on
     * top. And the next card turned face down was given the marker of the one rewound, which the fresh
     * marker exists to prevent. Kept on the rewind's own record, so every walk over the log - this one,
     * a restore, a replay stepping forward - agrees, and a log written before keeps nothing and folds
     * as it always did.
     */
    private static GameState whereTheCountersHadGot(GameState walking, SessionRecord.UndoRecord rewind) {
        GameState kept = walking;
        if (rewind.idsFrom() > kept.nextCardId()) {
            kept = kept.withNextCardId(rewind.idsFrom());
        }
        if (rewind.shufflesFrom() > kept.shuffleOrdinal()) {
            kept = kept.withShuffleOrdinal(rewind.shufflesFrom());
        }
        if (rewind.markersFrom() > kept.markerOrdinal()) {
            kept = kept.withMarkerOrdinal(rewind.markersFrom());
        }
        return kept;
    }

    private List<SeatId> occupiedSeats() {
        return state.seats().stream().filter(seat -> state.seatState(seat).isOccupied()).toList();
    }

    /** The standing event records at these positions, oldest first. */
    private List<SessionRecord.EventRecord> standingTail(List<Integer> indices) {
        List<SessionRecord.EventRecord> standing = new ArrayList<>();
        for (int index : indices) {
            standing.add((SessionRecord.EventRecord) records.get(index));
        }
        return standing;
    }

    /**
     * Why a rewind over the records at these positions would take back part of the table's own
     * setup, or empty when it would not.
     * <p>A seat taken or given up, a deck put down, and who goes first. The shuffle a deck goes
     * down with counts as part of putting it down: the server writes the two together under the
     * player's seat, and taking back only the shuffle is taking back the half that hid the order
     * the deck was typed in. It is told apart by what stands immediately before it - the same
     * seat's deck - because rewinding that shuffle is what would put the decklist order back,
     * whoever wrote it.
     * <p>A die, a coin or the planar die is the server's to write too, and stays a player's to
     * take back: undo takes back the asking, and changes nothing on the board (see GameFold).
     */
    private Optional<String> theTablesOwnSetUp(List<Integer> indices) {
        for (int index : indices) {
            GameEvent event = ((SessionRecord.EventRecord) records.get(index)).event();
            switch (event) {
                case GameEvent.SeatTaken ignored -> {
                    return Optional.of(NOT_REWOUND_SEAT);
                }
                case GameEvent.SeatReleased ignored -> {
                    return Optional.of(NOT_REWOUND_SEAT);
                }
                case GameEvent.DeckLoaded ignored -> {
                    return Optional.of(NOT_REWOUND_DECK);
                }
                case GameEvent.StartingPlayerChosen ignored -> {
                    return Optional.of(NOT_REWOUND_FIRST_PLAYER);
                }
                case GameEvent.LibraryShuffled shuffled -> {
                    if (shuffled.actor().equals(shuffled.seat())
                            && standingEventBefore(index) instanceof GameEvent.DeckLoaded loaded
                            && loaded.actor().equals(shuffled.seat())) {
                        return Optional.of(NOT_REWOUND_DECK);
                    }
                }
                default -> {
                }
            }
        }
        return Optional.empty();
    }

    /** The standing event just before this position in the record, or null at the start. */
    private GameEvent standingEventBefore(int index) {
        for (int earlier = index - 1; earlier >= 0; earlier--) {
            if (records.get(earlier) instanceof SessionRecord.EventRecord record && record.isStanding()) {
                return record.event();
            }
        }
        return null;
    }

    static final String NOT_REWOUND_SEAT = "Sitting down and standing up are not rewound.";
    static final String NOT_REWOUND_DECK = "A deck put down stays down.";
    static final String NOT_REWOUND_FIRST_PLAYER = "Who goes first is not rewound.";

    /**
     * Where those records are, oldest first.
     * <p>Positions rather than the records themselves, for the one caller that has to write
     * them back: it looked each one up again with {@code indexOf}, which walks the whole
     * record from the front - so undoing ten actions of a four-thousand-event game was ten
     * more passes over it.
     */
    private List<Integer> standingTailIndices(int count) {
        List<Integer> found = new ArrayList<>();
        for (int index = records.size() - 1; index >= 0 && found.size() < count; index--) {
            if (records.get(index) instanceof SessionRecord.EventRecord record && record.isStanding()) {
                found.add(index);
            }
        }
        java.util.Collections.reverse(found);
        return found;
    }

    /**
     * Whether any event in the range revealed something, judged against the board as it was
     * at the time rather than as it is now.
     */
    private Optional<SessionRecord.EventRecord> firstRevealing(List<SessionRecord.EventRecord> target) {
        GameState walking = initial;
        Set<Long> interesting = new LinkedHashSet<>();
        target.forEach(record -> interesting.add(record.sequence()));

        for (SessionRecord record : records) {
            if (record instanceof SessionRecord.UndoRecord rewind) {
                walking = whereTheCountersHadGot(walking, rewind);
                continue;
            }
            if (!(record instanceof SessionRecord.EventRecord eventRecord) || !eventRecord.isStanding()) {
                continue;
            }
            if (interesting.contains(eventRecord.sequence())
                    && eventRecord.event().revealsInformation(walking)) {
                return Optional.of(eventRecord);
            }
            walking = GameFold.apply(walking, eventRecord.event(), seed);
        }
        return Optional.empty();
    }

    /** What came of a submission. */
    public sealed interface Result {

        record Accepted(SessionRecord record, GameState state) implements Result {
        }

        record Rejected(String reason) implements Result {
        }

        default boolean isAccepted() {
            return this instanceof Accepted;
        }
    }
}
