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
 *
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

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
    public static void nothingIsWrittenOnSomethingThatIsNotACard(GameTestHelper helper) {
        ItemStack notACard = new ItemStack(net.minecraft.world.item.Items.STONE);
        CardStories.remember(notACard, won());

        if (!StoryComponent.on(notACard).isEmpty()) {
            helper.fail("A story was written onto a block of stone");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
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
