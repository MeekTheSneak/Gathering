package dev.gathering.core.ui;

/**
 * Where a place in a player's inventory sits in the menu the server has for it.
 * <p>The player's own menu numbers its slots in an order of its own - the crafting square first, then
 * armor, then the three rows, then the hotbar, then the off hand - so a place in the inventory and a
 * slot in that menu are two different numbers. The creative inventory draws its own slots over the
 * player's and renumbers them again, and a gesture that names a slot to the server has to say which
 * number it means.
 * <p>Pure, so the arithmetic is checked rather than counted on a screen.
 */
public final class InventorySlots {

    /** The player's menu: result, four crafting, four armor, then the rows. */
    public static final int FIRST_ROW_SLOT = 9;

    /** Where the hotbar starts in that menu, after the three rows. */
    public static final int HOTBAR_SLOT = 36;

    /** And the off hand, last. */
    public static final int OFF_HAND_SLOT = 45;

    /** Where the off hand sits in the inventory itself. */
    public static final int OFF_HAND_PLACE = 40;

    /** The last place of the three rows. */
    public static final int LAST_ROW_PLACE = 35;

    private InventorySlots() {
    }

    /**
     * The slot number the player's own menu gives a place in their inventory, or -1 for a place that
     * menu does not hold.
     *
     * @param place where it is in the inventory: 0-8 the hotbar, 9-35 the rows, 40 the off hand
     */
    public static int inTheirOwnMenu(int place) {
        if (place >= 0 && place < FIRST_ROW_SLOT) {
            return HOTBAR_SLOT + place;
        }
        if (place >= FIRST_ROW_SLOT && place <= LAST_ROW_PLACE) {
            return place;
        }
        return place == OFF_HAND_PLACE ? OFF_HAND_SLOT : -1;
    }
}
