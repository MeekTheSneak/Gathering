package dev.gathering.registry;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import net.minecraft.core.component.DataComponentType;

/**
 * The mod's data component types, bound by the loader bootstrap.
 *
 * <p>Two, and only two: what a card is, and what a deck is. Everything else about a card is
 * derived from a cache rather than stored on the stack.
 */
public final class GatheringComponents {

    public static final String CARD_ID = "card";
    public static final String DECK_ID = "deck";

    public static final Registered<DataComponentType<CardComponent>> CARD = new Registered<>(CARD_ID);
    public static final Registered<DataComponentType<DeckComponent>> DECK = new Registered<>(DECK_ID);

    private GatheringComponents() {
    }

    public static DataComponentType<CardComponent> createCardType() {
        return DataComponentType.<CardComponent>builder()
                .persistent(CardComponent.CODEC)
                .networkSynchronized(CardComponent.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    public static DataComponentType<DeckComponent> createDeckType() {
        return DataComponentType.<DeckComponent>builder()
                .persistent(DeckComponent.CODEC)
                .networkSynchronized(DeckComponent.STREAM_CODEC)
                .build();
    }
}
