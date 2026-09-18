package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import dev.gathering.item.TrophyComponent;
import dev.gathering.item.TrophyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a tournament leaves behind, standing on a shelf.
 * <p>Handed to whoever wins an event big enough to be worth remembering, engraved with the event,
 * the day and their name, and cast in a color of its own. It was an item first and the owner asked
 * for a block (2026-09-18), which is the right answer: an item in a chest is a souvenir nobody sees,
 * and the whole of what a trophy is for is standing where people walk past it.
 * <p>It does nothing. No rule anywhere reads it, it holds nothing and it powers nothing. A trophy
 * that did something would be a reward; what this is meant to be is a shelf of afternoons.
 * <p>What it says is on the item in the hand and, once it is down, read by clicking it - a placed
 * block has no tooltip, and an engraving nobody can read is a blank cup.
 */
public class TrophyBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<TrophyBlock> CODEC = simpleCodec(TrophyBlock::new);

    /** The cup and its plinth, which is the shape the model draws and nothing wider. */
    private static final VoxelShape SHAPE = Block.box(3, 0, 4, 13, 13, 12);

    public TrophyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Facing whoever put it down, so the engraved side is the side they are standing on.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrophyBlockEntity(pos, state);
    }

    /**
     * The engraving comes off the item and stays with the block.
     * <p>Which is what makes a placed trophy the trophy somebody won rather than a cup of the same
     * shape: the color and the three lines travel on the stack, and this is where they get off.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrophyBlockEntity trophy) {
            TrophyItem.trophyOf(stack).ifPresent(trophy::engrave);
        }
    }

    /**
     * Clicking one reads it out.
     * <p>A block has no tooltip, so without this the engraving is only legible by breaking the thing
     * and looking at it in the hand - which is the opposite of what a trophy on a shelf is for.
     * Anybody may read it: a trophy is meant to be shown, and there is nothing hidden on it.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TrophyBlockEntity trophy)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        TrophyComponent won = trophy.engraved();
        if (!won.isEngraved()) {
            say(player, Component.translatable("message.gathering.trophy_blank"));
            return InteractionResult.CONSUME;
        }
        if (!won.event().isEmpty()) {
            say(player, Component.literal(won.event())
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        if (!won.winner().isEmpty()) {
            say(player, Component.translatable("tooltip.gathering.trophy_won_by", won.winner())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (!won.day().isEmpty()) {
            say(player, Component.literal(won.day())
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * What one drops: itself, still engraved.
     * <p>One stack rather than a blank cup and a note beside it. The engraving is the object, so
     * breaking a trophy and putting it down again somewhere else has to be the same trophy - which
     * is also what makes it worth moving to a better shelf.
     */
    @Override
    protected java.util.List<ItemStack> getDrops(
            BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        ItemStack dropped = new ItemStack(state.getBlock().asItem());
        BlockEntity entity = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (entity instanceof TrophyBlockEntity trophy && trophy.engraved().isEngraved()) {
            dropped = TrophyItem.of(trophy.engraved());
        }
        return java.util.List.of(dropped);
    }

    /**
     * A trophy picked with the middle mouse button is the one on the shelf.
     * <p>Otherwise it hands back a blank cup, and the engraving is quietly gone in creative - where
     * somebody moving a shelf around is most likely to be.
     */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos,
            BlockState state) {
        return level.getBlockEntity(pos) instanceof TrophyBlockEntity trophy
                && trophy.engraved().isEngraved()
                ? TrophyItem.of(trophy.engraved())
                : super.getCloneItemStack(level, pos, state);
    }

    /**
     * What color to draw a placed trophy, for whichever loader is asking.
     * <p>The same rule the item uses, reading the same engraving from the block entity instead of
     * from the stack. A trophy with nothing behind it - the block in a creative preview, or a
     * moment before its entity has arrived on the client - is the blank cup's color rather than
     * white, because white through a gray texture is a cup made of nothing.
     */
    public static int tintOf(BlockState state, net.minecraft.world.level.BlockAndTintGetter level,
            BlockPos pos, int layer) {
        TrophyComponent won = level == null || pos == null ? null
                : level.getBlockEntity(pos) instanceof TrophyBlockEntity trophy
                        ? trophy.engraved()
                        : null;
        return (won == null ? TrophyComponent.BLANK.tint() : won.tint()) | 0xFF000000;
    }

    private static void say(Player player, Component line) {
        dev.gathering.server.Notices.tell(player, line);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }
}
