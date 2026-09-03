package dev.gathering.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The manners every outbound request is made with, when the far end is not having a good day.
 * <p>Reported from a real session: "GET /cards/search?...&page=3&q=set%3Aeoe returned HTTP
 * 429", after which a whole set was quietly left out of the archive. Three tries five hundred
 * milliseconds apart is barely longer than the burst that earned the 429 in the first place,
 * and backing off one request while the other threads carried on at full speed earned more.
 */
class HttpFetcherTest {

    private static final Map<String, String> NO_HEADERS = Map.of();

    @Test
    @DisplayName("a 429 holds the whole limiter back, not just this request")
    void beingAskedToSlowDownSlowsEverythingDown() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(429, "")
                .reply(200, "here you go");
        Held limiter = new Held();
        List<Long> slept = new ArrayList<>();

        HttpFetcher fetcher = new HttpFetcher(
                transport, limiter.limiter, 4, 500L, slept::add);
        assertThat(fetcher.get("http://x/y", NO_HEADERS, "a card").body())
                .isEqualTo("here you go");

        assertThat(limiter.heldFor)
                .describedAs("the next request through the limiter was not held off at all")
                .containsExactly(500L);
        assertThat(slept).containsExactly(500L);
    }

    @Test
    @DisplayName("the wait doubles rather than adding, so a slow far end gets real room")
    void theWaitDoubles() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(429, "").reply(429, "").reply(429, "").reply(200, "at last");
        Held limiter = new Held();
        List<Long> slept = new ArrayList<>();

        HttpFetcher fetcher = new HttpFetcher(transport, limiter.limiter, 4, 500L, slept::add);
        assertThat(fetcher.get("http://x/y", NO_HEADERS, "a card").body()).isEqualTo("at last");

        assertThat(slept).containsExactly(500L, 1_000L, 2_000L);
        assertThat(limiter.heldFor).containsExactly(500L, 1_000L, 2_000L);
    }

    @Test
    @DisplayName("a far end that never recovers is reported, not waited on for ever")
    void aFarEndThatNeverRecoversIsReported() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(429, "").reply(429, "").reply(429, "").reply(429, "");

        HttpFetcher fetcher = new HttpFetcher(
                transport, new Held().limiter, 4, 1L, millis -> { });

        assertThatThrownBy(() -> fetcher.get("http://x/y", NO_HEADERS, "a card"))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("429");
        assertThat(transport.requestCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("a 5xx is retried without telling the limiter to stand down")
    void aServerFaultIsNotARateProblem() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(503, "").reply(200, "recovered");
        Held limiter = new Held();

        HttpFetcher fetcher = new HttpFetcher(transport, limiter.limiter, 4, 1L, millis -> { });
        assertThat(fetcher.get("http://x/y", NO_HEADERS, "a card").body())
                .isEqualTo("recovered");

        assertThat(limiter.heldFor)
                .describedAs("a server having a moment is not the mod asking too often")
                .isEmpty();
    }

    /**
     * A real limiter on a stopped clock, which records what it made a caller wait.
     * <p>The real class rather than a stand-in for it, because what is being checked is that
     * a hold-off actually holds the next request off - not that a method was called.
     */
    private static final class Held implements RateLimiter.Clock, RateLimiter.Sleeper {
        private final List<Long> heldFor = new ArrayList<>();
        private final RateLimiter limiter = new RateLimiter(0, this, this);

        @Override
        public long currentTimeMillis() {
            return 0;
        }

        @Override
        public void sleep(long millis) {
            heldFor.add(millis);
        }
    }
}
