package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.ZoneView;
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

    /**
     * The one that matters, and the one the counter could never catch.
     * <p>Every other case here asserts {@code revealedIn == 0}, which is the mod's own bookkeeping
     * answering a question about itself. This asks the only question that means anything: which
     * card identities does a spectator actually receive. A window whose count is right and whose
     * contents are somebody else's cards is the leak, and it reads as correct from the counter.
     * <p>Putting the revealed top card on the bottom is how cascade, Bolas's Citadel and every
     * reveal-until effect resolve, and the card menu offers it. Done repeatedly it used to walk the
     * window down the whole shuffled library, handing every opponent and every spectator the order
     * of it.
     */
    @Test
    @DisplayName("putting the revealed card on the bottom shows nobody the next one")
    void movingWithinTheLibraryShowsNobodyANewCard() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 3));

        List<CardInstanceId> revealed =
                List.copyOf(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY).subList(0, 3));
        java.util.Set<CardInstanceId> mayBeSeen = new java.util.LinkedHashSet<>(revealed);

        // Seven times over, which is enough to walk a ten-card library most of the way round.
        for (int round = 0; round < 7; round++) {
            CardInstanceId top = session.state().contents(GameFixtures.ALICE, Zone.LIBRARY).get(0);
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, top,
                    ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY), new Placement.Bottom()));

            for (Viewer viewer : List.of(Viewer.seat(GameFixtures.BOB), Viewer.SPECTATOR)) {
                GameView view = VisibilityRules.viewFor(session.state(), viewer);
                ZoneView library = view.seat(GameFixtures.ALICE)
                        .zone(Zone.LIBRARY);
                for (CardView card : library.cards()) {
                    if (card instanceof CardView.Visible seen) {
                        assertThat(seen.id())
                                .withFailMessage("round %d showed %s a card nobody revealed: %s",
                                        round, viewer, seen.id())
                                .isIn(mayBeSeen);
                    }
                }
            }
        }
    }

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
    @DisplayName("a card put on top of a revealed library closes the window")
    void arrivingOnTopClosesTheWindow() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId inHand = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 2));

        // From the hand, where nobody had seen it, onto the top - where the window would have
        // handed its identity to the room.
        session.submit(new GameEvent.CardMoved(
                GameFixtures.ALICE, inHand, ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY), Placement.TOP));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isZero();
    }

    @Test
    @DisplayName("a card tucked under a revealed library leaves the window where it was")
    void arrivingAtTheBottomLeavesTheWindow() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId inHand = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 2));

        session.submit(new GameEvent.CardMoved(
                GameFixtures.ALICE, inHand, ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY), Placement.BOTTOM));

        assertThat(session.state().revealedIn(GameFixtures.ALICE)).isEqualTo(2);
    }

    @Test
    @DisplayName("a whole pile put on top of a revealed library closes the window too")
    void aPileArrivingOnTopClosesTheWindow() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 2));
        session.submit(new GameEvent.LibraryRevealed(GameFixtures.ALICE, GameFixtures.ALICE, 2));

        session.submit(new GameEvent.ZoneMoved(GameFixtures.ALICE, GameFixtures.ALICE, Zone.HAND,
                ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY), Placement.TOP));

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
