package dev.gathering.core.game;

import static dev.gathering.core.game.GameFixtures.ALICE;
import static dev.gathering.core.game.GameFixtures.BOB;
import static dev.gathering.core.game.GameFixtures.twoPlayerTable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.ZoneView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Turning your hand round, as a rule.
 *
 * <p>The one feature in the mod that deliberately opens a hidden zone, so it is the one that
 * has to be provably narrow: your own hand, to seats you named, until you take it back. Every
 * test here is really the same question asked from a different chair - can anybody see a hand
 * its owner did not turn toward them.
 */
class ShownHandTest {

    private static ZoneView handOf(GameSession session, SeatId owner, Viewer viewer) {
        GameView board = VisibilityRules.viewFor(session.state(), viewer);
        return board.seat(owner).zone(Zone.HAND);
    }

    private static GameSession dealt() {
        GameSession session = twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 3));
        return session;
    }

    @Test
    @DisplayName("a hand nobody was shown is a count to everybody else")
    void aClosedHandIsACount() {
        GameSession session = dealt();

        ZoneView asBobSeesIt = handOf(session, ALICE, new Viewer.Seated(BOB));

        assertThat(asBobSeesIt.count()).isEqualTo(3);
        assertThat(asBobSeesIt.cards()).isEmpty();
    }

    @Test
    @DisplayName("a hand turned toward one player is readable by that player")
    void oneSeatCanRead() {
        GameSession session = dealt();

        session.submit(new GameEvent.HandShown(ALICE, BOB, true));

        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards()).hasSize(3);
    }

    @Test
    @DisplayName("and by nobody else, watchers included")
    void nobodyElseCanRead() {
        GameSession session = dealt();

        session.submit(new GameEvent.HandShown(ALICE, BOB, true));

        assertThat(handOf(session, ALICE, Viewer.SPECTATOR).cards())
                .describedAs("a watcher was shown a hand that was turned toward a player")
                .isEmpty();
    }

    @Test
    @DisplayName("showing the table is showing every other seat, and not the watchers")
    void showingTheTable() {
        GameSession session = dealt();

        session.submit(new GameEvent.HandShown(ALICE, null, true));

        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards()).hasSize(3);
        assertThat(handOf(session, ALICE, Viewer.SPECTATOR).cards()).isEmpty();
        assertThat(VisibilityRules.viewFor(session.state(), new Viewer.Seated(BOB))
                .seat(ALICE).handIsShownTo(BOB)).isTrue();
    }

    @Test
    @DisplayName("taking it back closes it again")
    void takingItBack() {
        GameSession session = dealt();
        session.submit(new GameEvent.HandShown(ALICE, null, true));

        session.submit(new GameEvent.HandShown(ALICE, null, false));

        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards()).isEmpty();
        assertThat(session.state().seatState(ALICE).handIsShown()).isFalse();
    }

    @Test
    @DisplayName("nobody can open somebody else's hand")
    void onlyYourOwnHand() {
        GameSession session = dealt();

        // Bob asking to see Alice's hand, which is the packet the whole rule exists to stop.
        // It is refused because the actor is the seat: there is nowhere in the event to name
        // whose hand it is, so the worst a modified client can do is show its own.
        GameSession.Result result = session.submit(new GameEvent.HandShown(BOB, ALICE, true));

        assertThat(result.isAccepted())
                .describedAs("the event was accepted, which is fine - what matters is whose hand it opened")
                .isTrue();
        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards())
                .describedAs("Bob opened Alice's hand by naming her in his own event")
                .isEmpty();
    }

    @Test
    @DisplayName("showing one player and then another shows both")
    void showingAddsUp() {
        GameSession session = GameFixtures.table(3, 10);
        SeatId alice = SeatId.of(0);
        SeatId bob = SeatId.of(1);
        SeatId chris = SeatId.of(2);
        session.submit(new GameEvent.CardsDrawn(alice, alice, 2));

        session.submit(new GameEvent.HandShown(alice, bob, true));
        session.submit(new GameEvent.HandShown(alice, chris, true));

        assertThat(handOf(session, alice, new Viewer.Seated(bob)).cards()).hasSize(2);
        assertThat(handOf(session, alice, new Viewer.Seated(chris)).cards()).hasSize(2);

        session.submit(new GameEvent.HandShown(alice, bob, false));

        assertThat(handOf(session, alice, new Viewer.Seated(bob)).cards()).isEmpty();
        assertThat(handOf(session, alice, new Viewer.Seated(chris)).cards()).hasSize(2);
    }
}
