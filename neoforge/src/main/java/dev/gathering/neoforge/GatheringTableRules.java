package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.block.TableSeats;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Refuses to let a table be broken out from under the people sitting at it.
 *
 * <p>A block cannot decline to be broken in vanilla - by the time the block itself hears
 * about it the decision has been made - so this is the loader's break event, which is the
 * one place the answer can still be no.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID)
public final class GatheringTableRules {

    private GatheringTableRules() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (TableSeats.mayBreak(event.getLevel(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        event.getPlayer().sendSystemMessage(Component.translatable("message.gathering.table_in_use"));
    }
}
