package dev.gathering.core.scryfall;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.testing.Fixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the card cache holds in memory is bounded, and letting a card go costs a disk read.
 * <p>Every card ever looked up used to stay in memory for the life of the server, so a player
 * clicking through Magic's nine hundred sets in the collection screen pinned every card of every one
 * of them. The cards are bounded now; the indexes that say which printing a name means are not,
 * because they are a few tens of bytes each and they are what turns a let-go-of card back into a
 * file to read.
 */
@DisplayName("The card cache")
class CardStoreBoundTest {

    @Test
    @DisplayName("holds a bounded number of cards, however many are stored")
    void theCardsInMemoryAreBounded(@TempDir Path root) throws Exception {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        int many = 20_000;
        for (int number = 0; number < many; number++) {
            Made made = cardNumbered(number);
            store.store(made.card(), made.raw());
        }

        assertThat(store.size())
                .as("a store handed twenty thousand cards should not be holding all of them")
                .isLessThan(many);
    }

    @Test
    @DisplayName("finds a card it has let go of, by name as well as by id")
    void aletGoOfCardIsReadBackOffTheDisk(@TempDir Path root) throws Exception {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        Made made = cardNumbered(0);
        CardMetadata first = made.card();
        store.store(first, made.raw());
        // Enough afterwards to push the first one out of memory.
        for (int number = 1; number < 20_000; number++) {
            Made next = cardNumbered(number);
            store.store(next.card(), next.raw());
        }

        assertThat(store.inMemory(first.scryfallId()))
                .as("the first card of twenty thousand should have been let go of")
                .isEmpty();
        assertThat(store.find(CardQuery.byId(first.scryfallId())).map(CardMetadata::name))
                .contains(first.name());
        assertThat(store.find(new CardQuery.ByName(first.name())).map(CardMetadata::scryfallId))
                .as("a name lookup should read the card back rather than miss")
                .contains(first.scryfallId());
        assertThat(store.find(new CardQuery.ByPrinting(first.setCode(), first.collectorNumber()))
                        .map(CardMetadata::scryfallId))
                .contains(first.scryfallId());
    }

    @Test
    @DisplayName("keeps the cheapest printing under a name even after letting cards go")
    void theCheapestPrintingSurvivesTheBound(@TempDir Path root) throws Exception {
        DiskCardMetadataStore store = new DiskCardMetadataStore(root);
        Made cheapest = priced("Sol Ring", "tst", "1", 1.50);
        Made dearest = priced("Sol Ring", "old", "2", 400.00);
        CardMetadata cheap = cheapest.card();
        store.store(cheap, cheapest.raw());
        for (int number = 100; number < 20_000; number++) {
            Made next = cardNumbered(number);
            store.store(next.card(), next.raw());
        }
        // Stored after the cheap one has been let go of: the comparison used to have nothing to
        // compare against and took whichever was stored last, which is the one that costs a house.
        store.store(dearest.card(), dearest.raw());

        assertThat(store.find(new CardQuery.ByName("Sol Ring")).map(CardMetadata::scryfallId))
                .contains(cheap.scryfallId());
    }

    /**
     * A card and the Scryfall response it came from, so the store can read it back off its own disk.
     * <p>Built by editing a real response rather than by hand: what is written to disk is the raw
     * json, and a card is only as readable as that is.
     */
    private record Made(CardMetadata card, JsonObject raw) {
    }

    private static Made cardNumbered(int number) {
        return priced("Card " + number, "tst", String.valueOf(number), 1.0);
    }

    private static Made priced(String name, String setCode, String collectorNumber, double usd) {
        JsonObject raw = Fixtures.json("sol_ring").deepCopy();
        UUID id = UUID.nameUUIDFromBytes(
                (name + setCode + collectorNumber).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        raw.addProperty("id", id.toString());
        raw.addProperty("name", name);
        raw.addProperty("set", setCode);
        raw.addProperty("collector_number", collectorNumber);
        JsonObject prices = new JsonObject();
        prices.addProperty("usd", String.valueOf(usd));
        raw.add("prices", prices);
        return new Made(ScryfallCardCodec.parse(raw).orElseThrow(), raw);
    }
}
