package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("What a printed mana cost adds up to")
class ManaValueTest {

    @Test
    @DisplayName("ordinary costs")
    void theEasyOnes() {
        assertThat(ManaValue.of("{2}{U}{U}")).isEqualTo(4);
        assertThat(ManaValue.of("{U}")).isEqualTo(1);
        assertThat(ManaValue.of("{0}")).isZero();
        assertThat(ManaValue.of("{15}")).isEqualTo(15);
        assertThat(ManaValue.of("{W}{U}{B}{R}{G}")).isEqualTo(5);
    }

    @Test
    @DisplayName("a land costs nothing, and so does a card nobody has looked up")
    void nothingIsZero() {
        assertThat(ManaValue.of("")).isZero();
        assertThat(ManaValue.of(null)).isZero();
        assertThat(ManaValue.of("   ")).isZero();
    }

    @Test
    @DisplayName("X is nothing until somebody casts it")
    void variablesAreZero() {
        assertThat(ManaValue.of("{X}")).isZero();
        assertThat(ManaValue.of("{X}{R}")).isEqualTo(1);
        assertThat(ManaValue.of("{X}{X}{G}{G}")).isEqualTo(2);
    }

    @Test
    @DisplayName("a hybrid symbol is one symbol")
    void hybridIsOne() {
        assertThat(ManaValue.of("{W/U}")).isEqualTo(1);
        assertThat(ManaValue.of("{2}{W/U}{W/U}")).isEqualTo(4);
    }

    @Test
    @DisplayName("a twobrid symbol counts as the more expensive half")
    void twobridTakesTheNumber() {
        // The point of this one: it can be paid with two mana, so it is worth two.
        assertThat(ManaValue.of("{2/W}")).isEqualTo(2);
        assertThat(ManaValue.of("{2/W}{2/W}{2/W}")).isEqualTo(6);
    }

    @Test
    @DisplayName("phyrexian mana is still one symbol")
    void phyrexianIsOne() {
        assertThat(ManaValue.of("{U/P}")).isEqualTo(1);
        assertThat(ManaValue.of("{1}{U/P}{U/P}")).isEqualTo(3);
    }

    @Test
    @DisplayName("colorless and snow are one each")
    void theOddPipsAreOne() {
        assertThat(ManaValue.of("{C}")).isEqualTo(1);
        assertThat(ManaValue.of("{S}{S}")).isEqualTo(2);
    }

    @Test
    @DisplayName("nonsense reads as nothing rather than throwing")
    void rubbishIsSurvivable() {
        // A sort is not somewhere to fail. Whatever arrives, a number comes back.
        assertThat(ManaValue.of("hello")).isZero();
        assertThat(ManaValue.of("{")).isZero();
        assertThat(ManaValue.of("{}")).isZero();
        assertThat(ManaValue.of("{2}{")).isEqualTo(2);
        assertThat(ManaValue.of("{99999999999999}")).isEqualTo(1);
    }
}
