package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Cards sitting on other cards: auras, equipment, and whatever a group means by it.
 * <p>Attachment is a drawing relationship and nothing else. The mod does not know what an aura
 * is, does not check that one may legally be where it is, and does not move anything to a
 * graveyard when its host dies - the group decides all of that, as with every other rule.
 * <p>What it does have to guarantee is that the relationship can always be drawn. A card
 * attached to itself, to something in a pile, or to something that has left the table are all
 * arrangements with no picture, and the ones below are where each of them gets refused.
 */
class AttachmentTest {

    @Nested
    @DisplayName("putting a card on another")
    class PuttingACardOnAnother {

        @Test
        @DisplayName("a card can sit on a card that is on the table")
        void attachingWorks() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);

            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            assertThat(session.state().requireCard(cards.get(1)).host()).contains(cards.get(0));
            assertThat(session.state().attachmentsOf(cards.get(0))).containsExactly(cards.get(1));
        }

        @Test
        @DisplayName("a card cannot sit on itself")
        void nothingSitsOnItself() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            CardInstanceId card = onTheBattlefield(session, GameFixtures.ALICE, 1).get(0);

            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, card, card));

            assertThat(session.state().requireCard(card).isAttached()).isFalse();
        }

        @Test
        @DisplayName("a card cannot sit on something that is in a pile")
        void hostsHaveToBeOnTheTable() {
            // Nothing in a graveyard has a place on the table, so there is nowhere to draw the
            // attachment - and a client that tried would draw it beside nothing.
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(0),
                    ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD), Placement.TOP));

            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            assertThat(session.state().requireCard(cards.get(1)).isAttached()).isFalse();
        }

        @Test
        @DisplayName("chains are refused, which is the only way left to make a loop")
        void nothingSitsOnSomethingAlreadySittingOnSomething() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 3);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(2), cards.get(1)));

            assertThat(session.state().requireCard(cards.get(2)).isAttached()).isFalse();
        }

        @Test
        @DisplayName("a stale click on a card that has gone does nothing rather than exploding")
        void aVanishedHostIsHarmless() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 1);
            session.submit(new GameEvent.TokenCreated(
                    GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(9), 1));
            CardInstanceId token = session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD).stream()
                    .filter(id -> session.state().requireCard(id).token())
                    .findFirst()
                    .orElseThrow();
            session.submit(new GameEvent.TokenRemoved(GameFixtures.ALICE, token));

            GameSession.Result result = session.submit(
                    new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(0), token));

            assertThat(result.isAccepted()).isTrue();
            assertThat(session.state().requireCard(cards.get(0)).isAttached()).isFalse();
        }

        @Test
        @DisplayName("several cards can sit on one card")
        void aCardCanCarryMoreThanOneThing() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 3);

            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(2), cards.get(0)));

            assertThat(session.state().attachmentsOf(cards.get(0)))
                    .containsExactlyInAnyOrder(cards.get(1), cards.get(2));
        }
    }

    @Nested
    @DisplayName("coming apart")
    class ComingApart {

        @Test
        @DisplayName("a null host takes it off again")
        void detachingWorks() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), null));

            assertThat(session.state().requireCard(cards.get(1)).isAttached()).isFalse();
        }

        @Test
        @DisplayName("a host leaving the table drops everything on it")
        void thingsFallOffWhenTheHostGoes() {
            // In paper the aura falls off too. Here the point is narrower: an attachment to a
            // card with no place on the table has no picture.
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(0),
                    ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD), Placement.TOP));

            assertThat(session.state().requireCard(cards.get(1)).isAttached()).isFalse();
        }

        @Test
        @DisplayName("an attachment leaving the table comes off the thing it was on")
        void theAttachmentItselfComesOff() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(1),
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));

            assertThat(session.state().requireCard(cards.get(1)).isAttached()).isFalse();
            assertThat(session.state().attachmentsOf(cards.get(0))).isEmpty();
        }

        @Test
        @DisplayName("a token ceasing to exist takes nothing broken with it")
        void removingATokenDetachesWhatWasOnIt() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 1);
            session.submit(new GameEvent.TokenCreated(
                    GameFixtures.ALICE, GameFixtures.ALICE, GameFixtures.card(9), 1));
            CardInstanceId token = session.state().contents(GameFixtures.ALICE, Zone.BATTLEFIELD).stream()
                    .filter(id -> session.state().requireCard(id).token())
                    .findFirst()
                    .orElseThrow();
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(0), token));

            session.submit(new GameEvent.TokenRemoved(GameFixtures.ALICE, token));

            assertThat(session.state().requireCard(cards.get(0)).isAttached()).isFalse();
        }

        @Test
        @DisplayName("moving a host across the table keeps what is on it")
        void slidingSomethingAlongDoesNotShakeItOff() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, cards.get(0),
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(7000, 2000)));

            assertThat(session.state().requireCard(cards.get(1)).host()).contains(cards.get(0));
        }

        @Test
        @DisplayName("stealing a creature takes its equipment with it")
        void attachmentsSurviveChangingSides() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            session.submit(new GameEvent.CardMoved(GameFixtures.BOB, cards.get(0),
                    ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.BOTTOM));

            assertThat(session.state().requireCard(cards.get(1)).host()).contains(cards.get(0));
        }
    }

    @Nested
    @DisplayName("what the table sees")
    class WhatTheTableSees {

        @Test
        @DisplayName("everybody is told what is on what, because everybody can see it")
        void attachmentsArePublic() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));

            for (GameView view : VisibilityRules.allViews(session.state()).values()) {
                assertThat(hostsIn(view, GameFixtures.ALICE))
                        .as("hosts seen by %s", view.viewer())
                        .contains(cards.get(0));
            }
        }

        @Test
        @DisplayName("a face-down card says what it is attached to without saying what it is")
        void anAnonymousCardStillCarriesItsHost() {
            // Everybody at a real table can see the equipment lying across a morph. The host it
            // names is a card they can already see, so there is nothing here to invert.
            GameSession session = GameFixtures.twoPlayerTable(20);
            List<CardInstanceId> cards = onTheBattlefield(session, GameFixtures.ALICE, 2);
            session.submit(new GameEvent.CardAttached(GameFixtures.ALICE, cards.get(1), cards.get(0)));
            session.submit(new GameEvent.CardFacingSet(GameFixtures.ALICE, cards.get(1), Facing.FACE_DOWN));

            List<CardView> seen = VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.BOB))
                    .seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards();

            CardView anonymous = seen.stream()
                    .filter(CardView.Anonymous.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            assertThat(anonymous.host()).contains(cards.get(0));
        }
    }

    // ------------------------------------------------------------- fixtures

    private static List<CardInstanceId> onTheBattlefield(GameSession session, SeatId seat, int count) {
        List<CardInstanceId> before = List.copyOf(session.state().contents(seat, Zone.HAND));
        session.submit(new GameEvent.CardsDrawn(seat, seat, count));
        List<CardInstanceId> drawn = new ArrayList<>(session.state().contents(seat, Zone.HAND));
        drawn.removeAll(before);

        for (CardInstanceId card : drawn) {
            session.submit(new GameEvent.CardMoved(
                    seat, card, ZoneRef.of(seat, Zone.BATTLEFIELD), Placement.BOTTOM));
        }
        return drawn;
    }

    private static List<CardInstanceId> hostsIn(GameView view, SeatId seat) {
        List<CardInstanceId> hosts = new ArrayList<>();
        for (CardView card : view.seat(seat).zone(Zone.BATTLEFIELD).cards()) {
            card.host().ifPresent(hosts::add);
        }
        return hosts;
    }
}
