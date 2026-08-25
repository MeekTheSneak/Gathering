package dev.gathering.server;

import dev.gathering.core.config.GatheringConfig;
import dev.gathering.core.sealed.BoosterOdds;
import dev.gathering.core.sealed.LootRichness;
import dev.gathering.core.sealed.LootSets;
import dev.gathering.core.sealed.LootSource;
import dev.gathering.core.sealed.SealedProduct;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Which sets are on offer is the server's to say: whatever is out now, the last few
 * releases, or exactly the sets a seasonal or era server is about. Which kind of pack comes
 * out is not - a collector booster is rare everywhere and much likelier out of the chests
 * people build expeditions around, which is what makes where you looked worth caring about.
 *
 * <p>Only boosters. A display box or a Commander deck is a thing you buy, and a chest that
 * can hold thirty packs makes the shop pointless; {@link SealedProduct#isOneBooster()} is
 * what draws that line and it is drawn once, here.
 *
 * <p>What can drop is worked out once, when the server starts, and read from memory
 * afterwards. Loot is rolled deep inside the game with no time to reach a network, so this
 * never asks: a server that has only just come up drops nothing for the moment it takes to
 * read what its sets were sold as, and drops packs from then on.
 */
public final class SealedLoot {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /**
     * What can drop, by set, decided at start and read on the loot thread.
     *
     * <p>Replaced whole rather than added to, so a reader either sees a set or does not and
     * never sees half of one.
     */
    private static volatile Map<String, List<String>> available = Map.of();

    /** The sets in the order they will be offered in, so a roll is repeatable. */
    private static volatile List<String> sets = List.of();

    private SealedLoot() {
    }

    /**
     * Works out what this server can drop.
     *
     * <p>Called once at start. Does nothing at all unless collecting is on, so a play-only
     * server never fetches anything for a feature it has switched off.
     */
    public static void warm() {
        available = Map.of();
        sets = List.of();
        var settings = ServerSettings.get();
        if (!settings.modes().collectionEnabled()) {
            return;
        }
        CollationService collation = CollationService.active().orElse(null);
        if (collation == null) {
            return;
        }
        wanted(settings)
                .thenComposeAsync(codes -> readAll(collation, codes), collation.worker())
                .whenComplete((pool, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not read what this server's sets were sold as, so "
                                + "nothing drops", failure);
                        return;
                    }
                    available = pool;
                    sets = List.copyOf(pool.keySet());
                    LOGGER.info("Sealed product can be found in the world: {} set(s), {}",
                            pool.size(), describe(pool));
                });
    }

    /** Between servers, so one world's sets do not drop in the next. */
    public static void clear() {
        available = Map.of();
        sets = List.of();
    }

    /**
     * A pack for this loot table, if one comes up.
     *
     * <p>Called while loot is being rolled, so it does nothing that can block and nothing
     * that can throw. Every reason to say no is checked here rather than by whoever calls it:
     * collecting switched off, a table this mod has nothing to do with, a source the server
     * did not ask for, nothing resolved yet, or simply the odds.
     */
    public static Optional<ItemStack> rollFor(String tableId, RandomSource random) {
        LootSource source = LootSource.of(tableId).orElse(null);
        return source == null
                ? Optional.empty()
                : rollFrom(source, LootRichness.of(tableId), random);
    }

    /**
     * A pack from one source, out of a chest this good.
     *
     * <p>Three rolls, in this order: whether a pack comes out at all, which set it is from,
     * and which of that set's products it is. The last one is where the chest matters.
     */
    public static Optional<ItemStack> rollFrom(
            LootSource source, LootRichness richness, RandomSource random) {
        Map<String, List<String>> pool = available;
        List<String> offering = sets;
        if (pool.isEmpty() || offering.isEmpty() || random == null || source == null) {
            return Optional.empty();
        }
        if (!ServerSettings.get().modes().collectionEnabled() || !asked(source)) {
            return Optional.empty();
        }
        if (random.nextInt(source.oneIn()) != 0) {
            return Optional.empty();
        }

        String set = offering.get(random.nextInt(offering.size()));
        List<String> kinds = pool.getOrDefault(set, List.of());
        Map<String, Integer> weights = BoosterOdds.weightsFor(kinds, richness);
        int total = BoosterOdds.totalOf(weights);
        if (total <= 0) {
            return Optional.empty();
        }
        String kind = BoosterOdds.pick(weights, random.nextInt(total));
        return kind == null
                ? Optional.empty()
                : Optional.of(PackItem.of(new PackComponent(set, kind)));
    }

    // ------------------------------------------------------------------ bits

    /**
     * Which sets this server draws its packs from, once they are known.
     *
     * <p>What to do with the three answers is {@link LootSets}'s and is checked there; what
     * happens here is going and getting them. "Current" and "recent" come out of the same
     * one request, so asking for both costs no more than asking for either.
     */
    private static CompletableFuture<List<String>> wanted(GatheringConfig settings) {
        List<String> named = settings.collecting().lootSets();
        if (!LootSets.needsTheReleaseList(named)) {
            return CompletableFuture.completedFuture(
                    LootSets.wanted(named, Optional.empty(), List.of()));
        }
        CompletableFuture<List<String>> recent =
                LootSets.needsMoreThanTheNewest(named)
                        ? CurrentSet.recent(settings.collecting().lootRecentSets())
                        : CompletableFuture.completedFuture(List.of());
        return CurrentSet.whenKnown().thenCombine(recent, (current, releases) -> {
            List<String> all = LootSets.wanted(named, current, releases);
            if (all.isEmpty()) {
                LOGGER.warn("Collecting is on and no set could be worked out, so nothing drops. "
                        + "Name one in collection.loot_sets to run without asking Scryfall.");
            }
            return all;
        });
    }

    /**
     * What each set really sold, read one set at a time.
     *
     * <p>On the collation worker, one after another, because every set is a file to fetch and
     * a server asking for a dozen of them at once is a server asking somebody else's host for
     * forty megabytes at once.
     */
    private static CompletableFuture<Map<String, List<String>>> readAll(
            CollationService collation, List<String> codes) {
        CompletableFuture<Map<String, List<String>>> reading =
                CompletableFuture.completedFuture(new LinkedHashMap<>());
        for (String code : codes) {
            reading = reading.thenCompose(pool -> collation.productsFor(code)
                    .handle((found, failure) -> {
                        if (failure != null) {
                            LOGGER.warn("Could not read what {} was sold as, so it drops nothing",
                                    code, failure);
                            return pool;
                        }
                        List<String> kinds = new ArrayList<>();
                        for (SealedProduct booster : found.boosters()) {
                            // Only ever a single booster: a box or a precon is the shop's.
                            SealedProduct.Booster names = booster.asBooster();
                            if (names != null && names.setCode().equals(code)
                                    && !kinds.contains(names.kind())) {
                                kinds.add(names.kind());
                            }
                        }
                        if (!kinds.isEmpty()) {
                            pool.put(code, List.copyOf(kinds));
                        }
                        return pool;
                    }));
        }
        return reading.thenApply(Map::copyOf);
    }

    /** Whether the server's config asked for this source. */
    private static boolean asked(LootSource source) {
        for (String named : ServerSettings.get().collecting().packLootSources()) {
            if (LootSource.named(named).orElse(null) == source) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Map<String, List<String>> pool) {
        List<String> said = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : pool.entrySet()) {
            said.add(entry.getKey().toUpperCase(java.util.Locale.ROOT)
                    + " " + entry.getValue());
        }
        return String.join(", ", said);
    }
}
