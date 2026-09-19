package dev.gathering.client;

import dev.gathering.core.ui.TablePose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Putting a {@link TablePose.Aim} onto the parts of a player model.
 * <p>Both loaders' model hooks are one line calling this, which is the whole point: the hooks are
 * two files that have to stay identical, and everything either of them could get wrong is in here
 * instead, once.
 * <p><b>Degrees to radians, and the signs.</b> {@link TablePose} speaks in degrees about a body -
 * how far the arm has come away from hanging, how far forward - and the model speaks in radians
 * about parts, mirrored between left and right. This is the translation, and it is the part that
 * had to be settled with the game open rather than reasoned about.
 * <p><b>Nothing is remembered.</b> One {@code PlayerModel} draws every player in the frame, so
 * every answer comes from the entity handed in.
 * <p>Client-only.
 */
public final class TablePoseParts {

    private TablePoseParts() {
    }

    /**
     * Poses this model for this entity, if it is a seated player, and otherwise leaves it alone.
     * <p>Runs for every drawn player in every frame, so the cheap refusal comes first.
     */
    public static void pose(PlayerModel<?> model, LivingEntity entity) {
        if (model == null || !(entity instanceof Player player) || !TableBodyPose.poses(player)) {
            return;
        }
        TablePose.Aim aim = TableBodyPose.aimOf(player, partialTick());

        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart pointing = rightHanded ? model.rightArm : model.leftArm;
        ModelPart holding = rightHanded ? model.leftArm : model.rightArm;

        // The arm is hinged at the shoulder: swing it out sideways first, then bring it forward.
        // Vanilla's own idle bob pushes the right arm's zRot positive and the left arm's negative
        // as the arms drift away from the body, which is what says which way "out" is on each side.
        float out = (float) Math.toRadians(aim.armSwing()) * (rightHanded ? 1f : -1f);
        // Forward is negative xRot - an arm raised in front of the body runs toward -x - which is
        // the sign the brief warned would be written the other way round first.
        float forward = (float) -Math.toRadians(aim.armPitch());
        pointing.zRot = out;
        pointing.xRot = forward;

        // The other hand holds the cards, at the edge of the table, wherever the pointing one is.
        holding.zRot = rightHanded ? -CARDS_OUT : CARDS_OUT;
        holding.xRot = -CARDS_UP;

        // The head is not the arm: it turns to look at what it cannot reach, and it is set rather
        // than added to, because HumanoidModel has already pointed it wherever the entity is
        // facing and a seated player's body has stopped turning.
        model.head.yRot = (float) Math.toRadians(aim.headYaw());
        model.head.xRot = (float) Math.toRadians(aim.headPitch());

        // And the clothes. PlayerModel#setupAnim copies every arm onto its sleeve as the last
        // thing it does, and this runs after that - so a part moved here leaves its jacket behind,
        // floating where the arm used to be. Every part touched above is re-copied.
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.hat.copyFrom(model.head);
    }

    /**
     * How far out from the body the hand of cards is held, and how far up, in radians.
     * <p>A hand of cards at a table is held low and close: up at the table's edge rather than up
     * at the chin, which is where a card game is played and is also the only place it does not
     * cover the player's own face.
     */
    private static final float CARDS_OUT = (float) Math.toRadians(12);

    private static final float CARDS_UP = (float) Math.toRadians(52);

    /**
     * How far into the current tick this frame is.
     * <p>Not a parameter of the method being injected into, so it comes off the game's own frame
     * timer. False, because what is being interpolated is a body being drawn in the world and not
     * something running on its own clock while the game is paused.
     */
    private static float partialTick() {
        Minecraft client = Minecraft.getInstance();
        return client.getTimer() == null ? 0f : client.getTimer().getGameTimeDeltaPartialTick(false);
    }
}
