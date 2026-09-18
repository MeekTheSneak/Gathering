package dev.gathering.core.ui;

/**
 * Where a seated player's arm and head point, given somewhere on the table they are pointing at.
 * <p>A body at a card table is doing two things at once: one hand is out over the felt where its
 * owner is looking, and the head is looking there too. Both are the same question asked twice,
 * and both have the same answer spoiled by the same thing - a shoulder is not on a rail. A table
 * is five blocks across at a pod and an arm is three quarters of one, so "point at what the
 * cursor is over" taken literally gives a limb stretched across the room, which is worse than no
 * animation at all.
 * <p>So the target is pulled into reach <em>before</em> any angle is taken, and what comes out is
 * a direction rather than a destination: the arm points the whole way at something it can only
 * touch part of the way, which is what a person's arm does.
 * <p><b>In the player's own frame.</b> Across is to their right, forward is away from their chest,
 * down is toward the floor, all in blocks from the shoulder. Which compass direction that is
 * depends on the edge they are sitting at, and that rotation happens once on the render side -
 * there is no compass in here, which is what lets this be checked over every target rather than
 * over the ones somebody thought to write down.
 * <p><b>Degrees, and a stated convention.</b> {@link Aim#armSwing} is how far the arm has come
 * away from hanging straight down, positive toward the player's right. {@link Aim#armPitch} is how
 * far it has come forward from there, positive forward. Turning those into a model part's radians
 * is the renderer's job and the signs are mirrored between left and right arms, which is a thing
 * to settle in a running game rather than here.
 */
public final class TablePose {

    /**
     * How far a shoulder reaches, in blocks: an arm is twelve model pixels and the player is drawn
     * at fifteen sixteenths, the same arithmetic {@code ChairSeat.HIP_HEIGHT} does for a leg.
     * <p>Slightly generous on purpose. A hand exactly at the limit is a straight arm, and a
     * straight arm reads as a mannequin; the pose class leaves a little bend by never quite
     * arriving. See {@link #EXTENSION}.
     */
    public static final double ARM_REACH = 12.0 / 16.0 * 15.0 / 16.0;

    /**
     * How much of that reach is actually used.
     * <p>An arm held out dead straight is the pose of something that is not alive. Nine tenths
     * keeps a visible bend at the elbow the model does not have, by holding the hand short of
     * where a straight arm would put it.
     */
    private static final double EXTENSION = 0.9;

    /**
     * How far the arm may swing across the body before it stops following.
     * <p>A target to the player's left is reached for by the left arm, and this one is the right.
     * Left unbounded, pointing at the far left of a pod folded the right arm through the chest,
     * which is the single worst frame this feature can produce. Thirty degrees across is about
     * where a person stops and turns their shoulders instead.
     */
    private static final double SWING_ACROSS_THE_BODY = -30;

    /** And how far out to the side, which is where an arm stops without the other shoulder moving. */
    private static final double SWING_OUT = 100;

    /** How far forward the arm may come. Past this it is a salute rather than a reach. */
    private static final double PITCH_FORWARD = 95;

    /** And how far back, which at a table is never far: a hand at rest hangs, it does not trail. */
    private static final double PITCH_BACK = -15;

    /** How far a head turns before the neck has had enough and the body would follow. */
    private static final double HEAD_YAW = 70;

    /** How far it tips down, which at a table is most of what it does, and how far up. */
    private static final double HEAD_PITCH_DOWN = 80;

    private static final double HEAD_PITCH_UP = -30;

    private TablePose() {
    }

    /**
     * An arm and a head, in degrees, in the player's own frame.
     *
     * @param armSwing  away from hanging straight down, positive toward the player's right
     * @param armPitch  forward from there, positive away from the chest
     * @param headYaw   positive toward the player's right
     * @param headPitch positive downward, the way Minecraft's own pitch runs
     */
    public record Aim(float armSwing, float armPitch, float headYaw, float headPitch) {

        /** Nobody pointing at anything: arms down, head level. The pose to fall back to. */
        public static final Aim RESTING = new Aim(0, 0, 0, 0);

        /**
         * This aim a fraction of the way toward another one.
         * <p>Pointers arrive four or five times a second and frames are drawn sixty, so something
         * has to sit between two of them or the arm arrives in steps. Plain and linear: the arc an
         * arm sweeps is small enough that nothing fancier would be visible, and the cost of this
         * is paid once per drawn player per frame.
         */
        public Aim toward(Aim other, float fraction) {
            float part = Math.max(0f, Math.min(1f, fraction));
            return new Aim(
                    armSwing + (other.armSwing - armSwing) * part,
                    armPitch + (other.armPitch - armPitch) * part,
                    headYaw + (other.headYaw - headYaw) * part,
                    headPitch + (other.headPitch - headPitch) * part);
        }
    }

    /**
     * Where the hand ends up, in the player's own frame, once the target has been pulled into
     * reach.
     * <p>Its own method because it is the thing the property test asserts on: whatever the target,
     * this point is inside the shoulder's sphere. An aim that agreed with a hand outside that
     * sphere would be an arm off its body, and the whole of the owner's constraint is one line of
     * arithmetic here rather than a paragraph of intent somewhere else.
     *
     * @return the three components, in the order they were given
     */
    public static double[] handAt(double across, double forward, double down) {
        double length = Math.sqrt(across * across + forward * forward + down * down);
        double usable = ARM_REACH * EXTENSION;
        if (length <= usable || length == 0) {
            return new double[] {across, forward, down};
        }
        double shrink = usable / length;
        return new double[] {across * shrink, forward * shrink, down * shrink};
    }

    /**
     * The pose for a player pointing at a place on the table.
     * <p>The decomposition is a shoulder's own: swing the hanging arm out sideways until it is
     * under the target, then bring it forward until it is pointing at it. Taking a yaw and a pitch
     * off the vector directly is the obvious alternative and it is wrong for a limb hinged at the
     * top - it puts the arm through the hip on the way to anything low and wide.
     *
     * @param across  how far to the player's right the target is, in blocks from the shoulder
     * @param forward how far away from their chest
     * @param down    how far below the shoulder - a table is always below it, so this is usually
     *                positive
     */
    public static Aim reaching(double across, double forward, double down) {
        double[] hand = handAt(across, forward, down);
        double handAcross = hand[0];
        double handForward = hand[1];
        double handDown = hand[2];

        // Straight down is zero swing; positive is out to the player's right. Measured against the
        // downward component rather than against the horizontal one, so an arm reaching for
        // something almost underneath it barely swings at all.
        double swing = Math.toDegrees(Math.atan2(handAcross, Math.max(1e-6, handDown)));
        // And forward from wherever that swing left it. The hypotenuse of the other two is how far
        // the arm has got in its own plane, which is what the forward reach is an angle against.
        double pitch = Math.toDegrees(Math.atan2(
                handForward, Math.hypot(handAcross, handDown)));

        // The head is not hinged like the arm and is not clamped to reach at all - a neck turns to
        // look at things it cannot touch. It is the raw target, not the shortened one, for exactly
        // that reason: a player looks at the far side of the table, and reaches for the near one.
        double headYaw = Math.toDegrees(Math.atan2(across, Math.max(1e-6, forward)));
        double headPitch = Math.toDegrees(Math.atan2(down, Math.hypot(across, forward)));

        return new Aim(
                (float) clamp(swing, SWING_ACROSS_THE_BODY, SWING_OUT),
                (float) clamp(pitch, PITCH_BACK, PITCH_FORWARD),
                (float) clamp(headYaw, -HEAD_YAW, HEAD_YAW),
                (float) clamp(headPitch, HEAD_PITCH_UP, HEAD_PITCH_DOWN));
    }

    private static double clamp(double value, double least, double most) {
        return Math.max(least, Math.min(most, value));
    }
}
