package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.Rarity;
import dev.gathering.network.CardFaceSummary;
import dev.gathering.network.CardSummary;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Which side of a card is showing.
 *
 * <p>A card lies on a table one way up. Asking a summary for every printed side and laying
 * them out in a row - which is what every screen in the mod did - turns a transform card into
 * two half-size cards side by side, which is not a thing that exists.
 *
 * <p>The distinction that has to survive: a transform card has two of everything and shows
 * one at a time; a split card has two lots of rules on one piece of card and shows that one
 * piece whichever way it is read. What tells them apart is whether the second face carries
 * art of its own.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DoubleFacedGameTest {

    @GameTest(template = "empty")
    public static void aTransformCardShowsOneSideAtATime(GameTestHelper helper) {
        CardSummary card = new CardSummary(UUID.randomUUID(), UUID.randomUUID(),
                face("Delver of Secrets", "front.jpg"),
                Optional.of(face("Insectile Aberration", "back.jpg")),
                Rarity.COMMON, 1.0, java.util.Set.of());

        if (!card.hasAnotherSide()) {
            helper.fail("a transform card said it had no other side");
            return;
        }
        if (!card.sideShown(false).name().equals("Delver of Secrets")) {
            helper.fail("face up showed " + card.sideShown(false).name());
            return;
        }
        if (!card.sideShown(true).name().equals("Insectile Aberration")) {
            helper.fail("turned over showed " + card.sideShown(true).name());
            return;
        }
        helper.succeed();
    }

    /** One piece of card, whichever way it is read. */
    @GameTest(template = "empty")
    public static void aSplitCardIsOnePieceOfCard(GameTestHelper helper) {
        CardSummary split = new CardSummary(UUID.randomUUID(), UUID.randomUUID(),
                face("Fire // Ice", "one.jpg"),
                // No art of its own: the second half of a split card is printed on the first.
                Optional.of(new CardFaceSummary("Ice", "{1}{U}", "Instant", "", "", "", "", "")),
                Rarity.UNCOMMON, 1.0, java.util.Set.of());

        if (split.hasAnotherSide()) {
            helper.fail("a split card claimed a second printed side");
            return;
        }
        if (!split.sideShown(true).name().equals("Fire // Ice")) {
            helper.fail("turning a split card over showed " + split.sideShown(true).name());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anOrdinaryCardHasOneSide(GameTestHelper helper) {
        CardSummary bolt = new CardSummary(UUID.randomUUID(), UUID.randomUUID(),
                face("Lightning Bolt", "bolt.jpg"), Optional.empty(), Rarity.COMMON, 1.0, java.util.Set.of());
        if (bolt.hasAnotherSide() || !bolt.sideShown(true).name().equals("Lightning Bolt")) {
            helper.fail("an ordinary card did not behave like one card");
            return;
        }
        helper.succeed();
    }

    /** A printing with no art at all still has something to draw rather than nothing. */
    @GameTest(template = "empty")
    public static void aCardWithNoArtStillHasASideToDraw(GameTestHelper helper) {
        CardSummary blank = new CardSummary(UUID.randomUUID(), UUID.randomUUID(),
                new CardFaceSummary("Nameless", "", "", "", "", "", "", ""),
                Optional.empty(), Rarity.UNKNOWN, 1.0, java.util.Set.of());
        if (!blank.sideShown(false).name().equals("Nameless")
                || !blank.sideShown(true).name().equals("Nameless")) {
            helper.fail("a card with no art had no side to draw");
            return;
        }
        helper.succeed();
    }

    private static CardFaceSummary face(String name, String art) {
        return new CardFaceSummary(name, "{1}", "Creature", "", "", art, art, "");
    }
}
