package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Where cards sit once they are on the table, and which way round they are.
 * <p>There is no grid. A card goes exactly where it was put down, at exactly the angle it was
 * left at, and stays there. The board a player arranges is the board everyone else sees,
 * which is the whole reason position is state rather than a per-client layout guess.
 */
class TablePositionTest {

    @Nested
    @DisplayName("dropping")
    class Dropping {

        @Test
        @DisplayName("a card dropped somewhere lands exactly there, not on the nearest anything")
        void aDropLandsWhereItWasAimed() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(5137, 2049)));

            assertThat(session.state().requireCard(card).placedAt())
                    .contains(TablePosition.of(5137, 2049));
        }

        @Test
        @DisplayName("two spots a pixel apart are two spots, because the table is continuous")
        void neighboringSpotsAreDistinct() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            List<CardInstanceId> cards = drawMany(session, GameFixtures.ALICE, 2);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(0),
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(4000, 4000)));
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(1),
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(4001, 4000)));

            assertThat(session.state().requireCard(cards.get(0)).placedAt())
                    .isNotEqualTo(session.state().requireCard(cards.get(1)).placedAt());
        }

        @Test
        @DisplayName("a card that arrives without being aimed still gets a definite spot")
        void unaimedArrivalsAreStillPutDownSomewhere() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.state().requireCard(card).placedAt()).contains(TablePosition.unaimed(0));
        }

        @Test
        @DisplayName("tokens land somewhere rather than nowhere, and never on top of each other")
        void tokensGetDistinctSpots() {
            GameSession session = GameFixtures.twoPlayerTable(10);

            session.submit(new GameEvent.TokenCreated(
                    GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(500), 5));

            assertThat(spotsOn(session, GameFixtures.ALICE)).hasSize(5);
        }

        @Test
        @DisplayName("a vacated spot is reused, so a long game does not drift across the table")
        void gapsAreFilledRatherThanSkipped() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            List<CardInstanceId> cards = drawMany(session, GameFixtures.ALICE, 3);
            for (CardInstanceId card : cards) {
                session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                        ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
            }
            // Kill the middle one, leaving a hole where the second card was fanned out.
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(1),
                    ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD), Placement.TOP));

            CardInstanceId replacement = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, replacement,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.state().requireCard(replacement).placedAt())
                    .contains(TablePosition.unaimed(1));
        }

        @Test
        @DisplayName("an unaimed card never lands under a card a player put there by hand")
        void unaimedArrivalsAvoidHandPlacedCards() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            List<CardInstanceId> cards = drawMany(session, GameFixtures.ALICE, 2);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(0),
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD),
                    Placement.at(TablePosition.unaimed(0))));
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(1),
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.state().requireCard(cards.get(1)).placedAt())
                    .isNotEqualTo(session.state().requireCard(cards.get(0)).placedAt());
        }

        @Test
        @DisplayName("stacking two cards on one spot is allowed, because the mod never says no")
        void deliberateStackingIsPermitted() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            List<CardInstanceId> cards = drawMany(session, GameFixtures.ALICE, 2);

            for (CardInstanceId card : cards) {
                GameSession.Result result = session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                        ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(3000, 3000)));
                assertThat(result.isAccepted()).isTrue();
            }

            assertThat(session.state().requireCard(cards.get(0)).placedAt())
                    .isEqualTo(session.state().requireCard(cards.get(1)).placedAt());
        }
    }

    @Nested
    @DisplayName("turning")
    class Turning {

        @Test
        @DisplayName("a card turned to an angle stays at that angle")
        void rotationSticks() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = onTheBattlefield(session, GameFixtures.ALICE);

            session.submit(new GameEvent.CardRotated(GameFixtures.ALICE, card, 37));

            assertThat(session.state().requireCard(card).placedAt())
                    .get()
                    .extracting(TablePosition::rotation)
                    .isEqualTo(37);
        }

        @Test
        @DisplayName("turning does not move the card")
        void rotationKeepsTheSpot() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(1234, 5678)));

            session.submit(new GameEvent.CardRotated(GameFixtures.ALICE, card, 90));

            assertThat(session.state().requireCard(card).placedAt())
                    .contains(TablePosition.of(1234, 5678, 90));
        }

        @Test
        @DisplayName("any angle is a legal angle, and it wraps rather than being refused")
        void anglesWrap() {
            assertThat(TablePosition.of(0, 0, 450).rotation()).isEqualTo(90);
            assertThat(TablePosition.of(0, 0, -90).rotation()).isEqualTo(270);
            assertThat(TablePosition.of(0, 0, 360).rotation()).isZero();
        }

        @Test
        @DisplayName("untapping everything leaves a deliberately angled card alone")
        void untapAllDoesNotStraightenCards() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = onTheBattlefield(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardRotated(GameFixtures.ALICE, card, 15));
            session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, card, true));

            session.submit(new GameEvent.SeatUntappedAll(GameFixtures.ALICE, GameFixtures.ALICE));

            assertThat(session.state().requireCard(card).tapped()).isFalse();
            assertThat(session.state().requireCard(card).placedAt())
                    .get()
                    .extracting(TablePosition::rotation)
                    .isEqualTo(15);
        }

        @Test
        @DisplayName("turning a card that is no longer on the table changes nothing and throws nothing")
        void rotatingAPiledCardIsHarmless() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);

            GameSession.Result result = session.submit(new GameEvent.CardRotated(GameFixtures.ALICE, card, 90));

            assertThat(result.isAccepted()).isTrue();
            assertThat(session.state().requireCard(card).placedAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("leaving the surface")
    class LeavingTheSurface {

        @Test
        @DisplayName("a card going into a pile forgets where it sat, because a pile is an order")
        void pilesHaveNoGeometry() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(4000, 1000)));

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD), Placement.TOP));

            assertThat(session.state().requireCard(card).placedAt()).isEmpty();
        }

        @Test
        @DisplayName("cards drawn into a hand have no place on the table either")
        void handsHaveNoGeometry() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);

            assertThat(session.state().requireCard(card).placedAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("control changes")
    class ControlChanges {

        @Test
        @DisplayName("stealing is dragging to your own side, and the card keeps its owner")
        void draggingAcrossRegionsMovesControlAndNotOwnership() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId bobsCard = drawOne(session, GameFixtures.BOB);
            session.submit(new GameEvent.CardMoved(GameFixtures.BOB, bobsCard,
                    ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.at(2000, 500)));

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, bobsCard,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(7000, 1500)));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD)).contains(bobsCard);
            assertThat(session.state().contents(GameFixtures.BOB, Zone.BATTLEFIELD)).isEmpty();
            assertThat(session.state().requireCard(bobsCard).placedAt()).contains(TablePosition.of(7000, 1500));
            assertThat(session.state().requireCard(bobsCard).owner()).isEqualTo(GameFixtures.BOB);
        }
    }

    @Nested
    @DisplayName("what the table sees")
    class WhatTheTableSees {

        @Test
        @DisplayName("every viewer gets the spot and the angle, so every client draws the same board")
        void positionsReachEveryView() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(6000, 2000)));
            session.submit(new GameEvent.CardRotated(GameFixtures.ALICE, card, 45));

            for (var view : VisibilityRules.allViews(session.state()).values()) {
                assertThat(view.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards())
                        .singleElement()
                        .satisfies(seen ->
                                assertThat(seen.placedAt()).contains(TablePosition.of(6000, 2000, 45)));
            }
        }

        @Test
        @DisplayName("a face-down card's place is public; only which card it is stays hidden")
        void facedownCardsStillHaveAVisiblePlace() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(1000, 1000)));
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));

            CardView seen = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.BOB))
                    .seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards().get(0);

            assertThat(seen).isInstanceOf(CardView.Anonymous.class);
            assertThat(seen.placedAt()).contains(TablePosition.of(1000, 1000));
        }
    }

    @Nested
    @DisplayName("the coordinate space itself")
    class CoordinateSpace {

        @Test
        @DisplayName("a position off the table is refused rather than drawn a mile away")
        void positionsAreBounded() {
            assertThatThrownBy(() -> TablePosition.of(-1, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TablePosition.of(0, TablePosition.SPAN + 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(TablePosition.of(TablePosition.SPAN, TablePosition.SPAN)).isNotNull();
        }

        @Test
        @DisplayName("dragging past the edge stops at the edge rather than being refused")
        void movesClampInsteadOfThrowing() {
            TablePosition position = TablePosition.of(500, 500, 90);

            assertThat(position.movedTo(-4000, TablePosition.SPAN * 2))
                    .isEqualTo(TablePosition.of(0, TablePosition.SPAN, 90));
        }

        @Test
        @DisplayName("a fraction of the way across survives the round trip")
        void fractionsRoundTrip() {
            assertThat(TablePosition.fraction(0.25, 0.75).acrossFraction()).isEqualTo(0.25);
            assertThat(TablePosition.fraction(0.25, 0.75).downFraction()).isEqualTo(0.75);
        }

        @Test
        @DisplayName("the fan hands out distinct spots for as long as it claims to")
        void theFanDoesNotRepeatEarly() {
            Set<TablePosition> spots = new HashSet<>();
            for (int index = 0; index < TablePosition.FAN_SPOTS; index++) {
                spots.add(TablePosition.unaimed(index));
            }
            assertThat(spots).hasSize(TablePosition.FAN_SPOTS);
        }

        @Test
        @DisplayName("every fanned spot is on the table")
        void theFanStaysOnTheTable() {
            for (int index = 0; index < TablePosition.FAN_SPOTS; index++) {
                TablePosition spot = TablePosition.unaimed(index);
                assertThat(spot.acrossFraction()).isBetween(0.0, 1.0);
                assertThat(spot.downFraction()).isBetween(0.0, 1.0);
            }
        }
    }

    // ------------------------------------------------------------- fixtures

    private static CardInstanceId drawOne(GameSession session, SeatId seat) {
        return drawMany(session, seat, 1).get(0);
    }

    private static CardInstanceId onTheBattlefield(GameSession session, SeatId seat) {
        CardInstanceId card = drawOne(session, seat);
        session.submit(new GameEvent.CardMoved(
                seat, card, ZoneRef.of(seat, Zone.BATTLEFIELD), Placement.BOTTOM));
        return card;
    }

    private static List<CardInstanceId> drawMany(GameSession session, SeatId seat, int count) {
        List<CardInstanceId> before = List.copyOf(session.state().contents(seat, Zone.HAND));
        session.submit(new GameEvent.CardsDrawn(seat, seat, count));
        List<CardInstanceId> after = new ArrayList<>(session.state().contents(seat, Zone.HAND));
        after.removeAll(before);
        return after;
    }

    private static Set<TablePosition> spotsOn(GameSession session, SeatId seat) {
        Set<TablePosition> spots = new HashSet<>();
        for (CardInstanceId id : session.state().contents(seat, Zone.BATTLEFIELD)) {
            session.state().requireCard(id).placedAt().ifPresent(spots::add);
        }
        return spots;
    }
}
