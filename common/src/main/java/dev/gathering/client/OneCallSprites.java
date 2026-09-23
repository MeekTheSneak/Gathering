package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.gathering.core.ui.SpriteTiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import org.joml.Matrix4f;

/**
 * Draws a GUI sprite exactly as {@code GuiGraphics#blitSprite} does, in one draw call rather than
 * one per tile.
 * <p>The game cuts a nine-sliced sprite into tiles and draws each as its own call - a buffer
 * begun, four vertices, uploaded, drawn. That is fine for a button and ruinous for a seat's ring
 * across a zoomed-in mat, which is thousands of tiles, and it was the whole of the flat board's
 * lag. The cutting is {@link SpriteTiles}, a statement-for-statement port of the game's; this
 * puts every piece into one buffer and draws it once.
 * <p><b>The same picture, not a similar one.</b> The shader is the game's own position-texture
 * shader, not the tinted one, because the two are not interchangeable: the tinted shader throws
 * away anything under a tenth opacity, and a free seat's ring is drawn at a quarter - the faint
 * outline would have been mostly erased. Tint still comes from the shader color a caller sets,
 * as it always did. The vertices, texture coordinates, depth and order are the game's, taken from
 * {@code GuiGraphics#innerBlit}.
 * <p>Render thread only. The one {@link Quads} is reused rather than allocated per draw, because
 * this runs for every themed rectangle on every screen, every frame.
 */
final class OneCallSprites {

    private static final Quads QUADS = new Quads();

    private OneCallSprites() {
    }

    /** Draws this sprite into this box the way the game would, in one call. */
    static void blit(GuiGraphics graphics, TextureAtlasSprite sprite, int x, int y, int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        GuiSpriteScaling scaling = Minecraft.getInstance().getGuiSprites().getSpriteScaling(sprite);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        QUADS.into(buffer, graphics.pose().last().pose(), sprite);
        try {
            if (scaling instanceof GuiSpriteScaling.NineSlice nine) {
                GuiSpriteScaling.NineSlice.Border border = nine.border();
                SpriteTiles.nineSlice(nine.width(), nine.height(),
                        border.left(), border.top(), border.right(), border.bottom(),
                        x, y, width, height, QUADS);
            } else if (scaling instanceof GuiSpriteScaling.Tile tile) {
                SpriteTiles.tiled(x, y, width, height, 0, 0,
                        tile.width(), tile.height(), tile.width(), tile.height(), QUADS);
            } else {
                // Stretched: the whole picture into the whole box, from the sprite's own corners
                // rather than from fractions of it, which is how the game draws this one.
                QUADS.quad(x, x + width, y, y + height,
                        sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1());
            }
        } catch (RuntimeException failed) {
            // The game throws here too, for a sprite whose border leaves no middle to tile. But the
            // game begins a buffer per tile and this began one for all of them, so it is finished
            // and thrown away first: left half-built, the next thing anywhere to draw from the
            // game's one shared buffer would have drawn these quads as well.
            MeshData abandoned = buffer.build();
            if (abandoned != null) {
                abandoned.close();
            }
            throw failed;
        } finally {
            QUADS.done();
        }
        // Null for a box that came to no pieces at all. The game would have drawn nothing too.
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * Turns pieces into vertices in the open buffer: the body of the game's per-tile blit, without
     * the draw call at the end of it.
     */
    private static final class Quads implements SpriteTiles.Piece {

        private BufferBuilder buffer;

        private Matrix4f pose;

        private TextureAtlasSprite sprite;

        void into(BufferBuilder buffer, Matrix4f pose, TextureAtlasSprite sprite) {
            this.buffer = buffer;
            this.pose = pose;
            this.sprite = sprite;
        }

        /** Let go of the frame's objects, so nothing outlives the draw that needed it. */
        void done() {
            this.buffer = null;
            this.pose = null;
            this.sprite = null;
        }

        @Override
        public void blit(int textureWidth, int textureHeight, int u, int v, int x, int y, int width, int height) {
            // As fractions of the painted size, cast before dividing - the game's own arithmetic,
            // so the texture coordinates come out to the same float.
            quad(x, x + width, y, y + height,
                    sprite.getU((float) u / (float) textureWidth),
                    sprite.getU((float) (u + width) / (float) textureWidth),
                    sprite.getV((float) v / (float) textureHeight),
                    sprite.getV((float) (v + height) / (float) textureHeight));
        }

        /** Four corners in the game's order, at depth zero, which is where it puts a GUI sprite. */
        void quad(int x1, int x2, int y1, int y2, float minU, float maxU, float minV, float maxV) {
            buffer.addVertex(pose, (float) x1, (float) y1, 0f).setUv(minU, minV);
            buffer.addVertex(pose, (float) x1, (float) y2, 0f).setUv(minU, maxV);
            buffer.addVertex(pose, (float) x2, (float) y2, 0f).setUv(maxU, maxV);
            buffer.addVertex(pose, (float) x2, (float) y1, 0f).setUv(maxU, minV);
        }
    }
}
