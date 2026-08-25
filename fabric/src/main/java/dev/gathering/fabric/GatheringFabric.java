package dev.gathering.fabric;

import dev.gathering.Gathering;
import dev.gathering.platform.Platform;
import dev.gathering.service.CardDataService;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.io.IOException;
import dev.gathering.command.GatheringCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point. The same three jobs as the NeoForge one: register content, provide
 * the platform service, own the card pipeline's lifetime.
 */
public final class GatheringFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Gathering.MOD_NAME);

    private CardDataService cardData;
    private CollationService collation;

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

        // Fabric hands a mod the table as a builder, so a pack goes in as a pool of its
        // own: the chest's own loot is untouched and a pack is one extra thing in it rather
        // than something taking a slot away. Which tables are eligible is decided here,
        // once per table at load, rather than on every roll. (NeoForge cannot append to a
        // loaded table and uses a global loot modifier instead - see PackLootModifier.)
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            dev.gathering.core.sealed.LootSource.of(key.location().toString()).ifPresent(from ->
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1.0f))
                            .add(dev.gathering.loot.PackLootEntry.forSource(from))));
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerSettings.load(Platform.get());
            try {
                cardData = CardDataService.start(Platform.get());
                collation = CollationService.start(Platform.get());
            } catch (IOException e) {
                throw new IllegalStateException("Could not open the Gathering card metadata cache", e);
            }
            cardData.warmCache().thenAccept(count ->
                    LOGGER.info("Card metadata cache warmed: {} printings", count));
            dev.gathering.server.CurrentSet.resolve();
            dev.gathering.server.SealedLoot.warm();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ServerSettings.clear();
            dev.gathering.server.SealedLoot.clear();
            dev.gathering.server.CurrentSet.clear();
            if (cardData != null) {
                cardData.close();
                cardData = null;
            }
            if (collation != null) {
                collation.close();
                collation = null;
            }
        });

        LOGGER.info("{} loaded. {}", Gathering.MOD_NAME, Gathering.FAN_CONTENT_DISCLAIMER);
    }
}
