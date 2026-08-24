package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.ZoneView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The one security property of the mod, stated once and tested forever.
 *
 * <blockquote>No payload containing hidden card identity is ever addressed to a client that
 * the visibility table does not entitle.</blockquote>
 *
 * <p>This is the test suite that must never regress. It asserts on the actual view objects
 * that go on the wire, not on the rules that build them, because a client's entitlement is a
 * property of what it receives.
 */
class VisibilityInvariantTest {

    /**
     * Every zone reaches every viewer, even the ones they may only count.
     *
     * <p>Three screens ask a seat view for a zone by name and take the game down if it is not
     * there - which is fine while every zone is always built, and is the assumption this
     * checks. It is also what makes adding a zone additive: the fold lays out one of each per
     * seat from the enum, and the visibility rules walk the enum too, so a constant added
     * tomorrow arrives at every client without anybody remembering to list it.
     */
    @Test
    @DisplayName("a seat view has one of every zone, for its owner and for everyone else")
    void everyZoneReachesEveryViewer() {
        GameSession session = GameFixtures.twoPlayerTable(40);
        for (Viewer viewer : List.of(
                Viewer.seat(GameFixtures.ALICE), Viewer.seat(GameFixtures.BOB),
                Viewer.SPECTATOR)) {
            GameView view = VisibilityRules.viewFor(session.state(), viewer);
            for (SeatView seat : view.seats()) {
                assertThat(seat.zones().keySet())
                        .describedAs("%s sees every zone of seat %s", viewer, seat.seat())
                        .containsExactlyInAnyOrder(Zone.values());
            }
        }
    }

    @Nested
    @DisplayName("section 6's table, line by line")
    class TheTable {

        @Test
        @DisplayName("a library is a count to everyone, its owner included")
        void librariesAreCountOnlyEvenForTheirOwner() {
            GameSession session = GameFixtures.twoPlayerTable(40);

            for (Map.Entry<Viewer, GameView> entry : VisibilityRules.allViews(session.state()).entrySet()) {
                ZoneView library = entry.getValue().seat(GameFixtures.ALICE).zone(Zone.LIBRARY);
                assertThat(library.cards())
                        .as("library contents for %s", entry.getKey())
                        .isEmpty();
                assertThat(library.count()).isEqualTo(40);
            }
        }

        @Test
        @DisplayName("a hand is full to its owner and a count to everybody else")
        void handsAreOwnerOnly() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 7));

            GameView alice = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.ALICE));
            GameView bob = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.BOB));
            GameView spectator = VisibilityRules.viewFor(session.state(), Viewer.SPECTATOR);

            assertThat(alice.seat(GameFixtures.ALICE).zone(Zone.HAND).cards()).hasSize(7);
            assertThat(bob.seat(GameFixtures.ALICE).zone(Zone.HAND).cards()).isEmpty();
            assertThat(bob.seat(GameFixtures.ALICE).zone(Zone.HAND).count()).isEqualTo(7);
            assertThat(spectator.seat(GameFixtures.ALICE).zone(Zone.HAND).cards()).isEmpty();
            assertThat(spectator.seat(GameFixtures.ALICE).zone(Zone.HAND).count()).isEqualTo(7);
        }

        @Test
        @DisplayName("a face-up battlefield is full to everyone, spectators included")
        void faceUpPublicZonesAreFullyVisible() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId played = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, played, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            for (GameView view : VisibilityRules.allViews(session.state()).values()) {
                assertThat(view.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards())
                        .singleElement()
                        .isInstanceOf(CardView.Visible.class);
            }
        }

        @Test
        @DisplayName("a face-down card is its owner's card and everyone else's marker")
        void faceDownCardsAreAnonymousToEveryoneElse() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));

            GameView alice = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.ALICE));
            GameView bob = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.BOB));
            GameView spectator = VisibilityRules.viewFor(session.state(), Viewer.SPECTATOR);

            assertThat(alice.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards())
                    .singleElement().isInstanceOf(CardView.Visible.class);
            assertThat(bob.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards())
                    .singleElement().isInstanceOf(CardView.Anonymous.class);
            assertThat(spectator.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards())
                    .singleElement().isInstanceOf(CardView.Anonymous.class);
        }

        @Test
        @DisplayName("a marker carries tap state and counters, so opponents can still follow it")
        void markersCarryEverythingButIdentity() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));
            session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, card, true));
            session.submit(new GameEvent.CounterChanged(
                    GameFixtures.ALICE, card, CardInstance.Counters.PLUS_ONE_PLUS_ONE, 2));

            CardView seen = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.BOB))
                    .seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards().get(0);

            assertThat(seen).isInstanceOfSatisfying(CardView.Anonymous.class, anonymous -> {
                assertThat(anonymous.tapped()).isTrue();
                assertThat(anonymous.counters()).containsEntry(CardInstance.Counters.PLUS_ONE_PLUS_ONE, 2);
                assertThat(anonymous.marker()).isNotNull();
            });
        }

        @Test
        @DisplayName("the command zone is public, which is why commanders are exempt from ante")
        void commandZoneIsPublic() {
            GameSession session = GameFixtures.twoPlayerTable(40);

            for (GameView view : VisibilityRules.allViews(session.state()).values()) {
                assertThat(view.seat(GameFixtures.ALICE).zone(Zone.COMMAND).cards())
                        .singleElement().isInstanceOf(CardView.Visible.class);
            }
        }
    }

    @Nested
    @DisplayName("the invariant itself")
    class TheInvariant {

        @Test
        @DisplayName("no viewer ever receives identity for a card they are not entitled to")
        void noUnentitledIdentityInAnyView() {
            GameSession session = playARepresentativeGame();
            GameState state = session.state();

            for (Map.Entry<Viewer, GameView> entry : VisibilityRules.allViews(state).entrySet()) {
                Viewer viewer = entry.getKey();
                for (CardView card : entry.getValue().allCardViews()) {
                    if (card instanceof CardView.Visible visible) {
                        assertThat(isEntitled(state, viewer, visible.id()))
                                .as("%s received identity for %s", viewer, visible.id())
                                .isTrue();
                    }
                }
            }
        }

        @Test
        @DisplayName("a marker is never derived from the card it hides")
        void markersAreIndependentOfCardIdentity() {
            // The property, stated as an experiment: run the same session twice, flipping a
            // *different* card face down each time. If the markers still match, then marker
            // generation cannot be reading the card - there is no function from marker back
            // to identity, because identity was never an input.
            MarkerId fromFirstCard = flipNthCardOfHandFaceDown(0);
            MarkerId fromSecondCard = flipNthCardOfHandFaceDown(1);

            assertThat(fromFirstCard).isEqualTo(fromSecondCard);
        }

        @Test
        @DisplayName("a marker shares nothing with the Scryfall id it stands in for")
        void markersShareNoSubstringWithTheScryfallId() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 3));

            List<CardInstanceId> hand = List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.HAND));
            for (CardInstanceId card : hand) {
                session.submit(new GameEvent.CardMoved(
                        GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
                session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));
            }

            Set<String> markers = session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD).stream()
                    .map(id -> session.state().requireCard(id).markerId().orElseThrow().value())
                    .collect(Collectors.toSet());

            assertThat(markers).as("every face-down card gets its own marker").hasSize(3);
            for (CardInstanceId card : hand) {
                CardIdentity identity = session.state().requireCard(card).identity();
                String scryfallId = identity.printing().orElseThrow().toString().replace("-", "");
                assertThat(markers).noneMatch(scryfallId::contains);
            }
        }

        @Test
        @DisplayName("two sessions with different seeds hand out different markers")
        void markersDifferBetweenSessions() {
            SessionSeed otherSeed = SessionSeed.fromBytes(
                    "a-completely-different-session-seed".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThat(firstMarkerWithSeed(GameFixtures.FIXED_SEED))
                    .isNotEqualTo(firstMarkerWithSeed(otherSeed));
        }

        @Test
        @DisplayName("flipping down twice gives two different markers, so periods cannot be linked")
        void markersAreFreshOnEveryFlipDown() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
            CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(
                    GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));
            MarkerId first = session.state().requireCard(card).markerId().orElseThrow();
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_UP));
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));
            MarkerId second = session.state().requireCard(card).markerId().orElseThrow();

            assertThat(second).isNotEqualTo(first);
        }

        @Test
        @DisplayName("a hidden zone sends an empty list, not a list of placeholders")
        void hiddenZonesSendNoPerCardPayloadAtAll() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 7));

            GameView bob = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.BOB));
            SeatView alice = bob.seat(GameFixtures.ALICE);

            assertThat(alice.zone(Zone.HAND).cards()).isEmpty();
            assertThat(alice.zone(Zone.LIBRARY).cards()).isEmpty();
            assertThat(alice.zone(Zone.HAND).count()).isEqualTo(7);
        }

        @Test
        @DisplayName("a spectator's view is never richer than an opponent's")
        void spectatorsSeeNoMoreThanOpponents() {
            GameSession session = playARepresentativeGame();
            GameState state = session.state();

            List<CardView> spectatorViews = VisibilityRules.viewFor(state, Viewer.SPECTATOR).allCardViews();
            List<CardView> opponentViews = VisibilityRules
                    .viewFor(state, Viewer.seat(GameFixtures.BOB)).allCardViews();

            long spectatorIdentities = spectatorViews.stream().filter(CardView::carriesIdentity).count();
            long opponentIdentities = opponentViews.stream().filter(CardView::carriesIdentity).count();

            assertThat(spectatorIdentities).isLessThanOrEqualTo(opponentIdentities);
        }
    }

    /** Flips one card of Alice's opening hand face down and returns the marker it got. */
    private static MarkerId flipNthCardOfHandFaceDown(int handIndex) {
        GameSession session = GameFixtures.twoPlayerTable(40);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 7));
        CardInstanceId card = session.state().contents(GameFixtures.ALICE, Zone.HAND).get(handIndex);
        session.submit(new GameEvent.CardMoved(
                GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
        session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));
        return session.state().requireCard(card).markerId().orElseThrow();
    }

    private static MarkerId firstMarkerWithSeed(SessionSeed seed) {
        GameSession session = GameSession.create(
                List.of(GameFixtures.ALICE, GameFixtures.BOB), 40, seed, UndoMode.shippedDefault());
        session.submit(new GameEvent.DeckLoaded(GameFixtures.ALICE, GameFixtures.deck(10), List.of()));
        CardInstanceId card = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(0);
        session.submit(new GameEvent.CardMoved(
                GameFixtures.ALICE, card, ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
        session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));
        return session.state().requireCard(card).markerId().orElseThrow();
    }

    /** Whether the visibility table entitles this viewer to this card's identity. */
    private static boolean isEntitled(GameState state, Viewer viewer, CardInstanceId id) {
        ZoneRef location = state.locationOf(id).orElseThrow();
        CardInstance card = state.requireCard(id);

        if (location.zone() == Zone.LIBRARY) {
            return false;
        }
        if (location.zone() == Zone.HAND) {
            return viewer.isSeatedAt(location.seat());
        }
        return !card.isFaceDown() || viewer.isSeatedAt(card.owner());
    }

    /** A short game that touches every zone and both facings. */
    private static GameSession playARepresentativeGame() {
        GameSession session = GameFixtures.twoPlayerTable(40);
        session.submit(new GameEvent.LibraryShuffled(GameFixtures.ALICE, GameFixtures.ALICE));
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 7));
        session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 7));

        List<CardInstanceId> aliceHand = List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.HAND));
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, aliceHand.get(0),
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, aliceHand.get(1),
                ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD), Placement.TOP));
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, aliceHand.get(2),
                ZoneRef.of(GameFixtures.ALICE, Zone.EXILE), Placement.TOP));
        session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, aliceHand.get(2), Facing.FACE_DOWN));

        List<CardInstanceId> bobHand = List.copyOf(session.state().contents(GameFixtures.BOB, Zone.HAND));
        session.submit(new GameEvent.CardMoved(GameFixtures.BOB, bobHand.get(0),
                ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.BOTTOM));
        session.submit(new GameEvent.CardFacingSet(GameFixtures.BOB, bobHand.get(0), Facing.FACE_DOWN));

        // A stolen creature: still Bob's card, now on Alice's side of the table.
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, bobHand.get(0),
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
        return session;
    }
}
