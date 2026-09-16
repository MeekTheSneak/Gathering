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
