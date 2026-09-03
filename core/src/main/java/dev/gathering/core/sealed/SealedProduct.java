package dev.gathering.core.sealed;

import dev.gathering.core.card.CardIdentity;
import java.util.List;
import java.util.Locale;

/**
 * One thing that was really sold in a shop.
 * <p>Not a thing this mod invented. Every product here is a product that existed on a shelf,
 * read from the sealed-product data published for its set, and that is the whole point: what
 * a player can find in the world or buy from a shop must be something somebody could have
 * bought in paper. A set that came only as precons has no booster, and a booster nobody
 * printed is not a product however convenient it would be to have one.
 * <p>The three shapes the design calls for all fall out of the contents rather than being
 * separate kinds. A pack is a product holding one booster arrangement. A box is a product
 * holding twelve of another product. A bundle is a product holding some of each, an exact
 * printing or two, and a spindown die nobody will ever render.
 *
 * @param productId  the published id for it, so a container can name what it holds
 * @param name       what it was called on the shelf
 * @param setCode    which set it belongs to, lower case as Scryfall writes it
 * @param category   what shape it is - "booster_pack", "booster_box", "bundle", "deck"
 * @param subtype    which of that shape - "play", "collector", "commander"
 * @param cardCount  how many cards are in it, as published
 */
public record SealedProduct(
        String productId,
        String name,
        String setCode,
        String category,
        String subtype,
        int cardCount,
        Contents contents) {

    /**
     * What is inside, in the four kinds the published data distinguishes.
     *
     * @param unbridged how many cards were published in it that could not be turned into a
     *                  printing, and so are not in {@code cards}. Counted rather than
     *                  forgotten: a product read without a card bridge would otherwise look
     *                  like a product with nothing in it, and a bundle with a promo in it
     *                  would read as a plain booster.
     */
    public record Contents(
            List<Booster> boosters,
            List<Held> holds,
            List<CardIdentity> cards,
            List<InDeck> decks,
            List<String> extras,
            int unbridged) {

        public Contents {
            boosters = boosters == null ? List.of() : List.copyOf(boosters);
            holds = holds == null ? List.of() : List.copyOf(holds);
            cards = cards == null ? List.of() : List.copyOf(cards);
            decks = decks == null ? List.of() : List.copyOf(decks);
            extras = extras == null ? List.of() : List.copyOf(extras);
            unbridged = Math.max(0, unbridged);
        }

        /** Contents where every card published in it was bridged. */
        public Contents(List<Booster> boosters, List<Held> holds, List<CardIdentity> cards,
                List<InDeck> decks, List<String> extras) {
            this(boosters, holds, cards, decks, extras, 0);
        }

        public boolean isEmpty() {
            return boosters.isEmpty() && holds.isEmpty() && cards.isEmpty() && decks.isEmpty()
                    && unbridged == 0;
        }
    }

    /**
     * A deck that comes in the box, named rather than listed.
     * <p>The published data puts a precon's hundred cards elsewhere in the same file and names
     * them here, so a product on its own says a deck is in the box and not what is in the
     * deck. By name and set, because that is the whole of what the product says and because
     * two sets have each published a deck called "Peace Offering".
     */
    public record InDeck(String name, String setCode) {

        public InDeck {
            name = name == null ? "" : name.trim();
            setCode = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        }
    }

    /** A booster arrangement this product opens as, named the way collation names it. */
    public record Booster(String setCode, String kind) {

        public Booster {
            setCode = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
            kind = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * So many of another product.
     * <p>By its published id rather than by its contents, because a box of twelve boosters is
     * twelve of the same product a player could buy singly - and a box that copied their
     * contents would be a second place for a booster's odds to live.
     */
    public record Held(String productId, String name, int count) {

        public Held {
            productId = productId == null ? "" : productId;
            name = name == null ? "" : name;
            count = Math.max(0, count);
        }
    }

    public SealedProduct {
        productId = productId == null ? "" : productId;
        name = name == null ? "" : name;
        setCode = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        category = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        subtype = subtype == null ? "" : subtype.trim().toLowerCase(Locale.ROOT);
        cardCount = Math.max(0, cardCount);
        contents = contents == null
                ? new Contents(List.of(), List.of(), List.of(), List.of(), List.of())
                : contents;
    }

    /**
     * Whether this is a single booster, and therefore something the opener can already open.
     * <p>One arrangement and nothing else in the wrapper. A bundle holds boosters too, and is
     * not one.
     */
    public boolean isOneBooster() {
        return contents.boosters().size() == 1
                && contents.holds().isEmpty()
                && contents.cards().isEmpty()
                && contents.unbridged() == 0
                && contents.decks().isEmpty();
    }

    /** The arrangement a single booster opens as, or null if this is not one. */
    public Booster asBooster() {
        return isOneBooster() ? contents.boosters().get(0) : null;
    }

    /** Whether this is a product made of other products - a box, a case, a bundle. */
    public boolean holdsOtherProducts() {
        return !contents.holds().isEmpty();
    }

    /** How many of everything it holds, counted one level down rather than all the way. */
    public int piecesHeld() {
        int pieces = 0;
        for (Held held : contents.holds()) {
            pieces += held.count();
        }
        return pieces;
    }
}
