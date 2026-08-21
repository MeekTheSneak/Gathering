package dev.gathering.fabric;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.service.CardDataService;
import java.io.IOException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                cardData = CardDataService.create(Platform.get());
            } catch (IOException e) {
                throw new IllegalStateException("Could not open the Gathering card metadata cache", e);
            }
            cardData.warmCache().thenAccept(count ->
                    LOGGER.info("Card metadata cache warmed: {} printings", count));
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (cardData != null) {
                cardData.close();
                cardData = null;
            }
        });

        LOGGER.info("{} loaded. {}", Gathering.MOD_NAME, Gathering.FAN_CONTENT_DISCLAIMER);
    }
}
