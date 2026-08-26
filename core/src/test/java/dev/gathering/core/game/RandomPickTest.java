package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.IntUnaryOperator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Taking cards out of a hand at random")
class RandomPickTest {

    private static final List<String> HAND = List.of("a", "b", "c", "d", "e");

    @Test
    @DisplayName("it takes as many as it was asked for")
    void itTakesTheNumberAsked() {
        assertThat(RandomPick.some(HAND, 2, bound -> 0)).hasSize(2);
        assertThat(RandomPick.some(HAND, 5, bound -> 0)).hasSize(5);
    }

    @Test
    @DisplayName("a hand with less in it than that gives up what it has")
    void itNeverTakesMoreThanThereIs() {
        assertThat(RandomPick.some(List.of("a", "b"), 4, bound -> 0)).hasSize(2);
        assertThat(RandomPick.some(List.of(), 4, bound -> 0)).isEmpty();
    }

    @Test
    @DisplayName("nothing is taken twice")
    void everythingTakenIsDistinct() {
        // The failure this rules out is quiet: one card sent to the graveyard while the log
        // says two went, which nobody notices until the hand count does not add up.
        List<String> taken = RandomPick.some(HAND, 5, bound -> bound - 1);

        assertThat(taken).containsExactlyInAnyOrderElementsOf(HAND);
    }

    @Test
    @DisplayName("asking for none, or for a negative, takes none")
    void nothingIsAnAnswer() {
        assertThat(RandomPick.some(HAND, 0, bound -> 0)).isEmpty();
        assertThat(RandomPick.some(HAND, -3, bound -> 0)).isEmpty();
    }

    @Test
    @DisplayName("one press cannot take a hundred cards")
    void thereIsACeiling() {
        List<String> huge = java.util.stream.IntStream.range(0, 60)
                .mapToObj(Integer::toString).toList();

        assertThat(RandomPick.some(huge, 60, bound -> 0)).hasSize(RandomPick.MOST_AT_ONCE);
    }

    @Test
    @DisplayName("the hand it was given is not reordered")
    void theSourceIsLeftAlone() {
        List<String> hand = new java.util.ArrayList<>(HAND);

        RandomPick.some(hand, 3, bound -> bound - 1);

        assertThat(hand).isEqualTo(HAND);
    }

    @Test
    @DisplayName("a source that ignores its own bound cannot read past the end")
    void anOutOfRangeAnswerIsNotACrash() {
        // The randomness is a function passed in. One that answered out of range would throw
        // on the server, in the middle of somebody's turn, over a discard.
        assertThat(RandomPick.some(HAND, 3, bound -> 9999)).hasSize(3);
        assertThat(RandomPick.some(HAND, 3, bound -> -9999)).hasSize(3);
    }

    @Property(tries = 2000)
    @Label("whatever it is asked, it takes distinct cards from the hand and no others")
    void itIsAlwaysADistinctSubset(
            @ForAll("hands") List<String> hand,
            @ForAll int howMany,
            @ForAll("randomness") IntUnaryOperator random) {
        List<String> taken = RandomPick.some(hand, howMany, random);

        assertThat(taken).doesNotHaveDuplicates();
        assertThat(hand).containsAll(taken);
        assertThat(taken).hasSizeLessThanOrEqualTo(
                Math.min(hand.size(), Math.max(0, Math.min(howMany, RandomPick.MOST_AT_ONCE))));
    }

    @Provide
    Arbitrary<List<String>> hands() {
        return Arbitraries.integers().between(0, 40).list().ofMaxSize(40)
                .map(numbers -> {
                    List<String> hand = new java.util.ArrayList<>();
                    for (int index = 0; index < numbers.size(); index++) {
                        hand.add("card-" + index);
                    }
                    return List.copyOf(hand);
                });
    }

    /** Sources that behave, and sources that do not. */
    @Provide
    Arbitrary<IntUnaryOperator> randomness() {
        return Arbitraries.integers().between(-5, 5).map(offset ->
                (IntUnaryOperator) bound -> bound <= 0 ? offset : Math.floorMod(bound + offset, bound) + offset);
    }
}
