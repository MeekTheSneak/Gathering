package dev.gathering.block;

import dev.gathering.Gathering;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places a whole table, or none of it.
 *
 * <p>The block clicked becomes the table's north-west corner and the table extends east and
 * south from it. All four quarters go down in one go: a table that placed its first quarter
 * and then discovered there was no room for the fourth would leave a stump behind, and the
 * player would have to work out which of the blocks in front of them was the wrong one.
 */
public class TableBlockItem extends BlockItem {

    public TableBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        BlockPos origin = context.getClickedPos();
        if (!TableBlock.canPlaceAt(context, origin)) {
            // Say why. A table that simply refuses to go down is a player clicking the same
            // spot four times and then putting the mod away: the two reasons it can refuse -
            // the cluster is already as big as one gets, and tables join in a line - are both
            // things somebody would move one block to the side for if they were told.
            String why = TableClusters.whyItWouldNotFit(context.getLevel(), origin);
            if (!why.isEmpty() && context.getPlayer() != null
                    && !context.getLevel().isClientSide()) {
                context.getPlayer().sendSystemMessage(Component.translatable(why));
            }
            return false;
        }

        Level level = context.getLevel();
        for (TablePart part : TablePart.values()) {
            level.setBlock(
                    part.offsetFrom(origin),
                    state.setValue(TableBlock.PART, part),
                    Block.UPDATE_ALL);
        }
        return true;
    }

    /**
     * How to use it, on the thing you are holding.
     *
     * <p>Two gestures and neither of them is guessable: a table you right-click with a deck
     * sits you down and starts a game, and one you crouch at asks what kind of game first.
     * Somebody who has just crafted this has no way to find either out, and a mod whose first
     * minute is spent clicking a block that does nothing is a mod that gets uninstalled in its
     * second minute.
     */
    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".table_play")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".table_format")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".table_size")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
