package dev.gathering.service;

import dev.gathering.core.booster.MtgjsonCollation;
import dev.gathering.core.booster.MtgjsonFeed;
import dev.gathering.core.net.JdkHttpTransport;
import dev.gathering.core.net.RateLimiter;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Booster collation, wired up and kept off every game thread.
 *
 * <p>The same shape as the card pipeline next door and for the same reason: the feed blocks
 * on a network and on a disk by design, this is the only thing that calls it, and every
 * method here hands back a {@link CompletableFuture}. There is no blocking method to misuse.
 *
 * <p>Its own single thread rather than the card pipeline's, because these are two different
 * hosts with two different sets of manners, and a set file being fetched should not hold up
 * somebody importing a decklist.
 *
 * <p>A set read once is kept for the life of the server. Collation for a released set does
 * not change, the file behind it is megabytes, and the alternative is re-reading it every
 * time somebody opens a booster.
 */
public final class CollationService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final String CACHE_DIRECTORY = "collation-cache";

    private static volatile CollationService active;

    private final ExecutorService executor;
    private final MtgjsonFeed feed;
    private final Map<String, MtgjsonCollation.Reading> alreadyRead = new ConcurrentHashMap<>();

    private CollationService(Path cacheRoot, String userAgent) throws IOException {
        this.executor = Executors.newSingleThreadExecutor(namedDaemonThreads("gathering-mtgjson"));
        this.feed = new MtgjsonFeed(
                new JdkHttpTransport(), RateLimiter.defaultLimiter(), userAgent, cacheRoot);
    }

    /** Builds the service and makes it the running server's, in one step. */
    public static CollationService start(Platform platform) throws IOException {
        CollationService service = new CollationService(
                platform.dataDirectory().resolve(CACHE_DIRECTORY),
                CardDataService.userAgentFor(platform));
        active = service;
        return service;
    }

    /** Empty between servers, which is the honest answer rather than a stale feed. */
    public static Optional<CollationService> active() {
        return Optional.ofNullable(active);
    }

    /**
     * Everything openable for one set, fetching it and whatever its packs reach into.
     *
     * <p>Never fails the future for a set that simply has no collation: that comes back as a
     * reading with nothing in it and a note saying why, which is an answer the caller can put
     * in front of somebody.
     */
    public CompletableFuture<MtgjsonCollation.Reading> collationFor(String setCode) {
        String set = setCode == null ? "" : setCode.trim().toLowerCase(java.util.Locale.ROOT);
        MtgjsonCollation.Reading known = alreadyRead.get(set);
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        return CompletableFuture.supplyAsync(() -> {
            MtgjsonCollation.Reading cached = alreadyRead.get(set);
            if (cached != null) {
                return cached;
            }
            try {
                MtgjsonCollation.Reading reading = feed.collationFor(set);
                for (String note : reading.notes()) {
                    LOGGER.info("Collation for {}: {}", set, note);
                }
                alreadyRead.put(set, reading);
                return reading;
            } catch (Exception couldNotRead) {
                throw new java.util.concurrent.CompletionException(couldNotRead);
            }
        }, executor);
    }

    /**
     * The thread this service's blocking work happens on.
     *
     * <p>Exposed so a caller composing more work onto a collation future can say where it
     * runs. Without that, a future this service has already completed - which is every set
     * read once - runs whatever is chained onto it on the thread that asked, and the thread
     * that asks is the server thread.
     */
    public java.util.concurrent.Executor worker() {
        return executor;
    }

    @Override
    public void close() {
        if (active == this) {
            active = null;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedDaemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            // Daemon so a server shutdown is never held open by an in-flight set fetch.
            thread.setDaemon(true);
            return thread;
        };
    }
}
