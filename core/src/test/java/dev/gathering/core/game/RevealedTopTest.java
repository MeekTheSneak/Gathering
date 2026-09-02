package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The revealed top of a library, and the moves that must take it back down.
 * <p>The count is positional - "the first N of this library are face up to the room" - and
 * the visibility rules hand exactly that many identities to everybody. Any move that changes
 * what those first N are has to clear the count, or the window slides down onto a card
 * nobody ever revealed and the whole table sees it. Mill and shuffle always did; these are
 * the ones that did not.
 */
class RevealedTopTest {

    @Test
    @DisplayName("exiling off the top takes the revealed window with it")
    void exilingClearsTheRevealedTop() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 3));

        session.submit(new GameEvent.LibraryExiled(GameFixtures.ALICE, GameFixtures.ALICE, 1));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isZero();
    }

    @Test
    @DisplayName("drawing takes the revealed window with it")
    void drawingClearsTheRevealedTop() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 3));

        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isZero();
    }

    @Test
    @DisplayName("taking a revealed card out of the library closes the window - the cascade flow")
    void movingACardOutOfTheLibraryClearsTheRevealedTop() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 3));
        CardInstanceId hit = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(2);

        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, hit,
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.TOP));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isZero();
    }

    @Test
    @DisplayName("a surveil decision rearranges the top, so the window closes")
    void surveilClearsTheRevealedTop() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 2));
        CardInstanceId top = GameFixtures.topOfLibrary(session, GameFixtures.ALICE);

        session.submit(new GameEvent.Surveiled(
                GameFixtures.ALICE, GameFixtures.ALICE, List.of(), List.of(top)));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isZero();
    }

    @Test
    @DisplayName("a scry decision rearranges the top, so the window closes")
    void reorderClearsTheRevealedTop() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 2));
        CardInstanceId top = GameFixtures.topOfLibrary(session, GameFixtures.ALICE);

        session.submit(new GameEvent.LibraryReordered(
                GameFixtures.ALICE, GameFixtures.ALICE, List.of(top), List.of()));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isZero();
    }

    @Test
    @DisplayName("moving a battlefield card around does not touch someone's revealed top")
    void anUnrelatedMoveLeavesTheWindowAlone() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId played = session.state().contents(GameFixtures.ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, played,
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.TOP));
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 2));

        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, played,
                ZoneRef.of(GameFixtures.BOB, Zone.BATTLEFIELD), Placement.TOP));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isEqualTo(2);
    }
}
