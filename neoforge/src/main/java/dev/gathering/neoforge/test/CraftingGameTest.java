package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Everything this mod adds can be got hold of without creative mode.
 *
 * <p>Asked of the recipe manager rather than of the files, because a recipe that is present
 * and does not resolve - a tag that is empty, an item id that moved - is a recipe nobody can
 * craft, and the file looks perfectly correct either way.
 *
 * <p>The collection block is the one this was written for. It had no recipe at all: it is
 * where every card a player owns lives, and the only way to a first one was the creative
 * menu.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftingGameTest {

    @GameTest(template = "empty")
    public static void aBinderCanBeCrafted(GameTestHelper helper) {
        crafts(helper, GatheringContent.COLLECTION_ITEM.get(), List.of(
                ItemStack.EMPTY, new ItemStack(Items.LEATHER), ItemStack.EMPTY,
                new ItemStack(Items.LEATHER), new ItemStack(Items.CHEST), new ItemStack(Items.LEATHER),
                ItemStack.EMPTY, new ItemStack(Items.LEATHER), ItemStack.EMPTY));
    }

    @GameTest(template = "empty")
    public static void aTableCanBeCrafted(GameTestHelper helper) {
        crafts(helper, GatheringContent.TABLE_ITEM.get(), List.of(
                new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS)));
    }

    @GameTest(template = "empty")
    public static void aStoneTableCanBeCrafted(GameTestHelper helper) {
        crafts(helper, GatheringContent.COBBLESTONE_TABLE_ITEM.get(), List.of(
                new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.COBBLESTONE),
                new ItemStack(Items.COBBLESTONE),
                new ItemStack(Items.COBBLESTONE), ItemStack.EMPTY, new ItemStack(Items.COBBLESTONE)));
    }

    /** Lays the nine stacks out on a bench and checks what comes off it. */
    private static void crafts(GameTestHelper helper, Item wanted, List<ItemStack> grid) {
        NonNullList<ItemStack> slots = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int slot = 0; slot < grid.size(); slot++) {
            slots.set(slot, grid.get(slot));
        }
        CraftingInput input = CraftingInput.of(3, 3, slots);
        ItemStack made = helper.getLevel().getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(found -> found.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);

        if (!made.is(wanted)) {
            helper.fail("That layout made " + made + " rather than " + wanted);
            return;
        }
        helper.succeed();
    }
}
