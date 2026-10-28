package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Putting a hand in a different order")
class HandSortedTest {

    @Test
    @DisplayName("the hand comes back in the order that was asked for")
    void itReorders() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        session.submit(new GameEvent.CardsDrawn(me, me, 4));
        List<CardInstanceId> hand = session.state().contents(me, Zone.HAND);

        List<CardInstanceId> backwards = new ArrayList<>(hand);
        java.util.Collections.reverse(backwards);
        session.submit(new GameEvent.HandSorted(me, me, backwards));

        assertThat(session.state().contents(me, Zone.HAND)).isEqualTo(backwards);
    }

    @Test
    @DisplayName("a card drawn since the sort was worked out is not lost")
    void aStaleOrderKeepsEverything() {
        // The client works the order out from what it can see, and a card can arrive between
        // that and the packet landing. Losing it would be a card gone from the game.
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        session.submit(new GameEvent.CardsDrawn(me, me, 3));
        List<CardInstanceId> asSeen = List.copyOf(session.state().contents(me, Zone.HAND));

        session.submit(new GameEvent.CardsDrawn(me, me, 1));
        List<CardInstanceId> withTheNewOne = session.state().contents(me, Zone.HAND);
        CardInstanceId arrivedLate = withTheNewOne.get(withTheNewOne.size() - 1);

        session.submit(new GameEvent.HandSorted(me, me, asSeen));

        List<CardInstanceId> after = session.state().contents(me, Zone.HAND);
        assertThat(after).hasSize(4).contains(arrivedLate);
        // Named first, in the order named; the straggler keeps its place behind them.
        assertThat(after.subList(0, 3)).isEqualTo(asSeen);
        assertThat(after.get(3)).isEqualTo(arrivedLate);
    }

    @Test
    @DisplayName("a made-up list cannot put a card into a hand")
    void anInventedOrderAddsNothing() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        SeatId them = session.state().seats().get(1);
        session.submit(new GameEvent.CardsDrawn(me, me, 2));
        session.submit(new GameEvent.CardsDrawn(them, them, 2));
        List<CardInstanceId> mine = List.copyOf(session.state().contents(me, Zone.HAND));
        List<CardInstanceId> theirs = session.state().contents(them, Zone.HAND);

        // Somebody else's cards, and a card id that does not exist at all.
        List<CardInstanceId> nonsense = new ArrayList<>(theirs);
        nonsense.add(new CardInstanceId(99_999));
        nonsense.addAll(mine);
        session.submit(new GameEvent.HandSorted(me, me, nonsense));

        assertThat(session.state().contents(me, Zone.HAND)).containsExactlyElementsOf(mine);
        assertThat(session.state().contents(them, Zone.HAND)).containsExactlyElementsOf(theirs);
    }

    @Test
    @DisplayName("a card named twice is only in the hand once")
    void duplicatesCollapse() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        session.submit(new GameEvent.CardsDrawn(me, me, 2));
        List<CardInstanceId> hand = List.copyOf(session.state().contents(me, Zone.HAND));

        session.submit(new GameEvent.HandSorted(
                me, me, List.of(hand.get(1), hand.get(1), hand.get(0), hand.get(1))));

        assertThat(session.state().contents(me, Zone.HAND))
                .containsExactly(hand.get(1), hand.get(0));
    }

    @Test
    @DisplayName("sorting nothing is not a failure")
    void anEmptyOrderLeavesItAlone() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        session.submit(new GameEvent.CardsDrawn(me, me, 2));
        List<CardInstanceId> hand = List.copyOf(session.state().contents(me, Zone.HAND));

        session.submit(new GameEvent.HandSorted(me, me, List.of()));

        assertThat(session.state().contents(me, Zone.HAND)).isEqualTo(hand);
    }
}
