package dev.gathering.item;

import net.minecraft.world.item.Item;

/**
 * What a card shop takes.
 * <p>The mod's own currency, and the reason the shop is not a vending machine. An emerald is
 * farmed - a trading hall makes them by the stack without anybody leaving the village - so a
 * shop priced in emeralds was the cheapest path to a collection and exploring was a flourish.
 * A coin is found in a chest, somewhere, and cannot be made, smelted, traded for or grown.
 * <p>It used to say both of those things in the hand. It does not any more: the owner's call
 * (2026-09-18) is that only a card, a deck and a sealed product - the things that genuinely carry
 * information about themselves - explain themselves in a tooltip, and that a coin, a chair and a
 * table are things you learn by using. A line under every item in the mod is a line nobody reads
 * under the two that matter.
 */
public final class ManaCoinItem extends Item {

    public ManaCoinItem(Properties properties) {
        super(properties);
    }
}
