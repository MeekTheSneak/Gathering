package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
 * One card, under glass, for a room to look at.
 * <p>The thing a shop has by the till and a player has by the door: the card you are proudest of,
 * standing up where it can be read from across the room rather than counted in a binder. The owner asked
 * for one (2026-09-16).
 * <p>Right-click it holding a card and the card goes in; right-click it empty-handed and the card comes
 * back out. Both are the case's owner's, which is whoever put it down - a case anybody could empty is a
 * case for a card you do not mind losing, which is the opposite of what this is for. Anybody at all may
 * look, because a case is glass and looking is the whole point.
 * <p>The card goes in face up and is stored face up. Everything a case holds is sent to every client
 * that can see the block, so a face-down card in one would be a hidden identity handed to the room; a
 * card put in face down turns over on the way in rather than being refused, which is what putting a card
 * in a display case means.
 */
public class DisplayCaseBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<DisplayCaseBlock> CODEC = simpleCodec(DisplayCaseBlock::new);

    /**
     * Whether there is another case of the same kind, facing the same way, against each end.
     * <p>Named for the model rather than for the world: a case's own left is its minus-x side, which is
     * whichever way round the block happens to be turned. Two cases side by side used to show two glass
     * ends and four corner posts back to back at the join, which reads as a row of boxes; with the ends
     * dropped the glass, the lining and the lid run straight through and a row is one long case, which is
     * what a shop has. The owner asked for exactly this (2026-09-16).
     */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LEFT =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("left");
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty RIGHT =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("right");

    public DisplayCaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LEFT, false)
                .setValue(RIGHT, false)
                .setValue(FurnitureDye.FELT, net.minecraft.world.item.DyeColor.WHITE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, FurnitureDye.FELT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return joinedTo(placed, context.getLevel(), context.getClickedPos());
    }

    /**
     * A case rejoins its neighbors whenever one is put down or taken away beside it.
     * <p>Which is what makes a row close up behind a case taken out of the middle of it, rather than
     * leaving the two either side still open to a gap.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction towards, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (towards.getAxis().isHorizontal() && level instanceof net.minecraft.world.level.Level world) {
            return joinedTo(state, world, pos);
        }
        return super.updateShape(state, towards, neighbor, level, pos, neighborPos);
    }

    /**
     * The same case, told which of its ends are against another.
     * <p>The model is drawn facing one way and turned, so its own plus-x side is whichever world
     * direction that turn lands on - see the blockstate. Only a case of the same wood facing the same way
     * counts: an oak case beside a spruce one is two cases, and one turned the other way is a corner.
     */
    private static BlockState joinedTo(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state
                .setValue(RIGHT, joins(state, level, pos.relative(facing.getCounterClockWise())))
                .setValue(LEFT, joins(state, level, pos.relative(facing.getClockWise())));
    }

    private static boolean joins(BlockState state, BlockGetter level, BlockPos beside) {
        BlockState neighbor = level.getBlockState(beside);
        return neighbor.getBlock() == state.getBlock()
                && neighbor.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        // The ends stay with the block: a whole row turned together is still a row, and one turned on
        // its own rejoins nothing, which the next neighbor update works out.
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayCaseBlockEntity(pos, state);
    }

    /** Whoever puts one down owns it, and owns what goes in it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof DisplayCaseBlockEntity display) {
            display.claimFor(player.getUUID());
        }
    }

    /** A card in hand goes on show. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof net.minecraft.world.item.DyeItem) {
            // The lining takes dye like every other piece of felt in the mod.
            return FurnitureDye.use(stack, state, level, pos, player);
        }
        CardComponent card = CardItem.cardOf(stack).orElse(null);
        if (card == null) {
            // Not a card. Falls through to the empty-handed path, so clicking with a pickaxe takes the
            // card out rather than doing nothing at all.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof DisplayCaseBlockEntity display)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!display.isOwner(player.getUUID())) {
            say(player, "message.gathering.display_case_not_yours");
            return ItemInteractionResult.CONSUME;
        }
        if (!display.show(card)) {
            say(player, "message.gathering.display_case_full");
            return ItemInteractionResult.CONSUME;
        }
        // Not in creative, where the stack in hand is a supply rather than the card itself and taking one
        // would empty the menu slot somebody is holding.
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** An empty hand takes it back out. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayCaseBlockEntity display)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (display.isEmpty()) {
            say(player, "message.gathering.display_case_empty");
            return InteractionResult.CONSUME;
        }
        if (!display.isOwner(player.getUUID())) {
            say(player, "message.gathering.display_case_not_yours");
            return InteractionResult.CONSUME;
        }
        display.take().ifPresent(card -> give(player, card));
        return InteractionResult.SUCCESS;
    }

    /**
     * What one drops: itself, and whatever was in it.
     * <p>The card comes out beside the case rather than inside it, the way a jukebox gives its record
     * back. A card that travelled in the item would be a card nobody can see they are carrying.
     */
    @Override
    protected java.util.List<ItemStack> getDrops(
            BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        drops.add(new ItemStack(state.getBlock().asItem()));
        BlockEntity entity = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (entity instanceof DisplayCaseBlockEntity display) {
            display.cards().forEach(card -> drops.add(CardItem.of(card)));
        }
        return drops;
    }

    private static void give(Player player, CardComponent card) {
        ItemStack stack = CardItem.of(card);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void say(Player player, String message) {
        player.displayClientMessage(Component.translatable(message), true);
    }

    /** Flush counter-height cabinet; the felt counter surface and case lid both finish at y=15. */
    private static final VoxelShape CASE_SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CASE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CASE_SHAPE;
    }
}
