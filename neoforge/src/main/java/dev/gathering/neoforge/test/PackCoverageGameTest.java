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
 * layer makes and could get wrong quietly: which printings count as the catalogue, and which
 * packs count as the ways out of it. Either one measured against the wrong thing gives a
 * report that is confidently the wrong number.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackCoverageGameTest {

    @GameTest(template = "empty")
    public static void theCatalogueIsWhatAPackCouldEverHaveHeld(GameTestHelper helper) {
        List<CardMetadata> set = List.of(
                card("ordinary", Rarity.COMMON, false, false),
                card("digital", Rarity.COMMON, true, false),
                card("oversized", Rarity.MYTHIC, false, true));

        List<UUID> catalogue = PackCoverage.catalogueOf(set);

        if (catalogue.size() != 1) {
            helper.fail("A set of one real card and two that were never on a print sheet "
                    + "came to a catalogue of " + catalogue.size());
            return;
        }
        if (!catalogue.get(0).equals(idOf("ordinary"))) {
            helper.fail("The wrong card survived into the catalogue");
            return;
        }
        helper.succeed();
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

        List<UUID> catalogue = PackCoverage.catalogueOf(set);

        if (catalogue.size() != 1 || !catalogue.get(0).equals(idOf("real"))) {
            helper.fail("A set of one spell and two basics came to a catalogue of "
                    + catalogue.size());
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

        List<Faucet> faucets = PackCoverage.faucetsFor("tst", reading, set);
        CoverageReport report = CoverageAudit.of(PackCoverage.catalogueOf(set), faucets);

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
                PackCoverage.catalogueOf(set), PackCoverage.faucetsFor("tst", reading, set));

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
    public static void aSetWithNoPublishedPacksIsAuditedAgainstTheOneItWouldOpen(
            GameTestHelper helper) {
        // Nothing published, so the pack this server would actually deal is the plain-odds
        // one - and auditing against a pack nobody opens would answer nothing.
        MtgjsonCollation.Reading nothing =
                new MtgjsonCollation.Reading("tst", Map.of(), List.of(), List.of());
        List<CardMetadata> set = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            set.add(card("common" + index, Rarity.COMMON, false, false));
        }
        set.add(card("rare", Rarity.RARE, false, false));
        set.add(card("digital", Rarity.COMMON, true, false));

        List<Faucet> faucets = PackCoverage.faucetsFor("tst", nothing, set);
        CoverageReport report = CoverageAudit.of(PackCoverage.catalogueOf(set), faucets);

        if (faucets.size() != 1) {
            helper.fail("A set with nothing published gave " + faucets.size() + " ways in");
            return;
        }
        if (!faucets.get(0).name().endsWith(":plain-odds")) {
            helper.fail("The fallback pack was called " + faucets.get(0).name());
            return;
        }
        // Every card on a plain-odds sheet is on a sheet a pack draws from, so a set audited
        // this way is complete by construction - and the digital printing is not in the
        // catalogue to be missing from it.
        if (!report.isComplete()) {
            helper.fail("A plain-odds pack left " + report.uncovered().size()
                    + " of its own set unreachable");
            return;
        }
        if (report.catalogue() != 13) {
            helper.fail("The catalogue counted " + report.catalogue() + ", not thirteen");
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
