package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.server.DeckEdits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Editing a deck: taking cards out of the list, naming commanders, and putting cards back.
 *
 * <p>These run against real item stacks and a real slot because that is where the asymmetry
 * this feature is built on actually lives. Cards go into a deck with the bundle gesture and
 * never come out of it that way - taking a card is done from the deck list, where you can
 * read the card's name first. A regression there is silent: it looks like a working bundle
 * right up until somebody blind-pulls a card off a hundred-card deck by accident.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckEditGameTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
    private static final UUID BOLT = UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a");

    private static final CardComponent SOL = card(SOL_RING);
    private static final CardComponent LIGHTNING = card(BOLT);

    @GameTest(template = "empty")
    public static void takingACardHandsItToThePlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(SOL, SOL, LIGHTNING), List.of(), List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND, deckStack);

        DeckEdits.handle(player, DeckEditPayload.take(
                InteractionHand.MAIN_HAND, DeckComponent.Section.MAINBOARD, SOL));

        DeckComponent after = deckIn(deckStack);
        if (after.entries().size() != 2) {
            helper.fail("Taking one card should leave two, got " + after.entries().size());
        }
        if (after.entries().stream().filter(SOL::equals).count() != 1) {
            helper.fail("Taking one copy took the wrong number of copies");
        }
        if (countCards(player, SOL) != 1) {
            helper.fail("The card left the deck but never reached the player");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingTheLastCardLeavesNoDeckBehind(GameTestHelper helper) {
        // An empty deck is a deckbox with nothing in it. Taking the last card out should hand
        // you a card, not a card and an object to tidy away.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(SOL), List.of(), List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND, deckStack);

        DeckEdits.handle(player, DeckEditPayload.take(
                InteractionHand.MAIN_HAND, DeckComponent.Section.MAINBOARD, SOL));

        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            helper.fail("An emptied deck stayed in the player's hand: "
                    + player.getItemInHand(InteractionHand.MAIN_HAND));
        }
        if (countCards(player, SOL) != 1) {
            helper.fail("The last card did not reach the player");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingTheLastCommanderAlsoEmptiesTheDeck(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(), List.of(SOL), List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND, deckStack);

        DeckEdits.handle(player, DeckEditPayload.take(
                InteractionHand.MAIN_HAND, DeckComponent.Section.COMMANDERS, SOL));

        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            helper.fail("A deck emptied from the command zone stayed in hand");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingACardTheDeckDoesNotHoldChangesNothing(GameTestHelper helper) {
        // What a click looks like when it raced somebody else's edit.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DeckComponent before = deck(List.of(SOL), List.of(), List.of());
        ItemStack deckStack = DeckItem.of(before);
        player.setItemInHand(InteractionHand.MAIN_HAND, deckStack);

        DeckEdits.handle(player, DeckEditPayload.take(
                InteractionHand.MAIN_HAND, DeckComponent.Section.MAINBOARD, LIGHTNING));

        if (!deckIn(deckStack).equals(before)) {
            helper.fail("A stale take changed the deck");
        }
        if (countCards(player, LIGHTNING) != 0) {
            helper.fail("A stale take conjured a card out of nothing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theCommanderToggleMovesBothWays(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(SOL, LIGHTNING), List.of(), List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND, deckStack);

        DeckEdits.handle(player, DeckEditPayload.toggleCommander(
                InteractionHand.MAIN_HAND, DeckComponent.Section.MAINBOARD, SOL));

        DeckComponent promoted = deckIn(deckStack);
        if (!promoted.commanders().equals(List.of(SOL))) {
            helper.fail("The card did not reach the command zone: " + promoted.commanders());
        }
        if (!promoted.entries().equals(List.of(LIGHTNING))) {
            helper.fail("The mainboard is wrong after a promotion: " + promoted.entries());
        }
        if (promoted.deckSize() != 2) {
            helper.fail("A promotion changed the size of the deck");
        }

        DeckEdits.handle(player, DeckEditPayload.toggleCommander(
                InteractionHand.MAIN_HAND, DeckComponent.Section.COMMANDERS, SOL));

        DeckComponent demoted = deckIn(deckStack);
        if (!demoted.commanders().isEmpty()) {
            helper.fail("The command zone should be empty again: " + demoted.commanders());
        }
        if (demoted.entries().size() != 2 || !demoted.entries().contains(SOL)) {
            helper.fail("The card did not go back to the mainboard: " + demoted.entries());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anEditOnlyEverTouchesTheHeldDeck(GameTestHelper helper) {
        // The payload names a hand, so an empty hand is the whole of the defence.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(SOL), List.of(), List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND, deckStack);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        DeckEdits.handle(player, new DeckEditPayload(
                true, DeckEditPayload.Action.TAKE, DeckComponent.Section.MAINBOARD, SOL));

        if (deckIn(deckStack).entries().size() != 1) {
            helper.fail("An edit aimed at an empty hand reached the deck in the other one");
        }
        if (countCards(player, SOL) != 0) {
            helper.fail("An edit aimed at an empty hand produced a card");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoCardsBecomeAnUnnamedDeck(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack inSlot = CardItem.of(SOL);
        ItemStack carried = CardItem.of(LIGHTNING);
        Slot slot = slotHolding(inSlot);

        boolean handled = inSlot.getItem().overrideOtherStackedOnMe(
                inSlot, carried, slot, ClickAction.SECONDARY, player, carriedAccess(carried));

        if (!handled) {
            helper.fail("A card refused another card");
        }
        DeckComponent made = deckIn(slot.getItem());
        if (!made.entries().equals(List.of(SOL, LIGHTNING))) {
            helper.fail("The new deck holds the wrong cards: " + made.entries());
        }
        if (!made.name().isEmpty()) {
            helper.fail("A deck started from two cards should have no name, got " + made.name());
        }
        if (!made.owner().equals(Optional.of(player.getUUID()))) {
            helper.fail("A deck started from two cards is not owned by whoever made it");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckStartedFromTwoCardsTakesAThird(GameTestHelper helper) {
        // The point of the gesture: the same click that made the deck keeps filling it.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack inSlot = CardItem.of(SOL);
        ItemStack first = CardItem.of(LIGHTNING);
        Slot slot = slotHolding(inSlot);

        inSlot.getItem().overrideOtherStackedOnMe(
                inSlot, first, slot, ClickAction.SECONDARY, player, carriedAccess(first));

        ItemStack deckStack = slot.getItem();
        ItemStack third = CardItem.of(SOL);
        deckStack.getItem().overrideOtherStackedOnMe(
                deckStack, third, slot, ClickAction.SECONDARY, player, carriedAccess(third));

        if (deckIn(slot.getItem()).entries().size() != 3) {
            helper.fail("The third card did not join: " + deckIn(slot.getItem()).entries());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBlankCardStartsNothing(GameTestHelper helper) {
        // The creative-mode card with no component: there is no identity to put in a deck.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack inSlot = CardItem.of(SOL);
        ItemStack blank = new ItemStack(dev.gathering.item.GatheringContent.CARD.get());
        Slot slot = slotHolding(inSlot);

        boolean handled = inSlot.getItem().overrideOtherStackedOnMe(
                inSlot, blank, slot, ClickAction.SECONDARY, player, carriedAccess(blank));

        if (handled) {
            helper.fail("A blank card was made into a deck");
        }
        if (!(slot.getItem().getItem() instanceof CardItem)) {
            helper.fail("The card in the slot stopped being a card");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardsGoIntoADeckLikeABundle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(SOL), List.of(), List.of()));
        // Cards never stack in normal play, so a slot can hold only one. The cursor is not
        // clamped the same way, so this is also the one place the multi-copy path is
        // reachable at all - worth exercising rather than trusting.
        ItemStack carried = CardItem.of(LIGHTNING);
        carried.setCount(3);

        boolean handled = deckStack.getItem().overrideOtherStackedOnMe(
                deckStack, carried, slotHolding(ItemStack.EMPTY), ClickAction.SECONDARY, player,
                net.minecraft.world.entity.SlotAccess.NULL);

        if (!handled) {
            helper.fail("A deck refused a stack of cards");
        }
        if (!carried.isEmpty()) {
            helper.fail("Cards put into a deck stayed on the cursor: " + carried.getCount());
        }
        DeckComponent after = deckIn(deckStack);
        if (after.entries().size() != 4) {
            helper.fail("Expected four cards after inserting three, got " + after.entries().size());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insertedCardsAreStoredFaceUp(GameTestHelper helper) {
        // Otherwise one face-down copy and one face-up copy of a card are two deck rows.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(SOL), List.of(), List.of()));
        ItemStack carried = CardItem.of(SOL.flip());

        deckStack.getItem().overrideOtherStackedOnMe(
                deckStack, carried, slotHolding(ItemStack.EMPTY), ClickAction.SECONDARY, player,
                net.minecraft.world.entity.SlotAccess.NULL);

        DeckComponent after = deckIn(deckStack);
        if (!after.entries().equals(List.of(SOL, SOL))) {
            helper.fail("A face-down card went into the deck face down: " + after.entries());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckNeverGivesACardBackLikeABundle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DeckComponent before = deck(List.of(SOL, LIGHTNING), List.of(), List.of());
        ItemStack deckStack = DeckItem.of(before);

        // Empty cursor onto the deck: a bundle hands one item back. A deck must not.
        boolean handledOnMe = deckStack.getItem().overrideOtherStackedOnMe(
                deckStack, ItemStack.EMPTY, slotHolding(ItemStack.EMPTY), ClickAction.SECONDARY, player,
                net.minecraft.world.entity.SlotAccess.NULL);

        // Deck on the cursor, onto an empty slot: the same half of the bundle gesture.
        boolean handledOnOther = deckStack.getItem().overrideStackedOnOther(
                deckStack, slotHolding(ItemStack.EMPTY), ClickAction.SECONDARY, player);

        if (handledOnMe || handledOnOther) {
            helper.fail("A deck answered the take half of the bundle gesture");
        }
        if (!deckIn(deckStack).equals(before)) {
            helper.fail("A deck lost a card to the bundle gesture");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckStopsAtItsCardLimit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<CardComponent> full = new ArrayList<>();
        for (int copy = 0; copy < DeckComponent.MAX_CARDS; copy++) {
            full.add(SOL);
        }
        ItemStack deckStack = DeckItem.of(deck(full, List.of(), List.of()));
        ItemStack carried = CardItem.of(LIGHTNING);

        deckStack.getItem().overrideOtherStackedOnMe(
                deckStack, carried, slotHolding(ItemStack.EMPTY), ClickAction.SECONDARY, player,
                net.minecraft.world.entity.SlotAccess.NULL);

        if (deckIn(deckStack).totalCards() != DeckComponent.MAX_CARDS) {
            helper.fail("A full deck accepted another card: " + deckIn(deckStack).totalCards());
        }
        if (carried.isEmpty()) {
            helper.fail("A full deck swallowed the card it refused");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckTakesCardsOutOfASlotItIsClickedOn(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(), List.of(), List.of()));
        // One card, because that is all a slot can hold: cards are stacksTo(1), and
        // SimpleContainer#setItem clamps anything larger on the way in.
        Slot slot = slotHolding(CardItem.of(LIGHTNING));

        boolean handled = deckStack.getItem()
                .overrideStackedOnOther(deckStack, slot, ClickAction.SECONDARY, player);

        if (!handled) {
            helper.fail("A deck ignored a slot full of cards");
        }
        if (!slot.getItem().isEmpty()) {
            helper.fail("Cards were left in the slot: " + slot.getItem().getCount());
        }
        if (!deckIn(deckStack).entries().equals(List.of(LIGHTNING))) {
            helper.fail("The card did not reach the deck: " + deckIn(deckStack).entries());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anythingThatIsNotACardIsLeftAlone(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack deckStack = DeckItem.of(deck(List.of(SOL), List.of(), List.of()));
        ItemStack diamonds = new ItemStack(net.minecraft.world.item.Items.DIAMOND, 4);

        boolean handled = deckStack.getItem().overrideOtherStackedOnMe(
                deckStack, diamonds, slotHolding(ItemStack.EMPTY), ClickAction.SECONDARY, player,
                net.minecraft.world.entity.SlotAccess.NULL);

        if (handled) {
            helper.fail("A deck claimed a click it should have left to vanilla");
        }
        if (diamonds.getCount() != 4) {
            helper.fail("A deck ate something that was not a card");
        }
        helper.succeed();
    }

    /** Stands in for the container menu's carried-stack access. */
    private static net.minecraft.world.entity.SlotAccess carriedAccess(ItemStack carried) {
        ItemStack[] held = {carried};
        return net.minecraft.world.entity.SlotAccess.of(() -> held[0], stack -> held[0] = stack);
    }

    private static Slot slotHolding(ItemStack stack) {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, stack);
        return new Slot(container, 0, 0, 0);
    }

    private static CardComponent card(UUID printing) {
        return CardComponent.of(CardIdentity.ofPrinting(printing, false));
    }

    private static DeckComponent deck(
            List<CardComponent> entries, List<CardComponent> commanders, List<CardComponent> sideboard) {
        return new DeckComponent("Test deck", "", Optional.empty(), entries, commanders, sideboard);
    }

    private static DeckComponent deckIn(ItemStack stack) {
        return DeckItem.deckOf(stack)
                .orElseThrow(() -> new GameTestAssertException("The stack stopped being a deck"));
    }

    private static int countCards(Player player, CardComponent card) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (CardItem.cardOf(stack).filter(card::equals).isPresent()) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
