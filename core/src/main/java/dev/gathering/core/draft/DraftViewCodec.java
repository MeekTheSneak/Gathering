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

/**
 * One drafter's view of a pod, as bytes.
 * <p>Deliberately writes a {@link DraftView} and never a {@link DraftState}, the same way
 * the table's codec writes a view and never the game state. The view has already been
 * through {@link DraftVisibility}: everybody else's pack is a number by the time it gets
 * here. Writing the pod instead - or "the pod, minus the bits they should not see" - is how
 * a client ends up holding the packs it is about to be passed.
 * <p>Bytes rather than a network codec so the shape lives in the pure module beside the
 * rules it mirrors, and so a round trip can be checked against arbitrary views rather than
 * the ones somebody thought to write down.
 */
public final class DraftViewCodec {

    public static final int VERSION = 1;

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

            DraftBytes.identities(out, view.myPack().cards());
            DraftBytes.identities(out, view.myPool());

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
            DrafterId me = DrafterId.of(DraftBytes.place(in.readInt()));
            int drafters = DraftBytes.size(in.readInt());
            int round = DraftBytes.size(in.readInt());
            int rounds = DraftBytes.size(in.readInt());
            boolean finished = in.readBoolean();
            boolean declared = in.readBoolean();
            int due = DraftBytes.size(in.readInt());

            DraftPack pack = DraftPack.of(DraftBytes.identities(in));
            List<CardIdentity> pool = DraftBytes.identities(in);

            int waitingCount = DraftBytes.size(in.readInt());
            List<DrafterId> waiting = new ArrayList<>(Math.min(waitingCount, 64));
            for (int index = 0; index < waitingCount; index++) {
                waiting.add(DrafterId.of(DraftBytes.place(in.readInt())));
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
        int count = DraftBytes.size(in.readInt());
        Map<DrafterId, Integer> read = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            read.put(DrafterId.of(DraftBytes.place(in.readInt())), DraftBytes.size(in.readInt()));
        }
        return read;
    }

}
