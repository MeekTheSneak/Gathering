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
    @Test
    @DisplayName("a place that has asked to be left alone is left alone")
    void aHoldOffIsWaitedOut() throws Exception {
        FakeTime time = new FakeTime(1_000);
        RateLimiter limiter = new RateLimiter(100, time, time);

        limiter.acquire();
        limiter.holdOff(5_000);
        time.advance(200);
        limiter.acquire();

        assertThat(time.sleeps).containsExactly(4_800L);
    }

    @Test
    @DisplayName("the longest hold-off asked for wins")
    void twoHoldOffsDoNotShortenEachOther() throws Exception {
        FakeTime time = new FakeTime(1_000);
        RateLimiter limiter = new RateLimiter(100, time, time);

        limiter.holdOff(5_000);
        limiter.holdOff(1_000);
        limiter.acquire();

        assertThat(time.sleeps).containsExactly(5_000L);
    }

    @Test
    @DisplayName("a hold-off of nothing holds nothing off")
    void aHoldOffOfNothingIsNotAHoldOff() throws Exception {
        FakeTime time = new FakeTime(1_000);
        RateLimiter limiter = new RateLimiter(100, time, time);

        limiter.holdOff(0);
        limiter.holdOff(-500);
        limiter.acquire();

        assertThat(time.sleeps).isEmpty();
    }

    /**
     * The bug this was reported for.
     * <p>A pool has several threads asking one limiter at once. Unlocked, two of them read
     * the same last-request time, both decide their turn has come, and both go without
     * waiting - which is a burst at the far end and an HTTP 429 back, after which a whole set
     * was quietly left out of the archive.
     * <p>On a real clock, because that is the thing being got right: a fake one advanced by
     * the test is not shared state and cannot be raced. Forty turns twenty milliseconds apart
     * cannot be handed out in less than thirty-nine of those gaps however many threads ask,
     * and the assertion is a floor - a slow machine only ever takes longer. Unlocked it comes
     * in at a fraction of it, because most of the threads never wait at all.
     */
    @Test
    @DisplayName("threads asking at once still go one at a time")
    void manyThreadsStillGoOneAtATime() throws Exception {
        int interval = 20;
        int threads = 8;
        int each = 5;
        RateLimiter limiter =
                new RateLimiter(interval, System::currentTimeMillis, Thread::sleep);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> running = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            running.add(pool.submit(() -> {
                ready.await();
                for (int once = 0; once < each; once++) {
                    limiter.acquire();
                }
                return null;
            }));
        }
        long began = System.currentTimeMillis();
        ready.countDown();
        for (java.util.concurrent.Future<?> one : running) {
            one.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(System.currentTimeMillis() - began)
                .describedAs("%s turns %sms apart went out too fast: threads shared a slot",
                        threads * each, interval)
                .isGreaterThanOrEqualTo((long) (threads * each - 1) * interval);
    }

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
