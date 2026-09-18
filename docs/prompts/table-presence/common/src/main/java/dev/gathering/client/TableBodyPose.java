package dev.gathering.client;

import dev.gathering.core.ui.TablePose;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;

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

    private TableBodyPose() {
    }

    /**
     * Whether this player's body should be posed at all.
     * <p>False for everybody standing up, which is the answer almost every frame, and it has to be
     * cheap: this is asked for every drawn player in every frame by a hook inside the model's own
     * setup.
     */
    public static boolean poses(Player player) {
        throw new UnsupportedOperationException("""
                Not written yet. True when SeatedPlayers.of gives a seat. Deliberately not "when
                they have a live pointer": a seated player with no pointer still has one hand full
                of cards and neither hand free for whatever they were carrying, so the pose applies
                and the arm is simply at rest.""");
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
        throw new UnsupportedOperationException("""
                Not written yet, and this is the part that will need a running game. In order:
                  0. everybodyIsStill(), and if so RESTING, before any lookup.
                  1. SeatedPlayers.of(player), else RESTING.
                  2. ClientTablePointing.pointedAt(player.getUUID(), partialTick), else RESTING.
                     A seated player who has closed the table screen has no pointer, because the
                     sender stopped and said so - it is not a case handled here.
                  3. The spot's surface coordinates through TableTop - worldX/worldZ, then
                     inTheWorld, then WorldSpace.toWorld - which is the same route TablePointer
                     takes and must stay the same route, or the arm points at where the table is
                     not on a moving structure.
                  4. That world point, less the shoulder: the player's position, plus
                     SHOULDER_ABOVE_THE_SEAT, offset to the shoulder joint's side of the body.
                  5. Into the player's own frame by rotating about Y by the player's body yaw -
                     yBodyRot, not yHeadRot, because the head is the thing being posed.
                  6. TablePose.reaching(across, forward, down).
                Left-handed players hold their cards in the right hand: read
                player.getMainArm() and mirror, or the cards are drawn over the pointing arm.""");
    }

    /**
     * Whether whatever this player is holding should be left undrawn.
     * <p>Both hands are busy: one is out over the felt and the other is holding a hand of cards.
     * A sword drawn through the fan is the single most obviously wrong frame this feature can
     * produce, and it is the frame a player gets by sitting down mid-fight.
     * <p>The seated player's own first-person hand is <em>not</em> this: {@link TableCameraView}
     * already sets {@code hideGui}, which takes the crosshair and the held item together.
     */
    public static boolean hidesHeldItems(Player player) {
        return poses(player);
    }

    /**
     * The fan of cards to draw in this player's off hand, or empty.
     * <p>A count and a sleeve. It is not possible to hand this a card, and that is the point:
     * the class that draws the fan has no route to an identity, so no future change to it can
     * leak one. See {@link SeatedPlayers}.
     */
    public static Optional<HandOfCardsLayer.Fan> fanOf(Player player) {
        throw new UnsupportedOperationException("""
                Not written yet. SeatedPlayers.of(player) mapped to a Fan of its card count and
                sleeve, and empty for a hand of none - an empty hand holds nothing, and a fan of
                zero cards drew a sliver at the wrist.""");
    }
}
