package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Telling tokens of one name apart in a row somebody can pick from. */
class TokenVariantTest {

    @Test
    @DisplayName("size and color tell two Cats apart")
    void sizeAndColor() {
        assertThat(TokenVariant.describe("1/1", Set.of("W"), "")).isEqualTo("1/1 white");
        assertThat(TokenVariant.describe("2/2", Set.of("G"), "")).isEqualTo("2/2 green");
    }

    @Test
    @DisplayName("an ability on the first line is named, reminder text is not")
    void abilities() {
        assertThat(TokenVariant.describe("1/1", Set.of("W"), "Lifelink (Damage dealt by this creature also causes you to gain that much life.)"))
                .isEqualTo("1/1 white · Lifelink");
        assertThat(TokenVariant.describe("4/4", Set.of("R", "G"), "trample\nWhen this dies, draw a card."))
                .isEqualTo("4/4 red and green · Trample");
    }

    @Test
    @DisplayName("colors read in Magic's order, and none reads as colorless")
    void colors() {
        assertThat(TokenVariant.colorWords(Set.of("G", "W", "U"))).isEqualTo("white, blue and green");
        assertThat(TokenVariant.colorWords(Set.of())).isEqualTo("colorless");
        assertThat(TokenVariant.describe("", Set.of(), "Sacrifice this token: Add one mana of any color."))
                .isEqualTo("colorless · Sacrifice this token: Add one mana of any color.");
    }

    @Test
    @DisplayName("a long first line is cut to fit one row")
    void longText() {
        String said = TokenVariant.firstRule(
                "Flying, first strike, vigilance, trample, lifelink, haste, deathtouch, reach");
        assertThat(said.length()).isLessThanOrEqualTo(TokenVariant.LONGEST_ABILITIES);
        assertThat(said).endsWith("...");
    }
}
