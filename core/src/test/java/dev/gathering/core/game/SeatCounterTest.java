package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The numbers a player accumulates that are not life.
 *
 * <p>Poison, energy, experience, and whatever a group has decided to track this week. Kept as
 * an open bag of named numbers rather than three fields, because the set of things a player
 * can accumulate is not one anybody can finish listing - the last three sets added two of
 * them.
 *
 * <p>Deliberately separate from the counters on a card. A counter on a player and a counter on
 * a permanent are different things that share a word, and "three poison" and "three +1/+1
 * counters on the bear" are not the same sentence.
 */
class SeatCounterTest {

    @Test
    @DisplayName("a counter starts at nothing and goes up")
    void countersAccumulate() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        assertThat(session.state().seatState(GameFixtures.ALICE).counter(SeatState.Counters.POISON))
                .isZero();

        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, SeatState.Counters.POISON, 3));

        assertThat(session.state().seatState(GameFixtures.ALICE).counter(SeatState.Counters.POISON))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("counters go negative, because the mod does not argue with a player")
    void countersGoNegative() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, SeatState.Counters.ENERGY, -2));

        assertThat(session.state().seatState(GameFixtures.ALICE).counter(SeatState.Counters.ENERGY))
                .isEqualTo(-2);
    }

    @Test
    @DisplayName("a counter back at zero stops being a counter rather than showing a zero")
    void zeroIsNotACounter() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, SeatState.Counters.POISON, 2));

        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, SeatState.Counters.POISON, -2));

        assertThat(session.state().seatState(GameFixtures.ALICE).counters()).isEmpty();
    }

    @Test
    @DisplayName("counters are per seat, not per table")
    void countersBelongToOneSeat() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, SeatState.Counters.POISON, 4));

        assertThat(session.state().seatState(GameFixtures.BOB).counter(SeatState.Counters.POISON))
                .isZero();
    }

    @Test
    @DisplayName("a name nobody has heard of is as good as poison")
    void anyNameWorks() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, "rad", 7));

        assertThat(session.state().seatState(GameFixtures.ALICE).counter("rad")).isEqualTo(7);
    }

    @Test
    @DisplayName("giving somebody else poison is allowed, because that is what poison is")
    void anyoneMayChangeAnyonesCounters() {
        // Nothing here is owner-locked: the restriction is about seeing hidden cards, and a
        // number beside a seat is the most public thing at the table.
        GameSession session = GameFixtures.twoPlayerTable(10);

        GameSession.Result result = session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.BOB, SeatState.Counters.POISON, 1));

        assertThat(result.isAccepted()).isTrue();
        assertThat(session.state().seatState(GameFixtures.BOB).counter(SeatState.Counters.POISON))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("everybody can see everybody's counters")
    void countersAreEntirelyPublic() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, SeatState.Counters.EXPERIENCE, 2));

        for (GameView view : VisibilityRules.allViews(session.state()).values()) {
            assertThat(view.seat(GameFixtures.ALICE).counter(SeatState.Counters.EXPERIENCE))
                    .as("counters seen by %s", view.viewer())
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("undoing a counter takes it back, because state is the fold of the log")
    void undoWorksWithoutAnythingBeingWrittenForIt() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, SeatState.Counters.POISON, 5));

        session.undo(GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB));

        assertThat(session.state().seatState(GameFixtures.ALICE).counter(SeatState.Counters.POISON))
                .isZero();
    }
}
