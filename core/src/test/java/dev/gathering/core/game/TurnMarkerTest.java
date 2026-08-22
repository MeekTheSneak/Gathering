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
 * <p>It exists so four people can agree where they are without saying it out loud every thirty
 * seconds. The mod never advances it, never checks that an action suits the phase, and never
 * stops anybody doing anything in any phase - so most of what is worth testing here is what it
 * refuses to do.
 */
class TurnMarkerTest {

    @Test
    @DisplayName("a game starts on turn one, untap, with the first seat")
    void gamesStartAtTheBeginning() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        assertThat(session.state().turn().turnNumber()).isEqualTo(1);
        assertThat(session.state().turn().phase()).isEqualTo(Phase.UNTAP);
        assertThat(session.state().turn().activeSeat()).isEqualTo(GameFixtures.ALICE);
    }

    @Test
    @DisplayName("phases advance one at a time and wrap round the turn")
    void phasesWrap() {
        assertThat(Phase.UNTAP.next()).isEqualTo(Phase.UPKEEP);
        assertThat(Phase.CLEANUP.next()).isEqualTo(Phase.UNTAP);
    }

    @Test
    @DisplayName("passing the turn hands it on and starts the new one at untap")
    void passingResetsThePhase() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.PhaseSet(GameFixtures.ALICE, Phase.END_STEP));

        session.submit(new GameEvent.TurnPassed(GameFixtures.ALICE, GameFixtures.BOB));

        assertThat(session.state().turn().activeSeat()).isEqualTo(GameFixtures.BOB);
        assertThat(session.state().turn().phase()).isEqualTo(Phase.UNTAP);
        assertThat(session.state().turn().turnNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("anybody may move the marker, because it is a marker and not a referee")
    void nobodyOwnsTheMarker() {
        // Somebody else advancing your phase is how a paper table works: whoever is nearest
        // the marker moves it. The log says who, which is the whole mechanism.
        GameSession session = GameFixtures.twoPlayerTable(10);

        GameSession.Result result =
                session.submit(new GameEvent.PhaseSet(GameFixtures.BOB, Phase.DECLARE_ATTACKERS));

        assertThat(result.isAccepted()).isTrue();
        assertThat(session.state().turn().phase()).isEqualTo(Phase.DECLARE_ATTACKERS);
    }

    @Test
    @DisplayName("the phase does not stop anybody doing anything")
    void thereIsNoEnforcement() {
        // Drawing six cards during your opponent's combat is a misplay, not an error. The mod
        // moves cards and the group decides what any of it means.
        GameSession session = GameFixtures.twoPlayerTable(20);
        session.submit(new GameEvent.PhaseSet(GameFixtures.ALICE, Phase.DECLARE_BLOCKERS));

        GameSession.Result result = session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 6));

        assertThat(result.isAccepted()).isTrue();
        assertThat(session.state().count(ZoneRef.of(GameFixtures.BOB, Zone.HAND))).isEqualTo(6);
    }

    @Test
    @DisplayName("everybody sees the same marker, including a spectator")
    void theMarkerIsEntirelyPublic() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.TurnPassed(GameFixtures.ALICE, GameFixtures.BOB));
        session.submit(new GameEvent.PhaseSet(GameFixtures.BOB, Phase.POSTCOMBAT_MAIN));

        for (GameView view : VisibilityRules.allViews(session.state()).values()) {
            assertThat(view.turn().activeSeat()).isEqualTo(GameFixtures.BOB);
            assertThat(view.turn().phase()).isEqualTo(Phase.POSTCOMBAT_MAIN);
            assertThat(view.turn().turnNumber()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("every phase has a name somebody could read on a bar")
    void everyPhaseIsNameable() {
        for (Phase phase : Phase.values()) {
            assertThat(phase.displayName()).doesNotContain("_").isNotBlank();
        }
    }
}
