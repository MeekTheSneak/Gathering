package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * A booster wrapper, drawn as a thing in space rather than a rectangle on the glass.
 *
 * <p>The same trick the inspect panel plays on a card, for the same reason: the interface
 * draws in an orthographic projection, where turning a flat picture is only ever a squash -
 * both edges keep their length and nothing comes toward you. So the pack is a rectangle in
 * space, turned about its own middle and projected through a pinhole a fixed distance in
 * front, drawn as a grid of quads so the picture does not warp across it. See {@link CardLens},
 * which is the pinhole, and {@link TiltedFace}, which does this for a card.
 *
 * <p>Where this differs from a card is the top edge. A card is a rectangle with its corners
 * cut; a pack being opened is a rectangle whose top edge is wherever the tear has got to, and
 * that edge is different in every column. So the mesh is built column by column from a line
 * handed in, which is the same line the light along the tear is drawn from - one line, read
 * twice, rather than two shapes that have to be kept in step.
 *
 * <p>It is also why this replaced a scissor per column: a scissor is a screen-space rectangle,
 * and once the pack is turned there are no screen-space rectangles left to clip to. Building
 * the tear into the mesh costs one draw call for the whole pack instead of one per column, so
 * the turned pack is cheaper than the flat one was.
 *
 * <p>Client thread only.
 */
public final class TiltedPack {

    /**
     * How many rows each piece of the wrapper is cut into, down the pack.
     *
     * <p>Enough that the perspective is a curve rather than a fold. The columns come from the
     * tear, which has plenty of them, so only the down direction needs saying here.
     */
    private static final int BODY_ROWS = 6;
    private static final int STRIP_ROWS = 2;

    private TiltedPack() {
    }

    /**
     * Where a piece of the wrapper's picture is, in the texture.
     *
     * <p>The wrapper is one small picture of a whole pack and this screen draws two pieces of
     * one, each stretched differently: the crimped strip is squashed into the top sixth while
     * the body fills the rest. So a piece carries the rows it is cut from and the run of the
     * pack it is laid along, and the mapping inside it is a straight line - which is what
     * keeps a quad's texture coordinates honest.
     *
     * @param fromRow  the first texture row this piece is cut from
     * @param rows     how many rows it is cut from
     * @param fromDown where the piece starts down the pack, from nought to one
     * @param down     how much of the pack it covers
     */
    public record Piece(float fromRow, float rows, float fromDown, float down) {

        /** Which texture row a point this far down the pack falls on, from nought to one. */
        float rowAt(float downThePack, float texturePixels) {
            float within = down <= 0f ? 0f : (downThePack - fromDown) / down;
            return (fromRow + within * rows) / texturePixels;
        }
    }

    /**
     * Draws the wrapper, turned, with its top edge where the tear has left it.
     *
     * @param topAt   how far down the pack each column's paper begins, from nought to one.
     *     Its length is the number of columns.
     * @param margin  how much of the texture's width either side is empty, in texture pixels
     * @param pixels  how many pixels the texture is across and down
     */
    public static void draw(
            Matrix4f matrix, CardLens lens, ResourceLocation texture,
            float[] topAt, Piece piece, int rows, float margin, float pixels) {
        if (topAt == null || topAt.length == 0 || rows < 1) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float[] corner = new float[2];
        float bottom = piece.fromDown() + piece.down();
        int columns = topAt.length;
        boolean anything = false;

        for (int column = 0; column < columns; column++) {
            // A column whose paper starts below this piece has none of this piece in it -
            // which is every column the tear has passed, for the strip it tore off.
            float top = Math.max(topAt[column], piece.fromDown());
            if (top >= bottom) {
                continue;
            }
            float u0 = column / (float) columns;
            float u1 = (column + 1) / (float) columns;
            for (int row = 0; row < rows; row++) {
                float v0 = top + (bottom - top) * row / rows;
                float v1 = top + (bottom - top) * (row + 1) / rows;
                // Vanilla's winding: top left, bottom left, bottom right, top right.
                at(buffer, matrix, lens, corner, u0, v0, piece, margin, pixels);
                at(buffer, matrix, lens, corner, u0, v1, piece, margin, pixels);
                at(buffer, matrix, lens, corner, u1, v1, piece, margin, pixels);
                at(buffer, matrix, lens, corner, u1, v0, piece, margin, pixels);
                anything = true;
            }
        }
        if (anything) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            // A buffer nothing was written into throws rather than drawing nothing, and a
            // pack torn all the way across has no strip left to draw.
            buffer.build();
        }
    }

    private static void at(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner,
            float u, float v, Piece piece, float margin, float pixels) {
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0], corner[1], 0f)
                .setUv((margin + u * (pixels - 2f * margin)) / pixels, piece.rowAt(v, pixels));
    }

    /** What color comes out of the tear, and how brightly where it meets the paper. */
    public record Glow(int rgb, int alpha) {
    }

    /**
     * The light coming out along the torn edge.
     *
     * <p>Bands stacked upward from the paper, each fainter than the one below, so the pack
     * reads as lit from inside rather than outlined. Through the same lens the paper went
     * through, from the same line, so there is no angle at which the light and the tear come
     * apart.
     *
     * @param reach how far up the pack the light carries, from nought to one
     * @param torn  how many of the columns the tear has passed
     * @param bands how many steps the light is faded over
     */
    public static void shine(
            Matrix4f matrix, CardLens lens, float[] topAt, float reach, Glow glow,
            int torn, int bands) {
        if (topAt == null || torn <= 0 || bands < 1 || reach <= 0f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] corner = new float[2];
        int columns = Math.min(torn, topAt.length);
        for (int column = 0; column < columns; column++) {
            float u0 = column / (float) topAt.length;
            float u1 = (column + 1) / (float) topAt.length;
            for (int band = 0; band < bands; band++) {
                float from = topAt[column] - reach * band / bands;
                float to = topAt[column] - reach * (band + 1) / bands;
                int top = fade(glow, (band + 1) / (float) bands);
                int under = fade(glow, band / (float) bands);
                lit(buffer, matrix, lens, corner, u0, to, top);
                lit(buffer, matrix, lens, corner, u0, from, under);
                lit(buffer, matrix, lens, corner, u1, from, under);
                lit(buffer, matrix, lens, corner, u1, to, top);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** The light this far from the paper, falling away faster than it goes. */
    private static int fade(Glow glow, float away) {
        float left = 1f - Math.min(1f, Math.max(0f, away));
        return (Math.round(glow.alpha() * left * left) << 24) | (glow.rgb() & 0x00FFFFFF);
    }

    private static void lit(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner,
            float u, float v, int color) {
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0], corner[1], 0f).setColor(color);
    }

    /**
     * A flat quad laid on the turned pack, for anything printed on the wrapper.
     *
     * <p>The set symbol, which is a picture in its own right rather than part of the wrapper,
     * so it takes its whole texture and only borrows the pack's shape. Subdivided too: a
     * symbol drawn as one quad on a turned pack is a symbol that slides off the paper it is
     * printed on.
     *
     * @param from the corner of the pack it sits at, and {@code to} the opposite one, both
     *     from nought to one across and down
     */
    public static void print(
            Matrix4f matrix, CardLens lens, ResourceLocation texture,
            float fromU, float fromV, float toU, float toV, int cuts) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float[] corner = new float[2];
        int steps = Math.max(1, cuts);
        for (int column = 0; column < steps; column++) {
            for (int row = 0; row < steps; row++) {
                float a = column / (float) steps;
                float b = (column + 1) / (float) steps;
                float c = row / (float) steps;
                float d = (row + 1) / (float) steps;
                printed(buffer, matrix, lens, corner, fromU, fromV, toU, toV, a, c);
                printed(buffer, matrix, lens, corner, fromU, fromV, toU, toV, a, d);
                printed(buffer, matrix, lens, corner, fromU, fromV, toU, toV, b, d);
                printed(buffer, matrix, lens, corner, fromU, fromV, toU, toV, b, c);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void printed(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner,
            float fromU, float fromV, float toU, float toV, float acrossIt, float downIt) {
        lens.at(fromU + (toU - fromU) * acrossIt, fromV + (toV - fromV) * downIt, corner);
        buffer.addVertex(matrix, corner[0], corner[1], 0f).setUv(acrossIt, downIt);
    }
}
