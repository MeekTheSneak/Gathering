package dev.gathering.core.booster;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.SetCode;
import dev.gathering.core.net.FetchException;
import dev.gathering.core.net.HttpFetcher;
import dev.gathering.core.net.HttpTransport;
import dev.gathering.core.net.RateLimiter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Where collation comes from: one MTGJSON set file at a time, cached on disk.
 * <p>Per set on demand, never the everything file. MTGJSON publishes one download with every
 * set in it and it is enormous; a server that wants to sell this month's boosters wants this
 * month's set, and the file for it is a few megabytes.
 * <p>It follows a pack where it reaches. A modern booster's Special Guest or List slot is
 * printed in a different set, and the file says which, so asking for one set quietly fetches
 * the handful it points at and comes back with every kind of pack openable rather than with
 * most of them dropped. A companion set that cannot be fetched is a note on the reading, not
 * a failure: the packs that do not need it still open.
 * <p>The set code is checked before it is put in a URL or a path. It arrives from a server
 * config, and a config value is a thing a person typed.
 * <p>This blocks on the network and on the disk. Pure core with an injected transport, so it
 * knows nothing about threads; keeping it off every game thread is the adapter layer's job.
 */
public final class MtgjsonFeed {

    public static final String DEFAULT_BASE_URL = "https://mtgjson.com/api/v5";

    /**
     * How long a cached set file is trusted for.
     * <p>Collation for a released set does not change, but MTGJSON corrects data, and a week
     * is short enough to pick a correction up and long enough that a server never fetches the
     * same file twice in a session.
     */
    public static final long DEFAULT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000;

    /** As many companion sets as a real product reaches into, with room to spare. */
    private static final int MOST_COMPANION_SETS = 12;

    /** A set file is a few megabytes. Anything this size is not one. */
    private static final int MOST_BODY_CHARACTERS = 96 * 1024 * 1024;

    /** MTGJSON's name for the list of every set, which is not a set code. */
    private static final String SET_LIST = "SetList";

    private final HttpFetcher fetcher;
    private final String baseUrl;
    private final Map<String, String> headers;
    private final Path cacheRoot;
    private final long maxAgeMillis;
    private final RateLimiter.Clock clock;

    public MtgjsonFeed(HttpTransport transport, RateLimiter rateLimiter, String userAgent,
            Path cacheRoot) throws IOException {
        this(new HttpFetcher(transport, rateLimiter), userAgent, DEFAULT_BASE_URL, cacheRoot,
                DEFAULT_MAX_AGE_MILLIS, System::currentTimeMillis);
    }

    public MtgjsonFeed(
            HttpFetcher fetcher,
            String userAgent,
            String baseUrl,
            Path cacheRoot,
            long maxAgeMillis,
            RateLimiter.Clock clock) throws IOException {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.headers = Map.of(
                "User-Agent", Objects.requireNonNull(userAgent, "userAgent"),
                "Accept", "application/json");
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
        this.maxAgeMillis = Math.max(0, maxAgeMillis);
        this.clock = Objects.requireNonNull(clock, "clock");
        Files.createDirectories(cacheRoot);
    }

    /**
     * Everything openable for one set, having fetched whatever its packs reach into.
     *
     * @param setCode the set as MTGJSON and Scryfall both write it - "blb", "dmu"
     * @throws IOException when the set file could not be fetched, which is worth trying
     *                     again later. A file that arrives and is not collation is not a
     *                     failure: it comes back as a set with none, which is what it means.
     */
    public MtgjsonCollation.Reading collationFor(String setCode) throws IOException {
        String code = checked(setCode);
        JsonObject file = setFile(code).orElse(null);
        if (file == null) {
            return new MtgjsonCollation.Reading(code.toLowerCase(Locale.ROOT), Map.of(), List.of(),
                    List.of(code + " is not a set MTGJSON has a file for"));
        }

        Map<String, UUID> bridge = new LinkedHashMap<>();
        Set<String> asked = new LinkedHashSet<>();
        asked.add(code);
        List<String> troubles = new ArrayList<>();
        MtgjsonCollation.Reading reading;
        try {
            bridge.putAll(MtgjsonCollation.printings(file));
            reading = MtgjsonCollation.read(file, bridge);
        } catch (BoosterCodecException notCollation) {
            // A file that arrived and is not what this thinks a set file is. Reported as a
            // set with no collation rather than as a failure, because that is what it means
            // to everything downstream: the packs fall back to plain rarity odds, which is
            // the same answer a set nobody has published anything for gets. Failing instead
            // would take the set's cards down with the file - and they are fine.
            return new MtgjsonCollation.Reading(code.toLowerCase(Locale.ROOT), Map.of(), List.of(),
                    List.of(code + "'s collation could not be read: " + notCollation.getMessage()));
        }

        // One round is enough for real data - a set names every source its packs use up front -
        // but it loops until nothing new arrives, so a source that itself names a source is
        // followed rather than half read.
        while (!reading.alsoNeeds().isEmpty()) {
            boolean anythingNew = false;
            for (String needed : reading.alsoNeeds()) {
                String companion;
                try {
                    companion = checked(needed);
                } catch (FetchException notASetCode) {
                    troubles.add(notASetCode.getMessage());
                    continue;
                }
                // Asked about already - either it came, or it did not and has its own note.
                if (asked.contains(companion)) {
                    continue;
                }
                if (asked.size() >= MOST_COMPANION_SETS) {
                    troubles.add("stopped after " + MOST_COMPANION_SETS
                            + " sets; " + companion + " and any after it were not fetched");
                    break;
                }
                asked.add(companion);
                // A companion that will not come is a reason for some packs not to open, not a
                // reason for the whole set to fail. Said out loud, and then carried on past.
                try {
                    Optional<JsonObject> fetched = setFile(companion);
                    if (fetched.isEmpty()) {
                        troubles.add(companion + " is not a set MTGJSON has a file for");
                        continue;
                    }
                    bridge.putAll(MtgjsonCollation.printings(fetched.get()));
                    anythingNew = true;
                } catch (IOException couldNotFetch) {
                    troubles.add(companion + " could not be fetched: "
                            + couldNotFetch.getMessage());
                } catch (BoosterCodecException notASetFile) {
                    troubles.add(companion + " is not a set file: " + notASetFile.getMessage());
                }
            }
            if (!anythingNew) {
                break;
            }
            try {
                reading = MtgjsonCollation.read(file, bridge);
            } catch (BoosterCodecException notCollation) {
                // It read once already, so this cannot happen for the file itself - only for
                // something a companion brought with it. Kept rather than thrown away: what
                // was read before the companion arrived is still a true answer.
                troubles.add("re-reading with " + asked + " failed: " + notCollation.getMessage());
                break;
            }
        }

        if (troubles.isEmpty()) {
            return reading;
        }
        List<String> notes = new ArrayList<>(reading.notes());
        notes.addAll(troubles);
        return new MtgjsonCollation.Reading(
                reading.setCode(), reading.packs(), reading.alsoNeeds(), notes);
    }

    /**
     * One set's file, from the cache if it is there and recent, otherwise from MTGJSON.
     *
     * @return empty if MTGJSON has no file for that set, which is an answer rather than a
     *         failure - not every set code is a set with a file
     */
    public Optional<JsonObject> setFile(String setCode) throws IOException {
        return file(checked(setCode));
    }

    /**
     * MTGJSON's one list of every set there has ever been.
     * <p>Around ten megabytes, and it carries each set's sealed product with it. That is the
     * whole of what a server needs to know which sets sell which boosters, so a server drawing
     * its packs from every set reads this one file rather than the several hundred set files
     * it would otherwise fetch to learn the same thing. What is actually inside a product -
     * the printings and the decks - still comes from that product's own set file, and only
     * when somebody opens one.
     */
    public Optional<JsonObject> setList() throws IOException {
        return file(SET_LIST);
    }

    private Optional<JsonObject> file(String code) throws IOException {
        Path cached = cacheRoot.resolve(code + ".json");
        if (isFresh(cached)) {
            try {
                return Optional.of(parse(Files.readString(cached, StandardCharsets.UTF_8), code));
            } catch (IOException unreadable) {
                // A half-written or corrupted cache file is not worth a failure when the
                // original is one request away.
                Files.deleteIfExists(cached);
            }
        }

        HttpTransport.HttpReply reply = fetcher.get(
                baseUrl + "/" + code + ".json", headers, "fetching collation for " + code);
        if (reply.status() == 404) {
            return Optional.empty();
        }
        String body = reply.body() == null ? "" : reply.body();
        if (body.length() > MOST_BODY_CHARACTERS) {
            throw new FetchException(
                    code + " came back as " + body.length() + " characters, which is not a set file",
                    reply.status());
        }
        JsonObject file = parse(body, code);
        // Written somewhere else and moved into place, so a fetch cut off half way through
        // leaves the previous file rather than a broken one - and to a name of its own, so
        // two callers after the same set cannot write into each other's.
        Path temporary = Files.createTempFile(cacheRoot, code + "-", ".part");
        try {
            Files.writeString(temporary, body, StandardCharsets.UTF_8);
            Files.move(temporary, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return Optional.of(file);
    }

    // ------------------------------------------------------------------- bits

    private boolean isFresh(Path cached) throws IOException {
        if (!Files.isRegularFile(cached)) {
            return false;
        }
        long age = clock.currentTimeMillis() - Files.getLastModifiedTime(cached).toMillis();
        return age >= 0 && age < maxAgeMillis;
    }

    private static JsonObject parse(String body, String code) throws FetchException {
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw new FetchException(code + " came back as something other than a set", -1);
            }
            return element.getAsJsonObject();
        } catch (RuntimeException notJson) {
            throw new FetchException(code + " came back as something that is not JSON", notJson);
        }
    }

    /**
     * A set code as MTGJSON names its files, or a refusal.
     * <p>What counts as one is {@link SetCode}'s to say - this value goes into a URL and into
     * a file name and arrives from a server config, and that rule is worth having exactly one
     * of. All this adds is the case MTGJSON writes them in and the refusal a caller can show.
     */
    private static String checked(String setCode) throws FetchException {
        return SetCode.upper(setCode).orElseThrow(
                () -> new FetchException("'" + setCode + "' is not a set code", -1));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
