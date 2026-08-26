package dev.gathering.server;

import dev.gathering.core.config.ConfigEdit;
import dev.gathering.core.config.GatheringConfig;
import dev.gathering.platform.Platform;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Changing what this server is, without stopping it.
 *
 * <p>Settings used to be read once at start and held, on the argument that a booster which
 * could be opened at one moment and not the next - halfway through somebody opening it - is
 * worse than one that waits for a restart. That argument is still right about the moment a
 * setting changes, and wrong about needing a restart to change one: nobody can test limited
 * play on a server whose collection mode takes a shutdown to turn on.
 *
 * <p>So a change is deliberate, announced, and followed immediately by re-reading everything
 * that depends on it. What is re-read is listed here rather than left to whoever calls it,
 * because a setting that changed and a shop that did not notice is exactly the half-applied
 * state the old rule was protecting against.
 */
public final class Settings {

    /** What a change came to. */
    public record Changed(String problem, List<String> notes, boolean rewarmed) {

        public boolean worked() {
            return problem == null;
        }
    }

    private Settings() {
    }

    /** Every setting this version reads, for listing and for completing a command. */
    public static List<String> names() {
        List<String> known = new ArrayList<>(GatheringConfig.knownKeys());
        java.util.Collections.sort(known);
        return List.copyOf(known);
    }

    /**
     * What a setting is currently set to, as a string anybody can read.
     *
     * <p>Read off the live config rather than off the file, because the live config is what
     * the server is actually running on - and a command that reported the file while the
     * server ran on something else would be the one problem a config file cannot recover
     * from, wearing a different hat.
     */
    public static String valueOf(String path) {
        GatheringConfig now = ServerSettings.get();
        return switch (path) {
            case "modes.import_enabled" -> String.valueOf(now.modes().importEnabled());
            case "modes.collection_enabled" -> String.valueOf(now.modes().collectionEnabled());
            case "import.allow_all_players" -> String.valueOf(now.importing().allowAllPlayers());
            case "import.formats" -> String.join(", ", now.importing().formats());
            case "collection.pack_loot_sources" ->
                    String.join(", ", now.collecting().packLootSources());
            case "collection.loot_sets" -> String.join(", ", now.collecting().lootSets());
            case "collection.loot_recent_sets" ->
                    String.valueOf(now.collecting().lootRecentSets());
            case "collection.sealed_store_enabled" ->
                    String.valueOf(now.collecting().sealedStoreEnabled());
            case "collection.sealed_price_item" -> now.collecting().sealedPriceItem();
            case "collection.sealed_price_block" -> now.collecting().sealedPriceBlock();
            case "collection.sealed_price_block_worth" ->
                    String.valueOf(now.collecting().sealedPriceBlockWorth());
            case "collection.sealed_price_booster" ->
                    String.valueOf(now.collecting().sealedPriceBooster());
            case "collection.sealed_rotation_hours" ->
                    String.valueOf(now.collecting().sealedRotationHours());
            case "collection.village_shop_weight" ->
                    String.valueOf(now.collecting().villageShopWeight());
            case "collection.current_set" -> now.collecting().currentSet();
            case "collection.booster_model" -> now.collecting().boosterModel();
            case "table.max_tables_loaded" -> String.valueOf(now.tables().maxTablesLoaded());
            case "table.max_cluster_tables" -> String.valueOf(now.tables().maxClusterTables());
            case "table.max_cards_per_session" ->
                    String.valueOf(now.tables().maxCardsPerSession());
            case "ante.enabled" -> String.valueOf(now.ante().enabled());
            case "ante.cards_per_player" -> String.valueOf(now.ante().cardsPerPlayer());
            case "ante.exclusions" -> String.join(", ", now.ante().exclusions());
            case "ante.allow_per_table_opt_out" ->
                    String.valueOf(now.ante().allowPerTableOptOut());
            default -> "?";
        };
    }

    /**
     * Sets one, and brings back into step whatever it decided.
     *
     * @param typed what somebody wrote at a command line, in their own shape rather than the
     *              file's - {@code on}, {@code 12}, {@code basic lands, foils}
     */
    public static Changed set(String path, String typed) {
        String value = ConfigEdit.asToml(flagWords(typed)).orElse(null);
        if (value == null) {
            return new Changed("No value given.", List.of(), false);
        }
        if (!names().contains(path)) {
            return new Changed("No setting called " + path + ".", List.of(), false);
        }

        String problem = ServerSettings.set(Platform.get(), path, value);
        if (problem != null) {
            return new Changed(problem, List.of(), false);
        }

        // Everything downstream, every time, rather than a table of which setting touches
        // what. That table would be the thing that goes stale, and re-reading a shelf costs a
        // moment on a command somebody types once.
        boolean rewarmed = rewarm();
        return new Changed(null, ServerSettings.get().notes(), rewarmed);
    }

    /**
     * Reads the sets, the loot pool, the shop and the loaner shelf again.
     *
     * <p>Off the game thread where it needs to be - these are the same calls a server start
     * makes, and they reach the network - so a command returns immediately and the shelf
     * arrives a moment later, exactly as it does at boot.
     */
    public static boolean rewarm() {
        CurrentSet.resolve();
        SealedLoot.warm();
        CardShop.warm();
        LoanerDecks.warm();
        return true;
    }

    /**
     * Turns the words people actually use into the two the file understands.
     *
     * <p>Somebody switching a mode on types "on". Refusing that and printing the word "true"
     * at them is a command being right at the cost of being usable.
     */
    private static String flagWords(String typed) {
        if (typed == null) {
            return null;
        }
        String value = typed.strip();
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "on", "yes", "enabled" -> "true";
            case "off", "no", "disabled" -> "false";
            default -> value;
        };
    }
}
