package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Turning cards over until one of them is the one")
class RevealUntilTest {

    @Test
    @DisplayName("cascade stops on the first card that costs less, and shows it")
    void itCountsTheCardThatStoppedIt() {
        List<CardMetadata> library = List.of(
                card("Wrath", 4, "Sorcery"),
                card("Titan", 6, "Creature - Giant"),
                card("Bolt", 1, "Instant"),
                card("Elf", 1, "Creature - Elf"));

        // Three: the two that were too dear, and the Bolt. The card that stopped it is the
        // one everybody at the table is waiting to see, so it is turned over too.
        assertThat(RevealUntil.howFarDown(library, RevealUntil.cheaperThan(3))).isEqualTo(3);
    }

    @Test
    @DisplayName("the top card can be the one")
    void sometimesItIsTheFirstOne() {
        assertThat(RevealUntil.howFarDown(
                List.of(card("Bolt", 1, "Instant")), RevealUntil.cheaperThan(3)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a deck with nothing cheaper reveals nothing")
    void nothingMatchesIsAnAnswer() {
        List<CardMetadata> library = List.of(
                card("Titan", 6, "Creature - Giant"),
                card("Wrath", 4, "Sorcery"));

        // Zero rather than the whole library. A cascade that found nothing has found nothing,
        // and turning every card face up looking is not a reveal, it is showing everybody the
        // deck.
        assertThat(RevealUntil.howFarDown(library, RevealUntil.cheaperThan(3))).isZero();
    }

    @Test
    @DisplayName("cheaper than means cheaper, not the same price")
    void theBoundaryIsExclusive() {
        List<CardMetadata> library = List.of(card("Wrath", 3, "Sorcery"));

        assertThat(RevealUntil.howFarDown(library, RevealUntil.cheaperThan(3))).isZero();
        assertThat(RevealUntil.howFarDown(library, RevealUntil.cheaperThan(4))).isEqualTo(1);
    }

    @Test
    @DisplayName("a type is looked for anywhere on the line, and without case")
    void theTypeLineIsALine() {
        List<CardMetadata> library = List.of(
                card("Bolt", 1, "Instant"),
                card("Druid", 2, "Legendary Creature - Elf Druid"));

        assertThat(RevealUntil.howFarDown(library, RevealUntil.ofType("creature"))).isEqualTo(2);
        assertThat(RevealUntil.howFarDown(library, RevealUntil.ofType("ELF"))).isEqualTo(2);
        assertThat(RevealUntil.howFarDown(library, RevealUntil.ofType("  Instant  "))).isEqualTo(1);
        assertThat(RevealUntil.howFarDown(library, RevealUntil.ofType("Planeswalker"))).isZero();
    }

    @Test
    @DisplayName("asking for nothing finds nothing")
    void anEmptyTypeMatchesNothing() {
        List<CardMetadata> library = List.of(card("Bolt", 1, "Instant"));

        assertThat(RevealUntil.howFarDown(library, RevealUntil.ofType(""))).isZero();
        assertThat(RevealUntil.howFarDown(library, RevealUntil.ofType("   "))).isZero();
        assertThat(RevealUntil.howFarDown(library, RevealUntil.ofType(null))).isZero();
    }

    @Test
    @DisplayName("a card this server has never looked up is passed over, not stopped on")
    void anUnknownCardIsNotAGuess() {
        // Stopping on it would show a card that might not match at all, and nothing on screen
        // would say which it was. It is still turned over on the way past - the count carries
        // on rather than the card being skipped.
        List<CardMetadata> library = Arrays.asList(
                null,
                card("Bolt", 1, "Instant"));

        assertThat(RevealUntil.howFarDown(library, RevealUntil.cheaperThan(3))).isEqualTo(2);
    }

    @Test
    @DisplayName("it will not turn over more of a library than anybody would")
    void thereIsACeiling() {
        List<CardMetadata> huge = new ArrayList<>();
        for (int card = 0; card < RevealUntil.MOST_TO_TURN_OVER + 50; card++) {
            huge.add(card("Titan " + card, 6, "Creature - Giant"));
        }
        huge.add(card("Bolt", 1, "Instant"));

        // The Bolt is past the ceiling, so it is not found: a cascade off a one-drop into a
        // deck with nothing cheaper would otherwise turn the whole library face up.
        assertThat(RevealUntil.howFarDown(huge, RevealUntil.cheaperThan(3))).isZero();
    }

    @Test
    @DisplayName("nothing to look through, and nothing to look for")
    void theEmptyAnswers() {
        assertThat(RevealUntil.howFarDown(List.of(), RevealUntil.cheaperThan(3))).isZero();
        assertThat(RevealUntil.howFarDown(null, RevealUntil.cheaperThan(3))).isZero();
        assertThat(RevealUntil.howFarDown(List.of(card("Bolt", 1, "Instant")), null)).isZero();
    }

    private static CardMetadata card(String name, double cost, String typeLine) {
        UUID id = UUID.nameUUIDFromBytes(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CardMetadata(
                id, id, name, "", cost, typeLine, "", java.util.Set.of(), java.util.Set.of(),
                List.of(), "normal", "tst", "Test Set", "1", Rarity.COMMON,
                false, true, true, false, false, List.of("paper"), Map.of(), Map.of(), "");
    }
}
