package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.server.DeckEdits;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A card taken out of a deck by somebody with no room for it lands on the floor.
 * <p>Reported from a real session: "removing a card from a deck while your inventory is full
 * deletes the card". A card is a collection item and must never evaporate because a bag was
 * full - so this fills every slot a player has and then takes one out.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FullInventoryGameTest {

    private static final UUID CARD = UUID.fromString("b7833c56-eb62-4c14-9db6-b9c1c92cb4ba");

    @GameTest(template = "empty")
    public static void takingACardOutWithNoRoomDropsIt(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        CardComponent card = CardComponent.of(
                dev.gathering.core.card.CardIdentity.ofPrinting(CARD));
        DeckComponent deck = new DeckComponent(
                "Test", "", Optional.of(player.getUUID()),
                List.of(card, card), List.of(), List.of());
        player.setItemInHand(InteractionHand.MAIN_HAND, DeckItem.of(deck));

        // Every other slot full of something that will not stack with a card.
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (player.getInventory().items.get(slot).isEmpty()) {
                player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
            }
        }
        player.getInventory().offhand.set(0, new ItemStack(Items.STONE, 64));

        int before = onTheFloor(helper, player);
        DeckEdits.handle(player, new DeckEditPayload(
                false, DeckEditPayload.Action.TAKE,
                DeckComponent.Section.MAINBOARD, DeckComponent.Section.MAINBOARD, card));

        DeckComponent left = DeckItem.deckOf(
                player.getItemInHand(InteractionHand.MAIN_HAND)).orElse(null);
        if (left == null || left.totalCards() != 1) {
            helper.fail("The deck did not lose exactly one card: "
                    + (left == null ? "no deck left" : left.totalCards() + " cards"));
            return;
        }
        if (onTheFloor(helper, player) != before + 1) {
            helper.fail("A card taken out of a deck with a full inventory was deleted rather "
                    + "than dropped");
            return;
        }
        helper.succeed();
    }

    /**
     * Item entities around the player rather than around the test structure: a mock player is
     * put down by {@code placeNewPlayer}, which lands them at the level's spawn point and not
     * inside the template the test is running in.
     */
    private static int onTheFloor(GameTestHelper helper, net.minecraft.world.entity.Entity near) {
        return helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                near.getBoundingBox().inflate(8)).size();
    }
}
