package dev.gathering.core.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.Phase;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionRecord;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A session's log, out to bytes and back.
 *
 * <p>State is the fold of the log, so a log that does not survive a round trip is a game that
 * does not survive a restart - and it will not fail loudly, it will fold to a board that never
 * existed. Checked over arbitrary logs rather than the handful anybody thought to write down.
 */
class SessionCodecTest {

    @Property(tries = 2000)
    void anyLogSurvivesTheRoundTrip(@ForAll("logs") List<SessionRecord> records) throws IOException {
        SessionCodec.Streams streams = SessionCodec.write(records);

        List<SessionRecord> restored = SessionCodec.read(streams.publicLog(), streams.secretLog());

        assertThat(restored).isEqualTo(records);
    }

    @Property(tries = 2000)
    void theOpenStreamNeverCarriesASecretEvent(@ForAll("logs") List<SessionRecord> records)
            throws IOException {
        // The whole point of two streams. If a secret event's content ends up in the readable
        // one, a save file opened with external tools gives up a library's order.
        SessionCodec.Streams streams = SessionCodec.write(records);

        List<SessionRecord> secretOnes = records.stream()
                .map(SessionRecord.EventRecord.class::cast)
                .filter(record -> record.event().secrecy().isSecret())
                .map(SessionRecord.class::cast)
                .toList();
        if (secretOnes.isEmpty()) {
            return;
        }

        // Reading the open stream on its own must fail rather than quietly yield the secrets.
        assertThatThrownBy(() -> SessionCodec.read(streams.publicLog(), new byte[0]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("sealed");
    }

    @Test
    @DisplayName("a sealed stream that will not open is a clean failure, not a log with holes")
    void aMissingSealedStreamFailsCleanly() throws IOException {
        List<SessionRecord> records = List.of(
                new SessionRecord.EventRecord(0, new GameEvent.LifeChanged(
                        new SeatId(0), new SeatId(1), -3), false),
                // DeckLoaded carries a library's order, which is the whole reason there is a
                // sealed stream. LibraryLooked is public: it records that a look happened
                // and never what was seen.
                new SessionRecord.EventRecord(1, new GameEvent.DeckLoaded(
                        new SeatId(0),
                        List.of(CardIdentity.ofPrinting(UUID.randomUUID(), false)),
                        List.of()), false));
        SessionCodec.Streams streams = SessionCodec.write(records);

        assertThatThrownBy(() -> SessionCodec.read(streams.publicLog(), null))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("an event nobody knows about is refused rather than skipped")
    void unknownEventsAreRefused() throws IOException {
        // Skipping it would fold to a board that never existed, which is the one outcome
        // worse than not loading at all.
        List<SessionRecord> records = List.of(new SessionRecord.EventRecord(
                0, new GameEvent.Conceded(new SeatId(0)), false));
        SessionCodec.Streams streams = SessionCodec.write(records);

        byte[] corrupted = new String(streams.publicLog(), java.nio.charset.StandardCharsets.ISO_8859_1)
                .replace("Conceded", "Nonsense!")
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> SessionCodec.read(corrupted, streams.secretLog()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unknown event");
    }

    @Test
    @DisplayName("every event the game can produce has a codec")
    void everyEventKindRoundTrips() throws IOException {
        // A sealed interface plus an exhaustive switch means a new event fails to compile
        // rather than failing to save, but only if this list is kept honest - so it is
        // checked against the interface's own permitted set.
        List<GameEvent> samples = everyKind();
        List<SessionRecord> records = new ArrayList<>();
        for (int index = 0; index < samples.size(); index++) {
            records.add(new SessionRecord.EventRecord(index, samples.get(index), false));
        }

        SessionCodec.Streams streams = SessionCodec.write(records);

        assertThat(SessionCodec.read(streams.publicLog(), streams.secretLog())).isEqualTo(records);
        assertThat(samples).hasSize(GameEvent.class.getPermittedSubclasses().length);
    }

    @Test
    @DisplayName("taking a card off another one survives the round trip as an absence")
    void aDetachRoundTrips() throws Exception {
        // The one event with an optional field in it. A null host that came back as a card id
        // would silently re-attach something the moment a session was reopened.
        GameEvent detach = new GameEvent.CardAttached(new SeatId(0), new CardInstanceId(3), null);

        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
            EventCodec.write(out, detach);
        }
        GameEvent back = EventCodec.read(
                new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));

        assertThat(back).isEqualTo(detach);
        assertThat(((GameEvent.CardAttached) back).host()).isNull();
    }

    private static List<GameEvent> everyKind() {
        SeatId a = new SeatId(0);
        SeatId b = new SeatId(1);
        CardInstanceId card = new CardInstanceId(7);
        CardIdentity identity = CardIdentity.ofPrinting(UUID.randomUUID(), true);
        return List.of(
                new GameEvent.SeatTaken(a, new PlayerRef(UUID.randomUUID(), "Chris")),
                new GameEvent.SeatReleased(a),
                new GameEvent.DeckLoaded(a, List.of(identity), List.of(identity)),
                new GameEvent.SessionEnded(a, "everyone agreed"),
                new GameEvent.CardMoved(a, card, new ZoneRef(b, Zone.BATTLEFIELD),
                        new Placement.At(new TablePosition(3, 4, 90))),
                new GameEvent.CardTapSet(a, card, true),
                new GameEvent.CardRotated(a, card, 137),
                new GameEvent.CardAttached(a, card, new CardInstanceId(4)),
                new GameEvent.SeatUntappedAll(a, b),
                new GameEvent.CardFacingSet(a, card, Facing.FACE_DOWN),
                new GameEvent.CardsDrawn(a, b, 7),
                new GameEvent.Mulliganed(a, b, 6),
                new GameEvent.LibraryShuffled(a, b),
                new GameEvent.LibrarySearched(a, b),
                new GameEvent.LibraryClosed(a),
                new GameEvent.LibraryMilled(a, b, 4),
                new GameEvent.LibraryRevealed(a, b, 2),
                new GameEvent.LibraryLooked(a, b, 2),
                new GameEvent.LibraryReordered(a, b, List.of(card), List.of(new CardInstanceId(8))),
                new GameEvent.Surveiled(a, b, List.of(card), List.of()),
                new GameEvent.CounterChanged(a, card, "+1/+1", 2),
                new GameEvent.TokenCreated(a, b, identity, 3),
                new GameEvent.TokenCopyCreated(a, card, b),
                new GameEvent.TokenRemoved(a, card),
                new GameEvent.LifeChanged(a, b, -3),
                new GameEvent.SeatCounterChanged(a, b, "poison", 2),
                new GameEvent.CommanderDamageChanged(a, b, a, 5),
                new GameEvent.CommanderTaxChanged(a, b, card, 2),
                new GameEvent.Conceded(a),
                new GameEvent.PhaseSet(a, Phase.values()[0]),
                new GameEvent.TurnPassed(a, b),
                new GameEvent.CardPinged(a, card));
    }

    @Provide
    Arbitrary<List<SessionRecord>> logs() {
        Arbitrary<GameEvent> events = Arbitraries.of(everyKind());
        return Combinators.combine(events.list().ofMaxSize(20), Arbitraries.of(true, false).list().ofMaxSize(20))
                .as((list, flags) -> {
                    List<SessionRecord> records = new ArrayList<>();
                    for (int index = 0; index < list.size(); index++) {
                        boolean undone = index < flags.size() && flags.get(index);
                        records.add(new SessionRecord.EventRecord(index, list.get(index), undone));
                    }
                    return records;
                });
    }
}
