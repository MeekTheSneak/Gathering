package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogCatchUpTest {

    @Test
    @DisplayName("a line or two is news, however long the table was quiet first")
    void aFewLinesAreNews() {
        assertThat(LogCatchUp.tooMuchToRead(0)).isFalse();
        assertThat(LogCatchUp.tooMuchToRead(1)).isFalse();
        assertThat(LogCatchUp.tooMuchToRead(LogCatchUp.STILL_NEWS)).isFalse();
    }

    @Test
    @DisplayName("a log that ran on while nobody was watching is taken in quietly")
    void awholeHistoryIsNotNews() {
        assertThat(LogCatchUp.tooMuchToRead(LogCatchUp.STILL_NEWS + 1)).isTrue();
        assertThat(LogCatchUp.tooMuchToRead(200)).isTrue();
    }
}
