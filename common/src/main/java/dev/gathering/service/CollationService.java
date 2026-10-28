package dev.gathering.service;

import dev.gathering.core.booster.MtgjsonCollation;
import dev.gathering.core.booster.MtgjsonFeed;
import dev.gathering.core.sealed.MtgjsonProducts;
import dev.gathering.core.sealed.MtgjsonDecks;
import dev.gathering.core.sealed.SealedCatalog;
import com.google.gson.JsonObject;
import java.util.List;
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
 * <p>The same shape as the card pipeline next door and for the same reason: the feed blocks
 * on a network and on a disk by design, this is the only thing that calls it, and every
 * method here hands back a {@link CompletableFuture}. There is no blocking method to misuse.
 * <p>Its own single thread rather than the card pipeline's, because these are two different
 * hosts with two different sets of manners, and a set file being fetched should not hold up
 * somebody importing a decklist.
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

    /** The same, for what the set sold rather than what its packs hold. */
    private final Map<String, Catalog> catalogsRead = new ConcurrentHashMap<>();

    /**
     * What every set that ever sold sealed product sold, read once.
     * <p>One field rather than a map, because it is one file. Held as the future rather than
     * the answer so that a dozen callers at server start share the one read instead of racing
     * to start a dozen of them.
     */
    private volatile CompletableFuture<Map<String, MtgjsonProducts.Reading>> everySet;

    /**
     * One set's file, read for everything a shop needs out of it.
     * <p>Products and decks together because they are two halves of one answer and one file:
     * a precon is published in {@code sealedProduct} and the hundred cards it names are
     * published in {@code decks}, and reading the file twice to get them would be a second
     * parse of thirty megabytes for a join.
     */
    public record Catalog(MtgjsonProducts.Reading products, MtgjsonDecks.Reading decks) {

        /** Both lookups, as the pure rules want them. */
        public SealedCatalog lookup() {
            return SealedCatalog.of(products, decks);
        }
    }

    private CollationService(Path cacheRoot, String userAgent) throws IOException {
        this.executor = Executors.newSingleThreadExecutor(ServiceThreads.named("gathering-mtgjson"));
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
     * What every set sold, out of MTGJSON's one list of every set.
     * <p>What a server drawing its packs from more than a handful of sets is drawing from.
     * Ten megabytes once, against three or four for each of several hundred set files - which
     * is the difference between a server that offers every set ever printed and a server that
     * cannot afford to.
     * <p>Products only. Cards published inside a product are counted rather than resolved
     * here, so this says truthfully what is on a shelf and in the world, and says nothing
     * about what comes out of a product: that is {@link #catalogFor}, read when somebody
     * actually opens one.
     * <p>Never fails the future: a list that could not be fetched comes back empty and says so
     * in the log, the same way a set with no file does.
     */
    public CompletableFuture<Map<String, MtgjsonProducts.Reading>> everySetsProducts() {
        CompletableFuture<Map<String, MtgjsonProducts.Reading>> known = everySet;
        if (known != null) {
            return known;
        }
        synchronized (this) {
            if (everySet == null) {
                everySet = CompletableFuture.supplyAsync(() -> {
                    try {
                        JsonObject list = feed.setList().orElse(null);
                        if (list == null) {
                            LOGGER.warn("MTGJSON has no list of sets, so this server knows of "
                                    + "no sealed product beyond the sets it reads in full");
                            return Map.<String, MtgjsonProducts.Reading>of();
                        }
                        return MtgjsonProducts.readSetList(list);
                    } catch (IOException | RuntimeException
                            | dev.gathering.core.booster.BoosterCodecException couldNotRead) {
                        LOGGER.warn("Could not read the list of every set, so this server knows "
                                + "of no sealed product beyond the sets it reads in full: {}",
                                couldNotRead.getMessage());
                        return Map.<String, MtgjsonProducts.Reading>of();
                    }
                }, executor);
            }
            return everySet;
        }
    }

    /**
     * What one set really sold, as products.
     * <p>Out of the same file the collation comes from, so a set already read costs nothing.
     * Never fails the future for a set that simply sold nothing sealed: that comes back as a
     * reading with nothing in it and a note saying why.
     */
    public CompletableFuture<MtgjsonProducts.Reading> productsFor(String setCode) {
        return catalogFor(setCode).thenApply(Catalog::products);
    }

    /**
     * What one set really sold, and the decks those products name.
     * <p>Out of the same file the collation comes from, read once. Never fails the future for
     * a set that simply sold nothing sealed: that comes back as a reading with nothing in it
     * and a note saying why.
     */
    public CompletableFuture<Catalog> catalogFor(String setCode) {
        String set = setCode == null ? "" : setCode.trim().toLowerCase(java.util.Locale.ROOT);
        Catalog known = catalogsRead.get(set);
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        return CompletableFuture.supplyAsync(() -> {
            Catalog cached = catalogsRead.get(set);
            if (cached != null) {
                return cached;
            }
            try {
                JsonObject file = feed.setFile(set).orElse(null);
                if (file == null) {
                    Catalog nothing = new Catalog(
                            new MtgjsonProducts.Reading(
                                    set, List.of(), List.of(set + " is not a set with a file")),
                            MtgjsonDecks.Reading.NOTHING);
                    catalogsRead.put(set, nothing);
                    return nothing;
                }
                Map<String, java.util.UUID> printings = MtgjsonCollation.printings(file);
                MtgjsonProducts.Reading reading = MtgjsonProducts.read(file, printings);
                MtgjsonDecks.Reading decks = MtgjsonDecks.read(file, printings);
                for (String note : reading.notes()) {
                    LOGGER.info("Products for {}: {}", set, note);
                }
                for (String note : decks.notes()) {
                    LOGGER.info("Decks in {}: {}", set, note);
                }
                Catalog read = new Catalog(reading, decks);
                catalogsRead.put(set, read);
                return read;
            } catch (Exception couldNotRead) {
                throw new java.util.concurrent.CompletionException(couldNotRead);
            }
        }, executor);
    }

    /**
     * The thread this service's blocking work happens on.
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

}
