package dev.gathering.client;

import net.minecraft.world.item.ItemStack;

/**
 * Which item stack the cursor is currently over.
 *
 * <p>Vanilla keeps the hovered slot as a protected field on the container screen, and
 * {@code :common} compiles against vanilla only - no loader access transformers - so the
 * answer has to come from the loader, which has an event that already knows it. Each
 * loader's client init feeds this from its own tooltip hook; everything above this line
 * stays loader-agnostic.
 *
 * <p>Client-only.
 */
public final class ClientHoverState {

    private static volatile ItemStack hovered = ItemStack.EMPTY;

    /**
     * A power and toughness written over this card's printed one, or empty.
     *
     * <p>Beside the stack rather than on it: it is a fact about one card in play, and the
     * stack is built from the printing so two copies of the same card would share it. Only
     * the table ever sets it; an inventory slot leaves it empty, which is what a card sitting
     * in a box has.
     */
    private static volatile String strength = "";

    private ClientHoverState() {
    }

    public static void setHovered(ItemStack stack) {
        setHovered(stack, "");
    }

    public static void setHovered(ItemStack stack, String writtenStrength) {
        hovered = stack == null ? ItemStack.EMPTY : stack;
        strength = writtenStrength == null ? "" : writtenStrength;
    }

    public static ItemStack hovered() {
        return hovered;
    }

    /** What somebody has written over this card's power and toughness, or empty. */
    public static String writtenStrength() {
        return strength;
    }

    public static void clear() {
        hovered = ItemStack.EMPTY;
        strength = "";
    }
}
