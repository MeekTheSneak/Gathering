package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CacheTrimTest {

    @Test
    @DisplayName("a cache under its limit keeps everything")
    void underTheLimitNothingGoes() {
        assertThat(CacheTrim.toDelete(List.of(new CacheTrim.Entry("a", 50, 1), new CacheTrim.Entry("b", 40, 2)), 100, 60))
                .isEmpty();
    }

    @Test
    @DisplayName("past the limit, the least recently used go first, down to the lower mark")
    void theOldestGoFirst() {
        var old = new CacheTrim.Entry("old", 40, 1);
        var middle = new CacheTrim.Entry("middle", 40, 5);
        var fresh = new CacheTrim.Entry("fresh", 40, 9);

        assertThat(CacheTrim.toDelete(List.of(fresh, old, middle), 100, 60)).containsExactly(old, middle);
    }
}
