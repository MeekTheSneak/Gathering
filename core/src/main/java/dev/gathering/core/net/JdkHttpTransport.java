package dev.gathering.core.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * The real transport, on the JDK's HTTP client. No dependency, no shading, no surprises.
 * <p>Blocking by design; the executor discipline lives one layer up.
 */
public final class JdkHttpTransport implements HttpTransport {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;

    /**
     * One client for every transport that does not bring its own. A JDK client owns a selector
     * thread and a connection pool; a fresh one per service, made again for every world opened
     * in single player, left each of those behind until the collector happened to reach it.
     */
    private static final HttpClient SHARED = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public JdkHttpTransport() {
        this(SHARED);
    }

    public JdkHttpTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public HttpReply get(String url, Map<String, String> headers) throws IOException {
        return send(builder(url, headers).GET().build());
    }

    @Override
    public HttpReply post(String url, String body, Map<String, String> headers) throws IOException {
        return send(builder(url, headers).POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpRequest.Builder builder(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT);
        headers.forEach(builder::header);
        return builder;
    }

    /**
     * The most a response may be. MTGJSON's largest set files are the biggest thing this mod
     * fetches, at well under a hundred megabytes; past this a response is not an answer.
     */
    static final int MOST_BYTES = 128 << 20;

    /**
     * How long the far end asked to be left alone for, from its Retry-After header.
     * <p>Seconds, per the standard's common form; the date form is not read, because nothing this mod
     * talks to sends one and guessing at a clock skew is worse than falling back on our own doubling.
     * Bounded, so a far end that says "an hour" does not hang a card lookup for an hour.
     */
    private static long retryAfterMillis(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After")
                .map(String::trim)
                .filter(said -> said.chars().allMatch(Character::isDigit) && !said.isEmpty())
                .map(said -> {
                    try {
                        return Math.min(MOST_RETRY_AFTER_MILLIS, Long.parseLong(said) * 1000L);
                    } catch (NumberFormatException tooBig) {
                        return MOST_RETRY_AFTER_MILLIS;
                    }
                })
                .orElse(0L);
    }

    /**
     * The longest a far end may hold us up for, however long it asks for.
     * <p>Five seconds, not thirty. This holds the whole rate limiter, so every other request waits too -
     * and opening one pack is a set read a page at a time, so a generous Retry-After honoured four times
     * over turned into minutes of nothing. The owner said packs were taking for ever. Five seconds is
     * long enough to be a real pause and short enough that four of them is still a wait somebody will sit
     * through; past that we would rather try and be turned away again than sit on our hands.
     */
    private static final long MOST_RETRY_AFTER_MILLIS = 5_000L;

    private HttpReply send(HttpRequest request) throws IOException {
        try {
            // Read up to a bound rather than whole: a response with no end to it - a broken proxy,
            // or something in between - was otherwise read into memory until the server ran out.
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (java.io.InputStream in = response.body()) {
                body = in.readNBytes(MOST_BYTES + 1);
            }
            if (body.length > MOST_BYTES) {
                throw new FetchException(request.method() + " " + request.uri() + " answered with more than "
                        + (MOST_BYTES >> 20) + " MB", -1);
            }
            return new HttpReply(response.statusCode(),
                    new String(body, java.nio.charset.StandardCharsets.UTF_8),
                    retryAfterMillis(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted during " + request.method() + " " + request.uri(), e);
        }
    }
}
