package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.ViewCodec;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A table cannot be grown until nobody can be sent it.
 * <p>The board goes out as one payload with a megabyte bound, and that bound throws rather
 * than truncating - so a board past it is a board that can never reach anybody again. Counter
 * names are the way to get there: a name is a map key, so a new one is a new entry rather
 * than a replaced one, and counters are saved with the session, which means restarting the
 * server does not undo it. That is a table anybody sitting at it can permanently destroy,
 * from an ordinary client with an unusual string in it.
 * <p>The numbers below are written out rather than read from {@link CounterName}. A bound
 * checked against itself is not checked: raise the constant and an assertion phrased in terms
 * of it simply moves, which is how a test comes to pass on code that has stopped working.
 */
class CounterBoundsTest {

    /** As big as one board may be on the wire, from TableViewPayload. */
    private static final int PAYLOAD_BOUND = 1 << 20;

    /** What a counter is actually called: "+1/+1", "loyalty", "charge". */
    private static final int SENSIBLE_NAME = 32;

    /** More kinds than anybody puts on one card, and still nowhere near the wire. */
    private static final int SENSIBLE_KINDS = 32;

    @Test
    @DisplayName("a counter name is cut where it enters, however long it arrives")
    void aCounterNameIsCutWhereItEnters() {
        assertThat(new GameEvent.CounterChanged(
                GameFixtures.ALICE, CardInstanceId.of(1), "y".repeat(20_000), 1).counter())
                .hasSizeLessThanOrEqualTo(SENSIBLE_NAME);
        assertThat(new GameEvent.SeatCounterChanged(
                GameFixtures.ALICE, GameFixtures.ALICE, "y".repeat(20_000), 1).counter())
                .hasSizeLessThanOrEqualTo(SENSIBLE_NAME);
    }

    @Test
    @DisplayName("one card carries only so many kinds of counter")
    void oneCardCarriesOnlySoManyKinds() {
        CardInstance card = CardInstance.faceUp(
                CardInstanceId.of(1), GameFixtures.card(1), GameFixtures.ALICE);
        CardInstance loaded = card;
        for (int kind = 0; kind < 5_000; kind++) {
            loaded = loaded.withCounter("kind" + kind, 1);
        }
        assertThat(loaded.counters()).hasSizeLessThanOrEqualTo(SENSIBLE_KINDS);

        // And one it already carries is never refused, however full it is.
        assertThat(loaded.withCounter("kind0", 4).counter("kind0")).isEqualTo(5);

        SeatState seat = SeatState.startingAt(GameFixtures.ALICE, 40);
        SeatState piled = seat;
        for (int kind = 0; kind < 5_000; kind++) {
            piled = piled.withCounter("kind" + kind, 1);
        }
        assertThat(piled.counters()).hasSizeLessThanOrEqualTo(SENSIBLE_KINDS);
    }

    /**
     * And the board that comes out of all of it still fits on the wire.
     * <p>The two bounds above are the mechanism; this is the thing they are for, stated where
     * somebody changing either of them will see it.
     */
    @Test
    @DisplayName("a flood of counters leaves a board that can still be sent")
    void aFloodOfCountersLeavesABoardThatCanStillBeSent() throws IOException {
        GameSession session = GameFixtures.twoPlayerTable(40);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId card = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));

        for (int attempt = 0; attempt < 400; attempt++) {
            session.submit(new GameEvent.CounterChanged(
                    GameFixtures.ALICE, card, "counter number " + attempt, 1));
            session.submit(new GameEvent.SeatCounterChanged(
                    GameFixtures.ALICE, GameFixtures.ALICE, "counter number " + attempt, 1));
        }

        for (GameView view : VisibilityRules.allViews(session.state()).values()) {
            assertThat(ViewCodec.write(view).length)
                    .describedAs("a board anybody at the table could grow past the wire bound")
                    .isLessThan(PAYLOAD_BOUND);
        }
    }
}
