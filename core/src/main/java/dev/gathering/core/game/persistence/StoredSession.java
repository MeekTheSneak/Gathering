package dev.gathering.core.game.persistence;

import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionRecord;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;

/**
 * A whole session, ready to be written down, and put back together again.
 * <p>Two blobs: one readable, one sealed. Which of a session's parts goes where is not a
 * judgment call made here - the log's own {@code Secrecy} decides that - with one addition.
 * The shuffle seed goes in the sealed blob, because seed plus decklist is every card anybody
 * will draw for the rest of the game, which makes it more sensitive than any single hidden
 * card rather than less.
 * <p>Seats, starting life and undo mode stay in the open. They are the shape of the table,
 * not its secrets, and keeping them readable is what lets a stored session be identified
 * without being unsealed.
 */
public record StoredSession(byte[] openPart, byte[] sealedPart) {

    private static final int VERSION = 1;

    /** Well past any real table; a bound so a corrupt file cannot ask for an allocation. */
    private static final int MAX_SEATS = 64;

    /**
     * @param startingLife the life the table started on, which the session itself does not
     *                     remember once anybody has gained or lost any
     */
    public static StoredSession of(GameSession session, int startingLife, SecretKey key)
            throws IOException {
        // Seats come from the session rather than from a caller, so there is no way to store
        // a session against a seat list that is not its own.
        List<SeatId> seats = session.state().seats();
        SessionCodec.Streams streams = SessionCodec.write(session.records());

        ByteArrayOutputStream open = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(open)) {
            out.writeInt(VERSION);
            out.writeInt(seats.size());
            for (SeatId seat : seats) {
                out.writeInt(seat.index());
            }
            out.writeInt(startingLife);
            out.writeUTF(session.undoMode().name());
            out.writeInt(streams.publicLog().length);
            out.write(streams.publicLog());
        }

        // The seed rides with the secret events rather than beside them: one sealed blob is
        // one thing to get right, and the seed is the most sensitive value in the mod.
        ByteArrayOutputStream sealed = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(sealed)) {
            out.writeInt(VERSION);
            byte[] seed = session.seed().toBytes();
            out.writeInt(seed.length);
            out.write(seed);
            out.writeInt(streams.secretLog().length);
            out.write(streams.secretLog());
        }

        return new StoredSession(open.toByteArray(), SessionCipher.seal(key, sealed.toByteArray()));
    }

    /**
     * Puts the session back.
     *
     * @throws SessionCipher.SealedStreamException if the sealed blob is missing, altered, or
     *                                             the key is not the one it was sealed with
     */
    public GameSession restore(SecretKey key) throws IOException, SessionCipher.SealedStreamException {
        byte[] unsealed = SessionCipher.open(key, sealedPart);

        SessionSeed seed;
        byte[] secretLog;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(unsealed))) {
            expectVersion(in.readInt());
            seed = SessionSeed.fromBytes(readBlob(in));
            secretLog = readBlob(in);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(openPart))) {
            expectVersion(in.readInt());
            int seatCount = in.readInt();
            if (seatCount < 0 || seatCount > MAX_SEATS) {
                throw new IOException("Implausible seat count in a stored session: " + seatCount);
            }
            List<SeatId> seats = new ArrayList<>(seatCount);
            for (int index = 0; index < seatCount; index++) {
                seats.add(new SeatId(in.readInt()));
            }
            int startingLife = in.readInt();
            UndoMode undoMode = UndoMode.valueOf(in.readUTF());
            byte[] publicLog = readBlob(in);

            List<SessionRecord> records = SessionCodec.read(publicLog, secretLog);
            return GameSession.restore(seats, startingLife, seed, undoMode, records);
        }
    }

    private static void expectVersion(int version) throws IOException {
        if (version != VERSION) {
            throw new IOException("Stored session is version " + version + ", this reads " + VERSION);
        }
    }

    private static byte[] readBlob(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > SessionCodec.MAX_LIST * 64) {
            throw new IOException("Implausible blob length in a stored session: " + length);
        }
        return in.readNBytes(length);
    }
}
