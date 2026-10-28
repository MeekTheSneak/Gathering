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
 * The behaviors an outside audit reproduced against this code, each now refused.
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
         * recognize it wherever it went.
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

    /**
     * The 2026-09-25 sweep: a card the asker cannot read is answered the same wherever it is.
     * <p>Two halves of one probe. The refusals named the zone - "in their hand", "in their
     * library" - and had their own wording for a face-down card, so a client walking the ids
     * sorted every card it could not see by where it was, and nothing was logged. And a ping or
     * a tap on somebody else's face-down card by a guessed id was accepted, so the public log
     * wrote that card's marker against the guess.
     */
    @Nested
    @DisplayName("a card the asker cannot read")
    class Unreadable {

        private static final SeatId ALICE = GameFixtures.ALICE;
        private static final SeatId BOB = GameFixtures.BOB;

        /** Bob with one card in hand, one face down on his side, and his library. */
        private record Bobs(GameSession session, CardInstanceId inHand, CardInstanceId inLibrary,
                CardInstanceId faceDown, CardInstanceId alicesOwn) {
        }

        private Bobs bobsTable() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.CardsDrawn(BOB, BOB, 2));
            List<CardInstanceId> hand = session.state().contents(BOB, Zone.HAND);
            CardInstanceId faceDown = hand.get(1);
            for (GameEvent event : dev.gathering.core.game.event.PlayingFaceDown.onto(
                    BOB, faceDown, ZoneRef.of(BOB, Zone.BATTLEFIELD), Placement.BOTTOM)) {
                assertThat(session.submit(event).isAccepted()).isTrue();
            }
            session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
            CardInstanceId alicesOwn = GameFixtures.firstInHand(session, ALICE);
            session.submit(new GameEvent.CardMoved(
                    ALICE, alicesOwn, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
            return new Bobs(session, hand.get(0), GameFixtures.topOfLibrary(session, BOB), faceDown, alicesOwn);
        }

        /** Every way of laying a hand on one card, from Alice's seat. */
        private List<GameEvent> everyTouch(CardInstanceId card, CardInstanceId alicesOwn) {
            return List.of(
                    new GameEvent.CardPinged(ALICE, card),
                    new GameEvent.CardTapSet(ALICE, card, true),
                    new GameEvent.CardRotated(ALICE, card, 90),
                    new GameEvent.CardNoted(ALICE, card, "this one"),
                    new GameEvent.CounterChanged(ALICE, card, "+1/+1", 1),
                    new GameEvent.CardFrozen(ALICE, card, true),
                    new GameEvent.CardStrengthSet(ALICE, card, "9/9"),
                    new GameEvent.CardTurnedOver(ALICE, card, true),
                    new GameEvent.CardFacingSet(ALICE, card, Facing.FACE_UP),
                    new GameEvent.CardFacingSet(ALICE, card, Facing.FACE_DOWN),
                    new GameEvent.TokenCopyCreated(ALICE, card, ALICE),
                    new GameEvent.CardMoved(ALICE, card, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.BOTTOM),
                    new GameEvent.CardAttached(ALICE, alicesOwn, card),
                    new GameEvent.CardAttached(ALICE, card, alicesOwn));
        }

        @Test
        @DisplayName("is refused in the same words in a hand, in a library and face down")
        void oneAnswerWhereverItIs() {
            Bobs bobs = bobsTable();
            java.util.Map<String, java.util.Set<String>> answers = new java.util.TreeMap<>();
            java.util.Map<String, CardInstanceId> where = new java.util.LinkedHashMap<>();
            where.put("in Bob's hand", bobs.inHand());
            where.put("in Bob's library", bobs.inLibrary());
            where.put("face down on Bob's side", bobs.faceDown());
            for (var place : where.entrySet()) {
                for (GameEvent event : everyTouch(place.getValue(), bobs.alicesOwn())) {
                    GameSession.Result result = bobs.session().submit(event);
                    String answer = result instanceof GameSession.Result.Rejected rejected
                            ? rejected.reason()
                            : "accepted";
                    answers.computeIfAbsent(answer, ignored -> new java.util.TreeSet<>())
                            .add(event.getClass().getSimpleName() + " " + place.getKey());
                }
            }

            assertThat(answers.keySet())
                    .describedAs("what Alice was told about Bob's cards, and for which: %s", answers)
                    .containsExactly(Authorization.NOT_YOURS);
        }

        @Test
        @DisplayName("is not tied to its marker by anything Alice can send")
        void aFaceDownCardIsNotTiedToItsMarker() {
            Bobs bobs = bobsTable();
            GameSession session = bobs.session();
            CardInstance before = session.state().requireCard(bobs.faceDown());
            MarkerId marker = before.markerId().orElseThrow();
            int logged = session.log().size();

            for (GameEvent event : everyTouch(bobs.faceDown(), bobs.alicesOwn())) {
                assertThat(session.submit(event))
                        .describedAs("%s naming Bob's face-down card", event.getClass().getSimpleName())
                        .isInstanceOf(GameSession.Result.Rejected.class);
            }

            assertThat(session.log().subList(logged, session.log().size()))
                    .describedAs("a line written against the id Alice guessed")
                    .noneMatch(entry -> entry.args().contains(
                            new dev.gathering.core.game.event.LogArg.Card(
                                    new dev.gathering.core.game.event.CardRef.ByMarker(marker))));
            assertThat(session.state().requireCard(bobs.faceDown())).isEqualTo(before);
        }

        /**
         * Nor in the hand of whoever holds it, where it can arrive face down: a stolen morph sent
         * back to hand with the rest of a battlefield. A note, a counter or a move within the hand
         * would answer a guessed id in the holder's own view of it; putting it down in the open is
         * the one thing that is theirs to do.
         */
        @Test
        @DisplayName("is not tied to its marker in the hand of whoever holds it either")
        void notInTheHoldersOwnHandEither() {
            Bobs bobs = bobsTable();
            GameSession session = bobs.session();
            CardInstanceId morph = bobs.faceDown();
            session.submit(new GameEvent.CardMoved(BOB, morph, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
            assertThat(session.submit(new GameEvent.ZoneMoved(
                    ALICE, ALICE, Zone.BATTLEFIELD, ZoneRef.of(ALICE, Zone.HAND), Placement.TOP)).isAccepted()).isTrue();
            assertThat(session.state().locationOf(morph)).contains(ZoneRef.of(ALICE, Zone.HAND));
            CardInstance before = session.state().requireCard(morph);
            List<CardInstanceId> handBefore = session.state().contents(ALICE, Zone.HAND);
            assertThat(before.isFaceDown()).describedAs("the fixture: the morph went to hand face down").isTrue();

            List<GameEvent> quiet = List.of(
                    new GameEvent.CardPinged(ALICE, morph),
                    new GameEvent.CardNoted(ALICE, morph, "this one"),
                    new GameEvent.CounterChanged(ALICE, morph, "+1/+1", 1),
                    new GameEvent.CardTapSet(ALICE, morph, true),
                    new GameEvent.CardFacingSet(ALICE, morph, Facing.FACE_UP),
                    new GameEvent.CardFacingSet(ALICE, morph, Facing.FACE_DOWN),
                    new GameEvent.TokenCopyCreated(ALICE, morph, ALICE),
                    new GameEvent.CardMoved(ALICE, morph, ZoneRef.of(ALICE, Zone.HAND), Placement.BOTTOM),
                    new GameEvent.CardMoved(ALICE, morph, ZoneRef.of(ALICE, Zone.LIBRARY), Placement.TOP));
            for (GameEvent event : quiet) {
                assertThat(session.submit(event))
                        .describedAs("%s naming Bob's face-down card in Alice's own hand", event)
                        .isInstanceOfSatisfying(GameSession.Result.Rejected.class, rejected ->
                                assertThat(rejected.reason()).isEqualTo(Authorization.NOT_YOURS));
            }
            assertThat(session.state().requireCard(morph)).isEqualTo(before);
            assertThat(session.state().contents(ALICE, Zone.HAND)).isEqualTo(handBefore);

            assertThat(session.submit(new GameEvent.CardMoved(
                    ALICE, morph, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.BOTTOM)).isAccepted())
                    .describedAs("putting it back down in the open, which is the holder's to do")
                    .isTrue();
        }

        @Test
        @DisplayName("is still the owner's to tap, and a card shown in your own hand is still yours to play face down")
        void theOwnerAndTheHolderStillCan() {
            Bobs bobs = bobsTable();
            GameSession session = bobs.session();

            assertThat(session.submit(new GameEvent.CardTapSet(BOB, bobs.faceDown(), true)).isAccepted()).isTrue();

            // A face-up card of Bob's, taken into Alice's hand and played face down from there: the
            // second move names a face-down card that is not hers, in her own hand.
            session.submit(new GameEvent.CardMoved(BOB, bobs.inHand(), ZoneRef.of(BOB, Zone.BATTLEFIELD),
                    Placement.BOTTOM));
            assertThat(session.submit(new GameEvent.CardMoved(ALICE, bobs.inHand(), ZoneRef.of(ALICE, Zone.HAND),
                    Placement.TOP)).isAccepted()).isTrue();
            for (GameEvent event : dev.gathering.core.game.event.PlayingFaceDown.onto(
                    ALICE, bobs.inHand(), ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.BOTTOM)) {
                assertThat(session.submit(event))
                        .describedAs("%s playing Bob's card face down from Alice's hand",
                                event.getClass().getSimpleName())
                        .matches(GameSession.Result::isAccepted);
            }
            assertThat(session.state().locationOf(bobs.inHand())).contains(ZoneRef.of(ALICE, Zone.BATTLEFIELD));
        }

        /**
         * A commander's tally is taken wherever the commander is, and says no more than the board.
         * <p>Every seat is shown the other seats' commanders by id, and records damage and casts
         * against them from the counters and life panels - so a tally refused in a hand and taken
         * face down told a client which one it was, and a tally on a face-down commander wrote the
         * marker that keeps it secret into the log beside the id everybody knows.
         */
        @Test
        @DisplayName("except a commander's tally, which is taken wherever it is and never names a marker")
        void aCommandersTallyIsTakenWherever() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            CardInstanceId commander = session.state().seatState(BOB).commanders().get(0);
            for (GameEvent event : dev.gathering.core.game.event.PlayingFaceDown.onto(
                    BOB, commander, ZoneRef.of(BOB, Zone.BATTLEFIELD), Placement.BOTTOM)) {
                assertThat(session.submit(event).isAccepted()).isTrue();
            }
            MarkerId marker = session.state().requireCard(commander).markerId().orElseThrow();
            int logged = session.log().size();

            java.util.List<GameSession.Result> results = new java.util.ArrayList<>();
            results.add(session.submit(new GameEvent.CommanderDamageChanged(ALICE, ALICE, commander, 3)));
            results.add(session.submit(new GameEvent.CommanderTaxChanged(ALICE, BOB, commander, 1)));
            session.submit(new GameEvent.CardMoved(BOB, commander, ZoneRef.of(BOB, Zone.HAND), Placement.TOP));
            results.add(session.submit(new GameEvent.CommanderDamageChanged(ALICE, ALICE, commander, 1)));
            results.add(session.submit(new GameEvent.CommanderTaxChanged(ALICE, BOB, commander, 1)));

            assertThat(results).describedAs("tallies on Bob's commander, face down and then in his hand")
                    .allMatch(GameSession.Result::isAccepted);
            assertThat(session.state().seatState(ALICE).commanderDamage()).containsEntry(commander, 4);
            assertThat(session.state().seatState(BOB).commanderTax()).containsEntry(commander, 2);
            assertThat(session.log().subList(logged, session.log().size()))
                    .describedAs("a tally line naming the face-down commander's marker")
                    .noneMatch(entry -> entry.args().contains(
                            new dev.gathering.core.game.event.LogArg.Card(
                                    new dev.gathering.core.game.event.CardRef.ByMarker(marker))));
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
