package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.block.BreakRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Refuses breaks the mod does not allow: a table with somebody sitting at it, a collection
 * belonging to somebody who has not shared it, and the same things taken by an explosion.
 * <p>A block cannot decline to be broken in vanilla - by the time the block itself hears
 * about it the decision has been made - so this is the loader's break event, which is the
 * one place the answer can still be no. What is refused is decided in {@link BreakRules},
 * shared with the Fabric hook so the two loaders cannot answer differently.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID)
public final class GatheringTableRules {

    private GatheringTableRules() {
    }

    /**
     * What is in use survives the blast.
     * <p>Vanilla asks a block how much explosion it can take and never says where that block is,
     * so "this table, which has a game on it" is not a question the block itself can answer.
     * The explosion's own list of what it is about to remove is, and taking entries out of it is
     * the whole of this.
     * <p>The owner lost a table in play to TNT (2026-09-22). Fabric has no event of its own here
     * and does it with a mixin; both ask {@link BreakRules#survivesExplosions}.
     */
    @SubscribeEvent
    public static void onExplode(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(
                pos -> BreakRules.survivesExplosions(event.getLevel(), pos));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        BreakRules.refuse(event.getLevel(), event.getPos(), event.getPlayer())
                .ifPresent(why -> {
                    event.setCanceled(true);
                    event.getPlayer().sendSystemMessage(why);
                    BreakRules.refused(event.getLevel(), event.getPos());
                });
    }
}
