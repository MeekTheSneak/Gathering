package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Looking through a library, which is the only thing that makes one anything but a number.
 *
 * <p>A library is a count to everybody, its owner included. That rule has exactly one
 * exception and this is it: a search, a scry or a surveil opens the library to the one seat
 * doing it, until something closes it again. So every one of those "until"s is a leak if it
 * does not fire - a search left open keeps sending the searcher every card that enters that
 * library for the rest of the game - and each of them gets its own case here.
 */
class LibraryPeekTest {

    @Nested
    @DisplayName("opening")
    class Opening {

        @Test
        @DisplayName("searching opens the whole library to the searcher")
        void aSearchOpensEverything() {
            GameSession session = GameFixtures.twoPlayerTable(20);

            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE))
                    .isEqualTo(librarySize(session, GameFixtures.ALICE));
        }

        @Test
        @DisplayName("and to nobody else at the table")
        void aSearchOpensNothingToAnyoneElse() {
            GameSession session = GameFixtures.twoPlayerTable(20);

            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isPositive();
            assertThat(openTo(session, GameFixtures.BOB, GameFixtures.ALICE)).isZero();
            assertThat(spectatorSees(session, GameFixtures.ALICE)).isZero();
        }

        @Test
        @DisplayName("searching somebody else's library is refused, so it never opens at all")
        void nobodySearchesAnotherSeatsLibrary() {
            // The one thing the mod says no to is an action that would let the actor see what
            // they are not entitled to, and searching means looking. Effects that really do
            // read an opponent's deck are rare enough to be worth doing out loud - that player
            // searches and shows - which costs a sentence and cannot leak a library.
            GameSession session = GameFixtures.twoPlayerTable(20);

            GameSession.Result result =
                    session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.BOB));

            assertThat(result.isAccepted()).isFalse();
            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.BOB)).isZero();
        }

        @Test
        @DisplayName("a scry opens exactly as far down as it says and no further")
        void aLookOpensOnlyItsOwnDepth() {
            GameSession session = GameFixtures.twoPlayerTable(20);

            session.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 3));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isEqualTo(3);
        }

        @Test
        @DisplayName("looking deeper than the library is deep shows the library, not an error")
        void aLookPastTheBottomIsHarmless() {
            GameSession session = GameFixtures.twoPlayerTable(4);

            session.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 40));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE))
                    .isEqualTo(librarySize(session, GameFixtures.ALICE));
        }

        @Test
        @DisplayName("a seat is doing one thing at a time, so a later look replaces an earlier one")
        void aSecondLookReplacesTheFirst() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));

            // Finishing a search and then scrying two must narrow what is open, not leave the
            // whole library open behind the scry.
            session.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 2));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isEqualTo(2);
        }

        @Test
        @DisplayName("the count stays truthful while the library is open")
        void countsDoNotChangeWhenALibraryOpens() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            int before = librarySize(session, GameFixtures.ALICE);

            session.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 2));

            for (GameView view : VisibilityRules.allViews(session.state()).values()) {
                assertThat(view.seat(GameFixtures.ALICE).zone(Zone.LIBRARY).count()).isEqualTo(before);
            }
        }
    }

    @Nested
    @DisplayName("closing")
    class Closing {

        @Test
        @DisplayName("closing it closes it")
        void closingClosesIt() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));

            session.submit(new GameEvent.LibraryClosed(GameFixtures.ALICE));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isZero();
        }

        @Test
        @DisplayName("closing one seat's look leaves another seat's alone")
        void closingIsPerSeat() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));
            session.submit(new GameEvent.LibrarySearched(GameFixtures.BOB, GameFixtures.BOB));

            session.submit(new GameEvent.LibraryClosed(GameFixtures.ALICE));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isZero();
            assertThat(openTo(session, GameFixtures.BOB, GameFixtures.BOB)).isPositive();
        }

        @Test
        @DisplayName("shuffling closes it, because what was in front of you is not there any more")
        void shufflingCloses() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));

            // Shuffled by somebody else, which is legal Magic and reveals nothing to them.
            // It still has to close Alice's search: the order she was reading is gone.
            session.submit(new GameEvent.LibraryShuffled(GameFixtures.BOB, GameFixtures.ALICE));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isZero();
        }

        @Test
        @DisplayName("deciding a scry closes it, because the deciding is the end of the looking")
        void scryingCloses() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 2));
            List<CardInstanceId> top = topOfLibrary(session, GameFixtures.ALICE, 2);

            session.submit(new GameEvent.LibraryReordered(
                    GameFixtures.ALICE, GameFixtures.ALICE, List.of(top.get(0)), List.of(top.get(1))));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isZero();
        }

        @Test
        @DisplayName("resolving a surveil closes it too")
        void surveillingCloses() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibraryLooked(GameFixtures.ALICE, GameFixtures.ALICE, 2));
            List<CardInstanceId> top = topOfLibrary(session, GameFixtures.ALICE, 2);

            session.submit(new GameEvent.Surveiled(
                    GameFixtures.ALICE, GameFixtures.ALICE, List.of(top.get(0)), List.of(top.get(1))));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isZero();
        }

        @Test
        @DisplayName("taking a card out does not close it, because a search is more than one card")
        void takingACardKeepsTheSearchOpen() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));
            CardInstanceId found = topOfLibrary(session, GameFixtures.ALICE, 1).get(0);

            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, found,
                    ZoneRef.of(GameFixtures.ALICE, Zone.HAND), Placement.BOTTOM));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE))
                    .isEqualTo(librarySize(session, GameFixtures.ALICE));
        }
    }

    @Nested
    @DisplayName("undo")
    class Undo {

        @Test
        @DisplayName("undoing a search closes the library, because state is the fold of the log")
        void undoingASearchClosesIt() {
            GameSession session = GameFixtures.twoPlayerTable(20);
            session.submit(new GameEvent.LibrarySearched(GameFixtures.ALICE, GameFixtures.ALICE));

            // A search reveals, so rewinding one always needs everybody - which is right: the
            // searcher has seen the library and cannot un-see it. What the rewind buys is that
            // the library stops being sent to them from here on.
            session.undo(GameFixtures.ALICE, 1, List.of(GameFixtures.ALICE, GameFixtures.BOB));

            assertThat(openTo(session, GameFixtures.ALICE, GameFixtures.ALICE)).isZero();
        }
    }

    // ------------------------------------------------------------- fixtures

    /** How many of a library's cards actually reach a seat's client, not what state claims. */
    private static int openTo(GameSession session, SeatId viewer, SeatId library) {
        GameView view = VisibilityRules.viewFor(session.state(), Viewer.seat(viewer));
        return view.seat(library).zone(Zone.LIBRARY).cards().size();
    }

    private static int spectatorSees(GameSession session, SeatId library) {
        return VisibilityRules.viewFor(session.state(), Viewer.SPECTATOR)
                .seat(library).zone(Zone.LIBRARY).cards().size();
    }

    private static int librarySize(GameSession session, SeatId seat) {
        return session.state().count(ZoneRef.of(seat, Zone.LIBRARY));
    }

    private static List<CardInstanceId> topOfLibrary(GameSession session, SeatId seat, int count) {
        return session.state().contents(seat, Zone.LIBRARY).subList(0, count);
    }
}
