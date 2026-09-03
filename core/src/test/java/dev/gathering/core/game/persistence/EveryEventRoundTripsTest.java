package dev.gathering.core.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.Sleeve;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.event.PlanarFace;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every event there is survives being written down and read back.
 * <p>A replay is the record of a game, and the way it stops being one is quiet: somebody adds
 * a verb, wires it to a button and folds it into the state, and forgets the two lines that
 * put it on disk. Nothing fails. The game plays correctly for the whole session; the replay
 * of it is simply missing a move, and nobody finds out until they watch one back.
 * <p>So this does not name the events. It asks the sealed interface what its kinds are, which
 * is a list the compiler maintains, builds one of each out of its own record components, and
 * round-trips it. An event added tomorrow is covered tomorrow, and one added without a codec
 * fails here rather than in somebody's replay.
 * <p>Values are picked by type rather than by meaning, so what is asserted is the codec and
 * not the sample. Every one of them is a value the event's own constructor accepts - the
 * records clean and validate what they are given, and both sides go through the same
 * constructor, so a cleaned value still compares equal after the trip.
 */
class EveryEventRoundTripsTest {

    @Test
    @DisplayName("every kind of event can be written to a session log and read back the same")
    void everyEventSurvivesTheRoundTrip() throws Exception {
        Class<?>[] kinds = GameEvent.class.getPermittedSubclasses();
        assertThat(kinds)
                .describedAs("GameEvent is sealed, so its kinds are known at compile time")
                .isNotEmpty();

        List<String> checked = new ArrayList<>();
        for (Class<?> kind : kinds) {
            GameEvent made = sampleOf(kind);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                EventCodec.write(out, made);
            }
            GameEvent back = EventCodec.read(
                    new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            assertThat(back)
                    .describedAs("%s did not survive being written down", kind.getSimpleName())
                    .isEqualTo(made);
            checked.add(kind.getSimpleName());
        }
        assertThat(checked).hasSameSizeAs(kinds);
    }

    /** One of these, built from whatever its record components happen to be. */
    private static GameEvent sampleOf(Class<?> kind) throws Exception {
        RecordComponent[] parts = kind.getRecordComponents();
        assertThat(parts)
                .describedAs("%s is a kind of GameEvent and not a record", kind.getSimpleName())
                .isNotNull();
        Class<?>[] types = new Class<?>[parts.length];
        Object[] values = new Object[parts.length];
        for (int part = 0; part < parts.length; part++) {
            types[part] = parts[part].getType();
            values[part] = valueFor(parts[part]);
        }
        return (GameEvent) kind.getDeclaredConstructor(types).newInstance(values);
    }

    /**
     * A value of this component's type.
     * <p>An unknown type is a failure rather than a null: a null slipped in here would be a
     * component this test silently stopped covering, which is the same kind of quiet as the
     * missing codec line it exists to catch.
     */
    private static Object valueFor(RecordComponent part) {
        Class<?> type = part.getType();
        if (type == int.class) {
            return 2;
        }
        if (type == boolean.class) {
            return true;
        }
        if (type == String.class) {
            // Plain, because several of these are cleaned on the way in - a note, a counter's
            // name, a typed power and toughness - and what is being checked is the trip.
            return "sample";
        }
        if (type == SeatId.class) {
            return new SeatId(1);
        }
        if (type == CardInstanceId.class) {
            return CardInstanceId.of(3);
        }
        if (type == CardIdentity.class) {
            return anIdentity();
        }
        if (type == PlayerRef.class) {
            return new PlayerRef(UUID.nameUUIDFromBytes("player".getBytes()), "Dev");
        }
        if (type == Zone.class) {
            return Zone.BATTLEFIELD;
        }
        if (type == ZoneRef.class) {
            return ZoneRef.of(new SeatId(1), Zone.BATTLEFIELD);
        }
        if (type == Placement.class) {
            return Placement.at(TablePosition.of(1_200, 3_400));
        }
        if (type == Facing.class) {
            return Facing.FACE_DOWN;
        }
        if (type == Sleeve.class) {
            return Sleeve.DEFAULT;
        }
        if (type == PaperStock.class) {
            return PaperStock.values()[0];
        }
        if (type == PlanarFace.class) {
            return PlanarFace.values()[0];
        }
        if (type == List.class) {
            return listFor(part);
        }
        throw new AssertionError("No sample value for " + type.getSimpleName()
                + " (" + part.getDeclaringRecord().getSimpleName() + "." + part.getName()
                + "). Add one, or this component stops being checked.");
    }

    /** And what goes in a list, which the plain type does not say. */
    private static Object listFor(RecordComponent part) {
        String of = part.getGenericType().getTypeName();
        if (of.contains("CardIdentity")) {
            return List.of(anIdentity());
        }
        if (of.contains("CardInstanceId")) {
            return List.of(CardInstanceId.of(3), CardInstanceId.of(4));
        }
        if (of.contains("String")) {
            return List.of("sample");
        }
        throw new AssertionError("No sample list for " + of
                + " (" + part.getDeclaringRecord().getSimpleName() + "." + part.getName() + ")");
    }

    private static CardIdentity anIdentity() {
        return CardIdentity.ofPrinting(UUID.nameUUIDFromBytes("printing".getBytes()), false);
    }
}
