package dev.gathering.core.scryfall;

import dev.gathering.core.net.FetchException;
import dev.gathering.core.net.FakeHttpTransport;
import dev.gathering.core.net.HttpTransport;
import dev.gathering.core.net.RateLimiter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.testing.Fixtures;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScryfallClientTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
    private static final String USER_AGENT = "Gathering/0.0.1 (+https://example.invalid)";

    @Test
    @DisplayName("every request identifies the mod, as the API guidelines ask")
    void sendsAnIdentifyingUserAgent() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().reply(200, Fixtures.json("sol_ring").toString());

        client(transport).cardById(SOL_RING);

        var headers = transport.requests().get(0).headers();
        assertThat(headers).containsEntry("User-Agent", USER_AGENT);
        assertThat(headers).containsEntry("Accept", "application/json");
    }

    @Test
    void aMissingCardIsAnAnswerNotAFailure() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(404, "{\"object\":\"error\",\"status\":404}");

        assertThat(client(transport).cardById(SOL_RING)).isEmpty();
    }

    @Test
    @DisplayName("looking for a token asks for tokens, not for the card of the same name")
    void tokenSearchAsksForTokens() throws Exception {
        // The trap this exists to avoid: Scryfall's named endpoint prefers real cards, so
        // asking it for "Thrull" hands back the Fallen Empires creature rather than the token
        // Tevesh Szat makes. Tokens are their own layout and have to be asked for by it.
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("sol_ring"));

        client(transport).tokensNamed("Thrull");

        String url = transport.requests().get(0).url();
        assertThat(url).contains("/cards/search");
        assertThat(java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8))
                .contains("t:token")
                .contains("Thrull");
    }

    @Test
    @DisplayName("an empty token name costs no request at all")
    void anEmptyTokenSearchAsksNothing() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport();

        assertThat(client(transport).tokensNamed("   ")).isEmpty();
        assertThat(transport.requestCount()).isZero();
    }

    @Test
    @DisplayName("a token name with a quote in it cannot break out of the query")
    void tokenNamesCannotEscapeTheQuery() throws Exception {
        // The name reaches this from a text field somebody typed into, so it is not trusted to
        // stay inside its own quotes.
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("sol_ring"));

        client(transport).tokensNamed("Beast\" or t:land");

        String query = java.net.URLDecoder.decode(
                transport.requests().get(0).url(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(query).doesNotContain("\" or");
    }

    @Test
    @DisplayName("a hundred-card decklist costs two requests, not a hundred")
    void batchesAtSeventyFive() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("sol_ring"))
                .reply(200, Fixtures.collectionResponse("delver_of_secrets"));

        List<CardQuery> queries = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            queries.add(CardQuery.byName("Card Number " + i));
        }

        client(transport).resolve(queries);

        assertThat(transport.requestCount()).isEqualTo(2);
        assertThat(identifierCount(transport.requests().get(0).body())).isEqualTo(75);
        assertThat(identifierCount(transport.requests().get(1).body())).isEqualTo(25);
    }

    @Test
    void identicalQueriesAreAskedOnce() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("sol_ring"));

        CollectionResult result = client(transport).resolve(List.of(
                CardQuery.byName("Sol Ring"),
                CardQuery.byName("sol ring"),
                CardQuery.byName("Sol Ring")));

        assertThat(identifierCount(transport.requests().get(0).body())).isEqualTo(1);
        assertThat(result.found()).hasSize(1);
    }

    @Test
    @DisplayName("results are matched to queries by content, never by position")
    void matchesResultsToQueriesEvenWhenOneIsMissing() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("sol_ring", "delver_of_secrets"));

        CardQuery missing = CardQuery.byName("Not A Real Card");
        CardQuery delver = CardQuery.byName("Delver of Secrets");
        CardQuery solRing = CardQuery.byPrinting("ltc", "284");

        CollectionResult result = client(transport).resolve(List.of(missing, delver, solRing));

        assertThat(result.get(solRing)).map(CardMetadata::name).contains("Sol Ring");
        assertThat(result.get(delver)).map(CardMetadata::name).contains("Delver of Secrets // Insectile Aberration");
        assertThat(result.get(missing)).isEmpty();
        assertThat(result.notFound()).containsExactly(missing);
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    @DisplayName("either half of a double-faced card's name resolves to the whole card")
    void matchesEitherFaceOfADoubleFacedCard() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("delver_of_secrets"));

        CardQuery backFace = CardQuery.byName("Insectile Aberration");

        assertThat(client(transport).resolve(List.of(backFace)).get(backFace)).isPresent();
    }

    @Test
    void aSetHintNarrowsANameQuery() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("sol_ring"));

        CardQuery wrongSet = CardQuery.byNameInSet("Sol Ring", "cmr");

        CollectionResult result = client(transport).resolve(List.of(wrongSet));

        assertThat(result.notFound()).containsExactly(wrongSet);
    }

    @Test
    void retriesServerErrorsAndSucceeds() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(503, "")
                .reply(200, Fixtures.json("sol_ring").toString());

        assertThat(client(transport).cardById(SOL_RING)).isPresent();
        assertThat(transport.requestCount()).isEqualTo(2);
    }

    @Test
    void givesUpAfterTheAttemptBudget() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(429, "")
                .reply(429, "")
                .reply(429, "");

        assertThatThrownBy(() -> client(transport).cardById(SOL_RING))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("429");
        assertThat(transport.requestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("a client error is not retried; it will fail identically forever")
    void doesNotRetryClientErrors() {
        FakeHttpTransport transport = new FakeHttpTransport().reply(422, "");

        assertThatThrownBy(() -> client(transport).cardById(SOL_RING)).isInstanceOf(FetchException.class);
        assertThat(transport.requestCount()).isEqualTo(1);
    }

    @Test
    void networkFailuresAreRetriedThenReported() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .failWith(new IOException("connection reset"))
                .failWith(new IOException("connection reset"))
                .failWith(new IOException("connection reset"));

        assertThatThrownBy(() -> client(transport).cardById(SOL_RING)).isInstanceOf(FetchException.class);
        assertThat(transport.requestCount()).isEqualTo(3);
    }

    @Test
    void anEmptyQueryListMakesNoRequests() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport();

        assertThat(client(transport).resolve(List.of())).isEqualTo(CollectionResult.empty());
        assertThat(transport.requestCount()).isZero();
    }

    @Test
    void printingSearchesAskForEveryPrintingCheapestFirst() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("sol_ring"));

        client(transport).printingsOf("Sol Ring");

        String url = transport.requests().get(0).url();
        assertThat(url).contains("unique=prints").contains("order=usd").contains("dir=asc");
    }

    @Test
    @DisplayName("split and double-faced cards are asked for by one face, because the API insists")
    void combinedNamesAreSplitBeforeTheyGoOnTheWire() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("delver_of_secrets"));

        CardQuery query = CardQuery.byName("Delver of Secrets // Insectile Aberration");
        CollectionResult result = client(transport).resolve(List.of(query));

        JsonObject sent = JsonParser.parseString(transport.requests().get(0).body()).getAsJsonObject();
        String askedFor = sent.getAsJsonArray("identifiers").get(0).getAsJsonObject()
                .get("name").getAsString();

        // Verified against the live API: the combined name comes back not-found, either half
        // returns the whole card. Every split card in every decklist depends on this.
        assertThat(askedFor).isEqualTo("Delver of Secrets");
        assertThat(result.get(query)).isPresent();
    }

    @Test
    void aSplitNameStillTiesBackToTheQueryThatAskedForIt() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport()
                .reply(200, Fixtures.collectionResponse("delver_of_secrets"));

        CardQuery query = CardQuery.byName("Delver of Secrets // Insectile Aberration");

        assertThat(client(transport).resolve(List.of(query)).get(query))
                .map(CardMetadata::name)
                .contains("Delver of Secrets // Insectile Aberration");
    }

    @Test
    void aBodyThatIsNotJsonIsReportedClearly() {
        FakeHttpTransport transport = new FakeHttpTransport().reply(200, "<html>maintenance</html>");

        assertThatThrownBy(() -> client(transport).cardById(SOL_RING))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("not JSON");
    }

    private static ScryfallClient client(HttpTransport transport) {
        RateLimiter noWaiting = new RateLimiter(0, () -> 0L, millis -> { });
        return new ScryfallClient(
                transport, noWaiting, USER_AGENT, "https://api.example.invalid", 3, millis -> { });
    }

    private static int identifierCount(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        return json.getAsJsonArray("identifiers").size();
    }
}
