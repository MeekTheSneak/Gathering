package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.server.BasicLands;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Basic lands going into a deck.
 * <p>A drafted pool is forty-five spells, so without these there is no forty-card deck to
 * build from one at all - the draft ends in a deck nobody can put down. What is checked here
 * is the arithmetic, which is the only part with any in it: the looking-up is the card
 * service's, and it is checked where the card service is.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BasicLandsGameTest {

    private static final UUID FOREST = UUID.fromString("b7833c56-eb62-4c14-9db6-b9c1c92cb4ba");

    @GameTest(template = "empty")
    public static void landsGoIntoTheMainboardAndNowhereElse(GameTestHelper helper) {
        DeckComponent deck = deckOf(0);

        DeckComponent grown = BasicLands.grow(deck, card(FOREST), 17);

        if (grown.entries().size() != 17) {
            helper.fail("Seventeen Forests came out as " + grown.entries().size());
            return;
        }
        if (!grown.sideboard().isEmpty() || !grown.commanders().isEmpty()) {
            helper.fail("A basic land landed somewhere other than the mainboard");
            return;
        }
        helper.succeed();
    }

    /** Asking again adds again rather than replacing, which is what seventeen clicks means. */
    @GameTest(template = "empty")
    public static void askingTwiceAddsTwice(GameTestHelper helper) {
        DeckComponent once = BasicLands.grow(deckOf(0), card(FOREST), 5);
        DeckComponent twice = BasicLands.grow(once, card(FOREST), 5);

        if (twice.entries().size() != 10) {
            helper.fail("Two lots of five came out as " + twice.entries().size());
        }
        helper.succeed();
    }

    /**
     * A deck with room for two takes two of a request for five.
     * <p>Whatever fitted is kept. Refusing the whole request because the last copy did not
     * fit would throw away the ones that did, and somebody who asked for five into a deck
     * with room for two wants the two.
     */
    @GameTest(template = "empty")
    public static void whateverFitsIsKept(GameTestHelper helper) {
        DeckComponent nearlyFull = deckOf(DeckComponent.MAX_CARDS - 2);

        DeckComponent grown = BasicLands.grow(nearlyFull, card(FOREST), 5);

        if (grown.totalCards() != DeckComponent.MAX_CARDS) {
            helper.fail("A nearly full deck came out at " + grown.totalCards()
                    + ", not " + DeckComponent.MAX_CARDS);
            return;
        }
        // And a full one takes none, which is what the caller reads to say so.
        DeckComponent full = BasicLands.grow(grown, card(FOREST), 5);
        if (full.totalCards() != grown.totalCards()) {
            helper.fail("A full deck took another " + (full.totalCards() - grown.totalCards()));
        }
        helper.succeed();
    }

    /** Asking for none changes nothing at all. */
    @GameTest(template = "empty")
    public static void askingForNoneChangesNothing(GameTestHelper helper) {
        DeckComponent deck = deckOf(3);

        if (!BasicLands.grow(deck, card(FOREST), 0).equals(deck)) {
            helper.fail("A request for no lands changed the deck");
        }
        helper.succeed();
    }

    private static DeckComponent deckOf(int cards) {
        List<CardComponent> entries = new ArrayList<>(cards);
        for (int index = 0; index < cards; index++) {
            entries.add(card(UUID.nameUUIDFromBytes(
                    ("spell-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        return new DeckComponent("Pool", "", Optional.empty(), entries, List.of(), List.of());
    }

    private static CardComponent card(UUID printing) {
        return CardComponent.of(CardIdentity.ofPrinting(printing));
    }
}
