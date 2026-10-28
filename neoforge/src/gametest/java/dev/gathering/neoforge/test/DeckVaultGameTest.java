package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.DeckVault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A deck whose cards arrive hidden - the copy every client is sent, which the creative menu sends back
 * - is given its real cards again, never kept as a deck of stand-ins.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckVaultGameTest {

    private static final CardComponent BOLT = CardComponent.of(
            dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(7L, 1L), false));
    private static final CardComponent BEARS = CardComponent.of(
            dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(7L, 2L), false));

    /** The copy of a deck a client holds: what it is sent, sent back. */
    private static DeckComponent asAClientHasIt(GameTestHelper helper, DeckComponent deck) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DeckComponent.PUBLIC_STREAM_CODEC.encode(buffer, deck);
            return DeckComponent.PUBLIC_STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "empty")
    public static void aDeckMovedOnTheCreativeMenuKeepsItsCards(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent real = new DeckComponent("Mine", "", Optional.of(player.getUUID()),
                List.of(BOLT, BEARS), List.of(), List.of());
        ItemStack stack = DeckItem.of(real);
        player.getInventory().setItem(3, stack);
        stack.inventoryTick(helper.getLevel(), player, 3, false);

        stack.set(GatheringComponents.DECK.get(), asAClientHasIt(helper, real));
        if (!DeckItem.deckOf(stack).orElseThrow().isRedacted()) {
            helper.fail("fixture: the client's copy of a deck came back with its cards");
            return;
        }
        stack.inventoryTick(helper.getLevel(), player, 3, false);
        if (!DeckItem.deckOf(stack).orElseThrow().entries().equals(List.of(BOLT, BEARS))) {
            helper.fail("a deck that came back from the creative menu holds " + DeckItem.deckOf(stack).orElseThrow().entries());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckMadeOnTheCreativeMenuHasTheCardsItWasMadeFrom(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        // What the client makes of two cards right-clicked together: its cards are real, because it made them
        // out of the two card items in front of it, so the first copy the server sees is the whole deck.
        ItemStack made = DeckItem.of(new DeckComponent("", "", Optional.of(player.getUUID()),
                List.of(BOLT, BEARS), List.of(), List.of()));
        player.getInventory().setItem(4, made);
        made.inventoryTick(helper.getLevel(), player, 4, false);
        // And what reaches the server the next time the creative menu sends that slot back.
        made.set(GatheringComponents.DECK.get(), asAClientHasIt(helper, DeckItem.deckOf(made).orElseThrow()));
        made.inventoryTick(helper.getLevel(), player, 4, false);
        if (!DeckItem.deckOf(made).orElseThrow().entries().equals(List.of(BOLT, BEARS))) {
            helper.fail("a deck made of two cards on the creative menu holds " + DeckItem.deckOf(made).orElseThrow().entries());
            return;
        }
        helper.succeed();
    }

    /**
     * A card put into a deck that is already carried is kept, not deleted.
     * <p>The owner found this in creative: right-clicking cards onto a deck emptied their hand and the deck
     * came back the size it was. The creative menu sends the client's copy of the stack back, every card in it
     * hidden except the ones the client has just put in - and the server was taking the list it had kept and
     * throwing the rest away. Twice over, because a stack ticks every tick: what is added must be added once.
     */
    @GameTest(template = "empty")
    public static void cardsPutIntoACarriedDeckSurviveTheCreativeMenu(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        ItemStack stack = DeckItem.of(new DeckComponent("Jank", "", Optional.of(player.getUUID()),
                List.of(BOLT), List.of(), List.of()));
        player.getInventory().setItem(5, stack);
        stack.inventoryTick(helper.getLevel(), player, 5, false);

        // The client's copy of that deck, with a card it has just put in: hidden cards, and one real one.
        DeckComponent hidden = asAClientHasIt(helper, DeckItem.deckOf(stack).orElseThrow());
        stack.set(GatheringComponents.DECK.get(),
                hidden.withAdded(DeckComponent.Section.MAINBOARD, BEARS).orElseThrow());
        stack.inventoryTick(helper.getLevel(), player, 5, false);
        if (!DeckItem.deckOf(stack).orElseThrow().entries().equals(List.of(BOLT, BEARS))) {
            helper.fail("a card put into a carried deck left it holding "
                    + DeckItem.deckOf(stack).orElseThrow().entries());
            return;
        }
        stack.inventoryTick(helper.getLevel(), player, 5, false);
        stack.inventoryTick(helper.getLevel(), player, 5, false);
        if (!DeckItem.deckOf(stack).orElseThrow().entries().equals(List.of(BOLT, BEARS))) {
            helper.fail("ticking the same deck again made it " + DeckItem.deckOf(stack).orElseThrow().entries());
            return;
        }
        helper.succeed();
    }

    /**
     * The owner's own gesture, through the item's own code: a card stack right-clicked onto a deck in the
     * creative menu.
     * <p>The existing check above sets the component by hand, which is the shape of what a creative click
     * does and not the thing itself. The owner has reported cards going into a deck and loading for ever
     * twice now (2026-09-15 and 2026-09-16), so this goes down the real path - {@code
     * overrideStackedOnOther} on the client's redacted copy, the whole stack handed back to the server the
     * way the creative menu hands one back, and then a tick - and then takes the card out again, because
     * "it comes out blank" is the half a deck's own list cannot show.
     */
    @GameTest(template = "empty")
    public static void aCardRightClickedOntoADeckInCreativeIsStillThere(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        ItemStack held = DeckItem.of(new DeckComponent("Jank", "", Optional.of(player.getUUID()),
                List.of(BOLT), List.of(), List.of()));
        player.getInventory().setItem(5, held);
        held.inventoryTick(helper.getLevel(), player, 5, false);

        // What the client has: the same stack, its cards hidden on the way out.
        ItemStack clients = held.copy();
        clients.set(GatheringComponents.DECK.get(),
                asAClientHasIt(helper, DeckItem.deckOf(held).orElseThrow()));

        // And the gesture, run the way the client runs it.
        net.minecraft.world.SimpleContainer bag = new net.minecraft.world.SimpleContainer(1);
        bag.setItem(0, dev.gathering.item.CardItem.of(BEARS));
        net.minecraft.world.inventory.Slot slot = new net.minecraft.world.inventory.Slot(bag, 0, 0, 0);
        if (!clients.overrideStackedOnOther(slot, net.minecraft.world.inventory.ClickAction.SECONDARY, player)) {
            helper.fail("right-clicking a card onto a deck did nothing at all");
            return;
        }

        // The creative menu hands the server whatever the client holds for that slot.
        player.getInventory().setItem(5, clients);
        clients.inventoryTick(helper.getLevel(), player, 5, false);

        DeckComponent after = DeckItem.deckOf(clients).orElseThrow();
        if (after.entries().stream().anyMatch(CardComponent::isHidden)) {
            helper.fail("the deck is holding hidden stand-ins after a creative click: " + after.entries());
            return;
        }
        if (!after.entries().equals(List.of(BOLT, BEARS))) {
            helper.fail("a card right-clicked into a deck left it holding " + after.entries());
            return;
        }
        helper.succeed();
    }

    /**
     * A deck the server has never seen held in a hand still gets a handle, so a hidden copy of it can be
     * put back together.
     * <p>This is the one the owner kept hitting. The handle used to be minted only when a deck reached a
     * hand, so a deck made and fiddled with in the creative menu had none - and with no handle there was
     * nothing to remember its real cards under. The first hidden copy the menu handed back was kept as-is,
     * and from then on the deck listed cards that loaded for ever and handed out blank ones.
     */
    @GameTest(template = "empty")
    public static void aDeckNeverHeldIsStillPutBackTogether(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        ItemStack stack = DeckItem.of(new DeckComponent("Never Held", "", Optional.of(player.getUUID()),
                List.of(BOLT), List.of(), List.of()));
        // Deliberately not in a hand, and with no handle: an item in a menu somewhere.
        stack.remove(GatheringComponents.DECK_HANDLE.get());
        player.getInventory().setItem(7, stack);
        stack.inventoryTick(helper.getLevel(), player, 7, false);

        if (DeckItem.handleOf(stack).isEmpty()) {
            helper.fail("a deck the server has ticked has no handle, so nothing can remember its cards");
            return;
        }
        // And now the creative menu hands back the client's copy of it.
        stack.set(GatheringComponents.DECK.get(),
                asAClientHasIt(helper, DeckItem.deckOf(stack).orElseThrow()));
        stack.inventoryTick(helper.getLevel(), player, 7, false);

        DeckComponent after = DeckItem.deckOf(stack).orElseThrow();
        if (after.entries().stream().anyMatch(CardComponent::isHidden)) {
            helper.fail("a deck that was never held kept its stand-ins: " + after.entries());
            return;
        }
        if (!after.entries().equals(List.of(BOLT))) {
            helper.fail("a deck that was never held came back as " + after.entries());
            return;
        }
        helper.succeed();
    }

    /**
     * When there is something real left in a hidden copy, the stand-ins beside it do not stay.
     * <p>They list cards that never load and, but for the guard in {@code DeckEdits}, would hand out blank
     * ones. What is really in it is what is left.
     * <p>What this deliberately does <em>not</em> do is strip a deck down to nothing. An empty deck is
     * removed - that is what emptying one means - so purging every card of a deck the server has merely
     * forgotten would delete the item, which is exactly what happened to the owner's decks. Keeping the
     * box beats tidying it: see {@link #aHiddenDeckNothingRemembersIsNotThrownAway}.
     */
    @GameTest(template = "empty")
    public static void aHiddenDeckKeepsWhatIsRealAndDropsTheRest(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent real = new DeckComponent("Half known", "", Optional.of(player.getUUID()),
                List.of(BOLT, BEARS), List.of(), List.of());
        ItemStack stack = DeckItem.of(real);
        // A copy with one real card still in it and the rest hidden, under a handle nothing remembers:
        // a creative click that added a card to a deck the server had forgotten.
        DeckComponent hidden = asAClientHasIt(helper, real);
        stack.set(GatheringComponents.DECK.get(),
                hidden.withAdded(DeckComponent.Section.MAINBOARD, BEARS).orElseThrow());
        stack.set(GatheringComponents.DECK_HANDLE.get(), UUID.randomUUID());

        player.getInventory().setItem(8, stack);
        stack.inventoryTick(helper.getLevel(), player, 8, false);

        DeckComponent after = DeckItem.deckOf(stack).orElseThrow();
        if (after.isRedacted()) {
            helper.fail("a deck with a real card in it kept its stand-ins too: " + after.entries());
            return;
        }
        if (!after.entries().equals(List.of(BEARS))) {
            helper.fail("the card that was really there did not survive: " + after.entries());
            return;
        }
        helper.succeed();
    }

    /**
     * Two cards put together in the creative menu make a deck, and the deck is still there afterwards.
     * <p>The owner reported the deck item simply vanishing (2026-09-16). Through the real gesture and the
     * real hand-back: the client makes the deck out of two cards it holds, the creative menu sends the
     * server whatever the client now has, and the server ticks it.
     */
    @GameTest(template = "empty")
    public static void twoCardsPutTogetherStayADeck(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);

        net.minecraft.world.SimpleContainer bag = new net.minecraft.world.SimpleContainer(1);
        bag.setItem(0, dev.gathering.item.CardItem.of(BOLT));
        net.minecraft.world.inventory.Slot slot = new net.minecraft.world.inventory.Slot(bag, 0, 0, 0);
        ItemStack carried = dev.gathering.item.CardItem.of(BEARS);
        net.minecraft.world.inventory.ClickAction secondary =
                net.minecraft.world.inventory.ClickAction.SECONDARY;

        ItemStack[] cursor = {carried};
        net.minecraft.world.entity.SlotAccess access = new net.minecraft.world.entity.SlotAccess() {
            @Override
            public ItemStack get() {
                return cursor[0];
            }

            @Override
            public boolean set(ItemStack put) {
                cursor[0] = put;
                return true;
            }
        };
        boolean handled = bag.getItem(0).overrideOtherStackedOnMe(carried, slot, secondary, player, access);
        if (!handled) {
            helper.fail("putting one card onto another did not make a deck at all");
            return;
        }
        ItemStack made = bag.getItem(0);
        if (DeckItem.deckOf(made).isEmpty()) {
            helper.fail("two cards put together left " + made + " rather than a deck");
            return;
        }

        // And now the creative menu hands the server what the client holds, and the server ticks it.
        player.getInventory().setItem(3, made);
        made.inventoryTick(helper.getLevel(), player, 3, false);
        made.inventoryTick(helper.getLevel(), player, 3, false);

        if (made.isEmpty() || made.getCount() == 0) {
            helper.fail("a deck made out of two cards was thrown away by the server");
            return;
        }
        if (DeckItem.deckOf(made).map(DeckComponent::entries).map(List::size).orElse(0) != 2) {
            helper.fail("a deck made out of two cards holds " + DeckItem.deckOf(made).map(DeckComponent::entries));
            return;
        }
        helper.succeed();
    }

    /**
     * A hidden copy of a deck nothing remembers is not thrown away.
     * <p>Stripping the stand-ins off such a deck can leave nothing at all, and an empty deck is removed -
     * so a lock meant to stop cards loading for ever could instead delete the item outright. Losing the
     * cards is bad; losing the box as well is worse, and it is the box the player is looking at.
     */
    @GameTest(template = "empty")
    public static void aHiddenDeckNothingRemembersIsNotThrownAway(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent real = new DeckComponent("Lost", "", Optional.of(player.getUUID()),
                List.of(BOLT, BEARS), List.of(), List.of());
        ItemStack stack = DeckItem.of(real);
        stack.set(GatheringComponents.DECK.get(), asAClientHasIt(helper, real));
        stack.set(GatheringComponents.DECK_HANDLE.get(), UUID.randomUUID());

        player.getInventory().setItem(4, stack);
        stack.inventoryTick(helper.getLevel(), player, 4, false);
        stack.inventoryTick(helper.getLevel(), player, 4, false);

        if (stack.isEmpty() || stack.getCount() == 0) {
            helper.fail("a deck whose cards could not be put back was deleted rather than left alone");
            return;
        }
        helper.succeed();
    }

    /**
     * A deck the player has never held is still told about, so its cards do not load for ever.
     * <p>The real list only ever went to the deck in a hand. A deck in a pocket - which is where one sits
     * while somebody adds cards to it in the creative menu - was never pushed, so the client had nothing
     * but the public copy and every card in it read as still loading. The owner reported it twice after
     * the handle was fixed, because the handle was only half of it.
     */
    @GameTest(template = "empty")
    public static void aDeckInAPocketIsToldAboutToo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckItem.forget(player.getUUID());
        // Nowhere near a hand: slot nine is the top row of the pack, not the hotbar.
        ItemStack stack = DeckItem.of(new DeckComponent("In a pocket", "", Optional.of(player.getUUID()),
                List.of(BOLT), List.of(), List.of()));
        player.getInventory().setItem(9, stack);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);

        stack.inventoryTick(helper.getLevel(), player, 9, false);

        Optional<DeckComponent> told =
                DeckItem.toldTheOwner(player.getUUID(), DeckItem.handleOf(stack).orElseThrow());
        if (told.isEmpty()) {
            helper.fail("a deck in a pocket was never described to its owner, so its cards never load");
            return;
        }
        if (!told.get().entries().equals(List.of(BOLT))) {
            helper.fail("the owner was told their pocketed deck holds " + told.get().entries());
            return;
        }
        helper.succeed();
    }
}
