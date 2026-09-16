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
     * And when there is genuinely nothing to put it back together with, the stand-ins still do not stay.
     * <p>A deck holding them is worse than a deck missing a card: it lists cards that never load and hands
     * out blank ones, which is a card that looks like it exists and does not.
     */
    @GameTest(template = "empty")
    public static void aHiddenDeckNothingRemembersKeepsNoStandIns(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent real = new DeckComponent("Lost", "", Optional.of(player.getUUID()),
                List.of(BOLT, BEARS), List.of(), List.of());
        ItemStack stack = DeckItem.of(real);
        // The hidden copy, and a handle nothing has ever been remembered under.
        stack.set(GatheringComponents.DECK.get(), asAClientHasIt(helper, real));
        stack.set(GatheringComponents.DECK_HANDLE.get(), UUID.randomUUID());
        player.getInventory().setItem(8, stack);
        stack.inventoryTick(helper.getLevel(), player, 8, false);

        if (DeckItem.deckOf(stack).map(DeckComponent::isRedacted).orElse(false)) {
            helper.fail("a deck nothing remembers kept its stand-ins: "
                    + DeckItem.deckOf(stack).orElseThrow().entries());
            return;
        }
        helper.succeed();
    }
}
