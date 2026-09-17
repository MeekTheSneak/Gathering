package dev.gathering.core.booster;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.SetRelease;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Which printings, across the whole of Magic's history, no ordinary way into a collection reaches.
 * <p>The Archive Pack's contents, by the owner's rule: everything a player cannot come by through
 * play, from every set there has ever been, not only the sets a server happens to draw from. A set a
 * server draws from reaches what its boosters hold and, with the shop open, what its sealed products
 * hold. A set a server does not draw from reaches nothing, so all of it belongs in the archive -
 * which is how a server playing only the newest set still has a way to the rest of the game.
 * <p>Worked out one set at a time from facts that do not depend on the server's settings - what the
 * set printed, and what its boosters and products could ever hold - so a change of settings is a
 * new sum over facts already known rather than another trip round every set.
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

    /** Whether a printing is a card that could go in a collection, rather than a token or an art card. */
    public static boolean isACard(CardMetadata card) {
        return card != null
                && card.scryfallId() != null
                && !card.digitalOnly()
                && !card.oversized()
                && card.games() != null && card.games().contains("paper")
                && !NOT_A_CARD.contains(card.layout() == null ? "" : card.layout().toLowerCase(Locale.ROOT));
    }

    /**
     * Every printing nothing reaches.
     *
     * @param inPlay   the sets this server draws its boosters and its shop from
     * @param shopOpen whether a shop sells, which is what makes a set's products a way in
     */
    public static Set<UUID> unobtainable(Collection<SetFacts> sets, Set<String> inPlay, boolean shopOpen) {
        Set<UUID> remainder = new LinkedHashSet<>();
        if (sets == null) {
            return remainder;
        }
        for (SetFacts set : sets) {
            if (set == null) {
                continue;
            }
            boolean drawnFrom = inPlay != null && inPlay.contains(set.code());
            for (UUID printing : set.catalog()) {
                // A set drawn from whose reach was never read is taken as reaching nothing. The
                // archive then holds a card that could have been found - the wrong way round in
                // the harmless direction, and only until the set's products are read.
                boolean reached = drawnFrom && set.reachKnown()
                        && (set.inBoosters().contains(printing) || (shopOpen && set.inProducts().contains(printing)));
                if (!reached) {
                    remainder.add(printing);
                }
            }
        }
        return remainder;
    }
}
