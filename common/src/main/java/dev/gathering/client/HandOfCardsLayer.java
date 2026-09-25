package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gathering.core.ui.HeldFan;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The cards in a seated player's off hand, fanned, backs out.
 * <p>A vanilla {@code RenderLayer}, so it lives here in {@code :common} beside
 * {@link CardItemRenderer} for the same reason that one does - the class it extends is vanilla,
 * both loaders can draw it, and there is no reason for two copies. Each loader only registers it.
 * <p><b>It is given a number.</b> Not a hand, not a list, not a view - a number. The count of
 * another player's hand is public: the whole table watches you draw, and {@code VisibilityRules}
 * has always sent it as {@code ZoneView.countOnly}. An identity is not, and the way this class is
 * kept honest about that is that it has no type in scope that could carry one -
 * {@link CardFaceRenderer#renderBack} takes nothing at all.
 * <p>Not while this client's table camera is over the table the player is seated at: the board
 * there draws every other seat's hand on the felt at their edge, and one hand is drawn once.
 * <p>Client-only.
 */
public class HandOfCardsLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /**
     * How big a card is drawn in a held hand, as a share of the card renderer's own card.
     * <p>That card is sized for a block's surface, where a mat is a table's worth of felt. A card
     * in a fist is a smaller thing: big enough to read as cards from across a room, small enough
     * that a hand of seven is a hand rather than a hoarding.
     */
    private static final float CARD_SCALE = 0.42f;

    /** How far out of the fist the cards stand, in the arm part's own units. */
    private static final float OUT_OF_THE_HAND = 5.5f;

    /** And how far down the arm the fist is: the far end of a twelve-pixel arm. */
    private static final float DOWN_THE_ARM = 10.5f;

    public HandOfCardsLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // Before the pose stack is touched at all: this runs for every player in every frame, and
        // almost every one of them is standing up somewhere else entirely.
        SeatedPlayers.Seated seated = TableBodyPose.fanOf(player).orElse(null);
        if (seated == null) {
            return;
        }
        // Not while this client's table camera is over that player's table. The board there draws
        // their hand on the felt at the edge they sit at, which is where this fan is held, and the
        // camera keeps the players at that table in view: both would be one player's hand twice,
        // and past ten cards two different counts of it.
        if (TableCameraView.isLookingAt(seated.table())) {
            return;
        }
        List<HeldFan.Card> fan = HeldFan.of(seated.cards());
        if (fan.isEmpty()) {
            return;
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        // The hand that is not pointing. Read from the player rather than assumed, or a
        // left-handed player's cards are drawn straight through their pointing arm.
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart holding = rightHanded ? model.leftArm : model.rightArm;

        poseStack.pushPose();
        // Onto the arm the pose class moved, and turning with it, rather than placed beside where
        // the arm used to be: the fan rides the hand through every frame of the arm coming up.
        holding.translateAndRotate(poseStack);
        // Model parts are drawn in sixteenths of a block, and the card renderer works in blocks.
        poseStack.translate(0.0f, DOWN_THE_ARM / 16.0f, -OUT_OF_THE_HAND / 16.0f);
        poseStack.scale(CARD_SCALE, CARD_SCALE, CARD_SCALE);
        // Backs to the room. The fan is held facing its owner, which is what makes it a hand of
        // cards rather than a display of one.
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));

        for (HeldFan.Card card : fan) {
            poseStack.pushPose();
            poseStack.translate(card.slide(), card.lift(), card.depth());
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(card.angle()));
            CardFaceRenderer.renderBack(poseStack, buffers, packedLight);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
