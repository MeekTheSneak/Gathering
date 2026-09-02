package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilterRowLayoutTest {

    private static final int NARROWEST = 320;

    /** Five colors and colorless. It was five, and adding the sixth is what broke the row. */
    private static final int PIPS = 6;

    @Test
    @DisplayName("the row fits the narrowest window the game draws")
    void fitsTheNarrowestWindow() {
        FilterRowLayout row = FilterRowLayout.of(NARROWEST, 58, PIPS);

        assertThat(row.build().right()).isLessThanOrEqualTo(NARROWEST - 16);
        assertThat(row.rarity().overlaps(row.build())).isFalse();
        assertThat(row.pip(PIPS - 1).right()).isLessThanOrEqualTo(row.rarity().x());
    }

    @Test
    @DisplayName("the rarity button is what gives way, not the one that leaves the screen")
    void rarityGivesWay() {
        FilterRowLayout wide = FilterRowLayout.of(1280, 58, PIPS);
        FilterRowLayout narrow = FilterRowLayout.of(NARROWEST, 58, PIPS);

        assertThat(narrow.rarity().width()).isLessThan(wide.rarity().width());
        assertThat(narrow.build().width()).isEqualTo(wide.build().width());
    }

    @Property
    @Label("nothing on the row overlaps or leaves the screen, at any width or pip count")
    void neverOverlapsOrLeaves(
            @ForAll @IntRange(min = NARROWEST, max = 3840) int width,
            @ForAll @IntRange(min = 0, max = 6) int pips) {
        FilterRowLayout row = FilterRowLayout.of(width, 58, pips);

        assertThat(row.rarity().overlaps(row.build())).isFalse();
        assertThat(row.rarity().x()).isGreaterThanOrEqualTo(0);
        assertThat(row.build().right()).isLessThanOrEqualTo(width);
        for (int index = 0; index < pips; index++) {
            assertThat(row.pip(index).overlaps(row.rarity())).isFalse();
            assertThat(row.pip(index).overlaps(row.build())).isFalse();
        }
    }
}
