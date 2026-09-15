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
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * <p>Its look borrows vanilla's lectern until it has one of its own.
 */
public class ScorekeepersDeskBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ScorekeepersDeskBlock> CODEC = simpleCodec(ScorekeepersDeskBlock::new);

    public ScorekeepersDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
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

    // The lectern's shapes, since it has the lectern's look: its outline, its collision, and the
    // footprint that hides a neighbor's faces and stops light, so nothing behind its thin parts shows
    // a hole into the world.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> LecternBlock.SHAPE_NORTH;
            case SOUTH -> LecternBlock.SHAPE_SOUTH;
            case EAST -> LecternBlock.SHAPE_EAST;
            default -> LecternBlock.SHAPE_WEST;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LecternBlock.SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return LecternBlock.SHAPE_COMMON;
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
}
