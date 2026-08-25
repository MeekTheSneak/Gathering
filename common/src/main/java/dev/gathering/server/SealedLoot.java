package dev.gathering.server;

import dev.gathering.core.sealed.LootSource;
import dev.gathering.core.sealed.SealedProduct;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sealed product turning up in the world.
 *
 * <p>The first of the two ways into a collection, and the one that decides how a server's
 * early game feels. A pack in a dungeon chest is a small event you tell somebody about; a
 * pack you can buy whenever you like is inventory.
 *
 * <p>What can drop is worked out once, when the server starts, and read from memory
 * afterwards. Loot is rolled deep inside the game with no time to reach a network, so this
 * never asks: a server that has only just come up drops nothing for the moment it takes to
 * read what the set was sold as, and then drops packs from then on.
 *
 * <p>Only products that were really sold. The list comes from the same place the shop's will,
 * so a set that never had a booster never drops one however the config is written.
 */
public final class SealedLoot {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** What can drop, decided at start and read on the loot thread. */
    private static volatile List<PackComponent> available = List.of();

    private SealedLoot() {
    }

    /**
     * Works out what this server can drop.
     *
     * <p>Called once at start. Does nothing at all unless collecting is on, so a play-only
     * server never fetches anything for a feature it has switched off.
     */
    public static void warm() {
        available = List.of();
        if (!ServerSettings.get().modes().collectionEnabled()) {
            return;
        }
        CollationService collation = CollationService.active().orElse(null);
        if (collation == null) {
            return;
        }
        // Which set this is happens off on its own thread - it may be a question for
        // Scryfall - so this waits for it rather than reading the config itself. A server
        // with no current set at all has already said so; there is nothing to add here.
        CurrentSet.whenKnown()
                .thenComposeAsync(current -> current
                        .map(collation::productsFor)
                        .orElseGet(() -> CompletableFuture.completedFuture(null)),
                        collation.worker())
                .whenComplete((reading, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not read what the current set was sold as, so "
                                + "nothing drops", failure);
                        return;
                    }
                    if (reading == null) {
                        return;
                    }
                    List<PackComponent> packs = new ArrayList<>();
                    for (SealedProduct booster : reading.boosters()) {
                        packs.add(new PackComponent(
                                booster.asBooster().setCode(), booster.asBooster().kind()));
                    }
                    available = List.copyOf(packs);
                    LOGGER.info("Sealed product from {} can be found in the world: {} kind(s) "
                            + "of pack", reading.setCode(), packs.size());
                });
    }

    /** Between servers, so one world's set does not drop in the next. */
    public static void clear() {
        available = List.of();
    }

    /**
     * A pack for this loot table, if one comes up.
     *
     * <p>For a caller that has a table's name and not a source - a loot modifier, which is
     * handed every table in the game and has to work out for itself whether it cares.
     */
    public static Optional<ItemStack> rollFor(String tableId, RandomSource random) {
        LootSource source = LootSource.of(tableId).orElse(null);
        return source == null ? Optional.empty() : rollFrom(source, random);
    }

    /**
     * A pack from one source, if one comes up.
     *
     * <p>Called while loot is being rolled, so it does nothing that can block and nothing
     * that can throw. Every reason to say no is checked here rather than by whoever calls
     * it: collecting switched off, a source the server did not ask for, nothing resolved
     * yet, or simply the odds.
     */
    public static Optional<ItemStack> rollFrom(LootSource source, RandomSource random) {
        List<PackComponent> packs = available;
        if (packs.isEmpty() || random == null || source == null) {
            return Optional.empty();
        }
        if (!ServerSettings.get().modes().collectionEnabled() || !wanted(source)) {
            return Optional.empty();
        }
        if (random.nextInt(source.oneIn()) != 0) {
            return Optional.empty();
        }
        return Optional.of(PackItem.of(packs.get(random.nextInt(packs.size()))));
    }

    /** Whether the server's config asked for this source. */
    private static boolean wanted(LootSource source) {
        for (String named : ServerSettings.get().collecting().packLootSources()) {
            if (LootSource.named(named).orElse(null) == source) {
                return true;
            }
        }
        return false;
    }
}
