package dev.gathering.server;

import dev.gathering.core.booster.BoosterSheet;
import dev.gathering.core.booster.CoverageAudit;
import dev.gathering.core.booster.CoverageReport;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.sealed.ArchiveDrops;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.service.CardDataService;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Archive Pack: everything this server's own faucets cannot reach.
 * <p>The closing move on completeness, and the last piece of the brief's acquisition story. A
 * booster is cut from a print sheet and a shop sells what was really sold, which between them
 * leave a remainder - the buy-a-box card, the promo, the long tail of a set whose product is
 * out of print. Those cards are in the catalog and no path reaches them, and a collection
 * that can never be finished is a collection nobody finishes.
 * <p>So the remainder is computed rather than listed. {@link CoverageAudit} already answers
 * "which of this set's cards does nothing reach", which is what the coverage command reports;
 * the same answer, for every set the server has in play, is this pack's sheet. <b>It shrinks
 * as a server adds products</b>, and a server whose faucets cover everything drops no archive
 * packs at all, which is the goal rather than a fault.
 * <p><b>Never sold.</b> Something you can buy is not a long tail, it is a shelf - so this
 * comes out of the three places worth going to and nowhere else. See {@link ArchiveDrops}.
 * <p>Worked out once when the server starts, like everything else loot has to read: loot is
 * rolled deep inside the game with no time to reach a network.
 */
public final class Archive {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** What an archive pack's set code is. See {@link PackComponent#ARCHIVE}. */
    public static final String SET = PackComponent.ARCHIVE;

    /**
     * How many sets the remainder is worked out across.
     * <p>Roughly two years of releases, which is the span whose cards a player would notice
     * they could not find. See {@link #audited}.
     */
    private static final int MOST_SETS_AUDITED = 8;

    /** Decided at start and read on the loot thread. Replaced whole, never edited. */
    private static volatile BoosterSheet sheet = BoosterSheet.EMPTY;

    private Archive() {
    }

    /** An archive pack, as the item a player finds. */
    public static ItemStack pack() {
        return PackItem.of(new PackComponent(SET, ""));
    }

    /** How many cards a server's faucets are not reaching. Zero is the goal. */
    public static int size() {
        return sheet.size();
    }

    /**
     * Works out the remainder, one set at a time.
     * <p>Two reads per set - what it was sold as, and everything printed in it - which is
     * exactly what the coverage command does for one set, run over the sets the server has in
     * play. One after another rather than at once, because every set is a file and a search
     * and a server asking for a dozen of each at the same moment is a server being rude to
     * somebody else's host.
     * <p>Does nothing at all unless collecting is on and something can actually be found: an
     * archive pack on a server where no pack is ever found would be the only card faucet in
     * the world, which is not what this is.
     */
    public static void warm() {
        sheet = BoosterSheet.EMPTY;
        var settings = ServerSettings.get();
        if (!settings.modes().collectionEnabled()
                || settings.collecting().packLootSources().isEmpty()) {
            return;
        }
        CollationService collation = CollationService.active().orElse(null);
        CardDataService cards = CardDataService.active().orElse(null);
        if (collation == null || cards == null) {
            return;
        }
        SetsInPlay.wanted(settings)
                .thenComposeAsync(codes -> remainderOf(collation, cards, audited(codes)),
                        collation.worker())
                .whenComplete((remainder, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not work out what this server's faucets miss, so no "
                                + "archive packs are found", failure);
                        return;
                    }
                    sheet = CoverageAudit.archiveSheet(new CoverageReport(
                            remainder.size(), remainder, java.util.Map.of()));
                    if (sheet.isEmpty()) {
                        LOGGER.info("Every card in this server's sets is reachable, so there is "
                                + "no archive to find");
                    } else {
                        LOGGER.info("Archive packs can be found: {} card(s) nothing else reaches",
                                sheet.size());
                    }
                });
    }

    /** Between servers, so one world's remainder is not the next one's. */
    public static void clear() {
        sheet = BoosterSheet.EMPTY;
    }

    /**
     * An archive pack for this loot table, if one comes up.
     * <p>Called while loot is being rolled, so it does nothing that can block and nothing
     * that can throw. Asked before the ordinary pack, and answering means the ordinary one is
     * not asked at all: two packs out of one chest reads as a fault rather than as luck.
     */
    public static Optional<ItemStack> rollFor(String tableId, RandomSource random) {
        // Before the string is touched. This runs for every loot table the game rolls.
        if (sheet.isEmpty() || random == null) {
            return Optional.empty();
        }
        ArchiveDrops where = ArchiveDrops.of(tableId).orElse(null);
        if (where == null || random.nextInt(where.oneIn()) != 0) {
            return Optional.empty();
        }
        return Optional.of(pack());
    }

    /**
     * What is inside one.
     * <p>Drawn with replacement, like every other pack in this mod: a sheet is a sheet, and a
     * remainder of two cards should still give three of them rather than refusing.
     */
    public static List<CardIdentity> open(RandomSource random) {
        BoosterSheet holding = sheet;
        if (holding.isEmpty() || random == null) {
            return List.of();
        }
        List<CardIdentity> cards = new ArrayList<>(ArchiveDrops.CARDS);
        long total = Math.max(1L, holding.total());
        for (int index = 0; index < ArchiveDrops.CARDS; index++) {
            cards.add(holding.identityOf(holding.at(Math.floorMod(random.nextLong(), total))));
        }
        return List.copyOf(cards);
    }

    // ------------------------------------------------------------------ bits

    /**
     * As many sets as are worth auditing, newest first.
     * <p>Every set audited is that set's file fetched and its whole printing list asked for,
     * which is a few megabytes and a search each. A server drawing from every set ever
     * printed would spend a gigabyte and several hundred searches at every start working out
     * a remainder that is mostly cards nobody was going to miss - so this reaches back over
     * the sets people are actually opening and stops. The sets in play are newest first
     * already, which is the order that matters.
     */
    private static List<String> audited(List<String> codes) {
        if (codes.size() <= MOST_SETS_AUDITED) {
            return codes;
        }
        LOGGER.info("Working out what this server's faucets miss across its newest {} set(s), "
                + "out of the {} it draws from", MOST_SETS_AUDITED, codes.size());
        return codes.subList(0, MOST_SETS_AUDITED);
    }

    /** Every printing in these sets that none of their own products reaches. */
    private static CompletableFuture<Set<UUID>> remainderOf(
            CollationService collation, CardDataService cards, List<String> codes) {
        CompletableFuture<Set<UUID>> reading =
                CompletableFuture.completedFuture(new LinkedHashSet<>());
        for (String code : codes) {
            reading = reading.thenCompose(found -> collation.collationFor(code)
                    .thenCombine(cards.everyPrintingIn(code), (packs, printings) -> {
                        List<UUID> catalog = PackCoverage.catalogOf(printings);
                        if (catalog.isEmpty()) {
                            return found;
                        }
                        // A set that was never sold in packs is not a hole in the faucets: it
                        // is a set the shop stocks or nobody does, and sweeping the whole of
                        // it into the archive would make the archive the set.
                        var faucets = PackCoverage.faucetsFor(packs);
                        if (faucets.isEmpty()) {
                            return found;
                        }
                        found.addAll(CoverageAudit.of(catalog, faucets).uncovered());
                        return found;
                    })
                    .exceptionally(failure -> {
                        LOGGER.warn("Could not audit {}, so its remainder is not in the archive",
                                code, failure);
                        return found;
                    }));
        }
        return reading;
    }

}
