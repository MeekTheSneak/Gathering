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
    private static int tappedWhenTheStepBegan;
    private static int countersWhenTheStepBegan;
    private static dev.gathering.core.game.TurnMarker turnWhenTheStepBegan;

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
     * Says a practice game has been asked for at this table.
     * <p>Not begun: asked for. The instruction goes up when a board actually arrives, which is
     * {@link #beginAt}. A tutorial that started on the press would be giving instructions
     * about a table that had just refused to have a practice game on it.
     */
    public static void expectAt(BlockPos where) {
        expecting = where == null ? null : where.immutable();
    }

    /** Whether a practice game is expected at this table and has not arrived. */
    public static boolean expectedAt(BlockPos where) {
        return expecting != null && where != null && expecting.equals(where);
    }

    /** The table a practice game was asked for at, and has not arrived at yet. */
    private static BlockPos expecting;

    /**
     * Begins, at this table.
     * <p>Called when the server has confirmed a practice game is running - not when the button
     * was pressed. A tutorial that started on the press would put its first instruction up
     * over a table that had refused to start one.
     */
    public static void beginAt(BlockPos where, GameView board) {
        expecting = null;
        table = where == null ? null : where.immutable();
        progress = TutorialProgress.start();
        remember(board);
        ClientSettings.tutorialOffered(true);
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
            } else {
                ClientSettings.tutorialSkipped(true);
            }
        }
        progress = null;
        table = null;
        expecting = null;
        turnWhenTheStepBegan = null;
    }

    /** Shows the previous instruction again. Reviews; changes nothing about the game. */
    public static void back() {
        if (progress != null) {
            progress = progress.back();
            turnWhenTheStepBegan = null;
        }
    }

    /** Moves on without doing this one, for somebody who already knows it. */
    public static void forward() {
        if (progress != null) {
            progress = progress.forward();
            turnWhenTheStepBegan = null;
        }
    }

    /** Starts over from the first instruction. Does not touch the game that is running. */
    public static void restart() {
        if (progress != null) {
            progress = TutorialProgress.start();
            turnWhenTheStepBegan = null;
        }
    }

    /** They said no. */
    public static void skip() {
        if (progress != null) {
            progress = progress.skip();
        }
        ClientSettings.tutorialOffered(true);
        ClientSettings.tutorialSkipped(true);
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
        if (turnWhenTheStepBegan == null) {
            remember(board);
            return;
        }
        TutorialStep step = progress.showing();
        boolean done = switch (step) {
            case DRAW -> handSize(board, me) > handWhenTheStepBegan;
            case PLAY -> battlefieldSize(board, me) > battlefieldWhenTheStepBegan;
            case TAP -> tappedCount(board, me) > tappedWhenTheStepBegan;
            case COUNT -> counterTotal(board, me) > countersWhenTheStepBegan;
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
        tappedWhenTheStepBegan = tappedCount(board, me);
        countersWhenTheStepBegan = counterTotal(board, me);
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

    private static int tappedCount(GameView board, SeatId me) {
        int tapped = 0;
        for (var card : board.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof dev.gathering.core.game.visibility.CardView.Visible visible
                    && visible.tapped()) {
                tapped++;
            }
        }
        return tapped;
    }

    /**
     * Every counter on every card this player has out, added up.
     * <p>A total rather than a set of names, so that adding a second +1/+1 to the same card
     * counts. Somebody following the instruction twice has still followed it.
     */
    private static int counterTotal(GameView board, SeatId me) {
        int total = 0;
        for (var card : board.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof dev.gathering.core.game.visibility.CardView.Visible visible) {
                for (int many : visible.counters().values()) {
                    total += many;
                }
            }
        }
        return total;
    }

    /** Between worlds. A tutorial belongs to the table it is running at. */
    public static void clear() {
        progress = null;
        table = null;
        expecting = null;
        turnWhenTheStepBegan = null;
    }
}
