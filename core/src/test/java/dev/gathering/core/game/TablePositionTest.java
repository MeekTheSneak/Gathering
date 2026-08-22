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
 * Where cards sit once they are on the table.
 *
 * <p>Cards snap to a grid and stay where they were put. The board a player arranges is the
 * board everyone else sees, which is the whole reason position is state rather than a
 * per-client layout guess.
 */
class TablePositionTest {

    @Nested
    @DisplayName("dropping")
    class Dropping {

        @Test
        @DisplayName("a card dragged onto a square lands on that square")
        void aDragLandsWhereItWasAimed() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(5, 2)));

            assertThat(session.state().requireCard(card).square())
                    .contains(TablePosition.of(5, 2));
        }

        @Test
        @DisplayName("a card that arrives without being aimed still gets a definite square")
        void unaimedArrivalsGetTheFirstFreeSquare() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.state().requireCard(card).square()).contains(TablePosition.ORIGIN);
        }

        @Test
        @DisplayName("tokens land somewhere rather than nowhere, and never on top of each other")
        void tokensGetDistinctSquares() {
            GameSession session = GameFixtures.twoPlayerTable(10);

            session.submit(new GameEvent.TokenCreated(
                    GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(500), 5));

            Set<TablePosition> squares = squaresOn(session, GameFixtures.ALICE);
            assertThat(squares).hasSize(5);
        }

        @Test
        @DisplayName("a freed square is reused, so a long game does not drift across the table")
        void gapsAreFilledRatherThanSkipped() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            List<CardInstanceId> cards = drawMany(session, GameFixtures.ALICE, 3);
            for (CardInstanceId card : cards) {
                session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                        ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
            }
            // Kill the middle one, leaving a hole at slot 1.
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(1),
                    ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD), Placement.TOP));

            CardInstanceId replacement = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, replacement,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.state().requireCard(replacement).square())
                    .contains(TablePosition.slot(1));
        }

        @Test
        @DisplayName("stacking two cards on one square is allowed, because the mod never says no")
        void deliberateStackingIsPermitted() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            List<CardInstanceId> cards = drawMany(session, GameFixtures.ALICE, 2);

            for (CardInstanceId card : cards) {
                GameSession.Result result = session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                        ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(3, 3)));
                assertThat(result.isAccepted()).isTrue();
            }

            assertThat(session.state().requireCard(cards.get(0)).square())
                    .isEqualTo(session.state().requireCard(cards.get(1)).square());
        }
    }

    @Nested
    @DisplayName("leaving the surface")
    class LeavingTheSurface {

        @Test
        @DisplayName("a card going into a pile forgets its square, because a pile is an order")
        void pilesHaveNoGeometry() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(4, 1)));

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD), Placement.TOP));

            assertThat(session.state().requireCard(card).square()).isEmpty();
        }

        @Test
        @DisplayName("cards drawn into a hand have no square either")
        void handsHaveNoGeometry() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);

            assertThat(session.state().requireCard(card).square()).isEmpty();
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
                    ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.at(2, 0)));

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, bobsCard,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(7, 1)));

            assertThat(session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD)).contains(bobsCard);
            assertThat(session.state().contents(GameFixtures.BOB, Zone.BATTLEFIELD)).isEmpty();
            assertThat(session.state().requireCard(bobsCard).square()).contains(TablePosition.of(7, 1));
            assertThat(session.state().requireCard(bobsCard).owner()).isEqualTo(GameFixtures.BOB);
        }
    }

    @Nested
    @DisplayName("what the table sees")
    class WhatTheTableSees {

        @Test
        @DisplayName("every viewer gets the square, so every client draws the same board")
        void positionsReachEveryView() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(6, 2)));

            for (var view : VisibilityRules.allViews(session.state()).values()) {
                assertThat(view.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards())
                        .singleElement()
                        .satisfies(seen -> assertThat(seen.square()).contains(TablePosition.of(6, 2)));
            }
        }

        @Test
        @DisplayName("a face-down card's square is public; only which card it is stays hidden")
        void facedownCardsStillHaveAVisiblePlace() {
            GameSession session = GameFixtures.twoPlayerTable(40);
            CardInstanceId card = drawOne(session, GameFixtures.ALICE);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(1, 1)));
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, card, Facing.FACE_DOWN));

            CardView seen = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.BOB))
                    .seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards().get(0);

            assertThat(seen).isInstanceOf(CardView.Anonymous.class);
            assertThat(seen.square()).contains(TablePosition.of(1, 1));
        }
    }

    @Test
    @DisplayName("a position off the table is refused rather than drawn a mile away")
    void positionsAreBounded() {
        assertThatThrownBy(() -> TablePosition.of(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablePosition.of(0, TablePosition.MAX_ROW + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TablePosition.of(TablePosition.MAX_COLUMN, TablePosition.MAX_ROW)).isNotNull();
    }

    @Test
    @DisplayName("auto-placement wraps into rows rather than running off the side")
    void autoPlacementWraps() {
        assertThat(TablePosition.slot(0)).isEqualTo(TablePosition.of(0, 0));
        assertThat(TablePosition.slot(TablePosition.DEFAULT_ROW_WIDTH - 1))
                .isEqualTo(TablePosition.of(TablePosition.DEFAULT_ROW_WIDTH - 1, 0));
        assertThat(TablePosition.slot(TablePosition.DEFAULT_ROW_WIDTH)).isEqualTo(TablePosition.of(0, 1));
    }

    // ------------------------------------------------------------- fixtures

    private static CardInstanceId drawOne(GameSession session, SeatId seat) {
        return drawMany(session, seat, 1).get(0);
    }

    private static List<CardInstanceId> drawMany(GameSession session, SeatId seat, int count) {
        List<CardInstanceId> before = List.copyOf(session.state().contents(seat, Zone.HAND));
        session.submit(new GameEvent.CardsDrawn(seat, seat, count));
        List<CardInstanceId> after = new ArrayList<>(session.state().contents(seat, Zone.HAND));
        after.removeAll(before);
        return after;
    }

    private static Set<TablePosition> squaresOn(GameSession session, SeatId seat) {
        Set<TablePosition> squares = new HashSet<>();
        for (CardInstanceId id : session.state().contents(seat, Zone.BATTLEFIELD)) {
            session.state().requireCard(id).square().ifPresent(squares::add);
        }
        return squares;
    }
}
