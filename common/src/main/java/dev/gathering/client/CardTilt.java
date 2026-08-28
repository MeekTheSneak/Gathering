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

    /** As far as the card ever turns. Slight on purpose: this is a hand moving, not a lathe. */
    private static final float MOST_YAW = 16f;
    private static final float MOST_PITCH = 11f;

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
     * <p>Cursor to the right of the card turns its right edge away, which is what happens when
     * you turn a card towards something on your right. Getting this backwards is the sort of
     * thing that reads as "wrong" without anybody being able to say why.
     */
    public static void towards(int mouseX, int mouseY, int centerX, int centerY, int width, int height) {
        float across = width <= 0 ? 0f : Mth.clamp((mouseX - centerX) / (width / 2f), -1f, 1f);
        float down = height <= 0 ? 0f : Mth.clamp((mouseY - centerY) / (height / 2f), -1f, 1f);
        ease(across * MOST_YAW, -down * MOST_PITCH);
    }

    /**
     * Aims the card by how far the player has turned their head since the read started.
     *
     * <p>For the world, where there is no cursor because the mouse is the camera. The anchor
     * is taken on the first frame of a read and dropped when it ends, so every card starts
     * flat and turning your head tips the one you are holding.
     */
    public static void withTheHead(Player player) {
        if (player == null) {
            return;
        }
        if (lookYaw == null) {
            lookYaw = player.getYRot();
            lookPitch = player.getXRot();
        }
        float turned = Mth.wrapDegrees(player.getYRot() - lookYaw) / LOOK_FOR_FULL;
        float raised = Mth.wrapDegrees(player.getXRot() - lookPitch) / LOOK_FOR_FULL;
        ease(Mth.clamp(turned, -1f, 1f) * MOST_YAW, -Mth.clamp(raised, -1f, 1f) * MOST_PITCH);
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
