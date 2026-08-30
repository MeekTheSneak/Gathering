package dev.gathering.client;

import dev.gathering.core.ui.Rect;

/**
 * A card held at an angle, seen through a lens.
 *
 * <p>The interface draws in a flat projection, where turning a card is only ever a squash:
 * both edges keep their length, nothing comes toward you, and the eye reads it as a picture
 * being compressed rather than as a card being tipped. That is what the first attempt at this
 * did, and it is why it looked wrong without being easy to say why.
 *
 * <p>So the corners are worked out here instead. The card is a real rectangle in space, turned
 * about its own middle, and each point on it is projected through a pinhole a fixed distance in
 * front: the near edge grows, the far edge shrinks, and the two vertical edges stop being
 * parallel. That is the whole of what makes it read as an object rather than an image.
 *
 * <p>Done in this class rather than by swapping the game's projection matrix, which is the
 * other way to get a perspective in an interface. Swapping it means restoring it, and the
 * interface's own model-view carries a large translation that would have to be undone and put
 * back around every draw - a lot of global state to get right for one card. Ten lines of
 * arithmetic and every draw goes through the ordinary pipeline.
 *
 * <p>Pure enough to reason about and cheap enough to call per vertex; a card is drawn as a
 * grid of a few hundred points, and the grid is why the picture does not warp across it.
 *
 * <p>Client-only.
 */
public final class CardLens {

    /**
     * How far in front of the card the eye is, as a multiple of the card's height.
     *
     * <p>A long lens rather than a short one. Short is a fish-eye: the near edge balloons and
     * the card reads as being shoved at you. What is wanted is the amount of perspective a
     * card gets when somebody a comfortable distance away tips it a few degrees, which is
     * barely any - and "barely any, but there" is exactly the difference between this and a
     * squash.
     */
    private static final float LENS = 2.6f;

    private final float centerX;
    private final float centerY;
    private final float width;
    private final float height;
    private final float eye;
    private final float cosYaw;
    private final float sinYaw;
    private final float cosPitch;
    private final float sinPitch;

    private CardLens(Rect where, float yaw, float pitch) {
        this.centerX = (float) where.centerX();
        this.centerY = (float) where.centerY();
        this.width = where.width();
        this.height = where.height();
        this.eye = Math.max(1f, LENS * where.height());
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        this.cosYaw = (float) Math.cos(y);
        this.sinYaw = (float) Math.sin(y);
        this.cosPitch = (float) Math.cos(p);
        this.sinPitch = (float) Math.sin(p);
    }

    /** A card of this size turned this far. Angles in degrees; see {@link CardTilt}. */
    public static CardLens of(Rect where, float yaw, float pitch) {
        return new CardLens(where, yaw, pitch);
    }

    /** Whether this is a card lying flat, which can be drawn the plain way instead. */
    public boolean isSquare() {
        return Math.abs(sinYaw) < 1.0e-4f && Math.abs(sinPitch) < 1.0e-4f;
    }

    /**
     * Where a point on the card lands on the screen.
     *
     * <p>{@code u} and {@code v} run zero to one across the card and down it, the same way a
     * texture does, so a caller walking a grid of them is walking the card.
     *
     * @param out a two-element array to write the screen point into, so a grid of several
     *     hundred points does not allocate several hundred times a frame
     */
    public void at(float u, float v, float[] out) {
        float across = (u - 0.5f) * width;
        float down = (v - 0.5f) * height;

        // Turned about the middle: first sideways, then up and down. Positive yaw sends the
        // right-hand edge away, which is what a card does when its face is turned to point at
        // something on the right.
        float x = across * cosYaw;
        float lean = across * sinYaw;
        float y = down * cosPitch + lean * sinPitch;
        float depth = down * sinPitch - lean * cosPitch;

        float near = eye / (eye + depth);
        out[0] = centerX + x * near;
        out[1] = centerY + y * near;
    }

    /** How much taller the card is than it is wide, for anything drawn square on it. */
    public float aspect() {
        return width <= 0f ? 1f : width / Math.max(1f, height);
    }

    /**
     * How far along the shine's rake a point on the card sits, from zero to one.
     *
     * <p>In the card's own space rather than on the screen, because the shine belongs to the
     * card: it must sit still on the picture while the card turns under it, and only move
     * because the light moved.
     */
    public float alongRake(float u, float v, float cosRake, float sinRake) {
        float across = (u - 0.5f) * width;
        float down = (v - 0.5f) * height;
        float span = Math.abs(width * cosRake) + Math.abs(height * sinRake);
        return span <= 0f ? 0.5f : 0.5f + (across * cosRake + down * sinRake) / span;
    }
}
