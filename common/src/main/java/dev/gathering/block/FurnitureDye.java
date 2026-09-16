package dev.gathering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Shared fabric color for single-block furniture. Block states handle save, sync and mesh rebuilds. */
public final class FurnitureDye {
    public static final EnumProperty<DyeColor> FELT = EnumProperty.create("felt", DyeColor.class);

    private FurnitureDye() {}

    public static ItemInteractionResult use(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
        // Browsing a public collection does not grant the right to recolor its cabinet.
        if (level.getBlockEntity(pos) instanceof CollectionBlockEntity collection
                && !collection.rights().isOwner(player.getUUID())) {
            return ItemInteractionResult.FAIL;
        }
        if (state.getValue(FELT) != dye.getDyeColor()
                && level.setBlock(pos, state.setValue(FELT, dye.getDyeColor()), Block.UPDATE_ALL)
                && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    public static int tint(BlockState state, int tintIndex) {
        if (tintIndex != 0 || !state.hasProperty(FELT) || state.getValue(FELT) == DyeColor.WHITE) {
            return 0xFFFFFF;
        }
        return state.getValue(FELT).getTextureDiffuseColor() & 0xFFFFFF;
    }
}
