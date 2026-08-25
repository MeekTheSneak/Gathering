package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Counting what a collection holds. */
class CardTallyTest {

    private static final UUID BOLT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID FOREST = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static CardIdentity bolt() {
        return CardIdentity.ofPrinting(BOLT, false);
    }

    private static CardIdentity foilBolt() {
        return CardIdentity.ofPrinting(BOLT, true);
    }

    private static CardIdentity forest() {
        return CardIdentity.ofPrinting(FOREST, false);
    }

    @Test
    @DisplayName("forty Forests is one entry with a forty on it")
    void copiesAreACount() {
        CardTally tally = CardTally.EMPTY.plus(forest(), 40);

        assertThat(tally.of(forest())).isEqualTo(40);
        assertThat(tally.distinct()).isEqualTo(1);
        assertThat(tally.total()).isEqualTo(40);
    }

    @Test
    @DisplayName("a foil is a different card from the same card")
    void finishIsPartOfIdentity() {
        CardTally tally = CardTally.EMPTY.plus(bolt(), 3).plus(foilBolt(), 1);

        assertThat(tally.distinct()).isEqualTo(2);
        assertThat(tally.of(bolt())).isEqualTo(3);
        assertThat(tally.of(foilBolt())).isEqualTo(1);
    }

    @Test
    @DisplayName("taking more than is there takes what is there")
    void takingTooMuchTakesWhatIsThere() {
        // Somebody clicking "take four" of a card they have three of means "take my three".
        CardTally tally = CardTally.EMPTY.plus(bolt(), 3);

        CardTally.Taking taken = tally.take(bolt(), 4);

        assertThat(taken.took()).isEqualTo(3);
        assertThat(taken.left().has(bolt())).isFalse();
        assertThat(taken.left().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a card taken down to nothing is not an entry saying nothing")
    void emptiedEntriesGoAway() {
        CardTally tally = CardTally.EMPTY.plus(bolt(), 1).plus(forest(), 2);

        CardTally left = tally.take(bolt(), 1).left();

        assertThat(left.cards()).containsExactly(forest());
        assertThat(left.counts()).doesNotContainKey(bolt());
        assertThat(new CardTally(java.util.Map.of(bolt(), 0)).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("taking a card nobody has is nothing happening rather than a failure")
    void takingWhatIsNotThereIsNothing() {
        CardTally tally = CardTally.EMPTY.plus(bolt(), 1);

        CardTally.Taking taken = tally.take(forest(), 1);

        assertThat(taken.took()).isZero();
        assertThat(taken.left()).isEqualTo(tally);
    }

    @Test
    @DisplayName("what is in it stays in the order it went in")
    void orderIsInsertionOrder() {
        // It goes to disk and onto a screen. A per-launch hash order would reshuffle
        // somebody's binder every time the server restarted.
        CardTally tally = CardTally.EMPTY.plus(forest(), 1).plus(bolt(), 1).plus(foilBolt(), 1);

        assertThat(tally.cards()).containsExactly(forest(), bolt(), foilBolt());
        assertThat(tally.plus(forest(), 1).cards())
                .as("adding more of something already there does not move it")
                .containsExactly(forest(), bolt(), foilBolt());
    }

    @Test
    @DisplayName("a pile of cards counts itself")
    void aPileCountsItself() {
        CardTally tally = CardTally.counting(List.of(bolt(), forest(), bolt(), bolt()));

        assertThat(tally.of(bolt())).isEqualTo(3);
        assertThat(tally.of(forest())).isEqualTo(1);
        assertThat(CardTally.counting(null)).isEqualTo(CardTally.EMPTY);
        assertThat(CardTally.counting(List.of())).isEqualTo(CardTally.EMPTY);
    }

    @Test
    @DisplayName("owning the cards to sleeve a deck is a question with an answer")
    void holdingADeckIsAskable() {
        CardTally collection = CardTally.EMPTY.plus(bolt(), 3).plus(forest(), 20);
        CardTally deck = CardTally.EMPTY.plus(bolt(), 4).plus(forest(), 20);

        assertThat(collection.holdsAllOf(deck)).isFalse();
        assertThat(collection.shortOf(deck).of(bolt())).isEqualTo(1);
        assertThat(collection.shortOf(deck).of(forest())).isZero();

        CardTally enough = collection.plus(bolt(), 1);
        assertThat(enough.holdsAllOf(deck)).isTrue();
        assertThat(enough.shortOf(deck).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("sleeving a whole deck takes every card of it at once")
    void takingAWholeDeck() {
        CardTally collection = CardTally.EMPTY.plus(bolt(), 4).plus(forest(), 20);
        CardTally deck = CardTally.EMPTY.plus(bolt(), 4).plus(forest(), 16);

        CardTally.Taking taken = collection.takeAll(deck);

        assertThat(taken.took()).isEqualTo(20);
        assertThat(taken.left().of(bolt())).isZero();
        assertThat(taken.left().of(forest())).isEqualTo(4);
    }

    @Test
    @DisplayName("dissolving a deck puts the cards back where they came from")
    void pouringOneIntoAnother() {
        CardTally collection = CardTally.EMPTY.plus(forest(), 4);
        CardTally deck = CardTally.EMPTY.plus(forest(), 16).plus(bolt(), 4);

        CardTally back = collection.plus(deck);

        assertThat(back.of(forest())).isEqualTo(20);
        assertThat(back.of(bolt())).isEqualTo(4);
        assertThat(collection.plus((CardTally) null)).isEqualTo(collection);
    }

    @Test
    @DisplayName("nothing added is nothing done")
    void nonsenseChangesNothing() {
        CardTally tally = CardTally.EMPTY.plus(bolt(), 2);

        assertThat(tally.plus(bolt(), 0)).isEqualTo(tally);
        assertThat(tally.plus(bolt(), -5)).isEqualTo(tally);
        assertThat(tally.plus(null, 3)).isEqualTo(tally);
        assertThat(tally.take(bolt(), 0).left()).isEqualTo(tally);
        assertThat(tally.take(null, 1).took()).isZero();
    }

    @Test
    @DisplayName("two collections holding the same cards are the same collection")
    void equalityIsByContents() {
        CardTally one = CardTally.EMPTY.plus(bolt(), 2).plus(forest(), 1);
        CardTally other = CardTally.EMPTY.plus(forest(), 1).plus(bolt(), 2);

        assertThat(one).isEqualTo(other);
        assertThat(one.hashCode()).isEqualTo(other.hashCode());
    }

    @Test
    @DisplayName("a tally handed out cannot be edited behind its back")
    void theMapIsNotWritable() {
        CardTally tally = CardTally.EMPTY.plus(bolt(), 1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tally.counts().put(forest(), 9))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
