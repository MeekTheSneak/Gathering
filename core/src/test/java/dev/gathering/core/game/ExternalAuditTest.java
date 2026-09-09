package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The behaviours an outside audit reproduced against this code, each now refused.
 * <p>Every test here was a probe that passed - that is, the defect happened - before the fix
 * beside it. They are kept together because they came from one reading of the mod by somebody
 * who did not write it, and because the shape of each is the same: an event no honest client
 * sends, arriving anyway, and being folded into a board rather than answered.
 */
class ExternalAuditTest {

    @Nested
    @DisplayName("a card in somebody else's hand or library")
    class HiddenCards {

        /**
         * Writing on a card nobody may name turns a guess into a marker.
         * <p>Card instance ids are consecutive, so a modified client can name a card in an
         * opponent's library by counting. Writing a note on it was accepted, and the note
         * came back out on that card's anonymous view once it was played face down - so the
         * note followed the card, and an opponent who could not read the card could still
         * recognise it wherever it went.
         */
        @Test
        @DisplayName("cannot be written on by anybody else")
        void nobodyWritesOnACardTheyCannotSee() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            CardInstanceId inBobsLibrary = session.state().contents(GameFixtures.BOB, Zone.LIBRARY).get(0);

            GameSession.Result marked = session.submit(
                    new GameEvent.CardNoted(GameFixtures.ALICE, inBobsLibrary, "tag"));

            assertThat(marked).isInstanceOf(GameSession.Result.Rejected.class);
            assertThat(session.state().requireCard(inBobsLibrary).note()).isNull();

            // And once it is face down on the table, no note went with it.
            session.submit(new GameEvent.CardFacingSet(GameFixtures.BOB, inBobsLibrary, Facing.FACE_DOWN));
            session.submit(new GameEvent.CardMoved(GameFixtures.BOB, inBobsLibrary,
                    ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.BOTTOM));
            CardView seen = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.ALICE))
                    .seat(GameFixtures.BOB).zone(Zone.BATTLEFIELD).cards().get(0);
            assertThat(seen).isInstanceOf(CardView.Anonymous.class);
            assertThat(((CardView.Anonymous) seen).note()).isNull();
        }

        /** The same rule for every other way of laying a hand on a card. */
        @Test
        @DisplayName("cannot be tapped, turned, counted or pointed at either")
        void norTouchedAnyOtherWay() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            CardInstanceId inBobsLibrary = session.state().contents(GameFixtures.BOB, Zone.LIBRARY).get(0);
            SeatId alice = GameFixtures.ALICE;

            List<GameEvent> tried = List.of(
                    new GameEvent.CardTapSet(alice, inBobsLibrary, true),
                    new GameEvent.CardFrozen(alice, inBobsLibrary, true),
                    new GameEvent.CardTurnedOver(alice, inBobsLibrary, true),
                    new GameEvent.CardRotated(alice, inBobsLibrary, 90),
                    new GameEvent.CardStrengthSet(alice, inBobsLibrary, "9/9"),
                    new GameEvent.CounterChanged(alice, inBobsLibrary, "+1/+1", 1),
                    new GameEvent.CardPinged(alice, inBobsLibrary));

            for (GameEvent event : tried) {
                assertThat(session.submit(event))
                        .describedAs("%s against a card in Bob's library", event.getClass().getSimpleName())
                        .isInstanceOf(GameSession.Result.Rejected.class);
            }
            CardInstance untouched = session.state().requireCard(inBobsLibrary);
            assertThat(untouched.tapped()).isFalse();
            assertThat(untouched.counters()).isEmpty();
            assertThat(untouched.strength()).isNull();
        }

        /** Your own, though, are yours to write on: a note in your own hand is a reminder. */
        @Test
        @DisplayName("your own are still yours to write on")
        void yourOwnAreStillYours() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId mine = GameFixtures.firstInHand(session, GameFixtures.ALICE);

            assertThat(session.submit(new GameEvent.CardNoted(GameFixtures.ALICE, mine, "on the play"))
                    .isAccepted()).isTrue();
            assertThat(session.state().requireCard(mine).note()).isEqualTo("on the play");
        }

        /** And a hand is its owner's to arrange, whatever the log would otherwise have said. */
        @Test
        @DisplayName("somebody else's hand is not yours to sort")
        void nobodySortsSomebodyElsesHand() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 3));
            List<CardInstanceId> hand = session.state().contents(GameFixtures.BOB, Zone.HAND);

            assertThat(session.submit(new GameEvent.HandSorted(
                    GameFixtures.ALICE, GameFixtures.BOB, List.of(hand.get(2), hand.get(0), hand.get(1)))))
                    .isInstanceOf(GameSession.Result.Rejected.class);
            assertThat(session.state().contents(GameFixtures.BOB, Zone.HAND)).isEqualTo(hand);
        }
    }

    @Nested
    @DisplayName("a look at a library")
    class Looking {

        /**
         * A look is at cards, not at a depth.
         * <p>The count was positional, so looking at the top card and then drawing it slid
         * the window down onto the card behind it: one look, two cards, and the second one
         * never announced by any event.
         */
        @Test
        @DisplayName("does not follow the top down as cards leave")
        void drawingDoesNotSlideTheLookDown() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibraryLooked(GameFixtures.BOB, GameFixtures.BOB, 1));
            assertThat(openTo(session, GameFixtures.BOB)).hasSize(1);

            session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 1));

            assertThat(openTo(session, GameFixtures.BOB))
                    .describedAs("the card behind the one that was looked at")
                    .isEmpty();
        }

        /** The same when somebody else disturbs the library while you are looking at it. */
        @Test
        @DisplayName("closes when the library it is at is milled")
        void millingClosesTheLook() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibraryLooked(GameFixtures.BOB, GameFixtures.BOB, 3));

            session.submit(new GameEvent.LibraryMilled(GameFixtures.ALICE, GameFixtures.BOB, 1));

            assertThat(openTo(session, GameFixtures.BOB)).isEmpty();
        }

        /** And a look at an undisturbed library stays open, or the verb would be useless. */
        @Test
        @DisplayName("stays open while nothing moves")
        void anUndisturbedLookStaysOpen() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibraryLooked(GameFixtures.BOB, GameFixtures.BOB, 3));

            session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -1));

            assertThat(openTo(session, GameFixtures.BOB)).hasSize(3);
        }

        private List<CardView> openTo(GameSession session, SeatId seat) {
            return VisibilityRules.viewFor(session.state(), Viewer.seat(seat))
                    .seat(seat).zone(Zone.LIBRARY).cards();
        }
    }

    @Nested
    @DisplayName("a seat or a commander nobody has")
    class Phantoms {

        /** A token for a seat that is not at the table went into a zone no view is built for. */
        @Test
        @DisplayName("a token for a seat that does not exist is refused")
        void noTokensForPhantomSeats() {
            GameSession session = GameFixtures.twoPlayerTable(20);

            GameSession.Result made = session.submit(new GameEvent.TokenCreated(
                    GameFixtures.ALICE, SeatId.of(99), CardIdentity.ofCustom("audit", false), 1));

            assertThat(made).isInstanceOf(GameSession.Result.Rejected.class);
            assertThat(session.state().contents(SeatId.of(99), Zone.BATTLEFIELD)).isEmpty();
            assertThat(VisibilityRules.viewFor(session.state(), Viewer.SPECTATOR).allCardViews())
                    .describedAs("nothing exists that no view can show")
                    .allSatisfy(card -> assertThat(card).isNotNull());
        }

        /** Commander tax is charged against a commander, and a number is not one. */
        @Test
        @DisplayName("commander tax on a card nobody played is refused")
        void noTaxOnACardNobodyPlayed() {
            GameSession session = GameFixtures.twoPlayerTable(20);

            assertThat(session.submit(new GameEvent.CommanderTaxChanged(
                    GameFixtures.ALICE, GameFixtures.BOB, CardInstanceId.of(999_999), 1)))
                    .isInstanceOf(GameSession.Result.Rejected.class);
            assertThat(session.state().seatState(GameFixtures.BOB).commanderTax()).isEmpty();
        }

        /** The real one still works, or the check would have taken the feature with it. */
        @Test
        @DisplayName("a real commander is still taxed and still deals damage")
        void arealCommanderStillWorks() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            CardInstanceId commander = session.state().seatState(GameFixtures.BOB).commanders().get(0);

            assertThat(session.submit(new GameEvent.CommanderTaxChanged(
                    GameFixtures.BOB, GameFixtures.BOB, commander, 1)).isAccepted()).isTrue();
            assertThat(session.submit(new GameEvent.CommanderDamageChanged(
                    GameFixtures.BOB, GameFixtures.ALICE, commander, 3)).isAccepted()).isTrue();
            assertThat(session.state().seatState(GameFixtures.BOB).commanderTax()).containsEntry(commander, 1);
            assertThat(session.state().seatState(GameFixtures.ALICE).commanderDamage())
                    .containsEntry(commander, 3);
        }
    }
}
