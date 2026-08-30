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
 *
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
 *
 * <p>Mutable at the edges, immutable at the center: this object holds a growing log and a
 * cached {@link GameState}, and the state itself is a value that is never modified.
 *
 * <p>Not thread-safe, and not meant to be. A session belongs to the server thread; the card
 * pipeline's I/O happens elsewhere and arrives as events.
 */
public final class GameSession {

    private final SessionSeed seed;
    private final GameState initial;
    private final List<SessionRecord> records = new ArrayList<>();

    /**
     * The public log, built as events arrive.
     *
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
     *
     * <p>Nothing is re-run and nothing is re-authorised: the log is what happened, and the
     * board is folded from it exactly as it was. Re-authorising would be worse than
     * pointless, because the state each decision was made against is gone.
     *
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
     *
     * <p>For watching a game back. A replay's frame is the board at step N, and the board at
     * step N is the board at step N-1 with one more record on it - so stepping forward costs
     * one event rather than a fold of the whole game, which at four frames a second is the
     * difference between a watcher costing a server nothing and costing it a third of a tick.
     *
     * <p>Exact rather than approximate, because a stored record already carries whether it is
     * still standing: an undo was resolved when the game was played, and nothing here has to
     * decide it again. Folding forward past these is the same walk {@link #restore} makes,
     * stopped earlier and continued.
     *
     * <p>Only ever the records that follow the ones already applied, in order. Nothing checks
     * that, because the only caller is a replay reading its own file front to back.
     */
    public void extendWith(List<SessionRecord> more) {
        for (SessionRecord record : more) {
            records.add(record);
            nextSequence = Math.max(nextSequence, record.sequence() + 1);
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
     *
     * <p>Rejection means the action was not permitted - which, per {@link Authorization},
     * means it would have revealed hidden information to the actor - or that it named a card
     * or seat this session no longer has, which is what a lagged click on a just-removed
     * token looks like by the time it arrives. It never means the play was illegal, because
     * the mod has no opinion about that.
     */
    public Result submit(GameEvent event) {
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
     *
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
        if (!state.hasSeat(requester)) {
            return UndoDecision.denied("Only seated players can rewind.");
        }

        List<SessionRecord.EventRecord> target = standingTail(actionCount);
        if (target.size() < actionCount) {
            return UndoDecision.denied("There are not that many actions to undo.");
        }

        // The hard boundary, in every mode: a seen card cannot be un-seen.
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

    /**
     * Performs a rewind.
     *
     * @param consents the seats that have agreed, needed only when the decision asks for it
     */
    public Result undo(SeatId requester, int actionCount, Collection<SeatId> consents) {
        UndoDecision decision = evaluateUndo(requester, actionCount);

        boolean unanimous = false;
        if (decision instanceof UndoDecision.Denied denied) {
            return new Result.Rejected(denied.reason());
        }
        if (decision instanceof UndoDecision.NeedsUnanimousConsent needed) {
            Set<SeatId> agreed = new LinkedHashSet<>(consents);
            if (!agreed.containsAll(occupiedSeats())) {
                return new Result.Rejected(needed.reason());
            }
            unanimous = true;
        }

        for (SessionRecord.EventRecord record : standingTail(actionCount)) {
            records.set(records.indexOf(record), record.asUndone());
        }
        SessionRecord.UndoRecord undoRecord =
                new SessionRecord.UndoRecord(nextSequence++, requester, actionCount, unanimous);
        records.add(undoRecord);

        state = refold();
        rebuildLog();
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

    /** The whole record, undone entries included, in order. Public by default. */
    public List<SessionRecord> records() {
        return List.copyOf(records);
    }

    /**
     * The public log, oldest first, rewound entries included and marked as such.
     *
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
     * Rebuilds every line by folding the log again.
     *
     * <p>Only after a rewind, and only because a line describes the board before its own
     * event: undoing something in the middle changes what every later line was describing.
     */
    private void rebuildLog() {
        rebuildLogAndFold();
    }

    /**
     * Writes the log again and hands back the board it walked past on the way.
     *
     * <p>One walk rather than two. Describing a line needs the board as it was before its own
     * event, so this fold has to happen anyway - and every caller that rebuilds the log also
     * wants the state, which used to mean folding the whole game twice. A restored session of
     * four thousand events paid for that twice on every replay frame somebody scrubbed to.
     */
    private GameState rebuildLogAndFold() {
        log.clear();
        GameState walking = initial;
        for (SessionRecord record : records) {
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

    /** Just the events still standing, which is what the board is folded from. */
    public List<GameEvent> standingEvents() {
        return records.stream()
                .filter(SessionRecord.EventRecord.class::isInstance)
                .map(SessionRecord.EventRecord.class::cast)
                .filter(SessionRecord.EventRecord::isStanding)
                .map(SessionRecord.EventRecord::event)
                .toList();
    }

    /**
     * Recomputes the board from the log.
     *
     * <p>Called after every undo, and cheap enough to call whenever a doubt arises: a few
     * hundred cards folded over a few thousand events is milliseconds. If this ever disagrees
     * with the incremental state, the incremental state is the one that is wrong.
     */
    public GameState refold() {
        return GameFold.fold(initial, standingEvents(), seed);
    }

    private List<SeatId> occupiedSeats() {
        return state.seats().stream().filter(seat -> state.seatState(seat).isOccupied()).toList();
    }

    /** The last {@code count} standing event records, oldest first. */
    private List<SessionRecord.EventRecord> standingTail(int count) {
        List<SessionRecord.EventRecord> standing = new ArrayList<>();
        for (int index = records.size() - 1; index >= 0 && standing.size() < count; index--) {
            if (records.get(index) instanceof SessionRecord.EventRecord record && record.isStanding()) {
                standing.add(record);
            }
        }
        java.util.Collections.reverse(standing);
        return standing;
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
