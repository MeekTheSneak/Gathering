package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One interpreter, every set, no code that knows which set it is.
 *
 * <p>Checked against made-up sheets rather than real ones on purpose. Nothing about any real
 * set is written down in this module - the point of the design is that a set published in ten
 * years works with no new code - so a test that named a real set would be testing the thing
 * the design exists to avoid.
 */
class BoosterOpenerTest {

    private static final byte[] SEED = "a booster".getBytes(StandardCharsets.UTF_8);

    /** A pack has the cards its arrangement says, off the sheets it names. */
    @Test
    void aPackIsTheSizeItsArrangementSays() {
        BoosterConfig config = config(
                variant("plain", 1, Map.of("common", 10, "rare", 1)),
                sheet("common", false, false, 30),
                sheet("rare", false, false, 8));

        OpenedPack pack = BoosterOpener.open(config, SEED, "1");

        assertThat(pack.size()).isEqualTo(11);
        assertThat(pack.from()).isEqualTo("tst:draft");
    }

    /**
     * A sheet that refuses duplicates never gives the same card twice in one pack.
     *
     * <p>Which is what cutting a real sheet does: the same card cannot be in one pack twice
     * because there is only one of it in that column. Run over many packs, because a
     * duplicate is a thing that happens sometimes rather than always.
     */
    @Test
    void aSheetThatRefusesDuplicatesGivesNoneInOnePack() {
        // Ten cards off a sheet of twelve: tight enough that a duplicate is near certain if
        // nothing is stopping it, and loose enough that the pack can still be filled.
        BoosterConfig config = config(
                variant("plain", 1, Map.of("common", 10)),
                sheet("common", false, false, 12));

        for (int packNumber = 0; packNumber < 200; packNumber++) {
            OpenedPack pack = BoosterOpener.open(config, SEED, "pack" + packNumber);
            Set<CardIdentity> distinct = new LinkedHashSet<>(pack.cards());
            assertThat(distinct)
                    .describedAs("pack %s gave the same card twice off a sheet that cannot",
                            packNumber)
                    .hasSize(pack.size());
        }
    }

    /** And a sheet that allows them can, which is the other half of the setting meaning anything. */
    @Test
    void aSheetThatAllowsDuplicatesEventuallyGivesOne() {
        BoosterConfig config = config(
                variant("plain", 1, Map.of("land", 6)),
                sheet("land", false, true, 5));

        boolean sawOne = false;
        for (int packNumber = 0; packNumber < 200 && !sawOne; packNumber++) {
            OpenedPack pack = BoosterOpener.open(config, SEED, "pack" + packNumber);
            sawOne = new LinkedHashSet<>(pack.cards()).size() < pack.size();
        }
        assertThat(sawOne)
                .describedAs("six cards off a sheet of five never repeated, which is impossible")
                .isTrue();
    }

    /** Six off a sheet of five must repeat, duplicates or not - and a short pack is not a crash. */
    @Test
    void aSheetTooSmallForItsSlotGivesAShortPackRatherThanThrowing() {
        BoosterConfig config = config(
                variant("plain", 1, Map.of("tiny", 6)),
                sheet("tiny", false, false, 3));

        OpenedPack pack = BoosterOpener.open(config, SEED, "1");

        assertThat(pack.size()).isEqualTo(3);
    }

    /** Cards off a foil sheet are foil, which is a property of the sheet and not of the card. */
    @Test
    void cardsOffAFoilSheetAreFoil() {
        BoosterConfig config = config(
                variant("plain", 1, new LinkedHashMap<>(Map.of("shiny", 1))),
                sheet("shiny", true, false, 4));

        OpenedPack pack = BoosterOpener.open(config, SEED, "1");

        assertThat(pack.cards()).allSatisfy(card -> assertThat(card.foil()).isTrue());
    }

    /**
     * A card that appears more often on a sheet turns up more often in packs.
     *
     * <p>The whole of what "weighted" has to mean. A weight that was read and then ignored
     * would pass every other test here: the packs would be the right size, off the right
     * sheets, with no duplicates - and the rare slot would be uniform, which is an economy
     * where nothing is rare.
     */
    @Test
    @DisplayName("a heavier card turns up more often")
    void weightsActuallyDecideHowOftenACardTurnsUp() {
        UUID common = printing("common");
        UUID scarce = printing("scarce");
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        weights.put(common, 9);
        weights.put(scarce, 1);
        BoosterConfig config = config(
                variant("plain", 1, Map.of("mixed", 1)),
                new BoosterSheet("mixed", false, true, false, weights));

        int scarceSeen = 0;
        int packs = 4000;
        for (int packNumber = 0; packNumber < packs; packNumber++) {
            if (BoosterOpener.open(config, SEED, "pack" + packNumber).last()
                    .equals(CardIdentity.ofPrinting(scarce))) {
                scarceSeen++;
            }
        }
        // Nine to one, so about a tenth. Generous bounds: this is checking that the weight is
        // used at all, not that the stream is a good one - that is the shuffle's own test.
        assertThat(scarceSeen)
                .describedAs("one in ten came out %s times in %s packs", scarceSeen, packs)
                .isBetween(packs / 20, packs / 5);
    }

    /**
     * And a heavier arrangement turns up more often than a lighter one.
     *
     * <p>The same property one level up, and the one that makes an upgraded rare slot mean
     * anything: a variant weight that was read and ignored would make every arrangement
     * equally likely, so the one-in-eight pack would be one in two.
     */
    @Test
    void variantWeightsDecideHowOftenAnArrangementTurnsUp() {
        BoosterConfig config = config(
                List.of(variant("plain", 7, Map.of("common", 1)),
                        variant("upgraded", 1, Map.of("rare", 1))),
                sheet("common", false, false, 4),
                sheet("rare", false, false, 4));
        List<UUID> rares = config.sheets().get("rare").printings();

        int upgraded = 0;
        int packs = 4000;
        for (int packNumber = 0; packNumber < packs; packNumber++) {
            CardIdentity card = BoosterOpener.open(config, SEED, "pack" + packNumber).last();
            if (rares.contains(card.scryfallId())) {
                upgraded++;
            }
        }
        assertThat(upgraded)
                .describedAs("one in eight came out %s times in %s packs", upgraded, packs)
                .isBetween(packs / 16, packs / 4);
    }

    /** The same seed and label open the same pack, which is what makes an economy auditable. */
    @Test
    void theSameSeedOpensTheSamePack() {
        BoosterConfig config = config(
                variant("plain", 1, Map.of("common", 10, "rare", 1)),
                sheet("common", false, false, 30),
                sheet("rare", false, false, 8));

        assertThat(BoosterOpener.open(config, SEED, "7"))
                .isEqualTo(BoosterOpener.open(config, SEED, "7"));
    }

    /** And two packs out of one seed are two packs rather than the same one twice. */
    @Test
    void twoPacksFromOneSeedAreDifferentPacks() {
        BoosterConfig config = config(
                variant("plain", 1, Map.of("common", 10, "rare", 1)),
                sheet("common", false, false, 60),
                sheet("rare", false, false, 20));

        assertThat(BoosterOpener.open(config, SEED, "1"))
                .isNotEqualTo(BoosterOpener.open(config, SEED, "2"));
    }

    /**
     * A config asking for a sheet it does not have says so rather than opening badly.
     *
     * <p>Collation data arriving incomplete is a thing that happens to real feeds, and the
     * right answer is for the server to fall back to its configured odds for that set - which
     * it can only decide to do if it is told which sheet is missing.
     */
    @Test
    void aMissingSheetIsReportedRatherThanOpened() {
        BoosterConfig broken = config(
                variant("plain", 1, Map.of("common", 10, "theList", 1)),
                sheet("common", false, false, 30));

        assertThat(broken.isUsable()).isFalse();
        assertThat(broken.whatIsMissing()).containsExactly("theList");
        assertThatThrownBy(() -> BoosterOpener.open(broken, SEED, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("theList");
    }

    /** As does one with no arrangements at all. */
    @Test
    void aConfigWithNoArrangementsOpensNothing() {
        BoosterConfig empty = new BoosterConfig("tst", "draft", Map.of(), List.of());

        assertThat(empty.isUsable()).isFalse();
        assertThatThrownBy(() -> BoosterOpener.open(empty, SEED, "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** An arrangement nobody can ever get is dropped rather than skewing the roll. */
    @Test
    void anArrangementWithNoWeightIsDropped() {
        BoosterConfig config = config(
                List.of(variant("never", 0, Map.of("common", 1)),
                        variant("always", 3, Map.of("common", 1))),
                sheet("common", false, false, 4));

        assertThat(config.variants()).hasSize(1);
        assertThat(config.totalWeight()).isEqualTo(3);
    }

    /** And a card printed zero times on a sheet is not on the sheet. */
    @Test
    void aCardWithNoWeightIsNotOnTheSheet() {
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        weights.put(printing("there"), 2);
        weights.put(printing("not-there"), 0);

        BoosterSheet sheet = new BoosterSheet("mixed", false, false, false, weights);

        assertThat(sheet.size()).isEqualTo(1);
        assertThat(sheet.total()).isEqualTo(2);
    }

    /**
     * A sheet is walked in the order its data was written.
     *
     * <p>Not fussiness about maps. The opener draws by walking weights until it passes the
     * roll, so the order decides which card a given roll lands on - and an order that is a
     * per-launch hash order means the same seed opens a different pack every time the game
     * starts. Which is not a test failure anybody would ever see: it passes all day inside
     * one run, and only a player asking why their pack was different yesterday would notice.
     *
     * <p>Checked here rather than trusted, because the collection this is built from decides
     * it silently and the wrong one compiles perfectly.
     */
    @Test
    @DisplayName("a sheet is walked in the order it was written")
    void aSheetKeepsTheOrderItsDataWasWrittenIn() {
        List<UUID> written = new ArrayList<>();
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        // Enough keys that a hash order is near-certain to differ from the written one.
        for (int index = 0; index < 24; index++) {
            UUID printing = printing("in-order-" + index);
            written.add(printing);
            weights.put(printing, 1);
        }

        BoosterSheet sheet = new BoosterSheet("ordered", false, false, false, weights);

        assertThat(sheet.printings())
                .describedAs("the sheet reordered its own cards, so a roll lands elsewhere")
                .containsExactlyElementsOf(written);
        // And walking it by weight gives them back in that order too, which is what a draw does.
        List<UUID> walked = new ArrayList<>();
        for (long at = 0; at < sheet.total(); at++) {
            walked.add(sheet.at(at));
        }
        assertThat(walked).containsExactlyElementsOf(written);
    }

    /** And an arrangement keeps its slot order, which is the order cards enter the pack. */
    @Test
    void anArrangementKeepsItsSlotOrder() {
        Map<String, Integer> slots = new LinkedHashMap<>();
        for (int index = 0; index < 12; index++) {
            slots.put("sheet-" + index, 1);
        }

        BoosterVariant variant = new BoosterVariant("plain", 1, slots);

        assertThat(variant.slots().keySet())
                .containsExactlyElementsOf(new ArrayList<>(slots.keySet()));
    }

    /**
     * A sheet heavier than an int can count is drawn from across its whole length.
     *
     * <p>This is the case the opener has been wrong about twice. First there was a
     * hand-rolled high-and-low draw whose high half was always nought, so the back of such a
     * sheet could never come up. Then that was deleted and the case refused outright, on the
     * reasoning that no real print sheet is that heavy - and real published collation
     * promptly produced one at 210,395,225,040, because foil sheets state their odds as exact
     * integer ratios.
     *
     * <p>So it is neither an impossible case nor one to improvise arithmetic for. What this
     * asserts is the thing both wrong versions got wrong: that a card sitting past the int
     * mark on the sheet can actually be opened.
     */
    @Test
    void aSheetHeavierThanAnIntIsDrawnFromAllTheWayAcross() {
        Map<UUID, Integer> heavy = new LinkedHashMap<>();
        UUID front = printing("front");
        UUID back = printing("back");
        // Two halves either side of the int mark, so a draw that cannot see past it can only
        // ever come up with the front card.
        heavy.put(front, Integer.MAX_VALUE);
        heavy.put(back, Integer.MAX_VALUE);
        BoosterSheet sheet = new BoosterSheet("heavy", false, true, false, heavy);
        assertThat(sheet.total()).isEqualTo(2L * Integer.MAX_VALUE);

        BoosterConfig config = config(new BoosterVariant("plain", 1, Map.of("heavy", 1)), sheet);

        Set<UUID> seen = new LinkedHashSet<>();
        for (int pack = 0; pack < 40; pack++) {
            seen.add(BoosterOpener.open(config, SEED, "heavy-" + pack).cards().get(0).scryfallId());
        }
        assertThat(seen).containsExactlyInAnyOrder(front, back);
    }

    /** And the same sheet opens the same way every time, which is what a seed is for. */
    @Test
    void aHeavySheetIsStillReproducible() {
        Map<UUID, Integer> heavy = new LinkedHashMap<>();
        heavy.put(printing("front"), Integer.MAX_VALUE);
        heavy.put(printing("back"), Integer.MAX_VALUE);
        BoosterConfig config = config(
                new BoosterVariant("plain", 1, Map.of("heavy", 4)),
                new BoosterSheet("heavy", false, true, false, heavy));

        assertThat(BoosterOpener.open(config, SEED, "same").cards())
                .isEqualTo(BoosterOpener.open(config, SEED, "same").cards());
    }

    // --- helpers ---

    private static BoosterConfig config(BoosterVariant variant, BoosterSheet... sheets) {
        return config(List.of(variant), sheets);
    }

    private static BoosterConfig config(List<BoosterVariant> variants, BoosterSheet... sheets) {
        Map<String, BoosterSheet> byName = new LinkedHashMap<>();
        for (BoosterSheet sheet : sheets) {
            byName.put(sheet.name(), sheet);
        }
        return new BoosterConfig("TST", "Draft", byName, variants);
    }

    private static BoosterVariant variant(String name, int weight, Map<String, Integer> slots) {
        return new BoosterVariant(name, weight, new LinkedHashMap<>(slots));
    }

    /** A sheet of {@code cards} distinct printings, each printed once. */
    private static BoosterSheet sheet(String name, boolean foil, boolean duplicates, int cards) {
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        for (int index = 0; index < cards; index++) {
            weights.put(printing(name + "-" + index), 1);
        }
        return new BoosterSheet(name, foil, duplicates, false, weights);
    }

    private static UUID printing(String of) {
        return UUID.nameUUIDFromBytes(of.getBytes(StandardCharsets.UTF_8));
    }
}
