package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block a collection lives in.
 *
 * <p>Right-click with a card and it goes in. Right-click with an empty hand and the
 * collection opens. Nothing else: a collection block is a container, and a container that
 * needs a manual is a container nobody uses.
 *
 * <p>Looking is public and touching is not. Anybody may open one and read every card in it,
 * because a collection is a thing you show off and browsing the playgroup's pool without
 * asking is most of what one is for. Putting cards in and taking them out are rights the
 * owner grants, and breaking it needs the right to take - a collection you cannot take from
 * is not one you can walk off with either.
 */
public class CollectionBlock extends BaseEntityBlock {

    public static final MapCodec<CollectionBlock> CODEC = simpleCodec(CollectionBlock::new);

    public CollectionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CollectionBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Whoever places it owns it, and nobody else may touch what is inside.
     *
     * <p>A collection placed from an item that already held one keeps its owner: it was
     * somebody's before it was picked up, and the person carrying it home is usually them.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof CollectionBlockEntity collection) {
            collection.claimFor(player.getUUID());
        }
    }

    /**
     * A card in hand goes in.
     *
     * <p>The whole stack, because cards do not stack, and because a player holding a card
     * over a collection block means to put that card in it. A deck is left alone: sleeving
     * and unsleeving are the deck screen's, and a deck dropped in here by accident would be
     * forty cards dissolved by a misclick.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CollectionBlockEntity collection)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        CardIdentity card = CardItem.cardOf(stack)
                .map(component -> component.faceUp().toIdentity())
                .orElse(null);
        if (card == null) {
            // Not a card. Falls through to the empty-handed path, which opens it - so
            // right-clicking with a pickaxe in hand reads a collection rather than doing
            // nothing at all.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!collection.rights().mayAdd(player.getUUID())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.gathering.collection_may_not_add"));
            return ItemInteractionResult.SUCCESS;
        }
        int howMany = stack.getCount();
        // A card with a history keeps it. A stack is only ever more than one card when none
        // of them has one - a story is part of what an item is, so two cards with different
        // histories never stacked in the first place.
        dev.gathering.core.story.CardStory story =
                dev.gathering.server.CardStories.storyOf(stack);
        if (story.isEmpty()) {
            collection.put(card, howMany);
        } else {
            for (int one = 0; one < howMany; one++) {
                collection.putStoried(card, story);
            }
        }
        stack.setCount(0);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.gathering.collection_added", howMany, collection.cards().total()));
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * An empty hand opens it, for anybody. Crouching, it puts everything loose away instead.
     *
     * <p>Shift for "all of it", which is what shift means everywhere else in this game and
     * in Minecraft. It is the same errand the deck builder's shift-click is: a booster box is
     * thirty-six stacks, and putting them away one right-click at a time is an evening.
     *
     * <p><b>Both hands have to be empty for the crouching half to happen at all</b>, and that
     * is vanilla's rule rather than this block's: {@code ServerPlayerGameMode#useItemOn}
     * skips block interaction entirely when the player is crouching and holding anything in
     * either hand, unless that item bypasses the sneak, so a collection cannot see the click.
     * Nothing here can widen that without a mixin, and a mixin for a convenience is not a
     * trade this mod makes. An empty hand is what the gesture is called anyway.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CollectionBlockEntity collection)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer opener) {
            if (opener.isSecondaryUseActive()) {
                dev.gathering.server.CollectionView.sweepPockets(opener, collection);
            } else {
                dev.gathering.server.CollectionView.open(opener, pos, collection);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * What one drops: itself, holding its contents.
     *
     * <p>Every card inside travels in the item's own data, so a collection moved across a
     * base is the same collection when it is put down. Ten thousand cards is a few hundred
     * entries, which is a size an item can carry.
     */
    @Override
    protected java.util.List<ItemStack> getDrops(
            BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        BlockEntity entity = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        ItemStack stack = new ItemStack(GatheringContent.COLLECTION_ITEM.get());
        if (entity instanceof CollectionBlockEntity collection) {
            collection.saveToItem(stack, collection.getLevel() == null
                    ? params.getLevel().registryAccess()
                    : collection.getLevel().registryAccess());
        }
        return java.util.List.of(stack);
    }

    /** For a game test and for anything that wants to know without opening a screen. */
    public static CardTally cardsAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CollectionBlockEntity collection
                ? collection.cards()
                : CardTally.EMPTY;
    }

}
