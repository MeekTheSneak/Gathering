package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Writing on a card grows and shrinks with the card.
 * <p>The fault was writing pinned to the screen: right at one zoom and wrong at every other.
 * The property that says it is fixed is that a bigger card never gets smaller writing.
 */
class CardTextTest {

    private static final int LINE = 9;

    @Property
    @Label("a bigger card never gets smaller writing")
    void biggerNeverMeansSmaller(
            @ForAll @IntRange(min = 1, max = 900) int shorter,
            @ForAll @IntRange(min = 0, max = 900) int taller) {
        int high = shorter + taller;
        assertThat(CardText.scaleFor(high, CardText.NOTE, LINE))
                .isGreaterThanOrEqualTo(CardText.scaleFor(shorter, CardText.NOTE, LINE));
    }

    @Property
    @Label("writing is never smaller than a smudge nor larger than the card's own text")
    void itStaysBetweenItsBounds(@ForAll @IntRange(min = 1, max = 2000) int cardHeight) {
        assertThat(CardText.scaleFor(cardHeight, CardText.NOTE, LINE))
                .as("scale on a card %d tall", cardHeight)
                .isBetween(CardText.SMALLEST, CardText.LARGEST);
    }

    @Test
    @DisplayName("a card too small for a sentence gets no note, and keeps its counters")
    void aNoteGoesAndACounterStays() {
        // A note trimmed to a letter and an ellipsis is a smudge, so it is not written. A
        // counter is the difference between a 2/2 and a 4/4, and a board zoomed out that
        // stopped saying which creatures carry them hides what the player zoomed out to see.
        assertThat(CardText.worthDrawing(8, CardText.NOTE, LINE)).isFalse();
        assertThat(CardText.worthDrawing(0, CardText.NOTE, LINE)).isFalse();
        assertThat(CardText.scaleFor(8, CardText.COUNTER, LINE)).isEqualTo(CardText.SMALLEST);
    }

    @Property
    @Label("writing is never drawn below the floor, however small the card")
    void nothingIsEverDrawnAsASmudge(@ForAll @IntRange(min = 0, max = 2000) int cardHeight) {
        assertThat(CardText.scaleFor(cardHeight, CardText.COUNTER, LINE))
                .isGreaterThanOrEqualTo(CardText.SMALLEST);
    }

    @Test
    @DisplayName("a card at the size the old fixed number was chosen at still reads the same")
    void theOrdinaryCardIsUnchanged() {
        // A note was drawn at the font's own size, and it was chosen looking at a card about
        // this tall. The fraction has to agree with that or every board changes at once.
        float scale = CardText.scaleFor(64, CardText.NOTE, LINE);
        assertThat(scale).isBetween(0.9f, 1.1f);
    }

    @Test
    @DisplayName("counters are a shorter line than a note, because a card may carry several")
    void countersAreShorter() {
        // On an ordinary card, where neither has run into the cap that stops a card filling
        // the window being written across like a poster.
        assertThat(CardText.scaleFor(64, CardText.COUNTER, LINE))
                .isLessThan(CardText.scaleFor(64, CardText.NOTE, LINE));
    }

    @Test
    @DisplayName("how tall a line comes out is at least a pixel")
    void aLineIsAlwaysSomething() {
        assertThat(CardText.lineAt(CardText.SMALLEST, LINE)).isGreaterThanOrEqualTo(1);
        assertThat(CardText.lineAt(0f, LINE)).isEqualTo(1);
    }
}
