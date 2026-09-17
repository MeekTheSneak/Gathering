package dev.gathering.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * What a card shop takes.
 * <p>The mod's own currency, and the reason the shop is not a vending machine. An emerald is
 * farmed - a trading hall makes them by the stack without anybody leaving the village - so a
 * shop priced in emeralds was the cheapest path to a collection and exploring was a flourish.
 * A coin is found in a chest, somewhere, and cannot be made, smelted, traded for or grown.
 * <p>It says both of those things in the hand, because neither is guessable from a coin: what
 * it is for, and that there is no recipe to go and look up.
 */
public final class ManaCoinItem extends Item {

    public ManaCoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gathering.mana_coin_spend")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gathering.mana_coin_find")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
