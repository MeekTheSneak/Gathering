package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The counter a card shop's keeper works behind.
 * <p>A workstation and nothing else. Put one down and an unemployed villager takes the job the
 * same way one takes a lectern or a grindstone; break it and they lose it. There is no screen or inventory. A dye recolors its fabric; trade with the keeper.
 * <p>It faces the way it was placed so a shop can be laid out - a counter with its display
 * side turned to the wall is a counter that looks like a mistake.
 */
public class ShopCounterBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<ShopCounterBlock> CODEC = simpleCodec(ShopCounterBlock::new);

    public ShopCounterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FurnitureDye.FELT, net.minecraft.world.item.DyeColor.WHITE).setValue(FACING, Direction.NORTH));
    }

    private static final VoxelShape BODY = Block.box(0, 0, 0, 16, 15, 16);
    private static final VoxelShape NORTH = Shapes.or(BODY, Block.box(0, 15, 15, 16, 16, 16));
    private static final VoxelShape EAST = Shapes.or(BODY, Block.box(0, 15, 0, 1, 16, 16));
    private static final VoxelShape SOUTH = Shapes.or(BODY, Block.box(0, 15, 0, 16, 16, 1));
    private static final VoxelShape WEST = Shapes.or(BODY, Block.box(15, 15, 0, 16, 16, 16));

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
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
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, FurnitureDye.FELT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Toward whoever put it down, the way a furnace faces.
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
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        return FurnitureDye.use(stack, state, level, pos, player);
    }

}
