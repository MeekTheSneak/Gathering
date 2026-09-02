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

    private HttpReply send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpReply(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted during " + request.method() + " " + request.uri(), e);
        }
    }
}
