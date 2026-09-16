package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Material-specific furniture sharing the original chair's seat and table behavior. */
public final class MaterialChairBlock extends ChairBlock {
    public static final MapCodec<MaterialChairBlock> CODEC = simpleCodec(MaterialChairBlock::new);

    public MaterialChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FurnitureDye.FELT, DyeColor.WHITE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FurnitureDye.FELT);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return FurnitureDye.use(stack, state, level, pos, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape[] shapes = state.is(GatheringContent.COBBLESTONE_CHAIR.get()) ? COBBLESTONE
                : state.is(GatheringContent.BLACKSTONE_CHAIR.get()) ? BLACKSTONE : CRYING_OBSIDIAN;
        return shapes[switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    // Generated from the same cuboids as the models; no model loading or shape unions per frame.
    private static final VoxelShape[] COBBLESTONE = rotated(Shapes.or(
            Block.box(2, 0, 2, 5, 8, 14),
            Block.box(11, 0, 2, 14, 8, 14),
            Block.box(1, 8, 1, 15, 9, 15),
            Block.box(2, 9, 2, 14, 10, 13),
            Block.box(1, 9, 13, 15, 16, 15)));
    private static final VoxelShape[] BLACKSTONE = rotated(Shapes.or(
            Block.box(1, 0, 1, 5, 2, 15),
            Block.box(11, 0, 1, 15, 2, 15),
            Block.box(2, 2, 2, 4, 8, 14),
            Block.box(12, 2, 2, 14, 8, 14),
            Block.box(1, 8, 1, 15, 9, 15),
            Block.box(3, 9, 2, 13, 10, 13),
            Block.box(1, 9, 3, 3, 12, 13),
            Block.box(13, 9, 3, 15, 12, 13),
            Block.box(1, 9, 13, 15, 15, 15),
            Block.box(3, 15, 13, 13, 16, 15)));
    private static final VoxelShape[] CRYING_OBSIDIAN = rotated(Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(6, 2, 6, 10, 8, 10),
            Block.box(1, 8, 1, 15, 9, 15),
            Block.box(2, 9, 2, 14, 10, 13),
            Block.box(2, 9, 13, 5, 16, 15),
            Block.box(11, 9, 13, 14, 16, 15),
            Block.box(5, 14, 13, 11, 16, 15)));

    private static VoxelShape[] rotated(VoxelShape north) {
        VoxelShape[] result = new VoxelShape[4];
        result[0] = north;
        for (int i = 1; i < result.length; i++) {
            VoxelShape[] next = {Shapes.empty()};
            result[i-1].forAllBoxes((x0,y0,z0,x1,y1,z1) -> next[0] = Shapes.or(next[0],
                    Shapes.box(1-z1,y0,x0,1-z0,y1,x1)));
            result[i] = next[0];
        }
        return result;
    }
}
