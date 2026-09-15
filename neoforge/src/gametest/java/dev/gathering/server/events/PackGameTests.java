package dev.gathering.server.events;

import dev.gathering.Gathering;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * The in-world tests that need another mod installed, registered only when it is.
 * <p>Not {@code @GameTestHolder}: a holder is registered whatever is installed, and these tests
 * reach into the other mod's classes. The gate runs without those mods and never sees them;
 * {@code ./gradlew runPackGameTestServer} with the jars in {@code neoforge/runs/pack-tests/mods}
 * runs them beside everything else.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PackGameTests {

    private PackGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (ModList.get().isLoaded("sable")) {
            event.register(SableTablesGameTest.class);
        }
        if (ModList.get().isLoaded("create")) {
            event.register(dev.gathering.neoforge.compat.create.CreateDisplayGameTest.class);
        }
    }
}
