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

    /**
     * Whether the mod may add its pool to a table from this source.
     *
     * <p>Everything except a table another mod built in {@code REPLACE}: there is no telling
     * what that is meant to be any more. A data pack that rewrites a dungeon chest is still
     * that dungeon chest, and this used to ask {@code isBuiltin()} - which is false for a data
     * pack - so a server with a loot pack installed got no card packs from the chests it had
     * edited, while the same world on NeoForge did. That modifier runs for every table there
     * is and never had to ask the question at all.
     *
     * <p>Its own method so the rule can be checked against every value of the enum. The case
     * that went wrong cannot be reproduced in a game test - a test world has no external data
     * pack to make a table {@code DATA_PACK} - so the alternative was a rule nothing checked.
     */
    public static boolean mayModify(net.fabricmc.fabric.api.loot.v3.LootTableSource source) {
        return source != net.fabricmc.fabric.api.loot.v3.LootTableSource.REPLACED;
    }

    @Override
    public void onInitialize() {
        GatheringRegistration.bootstrap();
        GatheringNetwork.bootstrap();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> dispatcher.register(GatheringCommands.root()));

        // A block cannot decline to be broken in vanilla - by the time the block hears about
        // it the decision is made - so what the mod protects is protected at the break event,
        // which is the one place the answer can still be no. Which breaks are refused is
        // decided in BreakRules, shared with the NeoForge hook.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            java.util.Optional<net.minecraft.network.chat.Component> why =
                    dev.gathering.block.BreakRules.refuse(level, pos, player);
            why.ifPresent(player::sendSystemMessage);
            return why.isEmpty();
        });

        // Fabric hands a mod the table as a builder, so a pack goes in as a pool of its
        // own: the chest's own loot is untouched and a pack is one extra thing in it rather
        // than something taking a slot away. Which tables are eligible is decided here,
        // once per table at load, rather than on every roll. (NeoForge cannot append to a
        // loaded table and uses a global loot modifier instead - see PackLootModifier.)
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!mayModify(source)) {
                return;
            }
            String table = key.location().toString();
            // Two reasons a table gets an entry, and only one of them was checked. A pack
            // source is a chest or the fishing table; an archive table is a boss, which is
            // not a pack source and never will be. Asking only about the first meant the
            // rarest thing in the mod could not drop from the four fights it was written for
            // - on this loader alone, because NeoForge's modifier runs for every table there
            // is and never had to ask.
            java.util.Optional<dev.gathering.core.sealed.LootSource> from =
                    dev.gathering.core.sealed.LootSource.of(table);
            boolean archive = dev.gathering.core.sealed.ArchiveDrops.of(table).isPresent();
            if (from.isEmpty() && !archive) {
                return;
            }
            tableBuilder.withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0f))
                    .add(dev.gathering.loot.PackLootEntry.forTable(
                            from.orElse(null), dev.gathering.core.sealed.LootRichness.of(table),
                            table)));
        });

        // A player's wants list, read when they arrive and let go when they leave. Both are
        // needed: without the first a client draws its first collection screen with no marks
        // on it, and without the second a long-running server holds every list ever read.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> dev.gathering.server.Wants.joined(handler.getPlayer()));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    dev.gathering.server.Wants.left(handler.getPlayer());
                    dev.gathering.server.ReplayWatch.forget(handler.getPlayer().getUUID());
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
            dev.gathering.server.Archive.warm();
            dev.gathering.server.CardShop.clear();
            dev.gathering.server.LoanerDecks.warm();
            // The one line the access widener exists for. Everything about which building
            // goes into which pool is LocalGameStore's; this reaches the list.
            dev.gathering.village.LocalGameStore.addToVillages(server,
                    (pool, building, times) -> {
                        for (int copy = 0; copy < times; copy++) {
                            pool.templates.add(building);
                        }
                    });
        });

        // A shopkeeper's counter is brought back in step just before somebody looks at it, so
        // every card shop in the world is stocking the same thing at the same time.
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, level, hand, entity, hit) -> {
                    if (entity instanceof net.minecraft.world.entity.npc.Villager villager) {
                        dev.gathering.village.Shopkeepers.refresh(villager);
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            dev.gathering.server.ServerState.forgetTheWorld();
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
