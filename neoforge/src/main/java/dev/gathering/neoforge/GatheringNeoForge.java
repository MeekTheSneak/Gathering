package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.service.CardDataService;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
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
    private CollationService collation;

    public GatheringNeoForge(IEventBus modBus) {
        GatheringRegistration.bootstrap(modBus);

        // Game bus: these are things happening in the game, not mod setup.
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GatheringNeoForge::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(GatheringNeoForge::onEntityInteract);
        // A player's wants list, read when they arrive and let go when they leave. Both are
        // needed: without the first a client draws its first collection screen with no marks
        // on it, and without the second a long-running server holds every list ever read.
        NeoForge.EVENT_BUS.addListener(GatheringNeoForge::onPlayerJoined);
        NeoForge.EVENT_BUS.addListener(GatheringNeoForge::onPlayerLeft);

        LOGGER.info("{} loaded. {}", Gathering.MOD_NAME, Gathering.FAN_CONTENT_DISCLAIMER);
    }

    private void onServerStarting(ServerStartingEvent event) {
        ServerSettings.load(Platform.get());
        try {
            cardData = CardDataService.start(Platform.get());
            collation = CollationService.start(Platform.get());
        } catch (IOException e) {
            // Without a cache directory there is no card pipeline, and every later failure
            // would be a confusing symptom of this one.
            throw new IllegalStateException("Could not open the Gathering card metadata cache", e);
        }
        cardData.warmCache().thenAccept(count -> LOGGER.info("Card metadata cache warmed: {} printings", count));
        dev.gathering.server.CurrentSet.resolve();
        dev.gathering.server.SealedLoot.warm();
        dev.gathering.server.Archive.warm();
        dev.gathering.server.CardShop.warm();
        dev.gathering.server.LoanerDecks.warm();
        // The one line the access transformer exists for. Everything about which building
        // goes into which pool is LocalGameStore's; this reaches the list.
        dev.gathering.village.LocalGameStore.addToVillages(event.getServer(),
                (pool, building, times) -> {
                    for (int copy = 0; copy < times; copy++) {
                        pool.templates.add(building);
                    }
                });
    }

    /** What a card shop's keeper sells, at each of their levels. */
    private static void onPlayerJoined(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            dev.gathering.server.Wants.joined(player);
        }
    }

    private static void onPlayerLeft(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            dev.gathering.server.Wants.left(player);
            dev.gathering.server.ReplayWatch.forget(player.getUUID());
        }
    }

    private static void onVillagerTrades(
            net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != dev.gathering.village.GatheringVillagers.SHOPKEEPER.get()) {
            return;
        }
        for (int level = 1; level <= dev.gathering.core.sealed.ShopTier.LEVELS; level++) {
            event.getTrades().get(level).addAll(dev.gathering.village.ShopTrades.at(level));
        }
    }

    /**
     * Brings a shopkeeper's counter back in step before anybody looks at it.
     *
     * <p>Here rather than on a tick because this is the only moment it matters, and because a
     * villager nobody is talking to is a villager whose offers nobody can see.
     */
    private static void onEntityInteract(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof net.minecraft.world.entity.npc.Villager villager) {
            dev.gathering.village.Shopkeepers.refresh(villager);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        dev.gathering.server.ServerState.forgetTheWorld();
        if (cardData != null) {
            cardData.close();
            cardData = null;
        }
        if (collation != null) {
            collation.close();
            collation = null;
        }
    }
}
