package dev.gathering.core.ui;

/**
 * Which way somebody is looking: the two angles and nothing else.
 * <p>"The view I had" is a pair of numbers, and putting a view back is putting those two
 * numbers back - but only after they have been said the same way both times. Yaw is an angle
 * that can be written down as 350 or as -10, and a view restored to 350 when it was taken at
 * -10 is the same direction with a full turn of drift in whatever interpolates between them.
 * So every one of these is wrapped when it is made, and two that point the same way are
 * equal.
 * <p>Pitch is clamped rather than wrapped, because it is not an angle that goes round: past
 * straight up the game does not carry on over the top, it stops.
 */
public record Facing(float yaw, float pitch) {

    /** As far up and as far down as anybody can look. */
    public static final float FURTHEST_UP = -90f;

    public static final float FURTHEST_DOWN = 90f;

    public Facing {
        yaw = wrapped(yaw);
        pitch = Float.isFinite(pitch)
                ? Math.max(FURTHEST_UP, Math.min(FURTHEST_DOWN, pitch))
                : 0f;
    }

    public static Facing of(float yaw, float pitch) {
        return new Facing(yaw, pitch);
    }

    /**
     * The same angle written between -180 and 180.
     * <p>Its own method rather than the game's {@code Mth.wrapDegrees} because this module is
     * compiled with no Minecraft on its classpath at all - which is the whole point of it.
     */
    public static float wrapped(float degrees) {
        if (!Float.isFinite(degrees)) {
            // A rotation can come back as a NaN from a client that has just been handed a new
            // world. Zero is a direction; NaN is a view that can never be put back.
            return 0f;
        }
        float turned = degrees % 360f;
        if (turned >= 180f) {
            turned -= 360f;
        }
        if (turned < -180f) {
            turned += 360f;
        }
        return turned;
    }

    /**
     * How far it is from one heading to another, the short way round.
     * <p>Signed, so it can be added up: turning ten degrees right five times is fifty degrees
     * right, not five separate tens.
     */
    public static float turnBetween(float from, float to) {
        return wrapped(to - from);
    }
}
