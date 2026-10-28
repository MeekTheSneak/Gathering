package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisplayCaseRowTest {

    @Test
    @DisplayName("cards in a case never touch each other, with a gap a player can see")
    void neighborsDoNotOverlap() {
        // A sixtieth of a block is a quarter of a texel: small, but a line of case lining between
        // every card rather than two cards drawn through each other.
        assertThat(DisplayCaseRow.gap()).isGreaterThan(1f / 64f);
    }

    @Test
    @DisplayName("however many cards a case holds, none reaches through the glass")
    void everyCardIsInsideTheGlass() {
        float half = DisplayCaseRow.cardWidth() / 2f;
        for (int count = 1; count <= DisplayCaseRow.HOLDS; count++) {
            for (int at = 0; at < count; at++) {
                float middle = 0.5f + DisplayCaseRow.offsetOf(at, count);
                assertThat(middle - half).as("left edge of card %s of %s", at, count).isGreaterThan(DisplayCaseRow.GLASS);
                assertThat(middle + half).as("right edge of card %s of %s", at, count).isLessThan(1f - DisplayCaseRow.GLASS);
            }
        }
    }
}
