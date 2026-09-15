package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Every block this mod adds says what it is for while it is still in a hand: none of them is a
 * plain building block, and one that says nothing is found out by placing it and guessing.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockTooltipGameTest {

    private BlockTooltipGameTest() {
    }

    @GameTest(template = "empty")
    public static void everyBlockSaysWhatItIsFor(GameTestHelper helper) {
        List<String> silent = new ArrayList<>();
        int blocks = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem) || !Gathering.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                continue;
            }
            blocks++;
            List<Component> lines = new ArrayList<>();
            item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
            if (lines.isEmpty()) {
                silent.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        if (blocks < 6) {
            helper.fail("only " + blocks + " of this mod's blocks were found to check");
            return;
        }
        if (!silent.isEmpty()) {
            helper.fail("these blocks say nothing about what they are for: " + silent);
            return;
        }
        helper.succeed();
    }
}
