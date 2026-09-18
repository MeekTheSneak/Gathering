package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.ui.InventorySlots;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.DeckSweepPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A deck swept over a row of cards takes them all in.
 * <p>The gesture is the client's - right-click held, the cursor dragged across the row - and the work
 * is the server's, which is what this checks: the cards named end up in the deck, the slots they came
 * from are empty, and nothing that was not named is touched.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckSweepGameTest {

    private DeckSweepGameTest() {
    }

    @GameTest(template = "empty")
    public static void asweepPutsEveryCardItCrossedIntoTheDeck(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        DeckComponent deck = new DeckComponent("Swept", "", Optional.of(player.getUUID()),
                List.of(), List.of(), List.of());
        player.inventoryMenu.setCarried(DeckItem.of(deck));
        player.getInventory().setItem(0, CardItem.of(card(1)));
        player.getInventory().setItem(1, CardItem.of(card(2)));
        player.getInventory().setItem(2, new ItemStack(net.minecraft.world.item.Items.DIRT));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId, Optional.empty(),
                List.of(InventorySlots.inTheirOwnMenu(0), InventorySlots.inTheirOwnMenu(1),
                        InventorySlots.inTheirOwnMenu(2)), false));

        DeckComponent after = DeckItem.deckOf(player.inventoryMenu.getCarried()).orElse(null);
        if (after == null || !after.entries().contains(card(1)) || !after.entries().contains(card(2))) {
            helper.fail("a sweep over two cards put " + (after == null ? "nothing" : after.entries()) + " in the deck");
            return;
        }
        if (!player.getInventory().getItem(0).isEmpty() || !player.getInventory().getItem(1).isEmpty()) {
            helper.fail("the cards swept into the deck are still in the inventory as well");
            return;
        }
        if (!player.getInventory().getItem(2).is(net.minecraft.world.item.Items.DIRT)) {
            helper.fail("a sweep took something that was not a card");
            return;
        }
        helper.succeed();
    }

    /** A sweep with anything but a single deck on the cursor does nothing at all. */
    @GameTest(template = "empty")
    public static void asweepWithoutaDeckDoesNothing(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.inventoryMenu.setCarried(new ItemStack(net.minecraft.world.item.Items.DIRT));
        player.getInventory().setItem(0, CardItem.of(card(3)));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId, Optional.empty(),
                List.of(InventorySlots.inTheirOwnMenu(0)), false));

        if (player.getInventory().getItem(0).isEmpty()) {
            helper.fail("a sweep with no deck on the cursor took a card anyway");
            return;
        }
        helper.succeed();
    }

    /** And a sweep naming a menu the player does not have open is not a sweep. */
    @GameTest(template = "empty")
    public static void asweepInSomebodyElsesMenuDoesNothing(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.inventoryMenu.setCarried(DeckItem.of(new DeckComponent("Swept", "", Optional.of(player.getUUID()),
                List.of(), List.of(), List.of())));
        player.getInventory().setItem(0, CardItem.of(card(4)));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId + 7, Optional.empty(),
                List.of(InventorySlots.inTheirOwnMenu(0)), false));

        if (player.getInventory().getItem(0).isEmpty()) {
            helper.fail("a sweep naming another menu moved a card out of this one");
            return;
        }
        helper.succeed();
    }

    /**
     * The creative inventory's cursor is the client's alone, so the client names the deck and the server
     * uses the one it kept. A card right-clicked into a deck held there used to be destroyed: the copy the
     * client sent back had the card hidden by the wire, and the slot it came from arrived empty.
     */
    @GameTest(template = "empty")
    public static void adeckOnTheCreativeCursorTakesTheCard(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        UUID handle = UUID.randomUUID();
        DeckComponent deck = new DeckComponent("Creative", "", Optional.of(player.getUUID()),
                List.of(card(5)), List.of(), List.of());
        dev.gathering.server.DeckVault.remember(player.getUUID(), handle, deck);
        net.minecraft.world.item.ItemStack held = DeckItem.of(deck);
        held.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        player.getInventory().setItem(1, held);
        player.getInventory().setItem(0, CardItem.of(card(6)));
        // Picked up, which is what puts a deck on the creative cursor: the slot it left arrives empty.
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37, ItemStack.EMPTY));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId,
                Optional.of(handle), List.of(InventorySlots.inTheirOwnMenu(0)), false));

        var kept = dev.gathering.server.DeckVault.deckOf(player.getUUID(), handle).orElse(null);
        if (kept == null || !kept.entries().contains(card(6)) || !kept.entries().contains(card(5))) {
            helper.fail("the deck on the creative cursor holds " + (kept == null ? "nothing" : kept.entries()));
            return;
        }
        if (!player.getInventory().getItem(0).isEmpty()) {
            helper.fail("the card is still in the inventory as well as in the deck");
            return;
        }
        helper.succeed();
    }

    /**
     * The card goes in once, not twice, however many times the deck is put down.
     * <p>Both halves of the gesture run: the server does the insert it is told about, and the client
     * does the same click on its own copy - so when that copy comes back, every card in it that is not
     * a stand-in was already in the deck the server holds. Adding them on top put each card in twice,
     * and repeating the gesture minted pairs of any card at will.
     */
    @GameTest(template = "empty")
    public static void acardPutIntoaDeckInCreativeGoesInOnce(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        UUID handle = UUID.randomUUID();
        DeckComponent deck = new DeckComponent("Creative", "", Optional.of(player.getUUID()),
                List.of(card(11)), List.of(), List.of());
        dev.gathering.server.DeckVault.remember(player.getUUID(), handle, deck);
        net.minecraft.world.item.ItemStack held = DeckItem.of(deck);
        held.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        player.getInventory().setItem(1, held);
        player.getInventory().setItem(0, CardItem.of(card(12)));
        // The deck picked up onto the creative cursor, then the card right-clicked into it.
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37, ItemStack.EMPTY));
        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId,
                Optional.of(handle), List.of(InventorySlots.inTheirOwnMenu(0)), false));

        // The copy the client puts back down: its own cards hidden, and the card it just put in face up.
        DeckComponent theirs = new DeckComponent("Creative", "", Optional.of(player.getUUID()),
                List.of(dev.gathering.item.CardComponent.HIDDEN, card(12)), List.of(), List.of());
        net.minecraft.world.item.ItemStack back = DeckItem.of(theirs);
        back.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37, back));

        DeckComponent put = DeckItem.deckOf(player.getInventory().getItem(1)).orElse(null);
        if (put == null) {
            helper.fail("the deck put back down in the creative menu is not a deck");
            return;
        }
        long copies = put.entries().stream().filter(card(12)::equals).count();
        if (copies != 1 || put.entries().size() != 2) {
            helper.fail("a card right-clicked into a deck in the creative menu went in " + copies
                    + " time(s), leaving " + put.entries().size() + " cards: " + put.entries());
            return;
        }
        helper.succeed();
    }

    /**
     * A hidden copy of a deck the player still has does not become a second real deck.
     * <p>The creative menu trusts the client's copy of a stack, so a deck arriving hidden is restored
     * from the real one - and where the real one is still lying in another slot, restoring it there as
     * well made two. A creative hotbar saved with a deck in it and loaded again did exactly that.
     */
    @GameTest(template = "empty")
    public static void asavedCopyOfaDeckDoesNotBecomeaSecondDeck(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        UUID handle = UUID.randomUUID();
        DeckComponent deck = new DeckComponent("Saved", "", Optional.of(player.getUUID()),
                List.of(card(21), card(22)), List.of(), List.of());
        net.minecraft.world.item.ItemStack real = DeckItem.of(deck);
        real.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        player.getInventory().setItem(0, real);

        // The saved hotbar arriving: the same handle, cards hidden, into a different slot.
        DeckComponent hidden = new DeckComponent("Saved", "", Optional.of(player.getUUID()),
                List.of(dev.gathering.item.CardComponent.HIDDEN, dev.gathering.item.CardComponent.HIDDEN),
                List.of(), List.of());
        net.minecraft.world.item.ItemStack copy = DeckItem.of(hidden);
        copy.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(38, copy));

        int decks = 0;
        for (var stack : player.getInventory().items) {
            if (stack.getItem() instanceof DeckItem) {
                decks++;
            }
        }
        if (decks != 1) {
            helper.fail("loading a saved hotbar left the player holding " + decks + " decks of the same cards");
            return;
        }
        var left = DeckItem.deckOf(player.getInventory().getItem(2)).orElse(null);
        if (left == null || left.entries().size() != 2 || left.entries().contains(
                dev.gathering.item.CardComponent.HIDDEN)) {
            helper.fail("the deck that arrived holds " + (left == null ? "nothing" : left.entries().toString()));
            return;
        }
        helper.succeed();
    }

    /**
     * The same gesture on a deck the vault has never seen, which is every deck until one is swept.
     * <p>The scripted client found this: its deck is made and put in the hotbar the way a player's
     * is, with nothing seeding the vault, so the server refuses to do the insert - and everything
     * then rests on the client's own copy being merged back in.
     */
    @GameTest(template = "empty")
    public static void acardGoesInEvenWhenTheVaultHasNeverSeenTheDeck(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        net.minecraft.world.item.ItemStack held = DeckItem.of(new DeckComponent(
                "Creative", "", Optional.of(player.getUUID()), List.of(card(31)), List.of(), List.of()));
        UUID handle = DeckItem.handleOf(held).orElseThrow();
        player.getInventory().setItem(0, held);
        player.getInventory().setItem(1, CardItem.of(card(32)));

        // Picked up onto the creative cursor: the slot it left arrives empty.
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, ItemStack.EMPTY));
        // The click the screen makes, told to the server; the client does it too.
        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId,
                Optional.of(handle), List.of(InventorySlots.inTheirOwnMenu(1)), true));
        // And the copy the client puts back down, its own cards hidden and the new one face up.
        net.minecraft.world.item.ItemStack back = DeckItem.of(new DeckComponent(
                "Creative", "", Optional.of(player.getUUID()),
                List.of(dev.gathering.item.CardComponent.HIDDEN, card(32)), List.of(), List.of()));
        back.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, back));

        DeckComponent put = DeckItem.deckOf(player.getInventory().getItem(0)).orElse(null);
        long loose = 0;
        for (var stack : player.getInventory().items) {
            if (stack.getItem() instanceof CardItem) {
                loose += stack.getCount();
            }
        }
        if (put == null || put.entries().size() != 2 || loose != 0) {
            helper.fail("a card put into a deck the vault has never seen left the deck with "
                    + (put == null ? "no deck" : put.entries().toString()) + " and " + loose + " loose");
            return;
        }
        helper.succeed();
    }

    /** And nobody who is not in creative can name a deck they are not holding. */
    @GameTest(template = "empty")
    public static void namingaDeckOnlyWorksInCreative(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        UUID handle = UUID.randomUUID();
        DeckComponent theirs = new DeckComponent("Survival", "", Optional.of(player.getUUID()),
                List.of(card(7)), List.of(), List.of());
        dev.gathering.server.DeckVault.remember(player.getUUID(), handle, theirs);
        net.minecraft.world.item.ItemStack held = DeckItem.of(theirs);
        held.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        player.getInventory().setItem(1, held);
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37, ItemStack.EMPTY));
        player.getInventory().setItem(0, CardItem.of(card(8)));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId,
                Optional.of(handle), List.of(InventorySlots.inTheirOwnMenu(0)), false));

        if (player.getInventory().getItem(0).isEmpty()) {
            helper.fail("a player who is not in creative took a card into a deck they were not holding");
            return;
        }
        helper.succeed();
    }

    /**
     * A deck the player is not holding takes nothing, even in creative.
     * <p>The creative cursor is the client's alone, so the client names which deck it is holding - and a
     * client that named a deck lying in a chest instead would have sent its cards somewhere the chest's
     * copy never hears about, which is a card destroyed. Only a deck that has just left one of this
     * player's own slots counts as being on their cursor.
     */
    @GameTest(template = "empty")
    public static void adeckNotOnTheCursorTakesNothing(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        UUID handle = UUID.randomUUID();
        dev.gathering.server.DeckVault.remember(player.getUUID(), handle,
                new DeckComponent("Elsewhere", "", Optional.of(player.getUUID()), List.of(card(11)), List.of(), List.of()));
        player.getInventory().setItem(0, CardItem.of(card(12)));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId,
                Optional.of(handle), List.of(InventorySlots.inTheirOwnMenu(0)), false));

        if (player.getInventory().getItem(0).isEmpty()) {
            helper.fail("a card was taken into a deck the player was not holding");
            return;
        }
        var kept = dev.gathering.server.DeckVault.deckOf(player.getUUID(), handle).orElse(null);
        if (kept != null && kept.entries().contains(card(12))) {
            helper.fail("a deck nobody was holding took a card anyway");
            return;
        }
        helper.succeed();
    }

    private static CardComponent card(int index) {
        return CardComponent.of(CardIdentity.ofPrinting(new UUID(77L, index)));
    }
}
