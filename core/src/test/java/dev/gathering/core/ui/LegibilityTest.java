package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class LegibilityTest {

    /**
     * The measurement this rule was written from.
     * <p>A seated player's mat buttons are 29 interface pixels wide inside their border and
     * the longest of the four words on them is 38. On an interface drawn at two screen pixels
     * per interface pixel that is sharp and was photographed being read; on one drawn at one
     * it was photographed as a smudge. Both pictures are the same fraction - 0.76 - so a rule
     * that looks only at the fraction cannot tell them apart, and this one has to.
     */
    @Test
    void theSameSquashedWordIsWorthWritingOnlyOnTheSharperInterface() {
        int longestVerb = 38;
        int roomOnTheButton = 29;

        assertThat(Legibility.roomToWrite(longestVerb, 2.0)).isLessThanOrEqualTo(roomOnTheButton);
        assertThat(Legibility.roomToWrite(longestVerb, 1.0)).isGreaterThan(roomOnTheButton);
    }

    @Test
    void aWordIsNeverWrittenAtLessThanHalfSizeHoweverSharpTheScreen() {
        assertThat(Legibility.smallestWorthWriting(4.0)).isEqualTo(0.5);
        assertThat(Legibility.smallestWorthWriting(100.0)).isEqualTo(0.5);
    }

    @Test
    void anInterfaceDrawnPixelForPixelWritesWordsWhole() {
        assertThat(Legibility.smallestWorthWriting(1.0)).isEqualTo(1.0);
        assertThat(Legibility.roomToWrite(38, 1.0)).isEqualTo(38);
    }

    @Property
    void aSharperInterfaceNeverNeedsMoreRoom(
            @ForAll @IntRange(min = 1, max = 400) int width,
            @ForAll @DoubleRange(min = 1.0, max = 8.0) double scale) {
        assertThat(Legibility.roomToWrite(width, scale))
                .isLessThanOrEqualTo(Legibility.roomToWrite(width, 1.0));
    }

    @Property
    void whateverTheScreenARoomThatFitsTheWholeWordIsEnough(
            @ForAll @IntRange(min = 1, max = 400) int width,
            @ForAll @DoubleRange(min = 0.0, max = 8.0) double scale) {
        assertThat(Legibility.roomToWrite(width, scale)).isLessThanOrEqualTo(width);
    }
}
