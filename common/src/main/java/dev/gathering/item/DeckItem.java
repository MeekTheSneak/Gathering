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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
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
 * <p>The deck is the thing you carry to a table. Cards inside it never become inventory
 * items during a session; hands are GUI-only, always.
 */
public class DeckItem extends Item {

    public DeckItem(Properties properties) {
        super(properties);
    }

    /**
     * A deck item, with a handle minted for it.
     * <p>Every deck in this mod becomes an item here, which is why the handle is minted here:
     * one place, and a deck that exists has one. See {@link GatheringComponents#DECK_HANDLE}
     * for what it is for - in short, the owner's real decklist travels separately from the
     * item and the client has to be able to say which item a list is about.
     */
    public static ItemStack of(DeckComponent deck) {
        ItemStack stack = new ItemStack(GatheringContent.DECK.get());
        stack.set(GatheringComponents.DECK.get(), deck);
        stack.set(GatheringComponents.DECK_HANDLE.get(), java.util.UUID.randomUUID());
        return stack;
    }

    /**
     * Which deck this stack is, if it says.
     * <p>Empty for a deck made before handles existed, or one built by something that set the
     * component directly. Both are answered the same way by everything that asks: fall back to
     * what is on the item, which is the counts without the list.
     */
    public static Optional<java.util.UUID> handleOf(ItemStack stack) {
        return stack == null
                ? Optional.empty()
                : Optional.ofNullable(stack.get(GatheringComponents.DECK_HANDLE.get()));
    }

    /**
     * The deck in this hand as its owner sees it: the real list where the server has sent it.
     * <p>What is on the item is the public copy - a name, a color, sleeves, commanders and a
     * thickness - so anything that lists the cards asks for this instead. Falls back to the
     * item's own copy, which is right for the frame or two before the first push lands and
     * for anybody who is not the owner.
     * <p>Client side. On the server the stack itself is the whole deck.
     */
    public static Optional<DeckComponent> contentsOf(
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (player == null || hand == null) {
            return Optional.empty();
        }
        ItemStack stack = player.getItemInHand(hand);
        Optional<DeckComponent> onTheItem = deckOf(stack);
        if (onTheItem.isEmpty() || !player.level().isClientSide()) {
            return onTheItem;
        }
        // By which deck it is, not by what it looks like. This used to accept the cached list
        // when its name and card count matched the item's, and two sixty-card decks both
        // called "Deck" match each other exactly - so somebody carrying two of those could be
        // shown one list while holding the other until the next push landed.
        return handleOf(stack)
                .flatMap(dev.gathering.client.ClientHeldDeck::of)
                .or(() -> onTheItem);
    }

    public static Optional<DeckComponent> deckOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(GatheringComponents.DECK.get()));
    }

    /**
     * What color to draw the box.
     * <p>A shelf of decks is a row of identical objects and the name is a hover away, so the
     * box carries its own color and you find your deck by looking rather than by picking
     * things up. See {@code DeckColors} for where the color comes from.
     * <p>White is "leave it alone", which is what the texture already is - so a deck built
     * before boxes had a color, or one a test made without one, draws exactly as it did.
     * <p>Read by both loaders' item color handlers. No client class is named here, so this
     * stays a plain question about a stack.
     */
    public static int tintOf(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return 0xFFFFFFFF;
        }
        return deckOf(stack).flatMap(DeckComponent::color).orElse(0xFFFFFFFF);
    }

    /**
     * Right-click to look inside.
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
     * Sneak and right-click a collection to pour the deck back into it.
     * <p>On the item rather than on the block, because that is where sneaking sends a
     * right-click: vanilla skips block interaction entirely when somebody is crouching with
     * something in hand. Which makes the two gestures fall out of the game's own rules -
     * right-click a collection holding a deck and it opens so you can sleeve into it, crouch
     * and the deck goes back in. Dissolving is not something to do by accident.
     */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos())
                instanceof dev.gathering.block.CollectionBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        return dev.gathering.server.CollectionView.dissolve(
                        player, context.getClickedPos(), context.getItemInHand(),
                        context.getHand())
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    /**
     * Carrying a deck and right-clicking a stack of cards puts them into the deck.
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
     * <p>The vanilla bundle sound, because this is the vanilla bundle gesture and a silent
     * one reads as a click that did not register. {@code Player#playSound} excludes the
     * player it is given, so running this on both sides plays it exactly once.
     */
    public static void playAssembleSound(Player player) {
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
        // Where the card has been goes in with it. A story lives on the item, and sleeving a
        // card used to leave the item behind - so the pack it came out of, the trade it came
        // through and the game it was won in were lost the first time it was played with.
        // The deck keeps them and hands one back to whichever copy is taken out again.
        StoryComponent story = cards.get(GatheringComponents.STORY.get());
        for (int copy = 0; copy < cards.getCount(); copy++) {
            Optional<DeckComponent> next =
                    updated.withAdded(DeckComponent.Section.MAINBOARD, card.get().faceUp());
            if (next.isEmpty()) {
                break;
            }
            updated = next.get();
            if (story != null) {
                updated = updated.keeping(card.get(), story.story());
            }
        }
        deck.set(GatheringComponents.DECK.get(), updated);
    }

    /**
     * An empty deck is not a thing you own.
     * <p>The edit that empties one already removes it, so this is the backstop for every
     * other way a deck can end up with nothing in it - a command, a creative click, an older
     * save. Checked on the server only, because the client would just have it reappear on
     * the next sync.
     * <p>A deck item with no component at all is left alone: that is the creative-menu deck,
     * which has never held anything and is not empty so much as blank.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide()) {
            return;
        }
        if (deckOf(stack).filter(DeckComponent::isEmpty).isPresent()) {
            stack.setCount(0);
            return;
        }
        if (entity instanceof net.minecraft.server.level.ServerPlayer holder) {
            tellTheOwner(holder, stack);
        }
    }

    /**
     * Which of a player's two hands is holding this exact stack, if either is.
     * <p>Asked of the hands rather than worked out from the {@code selected} flag
     * {@code inventoryTick} is handed, because that flag does not mean the same thing on both
     * loaders. NeoForge patches it to be true only for the selected hotbar slot; vanilla - so
     * Fabric - compares the selected hotbar index against the index within whichever
     * compartment is being ticked, and the off-hand is a compartment of one. So on Fabric an
     * off-hand deck is reported as selected whenever the player happens to have hotbar slot
     * one in hand, and reading the flag as "this is the main hand" quietly stopped sending
     * that player their own decklist.
     * <p>Identity rather than equality: two identical decks in two hands are two stacks, and
     * the question being asked is which of them this one is.
     */
    public static java.util.Optional<net.minecraft.world.InteractionHand> handHolding(
            net.minecraft.world.entity.player.Player holder, ItemStack stack) {
        if (holder == null || stack == null) {
            return java.util.Optional.empty();
        }
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            if (holder.getItemInHand(hand) == stack) {
                return java.util.Optional.of(hand);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Sends the owner what is really in the deck they are holding.
     * <p>The component on the item says how thick the deck is and nothing about what is in
     * it, because every client that can see the item is sent it. So the list goes to one
     * player, about a deck in their own hand, and only when it has changed - which catches
     * every way a deck can be edited without any of them having to remember to say so.
     */
    private static void tellTheOwner(
            net.minecraft.server.level.ServerPlayer holder, ItemStack stack) {
        // In a pocket rather than in a hand: nothing has a screen open on it, and its tooltip
        // is drawn from the public copy like everybody else's.
        net.minecraft.world.InteractionHand hand = handHolding(holder, stack).orElse(null);
        if (hand == null) {
            return;
        }
        DeckComponent deck = deckOf(stack).orElse(null);
        if (deck == null) {
            return;
        }
        // Which deck this is, so the client can tell the push apart from one about the other
        // deck it is carrying. A deck built before handles existed has none, so it is given
        // one here: this is the server holding the real stack, and minting it once is what
        // makes the deck addressable from then on. Random because a handle nobody guessed is
        // one nobody can ask about - see GatheringComponents.DECK_HANDLE.
        java.util.UUID handle = handleOf(stack).orElse(null);
        if (handle == null) {
            handle = java.util.UUID.randomUUID();
            stack.set(GatheringComponents.DECK_HANDLE.get(), handle);
        }
        Told[] last = LAST_TOLD.computeIfAbsent(holder.getUUID(), who -> new Told[2]);
        int at = hand.ordinal();
        Told before = last[at];
        // The handle is part of what "unchanged" means. Swap one deck for another with the
        // same cards in it and the contents compare equal, but it is a different deck and the
        // client has nothing cached under the new handle - so without this the push is
        // skipped and the screen falls back to the counts without the list.
        if (before != null && handle.equals(before.handle()) && deck.equals(before.deck())) {
            return;
        }
        last[at] = new Told(handle, deck);
        // Numbered, so a client can drop a push that arrives after a newer one. Per player
        // rather than per deck, which is fine: what matters is only that it goes up.
        int revision = TOLD_SO_FAR.merge(holder.getUUID(), 1, Integer::sum);
        dev.gathering.network.Sending.to(holder,
                dev.gathering.network.MyDeckPayload.of(hand, handle, revision, deck));
    }

    /**
     * What was last pushed to this player about the deck in that hand.
     * <p>Written before the send rather than after it, so this is what the server decided to
     * tell them and not what the wire managed to carry. That is what a test wants to read: a
     * stand-in player has no channel to take a payload on, and the decision is the part with
     * the rule in it.
     */
    public static java.util.Optional<DeckComponent> toldTheOwner(
            java.util.UUID player, net.minecraft.world.InteractionHand hand) {
        Told[] last = LAST_TOLD.get(player);
        Told told = last == null ? null : last[hand.ordinal()];
        return told == null ? java.util.Optional.empty() : java.util.Optional.of(told.deck());
    }

    /** Which deck was last sent for a hand, and which deck it was. */
    private record Told(java.util.UUID handle, DeckComponent deck) {
    }

    /** Per player, the deck last sent for each hand, so an unchanged deck is not re-sent. */
    private static final java.util.Map<java.util.UUID, Told[]> LAST_TOLD =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Per player, how many pushes have gone out. Numbers a push so an older one is dropped. */
    private static final java.util.Map<java.util.UUID, Integer> TOLD_SO_FAR =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Forgets a player, so the deck in their hand is sent again when they come back. */
    public static void forget(java.util.UUID player) {
        LAST_TOLD.remove(player);
        TOLD_SO_FAR.remove(player);
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
            // What it is for, then how to look inside it. A deck whose tooltip only offers to
            // open itself does not say that carrying it to a table is the whole game.
            tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".deck_play")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".open_deck")
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
