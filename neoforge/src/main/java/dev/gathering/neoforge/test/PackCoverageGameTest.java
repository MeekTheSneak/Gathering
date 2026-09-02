package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.booster.BoosterConfig;
import dev.gathering.core.booster.BoosterSheet;
import dev.gathering.core.booster.BoosterVariant;
import dev.gathering.core.booster.CoverageAudit;
import dev.gathering.core.booster.CoverageReport;
import dev.gathering.core.booster.Faucet;
import dev.gathering.core.booster.MtgjsonCollation;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.server.PackCoverage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the coverage report measures, and against what.
 *
 * <p>The arithmetic is checked where it is pure. What is here is the pair of decisions this
 * layer makes and could get wrong quietly: which printings count as the catalog, and which
 * packs count as the ways out of it. Either one measured against the wrong thing gives a
 * report that is confidently the wrong number.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackCoverageGameTest {

    @GameTest(template = "empty")
    public static void theCatalogIsWhatAPackCouldEverHaveHeld(GameTestHelper helper) {
        List<CardMetadata> set = List.of(
                card("ordinary", Rarity.COMMON, false, false),
                card("digital", Rarity.COMMON, true, false),
                card("oversized", Rarity.MYTHIC, false, true));

        List<UUID> catalog = PackCoverage.catalogOf(set);

        if (catalog.size() != 1) {
            helper.fail("A set of one real card and two that were never on a print sheet "
                    + "came to a catalog of " + catalog.size());
            return;
        }
        if (!catalog.get(0).equals(idOf("ordinary"))) {
            helper.fail("The wrong card survived into the catalog");
            return;
        }
        helper.succeed();
    }

    /**
     * A set nobody has published a collation for still opens.
     *
     * <p>{@link dev.gathering.core.booster.BoosterFallback} was written for exactly this and
     * for a long time nothing called it: a real set with no MTGJSON booster handed every pack
     * straight back. What is checked here is the join - that the catalog this layer hands the
     * fallback is the one the audit measures, and that what comes back can actually be opened.
     */
    @GameTest(template = "empty")
    public static void aSetWithNoPublishedCollationStillOpens(GameTestHelper helper) {
        List<CardMetadata> set = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            set.add(card("common" + index, Rarity.COMMON, false, false));
        }
        for (int index = 0; index < 4; index++) {
            set.add(card("uncommon" + index, Rarity.UNCOMMON, false, false));
        }
        set.add(card("rare", Rarity.RARE, false, false));
        set.add(card("mythic", Rarity.MYTHIC, false, false));
        // Neither of these was ever on a print sheet, so neither may reach a sheet here.
        set.add(card("digital", Rarity.COMMON, true, false));
        set.add(basic("Plains"));

        Map<Rarity, List<UUID>> pool = dev.gathering.server.PackOpening.poolOf(set);
        BoosterConfig made = dev.gathering.core.booster.BoosterFallback.configFor(
                "tst", "draft", pool, dev.gathering.core.booster.RaritySlots.usual());

        if (!made.isUsable()) {
            helper.fail("A set of eighteen real cards could not be cut into a pack: "
                    + made.whatIsMissing());
            return;
        }
        if (pool.getOrDefault(Rarity.COMMON, List.of()).size() != 12) {
            helper.fail("The common sheet came to "
                    + pool.getOrDefault(Rarity.COMMON, List.of()).size()
                    + " rather than twelve - a digital or basic printing reached it");
            return;
        }
        byte[] seed = "a seed for a made-up pack".getBytes(StandardCharsets.UTF_8);
        if (!dev.gathering.core.booster.BoosterOpener.open(made, seed, made.id())
                .cards().isEmpty()) {
            helper.succeed();
            return;
        }
        helper.fail("The pack cut from that set came out empty");
    }

    @GameTest(template = "empty")
    public static void basicLandsAreNotSomethingAPackHasToCover(GameTestHelper helper) {
        // Found by auditing a real set: it came out at ninety-three per cent, and every card
        // it said was unreachable was a basic land. They are not on any print sheet and the
        // rest of the mod already hands them out free, so a report that counts them is a
        // report that says every set in the game is incomplete.
        List<CardMetadata> set = List.of(
                spell("real"),
                basic("Plains"),
                basic("Island"));

        List<UUID> catalog = PackCoverage.catalogOf(set);

        if (catalog.size() != 1 || !catalog.get(0).equals(idOf("real"))) {
            helper.fail("A set of one spell and two basics came to a catalog of "
                    + catalog.size());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSetIsAuditedAgainstThePacksItPublishes(GameTestHelper helper) {
        // Two kinds of pack, and between them they reach both cards.
        Map<String, BoosterConfig> packs = new LinkedHashMap<>();
        packs.put("draft", packOf("draft", "one"));
        packs.put("collector", packOf("collector", "two"));
        MtgjsonCollation.Reading reading =
                new MtgjsonCollation.Reading("tst", packs, List.of(), List.of());
        List<CardMetadata> set = List.of(
                card("one", Rarity.COMMON, false, false),
                card("two", Rarity.RARE, false, false));

        List<Faucet> faucets = PackCoverage.faucetsFor(reading);
        CoverageReport report = CoverageAudit.of(PackCoverage.catalogOf(set), faucets);

        if (faucets.size() != 2) {
            helper.fail("A set with two kinds of pack gave " + faucets.size() + " ways in");
            return;
        }
        if (!report.isComplete()) {
            helper.fail("Two packs between them holding both cards left "
                    + report.uncovered().size() + " unreachable");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCardNoPublishedPackHoldsIsReportedMissing(GameTestHelper helper) {
        Map<String, BoosterConfig> packs = new LinkedHashMap<>();
        packs.put("draft", packOf("draft", "one"));
        MtgjsonCollation.Reading reading =
                new MtgjsonCollation.Reading("tst", packs, List.of(), List.of());
        List<CardMetadata> set = List.of(
                card("one", Rarity.COMMON, false, false),
                card("two", Rarity.RARE, false, false));

        CoverageReport report = CoverageAudit.of(
                PackCoverage.catalogOf(set), PackCoverage.faucetsFor(reading));

        if (report.isComplete()) {
            helper.fail("A card no pack of the set holds was reported as obtainable");
            return;
        }
        if (report.uncovered().size() != 1 || !report.uncovered().contains(idOf("two"))) {
            helper.fail("The missing card was reported as " + report.uncovered());
            return;
        }
        if (report.covered() != 1) {
            helper.fail("One of two covered came out as " + report.covered());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSetNeverSoldInPacksHasNoWayIn(GameTestHelper helper) {
        // Bloomburrow Commander is the case: it was sold as precons and its cards turn up in
        // Bloomburrow's collector boosters, and there has never been a pack of it. Inventing
        // one so the set has something to open would be a product that does not exist, and
        // the report would be a coverage figure against a pack nobody can buy.
        MtgjsonCollation.Reading nothing =
                new MtgjsonCollation.Reading("tst", Map.of(), List.of(), List.of());
        List<CardMetadata> set = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            set.add(card("common" + index, Rarity.COMMON, false, false));
        }
        set.add(card("rare", Rarity.RARE, false, false));

        List<Faucet> faucets = PackCoverage.faucetsFor(nothing);

        if (!faucets.isEmpty()) {
            helper.fail("A set never sold in packs offered " + faucets.size()
                    + " ways into it: " + faucets.get(0).name());
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------- bits

    private static BoosterConfig packOf(String kind, String holding) {
        Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
        sheets.put("main", new BoosterSheet("main", false, false, false,
                Map.of(idOf(holding), 1)));
        return new BoosterConfig("tst", kind, sheets,
                List.of(new BoosterVariant("plain", 1, Map.of("main", 1))));
    }

    private static UUID idOf(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static CardMetadata spell(String name) {
        return card(name, Rarity.COMMON, false, false);
    }

    private static CardMetadata basic(String name) {
        return new CardMetadata(
                idOf(name), idOf(name), name, "", 0.0, "Basic Land - " + name, "",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal",
                "tst", "Test Set", "1", Rarity.COMMON,
                false, true, true, false, false, List.of("paper"),
                Map.of(), Map.of(), "https://scryfall.com/card/tst/1");
    }

    private static CardMetadata card(
            String name, Rarity rarity, boolean digitalOnly, boolean oversized) {
        return new CardMetadata(
                idOf(name), idOf(name), name, "{1}", 1.0, "Artifact", "",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal",
                "tst", "Test Set", "1", rarity,
                false, true, true, digitalOnly, oversized, List.of("paper"),
                Map.of(), Map.of(), "https://scryfall.com/card/tst/1");
    }
}
