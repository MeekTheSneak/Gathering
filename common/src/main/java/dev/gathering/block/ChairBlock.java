package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A chair: set it against a table's edge and sit in it to take that seat.
 * <p>Asked for by the owner after another card mod, Charta, seats its players on chairs: a table
 * people stand at is a counter, and a chair says which side of the table is whose without anybody
 * reading it. Facing the way the player was looking when they put it down, which for somebody
 * standing at a table is the table.
 * <p>What sitting does is {@link Chairs}'s. This is only the furniture: where it faces, what it is
 * shaped like, and that using it asks to sit.
 */
public class ChairBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<ChairBlock> CODEC = simpleCodec(ChairBlock::new);

    /** How high the seat is, in blocks: where somebody sitting in it rests. */
    public static final double SEAT_HEIGHT = 10.0 / 16.0;

    // The seat and the back, facing north - the back along the south edge - and turned for the rest.
    private static final VoxelShape SEAT = Block.box(1, 0, 1, 15, 10, 15);
    private static final VoxelShape NORTH = Shapes.or(SEAT, Block.box(1, 10, 13, 15, 16, 15));
    private static final VoxelShape SOUTH = Shapes.or(SEAT, Block.box(1, 10, 1, 15, 16, 3));
    private static final VoxelShape EAST = Shapes.or(SEAT, Block.box(1, 10, 1, 3, 16, 15));
    private static final VoxelShape WEST = Shapes.or(SEAT, Block.box(13, 10, 1, 15, 16, 15));

    public ChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FurnitureDye.FELT, net.minecraft.world.item.DyeColor.WHITE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FurnitureDye.FELT);
    }

    /**
     * Dye recolors the cushion, as it does on the stone chairs.
     * <p>The wooden chair had a bare plank seat while its three stone cousins had wool ones you could
     * dye; the owner asked for the same cushion on all of them (2026-09-16).
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return FurnitureDye.use(stack, state, level, pos, player);
    }

    /** Facing where the player was looking: put down by somebody at a table, it faces the table. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sitting) {
            Chairs.sit(sitting, pos, state);
        }
        return InteractionResult.CONSUME;
    }

    /** Whoever is sitting in a chair that is taken away stands up; see {@link ChairSeat}. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replaced, boolean moved) {
        if (!level.isClientSide() && !state.is(replaced.getBlock())) {
            Chairs.emptied(level, pos);
        }
        super.onRemove(state, level, pos, replaced, moved);
    }
}
