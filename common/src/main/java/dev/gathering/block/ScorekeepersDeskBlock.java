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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a tournament is run from: the desk its players come to and its boards read.
 * <p>The host uses it while hosting, and it becomes that tournament's desk - and the place signing up
 * happens, so a room has one obvious spot to go to. After that anybody using it is shown that
 * tournament: to sign up, check in, see who they are playing and how it stands. With Create installed,
 * a Display Link against it reads the tournament onto a board, showing whichever of the standings,
 * pairings, round and clock, final places, prizes or sign-ups it is set to.
 * <p>Another host takes a desk over by using it twice: the first use says whose desk it is, and a
 * second soon after runs their tournament here. Not by sneaking, which vanilla gives to whatever is
 * in the hand - and a player signing up for constructed is holding a deck.
 * <p>Its white writing surface accepts dye.
 */
public class ScorekeepersDeskBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ScorekeepersDeskBlock> CODEC = simpleCodec(ScorekeepersDeskBlock::new);

    public ScorekeepersDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FurnitureDye.FELT, net.minecraft.world.item.DyeColor.WHITE).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, FurnitureDye.FELT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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

    private static final VoxelShape BODY = Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(4, 2, 4, 12, 11, 12),
            Block.box(1, 11, 1, 15, 13, 15));
    private static final VoxelShape NORTH = Shapes.or(BODY, Block.box(2, 12, 12, 14, 16, 14));
    private static final VoxelShape EAST = Shapes.or(BODY, Block.box(2, 12, 2, 4, 16, 14));
    private static final VoxelShape SOUTH = Shapes.or(BODY, Block.box(2, 12, 2, 14, 16, 4));
    private static final VoxelShape WEST = Shapes.or(BODY, Block.box(12, 12, 2, 14, 16, 14));

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            default -> WEST;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return BODY;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScorekeepersDeskBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (ticking, pos, ticked, entity) -> {
            if (ticking instanceof net.minecraft.server.level.ServerLevel server && entity instanceof ScorekeepersDeskBlockEntity desk) {
                ScorekeepersDeskBlockEntity.serverTick(server, pos, desk);
            }
        };
    }

    /**
     * A comparator beside the desk gives full strength while the round its tournament is playing has
     * had time called, and nothing otherwise - so a bell, a lamp, or with Create anything at all, can
     * say "time" across the hall as the clock runs out. Checked when the desk refreshes, once a second.
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ScorekeepersDeskBlockEntity desk && desk.timeCalled() ? 15 : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            dev.gathering.server.events.Events.useDesk(server, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            // Signing up moves with the desk: a registration point left where a desk used to be is a
            // spot in the middle of the floor players are told to walk to.
            dev.gathering.server.events.Events.deskRemoved(server, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        return FurnitureDye.use(stack, state, level, pos, player);
    }

}
