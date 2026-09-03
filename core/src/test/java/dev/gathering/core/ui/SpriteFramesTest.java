package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How small a framed box may be before its frame stops being a frame.
 * <p>A nine-slice keeps its border at a fixed size and tiles the middle. Give it a box no
 * bigger than two borders and there is no middle to tile: the corners run into each other and
 * what comes out is not the picture anybody painted. Reported from a real session twice - the
 * top bar in the table on the Future Sight look, and the outlines round the graveyard and
 * exile when the board is scrolled right out.
 * <p>What the border actually is belongs to the art and is read off the sprite as it is drawn.
 * What is checked here is the arithmetic, and the one layout in core that has to reserve room
 * for a frame it cannot see.
 */
class SpriteFramesTest {

    @Test
    @DisplayName("a framed box needs both its edges and a middle as big as one of them")
    void bothEdgesAndAMiddle() {
        assertThat(SpriteFrames.smallestFor(8)).isEqualTo(24);
        assertThat(SpriteFrames.smallestFor(4)).isEqualTo(12);
    }

    @Test
    @DisplayName("something that is not a frame has no smallest size")
    void nothingToBeTooSmallFor() {
        assertThat(SpriteFrames.smallestFor(0)).isZero();
        assertThat(SpriteFrames.smallestFor(-3)).isZero();
    }

    @Property(tries = 500)
    void theSmallestSizeAlwaysLeavesAMiddle(@ForAll @IntRange(min = 1, max = 64) int frame) {
        assertThat(SpriteFrames.smallestFor(frame) - frame * 2)
                .describedAs("a %s-pixel border needs a middle to sit either side of", frame)
                .isGreaterThanOrEqualTo(frame);
    }

    /**
     * The size a card gets to at the far end of the zoom, which is where this was reported.
     * <p>A card is twenty-four pixels tall there and about seventeen across. A slot drawn on
     * a sprite with an eight-pixel border has to be squashed rather than nine-sliced at that
     * size: sliced, it is sixteen pixels of border round one pixel of picture.
     */
    @Test
    @DisplayName("a slot at the far end of the zoom is too small for an eight-pixel border")
    void aSlotZoomedRightOutIsTooSmallToSlice() {
        assertThat(SpriteFrames.smallestFor(8)).isGreaterThan(17);
    }

    @Test
    @DisplayName("a border need not be square, and each axis is asked separately")
    void eachAxisIsAskedSeparately() {
        assertThat(SpriteFrames.smallestFor(4, 8)).isEqualTo(20);
        assertThat(SpriteFrames.smallestFor(8, 4)).isEqualTo(20);
        assertThat(SpriteFrames.smallestFor(0, 0)).isZero();
    }

    @Property(tries = 500)
    void twoEdgesTheSameIsTheSimpleRule(@ForAll @IntRange(min = 1, max = 64) int frame) {
        assertThat(SpriteFrames.smallestFor(frame))
                .isEqualTo(SpriteFrames.smallestFor(frame, frame));
    }
}
