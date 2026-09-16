package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.BreakRules;
import dev.gathering.block.DisplayCaseBlockEntity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * One card, under glass, and who may touch it.
 * <p>The owner asked for a display case (2026-09-16). What is checked here is everything that only
 * exists in a world: a card going in through a real right-click and coming back out, a stranger refused
 * both, the card surviving the block being saved and read back, and - the one that matters most - that
 * what goes in is face up. A case is drawn on every client that can see the block, so a face-down card
 * in one would be a hidden identity handed to the room.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisplayCaseGameTest {

    private static final UUID BOLT = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @GameTest(template = "tables")
    public static void aCardGoesInAndComesBackOut(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());

        ItemStack card = CardItem.of(new CardComponent(
                java.util.Optional.of(BOLT), false, java.util.Optional.empty(), false));
        click(helper, owner, at, card);

        if (display.isEmpty()) {
            helper.fail("a card was put into a case and the case is empty");
            return;
        }
        if (!card.isEmpty()) {
            helper.fail("a card went on show and the player is still holding it");
            return;
        }
        // And back out, into the hand that put it there.
        click(helper, owner, at, ItemStack.EMPTY);
        if (!display.isEmpty()) {
            helper.fail("the owner took the card back and the case still has one");
            return;
        }
        if (owner.getInventory().countItem(GatheringContent.CARD.get()) != 1) {
            helper.fail("the card came out of the case and is nowhere");
            return;
        }
        helper.succeed();
    }

    /** What a case holds is sent to everybody in sight, so what goes in has to be face up. */
    @GameTest(template = "tables")
    public static void aCardPutInFaceDownGoesOnShowFaceUp(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());

        CardComponent turnedOver = new CardComponent(
                java.util.Optional.of(BOLT), false, java.util.Optional.empty(), true);
        if (!turnedOver.flipped()) {
            helper.fail("the fixture is not a face-down card, so this checks nothing");
            return;
        }
        click(helper, owner, at, CardItem.of(turnedOver));

        if (display.card().map(CardComponent::flipped).orElse(true)) {
            helper.fail("a card put into a case face down is on show face down: " + display.card());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aStrangerCannotTouchIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());

        ItemStack theirs = CardItem.of(new CardComponent(
                java.util.Optional.of(BOLT), false, java.util.Optional.empty(), false));
        click(helper, stranger, at, theirs);
        if (!display.isEmpty()) {
            helper.fail("a stranger put a card into somebody else's case");
            return;
        }
        if (theirs.isEmpty()) {
            helper.fail("a stranger's card was taken by a case that refused it");
            return;
        }

        display.show(new CardComponent(
                java.util.Optional.of(BOLT), false, java.util.Optional.empty(), false));
        click(helper, stranger, at, ItemStack.EMPTY);
        if (display.isEmpty()) {
            helper.fail("a stranger took the card out of somebody else's case");
            return;
        }
        // Nor by breaking the glass.
        if (BreakRules.refuse(helper.getLevel(), helper.absolutePos(at), stranger).isEmpty()) {
            helper.fail("a stranger was allowed to break a case with somebody else's card in it");
            return;
        }
        if (BreakRules.refuse(helper.getLevel(), helper.absolutePos(at), owner).isPresent()) {
            helper.fail("the owner was refused their own case");
            return;
        }
        helper.succeed();
    }

    /** Breaking it gives the case and the card back, the way a jukebox gives its record back. */
    @GameTest(template = "tables")
    public static void breakingItDropsTheCaseAndTheCard(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());
        display.show(new CardComponent(
                java.util.Optional.of(BOLT), false, java.util.Optional.empty(), false));

        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                display.getBlockState(), helper.getLevel(), helper.absolutePos(at), display, owner,
                ItemStack.EMPTY);

        long cases = drops.stream().filter(drop -> drop.is(GatheringContent.DISPLAY_CASE_ITEM.get())).count();
        long cards = drops.stream().filter(drop -> drop.is(GatheringContent.CARD.get())).count();
        if (cases != 1 || cards != 1) {
            helper.fail("a broken case dropped " + cases + " case(s) and " + cards + " card(s): " + drops);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void whatIsOnShowSurvivesBeingSavedAndReadBack(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());
        display.show(new CardComponent(
                java.util.Optional.of(BOLT), true, java.util.Optional.empty(), false));

        CompoundTag saved = display.saveWithFullMetadata(helper.getLevel().registryAccess());
        DisplayCaseBlockEntity again = place(helper, new BlockPos(3, 1, 1));
        again.loadWithComponents(saved, helper.getLevel().registryAccess());

        if (again.card().flatMap(CardComponent::scryfallId).filter(BOLT::equals).isEmpty()) {
            helper.fail("a case read back off the disk is showing " + again.card());
            return;
        }
        if (!again.isOwner(owner.getUUID())) {
            helper.fail("a case read back off the disk forgot whose it was");
            return;
        }
        if (again.isOwner(helper.makeMockServerPlayerInLevel().getUUID())) {
            helper.fail("a case read back off the disk belongs to everybody");
            return;
        }
        helper.succeed();
    }

    /**
     * Through the block's own right-click, which is the only way a card reaches a case in play.
     * <p>In survival, because a mock player is creative and vanilla puts a creative player's held stack
     * back however the click went - so "the card left the hand" is a question only a survival player can
     * be asked. See {@code CollectionBlockGameTest.aCreativeDeckDoesNotSurviveDissolving}, which is the
     * same vanilla behavior seen from the other side.
     */
    /**
     * A locked case keeps its cards, from the hand that locked it as much as from anybody else's.
     * <p>The owner asked for a lock (2026-09-16). Every gesture on this block was already the owner's
     * alone, so a lock that only shut strangers out would have changed nothing: what it is for is a
     * case somebody walks past every day, where one empty-handed click on the way past pockets the
     * card. So this checks the owner being refused, which is the whole point of it.
     */
    @GameTest(template = "tables")
    public static void alockedCaseRefusesItsOwnOwner(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());
        click(helper, owner, at, CardItem.of(new CardComponent(
                java.util.Optional.of(BOLT), false, java.util.Optional.empty(), false)));
        if (display.isEmpty()) {
            helper.fail("the case would not take a card before it was even locked");
            return;
        }

        crouchClick(helper, owner, at);
        if (!display.isLocked()) {
            helper.fail("the owner crouched and clicked and the case did not lock");
            return;
        }

        // The gesture that empties it, from the owner, on a locked case.
        click(helper, owner, at, ItemStack.EMPTY);
        if (display.isEmpty()) {
            helper.fail("a locked case handed its card to the owner anyway");
            return;
        }
        // And nothing more goes in either, or a locked case is a case that only half locks.
        ItemStack another = CardItem.of(new CardComponent(
                java.util.Optional.of(BOLT), false, java.util.Optional.empty(), false));
        click(helper, owner, at, another);
        if (display.cards().size() != 1) {
            helper.fail("a locked case took another card: it now holds " + display.cards().size());
            return;
        }

        // Unlocked again by the same gesture, and it behaves as it did.
        crouchClick(helper, owner, at);
        if (display.isLocked()) {
            helper.fail("the owner crouched and clicked a locked case and it stayed locked");
            return;
        }
        click(helper, owner, at, ItemStack.EMPTY);
        if (!display.isEmpty()) {
            helper.fail("an unlocked case would not give its card back");
            return;
        }
        helper.succeed();
    }

    /** A stranger cannot lock somebody else's case, or unlock one. */
    @GameTest(template = "tables")
    public static void astrangerCannotWorkTheLock(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();

        crouchClick(helper, stranger, at);
        if (display.isLocked()) {
            helper.fail("a stranger locked somebody else's case");
            return;
        }

        crouchClick(helper, owner, at);
        crouchClick(helper, stranger, at);
        if (!display.isLocked()) {
            helper.fail("a stranger unlocked somebody else's case");
            return;
        }
        helper.succeed();
    }

    /** The lock survives the block being written out and read back, as the card and the owner do. */
    @GameTest(template = "tables")
    public static void alockSurvivesASave(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());
        display.setLocked(true);

        CompoundTag written = display.saveWithoutMetadata(helper.getLevel().registryAccess());
        DisplayCaseBlockEntity read = place(helper, new BlockPos(1, 1, 3));
        read.loadWithComponents(written, helper.getLevel().registryAccess());
        if (!read.isLocked()) {
            helper.fail("a locked case was saved and came back unlocked");
            return;
        }
        helper.succeed();
    }

    /** The same click, crouching, which is how the lock is worked. */
    private static void crouchClick(GameTestHelper helper, ServerPlayer player, BlockPos at) {
        player.setShiftKeyDown(true);
        try {
            click(helper, player, at, ItemStack.EMPTY);
        } finally {
            player.setShiftKeyDown(false);
        }
    }

    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos at, ItemStack holding) {
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos where = helper.absolutePos(at);
        player.moveTo(where.getX() + 0.5, where.getY(), where.getZ() + 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, holding);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(where), Direction.UP, where, false));
    }

    private static DisplayCaseBlockEntity place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, GatheringContent.DISPLAY_CASE.get().defaultBlockState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(at))
                instanceof DisplayCaseBlockEntity display) {
            return display;
        }
        throw new IllegalStateException("A display case was placed without its block entity");
    }

    /**
     * Four of them, and the fifth refused - a case is a row of the good ones, not a chest.
     * <p>The owner asked for a case that shows a handful beside a shop counter (2026-09-16); it held one.
     */
    @GameTest(template = "tables")
    public static void aCaseShowsFourAndNoMore(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        DisplayCaseBlockEntity display = place(helper, at);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        display.claimFor(owner.getUUID());

        for (int card = 0; card < DisplayCaseBlockEntity.HOLDS; card++) {
            click(helper, owner, at, CardItem.of(new CardComponent(
                    java.util.Optional.of(new UUID(9L, card)), false, java.util.Optional.empty(), false)));
        }
        if (display.cards().size() != DisplayCaseBlockEntity.HOLDS) {
            helper.fail("a case took " + display.cards().size() + " of four cards");
            return;
        }
        ItemStack fifth = CardItem.of(new CardComponent(
                java.util.Optional.of(new UUID(9L, 99L)), false, java.util.Optional.empty(), false));
        click(helper, owner, at, fifth);
        if (display.cards().size() != DisplayCaseBlockEntity.HOLDS) {
            helper.fail("a full case took a fifth card");
            return;
        }
        if (fifth.isEmpty()) {
            helper.fail("a full case refused a card and kept it anyway");
            return;
        }
        // Breaking it gives every one of them back, not just the first.
        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                display.getBlockState(), helper.getLevel(), helper.absolutePos(at), display, owner,
                ItemStack.EMPTY);
        long cards = drops.stream().filter(drop -> drop.is(GatheringContent.CARD.get())).count();
        if (cards != DisplayCaseBlockEntity.HOLDS) {
            helper.fail("a broken case holding four gave back " + cards + " card(s)");
            return;
        }
        helper.succeed();
    }

    /**
     * Cases put side by side become one long case, and closing up again when one is taken out.
     * <p>The owner asked for the edges between adjacent cases to go away (2026-09-16). Two ends back to
     * back read as a row of boxes; with them dropped the glass, the lining and the lid run through.
     */
    @GameTest(template = "tables")
    public static void casesSideBySideBecomeOneCase(GameTestHelper helper) {
        BlockPos left = new BlockPos(1, 1, 1);
        BlockPos middle = new BlockPos(2, 1, 1);
        BlockPos right = new BlockPos(3, 1, 1);
        net.minecraft.world.level.block.state.BlockState facing =
                GatheringContent.DISPLAY_CASE.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                                Direction.SOUTH);
        for (BlockPos at : java.util.List.of(left, middle, right)) {
            helper.setBlock(at, facing);
        }

        if (!joinedOn(helper, middle, dev.gathering.block.DisplayCaseBlock.LEFT)
                || !joinedOn(helper, middle, dev.gathering.block.DisplayCaseBlock.RIGHT)) {
            helper.fail("a case with one either side of it is open at neither end");
            return;
        }
        // The ends of the row keep their outside end, or the run has no end to it.
        boolean leftOpen = joinedOn(helper, left, dev.gathering.block.DisplayCaseBlock.LEFT)
                && joinedOn(helper, left, dev.gathering.block.DisplayCaseBlock.RIGHT);
        if (leftOpen) {
            helper.fail("the case at the end of a row lost the end nothing is against");
            return;
        }

        // And taking the middle one out closes the two either side back up.
        helper.setBlock(middle, net.minecraft.world.level.block.Blocks.AIR);
        if (joinedOn(helper, left, dev.gathering.block.DisplayCaseBlock.LEFT)
                || joinedOn(helper, left, dev.gathering.block.DisplayCaseBlock.RIGHT)
                || joinedOn(helper, right, dev.gathering.block.DisplayCaseBlock.LEFT)
                || joinedOn(helper, right, dev.gathering.block.DisplayCaseBlock.RIGHT)) {
            helper.fail("a case still thinks it is joined to one that has been taken away");
            return;
        }
        helper.succeed();
    }

    /** A case facing the other way is a corner, not part of the run. */
    @GameTest(template = "tables")
    public static void acaseTurnedTheOtherWayDoesNotJoin(GameTestHelper helper) {
        BlockPos one = new BlockPos(1, 1, 1);
        BlockPos other = new BlockPos(2, 1, 1);
        var facingProperty = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
        helper.setBlock(one, GatheringContent.DISPLAY_CASE.get().defaultBlockState()
                .setValue(facingProperty, Direction.SOUTH));
        helper.setBlock(other, GatheringContent.DISPLAY_CASE.get().defaultBlockState()
                .setValue(facingProperty, Direction.EAST));

        if (joinedOn(helper, one, dev.gathering.block.DisplayCaseBlock.LEFT)
                || joinedOn(helper, one, dev.gathering.block.DisplayCaseBlock.RIGHT)) {
            helper.fail("a case joined itself to one facing a different way");
            return;
        }
        helper.succeed();
    }

    private static boolean joinedOn(GameTestHelper helper, BlockPos at,
            net.minecraft.world.level.block.state.properties.BooleanProperty end) {
        return helper.getBlockState(at).getValue(end);
    }
}
