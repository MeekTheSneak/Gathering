package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("What counts as a text scale somebody meant to ask for")
class TextScaleTest {

    @Test
    @DisplayName("shrinking is fine, a little growing is fine, a width is a mistake")
    void theRangeIsUpToTheCeiling() {
        assertThat(TextScale.isSane(1f)).isTrue();
        assertThat(TextScale.isSane(0.6f)).isTrue();
        assertThat(TextScale.isSane(0.05f)).isTrue();
        // Writing on a card is drawn against the card, so zooming in grows it. That used to
        // be the mistake; the ceiling moved up to let it happen.
        assertThat(TextScale.isSane(CardText.LARGEST)).isTrue();
        assertThat(TextScale.isSane(TextScale.LARGEST)).isTrue();

        // An int width widened into the float scale parameter. Always a good deal bigger than
        // the ceiling, because a width is a number of pixels.
        assertThat(TextScale.isSane(18f)).isFalse();
        assertThat(TextScale.isSane(TextScale.LARGEST + 0.0001f)).isFalse();
    }

    @Test
    @DisplayName("the narrowest thing text is fitted into is still caught as a width")
    void everyRealWidthIsStillAMistake() {
        // The point of the ceiling: it has to sit under every width the mod could hand here
        // by accident. The narrowest is a counter's own room on a card a few pixels wide.
        for (int width = 4; width <= 400; width++) {
            assertThat(TextScale.isSane(width))
                    .describedAs("a width of %d handed to the scale", width)
                    .isFalse();
        }
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
        assertThat(TextScale.sane(18f)).isEqualTo(TextScale.LARGEST);
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
                18f, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 0.6f, 1f, 1.5f, 0.9f, 0.3f}) {
            assertThat(TextScale.isSane(TextScale.sane(scale)))
                    .describedAs("sane(%s)", scale)
                    .isTrue();
        }
    }
}
