package dev.gathering.server;

import dev.gathering.core.sealed.BoosterOdds;
import dev.gathering.core.sealed.CoinDrops;
import dev.gathering.core.sealed.LootRichness;
import dev.gathering.core.sealed.LootSource;
import dev.gathering.core.sealed.MtgjsonProducts;
import dev.gathering.core.sealed.SealedProduct;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sealed product turning up in the world.
 * <p>The first of the two ways into a collection, and the one that decides how a server's
 * early game feels. A pack in a dungeon chest is a small event you tell somebody about; a
 * pack you can buy whenever you like is inventory.
 * <p>Which sets are on offer is the server's to say: whatever is out now, the last few
 * releases, or exactly the sets a seasonal or era server is about. Which kind of pack comes
 * out is not - a collector booster is rare everywhere and much likelier out of the chests
 * people build expeditions around, which is what makes where you looked worth caring about.
 * <p>Only boosters. A display box or a Commander deck is a thing you buy, and a chest that
 * can hold thirty packs makes the shop pointless; {@link SealedProduct#isOneBooster()} is
 * what draws that line and it is drawn once, here.
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

    /** How many sets the "this worked" line names before it stops. See {@link #describe}. */
    private static final int MOST_SETS_NAMED = 8;

    /** Decided at start and read on the loot thread. Replaced whole, never edited. */
    private static volatile Pool pool = Pool.NOTHING;

    /**
     * What each loot table is worth, worked out once each.
     * <p>A global loot modifier is handed every table the game rolls - every mob, every
     * block, every chest - so this is on the hot path of anything that drops anything. The
     * answer for a given table never changes, and there are a few hundred tables, so it is
     * remembered rather than re-parsed a few thousand times a second in a farm.
     */
    private static final Map<String, Find> FINDS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * What a loot table is, for the purposes of finding something in it.
     * <p>All three questions in one entry rather than a map each. They are answers to the same
     * string and they are wanted in the same breath, and two caches keyed on the same table are
     * two chances for one of them to be looked up and the other not.
     *
     * @param source   where this table counts as, or null for a table packs never come out of
     * @param coins    the coin band this chest pays at, or null for a table that pays none
     */
    private record Find(LootSource source, LootRichness richness, CoinDrops coins) {
    }

    private SealedLoot() {
    }

    /**
     * Works out what this server can drop.
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
                .thenCombine(collation.everySetsProducts(), SealedLoot::boostersOf)
                .whenComplete(ServerRun.stillThisRun((found, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not read what this server's sets were sold as, so "
                                + "nothing drops", failure);
                        return;
                    }
                    pool = Pool.of(found);
                    LOGGER.info("Sealed product can be found in the world: {} set(s), {}",
                            found.size(), describe(found));
                }));
    }

    /** Between servers, so one world's sets do not drop in the next. */
    public static void clear() {
        pool = Pool.NOTHING;
        FINDS.clear();
    }

    /**
     * Everything one loot table gives, which is a pack, some coins, both or neither.
     * <p>The one entry point both loaders roll through, so the order and the guards are written
     * once: NeoForge asks it from a global loot modifier and Fabric from a pool added to the
     * table, and the two behaving differently is exactly the class of bug that put the archive
     * pack out of reach on Fabric for a release.
     * <p>A chest may pay both. A pack and a coin are not the same find and there is no reason
     * one should crowd the other out - which is not true of two packs, and {@link #rollFor}
     * still says so.
     */
    public static List<ItemStack> findsIn(String tableId, LootContext context) {
        if (context == null) {
            return List.of();
        }
        // Whether a player did the killing, in the only place that knows: the same parameter
        // vanilla's own killed_by_player condition reads.
        return findsIn(tableId, context.getRandom(),
                context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER));
    }

    /**
     * The same, where whether a player was involved is already known.
     *
     * @param killedByAPlayer whether a player had a hand in the kill this table is rolling for
     */
    public static List<ItemStack> findsIn(
            String tableId, RandomSource random, boolean killedByAPlayer) {
        Optional<ItemStack> pack = rollFor(tableId, random, killedByAPlayer);
        Optional<ItemStack> coins = coinsFor(tableId, random);
        if (pack.isEmpty()) {
            // Nothing allocated on the overwhelmingly common path, which is every block broken
            // and every mob killed on the server.
            return coins.isEmpty() ? List.of() : List.of(coins.get());
        }
        return coins.isEmpty() ? List.of(pack.get()) : List.of(pack.get(), coins.get());
    }

    /**
     * A pack for this loot table, if one comes up.
     * <p>Called while loot is being rolled, so it does nothing that can block and nothing
     * that can throw. Every reason to say no is checked here rather than by whoever calls it:
     * collecting switched off, a table this mod has nothing to do with, a source the server
     * did not ask for, nothing resolved yet, or simply the odds.
     * <p>Rolled as though nothing was killed, which is what a table reached without a kill
     * behind it is. Only mob drops care, and a mob table rolled by hand is not a mob a player
     * fought.
     */
    public static Optional<ItemStack> rollFor(String tableId, RandomSource random) {
        return rollFor(tableId, random, false);
    }

    /** The same, saying whether a player had a hand in the kill. */
    public static Optional<ItemStack> rollFor(
            String tableId, RandomSource random, boolean killedByAPlayer) {
        // The archive first, and answering means the ordinary pack is not asked at all: two
        // packs out of one chest reads as a fault rather than as luck, and the rarer of the
        // two is the one worth having come out.
        Optional<ItemStack> archive = Archive.rollFor(tableId, random, killedByAPlayer);
        if (archive.isPresent()) {
            return archive;
        }
        // Before the string is touched at all. On NeoForge this runs for every loot table the
        // game rolls, so a server that is not collecting must pay one volatile read for every
        // zombie that dies and not a pair of allocations.
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        Find find = findOf(tableId);
        if (find.source() == null) {
            return Optional.empty();
        }
        if (find.source().needsAPlayer() && !killedByAPlayer) {
            // A mob a player did not fight. Otherwise a drop chute under a spawner is a pack
            // faucet nobody is playing, which is the whole thing this is meant to stop.
            return Optional.empty();
        }
        return rollFrom(find.source(), find.richness(), random);
    }

    /**
     * The coins in this chest, if it has any.
     * <p>Not part of the pack roll and not competing with it: a chest can hold both, and the
     * coin is the one of the two that is actually rare. Independent of the pack pool as well -
     * a coin is a coin whether or not this server has finished reading what its sets were sold
     * as, and a server that had not would otherwise have spent its first minutes dropping
     * nothing at all.
     */
    public static Optional<ItemStack> coinsFor(String tableId, RandomSource random) {
        if (random == null || !ServerSettings.get().modes().collectionEnabled()) {
            return Optional.empty();
        }
        CoinDrops where = findOf(tableId).coins();
        if (where == null || !asked(LootSource.STRUCTURES)) {
            // Coins are a chest thing, so they follow the chests. A server that has switched
            // structure loot off has switched the coin off with it, and its config says so.
            return Optional.empty();
        }
        if (random.nextInt(where.oneIn()) != 0) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(GatheringContent.MANA_COIN.get(),
                where.count(random.nextInt(where.spread()))));
    }

    /**
     * A pack from one source, for a roll that knows what it is rolling for.
     * <p>What the context adds is whether a player did the killing, which is the whole of the
     * mob source's gate. A data pack may write {@code {"type": "gathering:sealed_product",
     * "source": "mobs"}} into a table of its own, and that entry has to mean the same thing
     * the mod's own does - otherwise "a farm buys nothing" is true only of vanilla's tables
     * and one datapack line reopens it.
     */
    public static Optional<ItemStack> rollFrom(
            LootSource source, LootRichness richness, LootContext context) {
        if (context == null) {
            return Optional.empty();
        }
        if (source != null && source.needsAPlayer()
                && !context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER)) {
            return Optional.empty();
        }
        return rollFrom(source, richness, context.getRandom());
    }

    /**
     * A pack from one source, out of a chest this good.
     * <p>Three rolls, in this order: whether a pack comes out at all, which set it is from,
     * and which of that set's products it is. The chest matters to the first and the last.
     * <p>Says nothing about who was killing what, so a mob source rolled through here is a mob
     * source with its gate already decided. Everything in the world reaches it through the
     * overload above.
     */
    public static Optional<ItemStack> rollFrom(
            LootSource source, LootRichness richness, RandomSource random) {
        if (random == null || source == null || pool.isEmpty()) {
            return Optional.empty();
        }
        if (!asked(source) || random.nextInt(source.oneIn(richness)) != 0) {
            return Optional.empty();
        }
        return packFrom(richness, random);
    }

    /** What one loot table is, worked out the first time it is rolled and kept. */
    private static Find findOf(String tableId) {
        return FINDS.computeIfAbsent(tableId == null ? "" : tableId,
                id -> new Find(LootSource.of(id).orElse(null), LootRichness.of(id),
                        CoinDrops.of(id).orElse(null)));
    }

    /**
     * A pack, with no odds and no source.
     * <p>For a chest that is meant to hold packs rather than one that might: the stock chest
     * behind a card shop's counter is part of the shop, not a lucky find, and rolling it
     * against one-in-eight would leave most of them empty. How many is the pool's to say.
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
     * Which single boosters each of the wanted sets sold.
     * <p>Out of the one list of every set rather than a file per set. A server drawing from
     * everything ever printed reads ten megabytes once here; reading each set's own file to
     * learn the same thing would be several hundred fetches and better than a gigabyte, and
     * nothing in a chest needs what those files hold. The set's own file is read when
     * somebody opens one of its packs, which is when what is inside starts to matter.
     * <p>A set the list says nothing about drops nothing, which is the same answer as a set
     * that sold no boosters.
     */
    private static Map<String, List<String>> boostersOf(
            List<String> codes, Map<String, MtgjsonProducts.Reading> everySet) {
        Map<String, List<String>> pool = new LinkedHashMap<>();
        for (String code : codes) {
            MtgjsonProducts.Reading sold = everySet.get(code);
            if (sold == null) {
                continue;
            }
            List<String> kinds = new ArrayList<>();
            for (SealedProduct booster : sold.boosters()) {
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
        }
        return Map.copyOf(pool);
    }

    /**
     * The sources the config asked for, and the settings they were read out of.
     * <p>One object rather than two fields, for the reason {@link Pool} is: a read that took
     * the new settings and the old set would answer about a config nobody is running.
     */
    private record Asked(Object from, java.util.Set<LootSource> sources) {
    }

    /** Worked out when the settings change, which is at start and on a reload. */
    private static volatile Asked asked = new Asked(null, java.util.Set.of());

    /**
     * Whether the server's config asked for this source.
     * <p>Kept rather than re-read, because mobs made this hot. It used to walk the config's
     * list and lowercase every entry on the way, which cost four small allocations - once per
     * chest, which nobody would notice, and now once per mob that dies on the server. The
     * config's own record is the key: settings are replaced whole on a reload, so an identity
     * check is exactly the question "is this still the config I read".
     */
    private static boolean asked(LootSource source) {
        var collecting = ServerSettings.get().collecting();
        Asked known = asked;
        if (known.from() != collecting) {
            java.util.EnumSet<LootSource> found = java.util.EnumSet.noneOf(LootSource.class);
            for (String named : collecting.packLootSources()) {
                LootSource.named(named).ifPresent(found::add);
            }
            known = new Asked(collecting, java.util.Collections.unmodifiableSet(found));
            asked = known;
        }
        return known.sources().contains(source);
    }

    /**
     * The pool in one line of log, and one line it stays.
     * <p>A server drawing from every set has a hundred and forty of them, each with a handful
     * of booster kinds beside it. Naming all of them is several thousand characters in
     * somebody's console for a line that is meant to say "this worked".
     */
    private static String describe(Map<String, List<String>> pool) {
        List<String> said = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : pool.entrySet()) {
            if (said.size() == MOST_SETS_NAMED) {
                said.add("and " + (pool.size() - MOST_SETS_NAMED) + " more");
                break;
            }
            said.add(entry.getKey().toUpperCase(java.util.Locale.ROOT)
                    + " " + entry.getValue());
        }
        return String.join(", ", said);
    }
}
