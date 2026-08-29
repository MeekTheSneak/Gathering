package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shared turn marker, which enforces nothing.
 *
 * <p>It exists so four people can agree whose turn it is without saying it out loud every
 * thirty seconds. The mod never hands it on by itself and never stops anybody doing anything
 * on anybody's turn - so most of what is worth testing here is what it refuses to do.
 *
 * <p>There was a phase beside it once, advanced by hand, and these tests were mostly about
 * that. It is gone: nothing ever read it and no action was ever checked against it, so it was
 * a label the table maintained for the mod's benefit rather than its own.
 */
class TurnMarkerTest {

    @Test
    @DisplayName("a game starts on turn one, with the first seat")
    void gamesStartAtTheBeginning() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        assertThat(session.state().turn().turnNumber()).isEqualTo(1);
        assertThat(session.state().turn().activeSeat()).isEqualTo(GameFixtures.ALICE);
    }

    @Test
    @DisplayName("passing the turn hands it on and counts the turn")
    void passingHandsItOn() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        session.submit(new GameEvent.TurnPassed(GameFixtures.ALICE, GameFixtures.BOB));

        assertThat(session.state().turn().activeSeat()).isEqualTo(GameFixtures.BOB);
        assertThat(session.state().turn().turnNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("anybody may move the marker, because it is a marker and not a referee")
    void nobodyOwnsTheMarker() {
        // Somebody else passing your turn is how a paper table works: whoever is nearest the
        // marker moves it, usually because you have gone to get a drink. The log says who,
        // which is the whole mechanism.
        GameSession session = GameFixtures.twoPlayerTable(10);

        GameSession.Result result =
                session.submit(new GameEvent.TurnPassed(GameFixtures.BOB, GameFixtures.BOB));

        assertThat(result.isAccepted()).isTrue();
        assertThat(session.state().turn().activeSeat()).isEqualTo(GameFixtures.BOB);
    }

    @Test
    @DisplayName("whose turn it is does not stop anybody doing anything")
    void thereIsNoEnforcement() {
        // Drawing six cards during somebody else's turn is a misplay, not an error. The mod
        // moves cards and the group decides what any of it means.
        GameSession session = GameFixtures.twoPlayerTable(20);
        session.submit(new GameEvent.TurnPassed(GameFixtures.ALICE, GameFixtures.ALICE));

        GameSession.Result result = session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 6));

        assertThat(result.isAccepted()).isTrue();
        assertThat(session.state().count(ZoneRef.of(GameFixtures.BOB, Zone.HAND))).isEqualTo(6);
    }

    @Test
    @DisplayName("passing does not untap the seat receiving the turn")
    void passingUntapsNobody() {
        // The one piece of turn structure that was ever automated, and it was reported as a
        // bug by the first table to play with it: untapping somebody's board is a game action
        // and this mod does not decide that game actions happened. It is one key for the
        // player whose board it is.
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 1));
        CardInstanceId card = session.state().contents(GameFixtures.BOB, Zone.HAND).get(0);
        session.submit(new GameEvent.CardMoved(GameFixtures.BOB, card,
                ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.at(TablePosition.ORIGIN)));
        session.submit(new GameEvent.CardTapSet(GameFixtures.BOB, card, true));

        session.submit(new GameEvent.TurnPassed(GameFixtures.ALICE, GameFixtures.BOB));

        assertThat(session.state().requireCard(card).tapped()).isTrue();
    }

    @Test
    @DisplayName("everybody sees the same marker, including a spectator")
    void theMarkerIsEntirelyPublic() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.TurnPassed(GameFixtures.ALICE, GameFixtures.BOB));

        for (GameView view : VisibilityRules.allViews(session.state()).values()) {
            assertThat(view.turn().activeSeat()).isEqualTo(GameFixtures.BOB);
            assertThat(view.turn().turnNumber()).isEqualTo(2);
        }
    }
}
