package dev.gathering.item;

import dev.gathering.Gathering;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.service.DeckScreenHook;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
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
        if (level.isClientSide() && deckOf(stack).isPresent()) {
            // The hand, not the deck: the screen reads the live stack every frame, so an edit
            // the server applies shows up without anyone having to push a new copy at it.
            DeckScreenHook.Binding.open(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Carrying a deck and right-clicking a stack of cards puts them into the deck.
     *
     * <p>This is the bundle gesture, and only half of it. A bundle right-clicked on an empty
     * slot hands one item back; a deck never does. Taking a card out is done from the deck
     * list, where you can see the card's name before you decide - pulling an unseen card off
     * the top of a shuffled-looking pile is a different and much worse interaction, and in a
     * collection game it is one you would do by accident.
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (!isInsertClick(stack, action) || !slot.allowModification(player)) {
            return false;
        }
        ItemStack cards = slot.getItem();
        int room = roomFor(stack, cards);
        if (room <= 0) {
            // Still ours to handle: a card stack should not swap itself into the cursor just
            // because the deck is full or holds something else.
            return insertable(cards);
        }
        insert(stack, slot.safeTake(room, room, player));
        playAssembleSound(player);
        return true;
    }

    /** The same gesture the other way round: cards on the cursor, deck in the slot. */
    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (!isInsertClick(stack, action) || !slot.allowModification(player)) {
            return false;
        }
        int room = roomFor(stack, other);
        if (room <= 0) {
            return insertable(other);
        }
        insert(stack, other.split(room));
        playAssembleSound(player);
        return true;
    }

    /**
     * The click of a card going into a deck, or of two cards becoming one.
     *
     * <p>The vanilla bundle sound, because this is the vanilla bundle gesture and a silent
     * one reads as a click that did not register. {@code Player#playSound} excludes the
     * player it is given, so running this on both sides plays it exactly once.
     */
    static void playAssembleSound(Player player) {
        player.playSound(
                SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
    }

    private static boolean isInsertClick(ItemStack deck, ClickAction action) {
        // A deck is edited one at a time; a stack of two decks has no single deck to edit.
        return deck.getCount() == 1 && action == ClickAction.SECONDARY && deckOf(deck).isPresent();
    }

    /** Only real cards go in - a blank creative card carries no identity to store. */
    private static boolean insertable(ItemStack cards) {
        return cards.getItem() instanceof CardItem && CardItem.cardOf(cards).isPresent();
    }

    private static int roomFor(ItemStack deck, ItemStack cards) {
        if (!insertable(cards)) {
            return 0;
        }
        int free = DeckComponent.MAX_CARDS - deckOf(deck).map(DeckComponent::totalCards).orElse(0);
        return Math.min(cards.getCount(), Math.max(0, free));
    }

    private static void insert(ItemStack deck, ItemStack cards) {
        Optional<DeckComponent> held = deckOf(deck);
        Optional<CardComponent> card = CardItem.cardOf(cards);
        if (held.isEmpty() || card.isEmpty()) {
            return;
        }
        DeckComponent updated = held.get();
        for (int copy = 0; copy < cards.getCount(); copy++) {
            Optional<DeckComponent> next =
                    updated.withAdded(DeckComponent.Section.MAINBOARD, card.get().faceUp());
            if (next.isEmpty()) {
                break;
            }
            updated = next.get();
        }
        deck.set(GatheringComponents.DECK.get(), updated);
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
