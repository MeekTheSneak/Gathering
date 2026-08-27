package dev.gathering.core.deck;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeckLinkTest {

    @Nested
    @DisplayName("recognising links")
    class Recognising {

        @ParameterizedTest
        @ValueSource(strings = {
            "https://archidekt.com/decks/1234567",
            "https://archidekt.com/decks/1234567/",
            "https://archidekt.com/decks/1234567/my_commander_deck",
            "https://www.archidekt.com/decks/1234567",
            "http://archidekt.com/decks/1234567",
            "archidekt.com/decks/1234567",
            "https://archidekt.com/decks/1234567?tab=view",
            "  https://archidekt.com/decks/1234567  ",
        })
        void archidektLinksInAllTheShapesPeoplePasteThem(String link) {
            assertThat(DeckLink.parse(link))
                    .get()
                    .satisfies(parsed -> {
                        assertThat(parsed.provider()).isEqualTo(DeckLink.Provider.ARCHIDEKT);
                        assertThat(parsed.deckId()).isEqualTo("1234567");
                    });
        }

        @Test
        void moxfieldLinksAreRecognizedButNotFetchable() {
            DeckLink link = DeckLink.parse("https://www.moxfield.com/decks/AbCdEf123456").orElseThrow();

            assertThat(link.provider()).isEqualTo(DeckLink.Provider.MOXFIELD);
            assertThat(link.provider().isFetchable()).isFalse();
            assertThat(link.describeUnfetchable()).contains("Moxfield").contains("Export");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "1 Sol Ring",
            "Fire // Ice",
            "https://archidekt.com/",
            "https://archidekt.com/decks/",
            "https://archidekt.com/decks/notanumber",
        })
        void thingsThatAreNotDeckLinks(String input) {
            assertThat(DeckLink.parse(input)).isEmpty();
        }

        @Test
        @DisplayName("a decklist that merely mentions a link is not a link")
        void multiLineInputIsNeverALink() {
            assertThat(DeckLink.parse("https://archidekt.com/decks/1234567\n1 Sol Ring")).isEmpty();
            assertThat(DeckLink.isOnlyALink("https://archidekt.com/decks/1234567\n1 Sol Ring")).isFalse();
        }
    }

    @Nested
    @DisplayName("the allowlist")
    class TheAllowlist {

        /**
         * These are the ones that matter. A pasted link makes the *server* fetch a URL, so a
         * host check that can be talked around lets any player point the server at anything it
         * can reach - a cloud metadata endpoint, something on the host's own network - and read
         * the answer back. Every one of these must be refused.
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "https://archidekt.com.evil.example/decks/1234567",
            "https://evil.example/archidekt.com/decks/1234567",
            "https://archidekt.com@evil.example/decks/1234567",
            "https://notarchidekt.com/decks/1234567",
            "https://evil.example/decks/1234567",
            "http://169.254.169.254/latest/meta-data/",
            "http://localhost:8080/decks/1234567",
            "http://127.0.0.1/decks/1234567",
            "file:///etc/passwd",
            "https://archidekt.com.evil.example/decks/1234567/deck",
        })
        void hostsWeDoNotKnowAreNeverFetched(String hostile) {
            assertThat(DeckLink.parse(hostile))
                    .as("must not be treated as a deck link: %s", hostile)
                    .isEmpty();
        }

        @Test
        @DisplayName("the address fetched is built from the id, not from what was pasted")
        void theUrlIsRebuiltRatherThanReused() {
            DeckLink link = DeckLink.parse("https://archidekt.com/decks/1234567/whatever?a=b#c").orElseThrow();

            assertThat(link.apiUrl()).isEqualTo("https://archidekt.com/api/decks/1234567/");
        }
    }
}
