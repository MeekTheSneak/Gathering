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
 *
 * <p>One answer for both ways into a collection. What turns up in a chest and what is on the
 * shop counter are the same sets, because a server that drops Bloomburrow and sells Innistrad
 * is a server where nobody can tell you what era they are playing in.
 *
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
        CompletableFuture<List<String>> recent =
                LootSets.needsMoreThanTheNewest(named)
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
}
