package dev.gathering.core.tutorial;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * How far through the guided first game somebody is.
 * <p>A value. Every change hands back a new one, so a controller can hold the old one and
 * compare, and a test can drive the whole thing without a game running.
 * <p>What it will not do is advance on a press. Five of the six steps are satisfied only by
 * {@link #saw(TutorialStep)} being told about a <em>confirmed</em> action attributed to the
 * learner; the sixth is satisfied by this client having actually shown them a card. A step
 * that advanced when the packet went out would teach somebody that pressing the key is the
 * thing, and would then sit there having taught it after the server refused.
 * <p>Going back reviews. It does not undo: the game is a real game and the cards really moved,
 * and a Back button that quietly returned a card to a library would be a rules engine, which
 * this mod does not have and will not grow. So a step already done stays done, and going back
 * only changes which instruction is on the screen.
 * <p>Skipping is recorded as skipping. It is never written down as finishing, however far
 * through somebody was, because the two are different facts about a player and the second one
 * is the one that decides whether they are ever offered this again.
 */
public record TutorialProgress(TutorialStep showing, Set<TutorialStep> done, boolean skipped) {

    public TutorialProgress {
        // Built up rather than EnumSet.copyOf, which throws on an empty collection that is
        // not already an EnumSet - and "nothing done yet" is the state this starts in.
        EnumSet<TutorialStep> kept = EnumSet.noneOf(TutorialStep.class);
        if (done != null) {
            kept.addAll(done);
        }
        done = Collections.unmodifiableSet(kept);
    }

    /** At the beginning, with nothing done. */
    public static TutorialProgress start() {
        return new TutorialProgress(TutorialStep.DRAW, EnumSet.noneOf(TutorialStep.class), false);
    }

    /**
     * Takes the news that this action really happened.
     * <p>Only the step being shown can be completed by it. Tapping a card during the draw step
     * is a player exploring, which is fine and teaches them something, but it is not the draw
     * step and marking it done would leave the tutorial claiming they had drawn a card when
     * they had not.
     * <p>Once it is done, the next instruction goes up. The last one leaves {@code showing}
     * where it is and {@link #isFinished()} becomes true.
     */
    public TutorialProgress saw(TutorialStep step) {
        if (step == null || skipped || step != showing || done.contains(step)) {
            return this;
        }
        EnumSet<TutorialStep> now = EnumSet.noneOf(TutorialStep.class);
        now.addAll(done);
        now.add(step);
        TutorialStep next = step.next();
        return new TutorialProgress(next == null ? step : next, now, false);
    }

    /**
     * Shows the previous instruction again, without changing what has been done.
     * <p>Review, not undo. See the note on the class.
     */
    public TutorialProgress back() {
        TutorialStep before = showing.previous();
        return before == null ? this : new TutorialProgress(before, done, skipped);
    }

    /**
     * Shows the next instruction without doing it.
     * <p>For somebody who already knows this one and wants to move on. Deliberately does not
     * mark the step done: they did not do it, and a tutorial that claimed they had would be
     * lying to the only person it exists to help.
     */
    public TutorialProgress forward() {
        TutorialStep after = showing.next();
        return after == null ? this : new TutorialProgress(after, done, skipped);
    }

    /** They said no. Recorded as exactly that. */
    public TutorialProgress skip() {
        return new TutorialProgress(showing, done, true);
    }

    /** Whether every step has actually been done. */
    public boolean isFinished() {
        return !skipped && done.size() == TutorialStep.count();
    }

    /** Whether there is nothing more to show: finished, or given up on. */
    public boolean isOver() {
        return skipped || isFinished();
    }

    /** Whether that step has been done. */
    public boolean isDone(TutorialStep step) {
        return step != null && done.contains(step);
    }

    /** How many steps are done, for "3 of 6". */
    public int count() {
        return done.size();
    }

    /**
     * Which step this action would complete, or null for one that is not what is being asked.
     * <p>The controller's question. It has a confirmed action's id and wants to know whether
     * to hand it here at all - and the answer is no unless it is the very step on screen,
     * which keeps the "a player exploring is not a player completing" rule in one place.
     */
    public TutorialStep stepFor(String actionId) {
        return !isOver() && actionId != null && actionId.equals(showing.action())
                ? showing
                : null;
    }
}
