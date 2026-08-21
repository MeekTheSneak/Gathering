package dev.gathering.item;

import dev.gathering.Gathering;
import dev.gathering.registry.GatheringComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A sleeved deck, produced by decklist import and bound to the player who imported it.
 *
 * <p>The deck is the thing you carry to a table. Cards inside it never become inventory
 * items during a session; hands are GUI-only, always.
 */
public class DeckItem extends Item {

    public DeckItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(DeckComponent deck) {
        ItemStack stack = new ItemStack(GatheringContent.DECK.get());
        stack.set(GatheringComponents.DECK.get(), deck);
        return stack;
    }

    public static Optional<DeckComponent> deckOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(GatheringComponents.DECK.get()));
    }

    @Override
    public Component getName(ItemStack stack) {
        return deckOf(stack)
                .map(DeckComponent::name)
                .filter(name -> !name.isBlank())
                .<Component>map(Component::literal)
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        deckOf(stack).ifPresent(deck -> {
            tooltip.add(Component.translatable(
                            "tooltip." + Gathering.MOD_ID + ".deck_size", deck.deckSize())
                    .withStyle(ChatFormatting.GRAY));
            if (!deck.sideboard().isEmpty()) {
                tooltip.add(Component.translatable(
                                "tooltip." + Gathering.MOD_ID + ".sideboard_size", deck.sideboard().size())
                        .withStyle(ChatFormatting.GRAY));
            }
            for (CardComponent commander : deck.commanders()) {
                dev.gathering.service.CardNameLookup.Binding.current().nameOf(commander)
                        .ifPresent(name -> tooltip.add(Component.literal(name).withStyle(ChatFormatting.GOLD)));
            }
        });
    }
}
