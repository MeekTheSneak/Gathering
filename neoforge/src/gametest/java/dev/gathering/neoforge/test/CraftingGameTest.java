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
 * <p>Asked of the recipe manager rather than of the files, because a recipe that is present
 * and does not resolve - a tag that is empty, an item id that moved - is a recipe nobody can
 * craft, and the file looks perfectly correct either way.
 * <p>The collection block is the one this was written for. It had no recipe at all: it is
 * where every card a player owns lives, and the only way to a first one was the creative
 * menu.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftingGameTest {

    @GameTest(template = "empty")
    public static void aBinderCanBeCrafted(GameTestHelper helper) {
        // Planks at its corners since the furniture comes in every wood: which wood they are is which
        // collection comes out, and the plain one is dark oak.
        crafts(helper, GatheringContent.COLLECTION_ITEM.get(), List.of(
                new ItemStack(Items.DARK_OAK_PLANKS), new ItemStack(Items.LEATHER), new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.LEATHER), new ItemStack(Items.CHEST), new ItemStack(Items.LEATHER),
                new ItemStack(Items.DARK_OAK_PLANKS), new ItemStack(Items.LEATHER), new ItemStack(Items.DARK_OAK_PLANKS)));
    }

    @GameTest(template = "empty")
    public static void aTableCanBeCrafted(GameTestHelper helper) {
        crafts(helper, GatheringContent.TABLE_ITEM.get(), List.of(
                new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.DARK_OAK_PLANKS), new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.DARK_OAK_PLANKS), ItemStack.EMPTY, new ItemStack(Items.DARK_OAK_PLANKS)));
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

    /**
     * The shop counter used to be the one block a player could not make: it was the village's, taken rather
     * than crafted. The owner asked for a recipe for every block (2026-09-16), and `tools/recipecheck.py`
     * now says so for all of them; this is the one that goes through a crafting grid.
     */
    @GameTest(template = "empty")
    public static void aShopCounterCanBeCrafted(GameTestHelper helper) {
        crafts(helper, GatheringContent.SHOP_COUNTER_ITEM.get(), List.of(
                new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.DARK_OAK_PLANKS), new ItemStack(Items.PAPER),
                new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.DARK_OAK_PLANKS), new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.DARK_OAK_PLANKS)));
    }

    @GameTest(template = "empty")
    public static void aScorekeepersDeskCanBeCrafted(GameTestHelper helper) {
        crafts(helper, GatheringContent.SCOREKEEPERS_DESK_ITEM.get(), List.of(
                new ItemStack(Items.PAPER), new ItemStack(Items.BOOK), new ItemStack(Items.PAPER),
                new ItemStack(Items.DARK_OAK_PLANKS), new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.DARK_OAK_PLANKS), ItemStack.EMPTY, new ItemStack(Items.DARK_OAK_PLANKS)));
    }

    @GameTest(template = "empty")
    public static void aChairCanBeCrafted(GameTestHelper helper) {
        crafts(helper, GatheringContent.CHAIR_ITEM.get(), List.of(
                new ItemStack(Items.STICK), ItemStack.EMPTY, ItemStack.EMPTY,
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.STICK), ItemStack.EMPTY, new ItemStack(Items.STICK)));
    }

    /**
     * The wood a player lays out is the wood they get. Every one of these used to take any planks and the
     * chair took a mixture; now each wood is its own block, the way a vanilla door is, and the plain ids are
     * the woods they were drawn in - dark oak, and oak for the chair.
     */
    @GameTest(template = "empty")
    public static void theWoodYouLayOutIsTheWoodYouGet(GameTestHelper helper) {
        crafts(helper, item("spruce_table"), List.of(
                new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.SPRUCE_PLANKS), new ItemStack(Items.SPRUCE_PLANKS), new ItemStack(Items.SPRUCE_PLANKS),
                new ItemStack(Items.SPRUCE_PLANKS), ItemStack.EMPTY, new ItemStack(Items.SPRUCE_PLANKS)));
        crafts(helper, item("cherry_chair"), List.of(
                new ItemStack(Items.STICK), ItemStack.EMPTY, ItemStack.EMPTY,
                new ItemStack(Items.CHERRY_PLANKS), new ItemStack(Items.CHERRY_PLANKS), new ItemStack(Items.CHERRY_PLANKS),
                new ItemStack(Items.STICK), ItemStack.EMPTY, new ItemStack(Items.STICK)));
        crafts(helper, item("warped_scorekeepers_desk"), List.of(
                new ItemStack(Items.PAPER), new ItemStack(Items.BOOK), new ItemStack(Items.PAPER),
                new ItemStack(Items.WARPED_PLANKS), new ItemStack(Items.WARPED_PLANKS), new ItemStack(Items.WARPED_PLANKS),
                new ItemStack(Items.WARPED_PLANKS), ItemStack.EMPTY, new ItemStack(Items.WARPED_PLANKS)));
        crafts(helper, item("bamboo_collection"), List.of(
                new ItemStack(Items.BAMBOO_PLANKS), new ItemStack(Items.LEATHER), new ItemStack(Items.BAMBOO_PLANKS),
                new ItemStack(Items.LEATHER), new ItemStack(Items.CHEST), new ItemStack(Items.LEATHER),
                new ItemStack(Items.BAMBOO_PLANKS), new ItemStack(Items.LEATHER), new ItemStack(Items.BAMBOO_PLANKS)));
    }

    /** One wood's furniture, by id. */
    private static Item item(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Gathering.id(id));
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
