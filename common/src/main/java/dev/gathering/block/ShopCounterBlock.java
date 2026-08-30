package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The counter a card shop's keeper works behind.
 *
 * <p>A workstation and nothing else. Put one down and an unemployed villager takes the job the
 * same way one takes a lectern or a grindstone; break it and they lose it. There is no screen,
 * no inventory and nothing to right-click, because the thing you came to do is talk to the
 * person standing behind it.
 *
 * <p>It faces the way it was placed so a shop can be laid out - a counter with its display
 * side turned to the wall is a counter that looks like a mistake.
 */
public class ShopCounterBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<ShopCounterBlock> CODEC = simpleCodec(ShopCounterBlock::new);

    public ShopCounterBlock(Properties properties) {
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
}
