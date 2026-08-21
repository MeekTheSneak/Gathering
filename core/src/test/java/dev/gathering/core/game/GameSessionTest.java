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
                    assertThat(rejected.reason()).contains("Only the owner"));
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
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));

            assertThat(session.evaluateUndo(GameFixtures.ALICE, 1))
                    .isInstanceOfSatisfying(UndoDecision.NeedsUnanimousConsent.class, needed ->
                            assertThat(needed.reason()).contains("revealed information"));
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
