package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gathering.Gathering;
import dev.gathering.core.ui.PackWrapper;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Draws a sealed pack as its own set's pack.
 * <p>A wrapper and a symbol printed on it. The wrapper is the mod's own artwork - the one
 * thing in this picture that ships in the jar - and the symbol is fetched by this client from
 * Scryfall like card art is, then printed in the color of the product: black for a draft
 * booster, gold for a set or play booster, the mythic's orange for a collector.
 * <p>A symbol that has not arrived yet is simply not drawn, so a pack looks like a plain
 * wrapper for a moment and like its own set's wrapper after that. Nothing waits, nothing
 * flickers, and a set whose symbol will never come is a plain pack rather than a hole.
 * <p>Client-only.
 */
public final class PackFaceRenderer {

    /** The wrapper. Ours, and the only picture here that is in the jar. */
    public static final ResourceLocation WRAPPER =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "textures/item/pack.png");

    /** The wrapper texture is square with its own margins, so the quad is the whole item. */
    private static final float SIDE = 1.0f;

    /**
     * Half the thickness of a pack, and where the printed wrapper stops.
     * <p>A booster is a bag with cards in it, not a sheet of paper: it used to be two quads a hundredth of a
     * block apart, so held in the third person - where a player is looked at from the side - it was a line.
     * The owner asked for one with depth (2026-09-16). Six faces now, the four edges taking a pixel of the
     * wrapper's own border so the sides are the color the front is.
     * <p>The wrapper is printed on the middle twelve pixels of a sixteen-pixel texture, which is where the
     * edges of the bag are and so where the sides of the box go.
     */
    private static final float HALF_THICKNESS = 0.03f;

    private static final float PRINTED_U0 = 2f / 16f;
    private static final float PRINTED_U1 = 14f / 16f;
    private static final float HALF_WIDTH = SIDE * (PRINTED_U1 - PRINTED_U0) / 2f;
    private static final float HALF_HEIGHT = SIDE / 2f;
    /** One pixel in from each edge, which is the strip of wrapper the sides are printed with. */
    private static final float PIXEL = 1f / 16f;

    /** How much of the wrapper the symbol covers, and where the middle of the body is. */
    private static final float SYMBOL_SIDE = 0.34f;
    private static final float SYMBOL_DROP = 0.09f;

    /** Drawn at this many pixels and scaled, which is plenty for an item and cheap to keep. */
    private static final int SYMBOL_PIXELS = 64;

    private PackFaceRenderer() {
    }

    public static void render(
            ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        PackComponent pack = PackItem.packOf(stack).orElse(null);

        poseStack.pushPose();
        // Item models are drawn in a 1x1 space with the origin at a corner; center the pack.
        poseStack.translate(0.5f, 0.5f, 0.5f);
        Matrix4f pose = poseStack.last().pose();

        quad(buffers, pose, WRAPPER, packedLight, HALF_THICKNESS, SIDE, 0f, 0xFFFFFFFF);
        // Both sides are the wrapper. A booster has no back worth telling from its front, and
        // an unopened one has nothing behind it to hide.
        quad(buffers, pose, WRAPPER, packedLight, -HALF_THICKNESS, SIDE, 0f, 0xFFFFFFFF);
        edges(buffers, pose, packedLight);

        symbolFor(pack).ifPresent(symbol -> {
            int color = PackWrapper.symbolColor(pack.kind());
            quad(buffers, pose, symbol, packedLight, HALF_THICKNESS * 1.02f,
                    SYMBOL_SIDE, -SYMBOL_DROP, color);
            quad(buffers, pose, symbol, packedLight, -HALF_THICKNESS * 1.02f,
                    SYMBOL_SIDE, -SYMBOL_DROP, color);
        });

        poseStack.popPose();
    }

    /**
     * The four sides of the bag, each a pixel of the wrapper's own border stretched over the thickness.
     * <p>Wound so that each face is front-facing from outside: the render type culls back faces, and a side
     * wound the wrong way round is a hole you can see the inside of the pack through.
     */
    private static void edges(MultiBufferSource buffers, Matrix4f pose, int packedLight) {
        float w = HALF_WIDTH;
        float h = HALF_HEIGHT;
        float d = HALF_THICKNESS;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(WRAPPER));

        // Left, then right: a column of the wrapper a pixel wide, running the height of the bag.
        side(consumer, pose, packedLight, -1f, 0f,
                -w, -h, -d, PRINTED_U0, 1f,
                -w, -h, d, PRINTED_U0 + PIXEL, 1f,
                -w, h, d, PRINTED_U0 + PIXEL, 0f,
                -w, h, -d, PRINTED_U0, 0f);
        side(consumer, pose, packedLight, 1f, 0f,
                w, -h, d, PRINTED_U1 - PIXEL, 1f,
                w, -h, -d, PRINTED_U1, 1f,
                w, h, -d, PRINTED_U1, 0f,
                w, h, d, PRINTED_U1 - PIXEL, 0f);
        // Then the crimped top and the bottom fold, a row of it a pixel deep.
        side(consumer, pose, packedLight, 0f, 1f,
                -w, h, d, PRINTED_U0, 0f,
                w, h, d, PRINTED_U1, 0f,
                w, h, -d, PRINTED_U1, PIXEL,
                -w, h, -d, PRINTED_U0, PIXEL);
        side(consumer, pose, packedLight, 0f, -1f,
                -w, -h, -d, PRINTED_U0, 1f - PIXEL,
                w, -h, -d, PRINTED_U1, 1f - PIXEL,
                w, -h, d, PRINTED_U1, 1f,
                -w, -h, d, PRINTED_U0, 1f);
    }

    /** One side of the bag: four corners in order, each with the piece of wrapper printed on it. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void side(
            VertexConsumer consumer, Matrix4f pose, int packedLight, float normalX, float normalY,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3) {
        corner(consumer, pose, packedLight, normalX, normalY, x0, y0, z0, u0, v0);
        corner(consumer, pose, packedLight, normalX, normalY, x1, y1, z1, u1, v1);
        corner(consumer, pose, packedLight, normalX, normalY, x2, y2, z2, u2, v2);
        corner(consumer, pose, packedLight, normalX, normalY, x3, y3, z3, u3, v3);
    }

    private static void corner(
            VertexConsumer consumer, Matrix4f pose, int packedLight, float normalX, float normalY,
            float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(normalX, normalY, 0f);
    }

    /**
     * The symbol texture for this pack, if this client has it yet.
     * <p>Asked for every frame on purpose: the answer is a map lookup once it is ready, and
     * asking is what starts the fetch the first time. Nothing here blocks.
     */
    private static Optional<ResourceLocation> symbolFor(PackComponent pack) {
        // The archive is not a set, so there is no symbol to fetch and asking would spend a
        // request on a URL that cannot exist. A plain wrapper is the right answer for it.
        if (pack == null || !pack.isReal() || pack.isArchive()) {
            return Optional.empty();
        }
        return ClientSetSymbols.get().symbol(
                pack.setCode(), PackWrapper.symbolColor(pack.kind()), SYMBOL_PIXELS);
    }

    /**
     * One textured quad, tinted.
     * <p>Two of them back to back with a hair of separation rather than one double-sided
     * quad, so the pack has two sides and neither z-fights with the other.
     */
    private static void quad(
            MultiBufferSource buffers, Matrix4f pose, ResourceLocation texture, int packedLight,
            float z, float side, float dropY, int color) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));

        float half = side / 2f;
        float left = -half;
        float right = half;
        float bottom = -half + dropY;
        float top = half + dropY;
        float normal = z >= 0 ? 1f : -1f;

        if (z >= 0) {
            vertex(consumer, pose, left, bottom, z, 0f, 1f, packedLight, normal, color);
            vertex(consumer, pose, right, bottom, z, 1f, 1f, packedLight, normal, color);
            vertex(consumer, pose, right, top, z, 1f, 0f, packedLight, normal, color);
            vertex(consumer, pose, left, top, z, 0f, 0f, packedLight, normal, color);
        } else {
            // Wound the other way so the back face is not culled, and mirrored so what is
            // printed on it reads the right way round rather than as a mirror image.
            vertex(consumer, pose, right, bottom, z, 0f, 1f, packedLight, normal, color);
            vertex(consumer, pose, left, bottom, z, 1f, 1f, packedLight, normal, color);
            vertex(consumer, pose, left, top, z, 1f, 0f, packedLight, normal, color);
            vertex(consumer, pose, right, top, z, 0f, 0f, packedLight, normal, color);
        }
    }

    private static void vertex(
            VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float u, float v,
            int packedLight, float normal, int color) {
        consumer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0f, 0f, normal);
    }
}
