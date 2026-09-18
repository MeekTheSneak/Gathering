package dev.gathering.service;

import dev.gathering.core.net.JdkHttpTransport;
import dev.gathering.core.net.RateLimiter;
import dev.gathering.Gathering;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.deck.CachingCardSource;
import dev.gathering.core.deck.DeckImporter;
import dev.gathering.core.deck.ResolvedDeck;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.DiskCardMetadataStore;
import dev.gathering.core.scryfall.ScryfallClient;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * <p>Both the Scryfall client and the disk cache block by design. This service is the only
 * thing that calls them, it does so on its own executor, and every method here hands back a
 * {@link CompletableFuture}. There is no blocking method to misuse - the API shape is the
 * enforcement.
 * <p>The executor is single-threaded on purpose: Scryfall's guidelines ask for one request
 * at a time with a delay between them, and a single worker makes that structural instead of
 * a promise the rate limiter has to keep alone.
 */
public final class CardDataService implements AutoCloseable {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Gathering");

    private static final String CACHE_DIRECTORY = "card-cache";

    /**
     * The pipeline belonging to the running server.
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
    private final BulkCardData bulk;
    private final dev.gathering.core.scryfall.bulk.BulkFirstStore lookups;

    private CardDataService(Path cacheRoot, String userAgent) throws IOException {
        // One worker, and a bounded queue in front of it. Unbounded, a queue of lookups could grow
        // faster than Scryfall's rate lets it drain - every request waiting behind the rest, and
        // memory growing with it. Past the bound a lookup fails at once, and says so.
        // Still one worker, and a queue that is not first come first served: a lookup a player is
        // waiting on - a pack being opened, a deck being read - goes ahead of anything that is not.
        this.executor = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.PriorityBlockingQueue<>(), ServiceThreads.named("gathering-scryfall"));
        this.store = new DiskCardMetadataStore(cacheRoot);
        this.client = new ScryfallClient(new JdkHttpTransport(), RateLimiter.defaultLimiter(), userAgent);
        // The local copy of Scryfall's bulk file first, on threads of its own; see BulkCardData.
        // Never in the in-world tests: a copy arriving part way through a run would answer some lookups and
        // not others, and the gate would download seventy megabytes it has no use for.
        this.bulk = new BulkCardData(cacheRoot, userAgent, ServerSettings.get().cards().bulkData()
                && System.getProperty("gathering.bulkcards.off") == null);
        this.lookups = new dev.gathering.core.scryfall.bulk.BulkFirstStore(store, bulk::ready);
        this.source = new CachingCardSource(lookups, client);
        this.importer = new DeckImporter(
                source, new dev.gathering.core.deck.ArchidektDeckSource(new JdkHttpTransport(), userAgent));
    }

    /** Builds the service from what the loader knows, and makes it the running server's. */
    public static CardDataService start(Platform platform) throws IOException {
        CardDataService service = new CardDataService(
                platform.dataDirectory().resolve(CACHE_DIRECTORY), userAgentFor(platform));
        active = service;
        return service;
    }

    /**
     * Every set Scryfall lists, by code, once it has been fetched.
     * <p>Volatile and written once: several things ask for it and any of them may be first.
     * Never written empty, so a failed fetch is asked again rather than remembered as an
     * answer.
     */
    private volatile Map<String, dev.gathering.core.card.SetRelease> everySet;

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
        return supply(Lane.PLAYER, () -> importer.importText(decklistText));
    }

    /**
     * One printing by canonical identity, through the same cache-then-network path as an
     * import, so a card fetched here is a card the next import does not have to fetch.
     */
    public CompletableFuture<Optional<CardMetadata>> card(UUID scryfallId) {
        Optional<CardMetadata> known = scryfallId == null ? Optional.empty() : store.inMemory(scryfallId);
        if (known.isPresent()) {
            return CompletableFuture.completedFuture(known);
        }
        return bulk.orElse(index -> lookups.find(CardQuery.byId(scryfallId)).map(Optional::of).orElse(null),
                () -> supply(Lane.PLAYER, () -> {
            CardQuery query = CardQuery.byId(scryfallId);
            return source.resolve(List.of(query)).get(query);
        }));
    }

    /**
     * One card by exact name, through the cache first.
     * <p>The single-card counterpart to an import: what the grant command and, later, a
     * collection search resolve against.
     */
    public CompletableFuture<Optional<CardMetadata>> findByName(String cardName) {
        return bulk.orElse(index -> lookups.find(CardQuery.byName(cardName)).map(Optional::of).orElse(null),
                () -> supply(Lane.PLAYER, () -> {
            CardQuery query = CardQuery.byName(cardName);
            return source.resolve(List.of(query)).get(query);
        }));
    }

    /**
     * What is already known about a printing, without going to look.
     * <p>The one lookup a game thread may make. Everything else here is a network call
     * wearing a cache, and a game thread that waited on one would stall the server; this
     * reads a map. An empty answer means "not looked up yet" rather than "no such card", so
     * the only safe thing to do with one is treat the card as unknown.
     */
    public java.util.Optional<CardMetadata> peek(UUID scryfallId) {
        return store.inMemory(scryfallId);
    }

    /**
     * A number that changes whenever what {@link #peek} could answer might have changed.
     * <p>Read it <em>before</em> peeking, and anything built from those answers is current for
     * as long as this still returns the same number.
     */
    public long knownGeneration() {
        return store.generation();
    }

    /**
     * Several printings at once, cache first.
     * <p>What answers a client opening a deck: one batched resolution rather than a hundred
     * separate ones, and usually zero network at all.
     */
    public CompletableFuture<List<CardMetadata>> findAll(List<UUID> scryfallIds) {
        // Answered at once where every one is already in memory, which after the first pack of a
        // set is nearly every pack: no queue, no thread, nothing to wait behind.
        List<CardMetadata> known = new java.util.ArrayList<>(scryfallIds.size());
        for (UUID printing : scryfallIds) {
            Optional<CardMetadata> found = printing == null ? Optional.empty() : store.inMemory(printing);
            if (found.isEmpty()) {
                known = null;
                break;
            }
            if (!known.contains(found.get())) {
                known.add(found.get());
            }
        }
        if (known != null) {
            return CompletableFuture.completedFuture(List.copyOf(known));
        }
        return bulk.orElse(index -> allKnown(scryfallIds), () -> supply(Lane.PLAYER, () -> {
            List<CardQuery> queries = scryfallIds.stream().map(CardQuery::byId).toList();
            return List.copyOf(source.resolve(queries).found().values());
        }));
    }

    /** Every one of these without the network, or null when any of them needs it. */
    private List<CardMetadata> allKnown(List<UUID> scryfallIds) {
        java.util.Map<UUID, CardMetadata> found = new java.util.LinkedHashMap<>();
        for (UUID printing : scryfallIds) {
            if (!found.containsKey(printing)) {
                CardMetadata card = lookups.find(CardQuery.byId(printing)).orElse(null);
                if (card == null) {
                    return null;
                }
                found.put(printing, card);
            }
        }
        return List.copyOf(found.values());
    }

    /**
     * How many stale printings this server will re-fetch at once.
     * <p>A refresh is a real network request, so a table where six people all sit down with
     * decks nobody has looked up since last season must not become six hundred of them at
     * once. Comfortably a deck's worth, which is the unit this is asked in.
     */
    private static final int MOST_REFRESHED_AT_ONCE = 128;

    /** Printings a refresh is already out for, so two decks sharing a card ask once. */
    private final java.util.Set<UUID> refreshing =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Fetches these printings again, past the cache, and stores what comes back.
     * <p>The one thing {@link #findAll} cannot do. A cached card is answered from the cache
     * for ever, which is the whole point of the cache and exactly wrong for legality: bans
     * and rotations change what a card already on this disk is allowed to do. A deck check
     * that finds stale entries asks for this, and until it lands the check keeps saying the
     * verdict rests on old data.
     * <p>Coalesced, so the same printing is not fetched twice while one request is out, and
     * bounded by {@link #MOST_REFRESHED_AT_ONCE}. Nothing waits on the result: the point is
     * that the <em>next</em> check is current.
     *
     * @return how many printings this call actually asked for
     */
    public int refresh(List<UUID> scryfallIds) {
        if (scryfallIds == null || scryfallIds.isEmpty()) {
            return 0;
        }
        List<UUID> asking = new java.util.ArrayList<>();
        for (UUID printing : scryfallIds) {
            if (printing == null || asking.size() >= MOST_REFRESHED_AT_ONCE) {
                continue;
            }
            if (refreshing.add(printing)) {
                asking.add(printing);
            }
        }
        if (asking.isEmpty()) {
            return 0;
        }
        List<UUID> wanted = List.copyOf(asking);
        supply(Lane.BACKGROUND, () -> source.refresh(wanted.stream().map(CardQuery::byId).toList()))
                .whenComplete((result, failure) -> {
                    refreshing.removeAll(wanted);
                    if (failure != null) {
                        LOGGER.warn("Could not refresh {} stale printing(s): {}",
                                wanted.size(), failure.toString());
                    }
                });
        return wanted.size();
    }

    /**
     * Reads these printings out of the cache and into the index, off the game thread.
     * <p>Not {@link #findAll}: this never touches the network. It is for a caller that must
     * not block a tick on a file read and must not wait on Scryfall either - the deck check,
     * which promises both. A printing that is not on this disk stays unknown, and the caller's
     * answer for an unknown card is "no opinion", which is the honest one.
     *
     * @return completes on the card executor once every one of them has been looked for
     */
    public CompletableFuture<Void> warm(List<UUID> scryfallIds) {
        if (scryfallIds == null || scryfallIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<UUID> wanted = List.copyOf(scryfallIds);
        try {
            return CompletableFuture.runAsync(() -> {
                for (UUID printing : wanted) {
                    // The store indexes what it reads, so asking is what warms it.
                    lookups.find(CardQuery.byId(printing));
                }
            }, lane(Lane.PLAYER));
        } catch (java.util.concurrent.RejectedExecutionException full) {
            // The queue is full, or the pool is stopping. Every other way into this service catches
            // this and answers with a failed future; this one threw, and it is called from the server
            // thread - DeckCheck.nowOrSoon, which a table and a tournament both reach. Warming is a
            // courtesy: not warming is a slower lookup later, never a failure now.
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Every printing of a card, cheapest first - what the import screen's chooser offers. */
    public CompletableFuture<List<CardMetadata>> printingsOf(String cardName) {
        return bulk.orElse(index -> nonEmpty(index.printingsOf(cardName)),
                () -> supply(Lane.PLAYER, () -> client.printingsOf(cardName)));
    }

    /**
     * Every printing in a set, read to be counted rather than kept.
     * <p>For the archive's audit of all of Magic's history, which reads every set there is: the
     * same search as {@link #everyPrintingIn}, without writing each card's details into the cache.
     * The audit keeps only which printings exist, and filling the cache with the whole history of
     * the game to learn that would be tens of thousands of files for a list of ids. A card from
     * the archive is looked up by id when somebody opens it, as any card is.
     *
     * @return empty where the pages ran out before the set did, so a set read short is never
     *         audited as though it were complete
     */
    public CompletableFuture<java.util.Optional<List<CardMetadata>>> everyPrintingToAudit(String setCode) {
        return bulk.orElse(index -> index.printingsIn(setCode).map(java.util.Optional::of).orElse(null),
                () -> supply(Lane.BACKGROUND, () -> {
            var printings = client.everyPrintingOf(setCode);
            if (!printings.allOfThem()) {
                LOGGER.warn("The card list for set {} came back short, so the archive does not audit it "
                        + "yet.", setCode);
                return java.util.Optional.<List<CardMetadata>>empty();
            }
            List<CardMetadata> found = new java.util.ArrayList<>(printings.cards().size());
            for (var parsed : printings.cards()) {
                found.add(parsed.metadata());
            }
            return java.util.Optional.of(List.copyOf(found));
        }));
    }

    /**
     * Every printing in one set, kept in the cache on the way past.
     * <p>What a set nobody has published the collation of is opened from. Stored as it
     * arrives, because the next thing that happens to these cards is a pack being dealt out
     * of them and then looked up one by one - and looking them up again over the network
     * would be three hundred cards fetched twice.
     */
    public CompletableFuture<List<CardMetadata>> everyPrintingIn(String setCode) {
        // Asked once a run per set: a set's printings are read page by page, up to forty requests, and
        // every pack of a set with no published collation used to read all of them again.
        String key = setCode == null ? "" : setCode.trim().toLowerCase(java.util.Locale.ROOT);
        CompletableFuture<List<CardMetadata>> reading;
        synchronized (setPrintings) {
            CompletableFuture<List<CardMetadata>> known = setPrintings.get(key);
            if (known != null) {
                return known;
            }
            reading = readEveryPrintingIn(setCode);
            setPrintings.put(key, reading);
        }
        CompletableFuture<List<CardMetadata>> asked = reading;
        reading.whenComplete((found, failure) -> {
            if (failure != null || found == null || found.isEmpty()) {
                synchronized (setPrintings) {
                    setPrintings.remove(key, asked);
                }
            }
        });
        return reading;
    }

    /**
     * How many sets' printings are kept at once, least recently asked for first out.
     * <p>Bounded because the key is a set code a player chooses: the collection screen reads a set
     * whenever somebody opens one, and Magic has some nine hundred of them - so an afternoon of
     * clicking through sets pinned every card of every one of them for the life of the server.
     * A pack is dealt out of the set that was just read, so what matters is keeping the last few.
     */
    private static final int MOST_SETS_KEPT = 32;

    /** Each set's printings, once read whole this run. See {@link #everyPrintingIn}. */
    private final Map<String, CompletableFuture<List<CardMetadata>>> setPrintings =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<List<CardMetadata>>> eldest) {
                    return size() > MOST_SETS_KEPT;
                }
            };

    private CompletableFuture<List<CardMetadata>> readEveryPrintingIn(String setCode) {
        return bulk.orElse(index -> index.printingsIn(setCode).map(cards -> {
            lookups.remember(cards, index);
            return List.copyOf(cards);
        }).orElse(null), () -> supply(Lane.PLAYER, () -> {
            List<CardMetadata> found = new java.util.ArrayList<>();
            var printings = client.everyPrintingOf(setCode);
            for (var parsed : printings.cards()) {
                try {
                    store.store(parsed.metadata(), parsed.raw());
                } catch (java.io.UncheckedIOException couldNotCache) {
                    // The cache is an optimisation. Failing the whole request because one file
                    // could not be written throws away data that arrived, and told the player the
                    // import could not reach Scryfall - which it had.
                    LOGGER.warn("Could not cache {}: {}",
                            parsed.metadata().scryfallId(), couldNotCache.getMessage());
                }
                found.add(parsed.metadata());
            }
            if (!printings.allOfThem()) {
                // Said where there is somewhere to say it. A set read short is a set whose coverage
                // audit is computed from an incomplete list, so cards in it can be unobtainable and
                // never reported as such - which is the one way of being wrong the faucet code names
                // as worse than the other.
                LOGGER.warn("The card list for set {} came back short at {} printings, so what is "
                        + "obtainable in it cannot be audited properly.", setCode, found.size());
            }
            return List.copyOf(found);
        }));
    }

    /**
     * Every set there has ever been, by its code.
     * <p>Fetched once and kept, because it is a megabyte of reply that changes about six times
     * a year and three separate features want it: which set is current, which sets a server
     * draws packs from, and how big a set is when somebody asks how much of one they have.
     * Asking Scryfall three times for the same answer would be rude as well as slow.
     * <p>The failure is remembered as an absence rather than as a value, so a server that
     * started with no network answers again the next time somebody asks rather than insisting
     * for the rest of its life that there are no sets.
     */
    public CompletableFuture<Map<String, dev.gathering.core.card.SetRelease>> allSets() {
        Map<String, dev.gathering.core.card.SetRelease> known = everySet;
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        return supply(Lane.NORMAL, () -> {
            Map<String, dev.gathering.core.card.SetRelease> byCode = new java.util.LinkedHashMap<>();
            for (dev.gathering.core.card.SetRelease set : client.everySet()) {
                byCode.putIfAbsent(set.code(), set);
            }
            Map<String, dev.gathering.core.card.SetRelease> settled = Map.copyOf(byCode);
            if (!settled.isEmpty()) {
                everySet = settled;
            }
            return settled;
        });
    }

    /**
     * Every premier set that has come out, newest first, from Scryfall's list of all of them.
     * <p>The whole list rather than only the newest, because a server drawing its packs from
     * the last few releases wants the same answer the current-set question does and it would
     * be a second megabyte to ask twice. One request, asked once at start.
     */
    public CompletableFuture<List<dev.gathering.core.card.SetRelease>> premierSets(
            String today, int howMany) {
        return allSets().thenApply(sets ->
                dev.gathering.core.card.SetRelease.recent(
                        List.copyOf(sets.values()), today, howMany));
    }

    /** Tokens matching a name, for the "make a token" screen. */
    public CompletableFuture<List<CardMetadata>> tokensNamed(String name) {
        return bulk.orElse(index -> nonEmpty(index.tokensNamed(name)),
                () -> supply(Lane.PLAYER, () -> client.tokensNamed(name)));
    }

    /** A list, or null for an empty one, so an empty answer from the bulk copy still asks Scryfall. */
    private static <T> List<T> nonEmpty(List<T> list) {
        return list.isEmpty() ? null : list;
    }

    /**
     * Whether the local copy of Scryfall's bulk file has this printing, without reading it.
     * <p>Memory only, so a game thread may ask: a card it has is worth waiting a moment for.
     */
    public boolean knownLocally(UUID printing) {
        return bulk.contains(printing);
    }

    /**
     * Warms the name and printing indexes from whatever is already on disk.
     * <p>Worth doing once at server start so a re-import of a known decklist makes no
     * requests at all; costs one pass over the cache directory and nothing afterwards.
     */
    public CompletableFuture<Integer> warmCache() {
        return supply(Lane.BACKGROUND, store::loadIndex);
    }

    public DiskCardMetadataStore store() {
        return store;
    }

    @Override
    public void close() {
        if (active == this) {
            active = null;
        }
        bulk.close();
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

    /** The most lookups waiting for the card worker at once. */
    static final int MOST_QUEUED = 4096;

    /**
     * How soon a lookup is answered, against the others waiting.
     * <p>A player opening a pack used to wait behind whatever was already queued - a set searched page by
     * page, an import, an audit - because the one worker took them in the order they came. The worker is
     * still one, for Scryfall's sake; what it takes next is the most urgent thing waiting.
     */
    enum Lane {
        /** Somebody is watching a screen for this: a pack, a deck, a card lookup. */
        PLAYER,
        /** Needed soon, by the server rather than by a person: a set's cards, the list of sets. */
        NORMAL,
        /** Nobody is waiting: refreshes, audits, warming the index. */
        BACKGROUND
    }

    /** One piece of work waiting for the worker, most urgent first and oldest first within that. */
    private record Waiting(Runnable work, Lane lane, long order) implements Runnable, Comparable<Waiting> {

        @Override
        public void run() {
            work.run();
        }

        @Override
        public int compareTo(Waiting other) {
            int byLane = lane.compareTo(other.lane);
            return byLane != 0 ? byLane : Long.compare(order, other.order);
        }
    }

    private final java.util.concurrent.atomic.AtomicLong arrivals = new java.util.concurrent.atomic.AtomicLong();

    /** An executor that queues its work in a lane, refusing past {@link #MOST_QUEUED} waiting. */
    private java.util.concurrent.Executor lane(Lane lane) {
        return work -> {
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor pool && pool.getQueue().size() >= MOST_QUEUED) {
                throw new java.util.concurrent.RejectedExecutionException("The card lookup queue is full");
            }
            executor.execute(new Waiting(work, lane, arrivals.incrementAndGet()));
        };
    }

    private <T> CompletableFuture<T> supply(Lane lane, IoSupplier<T> work) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return work.get();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, lane(lane));
        } catch (java.util.concurrent.RejectedExecutionException refused) {
            return CompletableFuture.failedFuture(new IOException(executor.isShutdown()
                    ? "Card lookups have stopped; the server is shutting down"
                    : "The card lookup queue is full; try again shortly", refused));
        }
    }


    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
