package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.gathering.core.ui.PackCloth;
import dev.gathering.core.ui.PackWrapper;
import dev.gathering.core.ui.Rect;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The simulated wrapper, drawn where the solver has put it.
 * <p>One quad per square of the sheet, each keeping the piece of the wrapper's picture it started with -
 * so the printing stretches and creases with the foil rather than sliding about on top of it. A square
 * that has lost a side is not drawn at all, which is what a hole is.
 * <p>The solver works in its own space - nought to one across the sheet and down it, and outside that
 * once a piece has been pulled away - so this is the only place that space meets pixels.
 * <p>Client thread only.
 */
final class PackClothRenderer {

    private PackClothRenderer() {
    }

    /**
     * Draws the wrapper as it now stands.
     *
     * @param where where the pack sits when the sheet is flat and whole
     */
    static void draw(Matrix4f matrix, PackCloth cloth, ResourceLocation texture, Rect where) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        boolean anything = false;
        for (int down = 0; down + 1 < PackCloth.DOWN; down++) {
            for (int across = 0; across + 1 < PackCloth.ACROSS; across++) {
                if (!cloth.stillThere(across, down)) {
                    continue;
                }
                // Vanilla's winding: top left, bottom left, bottom right, top right.
                corner(buffer, matrix, cloth, where, across, down);
                corner(buffer, matrix, cloth, where, across, down + 1);
                corner(buffer, matrix, cloth, where, across + 1, down + 1);
                corner(buffer, matrix, cloth, where, across + 1, down);
                anything = true;
            }
        }
        if (anything) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            // A buffer nothing was written into throws rather than drawing nothing, and a wrapper torn
            // entirely away has no squares left.
            buffer.build();
        }
        // Put back, as it was found: turned on here and left on, it was on for whatever drew next.
        RenderSystem.disableBlend();
    }

    /**
     * The set's symbol, printed on the body of the wrapper and creasing with it.
     * <p>It used to be printed flat over the pack, through the lens the whole pack was drawn with.
     * When the wrapper became a sheet that tears, that lens went and nothing called the printing any
     * more - so the symbol quietly stopped being drawn, while the class that drew it went on saying
     * it did. Drawn here instead, on the same squares as the foil, so it stretches and tears with
     * the paper rather than floating over it.
     *
     * @param across how wide the symbol is as a fraction of the wrapper
     */
    static void drawSymbol(Matrix4f matrix, PackCloth cloth, ResourceLocation symbol, Rect where,
            float across, int color) {
        float side = Math.clamp(across, 0.05f, 1f);
        float left = 0.5f - side / 2f;
        float right = 0.5f + side / 2f;
        // Down the middle of what is left under the crimp, in the sheet's own coordinates.
        float bodyTop = (float) PackWrapper.crimp();
        float middle = bodyTop + (1f - bodyTop) / 2f;
        float tall = side * where.width() / Math.max(1f, where.height());
        float top = middle - tall / 2f;
        float bottom = middle + tall / 2f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, symbol);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        boolean anything = false;
        for (int down = 0; down + 1 < PackCloth.DOWN; down++) {
            for (int sideways = 0; sideways + 1 < PackCloth.ACROSS; sideways++) {
                if (!cloth.stillThere(sideways, down) || !within(sideways, down, left, right, top, bottom)) {
                    continue;
                }
                // Trimmed to the symbol's rectangle rather than stretched to the square: a square
                // straddling its edge used to clamp its outer corners' texture to the edge, smearing
                // the symbol's last column of texels across the rest of the square. Here the square
                // is cut at the edge and the cut corners are placed on the sheet where the edge
                // falls, between the solver's four.
                float x1 = sideways / (float) (PackCloth.ACROSS - 1);
                float x2 = (sideways + 1) / (float) (PackCloth.ACROSS - 1);
                float y1 = down / (float) (PackCloth.DOWN - 1);
                float y2 = (down + 1) / (float) (PackCloth.DOWN - 1);
                float fromX = Math.max(x1, left);
                float toX = Math.min(x2, right);
                float fromY = Math.max(y1, top);
                float toY = Math.min(y2, bottom);
                printed(buffer, matrix, cloth, where, sideways, down, fromX, fromY, x1, x2, y1, y2,
                        left, right, top, bottom, color);
                printed(buffer, matrix, cloth, where, sideways, down, fromX, toY, x1, x2, y1, y2,
                        left, right, top, bottom, color);
                printed(buffer, matrix, cloth, where, sideways, down, toX, toY, x1, x2, y1, y2,
                        left, right, top, bottom, color);
                printed(buffer, matrix, cloth, where, sideways, down, toX, fromY, x1, x2, y1, y2,
                        left, right, top, bottom, color);
                anything = true;
            }
        }
        if (anything) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            buffer.build();
        }
        RenderSystem.disableBlend();
    }

    /** Whether any corner of this square falls inside the symbol's rectangle on the flat sheet. */
    private static boolean within(int across, int down, float left, float right, float top, float bottom) {
        float x1 = across / (float) (PackCloth.ACROSS - 1);
        float x2 = (across + 1) / (float) (PackCloth.ACROSS - 1);
        float y1 = down / (float) (PackCloth.DOWN - 1);
        float y2 = (down + 1) / (float) (PackCloth.DOWN - 1);
        return x2 > left && x1 < right && y2 > top && y1 < bottom;
    }

    /**
     * One corner of the symbol, at a point inside one square of the sheet, with the symbol's texture
     * across it.
     * <p>The point is given as where it sits on the flat sheet, and placed where the solver has moved
     * that part of the square to, between the square's four corners.
     */
    private static void printed(BufferBuilder buffer, Matrix4f matrix, PackCloth cloth, Rect where,
            int across, int down, float sheetX, float sheetY, float x1, float x2, float y1, float y2,
            float left, float right, float top, float bottom, int color) {
        float s = (sheetX - x1) / Math.max(1.0e-6f, x2 - x1);
        float r = (sheetY - y1) / Math.max(1.0e-6f, y2 - y1);
        int topLeft = PackCloth.at(across, down);
        int topRight = PackCloth.at(across + 1, down);
        int bottomLeft = PackCloth.at(across, down + 1);
        int bottomRight = PackCloth.at(across + 1, down + 1);
        float ax = cloth.xOf(topLeft) + (cloth.xOf(topRight) - cloth.xOf(topLeft)) * s;
        float bx = cloth.xOf(bottomLeft) + (cloth.xOf(bottomRight) - cloth.xOf(bottomLeft)) * s;
        float ay = cloth.yOf(topLeft) + (cloth.yOf(topRight) - cloth.yOf(topLeft)) * s;
        float by = cloth.yOf(bottomLeft) + (cloth.yOf(bottomRight) - cloth.yOf(bottomLeft)) * s;
        float x = where.x() + (ax + (bx - ax) * r) * where.width();
        float y = where.y() + (ay + (by - ay) * r) * where.height();
        float u = (sheetX - left) / Math.max(1.0e-4f, right - left);
        float v = (sheetY - top) / Math.max(1.0e-4f, bottom - top);
        buffer.addVertex(matrix, x, y, 0f)
                .setUv(u, v)
                .setColor(color);
    }

    /**
     * One corner: where the solver has put it, printed with the piece of wrapper it began as.
     * <p>The texture coordinate comes from the point's place in the grid and never from where it has
     * moved to - that is what makes the printing travel with the foil.
     */
    private static void corner(BufferBuilder buffer, Matrix4f matrix, PackCloth cloth, Rect where,
            int across, int down) {
        int at = PackCloth.at(across, down);
        float x = where.x() + cloth.xOf(at) * where.width();
        float y = where.y() + cloth.yOf(at) * where.height();
        float u = across / (float) (PackCloth.ACROSS - 1);
        float v = down / (float) (PackCloth.DOWN - 1);
        // Into the printed part of the picture, which has blank columns either side of the bag.
        float acrossTexture = (PackWrapper.MARGIN + u * (PackWrapper.PIXELS - 2f * PackWrapper.MARGIN))
                / PackWrapper.PIXELS;
        buffer.addVertex(matrix, x, y, 0f).setUv(acrossTexture, v);
    }
}
