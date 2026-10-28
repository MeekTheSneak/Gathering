package dev.gathering.client;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.tutorial.TutorialProgress;
import dev.gathering.core.tutorial.TutorialStep;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The guided first game, as this client sees it.
 * <p>The thin half. {@link TutorialProgress} holds the rules and is pure; this holds which
 * table it is happening at and turns a confirmed board into "they did the thing".
 * <p>How a step is actually completed matters and is the whole reason this is not simply
 * counted on the press:
 *
 * <ul>
 *   <li>Five steps watch the <b>board the server sent back</b>. A card is in the hand, or on
 *       the battlefield, or turned sideways, or has a counter on it, or the turn has moved.
 *       Every one of those is a fact about the authorized view, so a press the server refused
 *       advances nothing and a press it delayed advances when it lands.
 *   <li>Reading a card is the sixth, and nothing about it reaches the server. It is satisfied
 *       by this client's own inspect panel being open on a card - which cannot be a way to
 *       learn anything hidden, because the panel only ever shows a card the view already
 *       contained.
 * </ul>
 *
 * <p>Watching the board rather than the events also means the tutorial cannot be fooled by a
 * client sending itself something: what it reads is what the server said is true.
 * <p>Client thread only.
 */
public final class Tutorial {

    private static BlockPos table;
    private static TutorialProgress progress;

    /** What the board looked like when the current step went up, to notice what changed. */
    private static int handWhenTheStepBegan;
    private static int battlefieldWhenTheStepBegan;
    /**
     * What each of their cards was worth when the step went up - tapped or not, and how many counters.
     * <p>Kept per card rather than as a total, so a learner who taps one card and untaps another still
     * gets credit for the tap. See {@link dev.gathering.core.tutorial.TutorialEvidence}.
     */
    private static java.util.Map<dev.gathering.core.game.CardInstanceId, Integer> tappedWhenTheStepBegan =
            java.util.Map.of();
    private static java.util.Map<dev.gathering.core.game.CardInstanceId, Integer> countersWhenTheStepBegan =
            java.util.Map.of();
    private static dev.gathering.core.game.TurnMarker turnWhenTheStepBegan;

    /**
     * The last board the server confirmed, kept so navigation can take its own baseline.
     * <p>Restart, Back and Forward used to clear the baseline and wait for the next board to
     * set a new one - and that next board is the one carrying the player's next action. So
     * the action that satisfied the new step was spent establishing the baseline it was
     * measured against, and the instruction sat there unchanged until they did it twice. An
     * external review reproduced it: restart, draw once, feed the confirmed board, still
     * showing DRAW.
     * <p>Keeping the last confirmed board means a baseline can be taken at the moment the
     * step changes, before the next input, which is the only time it is honest.
     */
    private static GameView lastConfirmed;

    private Tutorial() {
    }

    /** Whether the guided first game is running at this table. */
    public static boolean runningAt(BlockPos where) {
        return progress != null && table != null && table.equals(where);
    }

    /** Whether it is running anywhere. */
    public static boolean running() {
        return progress != null;
    }

    /** How far through, for the overlay. */
    public static Optional<TutorialProgress> progress() {
        return Optional.ofNullable(progress);
    }

    /** The step being asked for, if any. */
    public static Optional<TutorialStep> showing() {
        return progress == null || progress.isOver()
                ? Optional.empty()
                : Optional.of(progress.showing());
    }

    /**
     * Begins, at this table.
     * <p>Called when the server has confirmed a practice game is running - not when the button
     * was pressed. A tutorial that started on the press would put its first instruction up
     * over a table that had refused to start one.
     */
    public static void beginAt(BlockPos where, GameView board) {
        table = where == null ? null : where.immutable();
        progress = TutorialProgress.start();
        remember(board);
        ClientSettings.tutorialOffered(true);
        // The server hears that it began, so it can hold a finish to having taken the time a lesson takes.
        ClientNetworking.send(new dev.gathering.network.LessonPayload(false, java.util.List.of()));
    }

    /**
     * Stops, however it ended.
     * <p>Records what really happened: finished if all six were done, skipped otherwise. The
     * two are different facts and the second one is never written as the first.
     */
    public static void stop() {
        if (progress != null) {
            if (progress.isFinished()) {
                ClientSettings.tutorialFinished(true);
                // What earns the starter boosters, on this world: see LessonRecords.
                ClientNetworking.send(new dev.gathering.network.LessonPayload(true,
                        progress.done().stream().map(Enum::name).toList()));
            } else {
                ClientSettings.tutorialSkipped(true);
            }
        }
        progress = null;
        table = null;
        turnWhenTheStepBegan = null;
        lastConfirmed = null;
    }

    /** Shows the previous instruction again. Reviews; changes nothing about the game. */
    public static void back() {
        if (progress != null) {
            progress = progress.back();
            baselineNow();
        }
    }

    /** Moves on without doing this one, for somebody who already knows it. */
    public static void forward() {
        if (progress != null) {
            progress = progress.forward();
            baselineNow();
        }
    }

    /**
     * Starts over on a board that has just been rebuilt from nothing.
     * <p>For the local demonstration, whose Restart is a new game rather than a rewound one -
     * which is what makes it work after the library has been drawn empty. The board is passed
     * in rather than waited for because the one that is about to arrive is the same one: a
     * baseline taken from the board that has just been thrown away would measure the player's
     * next action against a game nobody is playing, which is the shape of the defect an
     * external review reproduced against navigation. See {@link #baselineNow()}.
     */
    public static void restartOn(GameView fresh) {
        if (progress == null) {
            return;
        }
        progress = TutorialProgress.start();
        lastConfirmed = fresh;
        remember(fresh);
    }

    /**
     * Whether to offer the guided first game at all.
     * <p>Once. Somebody who has been asked has been asked, whatever they said, and a mod that
     * asks again every time somebody sits at a table is a mod people learn to dismiss without
     * reading. It stays reachable from the table's own menu for anybody who changes their mind.
     */
    public static boolean worthOffering() {
        return !ClientSettings.tutorialOffered()
                && !ClientSettings.tutorialFinished()
                && !ClientSettings.tutorialSkipped();
    }

    /**
     * Takes a board the server has confirmed, and sees whether the step on screen was done.
     * <p>Called every time a board arrives. Compares against what was true when the step went
     * up, so "one more card in hand than before" is the evidence for drawing rather than "the
     * hand is not empty", which was already true.
     */
    public static void sawBoard(BlockPos where, GameView board) {
        if (progress == null || board == null || !runningAt(where) || progress.isOver()) {
            return;
        }
        SeatId me = seatIn(board).orElse(null);
        if (me == null) {
            return;
        }
        lastConfirmed = board;
        if (turnWhenTheStepBegan == null) {
            remember(board);
            return;
        }
        TutorialStep step = progress.showing();
        boolean done = switch (step) {
            case DRAW -> handSize(board, me) > handWhenTheStepBegan;
            case PLAY -> battlefieldSize(board, me) > battlefieldWhenTheStepBegan;
            case TAP -> dev.gathering.core.tutorial.TutorialEvidence.anyRose(
                    tappedWhenTheStepBegan, tappedNow(board, me));
            case COUNT -> dev.gathering.core.tutorial.TutorialEvidence.anyRose(
                    countersWhenTheStepBegan, countersNow(board, me));
            // Not a thing that happens to the board. See readACard.
            case READ -> false;
            case PASS -> !board.turn().equals(turnWhenTheStepBegan);
        };
        if (done) {
            progress = progress.saw(step);
            remember(board);
        }
    }

    /**
     * Takes a baseline from the last board the server confirmed, now.
     * <p>Called when the step changes rather than when the next board arrives, so the
     * player's next action is measured against where they were when they were asked - not
     * against where they were after doing it.
     */
    private static void baselineNow() {
        if (lastConfirmed == null) {
            turnWhenTheStepBegan = null;
            return;
        }
        remember(lastConfirmed);
    }

    /**
     * Takes the news that this client is showing the player a card.
     * <p>The only step whose evidence is local, because it is the only one where nothing
     * happens to the game. What is passed in has already been through the view: the inspect
     * panel draws a card the board handed this client, so a card nobody was allowed to see
     * cannot get here.
     */
    public static void readACard() {
        if (progress != null && !progress.isOver()
                && progress.showing() == TutorialStep.READ) {
            progress = progress.saw(TutorialStep.READ);
        }
    }

    /**
     * What the lesson is watching and what it last saw, for the scripted run.
     * <p>A step that will not advance is the hardest kind of thing to read from outside: the tutorial
     * either moves on or sits there, and "sits there" is the same picture whether the action never
     * happened, happened to the wrong card, or happened and was not noticed. This says which.
     */
    public static String watching() {
        if (progress == null) {
            return "no lesson running";
        }
        GameView board = lastConfirmed;
        SeatId me = board == null ? null : seatIn(board).orElse(null);
        if (board == null || me == null) {
            return progress.showing() + ", with no confirmed board to measure against";
        }
        return progress.showing()
                + ": hand " + handSize(board, me) + " (was " + handWhenTheStepBegan + ")"
                + ", battlefield " + battlefieldSize(board, me) + " (was " + battlefieldWhenTheStepBegan + ")"
                + ", tapped " + tappedNow(board, me) + " (was " + tappedWhenTheStepBegan + ")"
                + ", counters " + countersNow(board, me) + " (was " + countersWhenTheStepBegan + ")";
    }

    /** The instruction on screen, with the key it names filled in from the real binding. */
    public static Component instruction() {
        TutorialStep step = showing().orElse(null);
        if (step == null) {
            return Component.empty();
        }
        // The read step names the read key rather than a table verb, because that is what it
        // is: a key held, not an action asked for.
        Component key = step.action() == null
                ? CardZoomOverlay.keyName()
                : TableShortcuts.labelOrUnbound(step.action());
        return Component.translatable(step.key(), key);
    }

    /** The one line under the instruction saying why it matters. */
    public static Component why() {
        return showing()
                .map(step -> (Component) Component.translatable(step.whyKey()))
                .orElse(Component.empty());
    }

    // ------------------------------------------------------------------ bits

    private static void remember(GameView board) {
        SeatId me = board == null ? null : seatIn(board).orElse(null);
        if (me == null) {
            return;
        }
        handWhenTheStepBegan = handSize(board, me);
        battlefieldWhenTheStepBegan = battlefieldSize(board, me);
        tappedWhenTheStepBegan = tappedNow(board, me);
        countersWhenTheStepBegan = countersNow(board, me);
        turnWhenTheStepBegan = board.turn();
    }

    private static Optional<SeatId> seatIn(GameView board) {
        return board.viewer() instanceof dev.gathering.core.game.visibility.Viewer.Seated seated
                ? Optional.of(seated.seat())
                : Optional.empty();
    }

    private static int handSize(GameView board, SeatId me) {
        return board.seat(me).zone(Zone.HAND).count();
    }

    private static int battlefieldSize(GameView board, SeatId me) {
        return board.seat(me).zone(Zone.BATTLEFIELD).count();
    }

    /** Which of their cards are turned sideways, one entry each. */
    private static java.util.Map<dev.gathering.core.game.CardInstanceId, Integer> tappedNow(
            GameView board, SeatId me) {
        java.util.Map<dev.gathering.core.game.CardInstanceId, Integer> now = new java.util.HashMap<>();
        for (var card : board.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof dev.gathering.core.game.visibility.CardView.Visible visible) {
                now.put(visible.id(), visible.tapped() ? 1 : 0);
            }
        }
        return now;
    }

    /**
     * How many counters each of their cards is carrying.
     * <p>A total per card rather than a grand total, so a learner who puts a counter on one card and
     * takes one off another still gets credit for the one they put on - and so that a second counter on
     * the same card counts, because somebody following the instruction twice has still followed it.
     */
    private static java.util.Map<dev.gathering.core.game.CardInstanceId, Integer> countersNow(
            GameView board, SeatId me) {
        java.util.Map<dev.gathering.core.game.CardInstanceId, Integer> now = new java.util.HashMap<>();
        for (var card : board.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof dev.gathering.core.game.visibility.CardView.Visible visible) {
                int total = 0;
                for (int many : visible.counters().values()) {
                    total += many;
                }
                now.put(visible.id(), total);
            }
        }
        return now;
    }

    /** Between worlds. A tutorial belongs to the table it is running at. */
    public static void clear() {
        progress = null;
        table = null;
        turnWhenTheStepBegan = null;
        lastConfirmed = null;
    }
}
