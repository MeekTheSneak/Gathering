package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventorySlotsTest {

    @Test
    @DisplayName("a place in the inventory is the slot the player's own menu gives it")
    void placesBecomeSlots() {
        assertThat(InventorySlots.inTheirOwnMenu(0)).isEqualTo(36);
        assertThat(InventorySlots.inTheirOwnMenu(8)).isEqualTo(44);
        assertThat(InventorySlots.inTheirOwnMenu(9)).isEqualTo(9);
        assertThat(InventorySlots.inTheirOwnMenu(35)).isEqualTo(35);
        assertThat(InventorySlots.inTheirOwnMenu(40)).isEqualTo(45);
    }

    @Test
    @DisplayName("every place has its own slot, and nothing else has one")
    void everyPlaceOnce() {
        java.util.Set<Integer> slots = new java.util.HashSet<>();
        for (int place = 0; place <= InventorySlots.LAST_ROW_PLACE; place++) {
            assertThat(slots.add(InventorySlots.inTheirOwnMenu(place))).as("place %s", place).isTrue();
        }
        assertThat(slots.add(InventorySlots.inTheirOwnMenu(InventorySlots.OFF_HAND_PLACE))).isTrue();
        assertThat(InventorySlots.inTheirOwnMenu(36)).isEqualTo(-1);
        assertThat(InventorySlots.inTheirOwnMenu(-1)).isEqualTo(-1);
    }
}
