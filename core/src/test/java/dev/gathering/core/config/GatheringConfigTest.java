package dev.gathering.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.sealed.LootSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a server runs as, and what it is told when the file says something impossible. */
class GatheringConfigTest {

    @Test
    @DisplayName("the file written on first start reads back as the defaults, exactly")
    void theDefaultFileIsTheDefaults() throws Exception {
        Toml written = Toml.read(GatheringConfig.defaultFileText());

        // The guard that matters: a setting mistyped in the template would otherwise ship as a
        // line that looks authoritative and does nothing.
        assertThat(written.unknownKeys(GatheringConfig.knownKeys())).isEmpty();
        for (String key : GatheringConfig.knownKeys()) {
            assertThat(written.has(key)).as(key + " is missing from the default file").isTrue();
        }
        assertThat(GatheringConfig.read(written)).isEqualTo(GatheringConfig.defaults());
        assertThat(GatheringConfig.defaults().notes()).isEmpty();
    }

    @Test
    @DisplayName("a server that never opens the file plays but does not collect")
    void defaultsArePlayOnly() {
        GatheringConfig config = GatheringConfig.defaults();

        assertThat(config.modes().importEnabled()).isTrue();
        assertThat(config.modes().collectionEnabled()).isFalse();
        assertThat(config.ante().enabled()).isFalse();
        assertThat(config.mayImport(false)).isTrue();
    }

    @Test
    @DisplayName("import off means nobody imports, operator or not")
    void importCanBeTurnedOff() throws Exception {
        GatheringConfig config = read("[modes]\nimport_enabled = false\n");

        assertThat(config.mayImport(false)).isFalse();
        assertThat(config.mayImport(true)).isFalse();
    }

    @Test
    @DisplayName("import can be narrowed to operators without being turned off")
    void importCanBeNarrowedToOperators() throws Exception {
        GatheringConfig config = read("[import]\nallow_all_players = false\n");

        assertThat(config.mayImport(false)).isFalse();
        assertThat(config.mayImport(true)).isTrue();
    }

    @Test
    @DisplayName("ante without collection is corrected and said out loud")
    void anteNeedsCollection() throws Exception {
        GatheringConfig config = read("[ante]\nenabled = true\n");

        assertThat(config.ante().enabled()).isFalse();
        assertThat(config.notes()).anySatisfy(note ->
                assertThat(note).contains("ante.enabled needs modes.collection_enabled"));
    }

    @Test
    @DisplayName("ante with collection is left alone")
    void anteWithCollectionStands() throws Exception {
        GatheringConfig config = read("""
                [modes]
                collection_enabled = true
                [ante]
                enabled = true
                cards_per_player = 2
                """);

        assertThat(config.ante().enabled()).isTrue();
        assertThat(config.ante().cardsPerPlayer()).isEqualTo(2);
        // Nothing plays for keeps yet, and a server owner who turned it on is told
        // rather than left to find out at a table.
        assertThat(config.notes()).containsExactlyInAnyOrder(
                "'ante.enabled' is set, but playing for keeps is not built yet",
                "'ante.cards_per_player' is set, but playing for keeps is not built yet");
    }

    @Test
    @DisplayName("a number out of range is clamped and said out loud")
    void numbersOutOfRangeAreClamped() throws Exception {
        GatheringConfig config = read("""
                [table]
                max_tables_loaded = 0
                max_cluster_tables = 99
                """);

        assertThat(config.tables().maxTablesLoaded()).isEqualTo(1);
        assertThat(config.tables().maxClusterTables())
                .isEqualTo(dev.gathering.core.table.TableCluster.MAX_TABLES);
        assertThat(config.notes()).anySatisfy(note ->
                assertThat(note).contains("max_tables_loaded").contains("using 1"));
        assertThat(config.notes()).anySatisfy(note ->
                assertThat(note).contains("max_cluster_tables").contains("above 4"));
        // Clamped back to four, which is what it already was, so nothing about it has
        // changed and there is nothing to say beyond the clamping.
        assertThat(config.notes()).noneSatisfy(note ->
                assertThat(note).contains("max_cluster_tables").contains("not built yet"));
    }

    @Test
    @DisplayName("a setting nobody recognizes is reported rather than ignored")
    void unknownSettingsAreReported() throws Exception {
        GatheringConfig config = read("[modes]\ncollection_enabeld = true\n");

        assertThat(config.modes().collectionEnabled()).isFalse();
        assertThat(config.notes()).anySatisfy(note ->
                assertThat(note).contains("modes.collection_enabeld").contains("not a setting"));
    }

    @Test
    @DisplayName("names of things are read as names, not as whatever case they were typed in")
    void namesAreNormalised() throws Exception {
        GatheringConfig config = read("""
                [collection]
                current_set = "  BLB "
                booster_model = "Play"
                """);

        assertThat(config.collecting().currentSet()).isEqualTo("blb");
        assertThat(config.collecting().boosterModel()).isEqualTo("play");
    }

    @Test
    @DisplayName("lists are kept in the order they were written")
    void listsKeepTheirOrder() throws Exception {
        GatheringConfig config = read("""
                [collection]
                pack_loot_sources = ["archaeology", "fishing"]
                """);
        assertThat(config.collecting().packLootSources())
                .containsExactly("archaeology", "fishing");
        assertThat(config.importing().formats()).containsExactly("commander");
        assertThat(config.ante().exclusions()).containsExactly("basic lands");
    }

    @Test
    @DisplayName("a loot source nobody has is dropped and said out loud")
    void anUnknownLootSourceIsReported() throws Exception {
        // The one list a typo is easiest to make in, and the one where a typo kept quietly
        // means a server owner believing packs come out of somewhere they never do.
        GatheringConfig config = read("""
                [collection]
                pack_loot_sources = ["fishing", "trading", "structrues"]
                """);
        assertThat(config.collecting().packLootSources()).containsExactly("fishing");
        assertThat(config.notes()).anyMatch(note -> note.contains("trading"));
        assertThat(config.notes()).anyMatch(note -> note.contains("structrues"));
    }

    @Test
    @DisplayName("a seasonal server names its set, and an era server names its block")
    void lootSetsTakeSetCodes() throws Exception {
        GatheringConfig config = read("""
                [collection]
                loot_sets = ["blb", "DSK", "otj"]
                loot_recent_sets = 4
                """);

        assertThat(config.collecting().lootSets()).containsExactly("blb", "dsk", "otj");
        assertThat(config.collecting().lootRecentSets()).isEqualTo(4);
        assertThat(config.notes()).noneMatch(note -> note.contains("loot_sets"));
    }

    @Test
    @DisplayName("\"current\" and \"recent\" mean themselves, and repeats are one")
    void lootSetsTakeTheTwoWords() throws Exception {
        GatheringConfig config = read("""
                [collection]
                loot_sets = ["current", "recent", "current"]
                """);

        assertThat(config.collecting().lootSets()).containsExactly("current", "recent");
    }

    @Test
    @DisplayName("a set code with a typo in it is dropped and said out loud")
    void anUnknownLootSetIsReported() {
        // A typo here is a set that never drops anything, and an empty chest is no way to
        // find that out.
        GatheringConfig config = readOrThrow("""
                [collection]
                loot_sets = ["blb", "../etc", "nonsense-set-code"]
                """);

        assertThat(config.collecting().lootSets()).containsExactly("blb");
        assertThat(config.notes()).anyMatch(note -> note.contains("../etc"));
    }

    @Test
    @DisplayName("a loot_sets naming nothing usable falls back to the current set")
    void lootSetsFallBack() {
        // Note what is not here: "whatever" would be kept, because eight letters is a shape a
        // set code has and this cannot know which eight-letter words Wizards has used. What a
        // config check can catch is a thing that could never be one.
        GatheringConfig config = readOrThrow("""
                [collection]
                loot_sets = ["../etc", "a b c"]
                """);

        assertThat(config.collecting().lootSets()).containsExactly("current");
        assertThat(config.notes()).anyMatch(note -> note.contains("loot_sets"));
    }

    @Test
    @DisplayName("how far back recent reaches is clamped to something sane")
    void recentIsBounded() {
        assertThat(readOrThrow("""
                [collection]
                loot_recent_sets = 900
                """).collecting().lootRecentSets()).isLessThanOrEqualTo(64);
        assertThat(readOrThrow("""
                [collection]
                loot_recent_sets = 0
                """).collecting().lootRecentSets()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("what a booster costs is one number, and it is never nothing")
    void theBoosterPriceIsBounded() {
        assertThat(readOrThrow("""
                [collection]
                sealed_price_booster = 5
                """).collecting().sealedPriceBooster()).isEqualTo(5);
        assertThat(readOrThrow("""
                [collection]
                sealed_price_booster = 0
                """).collecting().sealedPriceBooster()).isGreaterThanOrEqualTo(1);
        assertThat(GatheringConfig.defaults().collecting().sealedPriceBooster()).isPositive();
    }

    @Test
    @DisplayName("every source the default file names is a place packs really come from")
    void theDefaultSourcesAreAllReal() {
        // The default is written into the file a server is handed, so a name here that
        // nothing resolves is a note in every fresh server's log for a setting nobody typed.
        GatheringConfig defaults = GatheringConfig.defaults();
        assertThat(defaults.collecting().packLootSources())
                .isNotEmpty()
                .allMatch(named -> LootSource.named(named).isPresent());
        assertThat(defaults.notes()).isEmpty();
    }

    @Test
    @DisplayName("a file with nothing in it is the defaults")
    void anEmptyFileIsTheDefaults() throws Exception {
        assertThat(read("")).isEqualTo(GatheringConfig.defaults());
        assertThat(read("# nothing but a comment\n")).isEqualTo(GatheringConfig.defaults());
    }

    @Test
    @DisplayName("a setting for something that is not built yet says so when it is changed")
    void settingsForUnbuiltThingsSaySo() throws Exception {
        GatheringConfig config = read("""
                [collection]
                booster_model = "collector"
                [table]
                max_tables_loaded = 32
                """);

        assertThat(config.tables().maxTablesLoaded()).isEqualTo(32);
        assertThat(config.notes()).containsExactly(
                "'table.max_tables_loaded' is set, but a limit on tables kept loaded is not "
                        + "built yet");
        // booster_model is acted on when a pack is opened, so changing it says nothing.
        assertThat(config.collecting().boosterModel()).isEqualTo("collector");
    }

    @Test
    @DisplayName("a server that has changed nothing is told nothing")
    void anUneditedFileSaysNothing() throws Exception {
        assertThat(GatheringConfig.read(Toml.read(GatheringConfig.defaultFileText())).notes())
                .isEmpty();
    }

    private static GatheringConfig read(String text) throws TomlException {
        return GatheringConfig.read(Toml.read(text));
    }

    @Test
    @DisplayName("every list setting can be emptied without becoming its default again")
    void anEmptyListStaysEmpty() throws Exception {
        GatheringConfig config = read("""
                [collection]
                pack_loot_sources = []
                [import]
                formats = []
                """);
        assertThat(config.collecting().packLootSources()).isEmpty();
        assertThat(config.importing().formats()).isEmpty();
        // A list left out of the file is still its default; only an empty one written down
        // means empty.
        assertThat(config.ante().exclusions()).containsExactly("basic lands");
    }

    /** For the cases where the file is fine and only its values are being argued with. */
    private static GatheringConfig readOrThrow(String text) {
        try {
            return read(text);
        } catch (Exception unreadable) {
            throw new AssertionError(unreadable);
        }
    }
}
