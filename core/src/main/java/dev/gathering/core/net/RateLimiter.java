package dev.gathering.core.net;

/**
 * A minimum delay between outbound requests, per Scryfall's request guidelines.
 *
 * <p>Time and sleeping are injected rather than taken from the platform, so the rate
 * limiter's behavior is a unit test rather than a stopwatch. Nothing here ever runs on a
 * game thread; see the executors the adapter layer hands to the client.
 */
public final class RateLimiter {

    /** Scryfall asks for 50-100ms between requests. Take the polite end. */
    public static final long DEFAULT_MIN_INTERVAL_MILLIS = 100L;

    private final long minIntervalMillis;
    private final Clock clock;
    private final Sleeper sleeper;

    private long lastRequestMillis = Long.MIN_VALUE;

    public RateLimiter(long minIntervalMillis, Clock clock, Sleeper sleeper) {
        if (minIntervalMillis < 0) {
            throw new IllegalArgumentException("minIntervalMillis must not be negative: " + minIntervalMillis);
        }
        this.minIntervalMillis = minIntervalMillis;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public static RateLimiter defaultLimiter() {
        return new RateLimiter(DEFAULT_MIN_INTERVAL_MILLIS, System::currentTimeMillis, Thread::sleep);
    }

    /** Blocks the calling thread until the next request is allowed. Never call from a game thread. */
    public void acquire() throws InterruptedException {
        long now = clock.currentTimeMillis();
        if (lastRequestMillis != Long.MIN_VALUE) {
            long earliest = lastRequestMillis + minIntervalMillis;
            if (now < earliest) {
                sleeper.sleep(earliest - now);
                now = clock.currentTimeMillis();
            }
        }
        lastRequestMillis = now;
    }

    @FunctionalInterface
    public interface Clock {
        long currentTimeMillis();
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
