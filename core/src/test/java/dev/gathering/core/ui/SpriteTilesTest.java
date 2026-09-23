package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a GUI sprite is cut up, which is how many times the game is asked to draw it.
 * <p>The board's lag was this: 1.21.1 draws every tile of a nine-sliced sprite as its own draw
 * call, and a seat's ring tiles every eight pixels. These pin down the cutting - a port of the
 * game's own, which has to produce the same pieces for the batched draw to be the same picture -
 * and say in numbers what one ring used to cost.
 * <p>Properties at the top level on purpose: a jqwik property inside a JUnit {@code @Nested}
 * class is silently not run unless the class is also a {@code @Group}. See {@code DIALECT.md}.
 */
class SpriteTilesTest {

    /** A piece as the game's per-tile blit receives it. */
    private record Cut(int textureWidth, int textureHeight, int u, int v, int x, int y, int width, int height) {
    }

    private static List<Cut> nineSlice(int size, int border, int width, int height) {
        List<Cut> cuts = new ArrayList<>();
        SpriteTiles.nineSlice(size, size, border, border, border, border, 0, 0, width, height,
                (tw, th, u, v, x, y, w, h) -> cuts.add(new Cut(tw, th, u, v, x, y, w, h)));
        return cuts;
    }

    @Test
    @DisplayName("a ring on a mat was four hundred draw calls")
    void whatOneRingCost() {
        // A seat's ring is sixteen pixels with a four-pixel border, so its middle repeats every
        // eight. On a mat two hundred by a hundred and twenty - an ordinary two-seat board - that
        // is 336 middle tiles, 76 edge tiles and 4 corners, each of which the game drew as a draw
        // call of its own. Zoomed in to twice the size it is four times as many. This is the
        // number the batching exists to collapse to one.
        assertThat(nineSlice(16, 4, 200, 120)).hasSize(416);
        assertThat(nineSlice(16, 4, 400, 240)).hasSize(1_581);
    }

    @Test
    @DisplayName("drawn at its own size, a sprite is one piece: the whole picture")
    void atItsOwnSizeItIsOnePiece() {
        assertThat(nineSlice(16, 4, 16, 16))
                .containsExactly(new Cut(16, 16, 0, 0, 0, 0, 16, 16));
    }

    @Test
    @DisplayName("only wider than painted: a left, a tiled middle and a right, full height")
    void onlyWider() {
        assertThat(nineSlice(16, 4, 20, 16)).containsExactly(
                new Cut(16, 16, 0, 0, 0, 0, 4, 16),
                new Cut(16, 16, 4, 0, 4, 0, 8, 16),
                new Cut(16, 16, 4, 0, 12, 0, 4, 16),
                new Cut(16, 16, 12, 0, 16, 0, 4, 16));
    }

    /**
     * Wider and taller, worked through by hand from the game's source and not from this port.
     * <p>The order is the game's: top-left, top edge, top-right, bottom-left, bottom edge,
     * bottom-right, left edge, middle, right edge - and within each tiled strip, a column at a time.
     */
    @Test
    @DisplayName("wider and taller: nine regions, the edges and the middle tiled, in the game's order")
    void widerAndTaller() {
        assertThat(nineSlice(16, 4, 24, 20)).containsExactly(
                new Cut(16, 16, 0, 0, 0, 0, 4, 4),
                new Cut(16, 16, 4, 0, 4, 0, 8, 4),
                new Cut(16, 16, 4, 0, 12, 0, 8, 4),
                new Cut(16, 16, 12, 0, 20, 0, 4, 4),
                new Cut(16, 16, 0, 12, 0, 16, 4, 4),
                new Cut(16, 16, 4, 12, 4, 16, 8, 4),
                new Cut(16, 16, 4, 12, 12, 16, 8, 4),
                new Cut(16, 16, 12, 12, 20, 16, 4, 4),
                new Cut(16, 16, 0, 4, 0, 4, 4, 8),
                new Cut(16, 16, 0, 4, 0, 12, 4, 4),
                new Cut(16, 16, 4, 4, 4, 4, 8, 8),
                new Cut(16, 16, 4, 4, 4, 12, 8, 4),
                new Cut(16, 16, 4, 4, 12, 4, 8, 8),
                new Cut(16, 16, 4, 4, 12, 12, 8, 4),
                new Cut(16, 16, 12, 4, 20, 4, 4, 8),
                new Cut(16, 16, 12, 4, 20, 12, 4, 4));
    }

    @Test
    @DisplayName("a tile with no size is refused, as the game refuses it, rather than repeated forever")
    void aTileWithNoSizeIsRefused() {
        assertThatThrownBy(() -> SpriteTiles.tiled(0, 0, 10, 10, 0, 0, 0, 4, 16, 16,
                (tw, th, u, v, x, y, w, h) -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Every pixel of the box drawn exactly once: no gap for the felt to show through, and no
     * overlap for a translucent ring to double up on.
     */
    @Property(tries = 300)
    @Label("the pieces cover the box exactly once, whatever its size")
    void thePiecesPartitionTheBox(
            @ForAll @IntRange(min = 4, max = 64) int size,
            @ForAll @IntRange(min = 0, max = 31) int border,
            @ForAll @IntRange(min = 1, max = 220) int width,
            @ForAll @IntRange(min = 1, max = 220) int height) {
        // A border that leaves the painted middle empty cannot be tiled - the game throws - and no
        // sprite this mod ships is shaped like that.
        int kept = Math.min(border, size / 2 - 1);
        int[][] hits = new int[width][height];
        for (Cut cut : nineSlice(size, kept, width, height)) {
            for (int across = cut.x(); across < cut.x() + cut.width(); across++) {
                for (int down = cut.y(); down < cut.y() + cut.height(); down++) {
                    hits[across][down]++;
                }
            }
        }
        for (int across = 0; across < width; across++) {
            for (int down = 0; down < height; down++) {
                assertThat(hits[across][down])
                        .as("pixel %d,%d of a %dx%d box drawn from a %d/%d sprite",
                                across, down, width, height, size, kept)
                        .isEqualTo(1);
            }
        }
    }

    /** No piece reads outside the picture that was painted, or it would read its neighbor's. */
    @Property(tries = 300)
    @Label("every piece reads from inside the painted sprite")
    void everyPieceReadsInsideTheSprite(
            @ForAll @IntRange(min = 4, max = 64) int size,
            @ForAll @IntRange(min = 0, max = 31) int border,
            @ForAll @IntRange(min = 1, max = 400) int width,
            @ForAll @IntRange(min = 1, max = 400) int height) {
        int kept = Math.min(border, size / 2 - 1);
        for (Cut cut : nineSlice(size, kept, width, height)) {
            assertThat(cut.u()).isGreaterThanOrEqualTo(0);
            assertThat(cut.v()).isGreaterThanOrEqualTo(0);
            assertThat(cut.u() + cut.width()).isLessThanOrEqualTo(size);
            assertThat(cut.v() + cut.height()).isLessThanOrEqualTo(size);
        }
    }
}
