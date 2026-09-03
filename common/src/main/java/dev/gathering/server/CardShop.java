package dev.gathering.server;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.sealed.SealedCatalog;
import dev.gathering.core.sealed.SealedContents;
import dev.gathering.core.sealed.SealedDeck;
import dev.gathering.core.sealed.SealedProduct;
import dev.gathering.core.sealed.SealedShelf;
import dev.gathering.core.sealed.ShopCounter;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.item.SealedComponent;
import dev.gathering.item.SealedItem;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the shopkeeper has, and what comes out of it.
 * <p>The second of the two ways into a collection. Sealed only, ever: the manufacturer sells
 * sealed and singles come from people, which is the line paper Magic proved and the one thing
 * an infinite-stock shop must not cross.
 * <p>Everything above a booster lives here rather than in the world. A chest that could hold a
 * display box would make the shop pointless, and a shop that could not sell one would make the
 * whole profession a vending machine; so a pack is something you find and a box is something
 * you buy, and the shopkeeper's level says how big a box.
 * <p>What each thing costs is {@link dev.gathering.core.sealed.SealedPrice}'s and what each
 * thing turns into is {@link SealedContents}'s. Nothing here decides either.
 * <p>Worked out once, when the server starts, and read from memory afterwards - a trade is
 * offered deep inside a villager's brain with no time to reach a network.
 */
public final class CardShop {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /**
     * The shelf and the catalog it was built from, in one object.
     * <p>Two fields would be two halves of one answer: a sale that read the new shelf and the
     * old catalog would find a product it could not look up and hand over nothing.
     */
    private record Stock(SealedShelf shelf, SealedCatalog catalog) {

        static final Stock NOTHING = new Stock(SealedShelf.EMPTY, SealedCatalog.EMPTY);

        boolean isEmpty() {
            return shelf.isEmpty();
        }
    }

    private static volatile Stock stock = Stock.NOTHING;

    /** What {@link #stockedFor} says when nothing has been stocked yet. */
    private static final long NOTHING_STOCKED = -1;

    /**
     * Which turnover the shelf on it was built for, or -1 for a shelf nobody has stocked.
     * <p>Read on the server thread by every shopkeeper being looked at, written by whichever
     * restock finishes. A stale read costs one more turnover of yesterday's stock.
     */
    private static volatile long stockedFor = NOTHING_STOCKED;

    /** So a slow restock is not started a second time by the next villager to be looked at. */
    private static final java.util.concurrent.atomic.AtomicBoolean RESTOCKING =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * How many sets are behind the counter at once.
     * <p>Every set on the shelf is that set's own file - three or four megabytes - because
     * what a precon holds is published beside it and a box that cannot say what is in it
     * cannot be sold. A server drawing from every set ever printed would be reading a
     * gigabyte to stock one counter, so the shop stocks a window of them and the window moves
     * on with the shelf. Over a few turnovers the whole of what a server draws from has been
     * on the counter.
     */
    private static final int MOST_SETS_STOCKED = 6;

    private CardShop() {
    }

    /**
     * Whether the shelf is being read right now.
     * <p>A shopkeeper looked at during one is on the last turnover's stock, which is the
     * intended answer rather than a fault - so anything explaining a shop can say so.
     */
    public static boolean isRestocking() {
        return RESTOCKING.get();
    }

    /** Which turnover the shelf is for, or -1 if nothing has been stocked yet. */
    public static long stockedFor() {
        return stockedFor;
    }

    /**
     * Brings the shelf up to this turnover, in the background, if it is not there already.
     * <p>Called from the server thread by every shopkeeper about to be looked at, so it must
     * be cheap to ask and must never block: the usual answer is one volatile read. When the
     * turnover really has moved on, the reading is started and the old shelf stays up until
     * the new one lands - a shopkeeper with yesterday's stock being much better than one with
     * an empty counter.
     */
    public static void stockFor(long rotation) {
        if (rotation == stockedFor || !isStocking()) {
            return;
        }
        CollationService collation = CollationService.active().orElse(null);
        if (collation == null || !RESTOCKING.compareAndSet(false, true)) {
            return;
        }
        int perBooster = ServerSettings.get().collecting().sealedPriceBooster();
        SetsInPlay.wanted()
                .thenComposeAsync(codes -> readAll(collation,
                                ShopCounter.behindTheCounter(codes, rotation, MOST_SETS_STOCKED)),
                        collation.worker())
                .thenApply(read -> build(read, perBooster))
                .whenComplete((built, failure) -> {
                    try {
                        if (failure != null) {
                            LOGGER.warn("Could not read what this server's sets were sold as, so "
                                    + "the shop has nothing to sell", failure);
                            return;
                        }
                        stock = built;
                        stockedFor = rotation;
                        LOGGER.info("The shop sells {} product(s) across {} set(s)",
                                built.shelf().items().size(), built.shelf().items().stream()
                                        .map(item -> item.product().setCode()).distinct().count());
                    } finally {
                        RESTOCKING.set(false);
                    }
                });
    }


    /**
     * Whether this server's shop is open at all.
     * <p>Worth asking on its own, because a shopkeeper with nothing to sell shakes their head
     * at everybody and looks like a broken villager rather than a shut shop - so anything
     * that hires one, or explains one, needs to be able to tell those apart.
     */
    public static boolean isStocking() {
        var settings = ServerSettings.get();
        return settings.modes().collectionEnabled() && settings.collecting().sealedStoreEnabled();
    }

    /**
     * Empties the shelf, so the next shopkeeper somebody looks at stocks it again.
     * <p>Between servers, and after any settings change - and at the moment a server finishes
     * starting, which is why nothing is read here. Which sets the shop stocks depends on the
     * turnover the world is on, and there is no world to ask yet; the first shopkeeper
     * somebody walks up to says which turnover it is, and {@link #stockFor} does the reading.
     */
    public static void clear() {
        stock = Stock.NOTHING;
        stockedFor = NOTHING_STOCKED;
    }


    /**
     * What every shopkeeper of this level has on the counter.
     * <p>Every one of them, the same. Which is what {@link ShopCounter} is for and why it is
     * not decided per villager: trades are fixed when somebody takes the job, so a shelf that
     * varied by shopkeeper would be a shelf you could break the counter and re-place until it
     * offered what you wanted.
     * <p>Empty until the sets have been read, which is the honest answer: a villager who takes
     * the job in the first few seconds of a server offers nothing rather than offering
     * something wrong, and offers properly at their next level.
     */
    public static List<SealedShelf.Item> counterAt(int level, long rotation) {
        Stock now = stock;
        return ShopCounter.at(now.shelf(), now.catalog(), level, rotation);
    }

    /** The item somebody is handed when they buy this. */
    public static ItemStack itemFor(SealedProduct product) {
        if (product == null) {
            return ItemStack.EMPTY;
        }
        SealedProduct.Booster booster = product.asBooster();
        if (booster != null) {
            return PackItem.of(new PackComponent(booster.setCode(), booster.kind()));
        }
        return SealedItem.of(new SealedComponent(
                product.setCode(), product.productId(), product.name()));
    }

    /**
     * What is in a box, one level down.
     * <p>A case opens into six boxes and a box into thirty-six packs, rather than a case
     * opening into two hundred and sixteen loose packs. Opening the box is the good part and
     * doing it three times is three good parts.
     * <p>Empty where the product is not one this server can look up - a box bought on a server
     * that has since been pointed at other sets, or an item an operator wrote by hand. Nothing
     * is destroyed on that path; whoever calls this hands the box back.
     */
    public static List<ItemStack> openingOf(SealedComponent box) {
        if (box == null || !box.isReal()) {
            return List.of();
        }
        Stock now = stock;
        SealedProduct product = now.catalog().byId(box.productId());
        if (product == null) {
            return List.of();
        }
        SealedContents.Layer layer = SealedContents.opening(product, now.catalog());
        if (layer.isEmpty()) {
            return List.of();
        }

        List<ItemStack> out = new ArrayList<>();
        for (SealedProduct.Booster booster : layer.boosters()) {
            out.add(PackItem.of(new PackComponent(booster.setCode(), booster.kind())));
        }
        for (SealedContents.Layer.Inside inside : layer.boxes()) {
            ItemStack one = itemFor(inside.product());
            for (int copy = 0; copy < inside.count(); copy++) {
                out.add(one.copy());
            }
        }
        for (CardIdentity card : layer.cards()) {
            out.add(CardItem.of(CardComponent.of(card)));
        }
        for (SealedDeck deck : layer.decks()) {
            out.add(deckItem(deck));
        }
        return List.copyOf(out);
    }

    /**
     * A precon, sleeved and ready to sit down with.
     * <p>Its commander is in the command zone already, because that is where it was on the box
     * and because a hundred-card deck whose commander is the ninety-first card in the list is
     * a deck somebody has to fix before they can play it.
     * <p>Nobody's deck in particular: it has just been bought, so it belongs to whoever is
     * holding it, the same way it would if they had walked out of a shop with the box.
     */
    private static ItemStack deckItem(SealedDeck deck) {
        return DeckItem.of(new DeckComponent(
                deck.name(), "", Optional.empty(),
                components(deck.mainboard()),
                components(deck.commanders()),
                components(deck.sideboard()))
                // A precon is a printed product: every copy of it is the same box, the way
                // two of the same deck on a shop shelf are.
                .colored(dev.gathering.core.card.DeckColors.pick(deck.name().hashCode())));
    }

    private static List<CardComponent> components(List<CardIdentity> cards) {
        List<CardComponent> made = new ArrayList<>(cards.size());
        for (CardIdentity card : cards) {
            made.add(CardComponent.of(card));
        }
        return List.copyOf(made);
    }

    // ------------------------------------------------------------------ bits

    /**
     * Every set's catalog, read one set at a time.
     * <p>On the collation worker, one after another, because every set is a file to fetch and
     * a server asking for a dozen of them at once is a server asking somebody else's host for
     * forty megabytes at once.
     */
    private static CompletableFuture<Map<String, CollationService.Catalog>> readAll(
            CollationService collation, List<String> codes) {
        CompletableFuture<Map<String, CollationService.Catalog>> reading =
                CompletableFuture.completedFuture(new LinkedHashMap<>());
        for (String code : codes) {
            reading = reading.thenCompose(read -> collation.catalogFor(code)
                    .handle((found, failure) -> {
                        if (failure != null) {
                            LOGGER.warn("Could not read what {} was sold as, so the shop does "
                                    + "not stock it", code, failure);
                            return read;
                        }
                        read.put(code, found);
                        return read;
                    }));
        }
        return reading;
    }

    /**
     * One shelf out of every set that was read.
     * <p>The catalog spans all of them before any shelf is built, because a product can name
     * something published beside it: a Commander box holds a booster from the set it came out
     * with, and a starter kit names decks from another file entirely. Building each set's
     * shelf against only its own file would drop those - correctly, and needlessly.
     */
    private static Stock build(Map<String, CollationService.Catalog> read, int perBooster) {
        if (read.isEmpty()) {
            return Stock.NOTHING;
        }
        List<SealedCatalog> lookups = new ArrayList<>();
        for (CollationService.Catalog one : read.values()) {
            lookups.add(one.lookup());
        }
        SealedCatalog catalog = SealedCatalog.of(lookups);

        List<SealedShelf> shelves = new ArrayList<>();
        for (CollationService.Catalog one : read.values()) {
            shelves.add(SealedShelf.of(one.products(), catalog, perBooster));
        }
        return new Stock(SealedShelf.of(shelves), catalog);
    }
}
