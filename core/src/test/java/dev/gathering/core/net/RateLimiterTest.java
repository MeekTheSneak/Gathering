package dev.gathering.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rate limiting is a Scryfall guideline we agreed to, so it is a test and not a hope. */
class RateLimiterTest {

    @Test
    @DisplayName("the first request goes straight through")
    void firstAcquireDoesNotSleep() throws Exception {
        FakeTime time = new FakeTime(1_000);
        RateLimiter limiter = new RateLimiter(100, time, time);

        limiter.acquire();

        assertThat(time.sleeps).isEmpty();
    }

    @Test
    void aFastSecondRequestWaitsOutTheInterval() throws Exception {
        FakeTime time = new FakeTime(1_000);
        RateLimiter limiter = new RateLimiter(100, time, time);

        limiter.acquire();
        time.advance(30);
        limiter.acquire();

        assertThat(time.sleeps).containsExactly(70L);
    }

    @Test
    void aSlowSecondRequestWaitsForNothing() throws Exception {
        FakeTime time = new FakeTime(1_000);
        RateLimiter limiter = new RateLimiter(100, time, time);

        limiter.acquire();
        time.advance(5_000);
        limiter.acquire();

        assertThat(time.sleeps).isEmpty();
    }

    @Test
    void aBurstIsSpacedOutOneIntervalAtATime() throws Exception {
        FakeTime time = new FakeTime(0);
        RateLimiter limiter = new RateLimiter(100, time, time);

        for (int i = 0; i < 5; i++) {
            limiter.acquire();
        }

        assertThat(time.sleeps).containsExactly(100L, 100L, 100L, 100L);
        assertThat(time.now).isEqualTo(400);
    }

    /** A clock and a sleeper that agree with each other, so elapsed time is deterministic. */
    private static final class FakeTime implements RateLimiter.Clock, RateLimiter.Sleeper {
        private long now;
        private final List<Long> sleeps = new ArrayList<>();

        FakeTime(long start) {
            this.now = start;
        }

        void advance(long millis) {
            now += millis;
        }

        @Override
        public long currentTimeMillis() {
            return now;
        }

        @Override
        public void sleep(long millis) {
            sleeps.add(millis);
            now += millis;
        }
    }
}
