package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DropMeaningTest {

    @Test
    @DisplayName("a click on a card on the felt does nothing at all")
    void aClickDoesNothing() {
        // The rule this type exists for. It used to tap, so every mis-click turned something
        // sideways and a card could not be picked up and reconsidered.
        assertThat(DropMeaning.of(false, false, false, false)).isEqualTo(DropMeaning.NOTHING);
    }

    @Test
    @DisplayName("a stack picked up and put straight back down does nothing either")
    void aStackPutBack() {
        // And not a click on the card underneath, which would tap the top of the stack
        // somebody had just decided not to move.
        assertThat(DropMeaning.of(false, false, true, false)).isEqualTo(DropMeaning.NOTHING);
    }

    @Test
    @DisplayName("a card let go over the table is put down, however it was picked up")
    void aRealDragPlaces() {
        assertThat(DropMeaning.of(false, false, false, true)).isEqualTo(DropMeaning.PLACE);
        assertThat(DropMeaning.of(true, false, false, true)).isEqualTo(DropMeaning.PLACE);
        assertThat(DropMeaning.of(false, true, false, true)).isEqualTo(DropMeaning.PLACE);
        assertThat(DropMeaning.of(false, false, true, true)).isEqualTo(DropMeaning.PLACE);
    }

    @Test
    @DisplayName("a card out of the hand lands even without travelling")
    void playingFromTheHand() {
        // Letting go of it over the table is how a card gets out of a hand, so this one does
        // not get the click-means-nothing treatment.
        assertThat(DropMeaning.of(true, false, false, false)).isEqualTo(DropMeaning.PLACE);
    }

    @Test
    @DisplayName("a press on a pile belongs to the pile, not to this rule")
    void pressingAPile() {
        assertThat(DropMeaning.of(false, true, false, false)).isEqualTo(DropMeaning.PLACE);
        assertThat(DropMeaning.of(false, true, true, false)).isEqualTo(DropMeaning.PLACE);
    }

    @Property
    @Label("anything that actually moved is put down, whatever it was picked up from")
    void movingAlwaysPlaces(
            @ForAll boolean fromHand, @ForAll boolean fromPile, @ForAll boolean whole) {
        assertThat(DropMeaning.of(fromHand, fromPile, whole, true)).isEqualTo(DropMeaning.PLACE);
    }

    @Property
    @Label("a press on the felt that never moved never means anything but nothing")
    void aStillPressOnTheFeltIsNeverAnAction(@ForAll boolean whole) {
        // The one that must not drift: neither from a hand nor from a pile, and it did not
        // move. Whether the stack was held makes no difference to the answer.
        assertThat(DropMeaning.of(false, false, whole, false)).isEqualTo(DropMeaning.NOTHING);
    }
}
