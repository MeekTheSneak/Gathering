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
 * <p><b>Degrees, and a stated convention.</b> {@link Aim#armYaw} is which way the arm points
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
     * How far the arm may point across the body before it stops following.
     * <p>A target to the player's left is reached for by the left arm, and this one is the right.
     * Left unbounded, pointing at the far left of a pod folded the right arm through the chest,
     * which is the single worst frame this feature can produce. Forty-five degrees across is about
     * where a person stops and turns their shoulders instead - and it is generous rather than
     * mean, because the clamp being met is what pins an arm in place.
     */
    private static final double YAW_ACROSS_THE_BODY = -45;

    /** And how far out to the side, which is where an arm stops without the other shoulder moving. */
    private static final double YAW_OUT = 95;

    /** How far up from hanging the arm may come. Past this it is a salute rather than a reach. */
    private static final double PITCH_FORWARD = 100;

    /** And how far back, which at a table is never far: a hand at rest hangs, it does not trail. */
    private static final double PITCH_BACK = 0;

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
     * @param armYaw    which way the arm points, positive toward the player's right
     * @param armPitch  how far it has come up from hanging straight down: zero hanging, ninety
     *     straight out in front of the shoulder
     * @param headYaw   positive toward the player's right
     * @param headPitch positive downward, the way Minecraft's own pitch runs
     */
    public record Aim(float armYaw, float armPitch, float headYaw, float headPitch) {

        /** Nobody pointing at anything: arms down, head level. The pose to fall back to. */
        public static final Aim RESTING = new Aim(0, 0, 0, 0);

        /**
         * Sitting at a table, not pointing at anything: hands at the near edge, head down at the
         * felt.
         * <p>Not {@link #RESTING}, and the difference is the whole of what a seated body looks
         * like. Resting is a person standing with their arms down and their eyes on the horizon,
         * which is what a player at a table was drawn as for as long as nothing had told their
         * client where they were pointing - so they sat bolt upright staring across the room with
         * their arms by their sides. A card player leans in, hands on the table, looking down at
         * it.
         * <p>This is what a pointer moves <em>from</em>, too, so an arm that comes up to point and
         * goes back down again starts and finishes somewhere a body would actually be.
         */
        public static final Aim AT_THE_TABLE = new Aim(10, 72, 0, 32);

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
                    armYaw + (other.armYaw - armYaw) * part,
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

        // How far the arm has come up from hanging straight down, which is the angle between the
        // arm and the way gravity goes. Zero hanging, ninety straight out in front.
        double reach = Math.sqrt(
                handAcross * handAcross + handForward * handForward + handDown * handDown);
        double pitch = reach <= 0 ? 0
                : Math.toDegrees(Math.acos(clamp(handDown / reach, -1, 1)));
        // And which way it points once it is up there. This is a yaw about the body's own
        // vertical, which is what a model part's Y rotation does to an arm already pitched
        // forward - see the note at the top about why it is not a sideways swing.
        double yaw = Math.toDegrees(Math.atan2(handAcross, handForward));

        // The head is not hinged like the arm and is not clamped to reach at all - a neck turns to
        // look at things it cannot touch. It is the raw target, not the shortened one, for exactly
        // that reason: a player looks at the far side of the table, and reaches for the near one.
        double headYaw = Math.toDegrees(Math.atan2(across, Math.max(1e-6, forward)));
        double headPitch = Math.toDegrees(Math.atan2(down, Math.hypot(across, forward)));

        return new Aim(
                (float) clamp(yaw, YAW_ACROSS_THE_BODY, YAW_OUT),
                (float) clamp(pitch, PITCH_BACK, PITCH_FORWARD),
                (float) clamp(headYaw, -HEAD_YAW, HEAD_YAW),
                (float) clamp(headPitch, HEAD_PITCH_UP, HEAD_PITCH_DOWN));
    }

    private static double clamp(double value, double least, double most) {
        return Math.max(least, Math.min(most, value));
    }
}
