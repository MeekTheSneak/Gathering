package dev.gathering.core.scryfall.bulk;

import dev.gathering.core.net.FetchException;
import dev.gathering.core.net.HttpFetcher;
import dev.gathering.core.net.HttpTransport;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Keeps the local card index in step with Scryfall's bulk file, and no more often than that needs.
 * <p>One request to the API to ask which edition of the file is out - through the same fetcher and
 * its manners as every other request - and a download from Scryfall's file host only when the
 * edition is not the one already built, and not more than about once a day. Scryfall publishes the
 * file daily, so a server restarted three times in an afternoon asks three times and downloads
 * nothing.
 * <p>Downloaded to a file first and built from that, rather than built straight off the network:
 * a connection that drops a minute in leaves a partial file to throw away, not a half-read stream
 * the builder has to be told about.
 * <p>Blocking. Pure core with its network and clock handed in; the adapter keeps it on a thread
 * of its own.
 */
public final class BulkRefresh {

    /**
     * The least time between two downloads.
     * <p>Twenty hours rather than twenty-four so that a daily check a little early, or a file
     * Scryfall published a little late, still means one download a day and not one every two.
     */
    public static final long LEAST_BETWEEN_DOWNLOADS_MILLIS = 20L * 60 * 60 * 1000;

    /** Past this, what is arriving is not the card file. It is about a hundred megabytes packed. */
    static final long MOST_DOWNLOAD_BYTES = 2L << 30;

    private final HttpFetcher fetcher;
    private final BulkDownload download;
    private final Path bulkDir;
    private final String catalogUrl;
    private final Map<String, String> headers;
    private final LongSupplier clock;

    /**
     * @param baseUrl Scryfall's API, as {@link dev.gathering.core.scryfall.ScryfallClient} is given it
     */
    public BulkRefresh(HttpFetcher fetcher, BulkDownload download, Path bulkDir, String baseUrl, String userAgent,
            LongSupplier clock) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.download = Objects.requireNonNull(download, "download");
        this.bulkDir = Objects.requireNonNull(bulkDir, "bulkDir");
        String base = Objects.requireNonNull(baseUrl, "baseUrl");
        this.catalogUrl = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/bulk-data";
        this.headers = Map.of(
                "User-Agent", Objects.requireNonNull(userAgent, "userAgent"),
                "Accept", "application/json");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** What a refresh did. */
    public enum Outcome {
        /** The file out is the one already built. */
        UNCHANGED,
        /** A newer file is out, and the last download was too recent to fetch it yet. */
        TOO_SOON,
        /** A new index was built and is now current. */
        BUILT
    }

    /**
     * Brings the index up to date if it needs it.
     *
     * @param current the index in use, or empty when there is none or it could not be opened
     * @throws IOException when Scryfall could not be asked, the file could not be fetched, or it
     *                     would not build. Whatever index was current still is.
     */
    public Outcome refresh(Optional<BulkCardIndex> current) throws IOException {
        HttpTransport.HttpReply reply = fetcher.get(catalogUrl, headers, "GET /bulk-data");
        if (!reply.isSuccess()) {
            throw new FetchException("GET /bulk-data returned HTTP " + reply.status(), reply.status());
        }
        BulkCatalog.Entry file = BulkCatalog.defaultCards(reply.body())
                .orElseThrow(() -> new FetchException("Scryfall's bulk data list names no card file this will fetch", -1));
        if (current.isPresent()) {
            if (current.get().updatedAt().equals(file.updatedAt())) {
                return Outcome.UNCHANGED;
            }
            long since = clock.getAsLong() - current.get().builtAt();
            if (since >= 0 && since < LEAST_BETWEEN_DOWNLOADS_MILLIS) {
                return Outcome.TOO_SOON;
            }
        }
        Files.createDirectories(bulkDir);
        Path downloaded = Files.createTempFile(bulkDir, BulkIndexFiles.DOWNLOAD_PREFIX, ".part");
        try {
            try (InputStream in = download.open(file.uri(), Map.of(
                            "User-Agent", headers.get("User-Agent"),
                            "Accept", "*/*",
                            "Accept-Encoding", "gzip"));
                    OutputStream out = Files.newOutputStream(downloaded)) {
                copyBounded(in, out);
            }
            try (InputStream in = new BufferedInputStream(Files.newInputStream(downloaded), 1 << 16)) {
                BulkIndexBuilder.build(bulkDir, file.updatedAt(), clock.getAsLong(), in);
            }
        } finally {
            Files.deleteIfExists(downloaded);
        }
        BulkIndexBuilder.sweep(bulkDir);
        return Outcome.BUILT;
    }

    private static void copyBounded(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1 << 16];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > MOST_DOWNLOAD_BYTES) {
                throw new FetchException("The bulk card file is larger than any real one could be", -1);
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException("Stopped while downloading the bulk card file");
            }
            out.write(buffer, 0, read);
        }
    }
}
