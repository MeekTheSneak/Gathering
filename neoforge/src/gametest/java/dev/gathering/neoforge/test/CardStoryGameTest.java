package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlock;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.core.story.CardStory;
import dev.gathering.core.story.HowItCame;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.StoryComponent;
import dev.gathering.server.CardStories;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A card's history surviving the places a card goes.
 * <p>The story itself is pure and checked next door in milliseconds. What only exists in a
 * world is whether it survives being put away - a collection stores counts, and a count cannot
 * hold a story, so the one thing that could quietly destroy a card's history is the ordinary
 * act of putting it in a box.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CardStoryGameTest {

    private static final UUID BOLT = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final CardIdentity CARD = CardIdentity.ofPrinting(BOLT, false);

    private static CardStory.Chapter won() {
        return new CardStory.Chapter(HowItCame.WON, "Winner", "Loser", "", "2026-03-14");
    }

    /** A card, distinct from every other card, so two copies can be told apart in a fixture. */
    private static CardComponent aCard() {
        return new CardComponent(java.util.Optional.of(UUID.randomUUID()), false,
                java.util.Optional.empty(), false);
    }

    /** A history with one chapter naming who pulled it, so two of them are not equal. */
    private static CardStory pulledBy(String who) {
        return CardStory.begunWith(new CardStory.Chapter(HowItCame.PULLED, who, "", "DMU", "2026-09-09"));
    }

    /**
     * A card's history survives everything an ordinary deck has done to it.
     * <p>The story lives on the item, and going into a deck used to leave the item behind: the
     * pack it came out of, the trade it came through and the game it was won in were gone the
     * first time the card was played with. The deck keeps them instead.
     * <p>Kept, but only if every functional copy of the deck carries them. They did not: each
     * one went through the convenience constructor that starts the histories empty, so adding
     * a second card, moving one between sections, renaming the deck, painting its box or
     * changing its sleeves each wiped the provenance of everything already in it. This walks
     * a deck through all of that and then through the wire, rather than asking the three
     * methods that keep histories whether they keep histories.
     */
    @GameTest(template = "empty")
    public static void ahistorySurvivesEverythingADeckHasDoneToIt(GameTestHelper helper) {
        CardComponent first = aCard();
        CardStory theirs = pulledBy("Ana");

        dev.gathering.item.DeckComponent deck = new dev.gathering.item.DeckComponent(
                "Deck", "", java.util.Optional.empty(), java.util.List.of(first),
                java.util.List.of(), java.util.List.of(), java.util.Optional.empty(),
                dev.gathering.core.card.Sleeve.DEFAULT).keeping(first, theirs);

        // Every ordinary edit, one after another, the way an evening with a deck goes.
        deck = deck.withAdded(dev.gathering.item.DeckComponent.Section.MAINBOARD, aCard()).orElseThrow();
        deck = deck.named("Renamed");
        deck = deck.colored(0x884422);
        deck = deck.sleeved(dev.gathering.core.card.Sleeve.values()[1]);
        deck = deck.withAdded(dev.gathering.item.DeckComponent.Section.SIDEBOARD, aCard()).orElseThrow();
        deck = deck.moved(dev.gathering.item.DeckComponent.Section.SIDEBOARD,
                dev.gathering.item.DeckComponent.Section.MAINBOARD,
                deck.sideboard().get(0)).orElseThrow();

        for (String step : java.util.List.of("after all of that")) {
            if (!deck.storyOf(first).filter(theirs::equals).isPresent()) {
                helper.fail("The history was lost " + step + ": " + deck.stories());
                return;
            }
        }

        // And through the wire, which is how a deck reaches the client that draws it.
        dev.gathering.item.DeckComponent back = roundTrip(helper, deck);
        if (back == null) {
            return;
        }
        if (!back.storyOf(first).filter(theirs::equals).isPresent()) {
            helper.fail("The history did not survive being written down and read back");
            return;
        }
        helper.succeed();
    }

    /**
     * Two copies of one printing keep two histories, and taking one out takes one of them.
     * <p>The case the whole shape exists for: histories are kept beside the deck rather than
     * on the card, because two copies of a printing are the same card to a collection however
     * different their pasts. So the deck has to hand back one history per copy, and never the
     * same one twice.
     */
    @GameTest(template = "empty")
    public static void twoCopiesKeepTwoHistories(GameTestHelper helper) {
        CardComponent card = aCard();
        CardStory hers = pulledBy("Ana");
        CardStory his = pulledBy("Ben");

        dev.gathering.item.DeckComponent deck = new dev.gathering.item.DeckComponent(
                "Deck", "", java.util.Optional.empty(), java.util.List.of(card, card),
                java.util.List.of(), java.util.List.of(), java.util.Optional.empty(),
                dev.gathering.core.card.Sleeve.DEFAULT)
                .keeping(card, hers)
                .keeping(card, his);

        deck = deck.named("Still ours");
        if (deck.stories().size() != 2) {
            helper.fail("Two histories went in and " + deck.stories().size() + " came out");
            return;
        }
        CardStory firstOut = deck.storyOf(card).orElse(null);
        dev.gathering.item.DeckComponent after = deck.withoutStoryOf(card);
        CardStory secondOut = after.storyOf(card).orElse(null);
        if (firstOut == null || secondOut == null || firstOut.equals(secondOut)) {
            helper.fail("Two copies handed back the same history: " + firstOut + " and " + secondOut);
            return;
        }
        if (after.withoutStoryOf(card).storyOf(card).isPresent()) {
            helper.fail("A third history came out of a deck that was keeping two");
            return;
        }
        helper.succeed();
    }

    /**
     * The card handed back by the real TAKE handler carries its history on it.
     * <p>Not the pieces: the actual payload the client sends, through the actual handler, to
     * the actual item in the player's inventory. The pieces all passed while this did not,
     * because the deck the handler read the history off had already had it stripped.
     */
    @GameTest(template = "empty")
    public static void thetakeHandlerHandsBackTheHistoryToo(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        CardComponent card = aCard();
        CardStory theirs = pulledBy("Ana");

        player.setItemInHand(InteractionHand.MAIN_HAND, dev.gathering.item.DeckItem.of(
                new dev.gathering.item.DeckComponent(
                        "Deck", "", java.util.Optional.empty(),
                        java.util.List.of(card, aCard()), java.util.List.of(), java.util.List.of(),
                        java.util.Optional.empty(), dev.gathering.core.card.Sleeve.DEFAULT)
                        .keeping(card, theirs)));

        dev.gathering.server.DeckEdits.handle(player, dev.gathering.network.DeckEditPayload.take(
                InteractionHand.MAIN_HAND, dev.gathering.item.DeckComponent.Section.MAINBOARD, card));

        ItemStack drawn = null;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack maybe = player.getInventory().getItem(slot);
            if (CardItem.cardOf(maybe).filter(card::equals).isPresent()) {
                drawn = maybe;
                break;
            }
        }
        if (drawn == null) {
            helper.fail("The TAKE handler did not hand the card over at all");
            return;
        }
        if (StoryComponent.on(drawn).isEmpty()) {
            helper.fail("The TAKE handler handed back the card without its history");
            return;
        }
        if (!StoryComponent.on(drawn).equals(theirs)) {
            helper.fail("The TAKE handler handed back a different history");
            return;
        }
        // And the deck stopped keeping it, so the next copy out does not inherit it.
        dev.gathering.item.DeckComponent left = dev.gathering.item.DeckItem.deckOf(
                player.getItemInHand(InteractionHand.MAIN_HAND)).orElse(null);
        if (left == null || !left.stories().isEmpty()) {
            helper.fail("The deck is still keeping a history for a card that has left it");
            return;
        }
        helper.succeed();
    }

    /** A deck written down and read back the way one crosses to a client. */
    private static dev.gathering.item.DeckComponent roundTrip(
            GameTestHelper helper, dev.gathering.item.DeckComponent deck) {
        var registries = helper.getLevel().registryAccess();
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), registries);
        dev.gathering.item.DeckComponent.STREAM_CODEC.encode(buffer, deck);
        dev.gathering.item.DeckComponent back =
                dev.gathering.item.DeckComponent.STREAM_CODEC.decode(buffer);
        if (buffer.readableBytes() != 0) {
            helper.fail("A deck left " + buffer.readableBytes() + " bytes unread");
            return null;
        }
        return back;
    }

    @GameTest(template = "tables")
    public static void aStoryGoesOntoACard(GameTestHelper helper) {
        ItemStack card = CardItem.of(CardComponent.of(CARD));
        CardStories.remember(card, won());

        CardStory story = StoryComponent.on(card);
        if (story.isEmpty() || story.latest().who().isEmpty()) {
            helper.fail("A card that was won remembers nothing");
            return;
        }
        // And a card is still the same card: identity must not have moved.
        if (!CardItem.cardOf(card).orElseThrow().toIdentity().equals(CARD)) {
            helper.fail("Writing a story on a card changed what card it is");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void nothingIsWrittenOnSomethingThatIsNotACard(GameTestHelper helper) {
        ItemStack notACard = new ItemStack(net.minecraft.world.item.Items.STONE);
        CardStories.remember(notACard, won());

        if (!StoryComponent.on(notACard).isEmpty()) {
            helper.fail("A story was written onto a block of stone");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aCollectionKeepsAStoryRatherThanEatingIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));

        ItemStack card = CardItem.of(CardComponent.of(CARD));
        CardStories.remember(card, won());
        put(helper, at, player, card);

        if (collection.cards().of(CARD) != 1) {
            helper.fail("A card with a history did not go into the collection at all");
            return;
        }
        if (collection.storiedCount(CARD) != 1) {
            helper.fail("A collection ate the history of a card put into it");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void theOrdinaryCopyLeavesFirst(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));

        // One won off somebody, one ordinary.
        ItemStack trophy = CardItem.of(CardComponent.of(CARD));
        CardStories.remember(trophy, won());
        put(helper, at, player, trophy);
        put(helper, at, player, CardItem.of(CardComponent.of(CARD)));

        // Standing at the block, because taking from a collection is a reach check away and
        // a mock player starts wherever the structure put them.
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));

        // Taking one out has to hand back the plain one and leave the trophy in the box.
        int took = dev.gathering.server.CollectionView.take(
                player, helper.absolutePos(at), CardComponent.of(CARD), 1);
        if (took != 1) {
            helper.fail("A card that is in the collection could not be taken out");
            return;
        }
        if (collection.storiedCount(CARD) != 1) {
            helper.fail("Taking an ordinary copy out took the one with a history instead");
            return;
        }
        helper.succeed();
    }

    /**
     * And when the trophy is the copy coming out, it comes out carrying its history.
     * <p>The other half of the rule above. Ordinary copies leave first and the trophy stays
     * at the bottom of the box - but eventually it is the only one left, and that is the take
     * this got wrong. The number of storied copies was counted <em>after</em> the take, and
     * every departure prunes the stories the box no longer has copies to hang on: so by the
     * time it was asked, the answer was zero and every copy leaving looked ordinary. The card
     * somebody won off somebody else came out of the box as a plain card, with its history
     * already deleted behind it, and there was nothing anywhere to say it had ever had one.
     */
    @GameTest(template = "tables")
    public static void theLastCopyBringsItsHistoryWithIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));

        ItemStack trophy = CardItem.of(CardComponent.of(CARD));
        CardStories.remember(trophy, won());
        put(helper, at, player, trophy);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));

        if (dev.gathering.server.CollectionView.take(
                player, helper.absolutePos(at), CardComponent.of(CARD), 1) != 1) {
            helper.fail("the only copy in the collection could not be taken out");
            return;
        }

        ItemStack out = null;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(dev.gathering.item.GatheringContent.CARD.get())) {
                out = stack;
                break;
            }
        }
        if (out == null) {
            helper.fail("the card taken out of the collection is nowhere in the inventory");
            return;
        }
        CardStory story = CardStories.storyOf(out);
        if (story.isEmpty()) {
            helper.fail("the last copy came out of the box as a plain card, and its history"
                    + " is gone from the box as well");
            return;
        }
        if (story.latest() == null || !"Winner".equals(story.latest().who())) {
            helper.fail("the card came out carrying somebody else's history: " + story);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aStorySurvivesTheDisk(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.putStoried(CARD, CardStory.begunWith(won()));

        var registries = helper.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag saved = collection.saveWithoutMetadata(registries);
        CollectionBlockEntity read = new CollectionBlockEntity(
                helper.absolutePos(at), collection.getBlockState());
        read.loadWithComponents(saved, registries);

        if (read.storiedCount(CARD) != 1) {
            helper.fail("A card's history did not survive the collection being saved");
            return;
        }
        CardStory story = read.storied().get(0).story();
        if (story.latest() == null || !"Winner".equals(story.latest().who())
                || !"Loser".equals(story.latest().from())) {
            helper.fail("A card's history came back off the disk saying something else");
            return;
        }
        helper.succeed();
    }

    /** Puts one card in through the block, the way a player does. */
    private static void put(
            GameTestHelper helper, BlockPos at, net.minecraft.server.level.ServerPlayer player,
            ItemStack card) {
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        helper.getLevel().getBlockState(helper.absolutePos(at)).useItemOn(
                card, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)),
                        net.minecraft.core.Direction.UP, helper.absolutePos(at), false));
    }

    private static CollectionBlockEntity place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState());
        return (CollectionBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(at));
    }
}
