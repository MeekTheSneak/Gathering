package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.command.GatheringCommands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Registers the command tree built in {@code :common}. */
@EventBusSubscriber(modid = Gathering.MOD_ID)
public final class GatheringCommandRegistration {

    private GatheringCommandRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(GatheringCommands.root());
    }
}
