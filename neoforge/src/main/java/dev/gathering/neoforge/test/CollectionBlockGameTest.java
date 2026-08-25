package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlock;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cards going into a collection and coming out of it.
 *
 * <p>What is checked here is the block: the tally itself is pure and checked next door in
 * milliseconds. This is the part that only exists in a world - a card in a hand becoming a
 * count in a block, a block that survives being saved and read back, and permissions that
 * are asked before anything moves rather than after.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionBlockGameTest {

    private static final UUID BOLT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID FOREST = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID STRANGER = UUID.fromString("99999999-9999-4999-8999-999999999999");

    @GameTest(template = "empty")
    public static void aCardInHandGoesIn(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));

        ItemStack card = CardItem.of(CardComponent.of(CardIdentity.ofPrinting(BOLT, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        helper.getLevel().getBlockState(helper.absolutePos(at)).useItemOn(
                card, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)),
                        net.minecraft.core.Direction.UP, helper.absolutePos(at), false));

        if (collection.cards().of(CardIdentity.ofPrinting(BOLT, false)) != 1) {
            helper.fail("A card put into a collection is not in it: " + collection.cards());
            return;
        }
        if (!card.isEmpty()) {
            helper.fail("A card put into a collection is still in the hand as well");
            return;
        }
        helper.succeed();
    }

    /** Somebody else's collection is somebody else's. */
    @GameTest(template = "empty")
    public static void aStrangerMayNotPutCardsIn(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.setRights(CollectionRights.ownedBy(STRANGER));
        var player = helper.makeMockServerPlayerInLevel();

        ItemStack card = CardItem.of(CardComponent.of(CardIdentity.ofPrinting(BOLT, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        helper.getLevel().getBlockState(helper.absolutePos(at)).useItemOn(
                card, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)),
                        net.minecraft.core.Direction.UP, helper.absolutePos(at), false));

        if (!collection.cards().isEmpty()) {
            helper.fail("A stranger put a card into somebody else's collection");
            return;
        }
        if (card.isEmpty()) {
            helper.fail("A refused card was taken out of the hand anyway");
            return;
        }
        helper.succeed();
    }

    /** A collection is claimed by whoever puts it down, once. */
    @GameTest(template = "empty")
    public static void whoeverPlacesItOwnsIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();

        collection.claimFor(player.getUUID());
        collection.claimFor(STRANGER);

        if (!collection.rights().isOwner(player.getUUID())) {
            helper.fail("A collection changed hands to whoever asked second");
            return;
        }
        if (collection.rights().mayTake(STRANGER)) {
            helper.fail("A stranger may take from a collection they do not own");
            return;
        }
        helper.succeed();
    }

    /** What is in it survives being written down and read back. */
    @GameTest(template = "empty")
    public static void itSurvivesBeingSaved(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID())
                .allowingAdd(STRANGER));
        collection.put(CardIdentity.ofPrinting(BOLT, false), 4);
        collection.put(CardIdentity.ofPrinting(BOLT, true), 1);
        collection.put(CardIdentity.ofPrinting(FOREST, false), 40);
        collection.setLabel("The good one");

        var registries = helper.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag written = collection.saveWithFullMetadata(registries);
        CollectionBlockEntity read = new CollectionBlockEntity(
                helper.absolutePos(at),
                GatheringContent.COLLECTION.get().defaultBlockState());
        read.loadWithComponents(written, registries);

        if (!read.cards().equals(collection.cards())) {
            helper.fail("A collection read back is not the one that was written: "
                    + read.cards() + " against " + collection.cards());
            return;
        }
        if (read.cards().of(CardIdentity.ofPrinting(BOLT, true)) != 1) {
            helper.fail("A foil came back as something other than a foil");
            return;
        }
        if (!read.rights().isOwner(player.getUUID()) || !read.rights().mayAdd(STRANGER)) {
            helper.fail("Who may touch a collection was not written down");
            return;
        }
        if (!read.label().equals("The good one")) {
            helper.fail("A collection came back called " + read.label());
            return;
        }
        helper.succeed();
    }

    /** Breaking it leaves an item carrying everything, not ten thousand cards. */
    @GameTest(template = "empty")
    public static void itDropsAsOneItemHoldingEverything(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.put(CardIdentity.ofPrinting(FOREST, false), 40);

        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(
                helper.getLevel())
                .withParameter(
                        net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)))
                .withParameter(
                        net.minecraft.world.level.storage.loot.parameters.LootContextParams
                                .BLOCK_ENTITY, collection);
        var dropped = helper.getLevel().getBlockState(helper.absolutePos(at)).getDrops(params);

        if (dropped.size() != 1) {
            helper.fail("A collection of forty cards dropped " + dropped.size() + " items");
            return;
        }
        ItemStack stack = dropped.get(0);
        if (!stack.is(GatheringContent.COLLECTION_ITEM.get())) {
            helper.fail("A collection dropped " + stack + " rather than itself");
            return;
        }
        if (stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA) == null) {
            helper.fail("A collection dropped without what was inside it");
            return;
        }
        helper.succeed();
    }

    /**
     * Reading is public, and something other than a card in hand still opens it.
     *
     * <p>The opening itself is a screen, so it is checked by the scripted client run rather
     * than here - a mock player has no connection to send one to. What is checked here is
     * the two answers the block gives before it gets that far: that a stranger may look, and
     * that holding a pickaxe does not turn a right-click into nothing happening.
     */
    @GameTest(template = "empty")
    public static void anybodyMayLookAndAnythingInHandStillOpensIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.setRights(CollectionRights.ownedBy(STRANGER));
        var player = helper.makeMockServerPlayerInLevel();

        if (!collection.rights().mayLook(player.getUUID())) {
            helper.fail("Somebody was refused a look at a collection");
            return;
        }

        ItemStack pickaxe = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        var result = helper.getLevel().getBlockState(helper.absolutePos(at)).useItemOn(
                pickaxe, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)),
                        net.minecraft.core.Direction.UP, helper.absolutePos(at), false));
        if (result != net.minecraft.world.ItemInteractionResult
                .PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            helper.fail("Holding something that is not a card stopped a collection opening: "
                    + result);
            return;
        }
        if (pickaxe.getCount() != 1) {
            helper.fail("A collection ate a pickaxe");
            return;
        }
        helper.succeed();
    }

    /** Nothing in a collection reads as nothing, whichever way it is asked. */
    @GameTest(template = "empty")
    public static void anEmptyOneIsEmpty(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        place(helper, at);

        if (!CollectionBlock.cardsAt(helper.getLevel(), helper.absolutePos(at)).isEmpty()) {
            helper.fail("A collection nobody has touched is not empty");
            return;
        }
        helper.succeed();
    }

    private static CollectionBlockEntity place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(at))
                instanceof CollectionBlockEntity collection) {
            return collection;
        }
        throw new IllegalStateException("A collection block was placed without its block entity");
    }
}
