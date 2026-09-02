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
 * <p>Modes are config, not forks. A server runs import mode, collection mode, or both, and
 * that is a choice made in one file rather than by installing a different build - which is
 * why this exists at all, and why every switch has a working default so a server that never
 * opens the file still runs.
 * <p>What it will not do is fail quietly. A value out of range is clamped and said out loud,
 * a combination that cannot work is corrected and said out loud, and a setting nobody
 * recognizes is reported. All three go in {@link #notes}, because a server owner reading
 * their config file and a server behaving differently from it is the one problem no amount of
 * in-game polish can rescue.
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

    /**
     * The master switches.
     *
     * @param replays who may watch a finished game back. Public by default: a replay only
     *                ever shows a game that is over, so there is nothing left in it to
     *                exploit, and a game played at a table anybody could stand at was public
     *                while it happened. A server running a tournament where the same decks
     *                meet again the next evening has the other two.
     */
    public record Modes(boolean importEnabled, boolean collectionEnabled, Replays replays) {
    }

    /**
     * Who a kept game is shown to.
     * <p>Three states rather than a switch because the middle one is the one most servers
     * actually want: a group can settle their own argument about what was on top of the
     * library without a stranger reading their deck for the rematch.
     */
    public enum Replays {

        /** Anybody on the server may watch any finished game. */
        PUBLIC,

        /** Only the people who sat at the table may watch that game. */
        PARTICIPANTS,

        /** Nothing is kept at all. */
        OFF;

        /** What a word in the file means, falling back rather than refusing the whole file. */
        public static Replays parse(String raw, Replays fallback) {
            if (raw == null) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "public", "on", "true", "everybody" -> PUBLIC;
                case "participants", "players", "table" -> PARTICIPANTS;
                case "off", "false", "none" -> OFF;
                default -> fallback;
            };
        }

        /** Whether a finished game is written down at all. */
        public boolean keeps() {
            return this != OFF;
        }

        @Override
        public String toString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
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
            String sealedPriceBlock,
            int sealedPriceBlockWorth,
            int sealedPriceBooster,
            int sealedRotationHours,
            int villageShopWeight,
            String currentSet,
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
     * <p>The file is written from the design rather than from what happens to be finished, so
     * a server owner can see the shape of the thing. That is only honest as long as changing
     * one of these says so: a setting that is read, kept, and never consulted is a server
     * running differently from the file its owner is reading, which is the one failure a
     * config file cannot recover from.
     * <p>Entries come off this list as the feature behind them lands.
     */
    private static final java.util.Map<String, String> NOT_BUILT_YET = java.util.Map.ofEntries(
            java.util.Map.entry("table.max_tables_loaded", "a limit on tables kept loaded"),
            java.util.Map.entry("table.max_cluster_tables", "a cluster cap other than four"),
            java.util.Map.entry("table.max_cards_per_session", "a limit on cards in a session"));

    /**
     * Where sealed product turns up on a server that has not said otherwise.
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

    /** And by "everything Wizards ever put in a booster". */
    public static final String LOOT_SETS_ALL = "all";

    /**
     * Which sets a server's packs come from when it has not said.
     * <p>All of them. This used to be the current set alone, on the reasoning that a server
     * pinned to one release is what most people mean by turning collecting on - but what it
     * actually meant is that everything anybody ever fished out of the sea was from the same
     * three months of Magic, and a collection is a thing you build out of the whole game. A
     * server that wants an era or a season names it; a server that says nothing gets Magic.
     * <p>It costs a longer list at startup and nothing else: the sealed data for a set is
     * fetched when something needs it, not when the list is worked out.
     */
    private static final List<String> DEFAULT_LOOT_SETS = List.of(LOOT_SETS_ALL);

    /** Every setting name this understands, so a typo in the file can be spotted. */
    public static Set<String> knownKeys() {
        return new LinkedHashSet<>(List.of(
                "modes.import_enabled",
                "modes.collection_enabled",
                "modes.replays",
                "import.allow_all_players",
                "import.formats",
                "collection.pack_loot_sources",
                "collection.loot_sets",
                "collection.loot_recent_sets",
                "collection.sealed_store_enabled",
                "collection.sealed_price_item",
                "collection.sealed_price_block",
                "collection.sealed_price_block_worth",
                "collection.sealed_price_booster",
                "collection.sealed_rotation_hours",
                "collection.village_shop_weight",
                "collection.current_set",
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
        // Collecting is what a default server is. A card conjured out of a decklist and a
        // card opened out of a pack cannot both be ordinary at the same table - the first
        // makes the second pointless - so out of the box cards are things you find, and
        // importing is the operator's tool for testing, running an event, or handing
        // somebody a deck. A server that would rather everyone typed their own says so in
        // one line: import.allow_all_players.
        boolean collectionEnabled = toml.flag("modes.collection_enabled", true);
        Replays replays = Replays.parse(toml.string("modes.replays", "public"), Replays.PUBLIC);

        Importing importing = new Importing(
                toml.flag("import.allow_all_players", false),
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
                        toml.string("collection.sealed_price_item", "minecraft:emerald"),
                        "minecraft:emerald", notes),
                noted("collection.sealed_price_block",
                        toml.string("collection.sealed_price_block", "minecraft:emerald_block"),
                        "minecraft:emerald_block", notes),
                noted("collection.sealed_price_block_worth",
                        clamped(toml.number("collection.sealed_price_block_worth", 9), 1, 64,
                                "collection.sealed_price_block_worth", notes), 9, notes),
                noted("collection.sealed_price_booster",
                        clamped(toml.number("collection.sealed_price_booster", 2), 1, 512,
                                "collection.sealed_price_booster", notes), 2, notes),
                noted("collection.sealed_rotation_hours",
                        clamped(toml.number("collection.sealed_rotation_hours", 4), 1, 24 * 7,
                                "collection.sealed_rotation_hours", notes), 4, notes),
                noted("collection.village_shop_weight",
                        clamped(toml.number("collection.village_shop_weight", 8), 0, 64,
                                "collection.village_shop_weight", notes), 8, notes),
                noted("collection.current_set",
                        toml.string("collection.current_set", "auto").trim().toLowerCase(Locale.ROOT),
                        "auto", notes),
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
                        exclusions(toml.strings("ante.exclusions", List.of("basic lands")), notes),
                        List.of("basic lands"), notes),
                noted("ante.allow_per_table_opt_out",
                        toml.flag("ante.allow_per_table_opt_out", true), true, notes));

        for (String unknown : toml.unknownKeys(knownKeys())) {
            notes.add("'" + unknown + "' is not a setting this version knows about");
        }
        return new GatheringConfig(
                new Modes(importEnabled, collectionEnabled, replays),
                importing, collecting, tables, ante,
                notes);
    }

    /** Whether this player may turn a decklist into a deck out of nothing. */
    public boolean mayImport(boolean isOperator) {
        return modes.importEnabled() && (importing.allowAllPlayers() || isOperator);
    }

    /**
     * The loot sources a file asked for, keeping only ones that are a place in the world.
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
     * <p>Four shapes: {@code "all"} for everything ever sold in a booster, {@code "current"}
     * for whatever is out now, {@code "recent"} for the last few releases, and a set code for
     * exactly that set. A seasonal server names the set it is about; an era server names the
     * block it lives in; a server that says nothing gets all of them.
     * <p>Anything else is dropped and said out loud. A set code with a typo in it is a set
     * that never drops anything, and finding that out from an empty chest is no way to find
     * it out.
     */
    private static List<String> lootSets(List<String> asked, List<String> notes) {
        List<String> kept = new ArrayList<>();
        for (String named : asked) {
            String wanted = named == null ? "" : named.trim().toLowerCase(Locale.ROOT);
            if (wanted.equals(LOOT_SETS_CURRENT) || wanted.equals(LOOT_SETS_RECENT)
                    || wanted.equals(LOOT_SETS_ALL)) {
                if (!kept.contains(wanted)) {
                    kept.add(wanted);
                }
                continue;
            }
            String code = dev.gathering.core.card.SetCode.of(wanted).orElse(null);
            if (code == null) {
                notes.add("collection.loot_sets lists '" + named + "', which is not a set code "
                        + "and not \"" + LOOT_SETS_CURRENT + "\", \"" + LOOT_SETS_RECENT
                        + "\" or \"" + LOOT_SETS_ALL + "\".");
            } else if (!kept.contains(code)) {
                kept.add(code);
            }
        }
        if (kept.isEmpty() && !asked.isEmpty()) {
            notes.add("collection.loot_sets named nothing this understands, so packs come from "
                    + "every set.");
            return DEFAULT_LOOT_SETS;
        }
        return List.copyOf(kept);
    }

    /**
     * The ante exclusions, complaining about any word the rule does not know.
     * <p>{@link dev.gathering.core.ante.AnteExclusions} has always named the words it could
     * not use, and nothing has ever listened: the reading was done again at every stake, where
     * only the categories were taken and the notes were dropped on the floor. So a server that
     * wrote "basic land" instead of "basic lands" protected nothing, was told nothing, and
     * found out when somebody's Island went into the pot.
     * <p>Read here as well, at load, so the complaint arrives with all the others - in the
     * log, and in what the settings command reads back - rather than nowhere.
     */
    private static List<String> exclusions(List<String> written, List<String> notes) {
        notes.addAll(dev.gathering.core.ante.AnteExclusions.of(written).notes());
        return List.copyOf(written);
    }

    /**
     * A value, and a note if changing it will not have changed anything.
     * <p>Compared against the default rather than against whether the file mentions it,
     * because the file this mod writes mentions every setting - so "is it in the file" would
     * mean a fresh server reporting a dozen things it has not been asked to do.
     */
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
     * <p>Every setting written out at its default with the sentence explaining it, because a
     * config file whose options you have to find in a wiki is a config file nobody changes.
     */
    public static String defaultFileText() {
        return """
                # Gathering server settings.
                #
                # Two master switches decide what this server is.
                #
                # As it ships: collecting is on and importing is an operator's tool. Cards are
                # things you find, open, buy and trade, and nobody types a deck into existence
                # - which is what makes a pack worth opening. Operators can still import, for
                # testing, for running an event, or for handing somebody a deck.
                #
                # For a pure play server - everybody brings their own decklist, no economy -
                # set allow_all_players to true, and collection_enabled to false if you would
                # rather not have packs about at all.
                #
                # Delete this file to get it back with the defaults.

                [modes]
                import_enabled = true
                collection_enabled = true
                # Who may watch a finished game back, hidden information and all: public,
                # participants (only the people who sat at that table), or off. Safe because
                # the game is over - but a server where the same decks meet again the next
                # evening may want one of the other two.
                replays = "public"

                [import]
                # true lets every player import a decklist. false keeps it to operators, which
                # is the default, because a deck out of nothing beside a deck out of packs
                # makes the packs pointless.
                allow_all_players = false
                # Informational only. The mod never enforces a format during play; a deck check
                # before a formatted game is offered, and that is the whole of it.
                formats = ["commander"]

                [collection]
                # Collecting is built: packs are found and sold, opened, collected, drafted
                # and traded. Where a setting still describes where this is going rather than
                # where it is, changing it says so in the log rather than doing nothing
                # quietly.
                #
                # Where sealed product turns up: any of "fishing", "structures", "archaeology".
                # Needs collection_enabled.
                pack_loot_sources = ["fishing", "structures", "archaeology"]
                # Which sets those packs are from. "all" is everything ever sold in a booster,
                # "current" is whatever is out now, "recent" is the last few releases, and a set
                # code is exactly that set - so a seasonal server names its set and an era server
                # names its block. They combine: ["mh3", "current"] is one set plus whatever is new.
                loot_sets = ["all"]
                # How far back "recent" reaches. Twelve is about three years of Magic.
                loot_recent_sets = 12
                # A card-shop villager sells sealed product only, never single cards. Anything
                # bigger than a booster - a display box, a Commander deck, a case - is sold and
                # never found, and how far up a shopkeeper stocks depends on their level.
                sealed_store_enabled = true
                sealed_price_item = "minecraft:emerald"
                # And what to take for the dear things. A trade holds two stacks of sixty-four,
                # so a case is paid for in blocks and change rather than not at all.
                sealed_price_block = "minecraft:emerald_block"
                sealed_price_block_worth = 9
                # What one booster costs, in the loose item. Everything else follows from what
                # is inside it: a box of thirty costs thirty, a Commander deck costs what its
                # hundred cards would. No real-world price is used anywhere, ever.
                sealed_price_booster = 2
                # How often the shelf turns over, in hours of a running server. Every card shop
                # in the world stocks the same thing at the same time and moves on together, so
                # what is on the counter this evening is not what was there this morning.
                sealed_rotation_hours = 4
                # How often a card shop turns up among a village's buildings, against the
                # thirty-odd houses Minecraft already has - eight is about one shop per
                # village. Zero builds none, for a server placing its own.
                village_shop_weight = 8
                # Which set is found and sold. "auto" asks Scryfall for the newest release, so
                # a server left alone stays current; name a set code to stay where you are.
                current_set = "auto"
                booster_model = "play"

                [table]
                # Not enforced yet; the shapes and counts here are what they will be enforced at.
                # Counted in clusters, not in blocks.
                max_tables_loaded = 16
                max_cluster_tables = 4
                max_cards_per_session = 1600

                [ante]
                # Playing for keeps: a card out of each deck goes face up in the middle, and the
                # winner takes the pot. Needs collection_enabled, and every player at the table is
                # asked before a game with it on starts - one no and the game is played for
                # nothing instead, unless allow_per_table_opt_out is off.
                # exclusions names what may never be staked. The words it knows are
                # "basic lands", "lands", "rares", "mythics" and "foils"; anything else is
                # reported in the log at startup and protects nothing.
                enabled = false
                cards_per_player = 1
                exclusions = ["basic lands"]
                allow_per_table_opt_out = true
                """;
    }
}
