package dev.gathering.core.scryfall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardMetadata;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The mod's whole conversation with Scryfall.
 *
 * <p>Everything here obeys the API guidelines: one request at a time behind a
 * {@link RateLimiter}, an identifying User-Agent, an explicit Accept header, and batch
 * resolution through the collection endpoint rather than a request per card. A hundred-card
 * decklist costs two requests, not a hundred.
 *
 * <p>This class blocks. It is pure core with an injected transport, so it knows nothing
 * about threads; it is the adapter layer's job to keep it on a dedicated executor and off
 * every game thread.
 */
public final class ScryfallClient {

    public static final String DEFAULT_BASE_URL = "https://api.scryfall.com";

    /** The collection endpoint's documented ceiling. */
    public static final int COLLECTION_BATCH_SIZE = 75;

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 500L;

    private final HttpTransport transport;
    private final RateLimiter rateLimiter;
    private final String baseUrl;
    private final Map<String, String> headers;
    private final int maxAttempts;
    private final RateLimiter.Sleeper sleeper;

    public ScryfallClient(HttpTransport transport, RateLimiter rateLimiter, String userAgent) {
        this(transport, rateLimiter, userAgent, DEFAULT_BASE_URL, DEFAULT_MAX_ATTEMPTS, Thread::sleep);
    }

    public ScryfallClient(
            HttpTransport transport,
            RateLimiter rateLimiter,
            String userAgent,
            String baseUrl,
            int maxAttempts,
            RateLimiter.Sleeper sleeper) {
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        this.rateLimiter = java.util.Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.baseUrl = stripTrailingSlash(java.util.Objects.requireNonNull(baseUrl, "baseUrl"));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.sleeper = java.util.Objects.requireNonNull(sleeper, "sleeper");
        this.headers = Map.of(
                "User-Agent", java.util.Objects.requireNonNull(userAgent, "userAgent"),
                "Accept", "application/json",
                "Content-Type", "application/json");
    }

    /** One printing by its canonical identity. */
    public Optional<CardMetadata> cardById(UUID scryfallId) throws IOException {
        JsonObject json = getJson("/cards/" + scryfallId);
        return json == null ? Optional.empty() : ScryfallCardCodec.parse(json);
    }

    /** Exact name lookup, for the single-card path and for import fallbacks. */
    public Optional<CardMetadata> cardByExactName(String name) throws IOException {
        // The named endpoint is happier with combined names than the collection endpoint is,
        // but a half name resolves correctly on both, so use one rule everywhere.
        JsonObject json = getJson("/cards/named?exact=" + encode(CardQuery.lookupName(name)));
        return json == null ? Optional.empty() : ScryfallCardCodec.parse(json);
    }

    /**
     * Every printing of one card, cheapest first.
     *
     * <p>This is what makes "resolve to the cheapest matching printing by default, with a
     * chooser in the import screen" one request instead of a research project.
     */
    public List<CardMetadata> printingsOf(String name) throws IOException {
        String query = "!" + '"' + CardQuery.lookupName(name).replace("\"", "") + '"';
        JsonObject json = getJson("/cards/search?unique=prints&order=usd&dir=asc&q=" + encode(query));
        if (json == null) {
            return List.of();
        }
        List<CardMetadata> cards = new ArrayList<>(ScryfallCardCodec.parseCollection(json));
        // Scryfall pages at 175 results; a card with more printings than that is not a thing
        // the import screen needs, so the first page is the answer.
        return List.copyOf(cards);
    }

    /**
     * Tokens whose name matches, most-printed first.
     *
     * <p>Its own lookup rather than a name search with a filter, because the named endpoint
     * deliberately prefers real cards: asking it for "Thrull" returns the creature from Fallen
     * Empires, not the token Tevesh Szat makes. Tokens live in their own layout on Scryfall and
     * this asks for that layout by name.
     *
     * <p>A prefix search rather than an exact one. Nobody types "Thrull Token" and half the
     * tokens anybody wants are called something like "Beast" with six different printings, so
     * the useful answer is a short list to pick from rather than one guess.
     */
    public List<CardMetadata> tokensNamed(String name) throws IOException {
        String cleaned = name == null ? "" : name.replace("\"", "").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        String query = "t:token " + '"' + cleaned + '"';
        JsonObject json = getJson("/cards/search?unique=cards&order=released&dir=desc&q=" + encode(query));
        return json == null ? List.of() : List.copyOf(ScryfallCardCodec.parseCollection(json));
    }

    /**
     * Batch resolution. Splits into requests of {@value #COLLECTION_BATCH_SIZE}, de-duplicates
     * identical queries, and reports back which queries nothing answered.
     */
    public CollectionResult resolve(List<CardQuery> queries) throws IOException {
        if (queries == null || queries.isEmpty()) {
            return CollectionResult.empty();
        }

        List<CardQuery> unique = deduplicate(queries);
        CollectionResult result = CollectionResult.empty();
        for (int start = 0; start < unique.size(); start += COLLECTION_BATCH_SIZE) {
            List<CardQuery> batch = unique.subList(start, Math.min(start + COLLECTION_BATCH_SIZE, unique.size()));
            result = result.merge(resolveBatch(batch));
        }
        return result;
    }

    private CollectionResult resolveBatch(List<CardQuery> batch) throws IOException {
        JsonArray identifiers = new JsonArray();
        for (CardQuery query : batch) {
            identifiers.add(query.toJson());
        }
        JsonObject body = new JsonObject();
        body.add("identifiers", identifiers);

        JsonObject response = postJson("/cards/collection", body.toString());
        if (response == null) {
            return new CollectionResult(Map.of(), batch, Map.of());
        }

        List<ScryfallCardCodec.ParsedCard> cards = ScryfallCardCodec.parseCollectionEntries(response);
        Map<String, CardMetadata> found = new LinkedHashMap<>();
        Map<UUID, JsonObject> raw = new LinkedHashMap<>();
        List<CardQuery> notFound = new ArrayList<>();

        for (CardQuery query : batch) {
            Optional<ScryfallCardCodec.ParsedCard> match =
                    cards.stream().filter(card -> CardQueryMatcher.matches(query, card.metadata())).findFirst();
            if (match.isPresent()) {
                found.put(query.key(), match.get().metadata());
                raw.put(match.get().metadata().scryfallId(), match.get().raw());
            } else {
                notFound.add(query);
            }
        }
        return new CollectionResult(found, notFound, raw);
    }

    private static List<CardQuery> deduplicate(List<CardQuery> queries) {
        Map<String, CardQuery> unique = new LinkedHashMap<>();
        for (CardQuery query : queries) {
            unique.putIfAbsent(query.key(), query);
        }
        return List.copyOf(new ArrayList<>(new LinkedHashSet<>(unique.values())));
    }

    /** Returns null for a 404, which is an answer ("no such card"), not a failure. */
    private JsonObject getJson(String path) throws IOException {
        return parse(send(() -> transport.get(baseUrl + path, headers), "GET " + path));
    }

    private JsonObject postJson(String path, String body) throws IOException {
        return parse(send(() -> transport.post(baseUrl + path, body, headers), "POST " + path));
    }

    private HttpTransport.HttpReply send(Request request, String description) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScryfallException("Interrupted before " + description, e);
            }

            HttpTransport.HttpReply reply;
            try {
                reply = request.send();
            } catch (IOException e) {
                lastFailure = new ScryfallException(description + " failed", e);
                backoff(attempt, description);
                continue;
            }

            if (reply.isSuccess() || reply.status() == 404) {
                return reply;
            }
            if (!reply.isRetryable()) {
                throw new ScryfallException(description + " returned HTTP " + reply.status(), reply.status());
            }
            lastFailure = new ScryfallException(description + " returned HTTP " + reply.status(), reply.status());
            backoff(attempt, description);
        }
        throw lastFailure != null
                ? lastFailure
                : new ScryfallException(description + " failed after " + maxAttempts + " attempts", -1);
    }

    private void backoff(int attempt, String description) throws ScryfallException {
        if (attempt >= maxAttempts) {
            return;
        }
        try {
            sleeper.sleep(RETRY_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScryfallException("Interrupted while backing off before retrying " + description, e);
        }
    }

    private static JsonObject parse(HttpTransport.HttpReply reply) throws ScryfallException {
        if (reply.status() == 404) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(reply.body());
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            throw new ScryfallException("Scryfall returned a body that is not JSON", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @FunctionalInterface
    private interface Request {
        HttpTransport.HttpReply send() throws IOException;
    }
}
