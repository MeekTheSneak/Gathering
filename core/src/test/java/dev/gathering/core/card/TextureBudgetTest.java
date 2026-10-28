package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextureBudgetTest {

    private static final long CEILING_BYTES = TextureBudget.CEILING_MEBIBYTES * 1024 * 1024;

    @Test
    @DisplayName("a board's worth of crisp textures is not a budget, it is a fault")
    void theCrispTierIsNotABoardTier() {
        // What the reported near-crash actually cost: the tier chosen by drawn size alone, on
        // a board of sixty permanents zoomed in - more than half the whole ceiling on one board.
        // This test sat inside the comment above it and never ran; run, it said sixty of them were
        // over the ceiling, which they are not: 177 MiB of 325.
        assertThat(TextureBudget.mebibytesFor(TextureBudget.Tier.CRISP, 60))
                .isGreaterThan(TextureBudget.CEILING_MEBIBYTES / 2);
        // And what it costs when only a card being read may ask for it.
        assertThat(TextureBudget.mebibytesFor(
                TextureBudget.Tier.CRISP, TextureBudget.CRISP_AT_ONCE))
                .isLessThan(TextureBudget.CEILING_MEBIBYTES / 10);
    }

    @Test
    @DisplayName("a card texture costs exactly its pixels, four bytes each")
    void whatOneCosts() {
        assertThat(TextureBudget.Tier.NORMAL.bytes()).isEqualTo(488L * 680 * 4);
        assertThat(TextureBudget.Tier.SMALL.bytes()).isEqualTo(146L * 204 * 4);
    }

    @Test
    @DisplayName("the count of resident textures never binds before the bytes do")
    void theCountIsTheCeilingInSmallFaces() {
        // As many small faces as the ceiling holds, and not one more: the count is there to bound how many
        // textures the graphics card keeps, and the bytes are the budget.
        assertThat(TextureBudget.bytesFor(TextureBudget.Tier.SMALL, TextureBudget.MOST_TEXTURES))
                .isLessThanOrEqualTo(CEILING_BYTES);
        assertThat(TextureBudget.bytesFor(TextureBudget.Tier.SMALL, TextureBudget.MOST_TEXTURES + 1))
                .isGreaterThan(CEILING_BYTES);
    }

    @Test
    @DisplayName("the worst case is the number the brief now carries")
    void theBriefsNumber() {
        // 324, not the "well under 200" the brief claimed for years - the cap and the budget had never been
        // multiplied together. The ceiling holds 256 normal-tier faces or 2,860 small ones, and either way the
        // graphics card keeps 324 MiB at most. If this changes, the brief changes with it.
        assertThat(TextureBudget.mebibytesFor(TextureBudget.Tier.NORMAL, 256)).isEqualTo(324);
        assertThat(TextureBudget.bytesFor(TextureBudget.Tier.NORMAL, 257)).isGreaterThan(CEILING_BYTES);
        assertThat(TextureBudget.MOST_TEXTURES).isEqualTo(2860);
        assertThat(TextureBudget.mebibytesFor(TextureBudget.Tier.SMALL, TextureBudget.MOST_TEXTURES)).isEqualTo(324);
    }

    @Test
    @DisplayName("nothing costs anything")
    void nothingCostsNothing() {
        assertThat(TextureBudget.bytesFor(TextureBudget.Tier.NORMAL, 0)).isZero();
        assertThat(TextureBudget.bytesFor(TextureBudget.Tier.NORMAL, -3)).isZero();
        assertThat(TextureBudget.bytesFor(null, 10)).isZero();
    }
}
