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
        PoseProbe.sawHook(entity);
        if (model == null || !(entity instanceof Player player) || !TableBodyPose.poses(player)) {
            return;
        }
        TablePose.Aim aim = TableBodyPose.aimOf(player, partialTick());
        PoseProbe.sawPose(player, aim);

        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart pointing = rightHanded ? model.rightArm : model.leftArm;
        ModelPart holding = rightHanded ? model.leftArm : model.rightArm;

        // Up from hanging, then round to the side. A model part is turned in Z, then Y, then X,
        // so the Y rotation swings an already-pitched arm horizontally - which is what pointing
        // across a table is. Rolling it sideways instead, which is what this did first, pins the
        // arm against its across-the-body limit and leaves it moving only up and down.
        //
        // Neither sign is mirrored between the arms: the model's Y turns both of them toward the
        // player's right, and raising either one forward is negative in X.
        pointing.xRot = (float) -Math.toRadians(aim.armPitch());
        pointing.yRot = (float) Math.toRadians(aim.armYaw());
        pointing.zRot = 0f;

        // The other hand holds the cards up at the table's edge, turned in toward the chest so the
        // fan is in front of its owner rather than out over somebody else's mat.
        holding.xRot = -CARDS_UP;
        holding.yRot = rightHanded ? CARDS_IN : -CARDS_IN;
        holding.zRot = 0f;

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
     * How far out from the body the hand of cards is held, and how far forward, in radians.
     * <p>A Minecraft arm has no elbow: it is one rigid part hinged at the shoulder, so "forward"
     * and "up" are the same number and the hand ends up wherever that one angle puts it. Held
     * halfway forward and close to the body, the hand lands in front of the hips, which is not
     * where anybody holds cards and reads exactly as badly as it sounds - the owner said so the
     * first time he saw it.
     * <p>So the arm comes most of the way up, to about horizontal, and turns in toward the chest:
     * the hand finishes over the table's near edge holding the fan in front of its owner, which is
     * where a hand of cards is actually held and is plainly away from the body on the way there.
     */
    private static final float CARDS_IN = (float) Math.toRadians(22);

    private static final float CARDS_UP = (float) Math.toRadians(74);

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
