package dev.gathering.core.scryfall;

import dev.gathering.core.net.FetchException;
import dev.gathering.core.net.HttpFetcher;
import dev.gathering.core.net.HttpTransport;
import dev.gathering.core.net.RateLimiter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.SetCode;
import dev.gathering.core.card.SetRelease;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The mod's whole conversation with Scryfall.
 * <p>Everything here obeys the API guidelines: one request at a time behind a
 * {@link RateLimiter}, an identifying User-Agent, an explicit Accept header, and batch
 * resolution through the collection endpoint rather than a request per card. A hundred-card
 * decklist costs two requests, not a hundred.
 * <p>This class blocks. It is pure core with an injected transport, so it knows nothing
 * about threads; it is the adapter layer's job to keep it on a dedicated executor and off
 * every game thread.
 */
public final class ScryfallClient {

    public static final String DEFAULT_BASE_URL = "https://api.scryfall.com";

    /** The collection endpoint's documented ceiling. */
    public static final int COLLECTION_BATCH_SIZE = 75;

    /**
     * How many pages of a set's printings are read before giving up.
     * <p>At a hundred and seventy-five a page, eight pages is fourteen hundred printings - which
     * several real sets are past. Secret Lair and the two list-shaped sets are the obvious ones, and
     * they are exactly the sets somebody would notice a hole in.
     * <p>Raised, and no longer silent about running out: what this list is for is telling the
     * coverage auditor which cards exist, and a printing that never appears in it is never reported
     * as unobtainable and never swept into the Archive Pack. It is simply unreachable, and nothing
     * anywhere says so - which the faucet code names as the worse of the two ways to be wrong.
     */
    private static final int MOST_SEARCH_PAGES = 40;

    private final HttpFetcher fetcher;
    private final String baseUrl;
    private final Map<String, String> headers;

    public ScryfallClient(HttpTransport transport, RateLimiter rateLimiter, String userAgent) {
        this(transport, rateLimiter, userAgent, DEFAULT_BASE_URL,
                HttpFetcher.DEFAULT_MAX_ATTEMPTS, Thread::sleep);
    }

    public ScryfallClient(
            HttpTransport transport,
            RateLimiter rateLimiter,
            String userAgent,
            String baseUrl,
            int maxAttempts,
            RateLimiter.Sleeper sleeper) {
        this.fetcher = new HttpFetcher(transport, rateLimiter, maxAttempts,
                HttpFetcher.DEFAULT_BACKOFF_MILLIS, sleeper);
        this.baseUrl = stripTrailingSlash(java.util.Objects.requireNonNull(baseUrl, "baseUrl"));
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

    /**
     * Every printing of one card, cheapest first.
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
        // Without another language's copies of the English printings: sorted cheapest first, a
        // Spanish Foreign Black Border copy headed the chooser and was what a decklist resolved to.
        return dev.gathering.core.card.ForeignPrintings.withoutCopies(cards);
    }

    /**
     * Every printing in one set.
     * <p>What a set with no published collation has to be opened from: the cards that are in
     * it, so a pack can be dealt off plain rarity odds instead of not existing.
     * <p>Paged by number rather than by following the {@code next_page} the reply carries.
     * The set code is checked and the query built from it here, so nothing a server owner
     * typed - and nothing a reply contained - reaches the network as a URL. It is the same
     * rule the deck-link reader works to, for the same reason.
     *
     * @param setCode the set as Scryfall writes it, letters and digits only
     */
    public Printings everyPrintingOf(String setCode) throws IOException {
        String code = checkedSetCode(setCode);
        List<ScryfallCardCodec.ParsedCard> found = new ArrayList<>();
        boolean allOfIt = true;
        for (int page = 1; page <= MOST_SEARCH_PAGES; page++) {
            JsonObject json = getJson("/cards/search?unique=prints&order=set&page=" + page
                    + "&q=" + encode("set:" + code));
            if (json == null) {
                break;
            }
            // Kept with their original JSON, because the caller's next move is to put them in
            // the cache and the cache stores the body rather than a re-serialized model.
            found.addAll(ScryfallCardCodec.parseCollectionEntries(json));
            JsonElement more = json.get("has_more");
            if (more == null || !more.isJsonPrimitive() || !more.getAsBoolean()) {
                allOfIt = true;
                break;
            }
            allOfIt = false;
        }
        return new Printings(List.copyOf(found), allOfIt);
    }

    /**
     * Every printing in a set, and whether that is all of them.
     * <p>The second half is the point. This list is what the coverage auditor computes the
     * completeness guarantee from, so a set read short is a set with cards in it that nothing can
     * ever give a player - and a short list that does not say it is short reports no such thing.
     * Everything else here that truncates says so; this did not, because it had nowhere to say it:
     * {@code :core} has no logger and is not going to get one. So it is returned, and the caller
     * that does have one says it.
     *
     * @param allOfThem false when the pages ran out with more still to read
     */
    public record Printings(List<ScryfallCardCodec.ParsedCard> cards, boolean allOfThem) {
    }

    /** The same, for callers that only want the cards. */
    public List<ScryfallCardCodec.ParsedCard> everyPrintingIn(String setCode) throws IOException {
        return everyPrintingOf(setCode).cards();
    }

    /**
     * Every set Scryfall knows about, newest listed first.
     * <p>One request and about a megabyte, which is why nothing asks it per card: what it is
     * for is working out which set is the current one, once, when a server starts.
     * <p>Not paged. Scryfall returns the whole list in one reply and says so with
     * {@code has_more: false}; if that ever changes, a truncated list still answers the only
     * question asked of it, because the list arrives newest first.
     */
    public List<SetRelease> everySet() throws IOException {
        JsonObject json = getJson("/sets");
        return json == null ? List.of() : ScryfallSetCodec.parseList(json);
    }

    /**
     * A set code, or a refusal.
     * <p>This goes into a search query and arrives from a server config or a command
     * argument. What counts as one is {@link SetCode}'s to say, in one place.
     */
    private static String checkedSetCode(String setCode) throws FetchException {
        return SetCode.of(setCode).orElseThrow(
                () -> new FetchException("'" + setCode + "' is not a set code", -1));
    }

    /**
     * Tokens whose name matches, most-printed first.
     * <p>Its own lookup rather than a name search with a filter, because the named endpoint
     * deliberately prefers real cards: asking it for "Thrull" returns the creature from Fallen
     * Empires, not the token Tevesh Szat makes. Tokens live in their own layout on Scryfall and
     * this asks for that layout by name.
     * <p>The exact name first, and only when nothing has exactly that name a looser search.
     * Asked loosely every time, "Cat" answered with every token whose name contains the word -
     * a Cat Warrior, a Cat Dragon - and whichever was printed last was the one made. Exactly,
     * it answers with the tokens that are called Cat: which still differ, and that is the
     * point. Half the tokens anybody wants are called something like "Beast" with several
     * different bodies, so the answer is a short list to pick from, not one guess - and the
     * caller asks the player when the list has more than one token on it.
     * <p>One printing per distinct token ({@code unique=cards}), newest first.
     */
    public List<CardMetadata> tokensNamed(String name) throws IOException {
        String cleaned = name == null ? "" : name.replace("\"", "").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<CardMetadata> exactly = tokenSearch("t:token !" + '"' + cleaned + '"');
        return exactly.isEmpty() ? tokenSearch("t:token " + '"' + cleaned + '"') : exactly;
    }

    private List<CardMetadata> tokenSearch(String query) throws IOException {
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
        return parse(fetcher.get(baseUrl + path, headers, "GET " + path));
    }

    private JsonObject postJson(String path, String body) throws IOException {
        return parse(fetcher.post(baseUrl + path, body, headers, "POST " + path));
    }

    private static JsonObject parse(HttpTransport.HttpReply reply) throws FetchException {
        if (reply.status() == 404) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(reply.body());
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            throw new FetchException("Scryfall returned a body that is not JSON", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
