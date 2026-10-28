package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.Rarity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What light comes out of a pack being torn open. */
class PackGlowTest {

    @Test
    @DisplayName("the best card in the pack decides, wherever it came from")
    void theBestCardDecides() {
        assertThat(PackGlow.forPack(List.of(
                Rarity.COMMON, Rarity.MYTHIC, Rarity.COMMON, Rarity.RARE)))
                .isEqualTo(PackGlow.MYTHIC_LIGHT);
        assertThat(PackGlow.forPack(List.of(Rarity.COMMON, Rarity.RARE, Rarity.UNCOMMON)))
                .isEqualTo(PackGlow.RARE_LIGHT);
    }

    @Test
    @DisplayName("a pack of nothing much says so rather than glowing dimly")
    void nothingMuchDoesNotGlow() {
        assertThat(PackGlow.forPack(List.of(Rarity.COMMON, Rarity.UNCOMMON)))
                .isEqualTo(PackGlow.NO_LIGHT);
        assertThat(PackGlow.glows(List.of(Rarity.COMMON, Rarity.UNCOMMON))).isFalse();
        assertThat(PackGlow.forPack(List.of())).isEqualTo(PackGlow.NO_LIGHT);
        assertThat(PackGlow.forPack(null)).isEqualTo(PackGlow.NO_LIGHT);
    }

    @Test
    @DisplayName("the card the pack was opened for glows even when it is not called rare")
    void specialSlotsGlow() {
        // A Special Guest is the most exciting card in a play booster. A slot that produced
        // it in silence would read as the pack having produced nothing.
        assertThat(PackGlow.forPack(List.of(Rarity.COMMON, Rarity.SPECIAL)))
                .isEqualTo(PackGlow.RARE_LIGHT);
        assertThat(PackGlow.forPack(List.of(Rarity.COMMON, Rarity.BONUS)))
                .isEqualTo(PackGlow.RARE_LIGHT);
    }

    @Test
    @DisplayName("a mythic outranks everything, and rarities are ranked by opening not by listing")
    void mythicOutranksTheSpecials() {
        assertThat(PackGlow.best(List.of(Rarity.SPECIAL, Rarity.MYTHIC))).isEqualTo(Rarity.MYTHIC);
        assertThat(PackGlow.best(List.of(Rarity.RARE, Rarity.SPECIAL))).isEqualTo(Rarity.SPECIAL);
        assertThat(PackGlow.best(List.of(Rarity.UNKNOWN, Rarity.COMMON))).isEqualTo(Rarity.COMMON);
    }

    @Test
    @DisplayName("the two lights are told apart by more than their names")
    void theTwoLightsAreDifferent() {
        assertThat(PackGlow.MYTHIC_LIGHT).isNotEqualTo(PackGlow.RARE_LIGHT);
        assertThat(PackGlow.MYTHIC_LIGHT >>> 24).isEqualTo(0xFF);
        assertThat(PackGlow.RARE_LIGHT >>> 24).isEqualTo(0xFF);
        assertThat(PackGlow.NO_LIGHT >>> 24).isZero();
    }
}
