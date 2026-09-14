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

    public JdkHttpTransport() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
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
            return new HttpReply(response.statusCode(), new String(body, java.nio.charset.StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted during " + request.method() + " " + request.uri(), e);
        }
    }
}
