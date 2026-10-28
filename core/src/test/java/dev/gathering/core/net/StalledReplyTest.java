package dev.gathering.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A reply that stops halfway gives up, and is asked again.
 * <p>The JDK client's request timeout lasts until the headers arrive. Read through its body, a reply
 * that went quiet without closing was waited on for good - and every download in the mod runs on a
 * thread that is the only one of its kind, so one stalled set file held up every pack opening, deck
 * import and shop restock until the server was restarted. Without the body's own deadline, each of
 * these waits for {@link #LONGEST} and is failed by the timer rather than by the transport.
 */
@DisplayName("A reply that stops halfway")
class StalledReplyTest {

    /** Far longer than the one second each of these is given before it gives up. */
    private static final Duration LONGEST = Duration.ofSeconds(15);

    private static HttpClient client() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static JdkHttpTransport quickToGiveUp() {
        return new JdkHttpTransport(client(), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("gives up once nothing has come for the deadline, rather than waiting for ever")
    void aReplyThatGoesQuietHalfwayGivesUp() throws IOException {
        try (StallingServer far = new StallingServer(StallingServer.Reply.STALL)) {
            assertTimeoutPreemptively(LONGEST, () ->
                    assertThatThrownBy(() -> quickToGiveUp().get(far.url(), Map.of()))
                            .isInstanceOf(IOException.class)
                            .hasMessageContaining("stopped arriving"));
        }
    }

    @Test
    @DisplayName("gives up on a gzipped reply whose header never comes")
    void aGzippedReplyWhoseHeaderNeverComesGivesUp() throws IOException {
        try (StallingServer far = new StallingServer(StallingServer.Reply.STALL_BEFORE_THE_GZIP_HEADER)) {
            assertTimeoutPreemptively(LONGEST, () ->
                    assertThatThrownBy(() -> quickToGiveUp().get(far.url(), Map.of()))
                            .isInstanceOf(IOException.class)
                            .hasMessageContaining("stopped arriving"));
        }
    }

    /** The point of giving up: the fetcher's retry runs, and the second asking is answered. */
    @Test
    @DisplayName("is asked again by the fetcher, and the second asking is answered")
    void aStalledReplyIsAskedAgain() throws IOException {
        try (StallingServer far = new StallingServer(StallingServer.Reply.STALL)) {
            HttpFetcher fetcher = new HttpFetcher(quickToGiveUp(),
                    new RateLimiter(0L, System::currentTimeMillis, ignored -> { }), 2, 0L, ignored -> { });

            String answered = assertTimeoutPreemptively(LONGEST,
                    () -> fetcher.get(far.url(), Map.of(), "a test reply").body());

            assertThat(answered).isEqualTo("ok");
            assertThat(far.connections()).isEqualTo(2);
        }
    }
}
