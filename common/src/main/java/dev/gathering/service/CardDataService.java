package dev.gathering.service;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.deck.CachingCardSource;
import dev.gathering.core.deck.DeckImporter;
import dev.gathering.core.deck.ResolvedDeck;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.DiskCardMetadataStore;
import dev.gathering.core.scryfall.JdkHttpTransport;
import dev.gathering.core.scryfall.RateLimiter;
import dev.gathering.core.scryfall.ScryfallClient;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The card pipeline, wired up and kept off every game thread.
 *
 * <p>Both the Scryfall client and the disk cache block by design. This service is the only
 * thing that calls them, it does so on its own executor, and every method here hands back a
 * {@link CompletableFuture}. There is no blocking method to misuse - the API shape is the
 * enforcement.
 *
 * <p>The executor is single-threaded on purpose: Scryfall's guidelines ask for one request
 * at a time with a delay between them, and a single worker makes that structural instead of
 * a promise the rate limiter has to keep alone.
 */
public final class CardDataService implements AutoCloseable {

    private static final String CACHE_DIRECTORY = "card-cache";

    /**
     * The pipeline belonging to the running server.
     *
     * <p>A singleton because a server is one, bound and cleared by the loader's own start
     * and stop handlers. Commands and payload handlers reach it through here rather than
     * threading a reference through every call site.
     */
    private static volatile CardDataService active;

    private final ExecutorService executor;
    private final DiskCardMetadataStore store;
    private final ScryfallClient client;
    private final CachingCardSource source;
    private final DeckImporter importer;

    private CardDataService(Path cacheRoot, String userAgent) throws IOException {
        this.executor = Executors.newSingleThreadExecutor(namedDaemonThreads("gathering-scryfall"));
        this.store = new DiskCardMetadataStore(cacheRoot);
        this.client = new ScryfallClient(new JdkHttpTransport(), RateLimiter.defaultLimiter(), userAgent);
        this.source = new CachingCardSource(store, client);
        this.importer = new DeckImporter(
                source, new dev.gathering.core.deck.ArchidektDeckSource(new JdkHttpTransport(), userAgent));
    }

    /** Builds the service from what the loader knows about this installation. */
    public static CardDataService create(Platform platform) throws IOException {
        return new CardDataService(platform.dataDirectory().resolve(CACHE_DIRECTORY), userAgentFor(platform));
    }

    /** Builds the service and makes it the running server's, in one step. */
    public static CardDataService start(Platform platform) throws IOException {
        CardDataService service = create(platform);
        active = service;
        return service;
    }

    /** Empty between servers, which is the honest answer rather than a stale pipeline. */
    public static Optional<CardDataService> active() {
        return Optional.ofNullable(active);
    }

    /**
     * The identifying User-Agent the Scryfall guidelines ask for. Names the mod, its version,
     * and where to complain about it.
     */
    static String userAgentFor(Platform platform) {
        return Gathering.MOD_NAME + "/" + platform.modVersion()
                + " (Minecraft " + platform.loaderName() + "; +https://github.com/MeekTheSneak/Gathering)";
    }

    /** Paste to deck. The Phase 0 deliverable, in one call. */
    public CompletableFuture<ResolvedDeck> importDecklist(String decklistText) {
        return supply(() -> importer.importText(decklistText));
    }

    /**
     * One printing by canonical identity, through the same cache-then-network path as an
     * import, so a card fetched here is a card the next import does not have to fetch.
     */
    public CompletableFuture<Optional<CardMetadata>> card(UUID scryfallId) {
        return supply(() -> {
            CardQuery query = CardQuery.byId(scryfallId);
            return source.resolve(List.of(query)).get(query);
        });
    }

    /**
     * One card by exact name, through the cache first.
     *
     * <p>The single-card counterpart to an import: what the grant command and, later, a
     * collection search resolve against.
     */
    public CompletableFuture<Optional<CardMetadata>> findByName(String cardName) {
        return supply(() -> {
            CardQuery query = CardQuery.byName(cardName);
            return source.resolve(List.of(query)).get(query);
        });
    }

    /** Every printing of a card, cheapest first - what the import screen's chooser offers. */
    public CompletableFuture<List<CardMetadata>> printingsOf(String cardName) {
        return supply(() -> client.printingsOf(cardName));
    }

    /**
     * Warms the name and printing indexes from whatever is already on disk.
     *
     * <p>Worth doing once at server start so a re-import of a known decklist makes no
     * requests at all; costs one pass over the cache directory and nothing afterwards.
     */
    public CompletableFuture<Integer> warmCache() {
        return supply(store::loadIndex);
    }

    public DiskCardMetadataStore store() {
        return store;
    }

    @Override
    public void close() {
        if (active == this) {
            active = null;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private <T> CompletableFuture<T> supply(IoSupplier<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.get();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    private static ThreadFactory namedDaemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            // Daemon so a server shutdown is never held open by an in-flight card fetch.
            thread.setDaemon(true);
            return thread;
        };
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
