package dev.gathering.core.booster;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.SetRelease;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which printings of one set's family no ordinary way into a collection reaches.
 * <p>An Archive Pack is for one set: the cards out of that set, and the promo, Commander and other
 * sets released beside it, that a player cannot come by through play. A set a server draws from
 * reaches what its boosters hold and, with the shop open, what its sealed products hold; a set it
 * does not draw from reaches nothing, so all of that set belongs in its archive.
 * <p>Per family rather than across all of history at once. Working out every set there has ever been
 * at every start was a search per set on Scryfall's servers, several hundred of them, which Scryfall
 * answered by turning the server away. One family is a handful of searches, asked when somebody
 * opens its pack.
 * <p>Worked out from facts that do not depend on the server's settings - what each set printed, and
 * what its boosters and products could ever hold - so a change of settings is a new sum over facts
 * already known rather than another trip to the network.
 * <p>Pure.
 */
public final class ArchiveAudit {

    /**
     * What one set is known to contain, and what of it its own products can produce.
     *
     * @param catalog    every printing in the set that is a card a player could hold
     * @param inBoosters of those, what the set's boosters could ever hold
     * @param inProducts and what its sealed products could ever hold, boosters aside
     * @param reachKnown whether the last two were worked out at all. A set nobody draws from
     *                   does not need its products read to know none of it is reachable, so its
     *                   facts can stand without them - and are read again the day it is drawn from
     */
    public record SetFacts(String code, List<UUID> catalog, Set<UUID> inBoosters, Set<UUID> inProducts,
            boolean reachKnown) {

        public SetFacts {
            code = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
            catalog = catalog == null ? List.of() : List.copyOf(catalog);
            inBoosters = inBoosters == null ? Set.of() : Set.copyOf(inBoosters);
            inProducts = inProducts == null ? Set.of() : Set.copyOf(inProducts);
        }
    }

    /**
     * Set types that are not sets of cards a player collects.
     * <p>Tokens and emblems, gold-bordered memorabilia, and the digital-only Alchemy rebalances.
     * Everything else Scryfall lists is paper somebody opened, bought or was handed, however odd.
     */
    private static final Set<String> NOT_COLLECTED = Set.of("token", "memorabilia", "minigame", "alchemy");

    /** Card layouts that are not cards a deck holds: tokens, emblems, art cards, oversized formats. */
    private static final Set<String> NOT_A_CARD = Set.of(
            "token", "double_faced_token", "emblem", "art_series", "planar", "scheme", "vanguard", "augment",
            "host");

    private ArchiveAudit() {
    }

    /** Whether a set is worth auditing for the archive at all. */
    public static boolean isAudited(SetRelease set) {
        return set != null && !set.digital() && !NOT_COLLECTED.contains(set.type()) && !set.code().isBlank();
    }

    /**
     * Whether a printing's layout is a card's, rather than a token's, an emblem's or an art card's.
     * <p>Split out of {@link #isACard} because it is the only half of that question a pack needs. The
     * rest of it asks whether the printing exists on paper at all, which matters to an audit of what
     * a collection could contain and does not belong in a pack's own pool: a card whose data says
     * nothing about which games it is in still came out of the set, and refusing those emptied every
     * fallback pack on a server whose card data is thinner than Scryfall's own.
     */
    public static boolean isACardLayout(CardMetadata card) {
        return card != null
                && !NOT_A_CARD.contains(card.layout() == null ? "" : card.layout().toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a printing is a card that could go in a collection, rather than a token or an art card.
     * Which of a set's printings in another language count is {@link dev.gathering.core.card.ForeignPrintings}'s.
     */
    public static boolean isACard(CardMetadata card) {
        return card != null
                && card.scryfallId() != null
                && !card.digitalOnly()
                && !card.oversized()
                && card.games() != null && card.games().contains("paper")
                && !NOT_A_CARD.contains(card.layout() == null ? "" : card.layout().toLowerCase(Locale.ROOT));
    }

    /** The most parents followed up from a set, so a list naming a set its own grandparent still ends. */
    private static final int MOST_PARENTS = 8;

    /**
     * The family a set belongs to: the set at the top of its parents, or itself when it has none.
     * <p>{@code psos} and {@code soc} are both {@code sos}'s.
     */
    public static String familyOf(String code, Map<String, SetRelease> sets) {
        String at = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        for (int step = 0; step < MOST_PARENTS && sets != null; step++) {
            SetRelease set = sets.get(at);
            if (set == null || set.parent().isEmpty() || set.parent().equals(at)) {
                break;
            }
            at = set.parent();
        }
        return at;
    }

    /**
     * Every family an archive pack can be for, and the sets in each worth auditing, newest family
     * first. A family is named by the set at its top; one with nothing released or nothing
     * audited in it is not one.
     *
     * @param today the day to judge releases against, as Scryfall writes a date
     */
    public static Map<String, List<SetRelease>> families(Collection<SetRelease> sets, String today) {
        Map<String, SetRelease> byCode = new java.util.HashMap<>();
        if (sets != null) {
            for (SetRelease set : sets) {
                if (set != null) {
                    byCode.putIfAbsent(set.code(), set);
                }
            }
        }
        Map<String, List<SetRelease>> families = new java.util.HashMap<>();
        for (SetRelease set : byCode.values()) {
            if (isAudited(set) && today != null && set.wasOutBy(today)) {
                families.computeIfAbsent(familyOf(set.code(), byCode), ignored -> new java.util.ArrayList<>()).add(set);
            }
        }
        java.util.Comparator<SetRelease> newestFirst = java.util.Comparator.comparing(SetRelease::releasedOn)
                .reversed().thenComparing(SetRelease::code);
        Map<String, List<SetRelease>> ordered = new java.util.LinkedHashMap<>();
        families.entrySet().stream()
                .sorted(java.util.Comparator.comparing(
                        (Map.Entry<String, List<SetRelease>> family) -> newestOf(family.getValue())).reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(family -> ordered.put(family.getKey(),
                        family.getValue().stream().sorted(newestFirst).toList()));
        return java.util.Collections.unmodifiableMap(ordered);
    }

    private static String newestOf(List<SetRelease> members) {
        return members.stream().map(SetRelease::releasedOn).max(String::compareTo).orElse("");
    }

    /**
     * Every printing of a family nothing reaches.
     * <p>What any set of the family reaches counts for all of it: a promo printing handed out in the
     * main set's collector boosters is reached however its code is written.
     *
     * @param members  what each set of the family is known to contain
     * @param inPlay   the sets this server draws its boosters and its shop from
     * @param shopOpen whether a shop sells, which is what makes a set's products a way in
     */
    public static Set<UUID> unobtainable(Collection<SetFacts> members, Set<String> inPlay, boolean shopOpen) {
        Set<UUID> remainder = new LinkedHashSet<>();
        if (members == null) {
            return remainder;
        }
        Set<UUID> reached = new java.util.HashSet<>();
        for (SetFacts set : members) {
            // A set drawn from whose reach was never read is taken as reaching nothing. The
            // archive then holds a card that could have been found - the wrong way round in
            // the harmless direction, and only until the set's products are read.
            if (set != null && inPlay != null && inPlay.contains(set.code()) && set.reachKnown()) {
                reached.addAll(set.inBoosters());
                if (shopOpen) {
                    reached.addAll(set.inProducts());
                }
            }
        }
        for (SetFacts set : members) {
            if (set == null) {
                continue;
            }
            for (UUID printing : set.catalog()) {
                if (!reached.contains(printing)) {
                    remainder.add(printing);
                }
            }
        }
        return remainder;
    }
}
