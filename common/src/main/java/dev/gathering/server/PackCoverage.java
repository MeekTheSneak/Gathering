package dev.gathering.server;

import dev.gathering.core.booster.BoosterConfig;
import dev.gathering.core.booster.BoosterFaucet;
import dev.gathering.core.booster.CoverageAudit;
import dev.gathering.core.booster.CoverageReport;
import dev.gathering.core.booster.Faucet;
import dev.gathering.core.booster.MtgjsonCollation;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.service.CardDataService;
import dev.gathering.service.CollationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which of a set's cards no pack of it could ever produce.
 *
 * <p>The question an admin has before turning a set on, and one nobody can answer by reading:
 * a modern set's collation is a dozen sheets across eight products, and whether the union of
 * them covers the set is arithmetic. Completeness guaranteed rather than intended.
 *
 * <p>Answered for a server that has not turned collecting on yet, deliberately. "Would this
 * set be complete if I enabled it" is exactly the question you ask beforehand, and a report
 * that refused until you had already committed would be useless at the only moment it
 * mattered.
 *
 * <p>Everything blocking happens on the two pipelines that own it; only the telling runs on
 * the server thread.
 */
public final class PackCoverage {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** As many missing cards as are worth naming before the list stops being read. */
    private static final int NAMES_TO_SHOW = 8;

    private PackCoverage() {
    }

    /** Works out a set's coverage and tells the player, a line at a time. */
    public static void report(ServerPlayer player, String setCode) {
        CollationService collation = CollationService.active().orElse(null);
        CardDataService cards = CardDataService.active().orElse(null);
        if (collation == null || cards == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.pipeline_unavailable"));
            return;
        }
        String set = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        player.sendSystemMessage(Component.translatable("message.gathering.coverage_reading", set));

        collation.collationFor(set)
                .thenCombineAsync(cards.everyPrintingIn(set), Audited::new, collation.worker())
                .whenComplete((audited, failure) -> player.server.execute(() -> {
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null) {
                        LOGGER.warn("Auditing {} failed", set, failure);
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.coverage_failed", Failures.rootMessage(failure)));
                        return;
                    }
                    tell(player, set, audited);
                }));
    }

    /** A set's published collation and everything printed in it, side by side. */
    private record Audited(MtgjsonCollation.Reading reading, List<CardMetadata> inTheSet) {
    }

    /**
     * The paths a card of this set can come out of.
     *
     * <p>Every kind of pack the set was really printed with, and nothing else. A set that was
     * never sold in packs has none, and the honest report for one of those is that it has
     * none rather than a coverage figure against a pack that does not exist.
     */
    public static List<Faucet> faucetsFor(MtgjsonCollation.Reading reading) {
        List<Faucet> faucets = new ArrayList<>();
        if (reading == null) {
            return List.copyOf(faucets);
        }
        for (BoosterConfig config : reading.packs().values()) {
            faucets.add(new BoosterFaucet(config));
        }
        return List.copyOf(faucets);
    }

    /**
     * The catalog: every printing in the set a pack could ever have held.
     *
     * <p>The same rule the fallback pack is built to, asked once in one place, because an
     * audit measuring a different catalog from the one packs are cut from would report
     * holes that are not there.
     */
    public static List<UUID> catalogOf(List<CardMetadata> inTheSet) {
        List<UUID> catalog = new ArrayList<>();
        for (CardMetadata card : inTheSet) {
            if (PackOpening.wasEverInABooster(card)) {
                catalog.add(card.scryfallId());
            }
        }
        return List.copyOf(catalog);
    }

    /** Server thread only. */
    private static void tell(ServerPlayer player, String set, Audited audited) {
        List<UUID> catalog = catalogOf(audited.inTheSet());
        if (catalog.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.coverage_nothing", set));
            return;
        }
        List<Faucet> faucets = faucetsFor(audited.reading());
        if (faucets.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.coverage_no_packs", set, catalog.size()));
            return;
        }
        CoverageReport report = CoverageAudit.of(catalog, faucets);

        player.sendSystemMessage(Component.translatable("message.gathering.coverage_counted",
                set, report.covered(), report.catalog(),
                Math.round(report.fraction() * 100.0d)));
        report.byFaucet().forEach((name, reached) -> player.sendSystemMessage(
                Component.translatable("message.gathering.coverage_from", name, reached)));
        for (String idle : report.reachingNothing()) {
            // Not called a mistake. A prerelease pack holds a promo printed in its own little
            // set, so against one set's catalog it correctly reaches nothing - and telling
            // an admin that their prerelease product is misconfigured would send them looking
            // for a fault that is not there.
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.coverage_idle", idle));
        }
        if (report.isComplete()) {
            player.sendSystemMessage(Component.translatable("message.gathering.coverage_complete"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.gathering.coverage_missing",
                report.uncovered().size(), String.join(", ", someNames(audited, report))));
    }

    /**
     * A few of the missing cards, by name.
     *
     * <p>A count on its own is a number an admin cannot act on. Named, the answer is usually
     * obvious at a glance - a handful of promos, or a whole cycle that only came in a product
     * this server has not configured.
     */
    private static List<String> someNames(Audited audited, CoverageReport report) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (CardMetadata card : audited.inTheSet()) {
            if (card.scryfallId() != null) {
                names.putIfAbsent(card.scryfallId(), card.name());
            }
        }
        List<String> shown = new ArrayList<>();
        for (UUID printing : report.uncovered()) {
            if (shown.size() >= NAMES_TO_SHOW) {
                shown.add("...");
                break;
            }
            shown.add(names.getOrDefault(printing, printing.toString()));
        }
        return shown;
    }

}
