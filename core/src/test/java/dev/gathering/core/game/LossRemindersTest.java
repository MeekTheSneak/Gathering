package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The totals the rules count a loss at: 104.3b, 104.3d and 903.10a. */
class LossRemindersTest {

    @Test
    void theThresholdsAreTheRulesOnes() {
        assertThat(LossReminders.lifeIsAtALoss(1)).isFalse();
        assertThat(LossReminders.lifeIsAtALoss(0)).isTrue();
        assertThat(LossReminders.lifeIsAtALoss(-3)).isTrue();
        assertThat(LossReminders.poisonIsAtALoss(9)).isFalse();
        assertThat(LossReminders.poisonIsAtALoss(10)).isTrue();
        assertThat(LossReminders.commanderDamageIsAtALoss(20)).isFalse();
        assertThat(LossReminders.commanderDamageIsAtALoss(21)).isTrue();
        assertThat(LossReminders.counterIsAtALoss("poison", 10)).isTrue();
        assertThat(LossReminders.counterIsAtALoss("energy", 99)).isFalse();
    }
}
