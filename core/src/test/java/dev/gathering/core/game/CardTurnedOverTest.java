package dev.gathering.core.game;

import static dev.gathering.core.game.GameFixtures.ALICE;
import static dev.gathering.core.game.GameFixtures.BOB;
import static dev.gathering.core.game.GameFixtures.twoPlayerTable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.VisibilityRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Turning a card to its other printed face.
 * <p>The whole point of these is that turned over and face down are different things. One is
 * a transforming card showing its back, which everybody may read; the other is a sleeve
 * nobody may name. A model that folded them together would make a morph and a werewolf the
 * same act, and would leak a card's name the first time somebody transformed a face-down one.
 */
class CardTurnedOverTest {

    private static CardInstanceId aPermanentOf(GameSession session) {
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardMoved(
                ALICE, card, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.at(1000, 1000)));
        return card;
    }

    @Test
    @DisplayName("a card starts on its front and turns to its back")
    void turnsOver() {
        GameSession session = twoPlayerTable(6);
        CardInstanceId card = aPermanentOf(session);
        assertThat(session.state().requireCard(card).turnedOver()).isFalse();

        session.submit(new GameEvent.CardTurnedOver(ALICE, card, true));

        assertThat(session.state().requireCard(card).turnedOver()).isTrue();
    }

    @Test
    @DisplayName("turned over and face down are independent")
    void turnedOverIsNotFaceDown() {
        GameSession session = twoPlayerTable(6);
        CardInstanceId card = aPermanentOf(session);
        session.submit(new GameEvent.CardTurnedOver(ALICE, card, true));

        session.submit(new GameEvent.CardFacingSet(ALICE, card, Facing.FACE_DOWN));
        assertThat(session.state().requireCard(card).isFaceDown()).isTrue();
        assertThat(session.state().requireCard(card).turnedOver())
                .as("a transformed permanent turned face down comes back up transformed")
                .isTrue();

        session.submit(new GameEvent.CardFacingSet(ALICE, card, Facing.FACE_UP));
        assertThat(session.state().requireCard(card).isFaceDown()).isFalse();
        assertThat(session.state().requireCard(card).turnedOver()).isTrue();
    }

    @Test
    @DisplayName("which side is up is public, and a face-down card still names nothing")
    void whichSideIsUpIsPublic() {
        GameSession session = twoPlayerTable(6);
        CardInstanceId card = aPermanentOf(session);
        session.submit(new GameEvent.CardTurnedOver(ALICE, card, true));

        GameView theirs = VisibilityRules.viewFor(session.state(), new Viewer.Seated(BOB));
        CardView seen = theirs.seat(ALICE).zone(Zone.BATTLEFIELD).cards().get(0);
        assertThat(seen).isInstanceOf(CardView.Visible.class);
        assertThat(seen.turnedOver()).isTrue();

        session.submit(new GameEvent.CardFacingSet(ALICE, card, Facing.FACE_DOWN));
        CardView hidden = VisibilityRules.viewFor(session.state(), new Viewer.Seated(BOB))
                .seat(ALICE).zone(Zone.BATTLEFIELD).cards().get(0);
        assertThat(hidden).isInstanceOf(CardView.Anonymous.class);
        assertThat(hidden.turnedOver())
                .as("a sleeve has no side to be showing, and saying which would be a tell")
                .isFalse();
    }

    @Test
    @DisplayName("anybody may turn a public card over, and the log says who")
    void anybodyMayTurnItOver() {
        GameSession session = twoPlayerTable(6);
        CardInstanceId card = aPermanentOf(session);

        GameSession.Result result = session.submit(new GameEvent.CardTurnedOver(BOB, card, true));

        assertThat(result).isInstanceOf(GameSession.Result.Accepted.class);
        assertThat(session.log().get(session.log().size() - 1).key())
                .isEqualTo("log.gathering.card_turned_over");
    }

    @Test
    @DisplayName("undoing a turn puts the card back on the side it was")
    void undoTurnsItBack() {
        GameSession session = twoPlayerTable(6);
        CardInstanceId card = aPermanentOf(session);
        session.submit(new GameEvent.CardTurnedOver(ALICE, card, true));

        session.undo(ALICE, 1, List.of());

        assertThat(session.state().requireCard(card).turnedOver()).isFalse();
    }

    @Test
    @DisplayName("turning it back says so in the log")
    void turningItBackSaysSo() {
        GameSession session = twoPlayerTable(6);
        CardInstanceId card = aPermanentOf(session);
        session.submit(new GameEvent.CardTurnedOver(ALICE, card, true));
        session.submit(new GameEvent.CardTurnedOver(ALICE, card, false));

        assertThat(session.state().requireCard(card).turnedOver()).isFalse();
        assertThat(session.log().get(session.log().size() - 1).key())
                .isEqualTo("log.gathering.card_turned_back");
    }
}
