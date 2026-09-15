package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Choosing to draw (MTR 2.2): the player the table started hands the first turn on, and it stays
 * turn one - so the player who now goes first is the one skipping their draw (rule 103.8a).
 */
class DrawChosenTest {

    private static GameSession twoPlayers() {
        GameSession session = GameSession.create(List.of(SeatId.of(0), SeatId.of(1)), 20, SessionSeed.random(),
                UndoMode.shippedDefault());
        for (int index = 0; index < 2; index++) {
            SeatId seat = SeatId.of(index);
            session.submit(new GameEvent.SeatTaken(seat, new PlayerRef(UUID.randomUUID(), "P" + index)));
            session.submit(new GameEvent.DeckLoaded(seat, GameFixtures.deck(40), List.of()));
        }
        return session;
    }

    @Test
    void theFirstPlayerHandsTheFirstTurnOnAndItStaysTurnOne() {
        GameSession session = twoPlayers();
        SeatId first = session.state().turn().activeSeat();
        SeatId other = first.equals(SeatId.of(0)) ? SeatId.of(1) : SeatId.of(0);

        assertThat(session.submit(new GameEvent.DrawChosen(first, other)))
                .isInstanceOf(GameSession.Result.Accepted.class);
        assertThat(session.state().turn()).isEqualTo(TurnMarker.start(other));
        assertThat(FirstDraw.isSkippedBy(2, session.state().turn(), other)).isTrue();
        assertThat(FirstDraw.isSkippedBy(2, session.state().turn(), first)).isFalse();
    }

    @Test
    void itIsNotAChoiceAnybodyElseOrLaterCanMake() {
        GameSession session = twoPlayers();
        SeatId first = session.state().turn().activeSeat();
        SeatId other = first.equals(SeatId.of(0)) ? SeatId.of(1) : SeatId.of(0);

        assertThat(session.submit(new GameEvent.DrawChosen(other, first)))
                .isInstanceOf(GameSession.Result.Rejected.class);
        session.submit(new GameEvent.TurnPassed(first, other));
        assertThat(session.submit(new GameEvent.DrawChosen(other, first)))
                .isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.state().turn().turnNumber()).isEqualTo(2);
    }
}
