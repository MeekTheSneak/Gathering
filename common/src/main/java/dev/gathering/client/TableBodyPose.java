package dev.gathering.client;

import dev.gathering.core.ui.Shoulder;
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
 * somewhere else entirely. The felt becomes a place in the world here, once, and
 * {@link Shoulder} turns that place into the body's own frame.
 * <p>Client-only.
 */
public final class TableBodyPose {

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
        if (player == null || !poses(player)) {
            return TablePose.Aim.RESTING;
        }
        // Everything past here is a body already at a table, so what it falls back to is a body at
        // a table - hands at the edge, head down at the felt - and never a person standing at ease.
        if (everybodyIsStill()) {
            return TablePose.Aim.AT_THE_TABLE;
        }
        SeatedPlayers.Seated seated = SeatedPlayers.of(player.getUUID()).orElse(null);
        if (seated == null) {
            return TablePose.Aim.AT_THE_TABLE;
        }
        ClientTablePointing.Spot spot =
                ClientTablePointing.pointedAt(player.getUUID(), partialTick).orElse(null);
        if (spot == null || spot.table() == null || !spot.table().equals(seated.table())) {
            return TablePose.Aim.AT_THE_TABLE;
        }
        Vec3 at = inTheWorld(spot);
        if (at == null) {
            return TablePose.Aim.AT_THE_TABLE;
        }

        // The shoulder, the frame and the angles are all Shoulder's, in :core, where they can
        // be checked in milliseconds - which is where they belong, because getting the shoulder's
        // height wrong is precisely what made every body at a table look at the ceiling. The
        // body's yaw and not the head's: the head is one of the things being posed, and reading it
        // here would make the pose chase itself.
        Vec3 feet = player.getPosition(partialTick);
        TablePose.Aim aim = Shoulder.toward(
                feet.x, feet.y, feet.z, player.yBodyRot,
                player.getMainArm() == HumanoidArm.RIGHT,
                at.x, at.y, at.z);
        // A player who has just stopped lowers their arm back to the table rather than having it
        // vanish, and lands where a seated body sits rather than where a standing one does.
        return TablePose.Aim.AT_THE_TABLE.toward(aim, spot.settling());
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
