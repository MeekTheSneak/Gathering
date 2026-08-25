package dev.gathering.core.config;

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
            boolean sealedStoreEnabled,
            String sealedPriceItem,
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

    /** Every setting name this understands, so a typo in the file can be spotted. */
    public static Set<String> knownKeys() {
        return new LinkedHashSet<>(List.of(
                "modes.import_enabled",
                "modes.collection_enabled",
                "import.allow_all_players",
                "import.formats",
                "collection.pack_loot_sources",
                "collection.sealed_store_enabled",
                "collection.sealed_price_item",
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
                List.copyOf(toml.strings("collection.pack_loot_sources",
                        List.of("fishing", "structures", "trading"))),
                toml.flag("collection.sealed_store_enabled", true),
                toml.string("collection.sealed_price_item", "minecraft:diamond"),
                toml.string("collection.current_set", "auto").trim().toLowerCase(Locale.ROOT),
                clamped(toml.number("collection.stall_rotation_hours", 4), 1, 24 * 7,
                        "collection.stall_rotation_hours", notes),
                clamped(toml.number("collection.stall_rotating_slots", 6), 1, 32,
                        "collection.stall_rotating_slots", notes),
                toml.string("collection.booster_model", "play").trim().toLowerCase(Locale.ROOT));

        Tables tables = new Tables(
                clamped(toml.number("table.max_tables_loaded", 16), 1, 256,
                        "table.max_tables_loaded", notes),
                clamped(toml.number("table.max_cluster_tables", TableCluster.MAX_TABLES),
                        1, TableCluster.MAX_TABLES, "table.max_cluster_tables", notes),
                clamped(toml.number("table.max_cards_per_session", 1600), 100, 20_000,
                        "table.max_cards_per_session", notes));

        boolean anteWanted = toml.flag("ante.enabled", false);
        if (anteWanted && !collectionEnabled) {
            // Straight out of the brief, and not a preference: ante is players putting cards
            // up as property, and on a server where cards are not property there is nothing
            // to put up. Corrected rather than refused, so the server still starts.
            notes.add("ante.enabled needs modes.collection_enabled, so ante is off");
            anteWanted = false;
        }
        Ante ante = new Ante(
                anteWanted,
                clamped(toml.number("ante.cards_per_player", 1), 1, 10, "ante.cards_per_player",
                        notes),
                List.copyOf(toml.strings("ante.exclusions", List.of("basic lands"))),
                toml.flag("ante.allow_per_table_opt_out", true));

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
                # Where sealed product turns up. Needs collection_enabled.
                pack_loot_sources = ["fishing", "structures", "trading"]
                # Shops sell sealed product only, never single cards, at flat prices you set.
                sealed_store_enabled = true
                sealed_price_item = "minecraft:diamond"
                # Which set the pinned shelf stocks. "auto" follows the newest release.
                current_set = "auto"
                stall_rotation_hours = 4
                stall_rotating_slots = 6
                booster_model = "play"

                [table]
                # Counted in clusters, not in blocks.
                max_tables_loaded = 16
                max_cluster_tables = 4
                max_cards_per_session = 1600

                [ante]
                # Playing for keeps. Needs collection_enabled, and every player at the table has
                # to agree before a game with it on can start.
                enabled = false
                cards_per_player = 1
                exclusions = ["basic lands"]
                allow_per_table_opt_out = true
                """;
    }
}
