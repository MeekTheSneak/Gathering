package dev.gathering.core.deck;

import dev.gathering.core.net.HttpTransport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.decklist.DecklistEntry;
import dev.gathering.core.decklist.ParsedDecklist;
import dev.gathering.core.net.FetchException;
import dev.gathering.core.testing.Fixtures;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading a deck out of Archidekt's API, against a trimmed but real response. */
class ArchidektDeckSourceTest {

    @Test
    @DisplayName("cards arrive with their exact printing, so nothing has to be guessed")
    void everyCardCarriesItsScryfallId() {
        ParsedDecklist deck = ArchidektDeckSource.convert(Fixtures.json("archidekt_deck"));

        assertThat(deck.entries()).isNotEmpty();
        assertThat(deck.entries()).allSatisfy(entry ->
                assertThat(entry.printing()).as("printing for %s", entry.name()).isPresent());
        assertThat(deck.deckName()).contains("Fun With Fungus");
    }

    @Test
    @DisplayName("the command zone comes from Archidekt's own premier flag, not from the word Commander")
    void premierCategoriesBecomeCommanders() {
        ParsedDecklist deck = ArchidektDeckSource.convert(Fixtures.json("archidekt_deck"));

        assertThat(deck.entriesIn(DeckSection.COMMANDER))
                .singleElement()
                .satisfies(entry -> assertThat(entry.name()).isEqualTo("Thelon of Havenwood"));
    }

    @Test
    @DisplayName("a maybeboard card is not in the deck")
    void excludedCategoriesAreKeptOutOfTheDeck() {
        ParsedDecklist deck = ArchidektDeckSource.convert(Fixtures.json("archidekt_deck"));

        assertThat(deck.entriesIn(DeckSection.MAYBEBOARD)).hasSize(1);
        assertThat(deck.cardCount(DeckSection.MAINBOARD)).isEqualTo(2);
    }

    @Test
    @DisplayName("a category the owner renamed but marked as not counting is still excluded")
    void exclusionFollowsTheFlagRatherThanTheName() {
        var json = Fixtures.json("archidekt_deck");
        json.getAsJsonArray("cards").get(1).getAsJsonObject()
                .add("categories", com.google.gson.JsonParser.parseString("[\"Scrapped\"]").getAsJsonArray());

        ParsedDecklist deck = ArchidektDeckSource.convert(json);

        assertThat(deck.entriesIn(DeckSection.MAYBEBOARD)).hasSize(1);
    }

    @Test
    void finishesSurvive() {
        ParsedDecklist deck = ArchidektDeckSource.convert(Fixtures.json("archidekt_deck"));

        assertThat(deck.entries()).filteredOn(DecklistEntry::foil).hasSize(1);
    }

    @Test
    @DisplayName("Archidekt sends null rather than an empty list for an uncategorized card")
    void nullCategoriesAreAnEmptyListNotACrash() {
        // Straight from the live API: cards in no category have "categories": null. Reading
        // that as a missing field rather than as an absent list is the difference between a
        // deck importing and an import throwing on somebody's untidy list.
        ParsedDecklist deck = ArchidektDeckSource.convert(Fixtures.json("archidekt_deck"));

        assertThat(deck.entriesIn(DeckSection.MAINBOARD))
                .extracting(DecklistEntry::name)
                .contains("Spread the Sickness", "Plaguemaw Beast");
    }

    @Test
    @DisplayName("a missing or private deck says so, rather than importing nothing in silence")
    void missingDecksAreExplained() {
        ArchidektDeckSource source = new ArchidektDeckSource(
                new StubTransport(404, "{\"error\":\"Deck not found.\"}"), "test");

        assertThatThrownBy(() -> source.fetch(DeckLink.parse("https://archidekt.com/decks/1").orElseThrow()))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("does not exist, or it is private");
    }

    @Test
    void serverTroubleSuggestsTheTextExport() {
        ArchidektDeckSource source = new ArchidektDeckSource(new StubTransport(503, ""), "test");

        assertThatThrownBy(() -> source.fetch(DeckLink.parse("https://archidekt.com/decks/1").orElseThrow()))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("text export");
    }

    @Test
    @DisplayName("the request identifies the mod, as it does everywhere else")
    void requestsIdentifyThemselves() throws IOException {
        StubTransport transport = new StubTransport(200, Fixtures.json("archidekt_deck").toString());
        new ArchidektDeckSource(transport, "Gathering/test").fetch(
                DeckLink.parse("https://archidekt.com/decks/1").orElseThrow());

        assertThat(transport.urls).containsExactly("https://archidekt.com/api/decks/1/");
        assertThat(transport.lastHeaders).containsEntry("User-Agent", "Gathering/test");
    }

    @Test
    @DisplayName("a reply that is not a deck is reported rather than parsed into nonsense")
    void nonJsonRepliesAreReported() {
        ArchidektDeckSource source = new ArchidektDeckSource(new StubTransport(200, "<html>nope</html>"), "test");

        assertThatThrownBy(() -> source.fetch(DeckLink.parse("https://archidekt.com/decks/1").orElseThrow()))
                .isInstanceOf(FetchException.class);
    }

    private static final class StubTransport implements HttpTransport {
        private final int status;
        private final String body;
        private final List<String> urls = new ArrayList<>();
        private Map<String, String> lastHeaders = Map.of();

        StubTransport(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public HttpReply get(String url, Map<String, String> headers) {
            urls.add(url);
            lastHeaders = headers;
            return new HttpReply(status, body);
        }

        @Override
        public HttpReply post(String url, String requestBody, Map<String, String> headers) {
            throw new AssertionError("Archidekt is read with GET only");
        }
    }
}
