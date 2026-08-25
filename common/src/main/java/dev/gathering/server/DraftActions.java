package dev.gathering.server;

import dev.gathering.block.DraftPods;
import dev.gathering.block.TableBlock;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.DrafterId;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What a drafter does: take cards out of the pack in front of them.
 *
 * <p>One verb, and the whole of the checking is that the pick is theirs. Everything else a
 * pod could refuse - the wrong number of cards, a place that is not in the pack, picking
 * twice - is refused by the pure pod, so this is the part with a connection in it and
 * nothing else.
 */
public final class DraftActions {

    /** How far a drafter may be from the tables and still be picking at them. */
    private static final double REACH = 12.0d;

    private DraftActions() {
    }

    /**
     * Shows this player the pack in front of them, or says why there is none.
     *
     * <p>Also the second chance at handing the pools out. A pod can only finish when every
     * drafter has declared, so everybody was online moments before - but somebody who logs
     * out in the gap between their own last pick and the last one in the pod would otherwise
     * leave the pod holding pools nothing ever offered again.
     */
    public static void openFor(ServerPlayer player, BlockPos tableOrigin) {
        ServerLevel level = player.serverLevel();
        DraftPod pod = DraftPods.podAt(level, tableOrigin).orElse(null);
        if (pod == null) {
            return;
        }
        if (pod.isFinished()) {
            handOutThePools(level, tableOrigin, pod);
            return;
        }
        DrafterId mine = pod.placeOf(player.getUUID()).orElse(null);
        if (mine == null) {
            // A draft has no spectators. There is no public part of a pod to watch, so
            // somebody who was not in it is told that rather than shown an empty screen.
            player.sendSystemMessage(Component.translatable("message.gathering.draft_not_yours"));
            return;
        }
        DraftBroadcast.sendTo(player, tableOrigin, pod, mine, true);
    }

    public static void handle(ServerPlayer player, BlockPos tableOrigin, List<Integer> positions) {
        ServerLevel level = player.serverLevel();
        if (!within(player, tableOrigin) || TableBlock.entityAt(level, tableOrigin).isEmpty()) {
            return;
        }
        DraftPod pod = DraftPods.podAt(level, tableOrigin).orElse(null);
        if (pod == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.draft_not_running"));
            return;
        }
        // The place comes from the connection, never from the packet. A client that could
        // name a drafter could empty a pack it may not read and learn what was in it from
        // what came back.
        DrafterId mine = pod.placeOf(player.getUUID()).orElse(null);
        String denial = pod.denialFor(player.getUUID(), mine, positions).orElse(null);
        if (denial != null) {
            player.sendSystemMessage(Component.literal(denial));
            // And they are shown the pack again, because a refusal usually means their screen
            // is behind the pod - somebody else's pick moved the packs on while they were
            // deciding - and telling them why without showing them the truth is half an answer.
            DraftBroadcast.sendTo(player, tableOrigin, pod, mine, false);
            return;
        }

        DraftPod after = pod.declare(player.getUUID(), mine, positions);
        DraftPods.record(level, tableOrigin, after);
        DraftBroadcast.sendToPod(level, tableOrigin, false);
        if (after.isFinished()) {
            handOutThePools(level, tableOrigin, after);
        }
    }

    /**
     * The end of a draft: everybody gets what they drafted, as a deck to build from.
     *
     * <p>In the sideboard rather than the deck, because a drafted pool is not a deck yet -
     * it is forty-five cards you choose forty from, and the deck screen already knows how to
     * move cards between the two. Handing it over as a finished deck would mean pretending
     * a pile of picks was a decklist.
     *
     * <p>A sponsored pod whose cards go back to the sponsor hands out nothing, which the pod
     * itself decides: the question of who keeps what was settled before anybody picked.
     */
    private static void handOutThePools(ServerLevel level, BlockPos tableOrigin, DraftPod pod) {
        List<List<CardIdentity>> pools = pod.pooledAway();
        // Names the draft rather than identifying it: two pools from two different pods must
        // not read as the same pool, and this is what a deck check says it is checking
        // against. Never the session seed, which is the one value that never leaves the
        // server - the pod's own place in the world is enough to tell two drafts apart.
        String podName = tableOrigin.toShortString();
        if (!everybodyIsHere(level, pod)) {
            // Somebody left between their own last pick and the last one in the pod. Their
            // pool is still in it, and the pod is saved with the world - so nothing is handed
            // out until everybody can take theirs, and opening the table tries again.
            return;
        }
        for (int index = 0; index < pod.drafters().size(); index++) {
            DrafterId place = DrafterId.of(index);
            ServerPlayer drafter = level.getServer().getPlayerList()
                    .getPlayer(pod.drafterAt(place).orElseThrow().id());
            List<CardIdentity> pool = pools.get(index);
            if (pool.isEmpty()) {
                drafter.sendSystemMessage(
                        Component.translatable("message.gathering.draft_pool_returned"));
                continue;
            }
            List<CardComponent> cards = new ArrayList<>(pool.size());
            for (CardIdentity card : pool) {
                cards.add(CardComponent.of(card));
            }
            ItemStack stack = DeckItem.of(new DeckComponent(
                    Component.translatable("item.gathering.draft_pool").getString(),
                    "",
                    Optional.of(drafter.getUUID()),
                    List.of(),
                    List.of(),
                    cards));
            // And what it may be built from, which never changes while the deck inside it
            // changes constantly. Limited is "play what you opened", and this is the record
            // of what was opened - so it travels with the deck rather than living at the
            // table the draft happened at.
            stack.set(dev.gathering.registry.GatheringComponents.POOL.get(),
                    new dev.gathering.item.DraftedPool(cards, podName));
            if (!drafter.getInventory().add(stack)) {
                drafter.drop(stack, false);
            }
            drafter.sendSystemMessage(Component.translatable(
                    "message.gathering.draft_finished", pool.size()));
        }
        DraftPods.end(level, tableOrigin);
    }

    /** Whether every drafter is online to be handed their pool. */
    private static boolean everybodyIsHere(ServerLevel level, DraftPod pod) {
        for (int index = 0; index < pod.drafters().size(); index++) {
            java.util.UUID who = pod.drafterAt(DrafterId.of(index)).orElseThrow().id();
            if (level.getServer().getPlayerList().getPlayer(who) == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean within(ServerPlayer player, BlockPos tableOrigin) {
        return player.distanceToSqr(
                tableOrigin.getX() + 0.5d, tableOrigin.getY() + 0.5d, tableOrigin.getZ() + 0.5d)
                <= REACH * REACH;
    }
}
