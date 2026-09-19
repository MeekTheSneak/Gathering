package dev.gathering.core.ui;

/**
 * Turning a place in the world into a pose, for a body sitting at a table.
 * <p>{@link TablePose} works in a body's own frame - across, forward, down from the shoulder - and
 * knows nothing about north or about where a shoulder is. This is the step before it: where the
 * shoulder actually is, and which way the body is facing, so that a point on a table's felt
 * becomes three numbers in that frame.
 * <p><b>Here rather than in the renderer because this is where the mistake was.</b> The shoulder
 * was first put at hip height, 0.59 of a block above the player's own position. A table's felt is
 * 0.94 above its block, so the thing being pointed at was <em>above</em> the shoulder: the arm
 * reached upward and the head tipped up, and a player at a table appeared to be staring at the
 * ceiling. Nothing could have caught that except looking, because the arithmetic lived in a class
 * that needs a running game. It lives here now, and the test below is the one that would have.
 * <p>Pure, and no compass in it beyond the body's own yaw.
 */
public final class TableReach {

    /**
     * How far above a player's own position their shoulder is, in blocks.
     * <p>Off the player model: the arms hang from twenty-two of its thirty-two units, drawn at
     * fifteen sixteenths like every other measurement here.
     */
    public static final double SHOULDER_UP = 22.0 / 16.0 * 15.0 / 16.0;

    /**
     * And how far out to the side of the middle of the body.
     * <p>The torso is eight model units across and an arm hangs off its edge, so the joint is six
     * from the middle.
     */
    public static final double SHOULDER_OUT = 6.0 / 16.0 * 15.0 / 16.0;

    private TableReach() {
    }

    /**
     * Where a seated body's arm and head should point to reach a place in the world.
     *
     * @param feetX        the player's own position, which is where their feet are
     * @param bodyYaw      which way their shoulders are square to, in Minecraft's degrees: zero
     *     facing south, growing clockwise
     * @param rightHanded  whether the pointing arm is the right one, which decides which side of
     *     the body the shoulder is on
     * @param atX          the place being pointed at
     */
    public static TablePose.Aim toward(double feetX, double feetY, double feetZ, double bodyYaw,
            boolean rightHanded, double atX, double atY, double atZ) {
        double yaw = Math.toRadians(bodyYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // The shoulder, out to whichever side the pointing arm is on. A quarter turn right of the
        // way the body faces: facing south, the player's right hand is to the west.
        double side = rightHanded ? SHOULDER_OUT : -SHOULDER_OUT;
        double shoulderX = feetX - side * cos;
        double shoulderZ = feetZ - side * sin;
        double shoulderY = feetY + SHOULDER_UP;

        double dx = atX - shoulderX;
        double dz = atZ - shoulderZ;
        // Down is positive, and for anything on a table it had better be: a shoulder is above the
        // felt, and a negative here is the whole of the defect this class was pulled out for.
        double down = shoulderY - atY;

        // Into the body's own frame. Minecraft's yaw is zero facing south, so forward runs along
        // (-sin, cos) and the body's right along (-cos, -sin).
        double forward = dz * cos - dx * sin;
        double across = -(dx * cos + dz * sin);

        return TablePose.reaching(across, forward, down);
    }

    /**
     * How far below the shoulder a point is, for a caller that only wants to know which way a body
     * would have to look at it.
     * <p>Its own method so the one number that was wrong can be asserted on directly, rather than
     * inferred from an angle that several other things also move.
     */
    public static double belowTheShoulder(double feetY, double atY) {
        return feetY + SHOULDER_UP - atY;
    }
}
