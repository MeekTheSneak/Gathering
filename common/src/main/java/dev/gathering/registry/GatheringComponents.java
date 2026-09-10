package dev.gathering.registry;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import dev.gathering.item.PackComponent;
import dev.gathering.item.SealedComponent;
import dev.gathering.item.StoryComponent;
import net.minecraft.core.component.DataComponentType;

/**
 * The mod's data component types, bound by the loader bootstrap.
 * <p>Seven: what a card is, where it has been, what a deck is, which deck it is, what a sealed
 * pack is, what a sealed box is, and - on a deck a draft handed out - what it may be built
 * from. Everything else about a card is derived from a cache rather than stored on the stack.
 */
public final class GatheringComponents {

    public static final String CARD_ID = "card";
    public static final String DECK_ID = "deck";
    public static final String POOL_ID = "drafted_pool";
    public static final String PACK_ID = "pack";
    public static final String SEALED_ID = "sealed";
    public static final String STORY_ID = "story";
    public static final String DECK_HANDLE_ID = "deck_handle";

    public static final Registered<DataComponentType<CardComponent>> CARD = new Registered<>(CARD_ID);
    public static final Registered<DataComponentType<DeckComponent>> DECK = new Registered<>(DECK_ID);
    public static final Registered<DataComponentType<DraftedPool>> POOL = new Registered<>(POOL_ID);
    public static final Registered<DataComponentType<PackComponent>> PACK = new Registered<>(PACK_ID);
    public static final Registered<DataComponentType<SealedComponent>> SEALED =
            new Registered<>(SEALED_ID);
    public static final Registered<DataComponentType<StoryComponent>> STORY =
            new Registered<>(STORY_ID);

    /**
     * Which deck this is, as an opaque handle.
     * <p>Not part of {@link DeckComponent}, because that is what a deck <em>is</em> and two
     * decks with the same cards in them are the same deck by every rule in this mod. This is
     * about the object rather than the contents: it is minted once when a pile of cards
     * becomes a deck item, and it never changes as the deck is renamed, resleeved or edited.
     * <p>It exists for one thing. The owner's real decklist is pushed to them separately from
     * the item, and the client has to know which item that list is about - which it used to
     * work out by comparing the name and the card count, so somebody carrying two sixty-card
     * decks both called "Deck" could be shown one list while holding the other. Appearance is
     * not identity.
     * <p>Public, and nothing is given away by it: it is a random number that says nothing
     * about the cards. What it lets a client do is know when it is holding a deck it has not
     * been told the contents of yet, and say so instead of guessing.
     */
    public static final Registered<DataComponentType<java.util.UUID>> DECK_HANDLE =
            new Registered<>(DECK_HANDLE_ID);

    private GatheringComponents() {
    }

    public static DataComponentType<CardComponent> createCardType() {
        return DataComponentType.<CardComponent>builder()
                .persistent(CardComponent.CODEC)
                .networkSynchronized(CardComponent.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    /**
     * Where a card has been.
     * <p>Not cached the way the card's own component is: almost no card has one, so there is
     * nothing to cache for almost every card, and the ones that do have one are looked at
     * rather than drawn in their hundreds.
     */
    public static DataComponentType<StoryComponent> createStoryType() {
        return DataComponentType.<StoryComponent>builder()
                .persistent(StoryComponent.CODEC)
                .networkSynchronized(StoryComponent.STREAM_CODEC)
                .build();
    }

    /** Which deck an item is, as an opaque handle. See {@link #DECK_HANDLE}. */
    public static DataComponentType<java.util.UUID> createDeckHandleType() {
        return DataComponentType.<java.util.UUID>builder()
                .persistent(net.minecraft.core.UUIDUtil.CODEC)
                .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    /**
     * A deck, and what everybody else is told about it.
     * <p>The saved form is the whole deck; the synchronized form is not. An item component
     * goes to every client that can see the item, so carrying a deck used to hand the list to
     * the room - and hiding the tooltip protects nothing from a client that reads what it was
     * sent. What crosses is the box: a name, a note, a colour, sleeves, the commanders, and
     * how thick each part is. See {@link DeckComponent#PUBLIC_STREAM_CODEC}, and
     * {@link dev.gathering.network.MyDeckPayload} for how the owner gets the real thing.
     */
    public static DataComponentType<DeckComponent> createDeckType() {
        return DataComponentType.<DeckComponent>builder()
                .persistent(DeckComponent.CODEC)
                .networkSynchronized(DeckComponent.PUBLIC_STREAM_CODEC)
                .build();
    }

    public static DataComponentType<PackComponent> createPackType() {
        return DataComponentType.<PackComponent>builder()
                .persistent(PackComponent.CODEC)
                .networkSynchronized(PackComponent.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    public static DataComponentType<SealedComponent> createSealedType() {
        return DataComponentType.<SealedComponent>builder()
                .persistent(SealedComponent.CODEC)
                .networkSynchronized(SealedComponent.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    /**
     * What somebody drafted, which is theirs until they build out of it.
     * <p>Synchronized as a count for the same reason a deck is: the pool is the whole of what
     * a drafter knows that their opponents do not, and it travelled on a held item.
     */
    public static DataComponentType<DraftedPool> createPoolType() {
        return DataComponentType.<DraftedPool>builder()
                .persistent(DraftedPool.CODEC)
                .networkSynchronized(DraftedPool.PUBLIC_STREAM_CODEC)
                .build();
    }
}
