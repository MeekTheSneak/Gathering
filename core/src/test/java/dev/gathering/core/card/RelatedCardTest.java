package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.testing.Fixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a card says it makes, read off Scryfall's all_parts. */
class RelatedCardTest {

    @Test
    @DisplayName("Tevesh Szat offers his Thrull and does not offer himself")
    void theCommanderMakesThrulls() {
        // The reference deck's commander, and the reason named tokens exist at all: his +2
        // makes two Thrulls, twice a turn, every game.
        CardMetadata szat = Fixtures.card("tevesh_szat");

        assertThat(szat.tokensMade()).containsExactly("Thrull");
    }

    @Test
    @DisplayName("a card with nothing related to it makes nothing")
    void mostCardsMakeNothing() {
        assertThat(Fixtures.card("sol_ring").tokensMade()).isEmpty();
        assertThat(Fixtures.card("forest").tokensMade()).isEmpty();
    }

    @Test
    @DisplayName("emblems count, meld results do not")
    void onlyWhatGoesOntoTheBattlefield() {
        UUID card = UUID.randomUUID();
        CardMetadata walker = withParts(card,
                new RelatedCard(UUID.randomUUID(), "Beast", "Token Creature - Beast", "token"),
                new RelatedCard(UUID.randomUUID(), "Vivien Reid Emblem", "Emblem - Vivien",
                        "combo_piece"),
                // The card itself, which Scryfall lists in its own all_parts.
                new RelatedCard(card, "Vivien Reid", "Legendary Planeswalker - Vivien",
                        "combo_piece"),
                // Melding moves two cards you already own; it makes nothing.
                new RelatedCard(UUID.randomUUID(), "Chittering Host", "Creature - Horror",
                        "meld_result"),
                new RelatedCard(UUID.randomUUID(), "Graf Rats", "Creature - Rat", "meld_part"));

        assertThat(walker.tokensMade()).containsExactly("Beast", "Vivien Reid Emblem");
    }

    @Test
    @DisplayName("a card that makes the same token twice offers it once")
    void theSameTokenIsOfferedOnce() {
        UUID card = UUID.randomUUID();
        CardMetadata maker = withParts(card,
                new RelatedCard(UUID.randomUUID(), "Thrull", "Token Creature - Thrull", "token"),
                new RelatedCard(UUID.randomUUID(), "Thrull", "Token Creature - Thrull", "token"));

        assertThat(maker.tokensMade()).containsExactly("Thrull");
    }

    @Test
    @DisplayName("a card built without the field has no tokens rather than no metadata")
    void theShortFormStillWorks() {
        CardMetadata plain = new CardMetadata(
                UUID.randomUUID(), UUID.randomUUID(), "Anything", "", 0, "Creature", "",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal", "tst", "Test Set",
                "1", Rarity.COMMON, false, true, true, false, false, List.of("paper"),
                java.util.Map.of(), java.util.Map.of(), "");

        assertThat(plain.related()).isEmpty();
        assertThat(plain.tokensMade()).isEmpty();
    }

    private static CardMetadata withParts(UUID id, RelatedCard... parts) {
        return new CardMetadata(
                id, UUID.randomUUID(), "Test", "", 0, "Legendary Planeswalker", "",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal", "tst", "Test Set",
                "1", Rarity.MYTHIC, false, true, true, false, false, List.of("paper"),
                java.util.Map.of(), java.util.Map.of(), "", List.of(parts));
    }
}
