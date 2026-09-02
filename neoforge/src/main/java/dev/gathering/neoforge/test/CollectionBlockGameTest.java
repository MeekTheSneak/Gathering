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

    @GameTest(template = "tables")
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
    @GameTest(template = "tables")
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
    @GameTest(template = "tables")
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
    @GameTest(template = "tables")
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
    @GameTest(template = "tables")
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
     * <p>The opening itself is a screen, so it is checked by the scripted client run rather
     * than here - a mock player has no connection to send one to. What is checked here is
     * the two answers the block gives before it gets that far: that a stranger may look, and
     * that holding a pickaxe does not turn a right-click into nothing happening.
     */
    @GameTest(template = "tables")
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
    @GameTest(template = "tables")
    public static void anEmptyOneIsEmpty(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        place(helper, at);

        if (!CollectionBlock.cardsAt(helper.getLevel(), helper.absolutePos(at)).isEmpty()) {
            helper.fail("A collection nobody has touched is not empty");
            return;
        }
        helper.succeed();
    }

    /**
     * A deck in hand fills up from the collection rather than the inventory.
     * <p>Which is what sleeving is: you do not carry forty loose cards from the binder to the
     * table. Holding a deck is the whole of the gesture, so this is the one that must not
     * quietly stop working.
     */
    @GameTest(template = "tables")
    public static void cardsGoIntoTheDeckInHand(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));
        collection.put(CardIdentity.ofPrinting(FOREST, false), 20);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));

        ItemStack deck = dev.gathering.item.DeckItem.of(new dev.gathering.item.DeckComponent(
                "Jank", "", java.util.Optional.of(player.getUUID()),
                java.util.List.of(), java.util.List.of(), java.util.List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND, deck);

        dev.gathering.server.CollectionView.take(player, helper.absolutePos(at),
                CardComponent.of(CardIdentity.ofPrinting(FOREST, false)), 4);

        var after = dev.gathering.item.DeckItem.deckOf(
                player.getMainHandItem()).orElse(null);
        if (after == null || after.deckSize() != 4) {
            helper.fail("Four cards sleeved into a held deck came out as "
                    + (after == null ? "no deck" : after.deckSize() + " cards"));
            return;
        }
        if (collection.cards().of(CardIdentity.ofPrinting(FOREST, false)) != 16) {
            helper.fail("The collection still holds "
                    + collection.cards().of(CardIdentity.ofPrinting(FOREST, false)));
            return;
        }
        // The deck itself is in the inventory - it is what is in hand - so what is counted
        // is loose cards, which is what a card that failed to sleeve would look like.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(GatheringContent.CARD.get())) {
                helper.fail("A card sleeved into a deck turned up loose in the inventory as well");
                return;
            }
        }
        helper.succeed();
    }

    /** With nothing in hand the cards come out loose, as they always did. */
    @GameTest(template = "tables")
    public static void withoutADeckTheyComeOutLoose(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));
        collection.put(CardIdentity.ofPrinting(BOLT, false), 4);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));

        dev.gathering.server.CollectionView.take(player, helper.absolutePos(at),
                CardComponent.of(CardIdentity.ofPrinting(BOLT, false)), 2);

        if (collection.cards().of(CardIdentity.ofPrinting(BOLT, false)) != 2) {
            helper.fail("Two cards taken left " + collection.cards());
            return;
        }
        int loose = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(GatheringContent.CARD.get())) {
                loose++;
            }
        }
        if (loose != 2) {
            helper.fail("Two cards taken with an empty hand turned up as " + loose);
            return;
        }
        helper.succeed();
    }

    /** A deck poured back in is every card of it, from every section. */
    @GameTest(template = "tables")
    public static void aDeckDissolvesBackIntoIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));

        CardComponent forest = CardComponent.of(CardIdentity.ofPrinting(FOREST, false));
        CardComponent bolt = CardComponent.of(CardIdentity.ofPrinting(BOLT, false));
        ItemStack deck = dev.gathering.item.DeckItem.of(new dev.gathering.item.DeckComponent(
                "Jank", "", java.util.Optional.of(player.getUUID()),
                java.util.List.of(forest, forest, bolt),
                java.util.List.of(bolt),
                java.util.List.of(forest)));
        player.setItemInHand(InteractionHand.MAIN_HAND, deck);

        if (!dev.gathering.server.CollectionView.dissolve(
                player, helper.absolutePos(at), player.getMainHandItem())) {
            helper.fail("A deck held at a collection would not dissolve into it");
            return;
        }
        if (collection.cards().total() != 5) {
            helper.fail("A five-card deck poured in as " + collection.cards().total());
            return;
        }
        if (collection.cards().of(CardIdentity.ofPrinting(FOREST, false)) != 3
                || collection.cards().of(CardIdentity.ofPrinting(BOLT, false)) != 2) {
            helper.fail("The sideboard or the command zone did not come with it: "
                    + collection.cards().counts());
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            helper.fail("A dissolved deck is still in the hand as well as in the collection");
            return;
        }
        helper.succeed();
    }

    /** And a stranger cannot pour a deck into somebody else's collection. */
    @GameTest(template = "tables")
    public static void aStrangerMayNotDissolveIntoIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.setRights(CollectionRights.ownedBy(STRANGER));
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));

        CardComponent forest = CardComponent.of(CardIdentity.ofPrinting(FOREST, false));
        ItemStack deck = dev.gathering.item.DeckItem.of(new dev.gathering.item.DeckComponent(
                "Jank", "", java.util.Optional.of(player.getUUID()),
                java.util.List.of(forest), java.util.List.of(), java.util.List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND, deck);

        dev.gathering.server.CollectionView.dissolve(
                player, helper.absolutePos(at), player.getMainHandItem());

        if (!collection.cards().isEmpty()) {
            helper.fail("A stranger poured a deck into somebody else's collection");
            return;
        }
        if (player.getMainHandItem().isEmpty()) {
            helper.fail("A refused deck was taken off the player anyway");
            return;
        }
        helper.succeed();
    }

    /** A collection across the world is not one anybody is standing at. */
    @GameTest(template = "tables")
    public static void takingNeedsSomebodyStandingThere(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));
        collection.put(CardIdentity.ofPrinting(BOLT, false), 4);
        player.setPos(player.getX() + 400, player.getY(), player.getZ());

        dev.gathering.server.CollectionView.take(player, helper.absolutePos(at),
                CardComponent.of(CardIdentity.ofPrinting(BOLT, false)), 4);

        if (collection.cards().of(CardIdentity.ofPrinting(BOLT, false)) != 4) {
            helper.fail("A collection four hundred blocks away handed its cards over");
            return;
        }
        helper.succeed();
    }

    /**
     * The sweep takes every loose card and nothing else.
     * <p>The whole gesture in one assertion: cards go, the deck stays a deck, the sealed pack
     * stays sealed. Those two are the ones worth pinning - a sweep that dissolved a hundred
     * card Commander deck would be a misclick with no way back, and one that opened somebody's
     * packs would be worse.
     */
    @GameTest(template = "tables")
    public static void sweepingTakesLooseCardsAndNothingElse(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));

        ItemStack loose = CardItem.of(CardComponent.of(CardIdentity.ofPrinting(BOLT, false)));
        loose.setCount(4);
        player.getInventory().add(loose);
        player.getInventory().add(
                CardItem.of(CardComponent.of(CardIdentity.ofPrinting(FOREST, false))));

        CardComponent forest = CardComponent.of(CardIdentity.ofPrinting(FOREST, false));
        player.getInventory().add(dev.gathering.item.DeckItem.of(
                new dev.gathering.item.DeckComponent("Keep me", "", java.util.Optional.empty(),
                        java.util.List.of(forest, forest), java.util.List.of(),
                        java.util.List.of())));

        int swept = dev.gathering.server.CollectionView.sweepPockets(player, collection);

        if (swept != 5) {
            helper.fail("a sweep of four bolts and a forest took " + swept + " cards");
            return;
        }
        if (collection.cards().of(CardIdentity.ofPrinting(BOLT, false)) != 4
                || collection.cards().of(CardIdentity.ofPrinting(FOREST, false)) != 1) {
            helper.fail("the swept cards did not land in the collection: it holds "
                    + collection.cards().total());
            return;
        }
        int decks = 0;
        int cardsLeft = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (dev.gathering.item.DeckItem.deckOf(stack).isPresent()) {
                decks++;
            } else if (stack.getItem() instanceof CardItem) {
                cardsLeft += stack.getCount();
            }
        }
        if (cardsLeft != 0) {
            helper.fail("the sweep left " + cardsLeft + " loose card(s) behind");
            return;
        }
        if (decks != 1) {
            helper.fail("the sweep took the deck as well - " + decks + " left of one");
            return;
        }
        helper.succeed();
    }

    /** And a stranger sweeping into somebody else's box keeps their own cards. */
    @GameTest(template = "tables")
    public static void aStrangerMayNotSweepIntoIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.setRights(CollectionRights.ownedBy(STRANGER));
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(
                CardItem.of(CardComponent.of(CardIdentity.ofPrinting(BOLT, false))));

        int swept = dev.gathering.server.CollectionView.sweepPockets(player, collection);

        if (swept != 0 || !collection.cards().isEmpty()) {
            helper.fail("a stranger swept cards into somebody else's collection");
            return;
        }
        if (player.getInventory().countItem(
                dev.gathering.item.GatheringContent.CARD.get()) != 1) {
            helper.fail("a refused sweep took the cards off the player anyway");
            return;
        }
        helper.succeed();
    }

    /**
     * Somebody else's collection cannot be picked up by breaking it.
     * <p>Every other way in asks first, so a break that did not would be the way round all
     * of them: swing once and the block is in your inventory with ten thousand cards of
     * somebody else's in it. The block itself cannot say no - vanilla has decided by the
     * time {@code playerWillDestroy} runs, and removes the block whatever that returns - so
     * the answer comes from the loader's break event, and this is the test that the wiring
     * is actually there rather than a guard in a method that cannot refuse.
     */
    @GameTest(template = "tables")
    public static void aStrangerMayNotBreakACollectionOpen(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.setRights(CollectionRights.ownedBy(STRANGER));
        collection.put(CardIdentity.ofPrinting(BOLT, false), 4);

        var thief = helper.makeMockServerPlayerInLevel();
        thief.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));
        thief.gameMode.destroyBlock(helper.absolutePos(at));

        if (!helper.getLevel().getBlockState(helper.absolutePos(at))
                .is(GatheringContent.COLLECTION.get())) {
            helper.fail("A stranger broke somebody else's collection out of the ground");
            return;
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at))
                instanceof CollectionBlockEntity still)
                || still.cards().of(CardIdentity.ofPrinting(BOLT, false)) != 4) {
            helper.fail("A refused break emptied the collection anyway");
            return;
        }
        for (var dropped : helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(at)).inflate(2.0))) {
            if (dropped.getItem().is(GatheringContent.COLLECTION_ITEM.get())) {
                helper.fail("A refused break dropped the collection to be picked up anyway");
                return;
            }
        }
        helper.succeed();
    }

    /** The owner's own collection still comes up when they break it, cards and all. */
    @GameTest(template = "tables")
    public static void theOwnerBreaksTheirOwnCollectionUpNormally(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var owner = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()));
        collection.put(CardIdentity.ofPrinting(BOLT, false), 4);

        owner.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));
        owner.gameMode.destroyBlock(helper.absolutePos(at));

        if (helper.getLevel().getBlockState(helper.absolutePos(at))
                .is(GatheringContent.COLLECTION.get())) {
            helper.fail("An owner could not break their own collection");
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
