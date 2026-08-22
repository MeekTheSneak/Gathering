package dev.gathering.core.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.event.GameEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A game, saved and reopened.
 *
 * <p>The thing that has to hold is that the board comes back identical - not similar. State
 * is the fold of the log, so an event lost or reordered in storage is a board that never
 * existed, and nothing about it looks wrong.
 */
class StoredSessionTest {

    private static final SeatId ALICE = new SeatId(0);
    private static final SeatId BOB = new SeatId(1);

    @Test
    @DisplayName("a game reopens on exactly the board it was saved on")
    void aSessionSurvivesBeingStored() throws Exception {
        SecretKey key = SessionCipher.newKey();
        GameSession session = playALittle();

        StoredSession stored = StoredSession.of(session, List.of(ALICE, BOB), 40, key);
        GameSession restored = stored.restore(key);

        assertThat(restored.records()).isEqualTo(session.records());
        assertThat(restored.state()).isEqualTo(session.state());
        assertThat(restored.undoMode()).isEqualTo(session.undoMode());
        assertThat(restored.seed().toBytes()).isEqualTo(session.seed().toBytes());
    }

    @Test
    @DisplayName("a restored game carries on where it left off rather than reusing numbers")
    void restoringContinuesTheSequence() throws Exception {
        SecretKey key = SessionCipher.newKey();
        GameSession session = playALittle();
        long highest = session.records().stream()
                .map(dev.gathering.core.game.SessionRecord.EventRecord.class::cast)
                .mapToLong(dev.gathering.core.game.SessionRecord.EventRecord::sequence)
                .max()
                .orElse(0);

        GameSession restored = StoredSession.of(session, List.of(ALICE, BOB), 40, key).restore(key);
        restored.submit(new GameEvent.LifeChanged(ALICE, BOB, -1));

        long newest = restored.records().stream()
                .map(dev.gathering.core.game.SessionRecord.EventRecord.class::cast)
                .mapToLong(dev.gathering.core.game.SessionRecord.EventRecord::sequence)
                .max()
                .orElse(0);
        assertThat(newest).isGreaterThan(highest);
    }

    @Test
    @DisplayName("the seed is not in the readable half")
    void theSeedIsSealed() throws Exception {
        // Seed plus decklist is every card anybody will draw for the rest of the game, which
        // makes it worth more than any single hidden card rather than less.
        SecretKey key = SessionCipher.newKey();
        GameSession session = playALittle();

        StoredSession stored = StoredSession.of(session, List.of(ALICE, BOB), 40, key);

        assertThat(indexOf(stored.openPart(), session.seed().toBytes()))
                .describedAs("the shuffle seed is sitting in the readable half of the save")
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("a library's order is not in the readable half either")
    void theLibraryOrderIsSealed() throws Exception {
        SecretKey key = SessionCipher.newKey();
        UUID printing = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
        GameSession session = GameSession.create(
                List.of(ALICE), 40, SessionSeed.random(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE,
                new dev.gathering.core.game.PlayerRef(UUID.randomUUID(), "Chris")));
        session.submit(new GameEvent.DeckLoaded(ALICE,
                List.of(CardIdentity.ofPrinting(printing, false)), List.of()));

        StoredSession stored = StoredSession.of(session, List.of(ALICE), 40, key);

        byte[] identity = new byte[16];
        java.nio.ByteBuffer.wrap(identity)
                .putLong(printing.getMostSignificantBits())
                .putLong(printing.getLeastSignificantBits());
        assertThat(indexOf(stored.openPart(), identity))
                .describedAs("a card from the library is sitting in the readable half")
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("the readable half really is readable, which is the point of splitting it")
    void theOpenHalfIsNotEncrypted() throws Exception {
        SecretKey key = SessionCipher.newKey();
        GameSession session = playALittle();

        StoredSession stored = StoredSession.of(session, List.of(ALICE, BOB), 40, key);

        assertThat(new String(stored.openPart(), StandardCharsets.ISO_8859_1)).contains("LifeChanged");
    }

    @Test
    @DisplayName("somebody else's key does not open it")
    void theWrongKeyIsRefused() throws Exception {
        GameSession session = playALittle();
        StoredSession stored = StoredSession.of(session, List.of(ALICE, BOB), 40, SessionCipher.newKey());

        assertThatThrownBy(() -> stored.restore(SessionCipher.newKey()))
                .isInstanceOf(SessionCipher.SealedStreamException.class);
    }

    private static GameSession playALittle() {
        GameSession session = GameSession.create(
                List.of(ALICE, BOB), 40, SessionSeed.random(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE,
                new dev.gathering.core.game.PlayerRef(UUID.randomUUID(), "Chris")));
        session.submit(new GameEvent.SeatTaken(BOB,
                new dev.gathering.core.game.PlayerRef(UUID.randomUUID(), "Sam")));
        session.submit(new GameEvent.DeckLoaded(ALICE, library(), List.of()));
        session.submit(new GameEvent.LifeChanged(ALICE, BOB, -3));
        session.submit(new GameEvent.PhaseSet(ALICE, dev.gathering.core.game.Phase.values()[0]));
        return session;
    }

    private static List<CardIdentity> library() {
        List<CardIdentity> cards = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            cards.add(CardIdentity.ofPrinting(new UUID(0, index), index % 7 == 0));
        }
        return cards;
    }

    /** Where {@code needle} first appears in {@code haystack}, or -1. */
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }
}
