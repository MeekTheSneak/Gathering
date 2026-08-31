package dev.gathering.core.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.Sleeve;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A deck's sleeves on their way into the log, and a log written before there were any.
 *
 * <p>Adding a field to a stored event is the one change that can put somebody's saved game out
 * of reach, so the older shape stays readable and this is what says so.
 */
class SleevedLogTest {

    private static final SeatId ALICE = new SeatId(0);
    private static final UUID A_CARD = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    @Test
    @DisplayName("a deck's sleeves survive being written and read back")
    void sleevesSurviveTheLog() throws IOException {
        GameEvent.DeckLoaded loaded = new GameEvent.DeckLoaded(
                ALICE, List.of(CardIdentity.ofPrinting(A_CARD)), List.of(), Sleeve.NETHER_STAR);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            EventCodec.write(out, loaded);
        }
        GameEvent back = EventCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertThat(back).isInstanceOf(GameEvent.DeckLoaded.class);
        assertThat(((GameEvent.DeckLoaded) back).sleeve()).isEqualTo(Sleeve.NETHER_STAR);
    }

    @Test
    @DisplayName("a deck loaded before sleeves existed reads as the ordinary back")
    void anOlderLogStillOpens() throws IOException {
        // Written the way the previous build wrote it: the tag, the seat, the library and the
        // commanders, and nothing after them. Assembled by hand rather than by an old codec,
        // because the old codec is what was replaced.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("DeckLoaded");
            out.writeInt(ALICE.index());
            out.writeInt(0);
            out.writeInt(0);
            // And a second event straight afterwards, which is the part that matters: if the
            // reader looked for a sleeve that was never written it would eat this one's tag
            // and everything after it would be nonsense.
            out.writeUTF("SeatReleased");
            out.writeInt(ALICE.index());
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        GameEvent first = EventCodec.read(in, false);
        GameEvent second = EventCodec.read(in, false);

        assertThat(first).isInstanceOf(GameEvent.DeckLoaded.class);
        assertThat(((GameEvent.DeckLoaded) first).sleeve()).isEqualTo(Sleeve.DEFAULT);
        assertThat(second).isInstanceOf(GameEvent.SeatReleased.class);
    }

    @Test
    @DisplayName("the sleeve reaches the seat, and the whole table can read it")
    void theTableSeesTheSleeve() {
        dev.gathering.core.game.GameSession session =
                dev.gathering.core.game.GameFixtures.twoPlayerTable(4);
        session.submit(new GameEvent.DeckLoaded(
                dev.gathering.core.game.GameFixtures.ALICE,
                dev.gathering.core.game.GameFixtures.deck(4), List.of(), Sleeve.GRASS));

        // Read through the rival's eyes, because a sleeve nobody else can see is a sleeve
        // that does not do its job: telling whose cards are whose across the table.
        var board = dev.gathering.core.game.visibility.VisibilityRules.viewFor(
                session.state(),
                new dev.gathering.core.game.visibility.Viewer.Seated(
                        dev.gathering.core.game.GameFixtures.BOB));

        assertThat(board.seat(dev.gathering.core.game.GameFixtures.ALICE).sleeve())
                .isEqualTo(Sleeve.GRASS);
    }
}
