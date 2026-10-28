package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameSessionTest {

    @Nested
    @DisplayName("the fold")
    class TheFold {

        @Test
        @DisplayName("a deck arrives as a library in decklist order and a commander in the command zone")
        void decksLoad() {
            GameSession session = GameFixtures.twoPlayerTable(99);
            GameState state = session.state();

            assertThat(state.count(ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY))).isEqualTo(99);
            assertThat(state.count(ZoneRef.of(GameFixtures.ALICE, Zone.COMMAND))).isEqualTo(1);
            assertThat(state.count(ZoneRef.of(GameFixtures.ALICE, Zone.HAND))).isZero();
        }

        @Test
        void drawingMovesFromTheTopOfTheLibraryIntoTheHand() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId top = GameFixtures.topOfLibrary(session, GameFixtures.ALICE);

            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.HAND)).containsExactly(top);
            assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY))).isEqualTo(39);
        }

        @Test
        @DisplayName("a stale event is refused, not written down - the log must always fold")
        void aStaleEventCannotPoisonTheLog() {
            // The lagged-click shape: one player taps a token in the same breath as its
            // owner removes it. By the time the tap arrives the card is gone. The submit
            // used to append the record first and fold second, so the fold's refusal left
            // a standing record nothing could ever fold again - undo broke, restore threw
            // out of the block entity's ticker on every launch, and the save carried it.
            GameSession session = GameFixtures.twoPlayerTable(10);
            int recordsBefore = session.records().size();

            GameSession.Result result = session.submit(new GameEvent.CardTapSet(
                    GameFixtures.ALICE, new CardInstanceId(999_999), true));

            assertThat(result.isAccepted()).isFalse();
            assertThat(session.records()).hasSize(recordsBefore);
            // And the whole log still folds, which is the property the order exists for.
            assertThat(session.refold()).isNotNull();
        }

        @Test
        @DisplayName("drawing from an empty library draws nothing rather than failing")
        void drawingFromAnEmptyLibraryIsNotAnError() {
            GameSession session = GameFixtures.twoPlayerTable(2);

            GameSession.Result result = session.submit(
                    new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 10));

            assertThat(result.isAccepted()).isTrue();
            assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.HAND))).isEqualTo(2);
            assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY))).isZero();
        }

        @Test
        @DisplayName("ownership survives a card ending up on somebody else's side of the table")
        void stealingChangesPositionAndNotOwnership() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 1));
            CardInstanceId bobsCard = GameFixtures.firstInHand(session, GameFixtures.BOB);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.BOB, bobsCard, ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.BOTTOM));

            session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, bobsCard, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD)).contains(bobsCard);
            assertThat(session.state().requireCard(bobsCard).owner()).isEqualTo(GameFixtures.BOB);
        }

        @Test
        @DisplayName("the mod never says no to a misplay: tapping twice, negative life, negative counters")
        void misplaysAreAllowed() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, card, true)).isAccepted()).isTrue();
            assertThat(session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, card, true)).isAccepted()).isTrue();
            assertThat(session.submit(
                    new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -99)).isAccepted()).isTrue();
            assertThat(session.submit(
                    new GameEvent.CounterChanged(GameFixtures.ALICE, card, "loyalty", -5)).isAccepted()).isTrue();

            assertThat(session.state().seatState(GameFixtures.ALICE).life()).isEqualTo(-59);
            assertThat(session.state().requireCard(card).counter("loyalty")).isEqualTo(-5);
        }

        @Test
        void untapAllOnlyTouchesThatSeatsBattlefield() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 2));
            List<CardInstanceId> hand = List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.HAND));
            for (CardInstanceId card : hand) {
                session.submit(new GameEvent.CardMoved(
                        GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
                session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, card, true));
            }

            session.submit(new GameEvent.SeatUntappedAll(GameFixtures.ALICE, GameFixtures.ALICE));

            assertThat(hand).allSatisfy(card ->
                    assertThat(session.state().requireCard(card).tapped()).isFalse());
        }

        @Test
        @DisplayName("a scry puts the kept cards on top in the chosen order and the rest on the bottom")
        void scryReorders() {
            GameSession session = GameFixtures.twoPlayerTable(10);
            List<CardInstanceId> library = List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY));
            CardInstanceId first = library.get(0);
            CardInstanceId second = library.get(1);

            session.submit(new GameEvent.LibraryReordered(
                    GameFixtures.ALICE, GameFixtures.ALICE, List.of(second), List.of(first)));

            List<CardInstanceId> after = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
            assertThat(after.get(0)).isEqualTo(second);
            assertThat(after.get(after.size() - 1)).isEqualTo(first);
            assertThat(after).hasSize(10);
        }

        @Test
        void surveilSendsTheChosenCardsToTheGraveyard() {
            GameSession session = GameFixtures.twoPlayerTable(10);
            List<CardInstanceId> library = List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY));

            session.submit(new GameEvent.Surveiled(
                    GameFixtures.ALICE, GameFixtures.ALICE, List.of(library.get(1)), List.of(library.get(0))));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.GRAVEYARD))
                    .containsExactly(library.get(0));
            assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(0))
                    .isEqualTo(library.get(1));
        }

        @Test
        void tokensAreCreatedAndCanCeaseToExist() {
            GameSession session = GameFixtures.twoPlayerTable(10);

            session.submit(new GameEvent.TokenCreated(
                    GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(500), 2));

            List<CardInstanceId> battlefield =
                    List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD));
            assertThat(battlefield).hasSize(2);
            assertThat(session.state().requireCard(battlefield.get(0)).token()).isTrue();

            session.submit(new GameEvent.TokenRemoved(GameFixtures.ALICE, battlefield.get(0)));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD)).hasSize(1);
            assertThat(session.state().card(battlefield.get(0))).isEmpty();
        }
    }

    @Nested
    @DisplayName("shuffling")
    class Shuffling {

        @Test
        @DisplayName("the same seed and the same log produce the same shuffle, which is what replay needs")
        void shufflesAreDeterministic() {
            List<CardInstanceId> first = shuffleOnce(GameFixtures.FIXED_SEED);
            List<CardInstanceId> second = shuffleOnce(GameFixtures.FIXED_SEED);

            assertThat(first).isEqualTo(second);
        }

        @Test
        void differentSeedsProduceDifferentShuffles() {
            SessionSeed other = SessionSeed.fromBytes(
                    "an-entirely-different-seed-for-this-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThat(shuffleOnce(GameFixtures.FIXED_SEED)).isNotEqualTo(shuffleOnce(other));
        }

        @Test
        @DisplayName("two shuffles in one session differ, so shuffling twice is not a no-op")
        void consecutiveShufflesDiffer() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
            List<CardInstanceId> afterFirst = List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY));
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)).isNotEqualTo(afterFirst);
        }

        @Test
        void shufflingKeepsEveryCardExactlyOnce() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            Set<CardInstanceId> before = Set.copyOf(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY));

            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));

            List<CardInstanceId> after = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
            assertThat(after).hasSize(40).doesNotHaveDuplicates();
            assertThat(Set.copyOf(after)).isEqualTo(before);
        }

        @Test
        @DisplayName("a mulligan returns the hand, shuffles, and draws a smaller one")
        void mulliganDoesAllThree() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 7));

            session.submit(new GameEvent.Mulliganed(GameFixtures.ALICE, GameFixtures.ALICE, 6));

            assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.HAND))).isEqualTo(6);
            assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY))).isEqualTo(34);
        }

        private static List<CardInstanceId> shuffleOnce(SessionSeed seed) {
            GameSession session = GameSession.create(
                    List.of(GameFixtures.ALICE), 40, seed, UndoMode.shippedDefault());
            session.submit(new GameEvent.DeckLoaded(GameFixtures.ALICE, GameFixtures.deck(40), List.of()));
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
            return List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY));
        }
    }

    @Nested
    @DisplayName("authorization")
    class Authorizing {

        @Test
        @DisplayName("any seat may move any card in a public zone, because that is paper Magic")
        void publicZonesAreOpenToEverySeat() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 1));
            CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.BOB);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.BOB, card, ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.BOTTOM));

            GameSession.Result result = session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, card, true));

            assertThat(result.isAccepted()).isTrue();
        }

        @Test
        @DisplayName("nobody reaches into somebody else's hand")
        void hiddenZonesAreOwnerLocked() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 1));
            CardInstanceId inBobsHand = GameFixtures.firstInHand(session, GameFixtures.BOB);

            GameSession.Result result = session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, inBobsHand, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(result).isInstanceOfSatisfying(GameSession.Result.Rejected.class, rejected ->
                    assertThat(rejected.reason()).isEqualTo(Authorization.NOT_YOURS));
        }

        @Test
        @DisplayName("searching a library is looking, so only its owner may")
        void searchingIsOwnerLocked() {
            GameSession session = GameFixtures.twoPlayerTable(40);

            assertThat(session.submit(
                    new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.BOB)).isAccepted()).isFalse();
            assertThat(session.submit(
                    new GameEvent.LibrarySearched(GameFixtures.BOB, GameFixtures.BOB)).isAccepted()).isTrue();
        }

        @Test
        @DisplayName("making an opponent draw or shuffle is legal Magic and reveals nothing, so it is allowed")
        void actionsThatRevealNothingToTheActorAreAllowed() {
            GameSession session = GameFixtures.twoPlayerTable(40);

            assertThat(session.submit(
                    new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.BOB, 1)).isAccepted()).isTrue();
            assertThat(session.submit(
                    new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.BOB)).isAccepted()).isTrue();
            assertThat(session.state().count(ZoneRef.of(GameFixtures.BOB, Zone.HAND))).isEqualTo(1);
        }

        @Test
        @DisplayName("putting a card into somebody's hand is fine; taking one out is not")
        void movingIntoAHiddenZoneIsAllowed() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            GameSession.Result result = session.submit(new GameEvent.CardMoved(
                    GameFixtures.BOB, card, ZoneRef.of(GameFixtures.BOB, Zone.HAND), Placement.BOTTOM));

            assertThat(result.isAccepted()).isTrue();
        }

        @Test
        void anUnseatedActorCanDoNothingAtAll() {
            GameSession session = GameFixtures.twoPlayerTable(40);

            GameSession.Result result = session.submit(
                    new GameEvent.LifeChanged(SeatId.of(7), GameFixtures.ALICE, -1));

            assertThat(result).isInstanceOfSatisfying(GameSession.Result.Rejected.class, rejected ->
                    assertThat(rejected.reason()).contains("seated"));
        }

        @Test
        void nothingIsAcceptedAfterTheSessionEnds() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.SessionEnded(GameFixtures.ALICE, "done"));

            assertThat(session.submit(
                    new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -1)).isAccepted()).isFalse();
        }
    }

    @Nested
    @DisplayName("undo")
    class Undoing {

        @Test
        @DisplayName("a player rewinds their own last action instantly in the shipped default")
        void freeUndoOfYourOwnAction() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -3));
            assertThat(session.state().seatState(GameFixtures.ALICE).life()).isEqualTo(37);

            GameSession.Result result = session.undo(GameFixtures.ALICE, 1, List.of());

            assertThat(result.isAccepted()).isTrue();
            assertThat(session.state().seatState(GameFixtures.ALICE).life()).isEqualTo(40);
        }

        @Test
        @DisplayName("undoing somebody else's action needs everyone to agree, even in free mode")
        void undoingAnotherPlayersActionEscalates() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.LifeChanged(GameFixtures.BOB, GameFixtures.BOB, -3));

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1)).isInstanceOf(
                    UndoDecision.NeedsUnanimousConsent.class);
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isFalse();
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB))
                    .isAccepted()).isTrue();
        }

        @Test
        @DisplayName("no undo mode rewinds past a revealed card, because a seen card cannot be un-seen")
        void informationBoundariesAreHardInEveryMode() {
            // Every mode, as the name says, and the door as well as the advice. This used to
            // build one table in the shipped default and ask only evaluateUndo, so a mode that
            // skipped the boundary - or an undo() that ignored its own advice - passed.
            for (UndoMode mode : UndoMode.values()) {
                GameSession session = GameSession.create(List.of(GameFixtures.ALICE, GameFixtures.BOB),
                        40, GameFixtures.FIXED_SEED, mode);
                session.submit(new GameEvent.SeatTaken(GameFixtures.ALICE,
                        new PlayerRef(java.util.UUID.randomUUID(), "Alice")));
                session.submit(new GameEvent.SeatTaken(GameFixtures.BOB,
                        new PlayerRef(java.util.UUID.randomUUID(), "Bob")));
                session.submit(new GameEvent.DeckLoaded(GameFixtures.ALICE, GameFixtures.deck(40), List.of()));
                session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));

                if (mode != UndoMode.OFF) {
                    assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                            .as("in %s", mode)
                            .isInstanceOfSatisfying(UndoDecision.NeedsUnanimousConsent.class, needed ->
                                    assertThat(needed.reason()).contains("revealed information"));
                }
                assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted())
                        .as("one player alone rewound a draw in %s", mode)
                        .isFalse();
                assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.HAND)))
                        .as("the drawn card went back in %s", mode)
                        .isEqualTo(1);
            }
        }

        @Test
        @DisplayName("copying your own face-down card shows the table what it was, so that is a boundary too")
        void copyingAFaceDownCardIsAReveal() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId morph = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, morph, Facing.FACE_DOWN));
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, morph,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            session.submit(new GameEvent.TokenCopyCreated(GameFixtures.ALICE, morph, GameFixtures.ALICE));

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                    .isInstanceOfSatisfying(UndoDecision.NeedsUnanimousConsent.class, needed ->
                            assertThat(needed.reason()).contains("revealed information"));
        }

        @Test
        @DisplayName("handing a card out of your hand into somebody else's shows it to them, so that is a boundary")
        void aCardPushedIntoAnotherSeatsHandIsAReveal() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId held = GameFixtures.firstInHand(session, GameFixtures.ALICE);

            assertThat(session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, held,
                    ZoneRef.of(GameFixtures.BOB, Zone.HAND), Placement.TOP)).isAccepted()).isTrue();

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                    .isInstanceOfSatisfying(UndoDecision.NeedsUnanimousConsent.class, needed ->
                            assertThat(needed.reason()).contains("revealed information"));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isFalse();
        }

        @Test
        @DisplayName("a whole hand emptied into somebody else's is the same boundary, in one event")
        void aHandEmptiedIntoAnotherSeatsHandIsAReveal() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 5));

            assertThat(session.submit(new GameEvent.ZoneMoved(GameFixtures.ALICE, GameFixtures.ALICE,
                    Zone.HAND, ZoneRef.of(GameFixtures.BOB, Zone.HAND), Placement.TOP))
                    .isAccepted()).isTrue();

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                    .isInstanceOfSatisfying(UndoDecision.NeedsUnanimousConsent.class, needed ->
                            assertThat(needed.reason()).contains("revealed information"));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isFalse();
        }

        @Test
        @DisplayName("moving a card about within your own hand shows nobody anything, so it stays freely undoable")
        void aCardMovedWithinYourOwnHandIsNoBoundary() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 2));
            CardInstanceId held = GameFixtures.firstInHand(session, GameFixtures.ALICE);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, held,
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));

            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isTrue();
        }

        /**
         * What the table does when a deck goes down, and the one press of Undo after it.
         * <p>The server writes the deck and then the shuffle, both under the player's own seat. Taking
         * the shuffle back alone left the library in the order of the decklist its owner typed - and
         * drawing seven then drew exactly the first seven lines of it.
         */
        @Test
        @DisplayName("the shuffle a deck goes down with is not taken back by one player alone")
        void theShuffleADeckGoesDownWithStays() {
            GameSession session = GameSession.create(List.of(GameFixtures.ALICE, GameFixtures.BOB),
                    40, GameFixtures.FIXED_SEED, UndoMode.shippedDefault());
            session.submit(new GameEvent.SeatTaken(GameFixtures.ALICE, new PlayerRef(UUID.randomUUID(), "Alice")));
            session.submit(new GameEvent.SeatTaken(GameFixtures.BOB, new PlayerRef(UUID.randomUUID(), "Bob")));
            session.submit(new GameEvent.DeckLoaded(GameFixtures.ALICE, GameFixtures.deck(40), List.of()));
            List<CardInstanceId> asTyped = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
            List<CardInstanceId> shuffled = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
            assertThat(shuffled).as("the fixture shuffled nothing").isNotEqualTo(asTyped);

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1).isAllowed()).isFalse();
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted())
                    .as("one press of Undo put the library back in decklist order")
                    .isFalse();
            assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)).isEqualTo(shuffled);
        }

        /**
         * A fetch: look through the library, take the card, shut it, shuffle. Taking back only the
         * shuffle left the library in exactly the order the search had just shown.
         */
        @Test
        @DisplayName("a shuffle after a search is a boundary, and everyone agreeing still crosses it")
        void aShuffleAfterASearchStays() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));
            CardInstanceId fetched = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(17);
            assertThat(session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, fetched,
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM)).isAccepted()).isTrue();
            session.submit(new GameEvent.LibraryClosed(GameFixtures.ALICE));
            List<CardInstanceId> asSearched = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
            List<CardInstanceId> shuffled = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                    .isInstanceOfSatisfying(UndoDecision.NeedsUnanimousConsent.class, needed ->
                            assertThat(needed.reason()).contains("shuffle"));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted())
                    .as("one player rewound a shuffle to the order their search had shown them")
                    .isFalse();
            assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)).isEqualTo(shuffled);

            assertThat(session.undo(GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB))
                    .isAccepted()).isTrue();
            assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)).isEqualTo(asSearched);
        }

        /**
         * Putting a deck down is the table's to undo, not a player's. The table holds the deck item
         * and a rewind cannot hand it back, so two presses of Undo emptied the seat - no library, no
         * commander - while the table kept the deck and refused every deck put down there after.
         */
        @Test
        @DisplayName("a deck put down stays down, however many agree to take it back")
        void aDeckPutDownStaysDown() {
            GameSession session = GameSession.create(List.of(GameFixtures.ALICE, GameFixtures.BOB),
                    40, GameFixtures.FIXED_SEED, UndoMode.shippedDefault());
            session.submit(new GameEvent.SeatTaken(GameFixtures.ALICE, new PlayerRef(UUID.randomUUID(), "Alice")));
            session.submit(new GameEvent.SeatTaken(GameFixtures.BOB, new PlayerRef(UUID.randomUUID(), "Bob")));
            session.submit(new GameEvent.DeckLoaded(GameFixtures.ALICE, GameFixtures.deck(60),
                    List.of(GameFixtures.card(900))));
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
            List<CardInstanceId> library = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);

            for (int reach = 1; reach <= 2; reach++) {
                assertThat(session.evaluateUndo(GameFixtures.ALICE, reach))
                        .as("reaching back %s", reach)
                        .isInstanceOfSatisfying(UndoDecision.Denied.class, denied ->
                                assertThat(denied.reason()).isEqualTo(GameSession.NOT_REWOUND_DECK));
                assertThat(session.undo(GameFixtures.ALICE, reach, List.of()).isAccepted()).isFalse();
                assertThat(session.undo(GameFixtures.ALICE, reach, List.of(GameFixtures.ALICE, GameFixtures.BOB))
                        .isAccepted())
                        .as("everybody agreeing to take back %s", reach)
                        .isFalse();
            }
            assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)).isEqualTo(library);
            assertThat(session.state().seatState(GameFixtures.ALICE).commanders()).hasSize(1);
            assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.COMMAND))).isEqualTo(1);
        }

        @Test
        @DisplayName("a shuffle later in the game is the player's own, so agreeing still takes it back")
        void aLaterShuffleIsNotTheDeckGoingDown() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -1));
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1)).isInstanceOf(UndoDecision.NeedsUnanimousConsent.class);
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB))
                    .isAccepted()).isTrue();
        }

        /**
         * Sitting down is written under the seat sat in, so it was that player's own action - and
         * Undo straight after sitting down at a running game emptied the seat in the game while the
         * chair stayed claimed, and every rewind after it answered "Only seated players can rewind."
         */
        @Test
        @DisplayName("sitting down and standing up are the table's, so a rewind stops at them")
        void sittingDownAndStandingUpAreNotRewound() {
            GameSession session = GameSession.create(List.of(GameFixtures.ALICE, GameFixtures.BOB),
                    40, GameFixtures.FIXED_SEED, UndoMode.shippedDefault());
            session.submit(new GameEvent.SeatTaken(GameFixtures.BOB, new PlayerRef(UUID.randomUUID(), "Bob")));
            session.submit(new GameEvent.SeatTaken(GameFixtures.ALICE, new PlayerRef(UUID.randomUUID(), "Alice")));

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                    .isInstanceOfSatisfying(UndoDecision.Denied.class, denied ->
                            assertThat(denied.reason()).isEqualTo(GameSession.NOT_REWOUND_SEAT));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isFalse();
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB))
                    .isAccepted()).isFalse();
            assertThat(session.state().seatState(GameFixtures.ALICE).isOccupied()).isTrue();

            // Nor is somebody who got up put back in the chair by the people still sitting.
            session.submit(new GameEvent.SeatReleased(GameFixtures.BOB));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE)).isAccepted())
                    .as("everybody still sitting agreed to put Bob back in the chair he left")
                    .isFalse();
            assertThat(session.state().seatState(GameFixtures.BOB).isOccupied()).isFalse();
        }

        @Test
        @DisplayName("who goes first is the server's choice, and the player it chose cannot take it back")
        void whoGoesFirstIsNotRewound() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.StartingPlayerChosen(GameFixtures.BOB,
                    GameEvent.StartingPlayerChosen.Why.RANDOM));

            assertThat(session.evaluateUndo(GameFixtures.BOB, 1))
                    .isInstanceOfSatisfying(UndoDecision.Denied.class, denied ->
                            assertThat(denied.reason()).isEqualTo(GameSession.NOT_REWOUND_FIRST_PLAYER));
            assertThat(session.undo(GameFixtures.BOB, 1, List.of()).isAccepted()).isFalse();
            assertThat(session.state().turn().activeSeat()).isEqualTo(GameFixtures.BOB);
        }

        @Test
        @DisplayName("a die is still taken back: undo takes back the asking, and the board never moved")
        void aDieRollIsStillTakenBack() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.DiceRolled(GameFixtures.ALICE, 20, 7));

            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isTrue();
        }

        @Test
        @DisplayName("a player's own shuffle is the same boundary in every mode, however far the rewind reaches")
        void aShuffleIsABoundaryInEveryMode() {
            for (UndoMode mode : UndoMode.values()) {
                GameSession session = GameFixtures.twoPlayerTable(40);
                session.setUndoMode(mode);
                session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -1));
                session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
                session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -1));
                List<CardInstanceId> shuffled = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);

                assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted())
                        .as("the life change after it in %s", mode)
                        .isEqualTo(mode == UndoMode.FREE);
                assertThat(session.undo(GameFixtures.ALICE, 2, List.of()).isAccepted())
                        .as("one player rewound across their own shuffle in %s", mode)
                        .isFalse();
                assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)).isEqualTo(shuffled);
            }
        }

        /**
         * Holding your own library and letting go over your hand moves it there whole, in order -
         * and the hand is open to its owner. That was no boundary, because the cards stayed with
         * one seat; but a library is count-only even to its owner, so the player read every card in
         * it top to bottom and took the move back alone, the library returning in the order read.
         */
        @Test
        @DisplayName("a library emptied into its owner's hand is read in order, so that is a boundary")
        void aLibraryEmptiedIntoItsOwnersHandIsAReveal() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            List<CardInstanceId> order = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);

            assertThat(session.submit(new GameEvent.ZoneMoved(GameFixtures.ALICE, GameFixtures.ALICE,
                    Zone.LIBRARY, ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM))
                    .isAccepted()).isTrue();

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                    .isInstanceOfSatisfying(UndoDecision.NeedsUnanimousConsent.class, needed ->
                            assertThat(needed.reason()).contains("revealed information"));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted())
                    .as("one player rewound a library they had just read in order")
                    .isFalse();
            assertThat(session.state().contents(GameFixtures.ALICE, Zone.HAND)).isEqualTo(order);
        }

        @Test
        @DisplayName("one unread card taken out of your own library into your hand is read too")
        void anUnreadLibraryCardTakenIntoTheHandIsAReveal() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            // Only a modified client can name it - an honest one is never sent the id - but the
            // rule is about what was seen, not about how the card was named.
            CardInstanceId fifth = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(4);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, fifth,
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1)).isInstanceOf(UndoDecision.NeedsUnanimousConsent.class);
        }

        /**
         * What the player already had open is not read again. Taking one of the cards a look showed
         * stays a misclick anybody may take back; a card below the look does not.
         */
        @Test
        @DisplayName("a card taken from what a look or a search already showed stays freely undoable")
        void whatWasAlreadyOpenIsNotReadAgain() {
            GameSession looked = GameFixtures.twoPlayerTable(40);
            looked.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 3));
            CardInstanceId second = looked.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(1);
            looked.submit(new GameEvent.CardMoved(GameFixtures.ALICE, second,
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));
            assertThat(looked.evaluateUndo(GameFixtures.ALICE, 1).isAllowed())
                    .as("the second of three looked at").isTrue();

            GameSession below = GameFixtures.twoPlayerTable(40);
            below.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 3));
            CardInstanceId fifth = below.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(4);
            below.submit(new GameEvent.CardMoved(GameFixtures.ALICE, fifth,
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));
            assertThat(below.evaluateUndo(GameFixtures.ALICE, 1).isAllowed())
                    .as("the fifth, below a look at three").isFalse();

            GameSession searched = GameFixtures.twoPlayerTable(40);
            searched.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));
            CardInstanceId deep = searched.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(30);
            searched.submit(new GameEvent.CardMoved(GameFixtures.ALICE, deep,
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));
            assertThat(searched.undo(GameFixtures.ALICE, 1, List.of()).isAccepted())
                    .as("a card taken during a search").isTrue();

            GameSession emptied = GameFixtures.twoPlayerTable(40);
            emptied.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));
            emptied.submit(new GameEvent.ZoneMoved(GameFixtures.ALICE, GameFixtures.ALICE,
                    Zone.LIBRARY, ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));
            assertThat(emptied.evaluateUndo(GameFixtures.ALICE, 1).isAllowed())
                    .as("a library emptied into the hand while it was being searched").isTrue();
        }

        @Test
        @DisplayName("a card going back into its owner's library, or along it, shows nobody anything")
        void intoAndAlongYourOwnLibraryIsNoBoundary() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId held = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, held,
                    ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY), Placement.TOP));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).as("hand to library").isTrue();

            CardInstanceId top = GameFixtures.topOfLibrary(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, top,
                    ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY), Placement.BOTTOM));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).as("top to bottom").isTrue();
        }

        @Test
        @DisplayName("with everyone's consent a rewind may cross an information boundary")
        void unanimousConsentCrossesTheBoundary() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));

            GameSession.Result result = session.undo(
                    GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB));

            assertThat(result.isAccepted()).isTrue();
            assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.HAND))).isZero();
        }

        @Test
        void undoOffRefusesEverything() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.setUndoMode(UndoMode.OFF);
            session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -3));

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1)).isInstanceOf(UndoDecision.Denied.class);
            assertThat(session.undo(GameFixtures.ALICE, 1,
                    List.of(GameFixtures.ALICE, GameFixtures.BOB)).isAccepted()).isFalse();
        }

        /**
         * A rewound token's id is not handed to the next card made. The counter new cards are
         * numbered from is part of the board, and a rewind folds the board again - so the next token
         * was given the rewound one's id, and a menu still open on it, a selection, or a removal
         * already on its way landed on the new card.
         */
        @Test
        @DisplayName("a card's id is never handed to another card, even after the card is rewound")
        void aRewoundTokensIdIsNotHandedOutAgain() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.TokenCreated(GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(9), 1));
            CardInstanceId soldier = session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD).get(0);
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isTrue();

            session.submit(new GameEvent.TokenCreated(GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(10), 1));
            CardInstanceId angel = session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD).get(0);

            assertThat(angel).isNotEqualTo(soldier);
            assertThat(session.submit(new GameEvent.TokenRemoved(GameFixtures.BOB, soldier)))
                    .as("a removal sent at the rewound token")
                    .isInstanceOf(GameSession.Result.Rejected.class);
            assertThat(session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD)).containsExactly(angel);

            // Every walk over the log numbers them the same: a restore, a refold, a rewind's own walk.
            GameSession restored = GameSession.restore(List.of(GameFixtures.ALICE, GameFixtures.BOB), 40,
                    GameFixtures.FIXED_SEED, UndoMode.shippedDefault(), session.records());
            assertThat(restored.state()).isEqualTo(session.state());
            assertThat(session.refold()).isEqualTo(session.state());
            session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, angel, true));
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isTrue();
            assertThat(session.state().card(angel)).isPresent();
            assertThat(session.refold()).isEqualTo(session.state());
        }

        /**
         * A shuffle after an agreed rewind is a fresh shuffle. The count of shuffles is what each
         * permutation is drawn from, and a rewind took it back too: shuffle, draw, rewind both with
         * everybody's agreement, shuffle again - and the library came out in the same order, the card
         * just seen back on top for a player who now knew it was there.
         */
        @Test
        @DisplayName("a shuffle after a rewind is not the shuffle that was rewound")
        void aShuffleAfterARewindIsAFreshOne() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
            List<CardInstanceId> first = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            assertThat(session.undo(GameFixtures.ALICE, 2, List.of(GameFixtures.ALICE, GameFixtures.BOB))
                    .isAccepted()).isTrue();

            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY))
                    .as("the library shuffled again after the rewind came out exactly as the shuffle taken back")
                    .isNotEqualTo(first);
            assertThat(session.refold()).isEqualTo(session.state());
        }

        /**
         * A card turned face down after a rewound one gets a marker of its own. A fresh marker each
         * time is what stops two face-down periods being read as one card, and turning a card face down
         * is freely undone - so the next card turned down was given the rewound one's marker.
         */
        @Test
        @DisplayName("a face-down marker is never handed to a second card, even after a rewind")
        void aRewoundMarkerIsNotHandedOutAgain() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 2));
            List<CardInstanceId> hand = session.state().contents(GameFixtures.ALICE, Zone.HAND);
            for (CardInstanceId card : hand) {
                session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                        ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
            }
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, hand.get(0), Facing.FACE_DOWN));
            MarkerId rewound = session.state().requireCard(hand.get(0)).markerId().orElseThrow();
            assertThat(session.undo(GameFixtures.ALICE, 1, List.of()).isAccepted()).isTrue();

            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, hand.get(1), Facing.FACE_DOWN));

            assertThat(session.state().requireCard(hand.get(1)).markerId()).isPresent().get().isNotEqualTo(rewound);
            assertThat(session.refold()).isEqualTo(session.state());
        }

        /**
         * A log written before rewinds kept their ids, read back: its rewind kept none, its second
         * token has the id the first one had, and what came after names that id. It folds to exactly
         * that, and nothing in it is a card the session does not have.
         */
        @Test
        @DisplayName("a log written before rewinds kept their ids folds to the ids it always had")
        void aLogFromBeforeKeepsItsIds() {
            SeatId alice = GameFixtures.ALICE;
            CardInstanceId reissued = CardInstanceId.of(10);
            List<SessionRecord> older = List.of(
                    new SessionRecord.EventRecord(1, new GameEvent.SeatTaken(alice, new PlayerRef(UUID.randomUUID(), "Alice")), false),
                    new SessionRecord.EventRecord(2, new GameEvent.SeatTaken(GameFixtures.BOB,
                            new PlayerRef(UUID.randomUUID(), "Bob")), false),
                    new SessionRecord.EventRecord(3, new GameEvent.DeckLoaded(alice, GameFixtures.deck(10), List.of()), false),
                    new SessionRecord.EventRecord(4, new GameEvent.TokenCreated(alice, alice, GameFixtures.card(90), 1), true),
                    new SessionRecord.UndoRecord(5, alice, 1, false),
                    new SessionRecord.EventRecord(6, new GameEvent.TokenCreated(alice, alice, GameFixtures.card(91), 1), false),
                    new SessionRecord.EventRecord(7, new GameEvent.CardTapSet(alice, reissued, true), false));

            GameSession restored = GameSession.restore(List.of(alice, GameFixtures.BOB), 40,
                    GameFixtures.FIXED_SEED, UndoMode.shippedDefault(), older);

            assertThat(restored.state().requireCard(reissued).identity()).isEqualTo(GameFixtures.card(91));
            assertThat(restored.state().requireCard(reissued).tapped()).isTrue();
            assertThat(restored.refold()).isEqualTo(restored.state());
        }

        @Test
        @DisplayName("nothing disappears from the record: an undone action stays, marked undone")
        void theLogIsAppendOnly() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            int before = session.records().size();
            session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -3));

            session.undo(GameFixtures.ALICE, 1, List.of());

            List<SessionRecord> records = session.records();
            assertThat(records).hasSize(before + 2);
            assertThat(records.get(before)).isInstanceOfSatisfying(SessionRecord.EventRecord.class, record ->
                    assertThat(record.undone()).isTrue());
            assertThat(records.get(before + 1)).isInstanceOf(SessionRecord.UndoRecord.class);
        }

        @Test
        @DisplayName("the incremental board always agrees with a fresh fold of the log")
        void stateAlwaysMatchesARefold() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 7));
            session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -3));
            session.undo(GameFixtures.ALICE, 1, List.of());
            session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.BOB, -5));

            assertThat(session.state()).isEqualTo(session.refold());
        }
    }

    @Test
    @DisplayName("a game's record stops growing long before it could no longer be read back, and can still be ended")
    void aRecordFullOfMovesStopsTakingMoves() throws Exception {
        GameSession session = GameSession.sandbox(40);
        SeatId solo = SeatId.of(0);
        session.submit(new GameEvent.SeatTaken(solo, new PlayerRef(UUID.randomUUID(), "Chris")));
        while (session.records().size() < GameSession.MOST_RECORDS) {
            session.submit(new GameEvent.LifeChanged(solo, solo, 1));
        }
        assertThat(session.submit(new GameEvent.LifeChanged(solo, solo, 1)))
                .isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.undo(solo, 1, List.of())).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.submit(new GameEvent.SessionEnded(solo, "full")))
                .isInstanceOf(GameSession.Result.Accepted.class);
        assertThat(session.records().size()).isLessThanOrEqualTo(dev.gathering.core.game.persistence.SessionCodec.MAX_LIST);
    }

    @Test
    @DisplayName("a solo table needs no other humans, which is what goldfishing on a weeknight requires")
    void sandboxSessionsWork() {
        GameSession session = GameSession.sandbox(40);
        SeatId solo = SeatId.of(0);
        session.submit(new GameEvent.SeatTaken(solo, new PlayerRef(UUID.randomUUID(), "Chris")));
        session.submit(new GameEvent.DeckLoaded(solo, GameFixtures.deck(99), List.of(GameFixtures.card(900))));
        session.submit(new GameEvent.LibraryShuffled(solo, solo));
        session.submit(new GameEvent.CardsDrawn(solo, solo, 7));

        assertThat(session.state().count(ZoneRef.of(solo, Zone.HAND))).isEqualTo(7);
        assertThat(session.state().count(ZoneRef.of(solo, Zone.COMMAND))).isEqualTo(1);
    }
}
