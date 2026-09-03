package dev.gathering.core.net;

/**
 * A minimum delay between outbound requests, per Scryfall's request guidelines.
 * <p>Time and sleeping are injected rather than taken from the platform, so the rate
 * limiter's behavior is a unit test rather than a stopwatch. Nothing here ever runs on a
 * game thread; see the executors the adapter layer hands to the client.
 * <p>Shared between threads, and locked. One limiter stands in front of every request to one
 * place, and a pool has several threads asking it at once - which is the whole reason it
 * exists, and was also why it did not work: two threads read the same last-request time,
 * both decided their turn had come, and both went. Reported by the far end, as it would be:
 * "GET /cards/search?...&page=3&q=set%3Aeoe returned HTTP 429", after which a whole set was
 * quietly left out of the archive.
 * <p>The wait happens inside the lock on purpose. A limiter that let the next thread compute
 * its turn while this one was still waiting for its own would hand them both the same slot,
 * which is the bug above written a second way.
 */
public final class RateLimiter {

    /** Scryfall asks for 50-100ms between requests. Take the polite end. */
    public static final long DEFAULT_MIN_INTERVAL_MILLIS = 100L;

    private final long minIntervalMillis;
    private final Clock clock;
    private final Sleeper sleeper;

    private long lastRequestMillis = Long.MIN_VALUE;

    /** Set when the far end has asked to be left alone for a while. */
    private long notBeforeMillis = Long.MIN_VALUE;

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
    public synchronized void acquire() throws InterruptedException {
        long now = clock.currentTimeMillis();
        long earliest = notBeforeMillis;
        if (lastRequestMillis != Long.MIN_VALUE) {
            earliest = Math.max(earliest, lastRequestMillis + minIntervalMillis);
        }
        if (earliest != Long.MIN_VALUE && now < earliest) {
            sleeper.sleep(earliest - now);
            now = clock.currentTimeMillis();
        }
        lastRequestMillis = now;
    }

    /**
     * Nothing goes out to this place for a while, because it has just said it is being asked
     * too often.
     * <p>Every thread on this limiter, not only the one that was turned away. A 429 is not
     * one request's problem, it is the rate: backing one request off while nine others carry
     * on at full speed earns nine more 429s and gets the mod no further.
     * <p>The longest wait already asked for wins, so two threads both turned away in the same
     * second do not shorten each other's pause.
     */
    public synchronized void holdOff(long millis) {
        if (millis > 0) {
            notBeforeMillis = Math.max(notBeforeMillis, clock.currentTimeMillis() + millis);
        }
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
