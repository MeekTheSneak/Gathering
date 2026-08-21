package dev.gathering.item;

import dev.gathering.registry.Registered;
import net.minecraft.world.item.Item;

/**
 * Everything the mod registers into vanilla registries, named once and bound by whichever
 * loader is running.
 *
 * <p>The factories live here rather than in the loader modules so both loaders register
 * identical objects; only the registration mechanism differs between them, which is exactly
 * the size the platform-specific surface is supposed to be.
 */
public final class GatheringContent {

    public static final String CARD_ID = "card";
    public static final String DECK_ID = "deck";

    public static final Registered<Item> CARD = new Registered<>(CARD_ID);
    public static final Registered<Item> DECK = new Registered<>(DECK_ID);

    private GatheringContent() {
    }

    /** Cards never stack: two cards are two objects, even when they are the same printing. */
    public static Item createCard() {
        return new CardItem(new Item.Properties().stacksTo(1));
    }

    public static Item createDeck() {
        return new DeckItem(new Item.Properties().stacksTo(1));
    }
}
