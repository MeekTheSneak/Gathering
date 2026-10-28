package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A card that does not exist and a card that could not be looked up are different answers. */
class UnresolvedCardsTest {

    private static final UUID GHOST = new UUID(1L, 1L);
    private static final UUID OTHER = new UUID(1L, 2L);

    @Test
    void aMissingCardIsNotAskedAboutAgainForAWhile() {
        UnresolvedCards cards = new UnresolvedCards();
        cards.missing(List.of(GHOST), 1_000L);

        assertThat(cards.reasonFor(GHOST, 2_000L)).contains(UnresolvedCards.Reason.MISSING);
        assertThat(cards.alreadyAnswered(GHOST, 2_000L)).isTrue();
        assertThat(cards.reasonFor(OTHER, 2_000L)).isEmpty();
    }

    @Test
    void noSuchCardIsBelievedOnlyForSoLong() {
        UnresolvedCards cards = new UnresolvedCards();
        cards.missing(List.of(GHOST), 0L);

        long later = UnresolvedCards.MISSING_FOR_MILLIS;
        assertThat(cards.alreadyAnswered(GHOST, later)).isFalse();
        assertThat(cards.reasonFor(GHOST, later)).isEmpty();
        assertThat(cards.size()).isZero();
    }

    /** An outage must never become a permanent negative answer. */
    @Test
    void anUnavailableCardIsShownButNeverStopsARetry() {
        UnresolvedCards cards = new UnresolvedCards();
        cards.unavailable(List.of(GHOST), 0L);

        assertThat(cards.reasonFor(GHOST, 10L)).contains(UnresolvedCards.Reason.UNAVAILABLE);
        assertThat(cards.alreadyAnswered(GHOST, 10L)).isFalse();
        assertThat(cards.reasonFor(GHOST, UnresolvedCards.MISSING_FOR_MILLIS * 4))
                .contains(UnresolvedCards.Reason.UNAVAILABLE);
    }

    @Test
    void anOutageDoesNotOverruleAnAnswer() {
        UnresolvedCards cards = new UnresolvedCards();
        cards.missing(List.of(GHOST), 0L);
        cards.unavailable(List.of(GHOST), 5L);

        assertThat(cards.reasonFor(GHOST, 6L)).contains(UnresolvedCards.Reason.MISSING);
    }

    @Test
    void aNameArrivingSettlesEitherKind() {
        UnresolvedCards cards = new UnresolvedCards();
        cards.missing(List.of(GHOST), 0L);
        cards.unavailable(List.of(OTHER), 0L);

        cards.found(GHOST);
        cards.found(OTHER);

        assertThat(cards.reasonFor(GHOST, 1L)).isEmpty();
        assertThat(cards.reasonFor(OTHER, 1L)).isEmpty();
    }

    @Test
    void itForgetsTheOldestPastItsBound() {
        UnresolvedCards cards = new UnresolvedCards();
        List<UUID> many = new ArrayList<>();
        for (int index = 0; index < UnresolvedCards.MOST_REMEMBERED + 10; index++) {
            many.add(new UUID(2L, index));
        }
        cards.missing(many, 0L);

        assertThat(cards.size()).isEqualTo(UnresolvedCards.MOST_REMEMBERED);
        assertThat(cards.reasonFor(many.get(0), 1L)).isEmpty();
        assertThat(cards.reasonFor(many.get(many.size() - 1), 1L)).isPresent();
    }

    @Test
    void clearingForgetsEverything() {
        UnresolvedCards cards = new UnresolvedCards();
        cards.missing(List.of(GHOST), 0L);
        cards.unavailable(List.of(OTHER), 0L);
        cards.clear();
        assertThat(cards.size()).isZero();
    }
}
