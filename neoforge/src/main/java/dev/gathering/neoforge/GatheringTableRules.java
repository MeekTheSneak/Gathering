package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.block.BreakRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Refuses breaks the mod does not allow: a table with somebody sitting at it, a collection
 * belonging to somebody who has not shared it.
 * <p>A block cannot decline to be broken in vanilla - by the time the block itself hears
 * about it the decision has been made - so this is the loader's break event, which is the
 * one place the answer can still be no. What is refused is decided in {@link BreakRules},
 * shared with the Fabric hook so the two loaders cannot answer differently.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID)
public final class GatheringTableRules {

    private GatheringTableRules() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        BreakRules.refuse(event.getLevel(), event.getPos(), event.getPlayer())
                .ifPresent(why -> {
                    event.setCanceled(true);
                    event.getPlayer().sendSystemMessage(why);
                });
    }
}
