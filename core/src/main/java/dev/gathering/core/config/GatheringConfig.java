package dev.gathering.core.config;

import dev.gathering.core.sealed.LootSource;
import dev.gathering.core.table.TableCluster;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One server's settings, read.
 *
 * <p>Modes are config, not forks. A server runs import mode, collection mode, or both, and
 * that is a choice made in one file rather than by installing a different build - which is
 * why this exists at all, and why every switch has a working default so a server that never
 * opens the file still runs.
 *
 * <p>What it will not do is fail quietly. A value out of range is clamped and said out loud,
 * a combination that cannot work is corrected and said out loud, and a setting nobody
 * recognises is reported. All three go in {@link #notes}, because a server owner reading
 * their config file and a server behaving differently from it is the one problem no amount of
 * in-game polish can rescue.
 *
 * <p>Pure. Reading the file off disk and writing the default one belong to the layer that has
 * a folder.
 */
public record GatheringConfig(
        Modes modes,
        Importing importing,
        Collecting collecting,
        Tables tables,
        Ante ante,
        List<String> notes) {

    /** The two master switches. */
    public record Modes(boolean importEnabled, boolean collectionEnabled) {
    }

    /**
     * @param formats informational only; the deck check is offered per game, never enforced
     */
    public record Importing(boolean allowAllPlayers, List<String> formats) {
    }

    public record Collecting(
            List<String> packLootSources,
            List<String> lootSets,
            int lootRecentSets,
            boolean sealedStoreEnabled,
            String sealedPriceItem,
            int sealedPriceBooster,
            String currentSet,
            int stallRotationHours,
            int stallRotatingSlots,
            String boosterModel) {
    }

    public record Tables(int maxTablesLoaded, int maxClusterTables, int maxCardsPerSession) {
    }

    public record Ante(
            boolean enabled, int cardsPerPlayer, List<String> exclusions,
            boolean allowPerTableOptOut) {
    }

    public GatheringConfig {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * Settings the file describes and this version does not act on yet, and what is missing.
     *
     * <p>The file is written from the design rather than from what happens to be finished, so
     * a server owner can see the shape of the thing. That is only honest as long as changing
     * one of these says so: a setting that is read, kept, and never consulted is a server
     * running differently from the file its owner is reading, which is the one failure a
     * config file cannot recover from.
     *
     * <p>Entries come off this list as the feature behind them lands.
     */
    private static final java.util.Map<String, String> NOT_BUILT_YET = java.util.Map.ofEntries(
            java.util.Map.entry("collection.sealed_store_enabled", "the shop"),
            java.util.Map.entry("collection.sealed_price_item", "the shop"),
            java.util.Map.entry("collection.sealed_price_booster", "the shop"),
            java.util.Map.entry("collection.stall_rotation_hours", "the rotating shelf"),
            java.util.Map.entry("collection.stall_rotating_slots", "the rotating shelf"),
            java.util.Map.entry("table.max_tables_loaded", "a limit on tables kept loaded"),
            java.util.Map.entry("table.max_cluster_tables", "a cluster cap other than four"),
            java.util.Map.entry("table.max_cards_per_session", "a limit on cards in a session"),
            java.util.Map.entry("ante.enabled", "playing for keeps"),
            java.util.Map.entry("ante.cards_per_player", "playing for keeps"),
            java.util.Map.entry("ante.exclusions", "playing for keeps"),
            java.util.Map.entry("ante.allow_per_table_opt_out", "playing for keeps"));

    /**
     * Where sealed product turns up on a server that has not said otherwise.
     *
     * <p>Every source there is, because a server that switched collecting on wants the
     * feature. Turning one off is a decision worth making deliberately; having one silently
     * absent from the file you were given is not.
     */
    private static final List<String> DEFAULT_LOOT_SOURCES = List.of(
            LootSource.FISHING.configName(),
            LootSource.STRUCTURES.configName(),
            LootSource.DIGGING.configName());

    /** What {@code loot_sets} means by "whatever is current". */
    public static final String LOOT_SETS_CURRENT = "current";

    /** And by "the last few releases", however many {@code loot_recent_sets} says. */
    public static final String LOOT_SETS_RECENT = "recent";

    /**
     * Which sets a server's packs come from when it has not said.
     *
     * <p>Just the current one. A server that wants more says so, because every extra set is
     * another few megabytes fetched at start - and because a server pinned to one release is
     * what most people mean by turning collecting on.
     */
    private static final List<String> DEFAULT_LOOT_SETS = List.of(LOOT_SETS_CURRENT);

    /** Every setting name this understands, so a typo in the file can be spotted. */
    public static Set<String> knownKeys() {
        return new LinkedHashSet<>(List.of(
                "modes.import_enabled",
                "modes.collection_enabled",
                "import.allow_all_players",
                "import.formats",
                "collection.pack_loot_sources",
                "collection.loot_sets",
                "collection.loot_recent_sets",
                "collection.sealed_store_enabled",
                "collection.sealed_price_item",
                "collection.sealed_price_booster",
                "collection.current_set",
                "collection.stall_rotation_hours",
                "collection.stall_rotating_slots",
                "collection.booster_model",
                "table.max_tables_loaded",
                "table.max_cluster_tables",
                "table.max_cards_per_session",
                "ante.enabled",
                "ante.cards_per_player",
                "ante.exclusions",
                "ante.allow_per_table_opt_out"));
    }

    /** What a server that has never touched the file runs as. */
    public static GatheringConfig defaults() {
        try {
            return read(Toml.empty());
        } catch (TomlException impossible) {
            throw new IllegalStateException("The defaults cannot be unreadable", impossible);
        }
    }

    public static GatheringConfig read(Toml toml) throws TomlException {
        List<String> notes = new ArrayList<>();

        boolean importEnabled = toml.flag("modes.import_enabled", true);
        boolean collectionEnabled = toml.flag("modes.collection_enabled", false);

        Importing importing = new Importing(
                toml.flag("import.allow_all_players", true),
                List.copyOf(toml.strings("import.formats", List.of("commander"))));

        Collecting collecting = new Collecting(
                lootSources(toml.strings("collection.pack_loot_sources", DEFAULT_LOOT_SOURCES), notes),
                lootSets(toml.strings("collection.loot_sets", DEFAULT_LOOT_SETS), notes),
                noted("collection.loot_recent_sets",
                        clamped(toml.number("collection.loot_recent_sets", 12), 1, 64,
                                "collection.loot_recent_sets", notes), 12, notes),
                noted("collection.sealed_store_enabled",
                        toml.flag("collection.sealed_store_enabled", true), true, notes),
                noted("collection.sealed_price_item",
                        toml.string("collection.sealed_price_item", "minecraft:diamond"),
                        "minecraft:diamond", notes),
                noted("collection.sealed_price_booster",
                        clamped(toml.number("collection.sealed_price_booster", 2), 1, 512,
                                "collection.sealed_price_booster", notes), 2, notes),
                noted("collection.current_set",
                        toml.string("collection.current_set", "auto").trim().toLowerCase(Locale.ROOT),
                        "auto", notes),
                noted("collection.stall_rotation_hours",
                        clamped(toml.number("collection.stall_rotation_hours", 4), 1, 24 * 7,
                                "collection.stall_rotation_hours", notes), 4, notes),
                noted("collection.stall_rotating_slots",
                        clamped(toml.number("collection.stall_rotating_slots", 6), 1, 32,
                                "collection.stall_rotating_slots", notes), 6, notes),
                toml.string("collection.booster_model", "play").trim().toLowerCase(Locale.ROOT));

        Tables tables = new Tables(
                noted("table.max_tables_loaded",
                        clamped(toml.number("table.max_tables_loaded", 16), 1, 256,
                                "table.max_tables_loaded", notes), 16, notes),
                noted("table.max_cluster_tables",
                        clamped(toml.number("table.max_cluster_tables", TableCluster.MAX_TABLES),
                                1, TableCluster.MAX_TABLES, "table.max_cluster_tables", notes),
                        TableCluster.MAX_TABLES, notes),
                noted("table.max_cards_per_session",
                        clamped(toml.number("table.max_cards_per_session", 1600), 100, 20_000,
                                "table.max_cards_per_session", notes), 1600, notes));

        boolean anteWanted = toml.flag("ante.enabled", false);
        if (anteWanted && !collectionEnabled) {
            // Straight out of the brief, and not a preference: ante is players putting cards
            // up as property, and on a server where cards are not property there is nothing
            // to put up. Corrected rather than refused, so the server still starts.
            notes.add("ante.enabled needs modes.collection_enabled, so ante is off");
            anteWanted = false;
        }
        Ante ante = new Ante(
                noted("ante.enabled", anteWanted, false, notes),
                noted("ante.cards_per_player",
                        clamped(toml.number("ante.cards_per_player", 1), 1, 10,
                                "ante.cards_per_player", notes), 1, notes),
                noted("ante.exclusions",
                        List.copyOf(toml.strings("ante.exclusions", List.of("basic lands"))),
                        List.of("basic lands"), notes),
                noted("ante.allow_per_table_opt_out",
                        toml.flag("ante.allow_per_table_opt_out", true), true, notes));

        for (String unknown : toml.unknownKeys(knownKeys())) {
            notes.add("'" + unknown + "' is not a setting this version knows about");
        }
        return new GatheringConfig(
                new Modes(importEnabled, collectionEnabled), importing, collecting, tables, ante,
                notes);
    }

    /** Whether this player may turn a decklist into a deck out of nothing. */
    public boolean mayImport(boolean isOperator) {
        return modes.importEnabled() && (importing.allowAllPlayers() || isOperator);
    }

    /**
     * A value, and a note if changing it will not have changed anything.
     *
     * <p>Compared against the default rather than against whether the file mentions it,
     * because the file this mod writes mentions every setting - so "is it in the file" would
     * mean a fresh server reporting a dozen things it has not been asked to do.
     */
    /**
     * The loot sources a file asked for, keeping only ones that are a place in the world.
     *
     * <p>A name nobody knows is dropped and said out loud. Silently keeping it would be a
     * server owner who believes packs come out of somewhere they never come out of, and this
     * is exactly the list a typo is easiest to make in.
     */
    private static List<String> lootSources(List<String> asked, List<String> notes) {
        List<String> kept = new ArrayList<>();
        for (String named : asked) {
            LootSource source = LootSource.named(named).orElse(null);
            if (source == null) {
                notes.add("collection.pack_loot_sources lists '" + named
                        + "', which is not somewhere packs can be found. Packs turn up in "
                        + String.join(", ", DEFAULT_LOOT_SOURCES) + ".");
            } else if (!kept.contains(source.configName())) {
                kept.add(source.configName());
            }
        }
        return List.copyOf(kept);
    }

    /**
     * Which sets packs may be found from, keeping only what means something.
     *
     * <p>Three shapes: {@code "current"} for whatever is out now, {@code "recent"} for the
     * last few releases, and a set code for exactly that set. A seasonal server names the
     * set it is about; an era server names the block it lives in; a server that says nothing
     * gets the current one.
     *
     * <p>Anything else is dropped and said out loud. A set code with a typo in it is a set
     * that never drops anything, and finding that out from an empty chest is no way to find
     * it out.
     */
    private static List<String> lootSets(List<String> asked, List<String> notes) {
        List<String> kept = new ArrayList<>();
        for (String named : asked) {
            String wanted = named == null ? "" : named.trim().toLowerCase(Locale.ROOT);
            if (wanted.equals(LOOT_SETS_CURRENT) || wanted.equals(LOOT_SETS_RECENT)) {
                if (!kept.contains(wanted)) {
                    kept.add(wanted);
                }
                continue;
            }
            String code = dev.gathering.core.card.SetCode.of(wanted).orElse(null);
            if (code == null) {
                notes.add("collection.loot_sets lists '" + named + "', which is not a set code "
                        + "and not \"" + LOOT_SETS_CURRENT + "\" or \"" + LOOT_SETS_RECENT
                        + "\".");
            } else if (!kept.contains(code)) {
                kept.add(code);
            }
        }
        if (kept.isEmpty() && !asked.isEmpty()) {
            notes.add("collection.loot_sets named nothing this understands, so packs come from "
                    + "the current set.");
            return DEFAULT_LOOT_SETS;
        }
        return List.copyOf(kept);
    }

    private static <T> T noted(String path, T value, T fallback, List<String> notes) {
        String missing = NOT_BUILT_YET.get(path);
        if (missing != null && !java.util.Objects.equals(value, fallback)) {
            notes.add("'" + path + "' is set, but " + missing + " is not built yet");
        }
        return value;
    }

    private static int clamped(int value, int least, int most, String path, List<String> notes) {
        if (value < least) {
            notes.add(path + " was " + value + ", which is below " + least + "; using " + least);
            return least;
        }
        if (value > most) {
            notes.add(path + " was " + value + ", which is above " + most + "; using " + most);
            return most;
        }
        return value;
    }

    /**
     * The file a server gets on its first start.
     *
     * <p>Every setting written out at its default with the sentence explaining it, because a
     * config file whose options you have to find in a wiki is a config file nobody changes.
     */
    public static String defaultFileText() {
        return """
                # Gathering server settings.
                #
                # Two master switches decide what this server is. With import on, players turn
                # decklists into decks out of nothing and the mod is a pure play client. With
                # collection on, cards are things you find, open, buy and trade. With both on,
                # gate import behind a rank if you want collecting to still mean something.
                #
                # Delete this file to get it back with the defaults.

                [modes]
                import_enabled = true
                collection_enabled = false

                [import]
                # false restricts importing to server operators.
                allow_all_players = true
                # Informational only. The mod never enforces a format during play; a deck check
                # before a formatted game is offered, and that is the whole of it.
                formats = ["commander"]

                [collection]
                # Some of this section describes where collecting is going rather than where it
                # is: the shop is not built yet, and changing a setting for it says so in the
                # log rather than doing nothing quietly.
                #
                # Where sealed product turns up: any of "fishing", "structures", "archaeology".
                # Needs collection_enabled.
                pack_loot_sources = ["fishing", "structures", "archaeology"]
                # Which sets those packs are from. "current" is whatever is out now, "recent"
                # is the last few releases, and a set code is exactly that set - so a seasonal
                # server names its set and an era server names its block. Every extra set is
                # another few megabytes fetched when the server starts.
                loot_sets = ["current"]
                # How far back "recent" reaches. Twelve is about three years of Magic.
                loot_recent_sets = 12
                # Shops sell sealed product only, never single cards, at flat prices you set.
                sealed_store_enabled = true
                sealed_price_item = "minecraft:diamond"
                # What one booster costs, in that item. Everything else follows from what is
                # inside it: a box of thirty costs thirty, a Commander deck costs what its
                # hundred cards would. No real-world price is used anywhere, ever.
                sealed_price_booster = 2
                # Which set is found and sold. "auto" asks Scryfall for the newest release, so
                # a server left alone stays current; name a set code to stay where you are.
                current_set = "auto"
                stall_rotation_hours = 4
                stall_rotating_slots = 6
                booster_model = "play"

                [table]
                # Not enforced yet; the shapes and counts here are what they will be enforced at.
                # Counted in clusters, not in blocks.
                max_tables_loaded = 16
                max_cluster_tables = 4
                max_cards_per_session = 1600

                [ante]
                # Playing for keeps. Not built yet. Needs collection_enabled, and every player at
                # the table will have to agree before a game with it on can start.
                enabled = false
                cards_per_player = 1
                exclusions = ["basic lands"]
                allow_per_table_opt_out = true
                """;
    }
}
