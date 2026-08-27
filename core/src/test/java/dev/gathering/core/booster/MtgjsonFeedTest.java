package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.net.FakeHttpTransport;
import dev.gathering.core.net.FetchException;
import dev.gathering.core.net.HttpFetcher;
import dev.gathering.core.net.RateLimiter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fetching set files, caching them, and following where a pack reaches. */
class MtgjsonFeedTest {

    private static final String ONE = "11111111-1111-4111-8111-111111111111";
    private static final String TWO = "22222222-2222-4222-8222-222222222222";

    @TempDir
    Path cache;

    // Real time plus however far this test wants to have traveled. A frozen "now" would be
    // earlier than the timestamp the file system puts on a file written a moment later, and
    // every cached file would look like it came from the future.
    private long traveled = 0;

    @Test
    @DisplayName("a set is fetched once and read from the cache after that")
    void aSetIsFetchedOnceAndCached() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().reply(200, setFile("TST", "a", ONE));
        MtgjsonFeed feed = feed(transport);

        assertThat(feed.collationFor("tst").packs()).containsOnlyKeys("draft");
        assertThat(feed.collationFor("tst").packs()).containsOnlyKeys("draft");

        assertThat(transport.requestCount()).isEqualTo(1);
        assertThat(cache.resolve("TST.json")).exists();
    }

    @Test
    @DisplayName("the URL is the set code and nothing else the caller wrote")
    void theUrlIsBuiltFromTheSetCode() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().reply(200, setFile("TST", "a", ONE));
        feed(transport).collationFor("TsT");

        assertThat(transport.requests()).singleElement().satisfies(request -> {
            assertThat(request.url()).isEqualTo("https://mtgjson.com/api/v5/TST.json");
            assertThat(request.headers()).containsKey("User-Agent");
        });
    }

    @Test
    @DisplayName("anything that is not a set code never reaches the network or the disk")
    void anythingOtherThanASetCodeIsRefused() {
        FakeHttpTransport transport = new FakeHttpTransport();
        for (String notASetCode : new String[] {
                "../../etc/passwd", "TST/../X", "tst.json", "", "  ", "toolongaset",
                "T ST", "https://elsewhere.example/x"}) {
            assertThatThrownBy(() -> feed(transport).collationFor(notASetCode))
                    .as(notASetCode)
                    .isInstanceOf(FetchException.class)
                    .hasMessageContaining("is not a set code");
        }
        assertThat(transport.requestCount()).isZero();
    }

    @Test
    @DisplayName("a set nobody has a file for is an answer, not a failure")
    void aMissingSetIsAnAnswer() throws Exception {
        MtgjsonFeed feed = feed(new FakeHttpTransport().reply(404, "not found"));

        MtgjsonCollation.Reading reading = feed.collationFor("zzz");
        assertThat(reading.isEmpty()).isTrue();
        assertThat(reading.notes()).containsExactly("ZZZ is not a set MTGJSON has a file for");
    }

    @Test
    @DisplayName("a pack that reaches into another set fetches it and comes out whole")
    void aPackThatReachesElsewhereFetchesIt() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, reachingSetFile())
                .reply(200, setFile("SPG", "guest", TWO));
        MtgjsonFeed feed = feed(transport);

        MtgjsonCollation.Reading reading = feed.collationFor("tst");

        assertThat(transport.requestCount()).isEqualTo(2);
        assertThat(reading.notes()).isEmpty();
        assertThat(reading.alsoNeeds()).isEmpty();
        assertThat(reading.packs()).containsOnlyKeys("play");
        assertThat(reading.pack("play").sheets().get("guest").weights()).hasSize(1);
    }

    @Test
    @DisplayName("a companion set that will not come is a note, not a failure")
    void aCompanionThatWillNotComeIsANote() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, reachingSetFile())
                .failWith(new IOException("the network went away"));

        MtgjsonCollation.Reading reading = feed(transport, 1).collationFor("tst");

        assertThat(reading.packs()).isEmpty();
        assertThat(reading.notes()).anySatisfy(note ->
                assertThat(note).contains("SPG could not be fetched"));
    }

    @Test
    @DisplayName("a stale cached file is fetched again")
    void aStaleFileIsFetchedAgain() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, setFile("TST", "a", ONE))
                .reply(200, setFile("TST", "a", ONE));
        MtgjsonFeed feed = feed(transport);

        feed.collationFor("tst");
        traveled += MtgjsonFeed.DEFAULT_MAX_AGE_MILLIS + 1;
        feed.collationFor("tst");

        assertThat(transport.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a cached file that got corrupted is fetched again rather than failing")
    void aCorruptedCacheIsFetchedAgain() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, setFile("TST", "a", ONE));
        Files.writeString(cache.resolve("TST.json"), "{ this is not", StandardCharsets.UTF_8);

        assertThat(feed(transport).collationFor("tst").packs()).containsOnlyKeys("draft");
        assertThat(transport.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a set file that is not JSON says so and leaves nothing cached")
    void aReplyThatIsNotJsonIsRefused() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().reply(200, "<html>nope</html>");

        assertThatThrownBy(() -> feed(transport).collationFor("tst"))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("not JSON");
        // Nothing at all, not just no set file: a half-written temporary left behind is a
        // cache directory that grows every time somebody's proxy returns a login page.
        try (java.util.stream.Stream<Path> left = Files.list(cache)) {
            assertThat(left).isEmpty();
        }
    }

    // ------------------------------------------------------------------- bits

    private MtgjsonFeed feed(FakeHttpTransport transport) throws IOException {
        return feed(transport, 1);
    }

    private MtgjsonFeed feed(FakeHttpTransport transport, int attempts) throws IOException {
        HttpFetcher fetcher = new HttpFetcher(
                transport, new RateLimiter(0, this::clock, millis -> { }), attempts, 0,
                millis -> { });
        return new MtgjsonFeed(fetcher, "Gathering/test", MtgjsonFeed.DEFAULT_BASE_URL, cache,
                MtgjsonFeed.DEFAULT_MAX_AGE_MILLIS, this::clock);
    }

    private long clock() {
        return System.currentTimeMillis() + traveled;
    }

    private static String setFile(String code, String uuid, String printing) {
        return """
                {"data": {
                  "code": "%s",
                  "cards": [{"uuid": "%s", "identifiers": {"scryfallId": "%s"}}],
                  "booster": {"draft": {
                    "sourceSetCodes": ["%s"],
                    "sheets": {"common": {"foil": false, "totalWeight": 1, "cards": {"%s": 1}}},
                    "boosters": [{"contents": {"common": 1}, "weight": 1}],
                    "boostersTotalWeight": 1
                  }}
                }}
                """.formatted(code, uuid, printing, code, uuid);
    }

    /** A play booster with a slot printed in another set, which is what a real one has. */
    private static String reachingSetFile() {
        return """
                {"data": {
                  "code": "TST",
                  "cards": [{"uuid": "a", "identifiers": {"scryfallId": "%s"}}],
                  "booster": {"play": {
                    "sourceSetCodes": ["TST", "SPG"],
                    "sheets": {
                      "common": {"foil": false, "totalWeight": 1, "cards": {"a": 1}},
                      "guest":  {"foil": false, "totalWeight": 1, "cards": {"guest": 1}}
                    },
                    "boosters": [{"contents": {"common": 1, "guest": 1}, "weight": 1}],
                    "boostersTotalWeight": 1
                  }}
                }}
                """.formatted(ONE);
    }
}
