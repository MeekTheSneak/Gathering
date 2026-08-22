package dev.gathering.item;

import dev.gathering.Gathering;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.service.DeckScreenHook;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

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

    /**
     * Right-click to look inside.
     *
     * <p>Opens on the client from the stack's own data, so there is no round trip just to see
     * what a deck holds. The screen asks the server for card metadata separately, because the
     * stack knows which printings it contains but not what they are called.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            deckOf(stack).ifPresent(DeckScreenHook.Binding::open);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
            // The player's own note first, because it is the thing they wrote to tell decks
            // apart on a shelf.
            if (deck.hasDescription()) {
                tooltip.add(Component.literal(deck.description()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
            }
            for (CardComponent commander : deck.commanders()) {
                dev.gathering.service.CardNameLookup.Binding.current().nameOf(commander)
                        .ifPresent(name -> tooltip.add(Component.literal(name).withStyle(ChatFormatting.GOLD)));
            }
            tooltip.add(Component.translatable(
                            "tooltip." + Gathering.MOD_ID + ".deck_size", deck.deckSize())
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (!deck.sideboard().isEmpty()) {
                tooltip.add(Component.translatable(
                                "tooltip." + Gathering.MOD_ID + ".sideboard_size", deck.sideboard().size())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".open_deck")
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
