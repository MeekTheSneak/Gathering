package dev.gathering.core.scryfall.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.net.FakeHttpTransport;
import dev.gathering.core.net.HttpFetcher;
import dev.gathering.core.net.RateLimiter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BulkRefreshTest {

    private static final String FILE = "https://data.scryfall.io/default-cards/default-cards-1.jsonl.gz";
    private static final long HOUR = 60L * 60 * 1000;

    /** A download that hands back the fixture file and counts how often it was asked. */
    private static final class Downloads implements BulkDownload {
        final List<String> asked = new ArrayList<>();
        byte[] body = BulkFixtures.asGzippedLines(BulkFixtures.cards());

        @Override
        public java.io.InputStream open(String url, java.util.Map<String, String> headers) {
            asked.add(url);
            return new ByteArrayInputStream(body);
        }
    }

    private static final class Clock {
        long now = 100 * HOUR;
    }

    private static BulkRefresh refresh(FakeHttpTransport transport, Downloads downloads, Path dir, Clock clock) {
        HttpFetcher fetcher = new HttpFetcher(transport, new RateLimiter(0, () -> 0L, millis -> { }), 1, 0, millis -> { });
        return new BulkRefresh(fetcher, downloads, dir, "https://api.scryfall.com", "Gathering/test", () -> clock.now);
    }

    @Test
    @DisplayName("the first check downloads and builds; the same file again asks once and downloads nothing")
    void anUnchangedFileIsNotDownloadedAgain(@TempDir Path dir) throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, BulkFixtures.catalog("day-one", FILE))
                .reply(200, BulkFixtures.catalog("day-one", FILE));
        Downloads downloads = new Downloads();
        Clock clock = new Clock();
        BulkRefresh refresh = refresh(transport, downloads, dir, clock);

        assertThat(refresh.refresh(Optional.empty())).isEqualTo(BulkRefresh.Outcome.BUILT);
        assertThat(downloads.asked).containsExactly(FILE);
        assertThat(transport.requests().get(0).url()).isEqualTo("https://api.scryfall.com/bulk-data");

        clock.now += 30 * HOUR;
        try (BulkCardIndex index = BulkCardIndex.open(dir).orElseThrow()) {
            assertThat(index.updatedAt()).isEqualTo("day-one");
            assertThat(refresh.refresh(Optional.of(index))).isEqualTo(BulkRefresh.Outcome.UNCHANGED);
        }
        assertThat(transport.requestCount()).isEqualTo(2);
        assertThat(downloads.asked).as("still only the first download").hasSize(1);
        try (var left = Files.list(dir)) {
            assertThat(left.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(BulkIndexFiles.DOWNLOAD_PREFIX));
        }
    }

    @Test
    @DisplayName("a newer file is fetched at most about once a day")
    void aNewerFileWaitsForTheDay(@TempDir Path dir) throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, BulkFixtures.catalog("day-one", FILE))
                .reply(200, BulkFixtures.catalog("day-two", FILE))
                .reply(200, BulkFixtures.catalog("day-two", FILE));
        Downloads downloads = new Downloads();
        Clock clock = new Clock();
        BulkRefresh refresh = refresh(transport, downloads, dir, clock);
        refresh.refresh(Optional.empty());

        clock.now += 3 * HOUR;
        try (BulkCardIndex index = BulkCardIndex.open(dir).orElseThrow()) {
            assertThat(refresh.refresh(Optional.of(index))).isEqualTo(BulkRefresh.Outcome.TOO_SOON);
            assertThat(downloads.asked).hasSize(1);

            clock.now += 21 * HOUR;
            assertThat(refresh.refresh(Optional.of(index))).isEqualTo(BulkRefresh.Outcome.BUILT);
        }
        assertThat(downloads.asked).hasSize(2);
        try (BulkCardIndex index = BulkCardIndex.open(dir).orElseThrow()) {
            assertThat(index.updatedAt()).isEqualTo("day-two");
        }
    }

    @Test
    @DisplayName("an index that will not open is rebuilt, even when Scryfall's file has not changed")
    void aBrokenIndexIsRebuilt(@TempDir Path dir) throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, BulkFixtures.catalog("day-one", FILE))
                .reply(200, BulkFixtures.catalog("day-one", FILE));
        Downloads downloads = new Downloads();
        Clock clock = new Clock();
        BulkRefresh refresh = refresh(transport, downloads, dir, clock);
        refresh.refresh(Optional.empty());

        Path indexFile = dir.resolve(BulkCardIndex.currentName(dir).orElseThrow()).resolve(BulkIndexFiles.INDEX_FILE);
        byte[] stale = Files.readAllBytes(indexFile);
        stale[7] = 0;
        Files.write(indexFile, stale);
        assertThatThrownBy(() -> BulkCardIndex.open(dir)).isInstanceOf(IOException.class);

        // What the service does with a refusal: refresh as though there were no index at all.
        clock.now += HOUR;
        assertThat(refresh.refresh(Optional.empty())).isEqualTo(BulkRefresh.Outcome.BUILT);
        assertThat(downloads.asked).hasSize(2);
        try (BulkCardIndex index = BulkCardIndex.open(dir).orElseThrow()) {
            assertThat(index.byId(BulkFixtures.SOL_RING)).isPresent();
        }
    }

    @Test
    @DisplayName("a file on any host but Scryfall's bulk host is never fetched")
    void onlyScryfallsBulkHostIsFetched(@TempDir Path dir) {
        for (String elsewhere : List.of(
                "http://data.scryfall.io/default-cards.jsonl.gz",
                "https://data.scryfall.io.example.com/default-cards.jsonl.gz",
                "https://user@data.scryfall.io/default-cards.jsonl.gz",
                "https://data.scryfall.io:8443/default-cards.jsonl.gz",
                "https://169.254.169.254/latest/meta-data",
                "file:///etc/passwd")) {
            FakeHttpTransport transport = new FakeHttpTransport().reply(200, BulkFixtures.catalog("day-one", elsewhere));
            Downloads downloads = new Downloads();
            assertThatThrownBy(() -> refresh(transport, downloads, dir, new Clock()).refresh(Optional.empty()))
                    .as(elsewhere).isInstanceOf(IOException.class);
            assertThat(downloads.asked).as(elsewhere).isEmpty();
        }
    }

    @Test
    @DisplayName("the older download address is used when the lines address is not listed")
    void theArrayAddressIsTheFallback() {
        String body = """
                {"data":[{"type":"default_cards","updated_at":"x",
                  "download_uri":"https://data.scryfall.io/default-cards/default-cards.json"}]}
                """;
        assertThat(BulkCatalog.defaultCards(body)).map(BulkCatalog.Entry::uri)
                .contains("https://data.scryfall.io/default-cards/default-cards.json");
        assertThat(BulkCatalog.defaultCards("not json")).isEmpty();
        assertThat(BulkCatalog.defaultCards("{\"data\":[{\"type\":\"all_cards\",\"updated_at\":\"x\","
                + "\"download_uri\":\"https://data.scryfall.io/a.json\"}]}")).isEmpty();
    }

    @Test
    @DisplayName("a failed download leaves the index that was there, and no partial file")
    void aFailedDownloadKeepsWhatWasThere(@TempDir Path dir) throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, BulkFixtures.catalog("day-one", FILE))
                .reply(200, BulkFixtures.catalog("day-two", FILE));
        Downloads downloads = new Downloads();
        Clock clock = new Clock();
        BulkRefresh refresh = refresh(transport, downloads, dir, clock);
        refresh.refresh(Optional.empty());

        downloads.body = java.util.Arrays.copyOf(downloads.body, downloads.body.length / 2);
        clock.now += 30 * HOUR;
        try (BulkCardIndex index = BulkCardIndex.open(dir).orElseThrow()) {
            assertThatThrownBy(() -> refresh.refresh(Optional.of(index))).isInstanceOf(IOException.class);
        }
        try (BulkCardIndex index = BulkCardIndex.open(dir).orElseThrow()) {
            assertThat(index.updatedAt()).isEqualTo("day-one");
        }
        try (var left = Files.list(dir)) {
            assertThat(left.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(BulkIndexFiles.DOWNLOAD_PREFIX)
                            || name.startsWith(BulkIndexFiles.BUILDING_PREFIX));
        }
    }
}
