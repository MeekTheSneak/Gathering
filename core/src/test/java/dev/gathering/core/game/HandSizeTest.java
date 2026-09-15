package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HandSizeTest {

    @Test
    void sevenIsNotOverAndNineIsTwoOver() {
        assertThat(HandSize.overBy(0)).isZero();
        assertThat(HandSize.overBy(7)).isZero();
        assertThat(HandSize.overBy(9)).isEqualTo(2);
    }
}
