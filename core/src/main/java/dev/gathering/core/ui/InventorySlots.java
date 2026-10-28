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

    /** The bottom row of the creative inventory, which is the player's hotbar on every tab. */
    public static final int HOTBAR_SLOTS = 9;

    /**
     * The slot of the player's own menu that a slot of the creative inventory stands for, or -1 where it
     * stands for none of them.
     * <p>The creative inventory draws the player's pockets two different ways and numbers them
     * differently in each, which is the whole reason this exists. On its inventory tab every slot is a
     * wrapper standing in front of one of the player's own menu slots and says which by its container
     * slot, so that number is already the one the server wants. On every other tab the row along the
     * bottom is the hotbar built fresh over the inventory itself, so its container slot is a place in
     * the inventory - 0 to 8 - and everything in front of it is the tab's stock, which the player does
     * not own at all.
     * <p>Told apart by where the slot sits and what it claims, because nothing else is reliable: the
     * wrappers are put into the list directly rather than added to the menu, so they never get an index
     * and every one of them reads as slot zero. The hotbar is the last nine of either screen, and only
     * on a tab that is not the inventory does it call itself 0 to 8.
     *
     * @param inTheScreen    where the slot sits in the creative screen's own list of slots
     * @param slotsInScreen  how many slots that list has
     * @param containerSlot  what the slot says it stands for
     * @param playersOwn     whether the slot draws from the player's own inventory at all
     */
    public static int creativeSlot(int inTheScreen, int slotsInScreen, int containerSlot, boolean playersOwn) {
        if (!playersOwn || containerSlot < 0) {
            return -1;
        }
        boolean alongTheBottom = inTheScreen >= 0 && inTheScreen >= slotsInScreen - HOTBAR_SLOTS;
        return alongTheBottom && containerSlot < HOTBAR_SLOTS
                ? inTheirOwnMenu(containerSlot)
                : containerSlot;
    }
}
