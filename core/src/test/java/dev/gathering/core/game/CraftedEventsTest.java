package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Events no honest client sends, answered rather than folded.
 * <p>The interface never builds these. A modified client, or a packet that was honest when
 * it left and stale when it arrived, can - and each of these used to be folded into a board
 * nobody could draw, or into a log line about a change that never happened. Every one is now
 * a {@link GameSession.Result.Rejected} with a reason, and the board and the log are as they
 * were.
 */
class CraftedEventsTest {

    @Test
    @DisplayName("only a token can be removed from the table; a real card is refused")
    void aRealCardCannotBeRemoved() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId drawn = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        int logBefore = session.log().size();

        GameSession.Result result = session.submit(new GameEvent.TokenRemoved(GameFixtures.BOB, drawn));

        assertThat(result).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.state().card(drawn)).describedAs("the card is still in the session").isPresent();
        assertThat(session.log()).hasSize(logBefore);

        // And a token still goes the way tokens go.
        session.submit(new GameEvent.TokenCreated(GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(500), 1));
        CardInstanceId token = session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD).get(0);
        assertThat(session.submit(new GameEvent.TokenRemoved(GameFixtures.BOB, token)).isAccepted()).isTrue();
        assertThat(session.state().card(token)).isEmpty();
    }

    @Test
    @DisplayName("the turn cannot be passed to a seat the table does not have")
    void theTurnStaysAtTheTable() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        TurnMarker before = session.state().turn();

        GameSession.Result result = session.submit(new GameEvent.TurnPassed(GameFixtures.ALICE, SeatId.of(7)));

        assertThat(result).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.state().turn()).isEqualTo(before);
    }

    @Test
    @DisplayName("a card cannot be moved to a seat the table does not have")
    void nothingMovesToAMissingSeat() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId drawn = GameFixtures.firstInHand(session, GameFixtures.ALICE);

        GameSession.Result one = session.submit(new GameEvent.CardMoved(
                GameFixtures.ALICE, drawn, ZoneRef.of(SeatId.of(7), Zone.BATTLEFIELD), Placement.BOTTOM));
        GameSession.Result all = session.submit(new GameEvent.ZoneMoved(
                GameFixtures.ALICE, GameFixtures.ALICE, Zone.HAND, ZoneRef.of(SeatId.of(7), Zone.GRAVEYARD),
                Placement.TOP));

        assertThat(one).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(all).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.state().locationOf(drawn)).contains(ZoneRef.of(GameFixtures.ALICE, Zone.HAND));
        // Nothing leaked into a zone no view is built for.
        assertThat(session.state().contents(SeatId.of(7), Zone.BATTLEFIELD)).isEmpty();
        assertThat(session.state().contents(SeatId.of(7), Zone.GRAVEYARD)).isEmpty();
    }

    @Test
    @DisplayName("a counter with no name is refused, not written down")
    void aCounterNeedsAName() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId drawn = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        int logBefore = session.log().size();

        // The wide space an IME types for the space bar: nothing, once cleaned.
        GameSession.Result onCard = session.submit(new GameEvent.CounterChanged(GameFixtures.ALICE, drawn, "　", 1));
        GameSession.Result onSeat = session.submit(
                new GameEvent.SeatCounterChanged(GameFixtures.ALICE, GameFixtures.ALICE, "  ", 1));

        assertThat(onCard).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(onSeat).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.log()).describedAs("nothing was written about it").hasSize(logBefore);
    }

    @Test
    @DisplayName("a card full of kinds of counter refuses another out loud, and the log agrees")
    void theCounterCapIsARefusal() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId drawn = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        for (int kind = 0; kind < CounterName.MOST_PER_CARD; kind++) {
            assertThat(session.submit(new GameEvent.CounterChanged(GameFixtures.ALICE, drawn, "kind" + kind, 1))
                    .isAccepted()).isTrue();
        }
        int logBefore = session.log().size();

        GameSession.Result oneMore = session.submit(new GameEvent.CounterChanged(GameFixtures.ALICE, drawn, "one more", 1));

        assertThat(oneMore).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.log()).hasSize(logBefore);
        assertThat(session.state().requireCard(drawn).counters()).hasSize(CounterName.MOST_PER_CARD);
        // A kind it already carries is never refused, however full it is.
        assertThat(session.submit(new GameEvent.CounterChanged(GameFixtures.ALICE, drawn, "kind0", 2)).isAccepted())
                .isTrue();

        // The same for the counters beside a seat.
        for (int kind = 0; kind < CounterName.MOST_PER_CARD; kind++) {
            session.submit(new GameEvent.SeatCounterChanged(GameFixtures.BOB, GameFixtures.BOB, "kind" + kind, 1));
        }
        assertThat(session.submit(new GameEvent.SeatCounterChanged(GameFixtures.BOB, GameFixtures.BOB, "one more", 1)))
                .isInstanceOf(GameSession.Result.Rejected.class);
    }

    @Test
    @DisplayName("nothing rewinds once the game is over")
    void nothingRewindsPastTheEnd() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -3));
        session.submit(new GameEvent.SessionEnded(GameFixtures.ALICE, "test"));

        assertThat(session.evaluateUndo(GameFixtures.ALICE, 1)).isInstanceOf(UndoDecision.Denied.class);
        assertThat(session.undo(GameFixtures.ALICE, 2, List.of(GameFixtures.ALICE, GameFixtures.BOB)).isAccepted())
                .isFalse();
        assertThat(session.state().ended()).isTrue();
        assertThat(session.state().seatState(GameFixtures.ALICE).life()).isEqualTo(37);
    }

    @Test
    @DisplayName("a reorder naming one card for the top and the bottom both keeps it once")
    void aCardIsNotInTheLibraryTwice() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        List<CardInstanceId> library = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
        CardInstanceId twice = library.get(3);

        session.submit(new GameEvent.LibraryReordered(
                GameFixtures.ALICE, GameFixtures.ALICE, List.of(twice), List.of(twice)));

        List<CardInstanceId> after = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
        assertThat(after).hasSize(library.size()).doesNotHaveDuplicates();
        assertThat(after.get(0)).describedAs("the first decision, top, stands").isEqualTo(twice);
    }

    @Test
    @DisplayName("a rotation is logged as the angle the card was left at")
    void theLogSaysWhereTheCardEndedUp() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId drawn = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        session.submit(new GameEvent.CardMoved(
                GameFixtures.ALICE, drawn, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

        session.submit(new GameEvent.CardRotated(GameFixtures.ALICE, drawn, 450));

        assertThat(session.state().requireCard(drawn).placedAt().orElseThrow().rotation()).isEqualTo(90);
        assertThat(session.log().get(session.log().size() - 1).args())
                .anySatisfy(arg -> assertThat(arg.toString()).contains("90"))
                .noneSatisfy(arg -> assertThat(arg.toString()).contains("450"));
    }
}
