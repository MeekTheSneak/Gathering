package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Handing the turn on lands on a player.
 * <p>A cluster makes a seat for every place at it whether or not anybody is in one, so four
 * people at an eight-seat table were handing the turn to four empty chairs between every real
 * turn. Reported from the first four-player session, where the marker over the board spent the
 * evening naming nobody.
 */
final class TurnOrderTest {

    @Test
    @DisplayName("the turn skips chairs nobody is in")
    void theTurnSkipsEmptyChairs() {
        GameView board = tableOf(4, List.of(0, 2));

        // Seat 1 and seat 3 are chairs at the table that nobody ever sat in.
        assertThat(board.nextSeatWithABoard(SeatId.of(0))).isEqualTo(SeatId.of(2));
        assertThat(board.nextSeatWithABoard(SeatId.of(2))).isEqualTo(SeatId.of(0));
    }

    @Test
    @DisplayName("a seat somebody stood up from still takes its turn")
    void aBoardWithoutItsPlayerStillTakesItsTurn() {
        GameSession session = GameFixtures.table(3, 10);
        // Seat 1 walks away. Their cards are still on the table, so the table plays round
        // them rather than through them.
        session.submit(new GameEvent.SeatReleased(SeatId.of(1)));
        GameView board = VisibilityRules.viewFor(session.state(), Viewer.seat(SeatId.of(0)));

        assertThat(board.seat(SeatId.of(1)).occupant()).isEmpty();
        assertThat(board.seat(SeatId.of(1)).hasABoard()).isTrue();
        assertThat(board.nextSeatWithABoard(SeatId.of(0))).isEqualTo(SeatId.of(1));
    }

    @Test
    @DisplayName("one player at a big table keeps passing the turn to themselves")
    void aSoloGameKeepsTheTurn() {
        GameView board = tableOf(4, List.of(1));
        assertThat(board.nextSeatWithABoard(SeatId.of(1))).isEqualTo(SeatId.of(1));
    }

    @Test
    @DisplayName("a seat that is not at this table hands the turn straight back")
    void anUnknownSeatIsItsOwnAnswer() {
        GameView board = tableOf(2, List.of(0, 1));
        assertThat(board.nextSeatWithABoard(SeatId.of(7))).isEqualTo(SeatId.of(7));
    }

    /** A table of this many chairs with only the listed ones sat in and dealt a deck. */
    private static GameView tableOf(int chairs, List<Integer> seated) {
        List<SeatId> seats = new ArrayList<>(chairs);
        for (int index = 0; index < chairs; index++) {
            seats.add(SeatId.of(index));
        }
        GameSession session = GameSession.create(
                seats, 40, GameFixtures.FIXED_SEED, UndoMode.shippedDefault());
        for (int index : seated) {
            SeatId seat = SeatId.of(index);
            session.submit(new GameEvent.SeatTaken(seat, new PlayerRef(UUID.randomUUID(), "P" + index)));
            session.submit(new GameEvent.DeckLoaded(seat, GameFixtures.deck(10), List.of()));
        }
        return VisibilityRules.viewFor(session.state(), Viewer.SPECTATOR);
    }
}
