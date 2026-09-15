package dev.gathering.item;

import dev.gathering.platform.Platform;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * A block that says what it is for while it is still in a hand.
 * <p>A block with something to do and nothing to say about it is found out by placing it and
 * clicking it, and a player who places a Scorekeeper's Desk before hosting anything learns only that
 * it is a lectern. The first line is the use worth knowing, in the table's tooltip style; the rest
 * are the gestures after it.
 */
public class DescribedBlockItem extends BlockItem {

    private final List<String> lines;
    private final String withCreate;

    /** @param lines lang keys, the use first */
    public DescribedBlockItem(Block block, Properties properties, List<String> lines) {
        this(block, properties, lines, "");
    }

    /**
     * @param withCreate a last line, said only when Create is installed: a gesture that needs a
     *                   mod the server does not have is a dead end to be told about
     */
    public DescribedBlockItem(Block block, Properties properties, List<String> lines, String withCreate) {
        super(block, properties);
        this.lines = List.copyOf(lines);
        this.withCreate = withCreate;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (int index = 0; index < lines.size(); index++) {
            tooltip.add(Component.translatable(lines.get(index))
                    .withStyle(index == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
        if (!withCreate.isEmpty() && Platform.get().isModLoaded("create")) {
            tooltip.add(Component.translatable(withCreate).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
