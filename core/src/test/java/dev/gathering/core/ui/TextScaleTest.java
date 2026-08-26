package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("What counts as a text scale somebody meant to ask for")
class TextScaleTest {

    @Test
    @DisplayName("shrinking is fine, growing is a mistake")
    void theRangeIsUpToFullSize() {
        assertThat(TextScale.isSane(1f)).isTrue();
        assertThat(TextScale.isSane(0.6f)).isTrue();
        assertThat(TextScale.isSane(0.05f)).isTrue();

        // An int width widened into the float scale parameter. Always bigger than one, which
        // is why "bigger than one" is the rule: no caller in the mod grows text.
        assertThat(TextScale.isSane(18f)).isFalse();
        assertThat(TextScale.isSane(1.0001f)).isFalse();
    }

    @Test
    @DisplayName("a scale that is not a size at all is a mistake too")
    void degenerateScalesAreNotSane() {
        assertThat(TextScale.isSane(0f)).isFalse();
        assertThat(TextScale.isSane(-1f)).isFalse();
        assertThat(TextScale.isSane(Float.NaN)).isFalse();
        assertThat(TextScale.isSane(Float.POSITIVE_INFINITY)).isFalse();
    }

    @Test
    @DisplayName("after a mistake it still draws something readable")
    void saneAlwaysGivesSomethingToDrawAt() {
        assertThat(TextScale.sane(18f)).isEqualTo(TextScale.FULL);
        assertThat(TextScale.sane(0f)).isEqualTo(TextScale.SMALLEST);
        assertThat(TextScale.sane(-3f)).isEqualTo(TextScale.SMALLEST);
        assertThat(TextScale.sane(Float.NaN)).isEqualTo(TextScale.SMALLEST);
        assertThat(TextScale.sane(0.8f)).isEqualTo(0.8f);
        // Below the floor it comes back at the floor rather than at nothing, because a label
        // drawn too small to read is still better than a label that is not there.
        assertThat(TextScale.sane(0.05f)).isEqualTo(TextScale.SMALLEST);
    }

    @Test
    @DisplayName("everything sane comes back unchanged, and everything else comes back sane")
    void saneIsAlwaysSane() {
        for (float scale : new float[] {
                18f, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 0.6f, 1f, 0.9f, 0.3f}) {
            assertThat(TextScale.isSane(TextScale.sane(scale)))
                    .describedAs("sane(%s)", scale)
                    .isTrue();
        }
    }
}
