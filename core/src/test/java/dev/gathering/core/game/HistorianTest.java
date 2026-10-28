package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.ZoneView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one viewer that is entitled to everything.
 * <p>A replay is this mod's disclosure moment: during play nothing hidden is ever sent, and
 * once the game is over there is nothing left to protect. That makes a viewer who sees
 * everything both necessary and the single most dangerous type in the codebase, so what these
 * check is the fence around it rather than what it can see.
 */
class HistorianTest {

    @Test
    @DisplayName("a historian reads a hand that a spectator only gets a count of")
    void aHistorianSeesHands() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 3));

        ZoneView toASpectator = VisibilityRules
                .viewFor(session.state(), Viewer.SPECTATOR)
                .seat(GameFixtures.ALICE).zone(Zone.HAND);
        ZoneView toAHistorian = VisibilityRules
                .viewFor(session.state(), Viewer.HISTORIAN)
                .seat(GameFixtures.ALICE).zone(Zone.HAND);

        assertThat(toASpectator.count()).isEqualTo(3);
        assertThat(toASpectator.cards()).isEmpty();
        assertThat(toAHistorian.count()).isEqualTo(3);
        assertThat(toAHistorian.cards()).hasSize(3);
    }

    @Test
    @DisplayName("a historian reads the library, which not even its owner may during play")
    void aHistorianSeesLibraries() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        assertThat(VisibilityRules.viewFor(session.state(), Viewer.seat(GameFixtures.ALICE))
                .seat(GameFixtures.ALICE).zone(Zone.LIBRARY).cards()).isEmpty();
        assertThat(VisibilityRules.viewFor(session.state(), Viewer.HISTORIAN)
                .seat(GameFixtures.ALICE).zone(Zone.LIBRARY).cards()).isNotEmpty();
    }

    /**
     * The fence, and the reason it is a test rather than a comment.
     * <p>{@code allViews} is what the invariant suites walk and what a live table hands out.
     * A historian appearing in it would not fail anything loudly - it would quietly become one
     * of the boards a running game sends, which is the exact failure the whole visibility
     * design exists to prevent.
     */
    @Test
    @DisplayName("no historian is ever among the views a live table hands out")
    void allViewsNeverIncludesAHistorian() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        for (Viewer viewer : VisibilityRules.allViews(session.state()).keySet()) {
            assertThat(viewer.seesEverything())
                    .describedAs("%s is entitled to hidden information and is in allViews", viewer)
                    .isFalse();
        }
        for (GameView view : VisibilityRules.allViews(session.state()).values()) {
            assertThat(view.viewer().seesEverything()).isFalse();
        }
    }

    @Test
    @DisplayName("only a historian sees everything")
    void nothingElseIsEntitled() {
        assertThat(Viewer.HISTORIAN.seesEverything()).isTrue();
        assertThat(Viewer.SPECTATOR.seesEverything()).isFalse();
        assertThat(Viewer.seat(GameFixtures.ALICE).seesEverything()).isFalse();
    }
}
