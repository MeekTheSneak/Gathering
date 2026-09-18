package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * The cards in a seated player's off hand, fanned, backs out.
 * <p>A vanilla {@code RenderLayer}, so it lives here in {@code :common} beside
 * {@link CardItemRenderer} for the same reason that one does - the class it extends is vanilla,
 * both loaders can draw it, and there is no reason for two copies. Each loader only registers it:
 * NeoForge from {@code EntityRenderersEvent.AddLayers}, Fabric from
 * {@code LivingEntityFeatureRendererRegistrationCallback}.
 * <p><b>It is given a number and a sleeve.</b> Not a hand, not a list, not a view - a number. The
 * count of another player's hand is public: the whole table watches you draw, and
 * {@code VisibilityRules} has always sent it as {@code ZoneView.countOnly}. An identity is not,
 * and the way this class is kept honest about that is that it has no type in scope that could
 * carry one.
 * <p>Client-only.
 */
public class HandOfCardsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /**
     * How big a card is drawn in a hand, as a share of a block.
     * <p>A playing card is about six centimetres across and a Minecraft player is not a person, so
     * this is a judgment rather than a conversion: big enough to read as cards from across a room,
     * small enough that a hand of seven is a hand rather than a hoarding. Expect to set it by
     * looking at the pictures.
     */
    private static final float CARD_HEIGHT = 0.28f;

    /** What to draw: a count and what the backs look like. Nothing else, on purpose. */
    public record Fan(int cards, dev.gathering.core.card.Sleeve sleeve) {
    }

    public HandOfCardsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        throw new UnsupportedOperationException("""
                Not written yet. In order:
                  1. TableBodyPose.fanOf(player), else draw nothing - and return before touching the
                     PoseStack at all, because this runs for every player in every frame.
                  2. Translate onto the off arm's model part and rotate with it. The part is
                     getParentModel().leftArm or rightArm depending on player.getMainArm(); use
                     ModelPart#translateAndRotate so the fan rides the arm the pose class moved
                     rather than being placed beside where the arm used to be.
                  3. HandFan.of(fan.cards()) for the per-card angle, slide, lift and depth, scaled
                     by CARD_HEIGHT.
                  4. A back-only draw per card. CardFaceRenderer.CARD_BACK is the texture and
                     CardFaceRenderer already draws quads in world space; give it a public
                     back-only entry point rather than passing it an ItemStack, so that no code
                     path from here can reach a card's face. That entry point is the security
                     boundary, and it is worth a test that asserts it takes a Sleeve and not a
                     CardView.
                  5. Add no texture. tools/artcheck.py holds the hash of all 2,152 assets and the
                     card back and the sleeve emblems already exist.""");
    }
}
