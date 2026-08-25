package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.Rarity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Completeness as a computation rather than a promise.
 *
 * <p>The failure that matters is asymmetric, and the tests are weighted accordingly. A card
 * wrongly called uncovered turns up in the Archive Pack, which is a slightly larger archive;
 * a card wrongly called covered is a card nobody can ever get, and nothing will say so.
 */
class CoverageAuditTest {

    /** Everything a pack can produce is covered by it. */
    @Test
    void whatAPackProducesIsCovered() {
        Map<Rarity, List<UUID>> pool = poolOf(8, 4, 2, 1);
        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", pool, RaritySlots.usual());

        CoverageReport report = CoverageAudit.of(everythingIn(pool), List.of(new BoosterFaucet(config)));

        assertThat(report.isComplete()).isTrue();
        assertThat(report.uncovered()).isEmpty();
        assertThat(report.covered()).isEqualTo(15);
        assertThat(report.fraction()).isEqualTo(1.0d);
    }

    /** And a card in no path at all is named. */
    @Test
    void aCardNoPathReachesIsNamed() {
        Map<Rarity, List<UUID>> pool = poolOf(8, 4, 2, 1);
        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());
        UUID orphan = printing("an-old-promo");
        List<UUID> catalogue = new ArrayList<>(everythingIn(pool));
        catalogue.add(orphan);

        CoverageReport report = CoverageAudit.of(catalogue, List.of(new BoosterFaucet(config)));

        assertThat(report.isComplete()).isFalse();
        assertThat(report.uncovered()).containsExactly(orphan);
        assertThat(report.catalogue()).isEqualTo(16);
        assertThat(report.covered()).isEqualTo(15);
    }

    /**
     * A sheet no arrangement draws from covers nothing.
     *
     * <p>The check the auditor exists for. A config can carry a sheet that no variant names -
     * a feed that half-loaded, a product whose variant was dropped - and counting it would be
     * the auditor reporting coverage that does not exist, which is the one failure nothing
     * downstream can catch.
     */
    @Test
    @DisplayName("a sheet no arrangement draws from covers nothing")
    void anUnusedSheetCoversNothing() {
        UUID onlyOnTheUnusedSheet = printing("never-drawn");
        Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
        sheets.put("common", sheetOf("common", "c1", "c2", "c3"));
        sheets.put("theList", new BoosterSheet(
                "theList", false, false, Map.of(onlyOnTheUnusedSheet, 1)));
        BoosterConfig config = new BoosterConfig("tst", "draft", sheets,
                List.of(new BoosterVariant("plain", 1, Map.of("common", 2))));

        Set<UUID> catalogue = new LinkedHashSet<>(config.sheets().get("common").printings());
        catalogue.add(onlyOnTheUnusedSheet);

        CoverageReport report = CoverageAudit.of(catalogue, List.of(new BoosterFaucet(config)));

        assertThat(report.uncovered())
                .describedAs("a card only on a sheet nothing draws from was called covered")
                .containsExactly(onlyOnTheUnusedSheet);
    }

    /** A config that cannot open at all covers nothing, whatever sheets it carries. */
    @Test
    void aConfigThatCannotOpenCoversNothing() {
        Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
        sheets.put("common", sheetOf("common", "c1", "c2"));
        // Names a sheet it does not have, so nothing can ever be cut from it.
        BoosterConfig broken = new BoosterConfig("tst", "draft", sheets,
                List.of(new BoosterVariant("plain", 1, Map.of("common", 1, "missing", 1))));

        CoverageReport report = CoverageAudit.of(
                sheets.get("common").printings(), List.of(new BoosterFaucet(broken)));

        assertThat(broken.isUsable()).isFalse();
        assertThat(report.uncovered()).hasSize(2);
        assertThat(report.reachingNothing()).containsExactly("tst:draft");
    }

    /** Two paths together cover what neither covers alone. */
    @Test
    void twoPathsTogetherCoverWhatNeitherCoversAlone() {
        Map<Rarity, List<UUID>> ours = poolOf(4, 2, 1, 0);
        BoosterConfig first = BoosterFallback.configFor("one", "draft", ours, RaritySlots.usual());
        Map<Rarity, List<UUID>> second = new EnumMap<>(Rarity.class);
        second.put(Rarity.COMMON, List.of(printing("other-1"), printing("other-2")));
        BoosterConfig other = BoosterFallback.configFor("two", "draft", second, RaritySlots.usual());

        List<UUID> catalogue = new ArrayList<>(everythingIn(ours));
        catalogue.addAll(everythingIn(second));

        CoverageReport report = CoverageAudit.of(
                catalogue, List.of(new BoosterFaucet(first), new BoosterFaucet(other)));

        assertThat(report.isComplete()).isTrue();
        assertThat(report.byFaucet()).containsEntry("one:draft", 7).containsEntry("two:draft", 2);
    }

    /**
     * A path producing cards the server excluded is not thereby covering them.
     *
     * <p>Excluding a card from the catalogue is a deliberate, visible choice - a server that
     * wants hard scarcity. An auditor that quietly counted it as satisfied because some pack
     * could produce it would be arguing with the admin about their own config.
     */
    @Test
    void whatIsOutsideTheCatalogueIsNotCounted() {
        Map<Rarity, List<UUID>> pool = poolOf(6, 3, 1, 0);
        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());
        // The catalogue is only the commons: the rest are excluded on purpose.
        List<UUID> narrowed = pool.get(Rarity.COMMON);

        CoverageReport report = CoverageAudit.of(narrowed, List.of(new BoosterFaucet(config)));

        assertThat(report.catalogue()).isEqualTo(6);
        assertThat(report.isComplete()).isTrue();
        assertThat(report.byFaucet().get("tst:draft"))
                .describedAs("cards outside the catalogue were counted as coverage")
                .isEqualTo(6);
    }

    /** With no paths at all, nothing is covered and the report says how much that is. */
    @Test
    void noPathsCoverNothing() {
        Map<Rarity, List<UUID>> pool = poolOf(5, 0, 0, 0);

        CoverageReport report = CoverageAudit.of(everythingIn(pool), List.of());

        assertThat(report.covered()).isZero();
        assertThat(report.fraction()).isZero();
        assertThat(report.uncovered()).hasSize(5);
    }

    /** An empty catalogue is complete rather than a hole to go looking for. */
    @Test
    void anEmptyCatalogueIsComplete() {
        CoverageReport report = CoverageAudit.of(List.of(), List.of());

        assertThat(report.isComplete()).isTrue();
        assertThat(report.fraction()).isEqualTo(1.0d);
    }

    /** The archive holds exactly what nothing else reaches, and each card once. */
    @Test
    void theArchiveHoldsExactlyTheRemainder() {
        Map<Rarity, List<UUID>> pool = poolOf(4, 2, 1, 0);
        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());
        UUID first = printing("promo-1");
        UUID second = printing("promo-2");
        List<UUID> catalogue = new ArrayList<>(everythingIn(pool));
        catalogue.add(first);
        catalogue.add(second);

        CoverageReport report = CoverageAudit.of(catalogue, List.of(new BoosterFaucet(config)));
        BoosterSheet archive = CoverageAudit.archiveSheet(report);

        assertThat(archive.printings()).containsExactly(first, second);
        assertThat(archive.total())
                .describedAs("the archive weights the long tail instead of holding it evenly")
                .isEqualTo(2);
    }

    /** And when the configuration covers everything, the archive is empty - which is the goal. */
    @Test
    void aCompleteServerHasAnEmptyArchive() {
        Map<Rarity, List<UUID>> pool = poolOf(4, 2, 1, 0);
        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());

        CoverageReport report = CoverageAudit.of(everythingIn(pool), List.of(new BoosterFaucet(config)));

        assertThat(CoverageAudit.archiveSheet(report).isEmpty()).isTrue();
    }

    // --- helpers ---

    private static Map<Rarity, List<UUID>> poolOf(int commons, int uncommons, int rares, int mythics) {
        Map<Rarity, List<UUID>> pool = new EnumMap<>(Rarity.class);
        put(pool, Rarity.COMMON, commons);
        put(pool, Rarity.UNCOMMON, uncommons);
        put(pool, Rarity.RARE, rares);
        put(pool, Rarity.MYTHIC, mythics);
        return pool;
    }

    private static void put(Map<Rarity, List<UUID>> pool, Rarity rarity, int howMany) {
        if (howMany <= 0) {
            return;
        }
        List<UUID> printings = new ArrayList<>(howMany);
        for (int index = 0; index < howMany; index++) {
            printings.add(printing(rarity.scryfallName() + "-" + index));
        }
        pool.put(rarity, printings);
    }

    private static List<UUID> everythingIn(Map<Rarity, List<UUID>> pool) {
        List<UUID> all = new ArrayList<>();
        pool.values().forEach(all::addAll);
        return all;
    }

    private static BoosterSheet sheetOf(String name, String... cards) {
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        for (String card : cards) {
            weights.put(printing(card), 1);
        }
        return new BoosterSheet(name, false, false, weights);
    }

    private static UUID printing(String of) {
        return UUID.nameUUIDFromBytes(of.getBytes(StandardCharsets.UTF_8));
    }
}
