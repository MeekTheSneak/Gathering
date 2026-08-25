package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Telling one kind of pack from another at a glance. */
class PackWrapperTest {

    @Test
    @DisplayName("the three products that have a colour keep it")
    void theNamedProductsHaveTheirColours() {
        assertThat(PackWrapper.symbolColour("draft")).isEqualTo(PackWrapper.PLAIN);
        assertThat(PackWrapper.symbolColour("set")).isEqualTo(PackWrapper.GOLD);
        assertThat(PackWrapper.symbolColour("collector")).isEqualTo(PackWrapper.COLLECTOR);
    }

    @Test
    @DisplayName("a play booster is gold, because it replaced the set booster")
    void playBoostersAreGold() {
        assertThat(PackWrapper.symbolColour("play")).isEqualTo(PackWrapper.GOLD);
        assertThat(PackWrapper.symbolColour("play-arena")).isEqualTo(PackWrapper.GOLD);
    }

    @Test
    @DisplayName("anything else is plain rather than claiming to be a product it is not")
    void anythingElseIsPlain() {
        for (String kind : new String[] {
                "jumpstart", "prerelease", "arena", "box-topper", "value", "", null, "  ",
                "something nobody has printed"}) {
            assertThat(PackWrapper.symbolColour(kind)).as(String.valueOf(kind))
                    .isEqualTo(PackWrapper.PLAIN);
            assertThat(PackWrapper.hasItsOwnColour(kind)).isFalse();
        }
    }

    @Test
    @DisplayName("the kind is a name, however it was typed")
    void theKindIsANameNotAString() {
        assertThat(PackWrapper.symbolColour("  Collector ")).isEqualTo(PackWrapper.COLLECTOR);
        assertThat(PackWrapper.symbolColour("PLAY")).isEqualTo(PackWrapper.GOLD);
    }

    @Test
    @DisplayName("the three colours are told apart by more than their names, and are opaque")
    void theColoursAreDifferent() {
        assertThat(PackWrapper.PLAIN).isNotEqualTo(PackWrapper.GOLD);
        assertThat(PackWrapper.GOLD).isNotEqualTo(PackWrapper.COLLECTOR);
        assertThat(PackWrapper.PLAIN).isNotEqualTo(PackWrapper.COLLECTOR);
        for (int colour : new int[] {PackWrapper.PLAIN, PackWrapper.GOLD, PackWrapper.COLLECTOR}) {
            assertThat(colour >>> 24).isEqualTo(0xFF);
        }
    }
}
