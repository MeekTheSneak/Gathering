package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Telling one kind of pack from another at a glance. */
class PackWrapperTest {

    @Test
    @DisplayName("the three products that have a color keep it")
    void theNamedProductsHaveTheirColors() {
        assertThat(PackWrapper.symbolColor("draft")).isEqualTo(PackWrapper.PLAIN);
        assertThat(PackWrapper.symbolColor("set")).isEqualTo(PackWrapper.GOLD);
        assertThat(PackWrapper.symbolColor("collector")).isEqualTo(PackWrapper.COLLECTOR);
    }

    @Test
    @DisplayName("a play booster is gold, because it replaced the set booster")
    void playBoostersAreGold() {
        assertThat(PackWrapper.symbolColor("play")).isEqualTo(PackWrapper.GOLD);
        assertThat(PackWrapper.symbolColor("play-arena")).isEqualTo(PackWrapper.GOLD);
    }

    @Test
    @DisplayName("anything else is plain rather than claiming to be a product it is not")
    void anythingElseIsPlain() {
        for (String kind : new String[] {
                "jumpstart", "prerelease", "arena", "box-topper", "value", "", null, "  ",
                "something nobody has printed"}) {
            assertThat(PackWrapper.symbolColor(kind)).as(String.valueOf(kind))
                    .isEqualTo(PackWrapper.PLAIN);
        }
    }

    @Test
    @DisplayName("the kind is a name, however it was typed")
    void theKindIsANameNotAString() {
        assertThat(PackWrapper.symbolColor("  Collector ")).isEqualTo(PackWrapper.COLLECTOR);
        assertThat(PackWrapper.symbolColor("PLAY")).isEqualTo(PackWrapper.GOLD);
    }

    @Test
    @DisplayName("the three colors are told apart by more than their names, and are opaque")
    void theColorsAreDifferent() {
        assertThat(PackWrapper.PLAIN).isNotEqualTo(PackWrapper.GOLD);
        assertThat(PackWrapper.GOLD).isNotEqualTo(PackWrapper.COLLECTOR);
        assertThat(PackWrapper.PLAIN).isNotEqualTo(PackWrapper.COLLECTOR);
        for (int color : new int[] {PackWrapper.PLAIN, PackWrapper.GOLD, PackWrapper.COLLECTOR}) {
            assertThat(color >>> 24).isEqualTo(0xFF);
        }
    }

    /**
     * The whole picture is drawn, and it is drawn at its own shape. The screen that tears a pack open used
     * to cut rows one to fourteen out of a sixteen-row wrapper - losing the white top of the crimp and the
     * fold at the bottom, and drawing the first row of the body as crimp - and then lay them along a pack
     * two thirds as wide as it was tall. The owner saw it stretch the moment a pack opened (2026-09-16).
     */
    @Test
    @DisplayName("the two pieces of the wrapper are the whole wrapper, laid out at its own shape")
    void theWrapperIsDrawnWhole() {
        assertThat(PackWrapper.CRIMP_ROW).isZero();
        assertThat(PackWrapper.BODY_ROW).isEqualTo(PackWrapper.CRIMP_ROW + PackWrapper.CRIMP_ROWS);
        assertThat(PackWrapper.BODY_ROW + PackWrapper.BODY_ROWS).isEqualTo(PackWrapper.PIXELS);
        // A pack as wide against its height as the bag is against the picture, so nothing is squashed.
        assertThat(PackWrapper.shape())
                .isEqualTo((PackWrapper.PIXELS - 2.0 * PackWrapper.MARGIN) / PackWrapper.PIXELS);
        // And each piece laid along the share of the pack it takes up in the picture.
        assertThat(PackWrapper.crimp()).isEqualTo(PackWrapper.CRIMP_ROWS / (double) PackWrapper.PIXELS);
        assertThat(1.0 - PackWrapper.crimp())
                .isEqualTo(PackWrapper.BODY_ROWS / (double) PackWrapper.PIXELS);
    }
}
