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

    private ClientHoverState() {
    }

    public static void setHovered(ItemStack stack) {
        hovered = stack == null ? ItemStack.EMPTY : stack;
    }

    public static ItemStack hovered() {
        return hovered;
    }

    public static void clear() {
        hovered = ItemStack.EMPTY;
    }
}
