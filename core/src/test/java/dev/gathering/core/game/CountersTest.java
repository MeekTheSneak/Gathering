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
        // A pile below none used to be nothing to say as well, and it was the wrong answer:
        // see aNegativePileStillAddsUp.
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
     * A pile that has gone below none adds up too.
     * <p>Nothing in this mod argues with a player - see the misplay tests, which state it -
     * so pressing the minus on a card that has no +1/+1 counters really does leave it
     * carrying minus one of them. What that used to say on the card was "+1/+1 x-1", because
     * the arithmetic here produced "+-1" and the method gave up rather than print it.
     * <p>Minus two +1/+1 counters is -2/-2, which is a real thing for a creature to be. And
     * minus two -1/-1 counters is +2/+2, for the same reason and in the other direction.
     */
    @Test
    @DisplayName("a pile below none adds up, rather than falling back to a count")
    void aNegativePileStillAddsUp() {
        assertThat(Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, -1)).isEqualTo("-1/-1");
        assertThat(Counters.addedUp(Counters.PLUS_ONE_PLUS_ONE, -2)).isEqualTo("-2/-2");
        assertThat(Counters.addedUp(Counters.MINUS_ONE_MINUS_ONE, -2)).isEqualTo("+2/+2");
        assertThat(Counters.addedUp("+2/+2", -3)).isEqualTo("-6/-6");
        // A mixed printing keeps each side's own direction when it turns round.
        assertThat(Counters.addedUp("+1/-1", -2)).isEqualTo("-2/+2");
    }

    /**
     * And the other end of the same clamp.
     * <p>{@link #theArithmeticDoesNotWrapAround} states the top. There was no bottom, because
     * nothing below zero was ever printed; there is one now.
     */
    @Test
    @DisplayName("a silly number below none is clamped rather than wrapped, too")
    void theArithmeticDoesNotWrapTheOtherWayEither() {
        assertThat(Counters.addedUp("+1000000/+1000000", -100_000))
                .isEqualTo(Integer.MIN_VALUE + "/" + Integer.MIN_VALUE);
    }
}
