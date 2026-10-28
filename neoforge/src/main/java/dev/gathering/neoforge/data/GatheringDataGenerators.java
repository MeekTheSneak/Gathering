package dev.gathering.neoforge.data;

import dev.gathering.Gathering;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Data generation entry point.
 * <p>{@code @EventBusSubscriber} routes the event to the correct bus on its own, which
 * removes the wrong-bus failure mode entirely. The class is only ever loaded when
 * {@code GatherDataEvent} fires, which is why it can name client-side provider classes
 * without risking a dedicated server.
 */
// The mod bus said out loud. NeoForge from about 21.1.100 works the bus out from the event, and this mod was
// built on one of those; on the earlier 21.1 releases its range admits, a mod-bus event on the game bus stopped
// the mod loading at all ("IModBusEvent events are not allowed on the common NeoForge bus").
@SuppressWarnings("removal")
@EventBusSubscriber(modid = Gathering.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GatheringDataGenerators {

    private GatheringDataGenerators() {
    }

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        generator.addProvider(
                event.includeClient(),
                new GatheringItemModels(generator.getPackOutput(), event.getExistingFileHelper()));
        generator.addProvider(
                event.includeServer(),
                new GatheringLootModifiers(generator.getPackOutput(), event.getLookupProvider()));
    }
}
