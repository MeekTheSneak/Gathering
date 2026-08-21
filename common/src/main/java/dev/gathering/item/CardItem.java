package dev.gathering.item;

import dev.gathering.Gathering;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.service.CardNameLookup;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * One card, as an item.
 *
 * <p>Carries exactly one data component - {@link CardComponent} - and derives everything
 * else. A stack of these is a pile of cards; a card with no component is a blank, which is
 * what a creative-mode card looks like before anything is written on it.
 */
public class CardItem extends Item {

    public CardItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(CardComponent card) {
        ItemStack stack = new ItemStack(GatheringContent.CARD.get());
        stack.set(GatheringComponents.CARD.get(), card);
        return stack;
    }

    public static Optional<CardComponent> cardOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(GatheringComponents.CARD.get()));
    }

    @Override
    public Component getName(ItemStack stack) {
        return cardOf(stack)
                .flatMap(card -> CardNameLookup.Binding.current().nameOf(card))
                .<Component>map(Component::literal)
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        cardOf(stack).ifPresent(card -> {
            if (card.foil()) {
                tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".foil")
                        .withStyle(ChatFormatting.AQUA));
            }
            if (flag.isAdvanced()) {
                card.scryfallId().ifPresent(id -> tooltip.add(
                        Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY)));
                card.customId().ifPresent(id -> tooltip.add(
                        Component.literal(id).withStyle(ChatFormatting.DARK_GRAY)));
            }
        });
        // Attribution where card data appears, per the Scryfall API guidelines.
        tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".scryfall_attribution")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
