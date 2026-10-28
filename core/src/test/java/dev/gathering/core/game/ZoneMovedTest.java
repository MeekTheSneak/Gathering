package dev.gathering.core.game;

import static dev.gathering.core.game.GameFixtures.ALICE;
import static dev.gathering.core.game.GameFixtures.BOB;
import static dev.gathering.core.game.GameFixtures.twoPlayerTable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Emptying a whole zone somewhere else, which is the long hold on a pile.
 * <p>Its own verb because a client may not name the cards in a hidden zone, so the only way
 * to ask for a library to go anywhere is to ask for the library rather than for its contents.
 */
class ZoneMovedTest {

    @Test
    @DisplayName("a whole graveyard goes back into the library, keeping its order")
    void aWholeGraveyardGoesBack() {
        GameSession session = twoPlayerTable(12);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 3));
        List<CardInstanceId> hand = session.state().contents(ALICE, Zone.HAND);
        for (CardInstanceId card : hand) {
            session.submit(new GameEvent.CardMoved(
                    ALICE, card, ZoneRef.of(ALICE, Zone.GRAVEYARD), Placement.TOP));
        }
        List<CardInstanceId> graveyard = session.state().contents(ALICE, Zone.GRAVEYARD);
        assertThat(graveyard).hasSize(3);
        int libraryBefore = session.state().contents(ALICE, Zone.LIBRARY).size();

        // Submitted as it arrives from a client - decoded, not the object that was built
        // here. A placement compared by identity rather than by value passes every test that
        // hands the fold its own constant and fails every real move off the wire.
        session.submit(fromTheWire(new GameEvent.ZoneMoved(
                ALICE, ALICE, Zone.GRAVEYARD, ZoneRef.of(ALICE, Zone.LIBRARY), Placement.TOP)));

        assertThat(session.state().contents(ALICE, Zone.GRAVEYARD)).isEmpty();
        List<CardInstanceId> library = session.state().contents(ALICE, Zone.LIBRARY);
        assertThat(library).hasSize(libraryBefore + 3);
        // Top of the graveyard is top of the library: the pile went over as a pile.
        assertThat(library.subList(0, 3)).isEqualTo(graveyard);
    }

    @Test
    @DisplayName("a whole library goes to the graveyard without a card being named")
    void aWholeLibraryMillsOut() {
        GameSession session = twoPlayerTable(9);
        session.submit(new GameEvent.ZoneMoved(
                ALICE, ALICE, Zone.LIBRARY, ZoneRef.of(ALICE, Zone.GRAVEYARD), Placement.TOP));

        assertThat(session.state().contents(ALICE, Zone.LIBRARY)).isEmpty();
        assertThat(session.state().contents(ALICE, Zone.GRAVEYARD)).hasSize(9);
    }

    @Test
    @DisplayName("moving a zone onto itself leaves it exactly as it was")
    void ontoItselfChangesNothing() {
        GameSession session = twoPlayerTable(6);
        List<CardInstanceId> before = session.state().contents(ALICE, Zone.LIBRARY);

        session.submit(new GameEvent.ZoneMoved(
                ALICE, ALICE, Zone.LIBRARY, ZoneRef.of(ALICE, Zone.LIBRARY), Placement.TOP));

        assertThat(session.state().contents(ALICE, Zone.LIBRARY)).isEqualTo(before);
    }

    @Test
    @DisplayName("an empty zone moves nothing and is not an error")
    void anEmptyZoneMovesNothing() {
        GameSession session = twoPlayerTable(6);
        GameSession.Result result = session.submit(new GameEvent.ZoneMoved(
                ALICE, ALICE, Zone.EXILE, ZoneRef.of(ALICE, Zone.GRAVEYARD), Placement.TOP));

        assertThat(result).isInstanceOf(GameSession.Result.Accepted.class);
        assertThat(session.state().contents(ALICE, Zone.GRAVEYARD)).isEmpty();
    }

    @Test
    @DisplayName("nobody empties somebody else's hidden zone")
    void onlyTheOwnerEmptiesAHiddenZone() {
        GameSession session = twoPlayerTable(6);
        GameSession.Result result = session.submit(new GameEvent.ZoneMoved(
                ALICE, BOB, Zone.LIBRARY, ZoneRef.of(BOB, Zone.GRAVEYARD), Placement.TOP));

        assertThat(result).isInstanceOf(GameSession.Result.Rejected.class);
        assertThat(session.state().contents(BOB, Zone.LIBRARY)).hasSize(6);
    }

    @Test
    @DisplayName("anybody may move a public pile, because a graveyard is public")
    void anybodyMovesAPublicPile() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.LibraryMilled(BOB, BOB, 2));
        GameSession.Result result = session.submit(new GameEvent.ZoneMoved(
                ALICE, BOB, Zone.GRAVEYARD, ZoneRef.of(BOB, Zone.EXILE), Placement.TOP));

        assertThat(result).isInstanceOf(GameSession.Result.Accepted.class);
        assertThat(session.state().contents(BOB, Zone.EXILE)).hasSize(2);
    }

    @Test
    @DisplayName("undoing a zone move puts every card back where it was")
    void undoPutsThePileBack() {
        GameSession session = twoPlayerTable(8);
        session.submit(new GameEvent.LibraryMilled(ALICE, ALICE, 4));
        List<CardInstanceId> graveyard = session.state().contents(ALICE, Zone.GRAVEYARD);

        session.submit(new GameEvent.ZoneMoved(
                ALICE, ALICE, Zone.GRAVEYARD, ZoneRef.of(ALICE, Zone.EXILE), Placement.TOP));
        assertThat(session.state().contents(ALICE, Zone.GRAVEYARD)).isEmpty();

        session.undo(ALICE, 1, List.of());
        assertThat(session.state().contents(ALICE, Zone.GRAVEYARD)).isEqualTo(graveyard);
        assertThat(session.state().contents(ALICE, Zone.EXILE)).isEmpty();
    }

    /** The same event, put on the wire and read back, which is how every real one arrives. */
    private static GameEvent fromTheWire(GameEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                dev.gathering.core.game.persistence.EventCodec.write(out, event);
            }
            try (DataInputStream in = new DataInputStream(
                    new ByteArrayInputStream(bytes.toByteArray()))) {
                return dev.gathering.core.game.persistence.EventCodec.read(in);
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("a zone move survives a round trip through the codec")
    void roundTripsThroughTheCodec() throws Exception {
        GameEvent.ZoneMoved event = new GameEvent.ZoneMoved(
                ALICE, BOB, Zone.GRAVEYARD, ZoneRef.of(BOB, Zone.LIBRARY), Placement.BOTTOM);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            dev.gathering.core.game.persistence.EventCodec.write(out, event);
        }
        GameEvent back;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = dev.gathering.core.game.persistence.EventCodec.read(in);
        }
        assertThat(back).isEqualTo(event);
    }

    @Test
    @DisplayName("the log line names the pile and how many, never which cards")
    void theLogNamesThePileNotTheCards() {
        GameSession session = twoPlayerTable(8);
        session.submit(new GameEvent.LibraryMilled(ALICE, ALICE, 4));
        session.submit(new GameEvent.ZoneMoved(
                ALICE, ALICE, Zone.GRAVEYARD, ZoneRef.of(ALICE, Zone.LIBRARY), Placement.TOP));

        var line = session.log().get(session.log().size() - 1);
        assertThat(line.key()).isEqualTo("log.gathering.zone_moved_own");
        assertThat(line.args()).noneMatch(arg -> arg instanceof dev.gathering.core.game.event.LogArg.Card);
        assertThat(line.args()).contains(new dev.gathering.core.game.event.LogArg.Amount(4));
    }
}
