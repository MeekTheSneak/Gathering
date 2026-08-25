package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which kind of booster comes out of which chest. */
class BoosterOddsTest {

    private static final List<String> WHAT_A_SET_SELLS =
            List.of("play", "collector", "collector-sample");

    @Test
    @DisplayName("a collector booster is the rare one")
    void collectorIsRare() {
        Map<String, Integer> plain =
                BoosterOdds.weightsFor(WHAT_A_SET_SELLS, LootRichness.PLAIN);

        assertThat(plain.get("collector")).isLessThan(plain.get("play"));
        assertThat(plain.get("collector")).isLessThan(plain.get("collector-sample"));
        assertThat(plain.get("collector-sample")).isLessThan(plain.get("play"));
    }

    @Test
    @DisplayName("and much likelier out of somewhere you had to get to")
    void richChestsCarryCollectors() {
        int village = BoosterOdds.weightOf("collector", LootRichness.PLAIN);
        int endCity = BoosterOdds.weightOf("collector", LootRichness.RICH);

        assertThat(endCity).isGreaterThan(village);
        // Everything else is the same wherever it came from: what a better chest changes is
        // which pack, not how many.
        assertThat(BoosterOdds.weightOf("play", LootRichness.RICH))
                .isEqualTo(BoosterOdds.weightOf("play", LootRichness.PLAIN));
    }

    @Test
    @DisplayName("a kind nobody has heard of is an ordinary booster")
    void unknownKindsAreOrdinary() {
        assertThat(BoosterOdds.weightOf("jumpstart", LootRichness.PLAIN))
                .isEqualTo(BoosterOdds.ORDINARY);
        assertThat(BoosterOdds.weightOf("", LootRichness.PLAIN))
                .isEqualTo(BoosterOdds.ORDINARY);
        assertThat(BoosterOdds.weightOf(null, LootRichness.PLAIN))
                .isEqualTo(BoosterOdds.ORDINARY);
    }

    @Test
    @DisplayName("a roll lands on exactly one kind, and every kind is reachable")
    void everyKindIsReachable() {
        Map<String, Integer> weights =
                BoosterOdds.weightsFor(WHAT_A_SET_SELLS, LootRichness.PLAIN);
        int total = BoosterOdds.totalOf(weights);

        java.util.Set<String> landedOn = new java.util.LinkedHashSet<>();
        for (int roll = 0; roll < total; roll++) {
            String kind = BoosterOdds.pick(weights, roll);
            assertThat(kind).as("roll " + roll).isNotNull();
            landedOn.add(kind);
        }
        assertThat(landedOn).containsExactlyInAnyOrderElementsOf(WHAT_A_SET_SELLS);
    }

    @Test
    @DisplayName("how often each kind comes up is what its weight says")
    void theWeightsAreTheOdds() {
        Map<String, Integer> weights =
                BoosterOdds.weightsFor(WHAT_A_SET_SELLS, LootRichness.PLAIN);
        int total = BoosterOdds.totalOf(weights);

        Map<String, Integer> counted = new java.util.LinkedHashMap<>();
        for (int roll = 0; roll < total; roll++) {
            counted.merge(BoosterOdds.pick(weights, roll), 1, Integer::sum);
        }
        assertThat(counted).isEqualTo(weights);
    }

    @Test
    @DisplayName("the same roll against the same offer is the same pack every time")
    void theChoiceDoesNotWander() {
        Map<String, Integer> weights =
                BoosterOdds.weightsFor(WHAT_A_SET_SELLS, LootRichness.RICH);

        for (int roll = 0; roll < BoosterOdds.totalOf(weights); roll++) {
            assertThat(BoosterOdds.pick(weights, roll))
                    .isEqualTo(BoosterOdds.pick(weights, roll));
        }
    }

    @Test
    @DisplayName("nothing on offer is nothing chosen, not a crash")
    void nothingIsNothing() {
        assertThat(BoosterOdds.pick(Map.of(), 0)).isNull();
        assertThat(BoosterOdds.pick(null, 0)).isNull();
        assertThat(BoosterOdds.weightsFor(null, LootRichness.PLAIN)).isEmpty();
        assertThat(BoosterOdds.totalOf(Map.of())).isZero();
    }

    @Test
    @DisplayName("a roll past the end still produces a pack")
    void aRollPastTheEndStillOpens() {
        Map<String, Integer> weights =
                BoosterOdds.weightsFor(List.of("play"), LootRichness.PLAIN);

        assertThat(BoosterOdds.pick(weights, 9999)).isEqualTo("play");
        assertThat(BoosterOdds.pick(weights, -1)).isEqualTo("play");
    }

    @Test
    @DisplayName("what a set sells, named once each")
    void kindsAreListedOnce() {
        SealedProduct play = booster("play");
        SealedProduct alsoPlay = booster("play");
        SealedProduct collector = booster("collector");

        assertThat(BoosterOdds.kindsOf(List.of(play, alsoPlay, collector)))
                .containsExactly("play", "collector");
        assertThat(BoosterOdds.kindsOf(null)).isEmpty();
    }

    private static SealedProduct booster(String kind) {
        return new SealedProduct("id-" + kind, "Test " + kind, "tst", "booster_pack", kind, 15,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", kind)),
                        List.of(), List.of(), List.of(), List.of()));
    }
}
