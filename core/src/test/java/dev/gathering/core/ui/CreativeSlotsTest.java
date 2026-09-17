package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which slot of the player's own menu a creative inventory slot stands for.
 * <p>The numbers here are the creative inventory's own, read off vanilla: its inventory tab builds a
 * wrapper in front of each of the player's menu slots and hands it that slot's number, and every other
 * tab builds the bottom row fresh over the inventory, so the same row is 0 to 8 there and 36 to 44 on
 * the inventory tab. Reading the container slot straight was right on one tab and named the crafting
 * square and the armor on all the others.
 */
@DisplayName("A creative inventory slot in the player's own menu")
class CreativeSlotsTest {

    @Test
    @DisplayName("is itself on the inventory tab, where the two numbers agree")
    void theInventoryTabStandsInFrontOfTheMenu() {
        // The wrapper at place 36 says it stands for menu slot 36: the first hotbar slot.
        assertThat(InventorySlots.creativeSlot(36, 36, true)).isEqualTo(36);
        assertThat(InventorySlots.creativeSlot(9, 9, true)).isEqualTo(9);
        assertThat(InventorySlots.creativeSlot(45, 45, true)).isEqualTo(45);
    }

    @Test
    @DisplayName("is the hotbar on every other tab, where the row is numbered from nothing")
    void theOtherTabsNumberTheHotbarFromNothing() {
        // The bottom row of a category or search tab: places 45 to 53 in the screen, hotbar 0 to 8.
        assertThat(InventorySlots.creativeSlot(45, 0, true)).isEqualTo(36);
        assertThat(InventorySlots.creativeSlot(49, 4, true)).isEqualTo(40);
        assertThat(InventorySlots.creativeSlot(53, 8, true)).isEqualTo(44);
    }

    @Test
    @DisplayName("is nothing at all where the slot is the tab's own stock")
    void theTabsOwnStockIsNobodysSlot() {
        assertThat(InventorySlots.creativeSlot(0, 0, false)).isEqualTo(-1);
        assertThat(InventorySlots.creativeSlot(44, 44, false)).isEqualTo(-1);
    }
}
