package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.gathering.core.ui.CardMesh;
import dev.gathering.core.ui.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * A printed face, drawn as a card somebody is holding.
 * <p>Not a picture with a transform on it. The card is a real shape in space turned about its
 * own middle and projected through {@link CardLens}, drawn as many quads rather than one: a
 * single quad under a perspective has its texture stretched wrong across the diagonal, which
 * is the warp old console games are remembered for, and subdividing it is the whole fix.
 * <p>The shape is {@link CardMesh}, which is a rectangle with its corners cut, because that is
 * what a card is. The picture the mod fetches is a rectangle - a scan has to be - so the
 * rounding is the mod's to do, and it is done by covering the card in quads that stop where the
 * card stops rather than by painting the corners out afterwards, which would need to know what
 * is behind the card and that changes from screen to screen.
 * <p><b>The shine is painted onto that same grid.</b> That is the important part of the design
 * and not an implementation detail. It means the foil is made of the card's own points, so it
 * turns with the card exactly, it cannot drift a pixel out of register with the art, and there
 * is no arrangement of angles that puts any of it outside the card - there is nothing to clip,
 * because nothing is ever drawn off the card in the first place. The earlier version raked
 * bands across a bigger area and trimmed them with a scissor, which worked only for as long as
 * nobody asked it a question the scissor could not answer.
 * <p>Drawn through the ordinary interface pipeline, the same immediate-mode call vanilla's own
 * {@code blit} uses, in the vertex order it uses - so the winding matches and nothing about
 * culling, blending or the projection has to be touched or put back.
 * <p>Client-only.
 */
public final class TiltedFace {

    /**
     * How finely the picture is divided.
     * <p>Enough that the texture cannot visibly warp across a cell at the small angles this
     * ever uses. It does not need to be finer: the warp is a second-order effect and a card
     * turned nine degrees has almost none of it.
     */
    private static final int FACE_COLUMNS = 10;
    private static final int FACE_ROWS = 14;

    /** How many pieces each rounded corner is drawn in. Enough that the curve is a curve. */
    private static final int FACE_ARC = 10;
    private static final int SHINE_ARC = 12;

    /** The shadow only needs a silhouette, so it is walked as coarsely as a shape allows. */
    private static final int SHADOW_ARC = 10;

    /**
     * And how finely the shine is.
     * <p>Much finer, because this grid is carrying color rather than a picture: the color is
     * interpolated across each cell, so the mesh is the resolution of the gradient. Coarse
     * enough to see the cells is coarse enough to see bands, and bands are the thing that
     * made the first foil look like tape.
     */
    private static final int SHINE_COLUMNS = 26;
    private static final int SHINE_ROWS = 36;


    /**
     * The shadow the card casts on the backdrop behind it, and how far it drops and slides.
     * <p>Fractions of the card rather than a number of pixels. They were pixels, which is why
     * it was "cast much too far behind the card": nine pixels down and thirteen across is a
     * hint under a card filling the screen and a slab lying well clear of one drawn small,
     * and the same card at two sizes had two different lights on it.
     */
    private static final int SHADOW = 0x59000000;
    private static final float SHADOW_DROP = 0.030f;
    private static final float SHADOW_REACH = 0.045f;

    private TiltedFace() {
    }

    /**
     * Draws the face, its shine if it is a foil, and the shadow it casts.
     * <p>A card lying flat with no foil on it is drawn by the plain path instead, so every
     * screen that shows a card in a list is untouched by any of this and costs exactly what it
     * did before.
     *
     * @param shineX where the light is across the card, minus one to one
     * @param shineY and up and down it, so tipping the card either way moves the shine
     * @param grain a stable number about the printing, so its sparkle is its own
     */
    public static void draw(
            GuiGraphics graphics, ResourceLocation texture, Rect where,
            float yaw, float pitch, boolean foil, long grain, float shineX, float shineY) {
        if (!foil && yaw == 0f && pitch == 0f) {
            // Every card in every list takes this line and nothing else. Answered before a
            // lens is even built, because this runs for every card on a board on every frame
            // and the cost of a feature nobody asked for there should be two comparisons.
            graphics.blit(texture, where.x(), where.y(), where.width(), where.height(),
                    0f, 0f, 1, 1, 1, 1);
            return;
        }
        CardLens lens = CardLens.of(where, yaw, pitch);
        Matrix4f matrix = graphics.pose().last().pose();
        if (!lens.isSquare()) {
            castShadow(matrix, lens, where, shineX);
        }
        paint(matrix, lens, texture);
        if (foil) {
            FoilSheen.paint(matrix, lens, shineX, shineY, grain, SHINE_COLUMNS, SHINE_ROWS);
        }
    }

    /**
     * The picture itself, as a grid of textured quads.
     * <p>Vertices in the order vanilla's own blit uses - top left, bottom left, bottom right,
     * top right - so the winding is the winding the interface already culls by, and this needs
     * no state of its own.
     */
    private static void paint(Matrix4f matrix, CardLens lens, ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer =
                Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float[] corner = new float[2];
        CardMesh.walk(lens.aspect(), FACE_COLUMNS, FACE_ROWS, FACE_ARC,
                (u1, v1, u2, v2, u3, v3, u4, v4) -> {
                    textured(buffer, matrix, lens, corner, u1, v1);
                    textured(buffer, matrix, lens, corner, u2, v2);
                    textured(buffer, matrix, lens, corner, u3, v3);
                    textured(buffer, matrix, lens, corner, u4, v4);
                });
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void textured(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner, float u, float v) {
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0], corner[1], 0f).setUv(u, v);
    }

    /**
     * The shadow, as one quad the shape of the turned card, slid the way the light is not.
     * <p>The card's own outline rather than its upright rectangle, so it narrows as the card
     * narrows. A shadow that kept the flat shape read as a gray slab lying beside the card,
     * which is worse than no shadow at all.
     */
    private static void castShadow(Matrix4f matrix, CardLens lens, Rect where, float shineX) {
        // Measured off the card's own height both ways, so the light comes from one place
        // whatever size the card is drawn at rather than from further away the smaller it is.
        float span = Math.max(1, where.height());
        float cast = -shineX * SHADOW_REACH * span;
        float drop = SHADOW_DROP * span;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer =
                Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] corner = new float[2];
        // The card's own outline, corners and all, so what falls behind it is card-shaped.
        // Walked coarsely: a shadow needs a silhouette and nothing inside it.
        CardMesh.walk(lens.aspect(), 1, 1, SHADOW_ARC, (u1, v1, u2, v2, u3, v3, u4, v4) -> {
            shade(buffer, matrix, lens, corner, u1, v1, cast, drop);
            shade(buffer, matrix, lens, corner, u2, v2, cast, drop);
            shade(buffer, matrix, lens, corner, u3, v3, cast, drop);
            shade(buffer, matrix, lens, corner, u4, v4, cast, drop);
        });
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void shade(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner,
            float u, float v, float cast, float drop) {
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0] + cast, corner[1] + drop, 0f).setColor(SHADOW);
    }
}
