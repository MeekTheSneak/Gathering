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
                        InventorySlots.inTheirOwnMenu(2))));

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
                List.of(InventorySlots.inTheirOwnMenu(0))));

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
                List.of(InventorySlots.inTheirOwnMenu(0))));

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
        player.getInventory().setItem(0, CardItem.of(card(6)));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId,
                Optional.of(handle), List.of(InventorySlots.inTheirOwnMenu(0))));

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

    /** And nobody who is not in creative can name a deck they are not holding. */
    @GameTest(template = "empty")
    public static void namingaDeckOnlyWorksInCreative(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        UUID handle = UUID.randomUUID();
        dev.gathering.server.DeckVault.remember(player.getUUID(), handle,
                new DeckComponent("Survival", "", Optional.of(player.getUUID()), List.of(card(7)), List.of(), List.of()));
        player.getInventory().setItem(0, CardItem.of(card(8)));

        dev.gathering.server.DeckSweeps.handle(player, new DeckSweepPayload(player.inventoryMenu.containerId,
                Optional.of(handle), List.of(InventorySlots.inTheirOwnMenu(0))));

        if (player.getInventory().getItem(0).isEmpty()) {
            helper.fail("a player who is not in creative took a card into a deck they were not holding");
            return;
        }
        helper.succeed();
    }

    private static CardComponent card(int index) {
        return CardComponent.of(CardIdentity.ofPrinting(new UUID(77L, index)));
    }
}
