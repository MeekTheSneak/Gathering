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
 * A block says nothing in the hand, and the things that carry information still do.
 * <p>This test used to assert the opposite: that every block explained itself. The owner's call
 * (2026-09-18) is that it was too much - a line under a chair, a shop counter, a table and a coin
 * is a line under everything, and a tooltip under everything is a tooltip nobody reads under the
 * two that actually carry something. A card, a deck and a sealed product say what they hold,
 * because that cannot be seen any other way; a block is learned by placing it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockTooltipGameTest {

    private BlockTooltipGameTest() {
    }

    @GameTest(template = "empty")
    public static void ablockSaysNothingInTheHand(GameTestHelper helper) {
        List<String> talkative = new ArrayList<>();
        int blocks = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem)
                    || !Gathering.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                continue;
            }
            blocks++;
            List<Component> lines = new ArrayList<>();
            item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
            if (!lines.isEmpty()) {
                talkative.add(BuiltInRegistries.ITEM.getKey(item) + " says " + lines);
            }
        }
        if (blocks < 6) {
            helper.fail("only " + blocks + " of this mod's blocks were found to check");
            return;
        }
        if (!talkative.isEmpty()) {
            helper.fail("these blocks explain themselves in the hand and should not: " + talkative);
            return;
        }
        helper.succeed();
    }

    /** And the things that do carry information still explain themselves. */
    @GameTest(template = "empty")
    public static void acardAndaDeckStillSayWhatTheyHold(GameTestHelper helper) {
        // Real ones, with something in them: an empty stack of either carries no component and has
        // nothing to say, which is correct and is not what this is about.
        dev.gathering.item.CardComponent card = dev.gathering.item.CardComponent.of(
                dev.gathering.core.card.CardIdentity.ofPrinting(java.util.UUID.randomUUID()));
        List<ItemStack> holding = List.of(
                dev.gathering.item.CardItem.of(card),
                dev.gathering.item.DeckItem.of(new dev.gathering.item.DeckComponent(
                        "A deck", "", java.util.Optional.empty(),
                        List.of(card), List.of(), List.of())));
        for (ItemStack stack : holding) {
            List<Component> lines = new ArrayList<>();
            stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
            if (lines.isEmpty()) {
                helper.fail(BuiltInRegistries.ITEM.getKey(stack.getItem())
                        + " says nothing about what it holds");
                return;
            }
        }
        helper.succeed();
    }
}
