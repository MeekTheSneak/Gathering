package dev.gathering.core.scryfall.bulk;

import dev.gathering.core.net.FetchException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * A body read as it arrives, for the one file too big to hold.
 * <p>Its own shape rather than {@link dev.gathering.core.net.HttpTransport}, which reads a reply
 * whole and refuses anything past a bound set for set files. The bulk card file is several times
 * that bound unpacked, and is never meant to be in memory at all.
 */
@FunctionalInterface
public interface BulkDownload {

    /**
     * The body at this address, open for reading.
     *
     * @throws IOException when the far end answers with anything but success
     */
    InputStream open(String url, Map<String, String> headers) throws IOException;

    /** The real one, on the JDK's client. Blocking; the caller keeps it off every game thread. */
    static BulkDownload jdk() {
        return Jdk.INSTANCE;
    }

    /** See {@link #jdk()}. */
    final class Jdk implements BulkDownload {

        private static final Jdk INSTANCE = new Jdk(Duration.ofSeconds(60));

        /**
         * Until the reply starts, not until it ends: a hundred megabytes on a slow line takes as
         * long as it takes, and a far end that has not answered in a minute is not going to.
         */
        private static final Duration UNTIL_IT_ANSWERS = Duration.ofSeconds(60);

        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        /**
         * And no longer than this between one piece of it and the next. The file takes as long as it
         * takes while it keeps arriving; one that stops without the connection closing used to be
         * waited on for good, and the bulk refresh has a thread to itself that nothing else could use.
         */
        private final Duration quietest;

        /** For tests, which cannot wait a minute for a reply that stopped. */
        Jdk(Duration quietest) {
            this.quietest = quietest;
        }

        @Override
        public InputStream open(String url, Map<String, String> headers) throws IOException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(UNTIL_IT_ANSWERS).GET();
            headers.forEach(builder::header);
            try {
                HttpResponse<InputStream> response =
                        client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new FetchException("GET " + url + " returned HTTP " + response.statusCode(),
                            response.statusCode());
                }
                return dev.gathering.core.net.ReadDeadline.wrap(response.body(), quietest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("Interrupted during GET " + url, e);
            }
        }
    }
}
