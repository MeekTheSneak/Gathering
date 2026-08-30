package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The deck a builder screen is drawing, and the arithmetic it does not have to do itself. */
class DeckBuildTest {

    @Test
    @DisplayName("copies are counted by card, not by printing")
    void copiesAreByOracle() {
        UUID bolt = UUID.randomUUID();
        DeckBuild deck = DeckBuild.EMPTY
                .with(card("Lightning Bolt", bolt, "Instant", 1))
                .with(card("Lightning Bolt", bolt, "Instant", 1));

        assertThat(deck.copiesOf(bolt)).isEqualTo(2);
        // Two different printings of the same card are still two of that card.
        assertThat(deck.cards().get(0).printing()).isNotEqualTo(deck.cards().get(1).printing());
    }

    @Test
    @DisplayName("taking one out takes the last one added, not the first")
    void removingTakesTheLast() {
        BuildCard first = card("Forest", UUID.randomUUID(), "Basic Land — Forest", 0);
        DeckBuild deck = DeckBuild.EMPTY.with(first).with(first);

        DeckBuild left = deck.without(first.printing());

        assertThat(left.printingsOf(first.printing())).isEqualTo(1);
    }

    @Test
    @DisplayName("naming a commander takes it out of the deck proper")
    void aCommanderLeavesTheDeck() {
        BuildCard atraxa = card("Atraxa", UUID.randomUUID(), "Legendary Creature — Angel", 4);
        DeckBuild deck = DeckBuild.EMPTY.with(atraxa).with(
                card("Sol Ring", UUID.randomUUID(), "Artifact", 1));

        DeckBuild led = deck.led(atraxa);

        assertThat(led.commander()).contains(atraxa);
        assertThat(led.cards()).hasSize(1);
        // And it is still in the deck for the purpose of what leaves the collection.
        assertThat(led.everything()).hasSize(2);
        assertThat(led.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("an artifact creature is filed under creatures")
    void typePriorityPutsCreaturesFirst() {
        assertThat(CardKind.of("Artifact Creature — Golem")).isEqualTo(CardKind.CREATURE);
        assertThat(CardKind.of("Legendary Enchantment Artifact")).isEqualTo(CardKind.ARTIFACT);
        assertThat(CardKind.of("Basic Land — Forest")).isEqualTo(CardKind.LAND);
        assertThat(CardKind.of("Instant — Arcane")).isEqualTo(CardKind.INSTANT);
        // Nothing recognizable is filed rather than dropped.
        assertThat(CardKind.of("Scheme")).isEqualTo(CardKind.OTHER);
        assertThat(CardKind.of("")).isEqualTo(CardKind.OTHER);
    }

    @Test
    @DisplayName("a pile collapses copies into one row with a count")
    void pilesCollapseCopies() {
        UUID bolt = UUID.randomUUID();
        BuildCard one = card("Lightning Bolt", bolt, "Instant", 1);
        DeckBuild deck = DeckBuild.EMPTY.with(one).with(one).with(one);

        List<DeckBuild.Row> instants = deck.byKind().get(CardKind.INSTANT);

        assertThat(instants).hasSize(1);
        assertThat(instants.get(0).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("empty piles are left out, so no heading has nothing under it")
    void emptyPilesAreNotDrawn() {
        DeckBuild deck = DeckBuild.EMPTY.with(card("Sol Ring", UUID.randomUUID(), "Artifact", 1));

        assertThat(deck.byKind()).containsOnlyKeys(CardKind.ARTIFACT);
    }

    @Test
    @DisplayName("the curve leaves lands out and buckets everything from seven up")
    void theCurveIgnoresLands() {
        DeckBuild deck = DeckBuild.EMPTY
                .with(card("Forest", UUID.randomUUID(), "Basic Land — Forest", 0))
                .with(card("Bolt", UUID.randomUUID(), "Instant", 1))
                .with(card("Emrakul", UUID.randomUUID(), "Creature — Eldrazi", 15));

        int[] curve = deck.curve();

        assertThat(curve[0]).isZero();
        assertThat(curve[1]).isEqualTo(1);
        assertThat(curve[DeckBuild.CURVE_BUCKETS - 1]).isEqualTo(1);
    }

    @Test
    @DisplayName("a card outside the commander's colors is reported and not refused")
    void outsideIdentityIsSaidAndAllowed() {
        BuildCard commander = new BuildCard(UUID.randomUUID(), UUID.randomUUID(),
                "Talrand", "Legendary Creature — Merfolk Wizard", "", 3, Set.of("U"), false);
        BuildCard red = new BuildCard(UUID.randomUUID(), UUID.randomUUID(),
                "Lightning Bolt", "Instant", "", 1, Set.of("R"), false);
        BuildCard blue = new BuildCard(UUID.randomUUID(), UUID.randomUUID(),
                "Counterspell", "Instant", "", 2, Set.of("U"), false);

        DeckBuild deck = DeckBuild.EMPTY.with(red).with(blue).led(commander);

        // Both are in the deck. One of them is flagged.
        assertThat(deck.cards()).hasSize(2);
        assertThat(deck.outsideIdentity()).extracting(BuildCard::name).containsExactly("Lightning Bolt");
    }

    @Test
    @DisplayName("with no commander nothing is outside anything")
    void noCommanderNoIdentity() {
        DeckBuild deck = DeckBuild.EMPTY.with(new BuildCard(UUID.randomUUID(), UUID.randomUUID(),
                "Lightning Bolt", "Instant", "", 1, Set.of("R"), false));

        assertThat(deck.identity()).isEmpty();
        assertThat(deck.outsideIdentity()).isEmpty();
    }

    private static BuildCard card(String name, UUID oracle, String typeLine, double manaValue) {
        return new BuildCard(UUID.randomUUID(), oracle, name, typeLine, "", manaValue,
                Set.of(), false);
    }
}
