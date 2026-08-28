package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.gathering.core.ui.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * A printed face, drawn as a card somebody is holding.
 *
 * <p>Not a picture with a transform on it. The card is a rectangle in space turned about its
 * own middle and projected through {@link CardLens}, drawn as a grid of quads rather than one:
 * a single quad under a perspective has its texture stretched wrong across the diagonal, which
 * is the warp old console games are remembered for, and subdividing it is the whole fix.
 *
 * <p><b>The shine is painted onto that same grid.</b> That is the important part of the design
 * and not an implementation detail. It means the foil is made of the card's own points, so it
 * turns with the card exactly, it cannot drift a pixel out of register with the art, and there
 * is no arrangement of angles that puts any of it outside the card - there is nothing to clip,
 * because nothing is ever drawn off the card in the first place. The earlier version raked
 * bands across a bigger area and trimmed them with a scissor, which worked only for as long as
 * nobody asked it a question the scissor could not answer.
 *
 * <p>Drawn through the ordinary interface pipeline, the same immediate-mode call vanilla's own
 * {@code blit} uses, in the vertex order it uses - so the winding matches and nothing about
 * culling, blending or the projection has to be touched or put back.
 *
 * <p>Client-only.
 */
public final class TiltedFace {

    /**
     * How finely the picture is divided.
     *
     * <p>Enough that the texture cannot visibly warp across a cell at the small angles this
     * ever uses. It does not need to be finer: the warp is a second-order effect and a card
     * turned nine degrees has almost none of it.
     */
    private static final int FACE_COLUMNS = 10;
    private static final int FACE_ROWS = 14;

    /**
     * And how finely the shine is.
     *
     * <p>Much finer, because this grid is carrying color rather than a picture: the color is
     * interpolated across each cell, so the mesh is the resolution of the gradient. Coarse
     * enough to see the cells is coarse enough to see bands, and bands are the thing that
     * made the first foil look like tape.
     */
    private static final int SHINE_COLUMNS = 26;
    private static final int SHINE_ROWS = 36;

    /** The shadow the card casts on the backdrop behind it, and how far it drops and slides. */
    private static final int SHADOW = 0x59000000;
    private static final float SHADOW_DROP = 9f;
    private static final float SHADOW_REACH = 13f;

    private TiltedFace() {
    }

    /**
     * Draws the face, its shine if it is a foil, and the shadow it casts.
     *
     * <p>A card lying flat with no foil on it is drawn by the plain path instead, so every
     * screen that shows a card in a list is untouched by any of this and costs exactly what it
     * did before.
     *
     * @param slide where the light is across the card, minus one to one
     * @param grain a stable number about the printing, so its sparkle is its own
     */
    public static void draw(
            GuiGraphics graphics, ResourceLocation texture, Rect where,
            float yaw, float pitch, boolean foil, long grain, float slide) {
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
            castShadow(matrix, lens, slide);
        }
        paint(matrix, lens, texture);
        if (foil) {
            FoilSheen.paint(matrix, lens, slide, grain, SHINE_COLUMNS, SHINE_ROWS);
        }
    }

    /**
     * The picture itself, as a grid of textured quads.
     *
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
        for (int column = 0; column < FACE_COLUMNS; column++) {
            float leftU = column / (float) FACE_COLUMNS;
            float rightU = (column + 1) / (float) FACE_COLUMNS;
            for (int row = 0; row < FACE_ROWS; row++) {
                float topV = row / (float) FACE_ROWS;
                float bottomV = (row + 1) / (float) FACE_ROWS;
                textured(buffer, matrix, lens, corner, leftU, topV);
                textured(buffer, matrix, lens, corner, leftU, bottomV);
                textured(buffer, matrix, lens, corner, rightU, bottomV);
                textured(buffer, matrix, lens, corner, rightU, topV);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void textured(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner, float u, float v) {
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0], corner[1], 0f).setUv(u, v);
    }

    /**
     * The shadow, as one quad the shape of the turned card, slid the way the light is not.
     *
     * <p>The card's own outline rather than its upright rectangle, so it narrows as the card
     * narrows. A shadow that kept the flat shape read as a gray slab lying beside the card,
     * which is worse than no shadow at all.
     */
    private static void castShadow(Matrix4f matrix, CardLens lens, float slide) {
        float cast = -slide * SHADOW_REACH;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer =
                Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] corner = new float[2];
        shade(buffer, matrix, lens, corner, 0f, 0f, cast);
        shade(buffer, matrix, lens, corner, 0f, 1f, cast);
        shade(buffer, matrix, lens, corner, 1f, 1f, cast);
        shade(buffer, matrix, lens, corner, 1f, 0f, cast);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void shade(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner,
            float u, float v, float cast) {
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0] + cast, corner[1] + SHADOW_DROP, 0f).setColor(SHADOW);
    }
}
