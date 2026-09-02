package dev.gathering.core.game;

import static dev.gathering.core.game.GameFixtures.ALICE;
import static dev.gathering.core.game.GameFixtures.BOB;
import static dev.gathering.core.game.GameFixtures.twoPlayerTable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.EventCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Moving the top few cards of a library somewhere, without anybody naming them.
 * <p>Mill and exile are the same shape and share a fold, so they are checked together: the
 * thing worth guarding is that they stay two verbs going to two piles rather than one verb
 * that drifted into deciding where it lands.
 */
@DisplayName("The top of a library")
class TopOfLibraryTest {

    @Test
    @DisplayName("exiling takes cards off the top, in order")
    void exileTakesTheTop() {
        GameSession session = twoPlayerTable(8);
        List<CardInstanceId> library = session.state().contents(ALICE, Zone.LIBRARY);

        session.submit(new GameEvent.LibraryExiled(ALICE, ALICE, 3));

        // Top card first, so the third one down ends up on top of the exile pile.
        assertThat(session.state().contents(ALICE, Zone.EXILE))
                .containsExactly(library.get(2), library.get(1), library.get(0));
        assertThat(session.state().contents(ALICE, Zone.LIBRARY))
                .isEqualTo(library.subList(3, library.size()));
        assertThat(session.state().contents(ALICE, Zone.GRAVEYARD)).isEmpty();
    }

    @Test
    @DisplayName("milling still goes to the graveyard")
    void millStillGoesToTheGraveyard() {
        GameSession session = twoPlayerTable(8);

        session.submit(new GameEvent.LibraryMilled(ALICE, ALICE, 2));

        assertThat(session.state().contents(ALICE, Zone.GRAVEYARD)).hasSize(2);
        assertThat(session.state().contents(ALICE, Zone.EXILE)).isEmpty();
    }

    @Test
    @DisplayName("asking for more than is left exiles what is left")
    void aShortLibraryRunsOut() {
        GameSession session = twoPlayerTable(3);

        session.submit(new GameEvent.LibraryExiled(ALICE, ALICE, 40));

        assertThat(session.state().contents(ALICE, Zone.LIBRARY)).isEmpty();
        assertThat(session.state().contents(ALICE, Zone.EXILE)).hasSize(3);
    }

    @Test
    @DisplayName("anybody may exile off an opponent's library, because nobody looks")
    void anybodyMayDoItToAnybody() {
        // The same reason milling an opponent is allowed: the cards land in a pile the whole
        // table can already read, so doing it teaches the actor nothing they were not going
        // to be told anyway.
        GameSession session = twoPlayerTable(8);

        GameSession.Result result = session.submit(new GameEvent.LibraryExiled(ALICE, BOB, 2));

        assertThat(result).isInstanceOf(GameSession.Result.Accepted.class);
        assertThat(session.state().contents(BOB, Zone.EXILE)).hasSize(2);
    }

    @Test
    @DisplayName("undoing it alone is refused, because the table has read those cards")
    void undoStopsAtTheInformationBoundary() {
        // The same boundary a mill sits behind. Three cards went face up into a pile everybody
        // may read, and quietly putting them back on top would leave the actor knowing an
        // order nobody else does - so under the shipped undo mode this needs the table to
        // agree, and asking with nobody's agreement is refused.
        GameSession session = twoPlayerTable(8);

        session.submit(new GameEvent.LibraryExiled(ALICE, ALICE, 3));
        GameSession.Result result = session.undo(ALICE, 1, List.of());

        assertThat(result).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.state().contents(ALICE, Zone.EXILE)).hasSize(3);
    }

    @Test
    @DisplayName("with the table's agreement it puts them back on top in order")
    void unanimousUndoPutsThemBack() {
        GameSession session = twoPlayerTable(8);
        List<CardInstanceId> library = session.state().contents(ALICE, Zone.LIBRARY);

        session.submit(new GameEvent.LibraryExiled(ALICE, ALICE, 3));
        session.undo(ALICE, 1, List.of(ALICE, BOB));

        assertThat(session.state().contents(ALICE, Zone.LIBRARY)).isEqualTo(library);
        assertThat(session.state().contents(ALICE, Zone.EXILE)).isEmpty();
    }

    @Test
    @DisplayName("it survives a round trip through the codec")
    void roundTripsThroughTheCodec() throws Exception {
        GameEvent.LibraryExiled event = new GameEvent.LibraryExiled(ALICE, BOB, 4);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            EventCodec.write(out, event);
        }
        GameEvent back;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = EventCodec.read(in);
        }

        assertThat(back).isEqualTo(event);
    }
}
