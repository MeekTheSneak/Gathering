package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardFaceSummary;
import dev.gathering.network.CardSummary;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Draws a card as the actual card, rather than as a generic icon.
 *
 * <p>A card item in your hand shows its own printed face, fetched from Scryfall and cached
 * by this client. Turn it over and it shows the other side: the back face for a double-faced
 * card, and the card back for everything else.
 *
 * <p><b>The card back is ours.</b> The mod ships no Wizards artwork - that is a pillar of the
 * project, not an oversight - so the back is our own design, and it is the seed of the sleeve
 * system: a card's back is the sleeve it sits in, dyeable and eventually player-supplied.
 *
 * <p>Both loaders call this; only the plumbing that reaches it differs.
 *
 * <p>Client-only.
 */
public final class CardFaceRenderer {

    /** Our own card back. Never a Wizards one. */
    public static final ResourceLocation CARD_BACK =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "textures/card/back.png");

    /** Printed proportions, 2.5 by 3.5 inches, in the 1/16-block units item models use. */
    private static final float WIDTH = 0.64f;
    private static final float HEIGHT = WIDTH * (3.5f / 2.5f);
    private static final float HALF_THICKNESS = 0.002f;

    private static final int FULL_BRIGHT_UNUSED = OverlayTexture.NO_OVERLAY;

    private CardFaceRenderer() {
    }

    /**
     * Renders the card held in this stack.
     *
     * <p>Always draws something card-shaped. A stack with no card on it - the blank from the
     * creative tab - shows as a card back, which is what a blank card is; drawing nothing
     * would make the item invisible in the creative menu.
     */
    public static void render(
            ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ResourceLocation front = CardItem.cardOf(stack)
                .map(CardFaceRenderer::faceTexture)
                .orElse(CARD_BACK);

        poseStack.pushPose();
        // Item models are drawn in a 1x1 space with the origin at a corner; centre the card.
        poseStack.translate(0.5f, 0.5f, 0.5f);

        Matrix4f pose = poseStack.last().pose();
        drawFace(buffers, pose, front, packedLight, HALF_THICKNESS);
        // The reverse is always the card back, so a card seen from behind looks like a card.
        drawFace(buffers, pose, CARD_BACK, packedLight, -HALF_THICKNESS);

        poseStack.popPose();
    }

    /**
     * Which texture faces the viewer.
     *
     * <p>Unflipped: the printed front. Flipped: the other face if the card has one, otherwise
     * the back. A card whose art has not been fetched yet falls back to the back rather than
     * to nothing, so a hand full of cards looks like a hand full of cards while art loads.
     */
    static ResourceLocation faceTexture(CardComponent card) {
        Optional<CardSummary> summary = ClientCardCache.get().summary(card);
        if (summary.isEmpty()) {
            return CARD_BACK;
        }

        CardSummary details = summary.get();
        Optional<CardFaceSummary> face = card.flipped()
                ? details.back()
                : Optional.of(details.front());
        if (face.isEmpty()) {
            // Flipped, and there is no other side: this is a normal card, so show its back.
            return CARD_BACK;
        }

        return face.get().readableImage()
                .flatMap(url -> ClientCardImages.get().texture(url))
                .orElse(CARD_BACK);
    }

    /**
     * One textured quad.
     *
     * <p>Two of them back to back with a hair of separation, rather than one double-sided
     * quad, so the card has a front and a back and neither z-fights with the other.
     */
    private static void drawFace(
            MultiBufferSource buffers, Matrix4f pose, ResourceLocation texture, int packedLight, float z) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));

        float left = -WIDTH / 2f;
        float right = WIDTH / 2f;
        float bottom = -HEIGHT / 2f;
        float top = HEIGHT / 2f;
        float normal = z >= 0 ? 1f : -1f;

        if (z >= 0) {
            vertex(consumer, pose, left, bottom, z, 0f, 1f, packedLight, normal);
            vertex(consumer, pose, right, bottom, z, 1f, 1f, packedLight, normal);
            vertex(consumer, pose, right, top, z, 1f, 0f, packedLight, normal);
            vertex(consumer, pose, left, top, z, 0f, 0f, packedLight, normal);
        } else {
            // Wound the other way so the back face is not culled, and mirrored so the back
            // reads the right way round rather than as a mirror image.
            vertex(consumer, pose, right, bottom, z, 0f, 1f, packedLight, normal);
            vertex(consumer, pose, left, bottom, z, 1f, 1f, packedLight, normal);
            vertex(consumer, pose, left, top, z, 1f, 0f, packedLight, normal);
            vertex(consumer, pose, right, top, z, 0f, 0f, packedLight, normal);
        }
    }

    private static void vertex(
            VertexConsumer consumer, Matrix4f pose,
            float x, float y, float z, float u, float v, int packedLight, float normal) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(FULL_BRIGHT_UNUSED)
                .setLight(packedLight)
                .setNormal(0f, 0f, normal);
    }
}
