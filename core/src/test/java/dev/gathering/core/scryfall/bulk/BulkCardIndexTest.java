package dev.gathering.core.scryfall.bulk;

import static dev.gathering.core.scryfall.bulk.BulkFixtures.CHEAP_SOL_RING;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.DELVER;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.FIRE_ICE;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.FOIL_ONLY_SOL_RING;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.GOLD_BORDER_SOL_RING;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.JAPANESE_SOL_RING;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.OLD_THRULL_TOKEN;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.SOL_RING;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.THRULL_CREATURE;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.THRULL_TOKEN;
import static dev.gathering.core.scryfall.bulk.BulkFixtures.UNPRICED_SOL_RING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.DiskCardMetadataStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BulkCardIndexTest {

    private static final String STAMP = "2026-09-17T09:05:41.707+00:00";

    private static BulkCardIndex built(Path dir, byte[] file) throws IOException {
        BulkIndexBuilder.build(dir, STAMP, 1_000L, new ByteArrayInputStream(file));
        return BulkCardIndex.open(dir).orElseThrow();
    }

    private static List<UUID> ids(List<CardMetadata> cards) {
        return cards.stream().map(CardMetadata::scryfallId).toList();
    }

    @Test
    @DisplayName("a built index answers by id, by printing, by set, and says when it has never heard of a set")
    void answersByIdPrintingAndSet(@TempDir Path dir) throws IOException {
        try (BulkCardIndex index = built(dir, BulkFixtures.asArray(BulkFixtures.cards()))) {
            assertThat(index.size()).isEqualTo(BulkFixtures.cards().size());
            assertThat(index.updatedAt()).isEqualTo(STAMP);
            assertThat(index.asOf()).isEqualTo(Instant.parse("2026-09-17T09:05:41.707Z"));

            assertThat(index.byId(SOL_RING)).map(CardMetadata::name).contains("Sol Ring");
            assertThat(index.contains(DELVER)).isTrue();
            assertThat(index.contains(UUID.randomUUID())).isFalse();
            assertThat(index.byId(UUID.randomUUID())).isEmpty();

            assertThat(index.byPrinting("ISD", "51")).map(CardMetadata::scryfallId).contains(DELVER);
            assertThat(index.find(CardQuery.byPrinting("dmr", "215"))).map(CardMetadata::scryfallId).contains(FIRE_ICE);
            assertThat(index.byPrinting("isd", "52")).isEmpty();

            assertThat(index.printingsIn("c21")).map(BulkCardIndexTest::ids)
                    .contains(List.of(CHEAP_SOL_RING, JAPANESE_SOL_RING));
            assertThat(index.printingsIn("30a")).map(BulkCardIndexTest::ids).contains(List.of(GOLD_BORDER_SOL_RING));
            assertThat(index.printingsIn("zzz")).as("a set newer than the file goes to Scryfall").isEmpty();
        }
    }

    @Test
    @DisplayName("a name finds the cheapest printing a search would list, not a foreign copy or a gold-bordered one")
    void aNameFindsTheCheapestListedPrinting(@TempDir Path dir) throws IOException {
        try (BulkCardIndex index = built(dir, BulkFixtures.asArray(BulkFixtures.cards()))) {
            // Scryfall's price order: nothing priced first, a foil-only printing at its foil price. The
            // Japanese copy of the same picture and the memorabilia reprint are both cheaper, and
            // neither is what a line saying "Sol Ring" means.
            assertThat(ids(index.printingsOf("Sol Ring")))
                    .containsExactly(UNPRICED_SOL_RING, CHEAP_SOL_RING, FOIL_ONLY_SOL_RING, SOL_RING);
            // A name is the cheapest printing that has a price, not the unpriced one heading the list.
            assertThat(index.find(CardQuery.byName("sol ring"))).map(CardMetadata::scryfallId).contains(CHEAP_SOL_RING);
            assertThat(index.find(CardQuery.byNameInSet("Sol Ring", "LTC"))).map(CardMetadata::scryfallId)
                    .contains(SOL_RING);
            // Still there by id: a card somebody already holds is a card.
            assertThat(index.byId(GOLD_BORDER_SOL_RING)).isPresent();
        }
    }

    @Test
    @DisplayName("collector numbers sort as a set lists them")
    void collectorNumbersSortAsASetListsThem() {
        List<String> numbers = new java.util.ArrayList<>(List.of("10a", "★", "A-9", "10", "9", "100"));
        numbers.sort(BulkIndexFiles.COLLECTOR_ORDER);
        assertThat(numbers).containsExactly("9", "A-9", "10", "10a", "100", "★");
    }

    @Test
    @DisplayName("split and double-faced cards are found by the combined name and by either half")
    void combinedNamesAndHalves(@TempDir Path dir) throws IOException {
        try (BulkCardIndex index = built(dir, BulkFixtures.asArray(BulkFixtures.cards()))) {
            for (String name : List.of("Fire // Ice", "Fire", "ice")) {
                assertThat(index.find(CardQuery.byName(name))).as(name).map(CardMetadata::scryfallId).contains(FIRE_ICE);
            }
            assertThat(index.find(CardQuery.byName("Delver of Secrets // Insectile Aberration")))
                    .map(CardMetadata::scryfallId).contains(DELVER);
            assertThat(ids(index.printingsOf("Insectile Aberration"))).containsExactly(DELVER);
            assertThat(index.find(CardQuery.byName("No Such Card"))).isEmpty();
        }
    }

    @Test
    @DisplayName("a token is found as a token, the card of the same name as a card, one printing per token")
    void tokensAreTheirOwnLookup(@TempDir Path dir) throws IOException {
        try (BulkCardIndex index = built(dir, BulkFixtures.asArray(BulkFixtures.cards()))) {
            assertThat(index.find(CardQuery.byName("Thrull"))).map(CardMetadata::scryfallId).contains(THRULL_CREATURE);
            // Two printings of one token, answered once, by the newer.
            assertThat(ids(index.tokensNamed("Thrull"))).containsExactly(THRULL_TOKEN);
            assertThat(ids(index.tokensNamed("thr"))).as("loosely, when nothing has exactly the name")
                    .containsExactly(THRULL_TOKEN);
            assertThat(index.tokensNamed("Sol Ring")).isEmpty();
            assertThat(index.tokensNamed("  ")).isEmpty();
            assertThat(index.byId(OLD_THRULL_TOKEN)).isPresent();
        }
    }

    @Test
    @DisplayName("gzipped JSON lines build the same index as a JSON array")
    void eitherShapeOfTheFile(@TempDir Path array, @TempDir Path lines) throws IOException {
        try (BulkCardIndex fromArray = built(array, BulkFixtures.asArray(BulkFixtures.cards()));
                BulkCardIndex fromLines = built(lines, BulkFixtures.asGzippedLines(BulkFixtures.cards()))) {
            assertThat(fromLines.size()).isEqualTo(fromArray.size());
            assertThat(ids(fromLines.printingsOf("Sol Ring"))).isEqualTo(ids(fromArray.printingsOf("Sol Ring")));
            assertThat(fromLines.byId(FIRE_ICE)).isEqualTo(fromArray.byId(FIRE_ICE));
        }
    }

    @Test
    @DisplayName("a build that fails partway leaves the index that was current exactly as it was")
    void aFailedBuildKeepsTheOldIndex(@TempDir Path dir) throws IOException {
        List<JsonObject> first = BulkFixtures.cards().subList(0, 3);
        BulkIndexBuilder.build(dir, "first", 1_000L, new ByteArrayInputStream(BulkFixtures.asArray(first)));

        byte[] second = BulkFixtures.asArray(BulkFixtures.cards());
        InputStream dropsHalfway = new InputStream() {
            private int read;

            @Override
            public int read() throws IOException {
                if (read >= second.length / 2) {
                    throw new IOException("connection reset");
                }
                return second[read++];
            }
        };
        assertThatThrownBy(() -> BulkIndexBuilder.build(dir, "second", 2_000L, dropsHalfway))
                .isInstanceOf(IOException.class);

        try (BulkCardIndex index = BulkCardIndex.open(dir).orElseThrow()) {
            assertThat(index.updatedAt()).isEqualTo("first");
            assertThat(index.size()).isEqualTo(3);
            assertThat(index.byId(SOL_RING)).isPresent();
        }
        try (var left = Files.list(dir)) {
            assertThat(left.map(path -> path.getFileName().toString()))
                    .as("nothing half-built is left behind")
                    .noneMatch(name -> name.startsWith(BulkIndexFiles.BUILDING_PREFIX));
        }
    }

    @Test
    @DisplayName("a damaged index, one cut short, or another version's is refused rather than misread")
    void untrustworthyIndexesAreRefused(@TempDir Path dir) throws IOException {
        built(dir, BulkFixtures.asArray(BulkFixtures.cards())).close();
        Path indexFile = dir.resolve(BulkCardIndex.currentName(dir).orElseThrow()).resolve(BulkIndexFiles.INDEX_FILE);
        byte[] good = Files.readAllBytes(indexFile);

        byte[] flipped = good.clone();
        flipped[flipped.length / 2] ^= 0x10;
        Files.write(indexFile, flipped);
        assertThatThrownBy(() -> BulkCardIndex.open(dir)).isInstanceOf(IOException.class);

        Files.write(indexFile, java.util.Arrays.copyOf(good, good.length - 7));
        assertThatThrownBy(() -> BulkCardIndex.open(dir)).isInstanceOf(IOException.class);

        byte[] otherVersion = good.clone();
        otherVersion[7] = (byte) (BulkIndexFiles.VERSION + 1);
        Files.write(indexFile, otherVersion);
        assertThatThrownBy(() -> BulkCardIndex.open(dir)).isInstanceOf(IOException.class)
                .hasMessageContaining("version");

        Files.write(indexFile, good);
        try (RandomAccessFile data = new RandomAccessFile(
                indexFile.resolveSibling(BulkIndexFiles.DATA_FILE).toFile(), "rw")) {
            data.setLength(data.length() - 1);
        }
        assertThatThrownBy(() -> BulkCardIndex.open(dir)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("a pointer naming somewhere outside the bulk directory opens nothing")
    void thePointerCannotLeaveTheDirectory(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(BulkIndexFiles.CURRENT), "../elsewhere", StandardCharsets.UTF_8);
        assertThat(BulkCardIndex.open(dir)).isEmpty();
    }

    @Test
    @DisplayName("a card the index answers is kept in memory for the game thread, as current as the file")
    void aHitIsRemembered(@TempDir Path dir, @TempDir Path cache) throws IOException {
        try (BulkCardIndex index = built(dir, BulkFixtures.asArray(BulkFixtures.cards()))) {
            DiskCardMetadataStore disk = new DiskCardMetadataStore(cache);
            BulkFirstStore store = new BulkFirstStore(disk, () -> Optional.of(index));

            assertThat(disk.inMemory(DELVER)).isEmpty();
            assertThat(store.find(CardQuery.byId(DELVER))).isPresent();
            assertThat(disk.inMemory(DELVER)).isPresent();
            assertThat(disk.cachedAtInMemory(DELVER)).contains(index.asOf());
            assertThat(disk.isOnDisk(DELVER)).as("not written out a second time").isFalse();

            // What the index does not have is still asked of the cache.
            CardMetadata stranger = dev.gathering.core.testing.Fixtures.card("persistent_petitioners");
            disk.store(stranger, dev.gathering.core.testing.Fixtures.json("persistent_petitioners"));
            assertThat(store.find(CardQuery.byName("Persistent Petitioners"))).contains(stranger);

            BulkFirstStore notReady = new BulkFirstStore(new DiskCardMetadataStore(cache.resolve("other")), Optional::empty);
            assertThat(notReady.find(CardQuery.byId(FIRE_ICE))).isEmpty();
        }
    }

    @Test
    @DisplayName("the file is read one card at a time, never whole")
    void readingStreams() throws IOException {
        // Twenty thousand cards, generated as they are read. When each card arrives, the reader must
        // not have consumed much past it: a reader that parsed the whole document first would have
        // read every byte before handing on the first card.
        int count = 20_000;
        CountingCards source = new CountingCards(count);
        long[] handedOn = {0};
        long most = BulkCardReader.read(source, card -> {
            long index = card.get("n").getAsLong();
            assertThat(index).isEqualTo(handedOn[0]);
            long endOfThisCard = (index + 1) * CountingCards.LENGTH + 1;
            assertThat(source.consumed - endOfThisCard).as("bytes read ahead of card %d", index)
                    .isLessThan(1L << 18);
            handedOn[0]++;
        });
        assertThat(most).isEqualTo(count);
        assertThat(source.consumed).isGreaterThan(20L * CountingCards.LENGTH * 900);
    }

    /** A JSON array of small cards, made as it is read, counting what has been read. */
    private static final class CountingCards extends InputStream {
        static final int LENGTH = 64;
        final int count;
        long consumed;
        private byte[] chunk = "[".getBytes(StandardCharsets.UTF_8);
        private int at;
        private int next;

        CountingCards(int count) {
            this.count = count;
        }

        @Override
        public int read() {
            if (at >= chunk.length) {
                if (next > count) {
                    return -1;
                }
                chunk = next == count ? "]".getBytes(StandardCharsets.UTF_8) : card(next);
                next++;
                at = 0;
            }
            consumed++;
            return chunk[at++] & 0xff;
        }

        private static byte[] card(int n) {
            String body = (n == 0 ? "" : ",") + "{\"n\":" + n + ",\"id\":\"" + new UUID(0, n) + "\"";
            StringBuilder padded = new StringBuilder(body).append(",\"p\":\"");
            while (padded.length() < LENGTH - 2) {
                padded.append('x');
            }
            return padded.append("\"}").toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
