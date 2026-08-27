package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Finding a basic land in a library")
class BasicLandsTest {

    @Test
    @DisplayName("it finds them top first, as many as asked for")
    void itFindsThem() {
        List<CardMetadata> library = List.of(
                card("Lightning Bolt", "Instant"),
                card("Forest", "Basic Land — Forest"),
                card("Grizzly Bears", "Creature — Bear"),
                card("Forest", "Basic Land — Forest"),
                card("Forest", "Basic Land — Forest"));

        assertThat(BasicLands.findIn(library, "Forest", 2)).containsExactly(1, 3);
        assertThat(BasicLands.findIn(library, "Forest", 9)).containsExactly(1, 3, 4);
    }

    @Test
    @DisplayName("a deck with none in it fetches none")
    void anEmptyDeckIsTheAnswer() {
        // The whole point of fetching from a library rather than from nowhere: a deck that
        // is not running Forests cannot produce one.
        List<CardMetadata> library = List.of(
                card("Island", "Basic Land — Island"),
                card("Lightning Bolt", "Instant"));

        assertThat(BasicLands.findIn(library, "Forest", 1)).isEmpty();
    }

    @Test
    @DisplayName("a card merely called Forest is not one")
    void theTypeLineHasToAgree() {
        // Somebody's token, or a card whose name happens to match. The player asked for the
        // basic land, and a fetch that hands them something else is worse than one that
        // hands them nothing.
        List<CardMetadata> library = List.of(card("Forest", "Legendary Creature — Treefolk"));

        assertThat(BasicLands.findIn(library, "Forest", 1)).isEmpty();
    }

    @Test
    @DisplayName("a snow-covered one is a different card")
    void theNameHasToAgreeToo() {
        // Somebody chose to run these. Handing one over when a Forest was asked for changes
        // the deck they built.
        List<CardMetadata> library = List.of(
                card("Snow-Covered Forest", "Basic Snow Land — Forest"));

        assertThat(BasicLands.findIn(library, "Forest", 1)).isEmpty();
    }

    @Test
    @DisplayName("a card nobody has looked up is passed over, not guessed at")
    void unknownCardsAreNotGuesses() {
        List<CardMetadata> library = Arrays.asList(
                null,
                card("Forest", "Basic Land — Forest"));

        assertThat(BasicLands.findIn(library, "Forest", 2)).containsExactly(1);
    }

    @Test
    @DisplayName("asking for nothing, or out of nothing, finds nothing")
    void theEmptyAnswers() {
        List<CardMetadata> library = List.of(card("Forest", "Basic Land — Forest"));

        assertThat(BasicLands.findIn(library, "Forest", 0)).isEmpty();
        assertThat(BasicLands.findIn(library, "Forest", -3)).isEmpty();
        assertThat(BasicLands.findIn(library, "", 1)).isEmpty();
        assertThat(BasicLands.findIn(library, null, 1)).isEmpty();
        assertThat(BasicLands.findIn(null, "Forest", 1)).isEmpty();
        assertThat(BasicLands.findIn(List.of(), "Forest", 1)).isEmpty();
    }

    @Test
    @DisplayName("the name is matched without case or stray spaces")
    void theNameIsMatchedLoosely() {
        List<CardMetadata> library = List.of(card("Forest", "Basic Land — Forest"));

        assertThat(BasicLands.findIn(library, "forest", 1)).containsExactly(0);
        assertThat(BasicLands.findIn(library, "  FOREST  ", 1)).containsExactly(0);
    }

    private static CardMetadata card(String name, String typeLine) {
        UUID id = UUID.nameUUIDFromBytes(
                (name + typeLine).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CardMetadata(
                id, id, name, "", 0, typeLine, "", java.util.Set.of(), java.util.Set.of(),
                List.of(), "normal", "tst", "Test Set", "1", Rarity.COMMON,
                false, true, true, false, false, List.of("paper"), Map.of(), Map.of(), "");
    }
}
