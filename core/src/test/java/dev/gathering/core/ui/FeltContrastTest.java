package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Dark writing on a light felt, light writing on a dark one. */
class FeltContrastTest {

    @Test
    @DisplayName("undyed white wool, and the pale dyes, are written on in a dark color")
    void lightFelts() {
        assertThat(FeltContrast.isLight(0xFFFFFF)).isTrue();
        // Vanilla's own dye colors: yellow, light gray, lime.
        assertThat(FeltContrast.isLight(0xFED83D)).isTrue();
        assertThat(FeltContrast.isLight(0x9D9D97)).isTrue();
        assertThat(FeltContrast.isLight(0x80C71F)).isTrue();
    }

    @Test
    @DisplayName("the dark dyes are written on in a light color")
    void darkFelts() {
        // Black, blue, green, brown, purple.
        assertThat(FeltContrast.isLight(0x1D1D21)).isFalse();
        assertThat(FeltContrast.isLight(0x3C44AA)).isFalse();
        assertThat(FeltContrast.isLight(0x5E7C16)).isFalse();
        assertThat(FeltContrast.isLight(0x835432)).isFalse();
        assertThat(FeltContrast.isLight(0x8932B8)).isFalse();
    }
}
