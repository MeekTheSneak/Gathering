package dev.gathering.core.game.persistence;

import dev.gathering.core.game.SeatId;
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
    public static final int VERSION = 3;

    /**
     * A ceiling on any length read from a stream, checked before it sizes anything.
     *
     * <p>Well past a real game - a hundred-card library, a long match - and small enough that
     * a corrupt or hostile file cannot ask for an arbitrary allocation.
     */
    public static final int MAX_LIST = 100_000;

    /** A thing that happened. */
    private static final byte HAPPENING = 0;

    /** Somebody taking things back. Never secret: it names no card. */
    private static final byte REWIND = 1;

    /** The format before a rewind could be stored: every record an event, and none of them said so. */
    private static final int BEFORE_REWINDS = 1;

    /**
     * The format before a deck could be sleeved: DeckLoaded ended after its commanders.
     *
     * <p>Kept readable for the same reason {@link #BEFORE_REWINDS} is - somebody's
     * half-finished game and the replays of every game they have already played are on disk
     * in it, and a version bump that puts those out of reach costs more than the branch does.
     */
    private static final int BEFORE_SLEEVES = 2;

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
                // Which kind of record this is, because a log has two and used to be written
                // as though it had one. A rewind puts an UndoRecord in the log and the writer
                // refused it, so the first time anybody took a move back the table could no
                // longer be saved at all - and the session, on being told it could not be
                // written, went away with the game in it.
                if (record instanceof SessionRecord.UndoRecord rewind) {
                    open.writeByte(REWIND);
                    open.writeLong(rewind.sequence());
                    open.writeInt(rewind.requester().index());
                    open.writeInt(rewind.actionCount());
                    open.writeBoolean(rewind.unanimous());
                    continue;
                }
                if (!(record instanceof SessionRecord.EventRecord event)) {
                    throw new IOException("Cannot store a " + record.getClass().getSimpleName());
                }
                open.writeByte(HAPPENING);
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
            if (!readable(version)) {
                throw new IOException("Session log is version " + version + ", this reads " + VERSION);
            }
            // A log written before rewinds could be stored has no kind byte on its records,
            // because everything in it was an event. Reading it as though each record said so
            // is the whole of the difference, and it costs nothing to keep somebody's
            // half-finished game openable.
            boolean tagged = version != BEFORE_REWINDS;
            boolean sleeved = version > BEFORE_SLEEVES;
            int count = readSize(open.readInt());

            List<SessionRecord> records = new ArrayList<>(Math.min(count, 1024));
            for (int index = 0; index < count; index++) {
                byte kind = tagged ? open.readByte() : HAPPENING;
                if (kind == REWIND) {
                    records.add(new SessionRecord.UndoRecord(
                            open.readLong(), new SeatId(open.readInt()),
                            open.readInt(), open.readBoolean()));
                    continue;
                }
                if (kind != HAPPENING) {
                    throw new IOException("Session log has a record of kind " + kind);
                }
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
                    event = EventCodec.read(open, sleeved);
                }
                if (event == null) {
                    // A verb this build has retired - see EventCodec's read. The record is
                    // dropped rather than the whole log refused: the sequence numbers of the
                    // records around it are unchanged, so a rewind still points at what it
                    // always pointed at, and the board folds to the same table minus a label
                    // nothing ever read.
                    continue;
                }
                records.add(new SessionRecord.EventRecord(sequence, event, undone));
            }
            return List.copyOf(records);
        }
    }

    /** Whether this build can read a log stamped with that version. */
    private static boolean readable(int version) {
        return version == VERSION || version == BEFORE_REWINDS || version == BEFORE_SLEEVES;
    }

    private static Map<Long, GameEvent> readSecrets(byte[] secretLog) throws IOException {
        Map<Long, GameEvent> secrets = new LinkedHashMap<>();
        if (secretLog == null || secretLog.length == 0) {
            return secrets;
        }
        try (DataInputStream sealed = new DataInputStream(new ByteArrayInputStream(secretLog))) {
            int version = sealed.readInt();
            // The sealed stream did not change when rewinds became storable - it holds only
            // events and always did - so an older one still reads, and refusing it would put
            // the hidden half of somebody's game out of reach for no reason.
            if (!readable(version)) {
                throw new IOException("Sealed log is version " + version + ", this reads " + VERSION);
            }
            boolean sleeved = version > BEFORE_SLEEVES;
            while (true) {
                long sequence = sealed.readLong();
                if (sequence < 0) {
                    return secrets;
                }
                secrets.put(sequence, EventCodec.read(sealed, sleeved));
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
