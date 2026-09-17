package dev.gathering.service;

import dev.gathering.core.net.HttpFetcher;
import dev.gathering.core.net.JdkHttpTransport;
import dev.gathering.core.net.RateLimiter;
import dev.gathering.core.scryfall.ScryfallClient;
import dev.gathering.core.scryfall.bulk.BulkCardIndex;
import dev.gathering.core.scryfall.bulk.BulkDownload;
import dev.gathering.core.scryfall.bulk.BulkIndexBuilder;
import dev.gathering.core.scryfall.bulk.BulkRefresh;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The local copy of Scryfall's bulk card file, kept current and asked before Scryfall is.
 * <p>Owned by the card pipeline and closed with it, so it lives exactly as long as one server and
 * holds nothing past it. Two threads of its own, neither of them the Scryfall worker: one builds
 * and refreshes the copy, which is minutes of work the first time and must not sit in front of
 * every lookup; the other answers from it, which is a file read and must not wait behind a queue
 * of rate-limited requests.
 * <p>Until the copy is ready - the first start, or a server that turned it off - every lookup goes
 * the way it always did. Once it is, a lookup it can answer never reaches the network, and one it
 * cannot answer still does.
 */
public final class BulkCardData implements AutoCloseable {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Gathering");

    private static final String BULK_DIRECTORY = "bulk";

    /** How often a running server asks whether a newer file is out. Scryfall publishes daily. */
    private static final long CHECK_EVERY_HOURS = 24;

    /** How long a replaced copy stays open for the lookups that were already reading it. */
    private static final long OLD_COPY_CLOSES_AFTER_SECONDS = 120;

    /** The most lookups waiting on the copy at once; past that they go the ordinary way. */
    private static final int MOST_QUEUED = 4096;

    private final ScheduledExecutorService keeper;
    private final ThreadPoolExecutor answers;
    private final Path bulkDir;
    private final BulkRefresh refresh;
    private volatile BulkCardIndex ready;

    /** Whether the last refresh failure has been said. Keeper thread only. */
    private boolean saidRefreshFailed;

    private final AtomicBoolean saidLookupFailed = new AtomicBoolean();

    BulkCardData(Path cacheRoot, String userAgent, boolean enabled) {
        this.bulkDir = cacheRoot.resolve(BULK_DIRECTORY);
        this.keeper = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                ServiceThreads.named("gathering-bulk-cards"));
        this.answers = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(MOST_QUEUED), ServiceThreads.named("gathering-card-index"));
        // Its own limiter: this asks the API once a day, and the file itself is on a host with no
        // rate limit. Sharing the card worker's would change nothing but who waits for whom.
        this.refresh = new BulkRefresh(
                new HttpFetcher(new JdkHttpTransport(), RateLimiter.defaultLimiter()),
                BulkDownload.jdk(), bulkDir, ScryfallClient.DEFAULT_BASE_URL, userAgent, System::currentTimeMillis);
        if (!enabled) {
            LOGGER.info("Scryfall's bulk card file is off (cards.bulk_data), so every card is looked up one "
                    + "request at a time.");
            return;
        }
        keeper.execute(this::openThenRefresh);
        keeper.scheduleWithFixedDelay(this::refreshQuietly, CHECK_EVERY_HOURS, CHECK_EVERY_HOURS, TimeUnit.HOURS);
    }

    /** The copy, when one is ready to answer. */
    public Optional<BulkCardIndex> ready() {
        return Optional.ofNullable(ready);
    }

    /** Whether the copy has this printing. A binary search in memory; safe on a game thread. */
    public boolean contains(UUID printing) {
        BulkCardIndex index = ready;
        return index != null && index.contains(printing);
    }

    /** What a lookup does with the copy. Null means it does not have the answer. */
    @FunctionalInterface
    public interface Lookup<T> {
        T answer(BulkCardIndex index) throws IOException;
    }

    /**
     * Answers from the copy if it can, and the ordinary way if it cannot.
     * <p>The ordinary way is asked only after the copy has missed, never alongside it, so a hit
     * costs Scryfall nothing at all.
     */
    public <T> CompletableFuture<T> orElse(Lookup<T> local, Supplier<CompletableFuture<T>> ordinary) {
        BulkCardIndex index = ready;
        if (index == null) {
            return ordinary.get();
        }
        CompletableFuture<T> asked;
        try {
            asked = CompletableFuture.supplyAsync(() -> {
                try {
                    return local.answer(index);
                } catch (IOException unreadable) {
                    if (saidLookupFailed.compareAndSet(false, true)) {
                        LOGGER.warn("A card lookup could not be answered from the bulk card file, so it went to "
                                + "Scryfall instead: {}", unreadable.toString());
                    }
                    return null;
                } catch (RuntimeException refused) {
                    // A question the ordinary way refuses too - a blank name, say - and it says so
                    // there, to the caller, rather than here as a broken file.
                    return null;
                }
            }, answers);
        } catch (RejectedExecutionException busy) {
            return ordinary.get();
        }
        return asked.thenCompose(hit -> hit != null ? CompletableFuture.completedFuture(hit) : ordinary.get());
    }

    private void openThenRefresh() {
        BulkIndexBuilder.sweep(bulkDir);
        try {
            BulkCardIndex.open(bulkDir).ifPresent(opened -> {
                publish(opened);
                LOGGER.info("Card index ready: {} printings from Scryfall's file of {}", opened.size(),
                        opened.updatedAt());
            });
        } catch (IOException cannotTrust) {
            LOGGER.warn("The local card index could not be used and will be built again: {}", cannotTrust.getMessage());
        }
        refreshQuietly();
    }

    private void refreshQuietly() {
        long started = System.nanoTime();
        if (ready == null) {
            LOGGER.info("Checking Scryfall's bulk card file; until the local card index is built, cards are looked "
                    + "up one request at a time.");
        }
        try {
            BulkRefresh.Outcome outcome = refresh.refresh(Optional.ofNullable(ready));
            saidRefreshFailed = false;
            if (outcome != BulkRefresh.Outcome.BUILT) {
                return;
            }
            BulkCardIndex built = BulkCardIndex.open(bulkDir)
                    .orElseThrow(() -> new IOException("The card index was built and then was not there"));
            publish(built);
            LOGGER.info("Card index built in {} s: {} printings from Scryfall's file of {}",
                    (System.nanoTime() - started) / 1_000_000_000L, built.size(), built.updatedAt());
        } catch (java.io.InterruptedIOException stopping) {
            // The server is stopping; the copy that was current still is.
        } catch (IOException | RuntimeException failed) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (!saidRefreshFailed) {
                saidRefreshFailed = true;
                LOGGER.warn(ready != null
                        ? "Could not bring the local card index up to date, so it goes on answering from the copy it "
                                + "has: {}"
                        : "Could not build the local card index, so cards are looked up one request at a time: {}",
                        failed.toString());
            }
        }
    }

    private void publish(BulkCardIndex fresh) {
        BulkCardIndex old = ready;
        ready = fresh;
        if (old != null && old != fresh) {
            try {
                keeper.schedule(() -> closeQuietly(old), OLD_COPY_CLOSES_AFTER_SECONDS, TimeUnit.SECONDS);
            } catch (RejectedExecutionException stopping) {
                closeQuietly(old);
            }
        }
    }

    private static void closeQuietly(BulkCardIndex index) {
        try {
            index.close();
        } catch (IOException ignored) {
            // A file that will not close is let go of by the process ending.
        }
    }

    @Override
    public void close() {
        keeper.shutdownNow();
        answers.shutdownNow();
        try {
            // Long enough for a download or a build to notice it was stopped, so the next world's
            // copy of this service does not find this one still writing into the same directory.
            keeper.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        BulkCardIndex index = ready;
        ready = null;
        if (index != null) {
            closeQuietly(index);
        }
    }
}
