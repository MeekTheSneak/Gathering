package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.PlayerRef;
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
 * A whole pod, as bytes, so a draft survives a restart.
 *
 * <p>Everything, not a view: this is what the server keeps for itself, and it holds every
 * pack in the ring. It must never be sent to a client, which is why it is a different class
 * from {@link DraftViewCodec} rather than a flag on it - the two are told apart by their
 * names at every call site, and there is no argument anybody can pass that turns one into
 * the other.
 *
 * <p>The whole state rather than the opening packs and a list of picks. A pod is a few
 * hundred identities at its largest, so the saving is not worth the second implementation of
 * the passing rules that replaying would need, and a bug in that second implementation would
 * hand somebody else's cards back after a restart.
 */
public final class DraftPodCodec {

    public static final int VERSION = 1;

    private DraftPodCodec() {
    }

    public static byte[] write(DraftPod pod) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeBoolean(pod.poolsAreKept());

            out.writeInt(pod.drafters().size());
            for (PlayerRef drafter : pod.drafters()) {
                DraftBytes.player(out, drafter);
            }

            DraftState state = pod.state();
            out.writeInt(state.round());

            out.writeInt(state.opening().size());
            for (List<DraftPack> round : state.opening()) {
                out.writeInt(round.size());
                for (DraftPack pack : round) {
                    DraftBytes.identities(out, pack.cards());
                }
            }

            out.writeInt(state.holding().size());
            for (DraftPack pack : state.holding()) {
                DraftBytes.identities(out, pack.cards());
            }

            out.writeInt(state.declared().size());
            for (Map.Entry<DrafterId, List<Integer>> entry : state.declared().entrySet()) {
                out.writeInt(entry.getKey().index());
                out.writeInt(entry.getValue().size());
                for (Integer position : entry.getValue()) {
                    out.writeInt(position);
                }
            }

            out.writeInt(state.pools().size());
            for (List<CardIdentity> pool : state.pools()) {
                DraftBytes.identities(out, pool);
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("Writing to memory failed", impossible);
        }
        return bytes.toByteArray();
    }

    public static DraftPod read(byte[] written) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(written))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("A pod is version " + version + ", this reads " + VERSION);
            }
            boolean poolsAreKept = in.readBoolean();

            int drafterCount = DraftBytes.place(in.readInt());
            List<PlayerRef> drafters = new ArrayList<>(drafterCount);
            for (int index = 0; index < drafterCount; index++) {
                drafters.add(DraftBytes.player(in));
            }

            int round = DraftBytes.size(in.readInt());

            int roundCount = DraftBytes.size(in.readInt());
            List<List<DraftPack>> opening = new ArrayList<>(roundCount);
            for (int index = 0; index < roundCount; index++) {
                opening.add(packs(in));
            }

            List<DraftPack> holding = packs(in);

            int declaredCount = DraftBytes.place(in.readInt());
            Map<DrafterId, List<Integer>> declared = new LinkedHashMap<>();
            for (int index = 0; index < declaredCount; index++) {
                DrafterId drafter = DrafterId.of(DraftBytes.place(in.readInt()));
                int picks = DraftBytes.size(in.readInt());
                List<Integer> positions = new ArrayList<>(picks);
                for (int pick = 0; pick < picks; pick++) {
                    positions.add(DraftBytes.size(in.readInt()));
                }
                declared.put(drafter, List.copyOf(positions));
            }

            int poolCount = DraftBytes.place(in.readInt());
            List<List<CardIdentity>> pools = new ArrayList<>(poolCount);
            for (int index = 0; index < poolCount; index++) {
                pools.add(DraftBytes.identities(in));
            }

            return new DraftPod(
                    drafters,
                    new DraftState(drafterCount, round, opening, holding, declared, pools),
                    poolsAreKept);
        } catch (IllegalArgumentException malformed) {
            // A pod that does not add up is a pod that cannot be restored, and the caller has
            // a real decision to make about that. Letting the unchecked exception through
            // would have looked like a crash in whatever loaded the world.
            throw new IOException("A saved pod does not add up: " + malformed.getMessage(), malformed);
        }
    }

    private static List<DraftPack> packs(DataInputStream in) throws IOException {
        int count = DraftBytes.place(in.readInt());
        List<DraftPack> packs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            packs.add(DraftPack.of(DraftBytes.identities(in)));
        }
        return packs;
    }
}
