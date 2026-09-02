package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuilderFooterTest {

    /** The smallest window Minecraft will give you, in GUI-scaled units. */
    private static final int NARROWEST = 320;

    @Test
    @DisplayName("both groups share one row on a wide window")
    void oneRowWhenWide() {
        BuilderFooter footer = BuilderFooter.of(600, 400, true);

        assertThat(footer.rows()).isEqualTo(1);
        assertThat(footer.fromList().y()).isEqualTo(footer.cancel().y());
    }

    @Test
    @DisplayName("the left group takes a row of its own rather than sliding under Cancel")
    void twoRowsWhenNarrow() {
        // 346 is where the two groups stop fitting side by side; this was the width at which
        // Cancel was drawn on top of Sleeves.
        BuilderFooter footer = BuilderFooter.of(320, 240, true);

        assertThat(footer.rows()).isEqualTo(2);
        assertThat(footer.sleeves().overlaps(footer.cancel())).isFalse();
        assertThat(footer.sleeves().bottom()).isLessThanOrEqualTo(footer.cancel().y());
    }

    @Test
    @DisplayName("opened out of your own pockets there is no left group to place")
    void pocketsHaveTwoButtons() {
        BuilderFooter footer = BuilderFooter.of(NARROWEST, 240, false);

        assertThat(footer.rows()).isEqualTo(1);
        assertThat(footer.fromList()).isEqualTo(Rect.NONE);
        assertThat(footer.sleeves()).isEqualTo(Rect.NONE);
        assertThat(footer.cancel().overlaps(footer.finish())).isFalse();
    }

    @Property
    @Label("no two buttons ever overlap, at any window a player can have")
    void neverOverlaps(
            @ForAll @IntRange(min = NARROWEST, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll boolean withLeftGroup) {
        BuilderFooter footer = BuilderFooter.of(width, height, withLeftGroup);

        assertThat(footer.cancel().overlaps(footer.finish())).isFalse();
        assertThat(footer.fromList().overlaps(footer.sleeves())).isFalse();
        assertThat(footer.fromList().overlaps(footer.cancel())).isFalse();
        assertThat(footer.fromList().overlaps(footer.finish())).isFalse();
        assertThat(footer.sleeves().overlaps(footer.cancel())).isFalse();
        assertThat(footer.sleeves().overlaps(footer.finish())).isFalse();
    }

    @Property
    @Label("every button is on the screen, and the reserved height covers them all")
    void staysOnScreen(
            @ForAll @IntRange(min = NARROWEST, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        BuilderFooter footer = BuilderFooter.of(width, height, true);

        for (Rect button : new Rect[] {
                footer.fromList(), footer.sleeves(), footer.cancel(), footer.finish()}) {
            assertThat(button.x()).isGreaterThanOrEqualTo(0);
            assertThat(button.right()).isLessThanOrEqualTo(width);
            assertThat(button.bottom()).isLessThanOrEqualTo(height);
            assertThat(height - button.y()).isLessThanOrEqualTo(footer.height() + BuilderFooter.gap());
        }
    }
}
