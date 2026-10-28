package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectionGestureTest {

    @Test
    @DisplayName("empty hands and loose cards is the sweep")
    void emptyHandedWithCards() {
        assertThat(CollectionGesture.offered(false, true, true, true))
                .isEqualTo(CollectionGesture.SWEEP);
    }

    @Test
    @DisplayName("the sweep is not offered to somebody holding something")
    void handsFull() {
        // Vanilla skips block interaction when a crouching player is holding anything, so the
        // crouch never reaches the block. This was offered anyway, to exactly the player most
        // likely to be holding a card: the one carrying loose ones.
        assertThat(CollectionGesture.offered(false, false, true, true))
                .isEqualTo(CollectionGesture.NONE);
    }

    @Test
    @DisplayName("a deck in hand is the other gesture, which crouching does reach")
    void holdingADeck() {
        assertThat(CollectionGesture.offered(true, false, true, true))
                .isEqualTo(CollectionGesture.DISSOLVE);
        assertThat(CollectionGesture.offered(true, false, false, false))
                .isEqualTo(CollectionGesture.DISSOLVE);
    }

    @Test
    @DisplayName("nothing is offered to somebody who cannot add, or has nothing loose to add")
    void nothingToOffer() {
        assertThat(CollectionGesture.offered(false, true, false, true))
                .isEqualTo(CollectionGesture.NONE);
        assertThat(CollectionGesture.offered(false, true, true, false))
                .isEqualTo(CollectionGesture.NONE);
    }
}
