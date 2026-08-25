package dev.gathering.server;

import dev.gathering.core.sealed.BoosterOdds;
import dev.gathering.core.sealed.LootRichness;
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
     * What can drop: every set on offer, in the order they are offered in, and what each one
     * sells.
     *
     * <p>One object rather than a list and a map beside it. They are two halves of one answer
     * and they were two fields: a roll that read the new list and the old map picked a set
     * that was not in it and quietly dropped nothing, which is the kind of window that turns
     * up once a week on a busy server and never in a test.
     */
    private record Pool(List<String> sets, Map<String, List<String>> sells) {

        static final Pool NOTHING = new Pool(List.of(), Map.of());

        static Pool of(Map<String, List<String>> sells) {
            return sells.isEmpty() ? NOTHING : new Pool(List.copyOf(sells.keySet()), sells);
        }

        boolean isEmpty() {
            return sets.isEmpty();
        }
    }

    /** Decided at start and read on the loot thread. Replaced whole, never edited. */
    private static volatile Pool pool = Pool.NOTHING;

    /**
     * Which source and how good a chest one loot table is, worked out once each.
     *
     * <p>A global loot modifier is handed every table the game rolls - every mob, every
     * block, every chest - so this is on the hot path of anything that drops anything. The
     * answer for a given table never changes, and there are a few hundred tables, so it is
     * remembered rather than re-parsed a few thousand times a second in a farm.
     */
    private static final Map<String, Optional<Chest>> CHESTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** What a loot table is, for the purposes of dropping a pack out of it. */
    private record Chest(LootSource source, LootRichness richness) {
    }

    private SealedLoot() {
    }

    /**
     * Works out what this server can drop.
     *
     * <p>Called once at start. Does nothing at all unless collecting is on, so a play-only
     * server never fetches anything for a feature it has switched off.
     */
    public static void warm() {
        pool = Pool.NOTHING;
        var settings = ServerSettings.get();
        if (!settings.modes().collectionEnabled()) {
            return;
        }
        CollationService collation = CollationService.active().orElse(null);
        if (collation == null) {
            return;
        }
        SetsInPlay.wanted(settings)
                .thenComposeAsync(codes -> readAll(collation, codes), collation.worker())
                .whenComplete((found, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not read what this server's sets were sold as, so "
                                + "nothing drops", failure);
                        return;
                    }
                    pool = Pool.of(found);
                    LOGGER.info("Sealed product can be found in the world: {} set(s), {}",
                            found.size(), describe(found));
                });
    }

    /** Between servers, so one world's sets do not drop in the next. */
    public static void clear() {
        pool = Pool.NOTHING;
        CHESTS.clear();
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
        // Before the string is touched at all. On NeoForge this runs for every loot table the
        // game rolls, so a server that is not collecting must pay one volatile read for every
        // zombie that dies and not a pair of allocations.
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        Chest chest = CHESTS.computeIfAbsent(tableId == null ? "" : tableId,
                        id -> LootSource.of(id).map(source -> new Chest(source, LootRichness.of(id))))
                .orElse(null);
        return chest == null
                ? Optional.empty()
                : rollFrom(chest.source(), chest.richness(), random);
    }

    /**
     * A pack from one source, out of a chest this good.
     *
     * <p>Three rolls, in this order: whether a pack comes out at all, which set it is from,
     * and which of that set's products it is. The last one is where the chest matters.
     */
    public static Optional<ItemStack> rollFrom(
            LootSource source, LootRichness richness, RandomSource random) {
        if (random == null || source == null || pool.isEmpty()) {
            return Optional.empty();
        }
        if (!asked(source) || random.nextInt(source.oneIn()) != 0) {
            return Optional.empty();
        }
        return packFrom(richness, random);
    }

    /**
     * A pack, with no odds and no source.
     *
     * <p>For a chest that is meant to hold packs rather than one that might: the stock chest
     * behind a card shop's counter is part of the shop, not a lucky find, and rolling it
     * against one-in-eight would leave most of them empty. How many is the pool's to say.
     *
     * <p>Two rolls rather than three: which set it is from, and which of that set's products.
     */
    public static Optional<ItemStack> packFrom(LootRichness richness, RandomSource random) {
        Pool offering = pool;
        if (offering.isEmpty() || random == null
                || !ServerSettings.get().modes().collectionEnabled()) {
            return Optional.empty();
        }
        String set = offering.sets().get(random.nextInt(offering.sets().size()));
        List<String> kinds = offering.sells().getOrDefault(set, List.of());
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
