package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.Rarity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Packs for a set nobody published the collation of.
 *
 * <p>Built as sheets and arrangements for the one interpreter, so what is really being
 * checked here is that the fallback is expressible that way at all - a second opener is the
 * thing this design exists to avoid.
 */
class BoosterFallbackTest {

    private static final byte[] SEED = "a fallback".getBytes(StandardCharsets.UTF_8);

    /** The pack has the cards the configured odds say. */
    @Test
    void aPackHoldsWhatTheOddsSay() {
        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", poolOf(60, 40, 20, 6), RaritySlots.usual());

        assertThat(config.isUsable()).isTrue();
        OpenedPack pack = BoosterOpener.open(config, SEED, "1");
        assertThat(pack.size()).isEqualTo(14);
    }

    /** And it is the rarities the odds say, not fourteen of whatever came first. */
    @Test
    void thePackIsTheRaritiesTheOddsSay() {
        Map<Rarity, List<UUID>> pool = poolOf(60, 40, 20, 6);
        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());

        OpenedPack pack = BoosterOpener.open(config, SEED, "1");

        assertThat(countIn(pack, pool, Rarity.COMMON)).isEqualTo(10);
        assertThat(countIn(pack, pool, Rarity.UNCOMMON)).isEqualTo(3);
        assertThat(countIn(pack, pool, Rarity.RARE) + countIn(pack, pool, Rarity.MYTHIC))
                .describedAs("the rare slot holds exactly one card, mythic or not")
                .isEqualTo(1);
    }

    /**
     * The rare is sometimes a mythic, about as often as configured.
     *
     * <p>An arrangement rather than a special case in the opener, which is the point: one
     * pack in eight comes out with a different set of slots, and that is exactly what an
     * arrangement is.
     */
    @Test
    @DisplayName("one rare slot in eight is a mythic")
    void theRareSlotIsSometimesAMythic() {
        Map<Rarity, List<UUID>> pool = poolOf(60, 40, 20, 6);
        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());

        int mythics = 0;
        int packs = 4000;
        for (int packNumber = 0; packNumber < packs; packNumber++) {
            OpenedPack pack = BoosterOpener.open(config, SEED, "pack" + packNumber);
            mythics += countIn(pack, pool, Rarity.MYTHIC);
        }
        assertThat(mythics)
                .describedAs("one in eight came out %s times in %s packs", mythics, packs)
                .isBetween(packs / 16, packs / 4);
    }

    /** A set with no mythics is an ordinary set, not a set that cannot open. */
    @Test
    void aSetWithNoMythicsStillOpens() {
        Map<Rarity, List<UUID>> pool = poolOf(60, 40, 20, 0);

        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());

        assertThat(config.isUsable()).isTrue();
        assertThat(config.variants()).hasSize(1);
        OpenedPack pack = BoosterOpener.open(config, SEED, "1");
        assertThat(pack.size()).isEqualTo(14);
        assertThat(countIn(pack, pool, Rarity.RARE)).isEqualTo(1);
    }

    /** And so is one with no rares at all: the slot goes rather than the pack failing. */
    @Test
    void aSlotWithNothingToFillItIsDropped() {
        Map<Rarity, List<UUID>> pool = poolOf(60, 40, 0, 0);

        BoosterConfig config = BoosterFallback.configFor("tst", "draft", pool, RaritySlots.usual());

        assertThat(config.isUsable()).isTrue();
        assertThat(BoosterOpener.open(config, SEED, "1").size()).isEqualTo(13);
    }

    /** A set with nothing in it at all opens nothing, and says so rather than throwing. */
    @Test
    void aSetWithNothingInItOpensNothing() {
        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", Map.of(), RaritySlots.usual());

        assertThat(config.isUsable()).isFalse();
    }

    /** Odds that never upgrade produce one arrangement, which is what a pre-mythic set wants. */
    @Test
    void oddsThatNeverUpgradeGiveOneArrangement() {
        Map<Rarity, Integer> slots = new EnumMap<>(Rarity.class);
        slots.put(Rarity.COMMON, 11);
        slots.put(Rarity.UNCOMMON, 3);
        slots.put(Rarity.RARE, 1);
        RaritySlots older = new RaritySlots(slots, Rarity.RARE, Rarity.MYTHIC, 0);

        assertThat(older.upgrades()).isFalse();
        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", poolOf(60, 40, 20, 6), older);

        assertThat(config.variants()).hasSize(1);
        assertThat(BoosterOpener.open(config, SEED, "1").size()).isEqualTo(15);
    }

    /** The odds are configurable, which is the whole reason they are data. */
    @Test
    void theOddsAreWhateverTheServerSays() {
        Map<Rarity, Integer> slots = new EnumMap<>(Rarity.class);
        slots.put(Rarity.COMMON, 4);
        slots.put(Rarity.RARE, 2);
        RaritySlots odd = new RaritySlots(slots, Rarity.RARE, Rarity.MYTHIC, 2);

        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", poolOf(60, 40, 20, 6), odd);

        assertThat(odd.cards()).isEqualTo(6);
        assertThat(BoosterOpener.open(config, SEED, "1").size()).isEqualTo(6);
    }

    /** No card is in one pack twice, because a rarity sheet is cut like any other. */
    @Test
    void aPackNeverHoldsTheSameCardTwice() {
        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", poolOf(14, 5, 3, 2), RaritySlots.usual());

        for (int packNumber = 0; packNumber < 200; packNumber++) {
            OpenedPack pack = BoosterOpener.open(config, SEED, "pack" + packNumber);
            Set<CardIdentity> distinct = new LinkedHashSet<>(pack.cards());
            assertThat(distinct)
                    .describedAs("pack %s repeated a card", packNumber)
                    .hasSize(pack.size());
        }
    }

    /** A card listed twice in a catalog is still one card, not one that is twice as common. */
    @Test
    void aCardListedTwiceIsStillOneCard() {
        UUID only = printing("only");
        Map<Rarity, List<UUID>> pool = new EnumMap<>(Rarity.class);
        pool.put(Rarity.COMMON, List.of(only, only, only));

        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", pool, RaritySlots.usual());

        assertThat(config.sheets().get("common").size()).isEqualTo(1);
        assertThat(config.sheets().get("common").total()).isEqualTo(1);
    }

    /** The sheets are named as published data names them, so nothing downstream can tell. */
    @Test
    void theSheetsAreNamedTheSameWayPublishedDataNamesThem() {
        BoosterConfig config = BoosterFallback.configFor(
                "tst", "draft", poolOf(60, 40, 20, 6), RaritySlots.usual());

        assertThat(config.sheets().keySet())
                .containsExactlyInAnyOrder("common", "uncommon", "rare", "mythic");
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

    private static int countIn(OpenedPack pack, Map<Rarity, List<UUID>> pool, Rarity rarity) {
        List<UUID> of = pool.getOrDefault(rarity, List.of());
        int seen = 0;
        for (CardIdentity card : pack.cards()) {
            if (of.contains(card.scryfallId())) {
                seen++;
            }
        }
        return seen;
    }

    private static UUID printing(String of) {
        return UUID.nameUUIDFromBytes(of.getBytes(StandardCharsets.UTF_8));
    }
}
