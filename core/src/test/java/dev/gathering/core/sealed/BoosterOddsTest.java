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
    @DisplayName("a box topper and a promo pack are as rare as a collector booster")
    void theOtherRareKindsAreRareToo() {
        // They were not. The check was an equals against the one string "collector", so a
        // box topper - which is the rarest thing in a real booster box - was priced as an
        // ordinary draft booster and turned up as often as one.
        for (String rare : List.of("collector", "box-topper", "topper", "promo", "vip",
                "premium", "gift-bundle", "special-guest")) {
            assertThat(BoosterOdds.weightOf(rare, LootRichness.PLAIN)).as(rare)
                    .isEqualTo(BoosterOdds.RARE);
            assertThat(BoosterOdds.weightOf(rare, LootRichness.RICH)).as(rare)
                    .isEqualTo(BoosterOdds.RARE_WHERE_IT_IS_EARNED);
        }
        // And the ordinary ones stay ordinary wherever they came from.
        for (String ordinary : List.of("play", "draft", "set", "jumpstart", "theme")) {
            assertThat(BoosterOdds.weightOf(ordinary, LootRichness.PLAIN)).as(ordinary)
                    .isEqualTo(BoosterOdds.ORDINARY);
        }
        // A sample of a collector booster is not a collector booster, and the word is in the
        // name of both.
        assertThat(BoosterOdds.weightOf("collector-sample", LootRichness.PLAIN))
                .isEqualTo(BoosterOdds.SAMPLE);
    }

    /**
     * The band the design brief and the config file both quote, held as a property.
     * <p>Over every combination of kinds a real set could have sold, in either kind of chest.
     * Written as a property rather than as one example because the numbers are what a balance
     * argument moves, and the sentence "under one in two hundred out of an ordinary chest,
     * about one in ten out of an end city" is the thing that must not quietly stop being true
     * when somebody moves one of them.
     */
    @net.jqwik.api.Property(tries = 500)
    @net.jqwik.api.Label("a rare pack stays inside the band the brief quotes")
    void rareKindsStayInsideTheBand(
            @net.jqwik.api.ForAll("whatASetSells") List<String> kinds,
            @net.jqwik.api.ForAll boolean earned) {
        LootRichness richness = earned ? LootRichness.RICH : LootRichness.PLAIN;
        Map<String, Integer> weights = BoosterOdds.weightsFor(kinds, richness);
        int total = BoosterOdds.totalOf(weights);

        int rare = 0;
        int sample = 0;
        for (Map.Entry<String, Integer> offered : weights.entrySet()) {
            if (offered.getKey().contains("sample")) {
                sample += offered.getValue();
            } else if (offered.getKey().contains("collector")) {
                rare += offered.getValue();
            }
        }
        double share = total == 0 ? 0 : (double) rare / total;
        if (earned) {
            // Out of an end city, a bastion or an ancient city: rare, and worth the trip.
            assertThat(share).as("out of a chest worth an expedition").isLessThan(0.15);
        } else {
            // And out of a village barrel, barely ever.
            assertThat(share).as("out of an ordinary chest").isLessThan(0.01);
        }
        // Whatever else a set sold, the pack a player actually opens is an ordinary one.
        // Everything that is not rare and not a sample of the rare thing, because "ordinary"
        // is a band rather than three names: jumpstart and theme are ordinary boosters too.
        assertThat((total - rare - sample) / (double) total).as("an ordinary booster")
                .isGreaterThan(earned ? 0.7 : 0.9);
    }

    /**
     * Every combination of kinds a real set has been sold in.
     * <p>Always with an ordinary booster among them, because the band is a statement about
     * which pack comes out of a chest and there is nothing to choose between when a set only
     * ever sold one thing. A set whose single booster is the collector booster - and MTGJSON
     * publishes none - would drop collector boosters every time, correctly: the weights say
     * which of what is on offer, never whether a set is offered at all.
     */
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<List<String>> whatASetSells() {
        return net.jqwik.api.Arbitraries.subsetOf(
                        "collector", "collector-sample", "jumpstart", "theme")
                .flatMap(extra -> net.jqwik.api.Arbitraries.of("play", "draft", "set")
                        .map(ordinary -> {
                            List<String> sold = new java.util.ArrayList<>();
                            sold.add(ordinary);
                            sold.addAll(extra);
                            return List.copyOf(sold);
                        }));
    }
}
