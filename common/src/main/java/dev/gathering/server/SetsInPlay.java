package dev.gathering.server;

import dev.gathering.core.config.GatheringConfig;
import dev.gathering.core.sealed.LootSets;
import dev.gathering.service.ServerSettings;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which sets this server's product comes from.
 * <p>One answer for both ways into a collection. What turns up in a chest and what is on the
 * shop counter are the same sets, because a server that drops Bloomburrow and sells Innistrad
 * is a server where nobody can tell you what era they are playing in.
 * <p>What to do with the three config answers is {@link LootSets}'s and is checked there; this
 * is going and getting them. "Current" and "recent" come out of the same one request, so
 * asking for both costs no more than asking for either.
 */
public final class SetsInPlay {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private SetsInPlay() {
    }

    /** The sets, once they are known. Never fails: an unanswerable config gives nothing. */
    public static CompletableFuture<List<String>> wanted() {
        return wanted(ServerSettings.get());
    }

    static CompletableFuture<List<String>> wanted(GatheringConfig settings) {
        List<String> named = settings.collecting().lootSets();
        if (!LootSets.needsTheReleaseList(named)) {
            return CompletableFuture.completedFuture(
                    LootSets.wanted(named, Optional.empty(), List.of()));
        }
        // "All" is every set anything was sold for, out of the two lists already fetched at start -
        // Scryfall's sets and MTGJSON's products. "Recent" is the last few premier releases.
        CompletableFuture<List<String>> recent =
                LootSets.wantsEverySet(named)
                        ? everySold()
                        : LootSets.needsMoreThanTheNewest(named)
                                ? CurrentSet.recent(settings.collecting().lootRecentSets())
                                : CompletableFuture.completedFuture(List.of());
        return CurrentSet.whenKnown().thenCombine(recent, (current, releases) -> {
            List<String> all = LootSets.wanted(named, current, releases);
            if (all.isEmpty()) {
                LOGGER.warn("Collecting is on and no set could be worked out, so there is "
                        + "nothing to find and nothing to buy. Name one in collection.loot_sets "
                        + "to run without asking Scryfall.");
            }
            return all;
        });
    }

    /**
     * Every paper set that has come out and had something sold for it, premier sets first.
     * <p>Falls back to the premier sets alone where either list cannot be had, which is what
     * "all" used to mean and is still a server with every booster release in it.
     */
    private static CompletableFuture<List<String>> everySold() {
        var cards = dev.gathering.service.CardDataService.active().orElse(null);
        var collation = dev.gathering.service.CollationService.active().orElse(null);
        if (cards == null || collation == null) {
            return CurrentSet.recent(Integer.MAX_VALUE);
        }
        return cards.allSets().thenCombine(collation.everySetsProducts(), (sets, products) -> {
            java.util.Set<String> sold = new java.util.HashSet<>();
            products.forEach((code, reading) -> {
                if (!reading.isEmpty()) {
                    sold.add(code);
                }
            });
            return dev.gathering.core.card.SetRelease.everySold(List.copyOf(sets.values()), CurrentSet.today(), sold)
                    .stream().map(dev.gathering.core.card.SetRelease::code)
                    .flatMap(code -> dev.gathering.core.card.SetCode.of(code).stream()).toList();
        }).exceptionallyCompose(failure -> {
            LOGGER.warn("Could not list every set anything was sold for, so \"all\" is every expansion "
                    + "and core set", failure);
            return CurrentSet.recent(Integer.MAX_VALUE);
        });
    }
}
