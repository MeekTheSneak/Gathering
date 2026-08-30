package dev.gathering.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Which way a card being read is turned.
 *
 * <p>Reading a card in this mod used to be looking at a picture of one. Turning it with the
 * mouse is what makes it a card in your hands instead - and it is the only way a foil can
 * exist at all, because a holographic sheen that never moves is a sticker. See
 * {@link FoilSheen}.
 *
 * <p>The angles here are fed to {@link CardLens}, which turns a real rectangle in space and
 * projects it. That matters to the numbers: an earlier version rotated a flat picture under
 * the interface's flat projection, where a turn is only ever a squash, and it needed a large
 * angle before it read as anything at all. A genuine perspective needs a small one.
 *
 * <p>Two sources for the same number, because the read happens in two places. Over an open
 * screen there is a cursor, and where the cursor is relative to the card is the tilt. Out in
 * the world the mouse is turning the player's head, so the tilt is how far they have turned
 * it since they started reading - which is the same gesture producing the same result, and
 * the reason it reads as one feature rather than two.
 *
 * <p>Eased rather than followed. A card that snapped to the cursor would be a card being
 * dragged; a card that catches up over a few frames is a card with some weight to it, and the
 * lag is what makes the shine slide rather than jump.
 *
 * <p>One card is read at a time, so the state is static, and it is reset when the key goes up
 * so the next card starts flat rather than wherever the last one was left.
 *
 * <p>Client-only.
 */
public final class CardTilt {

    /**
     * As far as the card ever turns.
     *
     * <p>Slight on purpose, and slighter than it looks like it should be on paper: this is a
     * card resting in a hand, catching the light, not a card being shown to the room. Once
     * the turn became a real perspective rather than a squash, the angle that had been needed
     * to make a squash read as a turn at all was suddenly far too much - a card at sixteen
     * degrees of genuine perspective looks like it is being waved.
     */
    private static final float MOST_YAW = 9f;
    private static final float MOST_PITCH = 5.5f;

    /** How much of the gap is closed each frame. Enough to feel attached, slow enough to lag. */
    private static final float EASE = 0.22f;

    /** How far the head turns for a full tilt, in degrees. About a glance. */
    private static final float LOOK_FOR_FULL = 22f;

    private static float yaw;
    private static float pitch;

    /** Where the head was when this read started, or null between reads. */
    private static Float lookYaw;
    private static Float lookPitch;

    private CardTilt() {
    }

    /**
     * Aims the card at the cursor.
     *
     * <p>The card's face turns to point at the cursor: cursor to the right sends the card's
     * right-hand edge away and brings its left toward you, which is what a card does when
     * you tip it to show it to something over there. Getting this backwards reads as "wrong"
     * without anybody being able to say why.
     */
    public static void toward(int mouseX, int mouseY, int centerX, int centerY, int width, int height) {
        float across = width <= 0 ? 0f : Mth.clamp((mouseX - centerX) / (width / 2f), -1f, 1f);
        float down = height <= 0 ? 0f : Mth.clamp((mouseY - centerY) / (height / 2f), -1f, 1f);
        ease(across * MOST_YAW, down * MOST_PITCH);
    }

    /**
     * Aims the card by how far the player has turned their head since the read started, and
     * will not let them turn it any further than the card answers to.
     *
     * <p>For the world, where there is no cursor because the mouse is the camera. The anchor
     * is taken on the first frame of a read and dropped when it ends, so every card starts
     * flat and turning your head tips the one you are holding.
     *
     * <p>The holding is the reported half: "holding Alt while holding a card doesn't lock
     * your looking - it should". Past {@link #LOOK_FOR_FULL} degrees the card has already
     * tipped as far as it tips, so every further degree turned the world and did nothing to
     * the card - which meant reading a wordy card left you facing somewhere else, and reading
     * one while walking up to a table meant losing the table. Inside the arc the mouse still
     * moves the head, because that movement <em>is</em> the gesture; outside it the head is
     * simply held, which is what a person does when they stop to read something.
     */
    public static void withTheHead(Player player) {
        if (player == null) {
            return;
        }
        if (lookYaw == null) {
            lookYaw = player.getYRot();
            lookPitch = player.getXRot();
        }
        float turned = Mth.wrapDegrees(player.getYRot() - lookYaw);
        float raised = Mth.wrapDegrees(player.getXRot() - lookPitch);
        float heldYaw = Mth.clamp(turned, -LOOK_FOR_FULL, LOOK_FOR_FULL);
        float heldPitch = Mth.clamp(raised, -LOOK_FOR_FULL, LOOK_FOR_FULL);
        if (heldYaw != turned || heldPitch != raised) {
            player.setYRot(lookYaw + heldYaw);
            player.setXRot(lookPitch + heldPitch);
            // The previous rotation too, or the camera spends the next tick interpolating
            // from where the mouse got to toward where it was put back - which reads as the
            // view shuddering rather than stopping.
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        }
        ease(heldYaw / LOOK_FOR_FULL * MOST_YAW, heldPitch / LOOK_FOR_FULL * MOST_PITCH);
    }

    /** Drops the anchor and flattens the card, for when a read ends. */
    public static void forget() {
        lookYaw = null;
        lookPitch = null;
        yaw = 0f;
        pitch = 0f;
    }

    private static void ease(float wantedYaw, float wantedPitch) {
        yaw += (wantedYaw - yaw) * EASE;
        pitch += (wantedPitch - pitch) * EASE;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /**
     * Where the light is on the card, from minus one to one.
     *
     * <p>Taken off the turn rather than kept separately, because on a real foil they are the
     * same fact: the shine is where it is <em>because</em> the card is angled that way.
     */
    public static float shine() {
        return Mth.clamp(yaw / MOST_YAW, -1f, 1f);
    }

    /**
     * And where it is up and down the card.
     *
     * <p>Its own number rather than folded into the one above, because the shine is a place on
     * a surface and a place needs two. Taking only the sideways turn meant a card tipped up
     * and down was a card whose foil did nothing, which is the first thing anybody holding one
     * tries.
     */
    public static float shineDown() {
        return Mth.clamp(pitch / MOST_PITCH, -1f, 1f);
    }

    /**
     * Whether the mouse is currently free to aim it.
     *
     * <p>A screen means a cursor; no screen means the mouse is the camera. The two aiming
     * methods above are chosen by this, in one place, so the read cannot end up being aimed
     * by both at once.
     */
    public static boolean thereIsACursor() {
        return Minecraft.getInstance().screen != null;
    }
}
