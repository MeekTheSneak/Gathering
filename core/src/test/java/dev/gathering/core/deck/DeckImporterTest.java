package dev.gathering.core.deck;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.CollectionResult;
import dev.gathering.core.scryfall.ScryfallCardCodec;
import dev.gathering.core.testing.Fixtures;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Phase 0 deliverable, tested end to end: paste a list, get a deck.
 */
class DeckImporterTest {

    @Test
    @DisplayName("a Moxfield-shaped Commander list becomes a deck")
    void importsTheReferenceList() throws IOException {
        FakeCardSource source = new FakeCardSource()
                .with("halana_and_alena")
                .with("tevesh_szat")
                .with("sol_ring")
                .with("persistent_petitioners");

        ResolvedDeck deck = new DeckImporter(source).importText("""
                About
                Name Halana and Tevesh

                Commander
                1 Halana and Alena, Partners (VOW) 239
                1 Tevesh Szat, Doom of Fools (CMR) 153

                Deck
                1 Sol Ring (LTC) 284 *F*
                4 Persistent Petitioners
                """);

        assertThat(deck.isComplete()).isTrue();
        assertThat(deck.deckName()).contains("Halana and Tevesh");
        assertThat(deck.cardCount(DeckSection.COMMANDER)).isEqualTo(2);
        assertThat(deck.cardCount(DeckSection.MAINBOARD)).isEqualTo(5);
        assertThat(deck.totalCards()).isEqualTo(7);
    }

    @Test
    void foilFromTheDecklistLandsOnTheCardIdentity() throws IOException {
        FakeCardSource source = new FakeCardSource().with("sol_ring");

        ResolvedDeck deck = new DeckImporter(source).importText("1 Sol Ring (LTC) 284 *F*");

        assertThat(deck.cards()).singleElement().satisfies(card -> {
            assertThat(card.identity().foil()).isTrue();
            assertThat(card.identity().printing())
                    .contains(UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba"));
        });
    }

    @Test
    @DisplayName("a line naming a printing is taken at its word; a line naming a card is not")
    void marksOnlyTheLinesWeChoseAPrintingFor() throws IOException {
        FakeCardSource source = new FakeCardSource().with("sol_ring");

        ResolvedDeck deck = new DeckImporter(source).importText("""
                1 Sol Ring (LTC) 284
                1 Sol Ring
                """);

        assertThat(deck.cards()).hasSize(2);
        assertThat(deck.cards().get(0).printingChosenAutomatically()).isFalse();
        assertThat(deck.cards().get(1).printingChosenAutomatically()).isTrue();
        assertThat(deck.automaticallyChosenPrintings()).hasSize(1);
    }

    @Test
    @DisplayName("a stale set code costs a different printing, not a missing card")
    void fallsBackToNameWhenThePrintingHintIsWrong() throws IOException {
        FakeCardSource source = new FakeCardSource().with("sol_ring");

        ResolvedDeck deck = new DeckImporter(source).importText("1 Sol Ring (ZZZ) 999");

        assertThat(deck.unresolved()).isEmpty();
        assertThat(deck.cards()).singleElement().satisfies(card -> {
            assertThat(card.name()).isEqualTo("Sol Ring");
            assertThat(card.printingChosenAutomatically()).isTrue();
        });
        assertThat(source.batches()).hasSize(2);
    }

    @Test
    @DisplayName("one unresolvable line does not cost the other ninety-nine")
    void reportsUnresolvedLinesWithoutLosingTheRest() throws IOException {
        FakeCardSource source = new FakeCardSource().with("sol_ring");

        ResolvedDeck deck = new DeckImporter(source).importText("""
                1 Sol Ring (LTC) 284
                1 Sol Rong
                """);

        assertThat(deck.cards()).hasSize(1);
        assertThat(deck.isComplete()).isFalse();
        assertThat(deck.unresolved()).singleElement().satisfies(miss -> {
            assertThat(miss.entry().lineNumber()).isEqualTo(2);
            assertThat(miss.reason()).contains("Sol Rong");
        });
    }

    @Test
    void parseProblemsSurviveIntoTheImportResult() throws IOException {
        FakeCardSource source = new FakeCardSource().with("sol_ring");

        ResolvedDeck deck = new DeckImporter(source).importText("""
                1 Sol Ring (LTC) 284
                0 Arcane Signet
                """);

        assertThat(deck.cards()).hasSize(1);
        assertThat(deck.problems()).hasSize(1);
        assertThat(deck.isComplete()).isFalse();
    }

    @Test
    @DisplayName("resolution is batched: a whole list is one round trip when nothing is missing")
    void asksOnceForTheWholeList() throws IOException {
        FakeCardSource source = new FakeCardSource().with("sol_ring").with("persistent_petitioners");

        new DeckImporter(source).importText("""
                1 Sol Ring (LTC) 284
                4 Persistent Petitioners
                """);

        assertThat(source.batches()).hasSize(1);
        assertThat(source.batches().get(0)).hasSize(2);
    }

    @Test
    void anEmptyListImportsToAnEmptyDeckRatherThanFailing() throws IOException {
        ResolvedDeck deck = new DeckImporter(new FakeCardSource()).importText("");

        assertThat(deck.cards()).isEmpty();
        assertThat(deck.isComplete()).isTrue();
    }

    @Test
    void theChooserCanSwapAPrintingWithoutTouchingAnythingElse() throws IOException {
        FakeCardSource source = new FakeCardSource().with("sol_ring");
        ResolvedDeck deck = new DeckImporter(source).importText("2 Sol Ring");

        JsonObject other = Fixtures.json("sol_ring");
        other.addProperty("id", "00000000-0000-4000-8000-0000000000aa");
        CardMetadata replacement = ScryfallCardCodec.parse(other).orElseThrow();

        ResolvedCard swapped = deck.cards().get(0).withPrinting(replacement);

        assertThat(swapped.identity().printing()).contains(replacement.scryfallId());
        assertThat(swapped.quantity()).isEqualTo(2);
        assertThat(swapped.section()).isEqualTo(DeckSection.MAINBOARD);
        assertThat(swapped.printingChosenAutomatically()).isFalse();
    }

    /** Answers from a fixed set of fixtures, and records what it was asked for. */
    private static final class FakeCardSource implements CardSource {

        private final List<CardMetadata> cards = new ArrayList<>();
        private final List<List<CardQuery>> batches = new ArrayList<>();

        FakeCardSource with(String fixture) {
            cards.add(Fixtures.card(fixture));
            return this;
        }

        List<List<CardQuery>> batches() {
            return List.copyOf(batches);
        }

        @Override
        public CollectionResult resolve(List<CardQuery> queries) {
            batches.add(List.copyOf(queries));
            Map<String, CardMetadata> found = new LinkedHashMap<>();
            List<CardQuery> notFound = new ArrayList<>();
            for (CardQuery query : queries) {
                cards.stream()
                        .filter(card -> dev.gathering.core.scryfall.CardQueryMatcher.matches(query, card))
                        .findFirst()
                        .ifPresentOrElse(card -> found.put(query.key(), card), () -> notFound.add(query));
            }
            return new CollectionResult(found, notFound);
        }
    }
}
