package dev.gathering.client;

import dev.gathering.core.ui.TablePose;
import dev.gathering.core.ui.TableTop;
import java.util.Optional;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What a seated player's body is doing, decided once for both loaders.
 * <p>The twin of {@link TableCameraView}, and here for the same reason: two mixins reach into the
 * game's own drawing, and what they reach in <em>with</em> has to be one decision or the two
 * loaders will eventually disagree about somebody's arm. Each mixin asks one question at the top
 * of one method and does nothing at all if the answer is no.
 * <p>This is also where the compass lives. {@link TablePose} works in the player's own frame -
 * across, forward, down - and knows nothing about north; the pointed-at place is a point on a
 * table's felt, which a turned cluster lays a quarter round and a moving structure carries
 * somewhere else entirely. Turning one into the other happens here, once.
 * <p>Client-only.
 */
public final class TableBodyPose {

    /**
     * How far above the felt a seated player's shoulder is, in blocks.
     * <p>Worked out from the same numbers {@code ChairSeat} sits a player with rather than
     * measured off a screenshot, so that a change to the chair's height moves the arm with it
     * instead of leaving it hovering. Expect to correct this once in a running game: the model's
     * shoulder is not exactly where the arithmetic says the joint is.
     */
    private static final double SHOULDER_ABOVE_THE_SEAT =
            dev.gathering.block.ChairSeat.HIP_HEIGHT - dev.gathering.block.ChairSeat.HALF_A_THIGH;

    /**
     * How far out from the middle of the body a shoulder sits, in blocks.
     * <p>Two model pixels past the torso's own edge, at the scale the player is drawn. The arm is
     * hinged there and not at the player's feet, and pretending otherwise puts the whole pose off
     * by a hand's width - which at this range is the difference between pointing at a card and
     * pointing at the one beside it.
     */
    private static final double SHOULDER_OUT = 5.0 / 16.0 * 15.0 / 16.0;

    private TableBodyPose() {
    }

    /**
     * Whether this player's body should be posed at all.
     * <p>False for everybody standing up, which is the answer almost every frame, and it has to be
     * cheap: this is asked for every drawn player in every frame by a hook inside the model's own
     * setup.
     * <p>Deliberately not "when they have a live pointer": a seated player with no pointer still
     * has one hand full of cards and neither hand free for whatever they were carrying, so the
     * pose applies and the arm is simply at rest.
     */
    public static boolean poses(Player player) {
        // In a chair, not merely holding a seat. A seat outlives standing up on purpose - it is
        // what keeps your place while you are away from the board - so a player who had stood up
        // and walked off went on holding their cards and pointing at the felt from across the
        // room. The owner saw it (2026-09-18); it is the same confusion the table's own gestures
        // had, and the same answer.
        return player != null
                && player.getVehicle() instanceof dev.gathering.block.ChairSeat
                && SeatedPlayers.of(player.getUUID()).isPresent();
    }

    /**
     * Whether everybody should be drawn still, because the person looking is playing.
     * <p>The owner's decision, and the only version of it that works. The table camera looks
     * straight down, so an arm reaching out over the felt is foreshortened into a line lying over
     * whatever mats it crosses - a pointing player at a pod sweeps their own arm across three
     * other people's boards. At rest, the arms hang at the table's edge where the body already is.
     * <p>Asked before anything else in {@link #aimOf}, so a player in the table view runs no
     * lookup and no interpolation at all: one static boolean per drawn player per frame.
     * <p>This is about the <em>viewer</em>, not about the player being drawn. The same seated
     * player is still and pointing at the same moment, to two people in two different views, and
     * that is correct: the still one is being looked at by somebody who is playing.
     */
    private static boolean everybodyIsStill() {
        return TableCameraView.isLooking();
    }

    /**
     * Where this player's arm and head point this frame.
     * <p>{@link TablePose.Aim#RESTING} for a seated player who is not pointing at anything, which
     * is a real pose rather than a fallback: hands down, head level, holding their cards. A player
     * whose table screen is closed is one of those - no screen, no cursor, no pointer - and so is
     * every player at all while the person looking is in the table view.
     */
    public static TablePose.Aim aimOf(Player player, float partialTick) {
        if (player == null || everybodyIsStill()) {
            return TablePose.Aim.RESTING;
        }
        if (!poses(player)) {
            return TablePose.Aim.RESTING;
        }
        SeatedPlayers.Seated seated = SeatedPlayers.of(player.getUUID()).orElse(null);
        if (seated == null) {
            return TablePose.Aim.RESTING;
        }
        ClientTablePointing.Spot spot =
                ClientTablePointing.pointedAt(player.getUUID(), partialTick).orElse(null);
        if (spot == null || spot.table() == null || !spot.table().equals(seated.table())) {
            return TablePose.Aim.RESTING;
        }
        Vec3 at = inTheWorld(spot);
        if (at == null) {
            return TablePose.Aim.RESTING;
        }

        // From the shoulder, not from the feet: the arm is hinged at the top and a pose measured
        // from anywhere else is out by however far the joint actually is from where it was
        // measured.
        Vec3 shoulder = shoulderOf(player, partialTick);
        double dx = at.x - shoulder.x;
        double dz = at.z - shoulder.z;
        double down = shoulder.y - at.y;

        // Into the player's own frame. The body's yaw and not the head's, because the head is one
        // of the things being posed and reading it here would make the pose chase itself.
        double yaw = Math.toRadians(player.yBodyRot);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // Minecraft's yaw is zero facing south (+z) and grows clockwise, so forward is that
        // heading and across is a quarter turn to its right.
        double forward = dz * cos - dx * sin;
        double across = -(dx * cos + dz * sin);

        TablePose.Aim aim = TablePose.reaching(across, forward, down);
        // A player who has just stopped lowers their arm rather than having it vanish.
        return TablePose.Aim.RESTING.toward(aim, spot.settling());
    }

    /**
     * The point on the felt, in the world this frame.
     * <p>The same route {@link TablePointer} takes and it has to stay the same route: the
     * surface's coordinates are the table's own, and a table on a moving structure is somewhere
     * else by the time it is drawn.
     */
    private static Vec3 inTheWorld(ClientTablePointing.Spot spot) {
        net.minecraft.client.multiplayer.ClientLevel level =
                net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        TableTop top = surfaceOf(level, spot.table());
        double[] onTheTable = top.inTheWorld(top.worldX(spot.surfaceX()), top.worldZ(spot.surfaceY()));
        return dev.gathering.platform.WorldSpace.get().toWorld(level,
                new Vec3(onTheTable[0], top.topY(), onTheTable[1]));
    }

    /**
     * The surface of the whole cluster, built the way the camera builds it.
     * <p>How many tables it is comes from the board this client has been sent, because a pod is
     * four tables' worth of felt and a point on the far one is past the first table's edge.
     */
    private static TableTop surfaceOf(net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos corner) {
        int seats = ClientTableState.viewOf(corner)
                .map(board -> board.seats().size())
                .orElse(dev.gathering.core.table.TableCluster.SEATS_PER_TABLE);
        int tables = Math.max(1, (seats + dev.gathering.core.table.TableCluster.SEATS_PER_TABLE - 1)
                / dev.gathering.core.table.TableCluster.SEATS_PER_TABLE);
        return TableTop.forCluster(corner.getX(), corner.getY(), corner.getZ(), tables, 1,
                dev.gathering.block.TableClusters.at(level, corner).turned());
    }

    /** Where the pointing shoulder is this frame, in the world. */
    private static Vec3 shoulderOf(Player player, float partialTick) {
        Vec3 feet = player.getPosition(partialTick);
        double yaw = Math.toRadians(player.yBodyRot);
        // The main arm's shoulder, on whichever side that is: a left-handed player points with
        // their left and holds their cards in the right, and a pose that assumed otherwise draws
        // the fan straight through the pointing arm.
        double side = player.getMainArm() == HumanoidArm.RIGHT ? SHOULDER_OUT : -SHOULDER_OUT;
        // A quarter turn right of the way the body faces.
        double outX = -side * Math.cos(yaw);
        double outZ = -side * Math.sin(yaw);
        return new Vec3(feet.x + outX, feet.y + SHOULDER_ABOVE_THE_SEAT, feet.z + outZ);
    }

    /**
     * Whether whatever this player is holding should be left undrawn.
     * <p>Both hands are busy: one is out over the felt and the other is holding a hand of cards.
     * A sword drawn through the fan is the single most obviously wrong frame this feature can
     * produce, and it is the frame a player gets by sitting down midway through a fight.
     * <p>The seated player's own first-person hand is <em>not</em> this: {@link TableCameraView}
     * already sets {@code hideGui}, which takes the crosshair and the held item together.
     */
    public static boolean hidesHeldItems(Player player) {
        return poses(player);
    }

    /**
     * How many cards to draw in this player's off hand, and what their backs look like.
     * <p>A count and a sleeve. It is not possible to hand this a card, and that is the point: the
     * class that draws the fan has no route to an identity, so no future change to it can leak
     * one. See {@link SeatedPlayers}.
     */
    public static Optional<SeatedPlayers.Seated> fanOf(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        if (!poses(player)) {
            return Optional.empty();
        }
        // An empty hand holds nothing: a fan of no cards drew a sliver at the wrist.
        return SeatedPlayers.of(player.getUUID()).filter(seated -> seated.cards() > 0);
    }
}
