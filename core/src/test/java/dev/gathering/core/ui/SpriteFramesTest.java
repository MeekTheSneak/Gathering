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

    /**
     * The bug itself, as a rule.
     * <p>The strip along the top of the table is drawn on the panel sprite, and it used to be
     * sixteen pixels tall against a panel border of eight - exactly its own two edges, with
     * nothing in between. This fails at that number and passes at the one it is now.
     */
    @Property(tries = 3000)
    @DisplayName("the strip along the top is never shorter than a panel border needs")
    void theTopStripWearsItsFrame(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        for (Rect status : new Rect[] {
                TableScreenLayout.of(width, height).status(),
                TableScreenLayout.of(width, height, false).status(),
                TableScreenLayout.watching(width, height).status()}) {
            assertThat(status.height())
                    .describedAs("the top strip at %sx%s has no middle to its panel border",
                            width, height)
                    .isGreaterThanOrEqualTo(SpriteFrames.smallestFor(8));
        }
    }

    /**
     * And the same for a look whose border is heavier than the default assumes.
     * <p>Four of the fourteen shipped looks draw a panel at sixteen pixels rather than eight,
     * which needs thirty-three and not seventeen. The client reads the real number off the
     * sprite and hands it over; the strip has to actually grow for it.
     */
    @Property(tries = 3000)
    @DisplayName("a heavier border gets the room it asks for")
    void aHeavierBorderIsMadeRoomFor(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 1, max = 16) int border) {
        int needs = SpriteFrames.smallestFor(border);
        for (Rect status : new Rect[] {
                TableScreenLayout.of(width, height, true, needs).status(),
                TableScreenLayout.of(width, height, false, needs).status(),
                TableScreenLayout.watching(width, height, needs).status()}) {
            assertThat(status.height())
                    .describedAs("a %s-pixel border at %sx%s", border, width, height)
                    .isGreaterThanOrEqualTo(needs);
            assertThat(status.bottom()).isLessThanOrEqualTo(height);
        }
    }

    @Property(tries = 2000)
    @DisplayName("asking for nothing extra changes nothing")
    void nothingExtraAskedForChangesNothing(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        assertThat(TableScreenLayout.of(width, height, true, 0))
                .isEqualTo(TableScreenLayout.of(width, height, true));
        assertThat(TableScreenLayout.watching(width, height, 0))
                .isEqualTo(TableScreenLayout.watching(width, height));
    }
}
