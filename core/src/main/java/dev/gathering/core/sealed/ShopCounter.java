package dev.gathering.core.sealed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What is actually on the counter, at one level.
 *
 * <p>The same everywhere. Every card shop in the world stocks one shelf: a shopkeeper in one
 * village and a shopkeeper in the next offer the same two things at the same level, for the
 * same price. Two reasons, and the second is the important one.
 *
 * <p>The first is that a chain of shops that all stock the same thing is what a chain of shops
 * is. The second is that anything else is a reroll. A villager's trades are decided once, when
 * they take the job, so a shelf that varied per shopkeeper would be a shelf you could break the
 * counter and re-place until it offered what you wanted - which is the librarian trick, aimed
 * at boosters, and it would make every price in the mod meaningless.
 *
 * <p>A villager can only ever be given two trades per level, which is vanilla's number and not
 * this mod's to change. So the two are chosen for variety rather than taken off the top: two
 * collector boosters of the same set on the same counter would waste half of it - and the shelf
 * turns over, so the rest of what a set sold comes round in its own time. Every shop turns over
 * together, on the same clock, which is what keeps them all the same shop.
 *
 * <p>Pure.
 */
public final class ShopCounter {

    /** How many things a villager is given at each level. Vanilla's number, not this mod's. */
    public static final int PER_LEVEL = 2;

    /**
     * Which boosters a counter leads with, when it has to choose.
     *
     * <p>The ordinary pack first. Somebody walking into a card shop for the first time is
     * there for the booster everybody means by "a booster", and the collector pack is the one
     * you go back for.
     */
    private static final List<String> FIRST = List.of("play", "draft", "set", "collector");

    private ShopCounter() {
    }

    /**
     * The two things a shopkeeper of this level has.
     *
     * <p>Deterministic: the same shelf gives the same answer every time, on every counter, on
     * every server that read the same sets.
     */
    public static List<SealedShelf.Item> at(
            SealedShelf shelf, SealedCatalog catalog, int level) {
        return at(shelf, catalog, level, 0);
    }

    /**
     * The two things a shopkeeper of this level has, this many turnovers in.
     *
     * <p>Each turnover moves along by what a counter holds, so a level with six things at it
     * shows all six over three of them and then comes round again. The whole of a set is
     * buyable eventually without any single visit offering the whole of it.
     *
     * @param rotation which turnover this is, counted from when the world began
     */
    public static List<SealedShelf.Item> at(
            SealedShelf shelf, SealedCatalog catalog, int level, long rotation) {
        return pick(ShopTier.at(shelf, catalog, level), PER_LEVEL, rotation);
    }

    /**
     * As many of these as will fit, preferring variety.
     *
     * <p>One of each kind of thing first - a play booster and a collector booster rather than
     * two collector boosters, a display box and a bundle rather than two bundles - and then
     * whatever is left in shelf order, so a level with only one kind of thing at it still
     * fills its slots.
     */
    public static List<SealedShelf.Item> pick(List<SealedShelf.Item> stock, int howMany) {
        return pick(stock, howMany, 0);
    }

    /** The same, moved along by this many turnovers. */
    public static List<SealedShelf.Item> pick(
            List<SealedShelf.Item> stock, int howMany, long rotation) {
        if (stock == null || stock.isEmpty() || howMany <= 0) {
            return List.of();
        }
        List<SealedShelf.Item> ordered = turned(byPreference(stock), rotation, howMany);

        List<SealedShelf.Item> taken = new ArrayList<>(howMany);
        Set<String> families = new LinkedHashSet<>();
        for (SealedShelf.Item item : ordered) {
            if (taken.size() >= howMany) {
                break;
            }
            if (families.add(familyOf(item))) {
                taken.add(item);
            }
        }
        for (SealedShelf.Item item : ordered) {
            if (taken.size() >= howMany) {
                break;
            }
            if (!taken.contains(item)) {
                taken.add(item);
            }
        }
        return List.copyOf(taken);
    }

    /**
     * The same shelf, started from wherever this turnover starts.
     *
     * <p>Rotated rather than reshuffled: a shelf that shuffled would show the same thing twice
     * running as often as not, and would have no order for somebody to learn.
     */
    private static List<SealedShelf.Item> turned(
            List<SealedShelf.Item> ordered, long rotation, int howMany) {
        int size = ordered.size();
        if (rotation == 0 || size <= howMany) {
            // Nothing to turn: everything at this level is on the counter already.
            return ordered;
        }
        int from = (int) Math.floorMod(rotation * howMany, size);
        if (from == 0) {
            return ordered;
        }
        List<SealedShelf.Item> turned = new ArrayList<>(size);
        turned.addAll(ordered.subList(from, size));
        turned.addAll(ordered.subList(0, from));
        return turned;
    }

    /**
     * What makes two things the same sort of thing.
     *
     * <p>For a booster it is which booster - a play pack and a collector pack are two things
     * worth having both of, and the same pack out of two sets is one thing twice. For anything
     * else it is what shape the box is.
     */
    private static String familyOf(SealedShelf.Item item) {
        SealedProduct product = item.product();
        SealedProduct.Booster booster = product.asBooster();
        return booster != null ? "pack:" + booster.kind() : "box:" + product.category();
    }

    /** Shelf order, except that the ordinary booster comes before the collector one. */
    private static List<SealedShelf.Item> byPreference(List<SealedShelf.Item> stock) {
        List<SealedShelf.Item> ordered = new ArrayList<>(stock);
        ordered.sort(java.util.Comparator.comparingInt(ShopCounter::rankOf));
        return ordered;
    }

    private static int rankOf(SealedShelf.Item item) {
        SealedProduct.Booster booster = item.product().asBooster();
        if (booster == null) {
            return FIRST.size();
        }
        String kind = booster.kind().toLowerCase(Locale.ROOT);
        for (int rank = 0; rank < FIRST.size(); rank++) {
            if (kind.equals(FIRST.get(rank))) {
                return rank;
            }
        }
        return FIRST.size();
    }
}
