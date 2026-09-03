package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextureBudgetTest {

    /**
     * The cap {@code ClientCardImages.MAX_RESIDENT_TEXTURES
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a board's worth of crisp textures is not a budget, it is a fault")
    void theCrispTierIsNotABoardTier() {
        // What the reported near-crash actually cost: the tier chosen by drawn size alone, on
        // a board of sixty permanents zoomed in.
        assertThat(TextureBudget.mebibytesFor(TextureBudget.Tier.CRISP, 60))
                .isGreaterThan(TextureBudget.CEILING_MEBIBYTES);
        // And what it costs when only a card being read may ask for it.
        assertThat(TextureBudget.mebibytesFor(
                TextureBudget.Tier.CRISP, TextureBudget.CRISP_AT_ONCE))
                .isLessThan(TextureBudget.CEILING_MEBIBYTES / 10);
    }
} ships with.
     * <p>Repeated rather than imported: that constant is in the Minecraft layer and this module
     * has no Minecraft on its classpath. A game test checks the two are the same number, so
     * this copy cannot go stale on its own.
     */
    private static final int RESIDENT_CAP = 256;

    @Test
    @DisplayName("a card texture costs exactly its pixels, four bytes each")
    void whatOneCosts() {
        assertThat(TextureBudget.Tier.NORMAL.bytes()).isEqualTo(488L * 680 * 4);
        assertThat(TextureBudget.Tier.SMALL.bytes()).isEqualTo(146L * 204 * 4);
    }

    @Test
    @DisplayName("the resident cap stays inside the budget that is written down")
    void theCapStaysInsideTheBudget() {
        // The worst case is a full set of normal-tier textures: a session spent reading cards
        // at full size fills the cache with them and the small ones are evicted underneath.
        long worst = TextureBudget.mebibytesFor(TextureBudget.Tier.NORMAL, RESIDENT_CAP);

        assertThat(worst).isLessThanOrEqualTo(TextureBudget.CEILING_MEBIBYTES);
    }

    @Test
    @DisplayName("the worst case is the number the brief now carries")
    void theBriefsNumber() {
        // 324, not the "well under 200" the brief claimed for years - the cap and the budget
        // had never been multiplied together. If this changes, the brief changes with it.
        assertThat(TextureBudget.mebibytesFor(TextureBudget.Tier.NORMAL, RESIDENT_CAP))
                .isEqualTo(324);
        assertThat(TextureBudget.mebibytesFor(TextureBudget.Tier.SMALL, RESIDENT_CAP))
                .isEqualTo(29);
    }

    @Test
    @DisplayName("nothing costs anything")
    void nothingCostsNothing() {
        assertThat(TextureBudget.bytesFor(TextureBudget.Tier.NORMAL, 0)).isZero();
        assertThat(TextureBudget.bytesFor(TextureBudget.Tier.NORMAL, -3)).isZero();
        assertThat(TextureBudget.bytesFor(null, 10)).isZero();
    }
}
