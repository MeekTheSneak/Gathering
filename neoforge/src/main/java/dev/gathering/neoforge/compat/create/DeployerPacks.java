package dev.gathering.neoforge.compat.create;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.PackOpening;
import dev.gathering.server.ServerRun;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Create Deployer with an empty hand, pressing on a booster, tears it open: the booster turns into its
 * cards where it is, the way a deployer's own recipes turn one item into others.
 * <p>Two ways a press reaches a booster, because Create routes them differently. A booster lying loose
 * on the ground is an entity, and a deployer pointed at it in any direction interacts with it. A
 * booster on a Depot or a belt is reached by a deployer facing it <em>sideways</em>: one facing down
 * onto a Depot or belt hands the job to Create's own belt processing, which ignores an empty hand and
 * never presses at all.
 * <p>What comes out of a pack is drawn on the card workers and arrives a moment later, so the booster
 * stays where it is while that happens and is swapped for its cards in one step when they are ready.
 * Taken away in between - by a funnel, a hand, the belt moving on - it is not opened at all, and the
 * cards drawn for it are simply not handed out: nothing is consumed that did not come out, and nothing
 * comes out that was not consumed.
 * <p>A Deployer <em>holding</em> a booster opens it too, without any of this: that is the pack's own
 * right-click, and the cards go into the Deployer, which empties them to whatever funnels take from it.
 */
public final class DeployerPacks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** The depots and belts a pack is being drawn for right now, so a deployer pressing again waits. */
    private static final Set<GlobalPos> DRAWING = new HashSet<>();

    /** The loose boosters being drawn right now, by entity. */
    private static final Set<java.util.UUID> DRAWING_LOOSE = new HashSet<>();

    private DeployerPacks() {
    }

    static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof DeployerFakePlayer deployer) || !(event.getLevel() instanceof ServerLevel level)
                || !deployer.getMainHandItem().isEmpty()
                || !(event.getTarget() instanceof net.minecraft.world.entity.item.ItemEntity loose)) {
            return;
        }
        if (tearOpen(level, loose)) {
            event.setCanceled(true);
        }
    }

    /**
     * Starts opening a booster lying loose.
     *
     * @return whether it is a booster, now being opened
     */
    public static boolean tearOpen(ServerLevel level, net.minecraft.world.entity.item.ItemEntity loose) {
        PackComponent pack = PackItem.packOf(loose.getItem()).filter(PackComponent::isReal).orElse(null);
        if (pack == null || !loose.isAlive() || !aDeployerMayOpen(pack)) {
            return false;
        }
        java.util.UUID id = loose.getUUID();
        if (!DRAWING_LOOSE.add(id)) {
            return true;
        }
        long run = ServerRun.generation();
        PackOpening.draw(pack.setCode(), pack.kind(), pack.color()).whenComplete((cards, failure) ->
                ServerRun.onTheServerThread(level.getServer(), run, () -> {
                    DRAWING_LOOSE.remove(id);
                    if (failure != null || cards == null || cards.isEmpty()) {
                        LOGGER.warn("A deployer could not open a loose {} pack: {}", pack.setCode(),
                                failure == null ? "it drew nothing" : failure.toString());
                        return;
                    }
                    swapForCards(level, id, pack, cards);
                }));
        return true;
    }

    /**
     * Whether a press may open this pack at all.
     * <p>The same question opening one by hand asks, and a deployer used to skip it: with
     * collecting switched off, a hand is told no and a deployer opened the booster anyway. And
     * never an archive pack, which draws from the server's collections rather than a set - the
     * draw handed back nothing and warned on every press, for as long as the deployer spun.
     */
    static boolean aDeployerMayOpen(PackComponent pack) {
        return PackOpening.whyNot() == null && !pack.isArchive();
    }

    /** One of this loose booster, if it is still lying there, becomes these cards beside it. */
    static void swapForCards(ServerLevel level, java.util.UUID looseId, PackComponent pack, List<CardIdentity> cards) {
        if (!(level.getEntity(looseId) instanceof net.minecraft.world.entity.item.ItemEntity loose) || !loose.isAlive()
                || !PackItem.packOf(loose.getItem()).map(pack::equals).orElse(false)) {
            return;
        }
        ItemStack left = loose.getItem().copy();
        left.shrink(1);
        if (left.isEmpty()) {
            loose.discard();
        } else {
            loose.setItem(left);
        }
        for (CardIdentity card : cards) {
            net.minecraft.world.entity.item.ItemEntity out = new net.minecraft.world.entity.item.ItemEntity(
                    level, loose.getX(), loose.getY(), loose.getZ(), CardItem.of(CardComponent.of(card)));
            out.setDeltaMovement(0, 0.1, 0);
            level.addFreshEntity(out);
        }
    }

    /** For the in-world tests: whether this loose booster is being drawn. */
    public static boolean isDrawing(net.minecraft.world.entity.item.ItemEntity loose) {
        return DRAWING_LOOSE.contains(loose.getUUID());
    }

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof DeployerFakePlayer deployer) || !(event.getLevel() instanceof ServerLevel level)
                || !deployer.getMainHandItem().isEmpty()) {
            return;
        }
        if (tearOpen(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * Starts opening the first booster held on the depot or belt here.
     *
     * @return whether there was a booster to open, or one already being opened
     */
    public static boolean tearOpen(ServerLevel level, BlockPos pos) {
        TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return false;
        }
        GlobalPos where = GlobalPos.of(level.dimension(), pos.immutable());
        if (DRAWING.contains(where)) {
            return true;
        }
        PackComponent pack = firstPackOn(handler);
        if (pack == null || !aDeployerMayOpen(pack)) {
            return false;
        }
        DRAWING.add(where);
        long run = ServerRun.generation();
        PackOpening.draw(pack.setCode(), pack.kind(), pack.color()).whenComplete((cards, failure) ->
                ServerRun.onTheServerThread(level.getServer(), run, () -> {
                    DRAWING.remove(where);
                    if (failure != null || cards == null || cards.isEmpty()) {
                        LOGGER.warn("A deployer could not open a {} pack at {}: {}", pack.setCode(), pos,
                                failure == null ? "it drew nothing" : failure.toString());
                        return;
                    }
                    swapForCards(level, pos, pack, cards);
                }));
        return true;
    }

    /** The booster on this depot or belt that is ready to open, if any. */
    private static PackComponent firstPackOn(TransportedItemStackHandlerBehaviour handler) {
        PackComponent[] found = new PackComponent[1];
        handler.handleCenteredProcessingOnAllItems(.51f, transported -> {
            if (found[0] == null) {
                PackItem.packOf(transported.stack).filter(PackComponent::isReal).ifPresent(pack -> found[0] = pack);
            }
            return TransportedResult.doNothing();
        });
        return found[0];
    }

    /** One of this booster, if it is still there, becomes these cards; otherwise nothing happens. */
    static void swapForCards(ServerLevel level, BlockPos pos, PackComponent pack, List<CardIdentity> cards) {
        TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return;
        }
        boolean[] opened = new boolean[1];
        handler.handleCenteredProcessingOnAllItems(.51f, transported -> {
            if (opened[0] || !PackItem.packOf(transported.stack).map(pack::equals).orElse(false)) {
                return TransportedResult.doNothing();
            }
            opened[0] = true;
            List<TransportedItemStack> outputs = new ArrayList<>();
            for (CardIdentity card : cards) {
                TransportedItemStack out = transported.copy();
                out.stack = CardItem.of(CardComponent.of(card));
                outputs.add(out);
            }
            if (transported.stack.getCount() <= 1) {
                return TransportedResult.convertTo(outputs);
            }
            TransportedItemStack left = transported.copy();
            left.stack = transported.stack.copyWithCount(transported.stack.getCount() - 1);
            return TransportedResult.convertToAndLeaveHeld(outputs, left);
        });
    }

    /** Forgets what was being drawn, for a server that is stopping. */
    static void clear() {
        DRAWING.clear();
        DRAWING_LOOSE.clear();
    }

    /** For the in-world tests: whether a pack is being drawn here. */
    public static boolean isDrawingAt(ServerLevel level, BlockPos pos) {
        return DRAWING.contains(GlobalPos.of(level.dimension(), pos.immutable()));
    }
}
