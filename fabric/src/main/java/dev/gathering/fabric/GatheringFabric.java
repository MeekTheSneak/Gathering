package dev.gathering.fabric;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.service.CardDataService;
import dev.gathering.service.ServerSettings;
import java.io.IOException;
import dev.gathering.command.GatheringCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point. The same three jobs as the NeoForge one: register content, provide
 * the platform service, own the card pipeline's lifetime.
 */
public final class GatheringFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Gathering.MOD_NAME);

    private CardDataService cardData;

    @Override
    public void onInitialize() {
        GatheringRegistration.bootstrap();
        GatheringNetwork.bootstrap();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> dispatcher.register(GatheringCommands.root()));

        // A block cannot decline to be broken in vanilla - by the time the block hears about
        // it the decision is made - so a table in use is protected at the break event, which
        // is the one place the answer can still be no.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (dev.gathering.block.TableSeats.mayBreak(level, pos)) {
                return true;
            }
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("message.gathering.table_in_use"));
            return false;
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerSettings.load(Platform.get());
            try {
                cardData = CardDataService.start(Platform.get());
            } catch (IOException e) {
                throw new IllegalStateException("Could not open the Gathering card metadata cache", e);
            }
            cardData.warmCache().thenAccept(count ->
                    LOGGER.info("Card metadata cache warmed: {} printings", count));
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ServerSettings.clear();
            if (cardData != null) {
                cardData.close();
                cardData = null;
            }
        });

        LOGGER.info("{} loaded. {}", Gathering.MOD_NAME, Gathering.FAN_CONTENT_DISCLAIMER);
    }
}
