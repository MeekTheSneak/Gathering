package dev.gathering.item;

import dev.gathering.Gathering;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.service.CardNameLookup;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * One card, as an item.
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

    /**
     * Right-click to turn the card over.
     * <p>Server-authoritative, like every other change to a card: the component is written on
     * the server and syncs back, so two players looking at the same card never disagree about
     * which way up it is.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            cardOf(stack).ifPresent(card ->
                    stack.set(GatheringComponents.CARD.get(), card.flip()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Right-clicking one card onto another makes a deck of the two.
     * <p>Every deck otherwise has to start at the import screen, which is the wrong shape for
     * the way a deck actually comes together: you pick cards up, and at some point two of
     * them are the start of something. This is that moment, and it uses the gesture already
     * in the player's hands - the same right-click that adds a third card to the deck it just
     * made.
     * <p>The deck has no name. Naming a pile of two cards is a decision to make later, and
     * an unnamed deck reads as "Deck" on the item rather than as a blank.
     */
    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        // Cards never stack, so anything holding more than one is not a card the player is
        // looking at and should be left to vanilla.
        if (stack.getCount() != 1 || other.getCount() != 1 || !(other.getItem() instanceof CardItem)) {
            return false;
        }
        Optional<CardComponent> beneath = cardOf(stack);
        Optional<CardComponent> carried = cardOf(other);
        if (beneath.isEmpty() || carried.isEmpty()) {
            // A blank creative card carries no identity, so there is nothing to put in a deck.
            return false;
        }

        ItemStack deck = DeckItem.of(new DeckComponent(
                "",
                "",
                Optional.of(player.getUUID()),
                List.of(beneath.get().faceUp(), carried.get().faceUp()),
                List.of(),
                List.of())
                // Painted the moment it becomes a deck. A deck started this way has no name
                // yet - naming a pile of two cards is a decision for later - so its color is
                // the only thing telling it from the next one on the shelf.
                .colored(dev.gathering.core.card.DeckColors.pick(
                        player.level().getRandom().nextLong())));
        if (!slot.mayPlace(deck)) {
            // Somewhere a deck cannot go - a fuel slot, a crafting result. Leave the cards be.
            return false;
        }

        slot.setByPlayer(deck);
        access.set(ItemStack.EMPTY);
        DeckItem.playAssembleSound(player);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return cardOf(stack)
                .flatMap(card -> CardNameLookup.Binding.current().nameOf(card))
                .<Component>map(Component::literal)
                .orElseGet(() -> super.getName(stack));
    }

    /**
     * Right-click somebody while holding a card, and you are offering to trade.
     * <p>The gesture is the sentence: holding a card out to a person is what asking to trade
     * looks like at a table, and right-clicking a player does nothing else in this game. No
     * command to remember and nothing to find in a menu.
     * <p>Which card is in the hand does not matter and nothing is put up by it - the table
     * opens empty. Holding one is the way of saying you mean it, not the offer.
     */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(
            ItemStack stack, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.LivingEntity target,
            net.minecraft.world.InteractionHand hand) {
        if (player.level().isClientSide()) {
            return target instanceof net.minecraft.world.entity.player.Player
                    ? net.minecraft.world.InteractionResult.SUCCESS
                    : net.minecraft.world.InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer asking)
                || !(target instanceof ServerPlayer other)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!dev.gathering.server.TradeSessions.isOffered()) {
            // Collection mode's, and only there: with cards conjured out of a decklist,
            // swapping one is two people agreeing to trade things they could each have typed.
            asking.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.gathering.trade_off"));
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        dev.gathering.server.TradeSessions.open(asking, other);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        cardOf(stack).ifPresent(card -> {
            if (card.flipped()) {
                tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".face_down")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
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
        tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".flip_card")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".start_deck")
                .withStyle(ChatFormatting.DARK_GRAY));
        // Attribution where card data appears, per the Scryfall API guidelines.
        tooltip.add(Component.translatable("tooltip." + Gathering.MOD_ID + ".scryfall_attribution")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
