package dev.gathering.core.match;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Working out that a game is over, without a rules engine to tell us.
 * <p>The only result the mod recognizes is conceding. That is a deliberate limit and the tests
 * below are mostly about what it refuses to conclude: a player on zero life has not lost, a
 * player on minus four about to gain twelve has certainly not lost, and an empty chair is not
 * a defeated opponent.
 */
class GameOutcomeTest {

    private static final SeatId ALICE = new SeatId(0);
    private static final SeatId BOB = new SeatId(1);
    private static final SeatId CHRIS = new SeatId(2);

    @Test
    @DisplayName("a game with everybody still in it is not over")
    void aLiveGameHasNoWinner() {
        GameSession session = table(ALICE, BOB);

        assertThat(GameOutcome.winnerOf(session.state())).isEmpty();
        assertThat(GameOutcome.isFinished(session.state())).isFalse();
    }

    @Test
    @DisplayName("the last player standing wins it")
    void concedingLeavesAWinner() {
        GameSession session = table(ALICE, BOB);

        session.submit(new GameEvent.Conceded(BOB));

        assertThat(GameOutcome.winnerOf(session.state())).contains(ALICE);
        assertThat(GameOutcome.isFinished(session.state())).isTrue();
    }

    @Test
    @DisplayName("one player out of three conceding settles nothing")
    void aPodKeepsPlaying() {
        GameSession session = table(ALICE, BOB, CHRIS);

        session.submit(new GameEvent.Conceded(CHRIS));

        assertThat(GameOutcome.winnerOf(session.state())).isEmpty();
        assertThat(GameOutcome.isFinished(session.state())).isFalse();
    }

    @Test
    @DisplayName("everybody conceding is a drawn game, not a win for the last one to click")
    void aScoopedGameHasNoWinner() {
        GameSession session = table(ALICE, BOB);

        session.submit(new GameEvent.Conceded(ALICE));
        session.submit(new GameEvent.Conceded(BOB));

        assertThat(GameOutcome.winnerOf(session.state())).isEmpty();
        assertThat(GameOutcome.isFinished(session.state())).isTrue();
    }

    @Test
    @DisplayName("zero life is a number reaching zero, and nothing else")
    void lifeTotalsDecideNothing() {
        // There is no rules engine here on purpose. A player on zero who is about to gain
        // twelve has not lost, and a mod that decided otherwise would be wrong at the exact
        // moment it mattered most.
        GameSession session = table(ALICE, BOB);

        session.submit(new GameEvent.LifeChanged(BOB, BOB, -40));

        assertThat(session.state().seatState(BOB).life()).isNotPositive();
        assertThat(GameOutcome.winnerOf(session.state())).isEmpty();
        assertThat(GameOutcome.isFinished(session.state())).isFalse();
    }

    @Nested
    @DisplayName("playing alone")
    class PlayingAlone {

        @Test
        @DisplayName("a solo game is not over the moment it starts")
        void goldfishingIsPossible() {
            // The one that broke it: one player left standing out of one looked like a last
            // player standing, so a solo game ended on its first action. Goldfishing is how
            // anybody tests a deck, and a mod that cannot do it cannot be used alone at all.
            GameSession session = table(ALICE);

            assertThat(GameOutcome.isFinished(session.state())).isFalse();
            assertThat(GameOutcome.winnerOf(session.state())).isEmpty();
        }

        @Test
        @DisplayName("nor after doing things in it")
        void aSoloGameSurvivesBeingPlayed() {
            GameSession session = table(ALICE);

            session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 7));
            session.submit(new GameEvent.LifeChanged(ALICE, ALICE, -3));
            session.submit(new GameEvent.LibraryShuffled(ALICE, ALICE));

            assertThat(GameOutcome.isFinished(session.state())).isFalse();
        }

        @Test
        @DisplayName("a solo game at a table with empty chairs is still a solo game")
        void emptyChairsDoNotMakeItAMatch() {
            // A table cluster gives the session a seat per chair whether anybody is in it or
            // not, so this is what playing alone actually looks like.
            GameSession session = GameSession.create(
                    List.of(ALICE, BOB, CHRIS), 20, SessionSeed.random(), UndoMode.shippedDefault());
            session.submit(new GameEvent.SeatTaken(ALICE, player("Alice")));

            assertThat(GameOutcome.isFinished(session.state())).isFalse();
        }

        @Test
        @DisplayName("scooping ends it, because that is the one result there is")
        void concedingEndsASoloGame() {
            GameSession session = table(ALICE);

            session.submit(new GameEvent.Conceded(ALICE));

            assertThat(GameOutcome.isFinished(session.state())).isTrue();
            assertThat(GameOutcome.winnerOf(session.state())).isEmpty();
        }

        @Test
        @DisplayName("somebody sitting down turns it into a game that can be won")
        void asecondPlayerMakesItAMatch() {
            GameSession session = GameSession.create(
                    List.of(ALICE, BOB), 20, SessionSeed.random(), UndoMode.shippedDefault());
            session.submit(new GameEvent.SeatTaken(ALICE, player("Alice")));
            assertThat(GameOutcome.isFinished(session.state())).isFalse();

            session.submit(new GameEvent.SeatTaken(BOB, player("Bob")));
            session.submit(new GameEvent.Conceded(BOB));

            assertThat(GameOutcome.winnerOf(session.state())).contains(ALICE);
        }
    }

    @Test
    @DisplayName("an empty chair is not a defeated opponent")
    void emptySeatsAreNotPlayers() {
        // Every seat the table has becomes a seat in the session whether or not anybody is in
        // it, so a two-player game at a four-seat cluster has two empty chairs. Counting them
        // as players would mean no game there ever ended.
        GameSession session = GameSession.create(
                List.of(ALICE, BOB, CHRIS), 20, SessionSeed.random(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE, player("Alice")));
        session.submit(new GameEvent.SeatTaken(BOB, player("Bob")));

        session.submit(new GameEvent.Conceded(BOB));

        assertThat(GameOutcome.winnerOf(session.state())).contains(ALICE);
    }

    @Test
    @DisplayName("a table nobody is sitting at has not finished a game")
    void anEmptyTableIsNotAFinishedGame() {
        GameSession session = GameSession.create(
                List.of(ALICE, BOB), 20, SessionSeed.random(), UndoMode.shippedDefault());

        assertThat(GameOutcome.isFinished(session.state())).isFalse();
        assertThat(GameOutcome.winnerOf(session.state())).isEmpty();
    }

    private static GameSession table(SeatId... seats) {
        GameSession session = GameSession.create(
                List.of(seats), 20, SessionSeed.random(), UndoMode.shippedDefault());
        for (SeatId seat : seats) {
            session.submit(new GameEvent.SeatTaken(seat, player("Player " + seat.index())));
        }
        return session;
    }

    private static PlayerRef player(String name) {
        return new PlayerRef(new UUID(0L, name.hashCode()), name);
    }
}
