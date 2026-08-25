package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One drafter's view of a pod, as bytes.
 *
 * <p>Deliberately writes a {@link DraftView} and never a {@link DraftState}, the same way
 * the table's codec writes a view and never the game state. The view has already been
 * through {@link DraftVisibility}: everybody else's pack is a number by the time it gets
 * here. Writing the pod instead - or "the pod, minus the bits they should not see" - is how
 * a client ends up holding the packs it is about to be passed.
 *
 * <p>Bytes rather than a network codec so the shape lives in the pure module beside the
 * rules it mirrors, and so a round trip can be checked against arbitrary views rather than
 * the ones somebody thought to write down.
 */
public final class DraftViewCodec {

    public static final int VERSION = 1;

    /** A ceiling on any length read from the wire, checked before it sizes anything. */
    public static final int MAX_ENTRIES = 20_000;

    private DraftViewCodec() {
    }

    public static byte[] write(DraftView view) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(view.me().index());
            out.writeInt(view.drafters());
            out.writeInt(view.round());
            out.writeInt(view.rounds());
            out.writeBoolean(view.finished());
            out.writeBoolean(view.iHaveDeclared());
            out.writeInt(view.picksDueFromMe());

            identities(out, view.myPack().cards());
            identities(out, view.myPool());

            out.writeInt(view.waitingOn().size());
            for (DrafterId drafter : view.waitingOn()) {
                out.writeInt(drafter.index());
            }
            counts(out, view.others());
            counts(out, view.holdingSizes());
        } catch (IOException impossible) {
            throw new IllegalStateException("Writing to memory failed", impossible);
        }
        return bytes.toByteArray();
    }

    public static DraftView read(byte[] written) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(written))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException(
                        "A pod view is version " + version + ", this reads " + VERSION);
            }
            DrafterId me = DrafterId.of(place(in.readInt()));
            int drafters = size(in.readInt());
            int round = size(in.readInt());
            int rounds = size(in.readInt());
            boolean finished = in.readBoolean();
            boolean declared = in.readBoolean();
            int due = size(in.readInt());

            DraftPack pack = DraftPack.of(identities(in));
            List<CardIdentity> pool = identities(in);

            int waitingCount = size(in.readInt());
            List<DrafterId> waiting = new ArrayList<>(Math.min(waitingCount, 64));
            for (int index = 0; index < waitingCount; index++) {
                waiting.add(DrafterId.of(place(in.readInt())));
            }
            Map<DrafterId, Integer> others = counts(in);
            Map<DrafterId, Integer> holding = counts(in);

            return new DraftView(me, drafters, round, rounds, finished, pack, pool,
                    declared, due, waiting, others, holding);
        }
    }

    private static void counts(DataOutput out, Map<DrafterId, Integer> counts) throws IOException {
        out.writeInt(counts.size());
        for (Map.Entry<DrafterId, Integer> entry : counts.entrySet()) {
            out.writeInt(entry.getKey().index());
            out.writeInt(entry.getValue());
        }
    }

    private static Map<DrafterId, Integer> counts(DataInput in) throws IOException {
        int count = size(in.readInt());
        Map<DrafterId, Integer> read = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            read.put(DrafterId.of(place(in.readInt())), size(in.readInt()));
        }
        return read;
    }

    private static void identities(DataOutput out, List<CardIdentity> cards) throws IOException {
        out.writeInt(cards.size());
        for (CardIdentity card : cards) {
            boolean printing = card.scryfallId() != null;
            out.writeBoolean(printing);
            if (printing) {
                out.writeLong(card.scryfallId().getMostSignificantBits());
                out.writeLong(card.scryfallId().getLeastSignificantBits());
            } else {
                out.writeUTF(card.customId());
            }
            out.writeBoolean(card.foil());
        }
    }

    private static List<CardIdentity> identities(DataInput in) throws IOException {
        int count = size(in.readInt());
        List<CardIdentity> cards = new ArrayList<>(Math.min(count, 1024));
        for (int index = 0; index < count; index++) {
            if (in.readBoolean()) {
                UUID id = new UUID(in.readLong(), in.readLong());
                cards.add(CardIdentity.ofPrinting(id, in.readBoolean()));
            } else {
                cards.add(CardIdentity.ofCustom(in.readUTF(), in.readBoolean()));
            }
        }
        return cards;
    }

    /**
     * A length off the wire, refused before it sizes anything.
     *
     * <p>A hostile or broken sender saying "two billion cards follow" must not get an
     * allocation of two billion out of this before the read fails.
     */
    private static int size(int size) throws IOException {
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IOException("Implausible length in a pod view: " + size);
        }
        return size;
    }

    private static int place(int index) throws IOException {
        if (index < 0 || index > DraftRules.LARGEST_POD) {
            throw new IOException("Implausible place in a pod: " + index);
        }
        return index;
    }
}
