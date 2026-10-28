package dev.gathering.core.ui;

/**
 * The pieces a GUI sprite is cut into when it is drawn bigger than it was painted, in the order
 * the game draws them.
 * <p><b>Why this exists.</b> Minecraft 1.21.1 draws a nine-sliced sprite by <em>tiling</em> its
 * edges and middle, and it draws every tile as its own draw call - a buffer begun, four vertices,
 * uploaded, drawn. A seat's ring is sixteen pixels with a four-pixel border, so its middle repeats
 * every eight pixels; a mat two hundred pixels wide is about four hundred draw calls for one ring,
 * and zooming in multiplies that by the square of the zoom. That was the whole of the board's lag:
 * measured, the ring cost three and a half times the mat under it, which is exactly how many more
 * tiles an eight-pixel middle has than a sixteen-pixel one. Cards were never the cost.
 * <p>The pictures are right and the art is the owner's, so nothing about the tiling changes. What
 * changes is how many times the game is asked to draw: every piece here goes into one buffer and
 * the buffer is drawn once. Same quads, same texture coordinates, same order - one call.
 * <p><b>A port, not a design.</b> Everything below is {@code GuiGraphics#blitNineSlicedSprite} and
 * {@code #blitTiledSprite} from the decompiled 1.21.1 sources, statement for statement, down to
 * the quirk that the right-hand edge column is as wide as the <em>left</em> border. Every sprite
 * this mod ships has one border for all four sides, so the quirk never shows - but a port that
 * tidied it would be a port that draws something the game does not.
 * <p>Pure: no Minecraft in it, so what it cuts can be checked against the arithmetic in
 * milliseconds rather than by squinting at a frame.
 */
public final class SpriteTiles {

    private SpriteTiles() {
    }

    /**
     * One piece: this rectangle of the painted sprite, put at this rectangle of the screen.
     * <p>The arguments are exactly those of the game's own private per-tile blit, so a caller
     * turns them into texture coordinates the same way it does - as fractions of the painted size.
     */
    @FunctionalInterface
    public interface Piece {
        void blit(int textureWidth, int textureHeight, int u, int v, int x, int y, int width, int height);
    }

    /**
     * A nine-sliced sprite: four corners as they were painted, four edges and a middle tiled.
     *
     * @param sliceWidth  how wide the sprite was painted
     * @param sliceHeight how tall
     * @param borderLeft  the border on each side, as its metadata gives them
     */
    public static void nineSlice(int sliceWidth, int sliceHeight,
            int borderLeft, int borderTop, int borderRight, int borderBottom,
            int x, int y, int width, int height, Piece piece) {
        int left = Math.min(borderLeft, width / 2);
        int right = Math.min(borderRight, width / 2);
        int top = Math.min(borderTop, height / 2);
        int bottom = Math.min(borderBottom, height / 2);
        if (width == sliceWidth && height == sliceHeight) {
            one(piece, sliceWidth, sliceHeight, 0, 0, x, y, width, height);
        } else if (height == sliceHeight) {
            one(piece, sliceWidth, sliceHeight, 0, 0, x, y, left, height);
            tiled(x + left, y, width - right - left, height, left, 0,
                    sliceWidth - right - left, sliceHeight, sliceWidth, sliceHeight, piece);
            one(piece, sliceWidth, sliceHeight, sliceWidth - right, 0, x + width - right, y, right, height);
        } else if (width == sliceWidth) {
            one(piece, sliceWidth, sliceHeight, 0, 0, x, y, width, top);
            tiled(x, y + top, width, height - bottom - top, 0, top,
                    sliceWidth, sliceHeight - bottom - top, sliceWidth, sliceHeight, piece);
            one(piece, sliceWidth, sliceHeight, 0, sliceHeight - bottom, x, y + height - bottom, width, bottom);
        } else {
            one(piece, sliceWidth, sliceHeight, 0, 0, x, y, left, top);
            tiled(x + left, y, width - right - left, top, left, 0,
                    sliceWidth - right - left, top, sliceWidth, sliceHeight, piece);
            one(piece, sliceWidth, sliceHeight, sliceWidth - right, 0, x + width - right, y, right, top);
            one(piece, sliceWidth, sliceHeight, 0, sliceHeight - bottom, x, y + height - bottom, left, bottom);
            tiled(x + left, y + height - bottom, width - right - left, bottom, left, sliceHeight - bottom,
                    sliceWidth - right - left, bottom, sliceWidth, sliceHeight, piece);
            one(piece, sliceWidth, sliceHeight, sliceWidth - right, sliceHeight - bottom,
                    x + width - right, y + height - bottom, right, bottom);
            tiled(x, y + top, left, height - bottom - top, 0, top,
                    left, sliceHeight - bottom - top, sliceWidth, sliceHeight, piece);
            tiled(x + left, y + top, width - right - left, height - bottom - top, left, top,
                    sliceWidth - right - left, sliceHeight - bottom - top, sliceWidth, sliceHeight, piece);
            // The game's own quirk, kept: this column is as wide as the LEFT border, and tiled at the
            // right border's width. Identical for a sprite with one border all round, which is every
            // sprite this mod has.
            tiled(x + width - right, y + top, left, height - bottom - top, sliceWidth - right, top,
                    right, sliceHeight - bottom - top, sliceWidth, sliceHeight, piece);
        }
    }

    /**
     * A region repeated across a rectangle, a column at a time, the last tile in each direction
     * cut short rather than squashed.
     * <p>Throws where the game throws: a tile with no size would repeat forever, and the game
     * refuses it rather than hanging.
     */
    public static void tiled(int x, int y, int width, int height, int u, int v,
            int tileWidth, int tileHeight, int textureWidth, int textureHeight, Piece piece) {
        if (width > 0 && height > 0) {
            if (tileWidth > 0 && tileHeight > 0) {
                for (int across = 0; across < width; across += tileWidth) {
                    int w = Math.min(tileWidth, width - across);
                    for (int down = 0; down < height; down += tileHeight) {
                        int h = Math.min(tileHeight, height - down);
                        one(piece, textureWidth, textureHeight, u, v, x + across, y + down, w, h);
                    }
                }
            } else {
                throw new IllegalArgumentException(
                        "Tiled sprite texture size must be positive, got " + tileWidth + "x" + tileHeight);
            }
        }
    }

    /** One piece, skipped when it has no area, the same test the game makes. */
    private static void one(Piece piece, int textureWidth, int textureHeight, int u, int v,
            int x, int y, int width, int height) {
        if (width != 0 && height != 0) {
            piece.blit(textureWidth, textureHeight, u, v, x, y, width, height);
        }
    }
}
