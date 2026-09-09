package dev.gathering.core.deck;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Legality;
import dev.gathering.core.net.FakeHttpTransport;
import dev.gathering.core.net.RateLimiter;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.DiskCardMetadataStore;
import dev.gathering.core.scryfall.ScryfallCardCodec;
import dev.gathering.core.scryfall.ScryfallClient;
import dev.gathering.core.testing.Fixtures;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cache in front of the network, and the one door that goes past it.
 * <p>A printing is mostly a name, a picture and a mana cost, and none of those change - which
 * is why a cached card is answered from the cache for ever and why re-importing a decklist
 * costs nothing. One part of the same record does change underneath it: what is legal where.
 * Bans and rotations happen to cards nobody has looked up since.
 * <p>An audit found the deck check warning that its verdict rested on stale data and saying
 * the cards were being looked up again, while what it called was the cache-first path - which
 * answered instantly from the stale entry and made no request at all. The next check read the
 * same stale legality, for ever.
 */
class CachingCardSourceTest {

    @Test
    @DisplayName("resolving a card already on disk makes no request, which is the point")
    void acachedCardCostsNothing(@TempDir Path cache) throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport();
        DiskCardMetadataStore store = new DiskCardMetadataStore(cache);
        CardMetadata forest = stored(store, "legal");

        var found = new CachingCardSource(store, clientOf(transport))
                .resolve(List.of(CardQuery.byId(forest.scryfallId())));

        assertThat(found.found()).hasSize(1);
        assertThat(transport.requestCount()).isZero();
    }

    @Test
    @DisplayName("refreshing the same card goes to the network, past the cache")
    void arefreshActuallyRefreshes(@TempDir Path cache) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(cache);
        CardMetadata forest = stored(store, "legal");
        assertThat(store.find(CardQuery.byId(forest.scryfallId())).orElseThrow()
                .legalityIn("modern")).isEqualTo(Legality.LEGAL);

        // Upstream has banned it since. Nothing about the cached copy can know that.
        FakeHttpTransport transport = new FakeHttpTransport();
        transport.reply(200, collectionOf(rawWith(forest, "banned")));

        var refreshed = new CachingCardSource(store, clientOf(transport))
                .refresh(List.of(CardQuery.byId(forest.scryfallId())));

        assertThat(transport.requestCount())
                .describedAs("a refresh that makes no request is not a refresh")
                .isEqualTo(1);
        assertThat(refreshed.found()).hasSize(1);
        // And the store now answers with the new legality, so the next check is current.
        assertThat(store.find(CardQuery.byId(forest.scryfallId())).orElseThrow()
                .legalityIn("modern")).isEqualTo(Legality.BANNED);
    }

    @Test
    @DisplayName("a refresh of nothing asks for nothing")
    void refreshingNothingIsNothing(@TempDir Path cache) throws IOException {
        FakeHttpTransport transport = new FakeHttpTransport();
        CachingCardSource source = new CachingCardSource(
                new DiskCardMetadataStore(cache), clientOf(transport));

        assertThat(source.refresh(List.of()).found()).isEmpty();
        assertThat(source.refresh(null).found()).isEmpty();
        assertThat(transport.requestCount()).isZero();
    }

    @Test
    @DisplayName("a stored card's age moves on when it is stored again")
    void storingACardMakesItFresh(@TempDir Path cache) throws IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(cache);
        CardMetadata forest = stored(store, "legal");

        java.time.Instant when = store.cachedAt(forest.scryfallId()).orElseThrow();

        assertThat(when).isAfter(java.time.Instant.now().minusSeconds(60));
        // And it is answerable without going back to the disk, which is what the deck check
        // asks on the game thread.
        assertThat(store.cachedAtInMemory(forest.scryfallId())).isPresent();
        assertThat(store.cachedAtInMemory(UUID.randomUUID())).isEmpty();
    }

    private static ScryfallClient clientOf(FakeHttpTransport transport) {
        return new ScryfallClient(transport, new RateLimiter(0, () -> 0L, millis -> { }), "test");
    }

    /** A Forest in the cache, legal or banned in Modern as asked. */
    private static CardMetadata stored(DiskCardMetadataStore store, String modern) {
        JsonObject json = Fixtures.json("forest");
        legality(json, modern);
        CardMetadata card = ScryfallCardCodec.parse(json).orElseThrow();
        store.store(card, json);
        return card;
    }

    /** The same printing, as upstream would send it back with a different ruling. */
    private static JsonObject rawWith(CardMetadata card, String modern) {
        JsonObject json = Fixtures.json("forest");
        json.addProperty("id", card.scryfallId().toString());
        legality(json, modern);
        return json;
    }

    private static void legality(JsonObject json, String modern) {
        JsonObject legalities = json.getAsJsonObject("legalities");
        if (legalities == null) {
            legalities = new JsonObject();
            json.add("legalities", legalities);
        }
        legalities.addProperty("modern", modern);
    }

    /** What Scryfall's /cards/collection returns, which is what the client parses. */
    private static String collectionOf(JsonObject card) {
        JsonObject body = new JsonObject();
        body.addProperty("object", "list");
        com.google.gson.JsonArray data = new com.google.gson.JsonArray();
        data.add(card);
        body.add("data", data);
        body.add("not_found", new com.google.gson.JsonArray());
        return body.toString();
    }
}
