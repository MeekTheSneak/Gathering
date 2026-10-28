package dev.gathering.core.ui;

import java.util.Optional;

/**
 * The view held still while a card is being read, and the gesture that still gets through.
 * <p>Reported as "holding alt while holding a card in your hand needs to lock your camera
 * movement". It used to be a fence rather than a lock: the head was free inside an arc of a
 * few degrees and stopped at the edge of it, so reading a card moved you, and reading a wordy
 * one moved you as far as the fence and left you there. A player who looks at a card expects
 * to find the same room when they look up.
 * <p>So the view does not move at all. The mouse still does, and what it would have done is
 * added up here instead: the card turns in the hand by exactly the amount the head would have
 * turned, which is the gesture the read was built around, with none of it reaching the world.
 * That is why this counts as well as refuses. A lock that only refused would leave a foil that
 * cannot be tipped, which is a sticker.
 * <p>Asked once per frame with wherever the mouse has just put the view. It answers with where
 * the view has to go back to, and the caller puts it there.
 */
public final class ViewHold {

    /** How far the gesture is allowed to add up to before it is simply at its limit. */
    private final float mostTurn;

    private Facing anchor;
    private float turnedYaw;
    private float turnedPitch;

    /**
     * @param mostTurn the turn, in degrees, that counts as the whole gesture - past which
     *     further mouse movement means nothing rather than meaning more
     */
    public ViewHold(float mostTurn) {
        this.mostTurn = Math.abs(mostTurn);
    }

    /**
     * One frame of the hold.
     *
     * @param holding whether the read key is down over a card with no screen in the way
     * @param looking where the mouse has just left the view
     * @return where the view must be put back to, or empty when nothing is being held
     */
    public Optional<Facing> frame(boolean holding, Facing looking) {
        if (!holding || looking == null) {
            release();
            return Optional.empty();
        }
        if (anchor == null) {
            anchor = looking;
            turnedYaw = 0f;
            turnedPitch = 0f;
            return Optional.of(anchor);
        }
        // Everything between the anchor and where the mouse has got to is this frame's
        // movement, because last frame ended with the view back on the anchor.
        turnedYaw = clamped(turnedYaw + Facing.turnBetween(anchor.yaw(), looking.yaw()));
        turnedPitch = clamped(turnedPitch + (looking.pitch() - anchor.pitch()));
        return Optional.of(anchor);
    }

    private float clamped(float turn) {
        return Math.max(-mostTurn, Math.min(mostTurn, turn));
    }

    /** Whether a view is being held right now. */
    public boolean isHolding() {
        return anchor != null;
    }

    /** How far the mouse has asked to turn since the read started, sideways. */
    public float turnedYaw() {
        return anchor == null ? 0f : turnedYaw;
    }

    /** And up and down. */
    public float turnedPitch() {
        return anchor == null ? 0f : turnedPitch;
    }

    /**
     * Lets go.
     * <p>Called on every frame that is not a hold, so a lock cannot outlive the key, the
     * screen that opened over it, the player, or the server.
     */
    public void release() {
        anchor = null;
        turnedYaw = 0f;
        turnedPitch = 0f;
    }
}
