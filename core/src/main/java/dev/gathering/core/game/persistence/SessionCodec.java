package dev.gathering.core.game.persistence;

import dev.gathering.core.game.SessionRecord;
import dev.gathering.core.game.event.GameEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A session's log, written for storage in two streams.
 *
 * <p>State is the fold of the log, so persisting a session is persisting this. It goes out in
 * two pieces on purpose:
 *
 * <ul>
 *   <li>The <b>public</b> stream carries every record's place in the order, and the content of
 *       the events whose content is public. It is readable without a key, which is what lets
 *       a saved game be inspected, migrated or debugged without unsealing anybody's hand.</li>
 *   <li>The <b>secret</b> stream carries the content of the rest - a library's order, what a
 *       scry saw - and is what gets encrypted. A save file opened with external tools
 *       mid-session yields the shape of the game and none of its secrets.</li>
 * </ul>
 *
 * <p>The two are joined by sequence number. A secret stream that will not open is a session
 * that cannot be restored, and {@link #read} says so rather than returning a log with holes
 * in it: a hole folds to a board that never existed, which is worse than a clean failure.
 */
public final class SessionCodec {

    /** Bumped when the format changes in a way an older reader cannot handle. */
    public static final int VERSION = 1;

    /**
     * A ceiling on any length read from a stream, checked before it sizes anything.
     *
     * <p>Well past a real game - a hundred-card library, a long match - and small enough that
     * a corrupt or hostile file cannot ask for an arbitrary allocation.
     */
    public static final int MAX_LIST = 100_000;

    private SessionCodec() {
    }

    /** The two streams a session is stored as. */
    public record Streams(byte[] publicLog, byte[] secretLog) {
    }

    public static Streams write(List<SessionRecord> records) throws IOException {
        ByteArrayOutputStream publicBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream secretBytes = new ByteArrayOutputStream();

        try (DataOutputStream open = new DataOutputStream(publicBytes);
                DataOutputStream sealed = new DataOutputStream(secretBytes)) {
            open.writeInt(VERSION);
            open.writeInt(records.size());
            sealed.writeInt(VERSION);

            int secrets = 0;
            for (SessionRecord record : records) {
                if (!(record instanceof SessionRecord.EventRecord event)) {
                    throw new IOException("Cannot store a " + record.getClass().getSimpleName());
                }
                open.writeLong(event.sequence());
                open.writeBoolean(event.undone());

                boolean secret = event.event().secrecy().isSecret();
                open.writeBoolean(secret);
                if (secret) {
                    // Only the fact that something happened, and where in the order. What
                    // happened goes to the stream that gets sealed.
                    sealed.writeLong(event.sequence());
                    EventCodec.write(sealed, event.event());
                    secrets++;
                } else {
                    EventCodec.write(open, event.event());
                }
            }
            sealed.writeLong(-1L);
            open.writeInt(secrets);
        }
        return new Streams(publicBytes.toByteArray(), secretBytes.toByteArray());
    }

    /**
     * Puts a session's log back together.
     *
     * @param secretLog the decrypted secret stream; a session with no secrets still has one
     */
    public static List<SessionRecord> read(byte[] publicLog, byte[] secretLog) throws IOException {
        Map<Long, GameEvent> secrets = readSecrets(secretLog);

        try (DataInputStream open = new DataInputStream(new ByteArrayInputStream(publicLog))) {
            int version = open.readInt();
            if (version != VERSION) {
                throw new IOException("Session log is version " + version + ", this reads " + VERSION);
            }
            int count = readSize(open.readInt());

            List<SessionRecord> records = new ArrayList<>(Math.min(count, 1024));
            for (int index = 0; index < count; index++) {
                long sequence = open.readLong();
                boolean undone = open.readBoolean();
                boolean secret = open.readBoolean();

                GameEvent event;
                if (secret) {
                    event = secrets.get(sequence);
                    if (event == null) {
                        // The sealed stream did not open, or did not have this in it. A log
                        // with a hole folds to a board that never existed.
                        throw new IOException("The sealed part of this session is missing event " + sequence);
                    }
                } else {
                    event = EventCodec.read(open);
                }
                records.add(new SessionRecord.EventRecord(sequence, event, undone));
            }
            return List.copyOf(records);
        }
    }

    private static Map<Long, GameEvent> readSecrets(byte[] secretLog) throws IOException {
        Map<Long, GameEvent> secrets = new LinkedHashMap<>();
        if (secretLog == null || secretLog.length == 0) {
            return secrets;
        }
        try (DataInputStream sealed = new DataInputStream(new ByteArrayInputStream(secretLog))) {
            int version = sealed.readInt();
            if (version != VERSION) {
                throw new IOException("Sealed log is version " + version + ", this reads " + VERSION);
            }
            while (true) {
                long sequence = sealed.readLong();
                if (sequence < 0) {
                    return secrets;
                }
                secrets.put(sequence, EventCodec.read(sealed));
                if (secrets.size() > MAX_LIST) {
                    throw new IOException("Implausibly many sealed events");
                }
            }
        }
    }

    private static int readSize(int size) throws IOException {
        if (size < 0 || size > MAX_LIST) {
            throw new IOException("Implausible record count in the session log: " + size);
        }
        return size;
    }
}
