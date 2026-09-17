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

    @Test
    @DisplayName("a card set aside is in the sideboard and not in the deck")
    void asideIsItsOwnPile() {
        BuildCard bolt = card("Lightning Bolt", UUID.randomUUID(), "Instant", 1);

        DeckBuild build = DeckBuild.EMPTY.with(bolt).aside(bolt);

        assertThat(build.cards()).hasSize(1);
        assertThat(build.sideboard()).hasSize(1);
        assertThat(build.deckTotal()).isEqualTo(1);
        assertThat(build.total()).isEqualTo(2);
        assertThat(build.sideboardRows()).singleElement()
                .satisfies(row -> assertThat(row.count()).isEqualTo(1));
        // And the pile it is in has nothing to do with the kinds the deck is grouped into.
        assertThat(DeckBuild.EMPTY.aside(bolt).byKind()).isEmpty();
    }

    @Test
    @DisplayName("copies of a printing count across both piles, so the box is not asked twice")
    void bothPilesCountTowardWhatIsLeft() {
        UUID oracle = UUID.randomUUID();
        BuildCard bolt = card("Lightning Bolt", oracle, "Instant", 1);

        DeckBuild build = DeckBuild.EMPTY.with(bolt).with(bolt).aside(bolt);

        assertThat(build.printingsOf(bolt.printing())).isEqualTo(3);
        assertThat(build.copiesOf(oracle)).isEqualTo(3);
        // And so does what leaves the collection when Finish is pressed.
        assertThat(build.everything()).hasSize(3);
    }

    @Test
    @DisplayName("a row is taken out of the pile it was clicked in")
    void removingWorksFromEitherPile() {
        BuildCard bolt = card("Lightning Bolt", UUID.randomUUID(), "Instant", 1);
        DeckBuild build = DeckBuild.EMPTY.with(bolt).aside(bolt);

        DeckBuild side = build.without(bolt.printing(), DeckBuild.Pile.SIDEBOARD);
        assertThat(side.sideboard()).isEmpty();
        assertThat(side.cards()).hasSize(1);

        DeckBuild deck = build.without(bolt.printing(), DeckBuild.Pile.MAINBOARD);
        assertThat(deck.cards()).isEmpty();
        assertThat(deck.sideboard()).hasSize(1);

        // Without a pile named, the deck goes first and the sideboard answers when it cannot.
        assertThat(build.without(bolt.printing()).cards()).isEmpty();
        assertThat(DeckBuild.EMPTY.aside(bolt).without(bolt.printing()).sideboard()).isEmpty();
    }

    @Test
    @DisplayName("a card moves between the piles without a copy being made or lost")
    void movingIsOneOperationInEveryDirection() {
        BuildCard bolt = card("Lightning Bolt", UUID.randomUUID(), "Instant", 1);
        DeckBuild build = DeckBuild.EMPTY.with(bolt);

        DeckBuild across = build.moved(bolt, DeckBuild.Pile.MAINBOARD, DeckBuild.Pile.SIDEBOARD);
        assertThat(across.cards()).isEmpty();
        assertThat(across.sideboard()).containsExactly(bolt);
        assertThat(across.total()).isEqualTo(1);

        DeckBuild back = across.moved(bolt, DeckBuild.Pile.SIDEBOARD, DeckBuild.Pile.MAINBOARD);
        assertThat(back.sideboard()).isEmpty();
        assertThat(back.cards()).containsExactly(bolt);

        // And into the command zone, out of the sideboard, which is a move like any other.
        DeckBuild leading = across.moved(bolt, DeckBuild.Pile.SIDEBOARD, DeckBuild.Pile.COMMANDERS);
        assertThat(leading.commander()).contains(bolt);
        assertThat(leading.sideboard()).isEmpty();
        assertThat(leading.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("moving a card that is not in the pile it was named in does nothing")
    void aStaleMoveConjuresNothing() {
        BuildCard bolt = card("Lightning Bolt", UUID.randomUUID(), "Instant", 1);
        DeckBuild build = DeckBuild.EMPTY.with(bolt);

        DeckBuild after = build.moved(bolt, DeckBuild.Pile.SIDEBOARD, DeckBuild.Pile.MAINBOARD);

        assertThat(after).isEqualTo(build);
        assertThat(after.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("naming a commander takes it out of the sideboard as well")
    void aCommanderLeavesTheSideboardToo() {
        BuildCard atraxa = card("Atraxa", UUID.randomUUID(), "Legendary Creature — Angel", 4);
        DeckBuild build = DeckBuild.EMPTY.aside(atraxa);

        DeckBuild led = build.led(atraxa);

        assertThat(led.sideboard()).isEmpty();
        assertThat(led.commander()).contains(atraxa);
        assertThat(led.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("the commander it replaces goes back to the deck rather than nowhere")
    void aReplacedCommanderIsNotLost() {
        BuildCard first = card("Atraxa", UUID.randomUUID(), "Legendary Creature — Angel", 4);
        BuildCard second = card("Talrand", UUID.randomUUID(), "Legendary Creature — Merfolk", 3);

        DeckBuild build = DeckBuild.EMPTY.led(first).led(second);

        assertThat(build.commander()).contains(second);
        assertThat(build.cards()).containsExactly(first);
        assertThat(build.total()).isEqualTo(2);
        // And clearing the command zone keeps the card the same way.
        assertThat(build.led(null).cards()).containsExactly(first, second);
    }

    @Test
    @DisplayName("the two piles share one bound, so neither can carry a build past it")
    void oneBoundAcrossBothPiles() {
        BuildCard bolt = card("Lightning Bolt", UUID.randomUUID(), "Instant", 1);
        DeckBuild build = DeckBuild.EMPTY;
        for (int one = 0; one < DeckBuild.MOST_CARDS; one++) {
            build = build.with(bolt);
        }

        assertThat(build.cards()).hasSize(DeckBuild.MOST_CARDS);
        // Full is full, whichever pile the next card was headed for.
        assertThat(build.aside(bolt).sideboard()).isEmpty();
        assertThat(build.with(bolt).cards()).hasSize(DeckBuild.MOST_CARDS);
    }

    private static BuildCard card(String name, UUID oracle, String typeLine, double manaValue) {
        return new BuildCard(UUID.randomUUID(), oracle, name, typeLine, "", manaValue,
                Set.of(), false);
    }
}
