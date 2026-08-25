package dev.gathering.registry;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import dev.gathering.item.PackComponent;
import net.minecraft.core.component.DataComponentType;

/**
 * The mod's data component types, bound by the loader bootstrap.
 *
 * <p>Four: what a card is, what a deck is, what a sealed pack is, and - on a deck a draft
 * handed out - what it may be built from. Everything else about a card is derived from a
 * cache rather than stored on the stack.
 */
public final class GatheringComponents {

    public static final String CARD_ID = "card";
    public static final String DECK_ID = "deck";
    public static final String POOL_ID = "drafted_pool";
    public static final String PACK_ID = "pack";

    public static final Registered<DataComponentType<CardComponent>> CARD = new Registered<>(CARD_ID);
    public static final Registered<DataComponentType<DeckComponent>> DECK = new Registered<>(DECK_ID);
    public static final Registered<DataComponentType<DraftedPool>> POOL = new Registered<>(POOL_ID);
    public static final Registered<DataComponentType<PackComponent>> PACK = new Registered<>(PACK_ID);

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

    public static DataComponentType<PackComponent> createPackType() {
        return DataComponentType.<PackComponent>builder()
                .persistent(PackComponent.CODEC)
                .networkSynchronized(PackComponent.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    public static DataComponentType<DraftedPool> createPoolType() {
        return DataComponentType.<DraftedPool>builder()
                .persistent(DraftedPool.CODEC)
                .networkSynchronized(DraftedPool.STREAM_CODEC)
                .build();
    }
}
