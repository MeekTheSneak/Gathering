package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.service.CardDataService;
import java.io.IOException;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entry point.
 *
 * <p>Does three things and no more: register content, provide the platform service, and own
 * the card pipeline's lifetime. Everything else lives in {@code common} or, better, in
 * {@code core}.
 */
@Mod(Gathering.MOD_ID)
public final class GatheringNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(Gathering.MOD_NAME);

    private CardDataService cardData;

    public GatheringNeoForge(IEventBus modBus) {
        GatheringRegistration.bootstrap(modBus);

        // Game bus: these are things happening in the game, not mod setup.
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        LOGGER.info("{} loaded. {}", Gathering.MOD_NAME, Gathering.FAN_CONTENT_DISCLAIMER);
    }

    private void onServerStarting(ServerStartingEvent event) {
        try {
            cardData = CardDataService.create(Platform.get());
        } catch (IOException e) {
            // Without a cache directory there is no card pipeline, and every later failure
            // would be a confusing symptom of this one.
            throw new IllegalStateException("Could not open the Gathering card metadata cache", e);
        }
        cardData.warmCache().thenAccept(count -> LOGGER.info("Card metadata cache warmed: {} printings", count));
    }

    private void onServerStopped(ServerStoppedEvent event) {
        if (cardData != null) {
            cardData.close();
            cardData = null;
        }
    }
}
