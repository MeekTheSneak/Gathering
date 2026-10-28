package dev.gathering.core.scryfall;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.testing.Fixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskCardMetadataStoreTest {

    @Test
    void storesAndFindsByEveryQueryShape(@TempDir Path root) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        CardMetadata solRing = store(store, "sol_ring");

        assertThat(store.find(CardQuery.byId(solRing.scryfallId()))).contains(solRing);
        assertThat(store.find(CardQuery.byPrinting("LTC", "284"))).contains(solRing);
        assertThat(store.find(CardQuery.byName("sol ring"))).contains(solRing);
        assertThat(store.find(CardQuery.byNameInSet("Sol Ring", "ltc"))).contains(solRing);
        assertThat(store.find(CardQuery.byNameInSet("Sol Ring", "cmr"))).isEmpty();
    }

    @Test
    @DisplayName("what Scryfall said is what lands on disk, not a re-serialization of it")
    void writesTheRawResponse(@TempDir Path root) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        JsonObject raw = Fixtures.json("sol_ring");
        CardMetadata card = ScryfallCardCodec.parse(raw).orElseThrow();
        store.store(card, raw);

        Path file = root.resolve("cards")
                .resolve(card.scryfallId().toString().substring(0, 2))
                .resolve(card.scryfallId() + ".json");

        assertThat(file).exists();
        JsonObject written = com.google.gson.JsonParser
                .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertThat(written).isEqualTo(raw);
    }

    @Test
    @DisplayName("a restart finds cards by id without reading the whole cache")
    void survivesRestartForIdLookups(@TempDir Path root) throws IOException {
        CardMetadata solRing = store(new DiskCardMetadataStore(root), "sol_ring");

        DiskCardMetadataStore reopened = new DiskCardMetadataStore(root);

        assertThat(reopened.size()).isZero();
        assertThat(reopened.find(CardQuery.byId(solRing.scryfallId()))).isPresent();
        // Reading it in also indexed it, so the other lookups now work too.
        assertThat(reopened.find(CardQuery.byName("Sol Ring"))).isPresent();
    }

    @Test
    @DisplayName("a double-faced card comes back off disk with both faces intact")
    void bothFacesSurviveARestart(@TempDir Path root) throws IOException {
        // A restart throws away everything a client was told, so what it is told again comes
        // straight off this disk. A card whose back face did not survive the round trip would
        // come back single-faced - a transform card that will not turn over, and a split card
        // missing half its rules.
        CardMetadata delver = store(new DiskCardMetadataStore(root), "delver_of_secrets");
        CardMetadata fireIce = store(new DiskCardMetadataStore(root), "fire_ice");

        DiskCardMetadataStore reopened = new DiskCardMetadataStore(root);

        CardMetadata restoredDelver = reopened.find(CardQuery.byId(delver.scryfallId())).orElseThrow();
        assertThat(restoredDelver.faces()).hasSize(2);
        assertThat(restoredDelver.faces()).isEqualTo(delver.faces());
        assertThat(restoredDelver.faces().get(1).oracleText()).isNotBlank();
        assertThat(restoredDelver.faces().get(1).imageUris().normal()).isNotBlank();

        CardMetadata restoredFireIce = reopened.find(CardQuery.byId(fireIce.scryfallId())).orElseThrow();
        assertThat(restoredFireIce.faces()).hasSize(2);
        assertThat(restoredFireIce.faces()).isEqualTo(fireIce.faces());
        // Still one picture on the front and none on the back, exactly as it went in.
        assertThat(restoredFireIce.faces().get(0).hasImages()).isTrue();
        assertThat(restoredFireIce.faces().get(1).hasImages()).isFalse();
    }

    @Test
    void loadIndexMakesNameAndPrintingLookupsWorkAfterRestart(@TempDir Path root) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        store(store, "sol_ring");
        store(store, "delver_of_secrets");

        DiskCardMetadataStore reopened = new DiskCardMetadataStore(root);
        int loaded = reopened.loadIndex();

        assertThat(loaded).isEqualTo(2);
        assertThat(reopened.find(CardQuery.byName("Sol Ring"))).isPresent();
        assertThat(reopened.find(CardQuery.byPrinting("isd", "51"))).isPresent();
        assertThat(reopened.find(CardQuery.byName("Insectile Aberration"))).isPresent();
    }

    @Test
    @DisplayName("a corrupt cache entry is a miss, never a failed import")
    void corruptEntriesAreMisses(@TempDir Path root) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        CardMetadata solRing = store(store, "sol_ring");

        Path file = root.resolve("cards")
                .resolve(solRing.scryfallId().toString().substring(0, 2))
                .resolve(solRing.scryfallId() + ".json");
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);

        DiskCardMetadataStore reopened = new DiskCardMetadataStore(root);

        assertThat(reopened.find(CardQuery.byId(solRing.scryfallId()))).isEmpty();
        assertThat(reopened.loadIndex()).isZero();
    }

    @Test
    @DisplayName("the name index keeps the cheapest printing, not whichever arrived first")
    void nameIndexPrefersTheCheapestPrinting(@TempDir Path root) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);

        JsonObject expensive = Fixtures.json("sol_ring");
        expensive.addProperty("id", "00000000-0000-4000-8000-000000000001");
        expensive.getAsJsonObject("prices").addProperty("usd", "500.00");
        JsonObject cheap = Fixtures.json("sol_ring");
        cheap.addProperty("id", "00000000-0000-4000-8000-000000000002");
        cheap.getAsJsonObject("prices").addProperty("usd", "0.50");

        for (JsonObject raw : List.of(expensive, cheap)) {
            store.store(ScryfallCardCodec.parse(raw).orElseThrow(), raw);
        }

        assertThat(store.find(CardQuery.byName("Sol Ring")))
                .get()
                .extracting(card -> card.scryfallId().toString())
                .isEqualTo("00000000-0000-4000-8000-000000000002");
    }

    @Test
    void partitionSplitsHitsFromMisses(@TempDir Path root) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        store(store, "sol_ring");

        CardMetadataStore.Partition partition = store.partition(List.of(
                CardQuery.byName("Sol Ring"), CardQuery.byName("Black Lotus")));

        assertThat(partition.hits()).hasSize(1);
        assertThat(partition.misses()).hasSize(1);
        assertThat(partition.fullyCached()).isFalse();
    }

    private static CardMetadata store(DiskCardMetadataStore store, String fixture) {
        JsonObject raw = Fixtures.json(fixture);
        CardMetadata card = ScryfallCardCodec.parse(raw).orElseThrow();
        store.store(card, raw);
        return card;
    }
}
