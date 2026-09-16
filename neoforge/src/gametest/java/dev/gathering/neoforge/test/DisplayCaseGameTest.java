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
}
