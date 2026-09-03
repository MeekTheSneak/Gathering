package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.CardInstance.Counters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Counters that add up, and counters that are counted")
class CountersTest {

    @Test
    @DisplayName("two +1/+1 counters are +2/+2")
    void severalOfThemAddUp() {
        assertThat(Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, 2)).isEqualTo("+2/+2");
        assertThat(Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, 1)).isEqualTo("+1/+1");
        assertThat(Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, 7)).isEqualTo("+7/+7");
    }

    @Test
    @DisplayName("and so do the ones that take it away")
    void shrinkingOnesToo() {
        assertThat(Counters.addedUp(Counters.MINUS_ONE_MINUS_ONE, 3)).isEqualTo("-3/-3");
    }

    @Test
    @DisplayName("a counter that is not a power and toughness has nothing to add up")
    void everythingElseIsCounted() {
        // Charge, stun and loyalty are read as "three of them", not as one bigger one, so
        // the caller writes the name with a count beside it and this says so by returning
        // nothing at all.
        assertThat(Counters.addedUp("charge", 3)).isNull();
        assertThat(Counters.addedUp(Counters.LOYALTY, 4)).isNull();
        assertThat(Counters.addUp("charge")).isFalse();
        assertThat(Counters.addUp(Counters.PLUS_ONE_PLUS_ONE)).isTrue();
    }

    @Test
    @DisplayName("a counter that is already bigger than one multiplies from where it is")
    void notOnlyTheOnesThatSayOne() {
        // Matched rather than listed on purpose: these exist on real cards, and a rule that
        // named "+1/+1" would quietly write "+2/+2 x3" for three of them.
        assertThat(Counters.addedUp("+2/+2", 3)).isEqualTo("+6/+6");
        assertThat(Counters.addedUp("+0/+1", 4)).isEqualTo("+0/+4");
    }

    @Test
    @DisplayName("nothing to say about none of them, or about nothing")
    void theEmptyAnswers() {
        assertThat(Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, 0)).isNull();
        assertThat(Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, -1)).isNull();
        assertThat(Counters.addedUp(null, 2)).isNull();
        assertThat(Counters.addUp(null)).isFalse();
        assertThat(Counters.addUp("")).isFalse();
    }

    @Test
    @DisplayName("a player who spent the game pressing plus does not get a negative creature")
    void theArithmeticDoesNotWrapAround() {
        // Reachable: the counters panel takes an amount, and nothing stops somebody holding
        // the button. A card reading a negative power because an int went round is worse
        // than one reading a silly number.
        String silly = Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, Integer.MAX_VALUE);
        assertThat(silly).isEqualTo("+2147483647/+2147483647");
        assertThat(Counters.addedUp("+1000000/+1000000", Integer.MAX_VALUE))
                .as("clamped rather than wrapped")
                .isEqualTo("+2147483647/+2147483647");
    }

    /**
     * Never below none.
     * <p>A counter is a physical thing sitting on a card. There is no such pile as minus two
     * +1/+1 counters, and the board could hold one: nothing clamped the arithmetic, so
     * pressing the minus on a counter a card did not have wrote a negative into the state and
     * the card read "Charge x-1" - or "+1/+1 x-2", which is a power and toughness with no
     * meaning at all, since a multiplier is what a pile of those adds up to.
     * <p>Not a rule this mod declines to enforce. Whether a creature with a -1/-1 on it dies
     * is a rule; whether a card can carry a negative number of objects is arithmetic, and the
     * commander damage written beside this had been clamped since the day it was written.
     */
    @Test
    @DisplayName("a counter never goes below none, however often the minus is pressed")
    void aCounterNeverGoesNegative() {
        CardInstance card = CardInstance.faceUp(
                CardInstanceId.of(1),
                dev.gathering.core.card.CardIdentity.ofPrinting(
                        java.util.UUID.nameUUIDFromBytes("bear".getBytes()), false),
                new SeatId(0));

        // Pressing minus on one that is not there does nothing at all.
        assertThat(card.withCounter("charge", -1).counters()).isEmpty();
        assertThat(card.withCounter(Counters.PLUS_ONE_PLUS_ONE, -3).counters()).isEmpty();
        assertThat(card.withCounter(Counters.MINUS_ONE_MINUS_ONE, -1).counters()).isEmpty();

        // And taking away more than there are takes them all off, rather than owing some.
        CardInstance two = card.withCounter(Counters.PLUS_ONE_PLUS_ONE, 2);
        assertThat(two.counter(Counters.PLUS_ONE_PLUS_ONE)).isEqualTo(2);
        assertThat(two.withCounter(Counters.PLUS_ONE_PLUS_ONE, -5).counters()).isEmpty();
        assertThat(two.withCounter(Counters.PLUS_ONE_PLUS_ONE, -1)
                .counter(Counters.PLUS_ONE_PLUS_ONE)).isOne();
    }

    @Test
    @DisplayName("nor does one a player keeps beside them")
    void aSeatsCounterNeverGoesNegativeEither() {
        SeatState seat = SeatState.startingAt(new SeatId(0), 20);

        assertThat(seat.withCounter("poison", -1).counters()).isEmpty();
        assertThat(seat.withCounter("poison", 3).withCounter("poison", -10).counters()).isEmpty();
        assertThat(seat.withCounter("poison", 3).withCounter("poison", -1).counter("poison"))
                .isEqualTo(2);
    }
}
