package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.deck.ResolvedCard;
import dev.gathering.core.deck.ResolvedDeck;
import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.decklist.DecklistEntry;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.CollectionDecks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Building a deck out of a collection, in a running world.
 *
 * <p>What the pure rule cannot check is the half that moves things: that the cards actually
 * leave the box, that the deck that arrives holds exactly them, and that neither happens for
 * somebody who is not allowed to take. Cards that exist in two places at once is the one
 * failure collection mode must not have, and it is a failure of this half rather than of the
 * arithmetic.
 *
 * <p>Names come from the list rather than from the card cache, which is what lets this run
 * without reaching a network: a deck poured back into a box and rebuilt from the same list is
 * the ordinary case, and it is the case that needs no lookup at all.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionDeckGameTest {

    private static final UUID BOLT = UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111");
    private static final UUID RING = UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222");

    /** The whole point: a list goes in, a deck comes out, and the box is lighter. */
    @GameTest(template = "empty")
    public static void aListBecomesADeckAndTheBoxIsLighter(GameTestHelper helper) {
        BlockPos where = new BlockPos(1, 1, 1);
        CollectionBlockEntity box = collectionAt(helper, where);
        ServerPlayer player = standing(helper, where);
        box.claimFor(player.getUUID());
        box.put(CardIdentity.ofPrinting(BOLT, false), 4);
        box.put(CardIdentity.ofPrinting(RING, false), 1);

        CollectionDecks.build(player, helper.absolutePos(where),
                list(card(BOLT, "Lightning Bolt", 4), card(RING, "Sol Ring", 1)), "Burn", "");

        ItemStack deck = onlyDeck(player);
        if (deck.isEmpty()) {
            helper.fail("No deck came out of the collection");
            return;
        }
        var component = DeckItem.deckOf(deck).orElse(null);
        if (component == null || component.totalCards() != 5) {
            helper.fail("The deck holds " + (component == null ? "nothing" : component.totalCards())
                    + " cards rather than five");
            return;
        }
        if (!box.cards().isEmpty()) {
            helper.fail("The collection still holds " + box.cards().total()
                    + " card(s) that are now in a deck as well");
            return;
        }
        helper.succeed();
    }

    /**
     * A list the box cannot fill builds the part it can.
     *
     * <p>Ninety cards you have and a list of the ten to find is what somebody sitting at their
     * binder actually wants. A refusal would send them back to taking cards one at a time.
     */
    @GameTest(template = "empty")
    public static void shortIsStillADeck(GameTestHelper helper) {
        BlockPos where = new BlockPos(1, 1, 1);
        CollectionBlockEntity box = collectionAt(helper, where);
        ServerPlayer player = standing(helper, where);
        box.claimFor(player.getUUID());
        box.put(CardIdentity.ofPrinting(BOLT, false), 1);

        CollectionDecks.build(player, helper.absolutePos(where),
                list(card(BOLT, "Lightning Bolt", 4)), "Burn", "");

        var component = DeckItem.deckOf(onlyDeck(player)).orElse(null);
        if (component == null || component.totalCards() != 1) {
            helper.fail("A box with one of four in it did not build a deck of one");
            return;
        }
        if (box.cards().total() != 0) {
            helper.fail("The one card in the box did not move into the deck");
            return;
        }
        helper.succeed();
    }

    /** Somebody with no right to take gets no deck, and the box is untouched. */
    @GameTest(template = "empty")
    public static void withoutTheRightToTakeNothingMoves(GameTestHelper helper) {
        BlockPos where = new BlockPos(1, 1, 1);
        CollectionBlockEntity box = collectionAt(helper, where);
        ServerPlayer player = standing(helper, where);
        // Somebody else's collection, and they have not been let in.
        box.claimFor(UUID.fromString("cccccccc-3333-4333-8333-333333333333"));
        box.put(CardIdentity.ofPrinting(BOLT, false), 4);

        CollectionDecks.build(player, helper.absolutePos(where),
                list(card(BOLT, "Lightning Bolt", 4)), "Burn", "");

        if (!onlyDeck(player).isEmpty()) {
            helper.fail("A deck was built out of somebody else's collection");
            return;
        }
        if (box.cards().total() != 4) {
            helper.fail("Cards left a collection the taker had no right to");
            return;
        }
        helper.succeed();
    }

    /** A collection nobody is standing at hands over nothing, whatever the payload said. */
    @GameTest(template = "empty")
    public static void standingSomewhereElseTakesNothing(GameTestHelper helper) {
        BlockPos where = new BlockPos(1, 1, 1);
        CollectionBlockEntity box = collectionAt(helper, where);
        ServerPlayer player = standing(helper, where);
        box.claimFor(player.getUUID());
        box.put(CardIdentity.ofPrinting(BOLT, false), 4);
        // Reading a collection is public; being in front of one is not.
        player.setPos(player.getX() + 64.0, player.getY(), player.getZ());

        CollectionDecks.build(player, helper.absolutePos(where),
                list(card(BOLT, "Lightning Bolt", 4)), "Burn", "");

        if (box.cards().total() != 4 || !onlyDeck(player).isEmpty()) {
            helper.fail("A collection was emptied from across the room");
            return;
        }
        helper.succeed();
    }

    /**
     * A list longer than a deck can hold leaves the rest in the box.
     *
     * <p>A deck holds a thousand cards and past that it cannot be sent to the client that
     * asked for it. Taking the cards first and then failing to hand them over would be
     * somebody's collection gone for a list that was too long.
     */
    @GameTest(template = "empty")
    public static void whatWillNotFitStaysInTheBox(GameTestHelper helper) {
        BlockPos where = new BlockPos(1, 1, 1);
        CollectionBlockEntity box = collectionAt(helper, where);
        ServerPlayer player = standing(helper, where);
        box.claimFor(player.getUUID());
        int tooMany = dev.gathering.item.DeckComponent.MAX_CARDS + 200;
        box.put(CardIdentity.ofPrinting(BOLT, false), tooMany);

        // Four lines, because one line is capped on its own; together they ask for too much.
        CollectionDecks.build(player, helper.absolutePos(where), list(
                card(BOLT, "Lightning Bolt", 400), card(BOLT, "Lightning Bolt", 400),
                card(BOLT, "Lightning Bolt", 400), card(BOLT, "Lightning Bolt", 24)),
                "A very long list", "");

        var component = DeckItem.deckOf(onlyDeck(player)).orElse(null);
        if (component == null
                || component.totalCards() != dev.gathering.item.DeckComponent.MAX_CARDS) {
            helper.fail("A deck of " + (component == null ? "nothing" : component.totalCards())
                    + " rather than the " + dev.gathering.item.DeckComponent.MAX_CARDS
                    + " a deck holds");
            return;
        }
        if (box.cards().total() + component.totalCards() != tooMany) {
            helper.fail("Cards went missing: " + box.cards().total() + " left in the box and "
                    + component.totalCards() + " in the deck, out of " + tooMany);
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ bits

    private static CollectionBlockEntity collectionAt(GameTestHelper helper, BlockPos where) {
        helper.setBlock(where, GatheringContent.COLLECTION.get().defaultBlockState());
        return (CollectionBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(where));
    }

    private static ServerPlayer standing(GameTestHelper helper, BlockPos where) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absoluteVec(where.getCenter()));
        return player;
    }

    /** The one deck in the player's inventory, or an empty stack. */
    private static ItemStack onlyDeck(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(GatheringContent.DECK.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ResolvedDeck list(ResolvedCard... cards) {
        return new ResolvedDeck("A list", List.of(cards), List.of(), List.of());
    }

    private static ResolvedCard card(UUID printing, String name, int howMany) {
        CardMetadata about = new CardMetadata(
                printing, printing, name, "{R}", 1.0, "Instant", "Does a thing.",
                Set.of(), Set.of(), List.<CardFace>of(), "normal", "tst", "Test", "1",
                Rarity.COMMON, false, true, true, false, false,
                List.of("paper"), Map.of(), Map.of(), "https://scryfall.com/");
        return new ResolvedCard(
                CardIdentity.ofPrinting(printing, false), about, howMany, DeckSection.MAINBOARD,
                new DecklistEntry(howMany, name, null, null, false, DeckSection.MAINBOARD, 1, ""),
                false);
    }
}
