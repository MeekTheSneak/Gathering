package dev.gathering.client;

import dev.gathering.core.net.ArtHosts;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A GET that follows redirects only to addresses {@link ArtHosts} allows.
 * <p>The JDK client can follow redirects itself, but it follows them anywhere: an allowed first
 * address that answered "look over there" would have this client make its next request to
 * whatever "there" was - an address on the player's own network included - before anything
 * here could look at it. So redirects are followed by hand, and each hop is checked before it
 * is requested.
 * <p>Client-only; the fetchers call it off the render thread.
 */
final class AllowedFetch {

    /** More hops than a CDN takes to find a file. */
    private static final int MOST_REDIRECTS = 4;

    /** A client that never follows a redirect on its own. */
    static HttpClient client(Duration timeout) {
        return HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private AllowedFetch() {
    }

    /**
     * The response to a GET of this address, after any allowed redirects; its body is the
     * caller's to close.
     *
     * @throws IOException also when a redirect points somewhere not allowed, or there are too many
     */
    static HttpResponse<InputStream> get(HttpClient http, String url, String userAgent, String accept, Duration timeout)
            throws IOException, InterruptedException {
        URI at = URI.create(url);
        for (int hop = 0; ; hop++) {
            if (!ArtHosts.isAllowed(at.toString())) {
                throw new IOException("not an allowed address: " + at.getHost());
            }
            HttpRequest request = HttpRequest.newBuilder(at).timeout(timeout)
                    .header("User-Agent", userAgent).header("Accept", accept).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            boolean redirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
            if (!redirect) {
                return response;
            }
            response.body().close();
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null || hop >= MOST_REDIRECTS) {
                throw new IOException("a redirect with nowhere to go, or too many of them");
            }
            at = at.resolve(location);
        }
    }
}
