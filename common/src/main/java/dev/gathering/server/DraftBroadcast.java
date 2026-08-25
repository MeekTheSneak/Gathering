package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.block.DraftPods;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.DrafterId;
import dev.gathering.core.draft.DraftView;
import dev.gathering.core.draft.DraftViewCodec;
import dev.gathering.core.draft.DraftVisibility;
import dev.gathering.network.DraftViewPayload;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells everyone in a pod what is in front of them - each of them something different.
 *
 * <p>Built per recipient and sent to that recipient alone, the same shape the board uses and
 * for the same reason: a shared pod packet would have to contain every pack in the ring, and
 * every client would hold the one it is about to be passed.
 *
 * <p>Nobody outside the pod is sent anything at all. A draft has no spectators - there is no
 * public part of it to watch - so somebody standing at the tables gets no packet rather than
 * an empty one.
 */
public final class DraftBroadcast {

    private DraftBroadcast() {
    }

    /**
     * Sends every drafter what they may see.
     *
     * @param open whether this should open the pack screen, or only update one already
     *             showing - so a drafter who closed it to look something up is not dragged
     *             back to it every time a neighbour picks
     */
    public static void sendToPod(ServerLevel level, BlockPos tableOrigin, boolean open) {
        DraftPod pod = DraftPods.podAt(level, tableOrigin).orElse(null);
        if (pod == null) {
            return;
        }
        for (int index = 0; index < pod.drafters().size(); index++) {
            DrafterId place = DrafterId.of(index);
            UUID id = pod.drafterAt(place).map(drafter -> drafter.id()).orElse(null);
            ServerPlayer drafter = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
            if (drafter == null) {
                // Offline mid-draft. Their pack waits: the pod is saved with the world and
                // they are shown it again the moment they come back, which is the whole
                // reason a pod is state rather than something held in memory by a screen.
                continue;
            }
            sendTo(drafter, tableOrigin, pod, place, open);
        }
    }

    /** Sends one drafter their own pack, and the pictures for the cards in it. */
    public static void sendTo(
            ServerPlayer drafter, BlockPos tableOrigin, DraftPod pod, DrafterId place, boolean open) {
        DraftView seen = DraftVisibility.viewFor(pod.state(), place);
        Sending.to(drafter,
                new DraftViewPayload(tableOrigin, DraftViewCodec.write(seen), open));
        // Exactly the cards this view just named and no others, which is the same argument
        // the table's art push makes: a card the rules turned into a count has no identity
        // here to send.
        CardArtPush.send(drafter, printingsIn(seen));
    }

    /** Every printing this view named, which is the pack in front of them and their pool. */
    private static Set<UUID> printingsIn(DraftView view) {
        Set<UUID> printings = new LinkedHashSet<>();
        collect(printings, view.myPack().cards());
        collect(printings, view.myPool());
        return printings;
    }

    private static void collect(Set<UUID> into, List<CardIdentity> cards) {
        for (CardIdentity card : cards) {
            if (card.scryfallId() != null) {
                into.add(card.scryfallId());
            }
        }
    }
}
