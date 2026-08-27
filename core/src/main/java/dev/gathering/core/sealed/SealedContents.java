package dev.gathering.core.sealed;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a shop can actually hand somebody when they buy a product.
 *
 * <p>Boosters and exact cards, all the way down. A display box is thirty of the pack it names;
 * a bundle is nine packs and a foil promo; a case is six of the box. Every one of those is
 * something the mod can put in a hand.
 *
 * <p>And decks. A Commander precon names its hundred cards rather than listing them, so what
 * comes out depends on the catalog having read the list: where it has, the buyer gets the
 * deck sleeved up with its commander already in the command zone, which is the whole of what
 * was in that box. Where it has not, the product is not something this can hand over and it
 * comes back empty - taking somebody's diamonds and giving them the sample pack that came in
 * the box would be worse than not stocking it.
 *
 * <p>The dice, the deck boxes and the life counters are simply not given. They are not cards
 * and there is nothing to make of them.
 *
 * <p>Pure.
 */
public final class SealedContents {

    /**
     * How deep a product may hold other products before this gives up.
     *
     * <p>A case holds boxes holds packs. Four is room to spare and it exists because the data
     * is somebody else's: a product that holds itself would otherwise never finish.
     */
    public static final int MOST_NESTING = 4;

    /**
     * As many pieces as one purchase may come to. A case of six boxes is 216.
     *
     * <p>Counted in cards where a deck is involved, so a case of precons is measured by what
     * would actually have to be built rather than by how few boxes it looks like.
     */
    public static final int MOST_PIECES = 512;

    private SealedContents() {
    }

    /** Everything one product turns into. */
    public record Bag(List<SealedProduct.Booster> boosters, List<CardIdentity> cards,
            List<SealedDeck> decks) {

        public Bag {
            boosters = boosters == null ? List.of() : List.copyOf(boosters);
            cards = cards == null ? List.of() : List.copyOf(cards);
            decks = decks == null ? List.of() : List.copyOf(decks);
        }

        public boolean isEmpty() {
            return boosters.isEmpty() && cards.isEmpty() && decks.isEmpty();
        }

        /**
         * How many things this comes to.
         *
         * <p>A deck counts as its cards, because that is what has to be built and put in a
         * box; a hundred-card precon is not one piece however few slots it takes up.
         */
        public int pieces() {
            int cardsInDecks = 0;
            for (SealedDeck deck : decks) {
                cardsInDecks += deck.size();
            }
            return boosters.size() + cards.size() + cardsInDecks;
        }
    }

    /**
     * One product's contents, one level down.
     *
     * <p>What tearing the shrink wrap off actually gives you: a case opens into six boxes, a
     * box into thirty-six packs, a pack into cards. Not the same thing as {@link Bag}, which
     * goes all the way to the bottom and exists to answer whether a shop could sell the thing
     * at all - opening a case straight into two hundred and sixteen loose packs would skip the
     * best part.
     */
    public record Layer(List<SealedProduct.Booster> boosters, List<Inside> boxes,
            List<CardIdentity> cards, List<SealedDeck> decks) {

        public static final Layer NOTHING = new Layer(List.of(), List.of(), List.of(), List.of());

        /** So many of one product, as itself rather than as its id. */
        public record Inside(SealedProduct product, int count) {
        }

        public Layer {
            boosters = boosters == null ? List.of() : List.copyOf(boosters);
            boxes = boxes == null ? List.of() : List.copyOf(boxes);
            cards = cards == null ? List.of() : List.copyOf(cards);
            decks = decks == null ? List.of() : List.copyOf(decks);
        }

        public boolean isEmpty() {
            return boosters.isEmpty() && boxes.isEmpty() && cards.isEmpty() && decks.isEmpty();
        }
    }

    /**
     * What comes out of one product when it is opened.
     *
     * <p>Empty where anything in it could not be resolved, which is the same answer
     * {@link #of} gives and for the same reason: a box that opens into eleven packs and a
     * hole is worse than a box that will not open.
     */
    public static Layer opening(SealedProduct product, SealedCatalog catalog) {
        if (product == null || product.isOneBooster()) {
            return Layer.NOTHING;
        }
        List<Layer.Inside> boxes = new ArrayList<>();
        for (SealedProduct.Held held : product.contents().holds()) {
            SealedProduct inside = catalog == null ? null : catalog.byId(held.productId());
            if (inside == null || held.count() <= 0) {
                return Layer.NOTHING;
            }
            boxes.add(new Layer.Inside(inside, held.count()));
        }
        List<SealedDeck> decks = new ArrayList<>();
        for (SealedProduct.InDeck named : product.contents().decks()) {
            SealedDeck deck = catalog == null
                    ? null : catalog.deck(named.setCode(), named.name());
            if (deck == null || deck.isEmpty()) {
                return Layer.NOTHING;
            }
            decks.add(deck);
        }
        return new Layer(product.contents().boosters(), boxes,
                product.contents().cards(), decks);
    }

    /**
     * What is in a product, or nothing where it is not something a shop can hand over.
     *
     * @param catalog where the products a box holds are looked up
     */
    public static Optional<Bag> of(SealedProduct product, SealedCatalog catalog) {
        List<SealedProduct.Booster> boosters = new ArrayList<>();
        List<CardIdentity> cards = new ArrayList<>();
        List<SealedDeck> decks = new ArrayList<>();
        if (!gather(product, catalog, MOST_NESTING, boosters, cards, decks)) {
            return Optional.empty();
        }
        Bag bag = new Bag(boosters, cards, decks);
        if (bag.isEmpty() || bag.pieces() > MOST_PIECES) {
            return Optional.empty();
        }
        return Optional.of(bag);
    }

    /** Whether a shop could hand this over at all. */
    public static boolean canBeHandedOver(SealedProduct product, SealedCatalog catalog) {
        return of(product, catalog).isPresent();
    }

    /** @return false where something in here is not a booster, a card or a deck it could read */
    private static boolean gather(SealedProduct product, SealedCatalog catalog,
            int depth, List<SealedProduct.Booster> boosters, List<CardIdentity> cards,
            List<SealedDeck> decks) {
        if (product == null) {
            return false;
        }
        boosters.addAll(product.contents().boosters());
        cards.addAll(product.contents().cards());
        for (SealedProduct.InDeck named : product.contents().decks()) {
            SealedDeck deck = catalog == null
                    ? null : catalog.deck(named.setCode(), named.name());
            if (deck == null || deck.isEmpty()) {
                // Named in the box and nowhere this could read. Sold whole or not at all.
                return false;
            }
            decks.add(deck);
        }

        if (product.contents().holds().isEmpty()) {
            return true;
        }
        if (depth <= 0 || catalog == null) {
            return false;
        }
        for (SealedProduct.Held held : product.contents().holds()) {
            SealedProduct inside = catalog.byId(held.productId());
            if (inside == null) {
                // A box naming a pack out of another set's catalog. Sold whole or not at
                // all: half a box is worse than no box.
                return false;
            }
            for (int one = 0; one < held.count(); one++) {
                if (boosters.size() + cards.size() + decks.size() > MOST_PIECES) {
                    return false;
                }
                if (!gather(inside, catalog, depth - 1, boosters, cards, decks)) {
                    return false;
                }
            }
        }
        return true;
    }
}
