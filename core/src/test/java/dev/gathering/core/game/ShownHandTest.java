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

    /**
     * The table is the people sitting at it, not its chairs.
     * <p>From the 2026-09-25 sweep: "the whole table" went to every seat in the session, the empty
     * ones too, so a watcher who joined a free seat afterwards was sent a hand shown before they
     * sat down - and could get up again at once, holding it.
     */
    @Test
    @DisplayName("showing the table does not show a chair nobody was sitting in")
    void anEmptyChairIsNotShown() {
        SeatId alice = SeatId.of(0);
        SeatId bob = SeatId.of(1);
        SeatId empty = SeatId.of(2);
        GameSession session = GameSession.create(
                java.util.List.of(alice, bob, empty, SeatId.of(3)), 40, GameFixtures.FIXED_SEED,
                UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(alice, new PlayerRef(java.util.UUID.randomUUID(), "Alice")));
        session.submit(new GameEvent.SeatTaken(bob, new PlayerRef(java.util.UUID.randomUUID(), "Bob")));
        session.submit(new GameEvent.DeckLoaded(alice, GameFixtures.deck(10), java.util.List.of()));
        session.submit(new GameEvent.CardsDrawn(alice, alice, 3));

        session.submit(new GameEvent.HandShown(alice, null, true));
        session.submit(new GameEvent.SeatTaken(empty, new PlayerRef(java.util.UUID.randomUUID(), "Carol")));

        assertThat(handOf(session, alice, new Viewer.Seated(empty)).cards())
                .describedAs("a player who sat down after the table was shown a hand")
                .isEmpty();
        assertThat(handOf(session, alice, new Viewer.Seated(bob)).cards()).hasSize(3);
        assertThat(handOf(session, alice, Viewer.SPECTATOR).cards()).isEmpty();
    }

    /** A hand shown to one player is shown to that player, and does not go with their chair. */
    @Test
    @DisplayName("a hand shown to one player is not shown to whoever takes their chair")
    void aShownHandDoesNotPassWithTheChair() {
        GameSession session = dealt();
        session.submit(new GameEvent.HandShown(ALICE, BOB, true));

        session.submit(new GameEvent.SeatReleased(BOB));

        // Read by the chair before anybody new is in it: a board with no cards on it is handed to
        // whoever sits down next without their taking the seat, and that is the same view.
        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards())
                .describedAs("the chair Bob left, read before anybody sat in it")
                .isEmpty();
        session.submit(new GameEvent.SeatTaken(BOB, new PlayerRef(java.util.UUID.randomUUID(), "Dave")));
        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards())
                .describedAs("a hand shown to Bob, read by Dave in Bob's chair")
                .isEmpty();
    }

    /** And a hand its player showed does not stay turned round for whoever takes their seat. */
    @Test
    @DisplayName("a hand its player showed is not left shown for whoever takes their seat")
    void aHandShownBySomebodyWhoLeftIsNotLeftOpen() {
        GameSession session = dealt();
        session.submit(new GameEvent.HandShown(ALICE, BOB, true));

        session.submit(new GameEvent.SeatReleased(ALICE));
        session.submit(new GameEvent.SeatTaken(ALICE, new PlayerRef(java.util.UUID.randomUUID(), "Dave")));
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));

        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards())
                .describedAs("Dave's hand, open to Bob because Alice once showed hers")
                .isEmpty();
        assertThat(session.state().seatState(ALICE).handIsShown()).isFalse();
    }

    /**
     * Nor one chair with nobody in it, which is what the chooser sends when its player got up a
     * moment before it was pressed.
     */
    @Test
    @DisplayName("a hand is not turned toward a chair nobody is sitting in")
    void notTowardAnEmptyChair() {
        GameSession session = dealt();
        session.submit(new GameEvent.SeatReleased(BOB));

        GameSession.Result shown = session.submit(new GameEvent.HandShown(ALICE, BOB, true));
        session.submit(new GameEvent.SeatTaken(BOB, new PlayerRef(java.util.UUID.randomUUID(), "Dave")));

        assertThat(shown).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards())
                .describedAs("a hand turned toward an empty chair, read by whoever sat down in it")
                .isEmpty();
    }

    /** And a game an older server recorded with one in it still folds, with the chair not shown. */
    @Test
    @DisplayName("a game recorded showing an empty chair folds without showing it")
    void anOldRecordOfAnEmptyChairFoldsSafely() {
        GameSession played = dealt();
        played.submit(new GameEvent.SeatReleased(BOB));
        java.util.List<SessionRecord> records = new java.util.ArrayList<>(played.records());
        long next = records.get(records.size() - 1).sequence() + 1;
        records.add(new SessionRecord.EventRecord(next, new GameEvent.HandShown(ALICE, BOB, true), false));
        records.add(new SessionRecord.EventRecord(next + 1,
                new GameEvent.SeatTaken(BOB, new PlayerRef(java.util.UUID.randomUUID(), "Dave")), false));

        GameSession restored = GameSession.restore(java.util.List.of(ALICE, BOB), 40, GameFixtures.FIXED_SEED,
                UndoMode.shippedDefault(), records);

        assertThat(handOf(restored, ALICE, new Viewer.Seated(BOB)).cards()).isEmpty();
    }

    /** A chair somebody else sits down in without its being let go first is shown nothing either. */
    @Test
    @DisplayName("a hand shown to a chair is not kept for somebody else sitting down in it")
    void aChairTakenOverIsShownNothing() {
        GameSession session = dealt();
        session.submit(new GameEvent.HandShown(ALICE, BOB, true));

        session.submit(new GameEvent.SeatTaken(BOB, new PlayerRef(java.util.UUID.randomUUID(), "Dave")));

        assertThat(handOf(session, ALICE, new Viewer.Seated(BOB)).cards()).isEmpty();
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
