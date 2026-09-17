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
 * <p>So the remainder is computed rather than listed, and across the whole of Magic's history
 * rather than the sets a server draws from - by the owner's rule, the archive holds every card a
 * player cannot come by through play. A set the server draws from reaches what its boosters and,
 * with the shop open, its products hold; a set it does not draw from reaches nothing, and all of it
 * is here. See {@link dev.gathering.core.booster.ArchiveAudit}. <b>It shrinks as a server draws
 * from more</b>, and a server whose faucets covered everything would drop no archive packs at all,
 * which is the goal rather than a fault.
 * <p><b>Never sold.</b> Something you can buy is not a long tail, it is a shelf - so this
 * comes out of the three places worth going to and nowhere else. See {@link ArchiveDrops}.
 * <p>Worked out in the background when the server starts, one set at a time, and published as it
 * grows: loot is rolled deep inside the game with no time to reach a network. Each set is read once
 * and kept on disk ({@link ArchiveFacts}), so only the first start on a machine walks all of it.
 */
public final class Archive {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** What an archive pack's set code is. See {@link PackComponent#ARCHIVE}. */
    public static final String SET = PackComponent.ARCHIVE;

    /** How many sets are audited between one publishing of the sheet and the next. */
    private static final int PUBLISH_EVERY = 25;

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
     * Works out the remainder across every set there has ever been.
     * <p>The sets this server draws from first, so the archive is right about the game being played
     * soonest, then the rest of history newest first. Each set's facts come off disk where they are
     * still good and off the network where they are not, and the sheet is published every
     * {@value #PUBLISH_EVERY} sets and at the end - so the archive is findable within a minute of a
     * first start rather than after the whole walk.
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
        boolean shopOpen = settings.collecting().sealedStoreEnabled();
        java.nio.file.Path root = dev.gathering.platform.Platform.get().dataDirectory();
        long run = ServerRun.generation();
        SetsInPlay.wanted(settings)
                .thenCombine(cards.allSets(), (inPlay, everySet) -> new Walk(
                        collation, cards, root, run, java.util.Set.copyOf(inPlay), shopOpen,
                        order(inPlay, everySet)))
                .thenCompose(Walk::next)
                .whenComplete(ServerRun.stillThisRun((walked, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not work out what this server's faucets miss, so the archive "
                                + "holds only what was worked out before it stopped", failure);
                    }
                }));
    }

    /**
     * Every audited set, the ones drawn from first and then the rest of history newest first.
     */
    private static List<dev.gathering.core.card.SetRelease> order(
            List<String> inPlay, java.util.Map<String, dev.gathering.core.card.SetRelease> everySet) {
        List<dev.gathering.core.card.SetRelease> first = new ArrayList<>();
        for (String code : inPlay) {
            dev.gathering.core.card.SetRelease known = everySet.get(code);
            if (dev.gathering.core.booster.ArchiveAudit.isAudited(known)) {
                first.add(known);
            }
        }
        List<dev.gathering.core.card.SetRelease> rest = everySet.values().stream()
                .filter(dev.gathering.core.booster.ArchiveAudit::isAudited)
                .filter(set -> !inPlay.contains(set.code()))
                .sorted(java.util.Comparator.comparing(dev.gathering.core.card.SetRelease::releasedOn).reversed())
                .toList();
        first.addAll(rest);
        return List.copyOf(first);
    }

    /** One walk over history: where it is, and what it has learned. */
    private static final class Walk {

        private final CollationService collation;
        private final CardDataService cards;
        private final java.nio.file.Path root;
        private final long run;
        private final java.util.Set<String> inPlay;
        private final boolean shopOpen;
        private final List<dev.gathering.core.card.SetRelease> sets;
        private final List<dev.gathering.core.booster.ArchiveAudit.SetFacts> learned = new ArrayList<>();
        private int at;

        Walk(CollationService collation, CardDataService cards, java.nio.file.Path root, long run,
                java.util.Set<String> inPlay, boolean shopOpen, List<dev.gathering.core.card.SetRelease> sets) {
            this.collation = collation;
            this.cards = cards;
            this.root = root;
            this.run = run;
            this.inPlay = inPlay;
            this.shopOpen = shopOpen;
            this.sets = sets;
        }

        /**
         * The next set, and then the one after it, until history or this world runs out.
         * <p>A loop over everything that can be answered off disk, and a hop onto the card worker
         * after each set that had to be read. Chaining every set onto the last would recurse once per
         * set wherever a future was already complete - which, on every start after the first, is
         * almost all of seven hundred of them, and deep enough to overflow a stack.
         */
        CompletableFuture<Walk> next() {
            while (at < sets.size()) {
                if (!ServerRun.isStill(run)) {
                    return CompletableFuture.completedFuture(this);
                }
                dev.gathering.core.card.SetRelease set = sets.get(at++);
                Optional<dev.gathering.core.booster.ArchiveAudit.SetFacts> fromDisk = keptFor(set);
                if (fromDisk.isPresent()) {
                    learned(fromDisk);
                    continue;
                }
                return factsFor(set)
                        .exceptionally(failure -> {
                            LOGGER.warn("Could not audit {} for the archive: {}", set.code(), failure.toString());
                            return ArchiveFacts.read(root, set.code()).map(ArchiveFacts.Kept::facts);
                        })
                        .thenComposeAsync(facts -> {
                            learned(facts);
                            return next();
                        }, collation.worker());
            }
            publish(true);
            return CompletableFuture.completedFuture(this);
        }

        private void learned(Optional<dev.gathering.core.booster.ArchiveAudit.SetFacts> facts) {
            facts.ifPresent(learned::add);
            if (at % PUBLISH_EVERY == 0) {
                publish(false);
            }
        }

        /** This set's facts off disk, where they still stand for the set as it is listed now. */
        private Optional<dev.gathering.core.booster.ArchiveAudit.SetFacts> keptFor(
                dev.gathering.core.card.SetRelease set) {
            return ArchiveFacts.read(root, set.code())
                    .filter(kept -> kept.stillGood(set.cardCount(), System.currentTimeMillis(),
                            inPlay.contains(set.code())))
                    .map(ArchiveFacts.Kept::facts);
        }

        /** This set's facts: off disk where they still hold, otherwise read again and kept. */
        private CompletableFuture<Optional<dev.gathering.core.booster.ArchiveAudit.SetFacts>> factsFor(
                dev.gathering.core.card.SetRelease set) {
            boolean drawnFrom = inPlay.contains(set.code());
            Optional<ArchiveFacts.Kept> kept = ArchiveFacts.read(root, set.code());
            return cards.everyPrintingToAudit(set.code()).thenCompose(read -> {
                if (read.isEmpty()) {
                    // Short or unreadable: whatever was kept stands until it can be read whole.
                    return CompletableFuture.completedFuture(kept.map(ArchiveFacts.Kept::facts));
                }
                List<UUID> catalog = read.get().stream()
                        .filter(dev.gathering.core.booster.ArchiveAudit::isACard)
                        .map(dev.gathering.core.card.CardMetadata::scryfallId)
                        .distinct()
                        .toList();
                if (!drawnFrom) {
                    return CompletableFuture.completedFuture(Optional.of(keep(set,
                            new dev.gathering.core.booster.ArchiveAudit.SetFacts(
                                    set.code(), catalog, java.util.Set.of(), java.util.Set.of(), false))));
                }
                return collation.collationFor(set.code())
                        .thenCombine(collation.catalogFor(set.code()), (packs, catalogued) -> Optional.of(keep(set,
                                new dev.gathering.core.booster.ArchiveAudit.SetFacts(set.code(), catalog,
                                        inBoosters(packs), inProducts(catalogued), true))));
            });
        }

        private dev.gathering.core.booster.ArchiveAudit.SetFacts keep(
                dev.gathering.core.card.SetRelease set, dev.gathering.core.booster.ArchiveAudit.SetFacts facts) {
            ArchiveFacts.write(root, new ArchiveFacts.Kept(facts, System.currentTimeMillis(), set.cardCount()));
            return facts;
        }

        private void publish(boolean finished) {
            if (!ServerRun.isStill(run)) {
                return;
            }
            Set<UUID> remainder = dev.gathering.core.booster.ArchiveAudit.unobtainable(learned, inPlay, shopOpen);
            sheet = CoverageAudit.archiveSheet(new CoverageReport(remainder.size(), remainder, java.util.Map.of()));
            if (finished) {
                LOGGER.info("Archive packs can be found: {} card(s) across {} set(s) of Magic's history that "
                        + "nothing else reaches", sheet.size(), learned.size());
            }
        }
    }

    /** Every printing any of a set's boosters could hold. */
    private static Set<UUID> inBoosters(dev.gathering.core.booster.MtgjsonCollation.Reading packs) {
        Set<UUID> reached = new LinkedHashSet<>();
        for (var faucet : PackCoverage.faucetsFor(packs)) {
            reached.addAll(faucet.reaches());
        }
        return reached;
    }

    /** Every printing a set's sellable products could hand over, by name or by the decks they hold. */
    private static Set<UUID> inProducts(CollationService.Catalog catalogued) {
        Set<UUID> reached = new LinkedHashSet<>();
        if (catalogued == null) {
            return reached;
        }
        var lookup = catalogued.lookup();
        for (var product : catalogued.products().products()) {
            if (!dev.gathering.core.sealed.SealedPrice.isSellable(product)) {
                continue;
            }
            dev.gathering.core.sealed.SealedContents.of(product, lookup).ifPresent(bag -> {
                bag.cards().forEach(card -> card.printing().ifPresent(reached::add));
                for (var deck : bag.decks()) {
                    for (var section : List.of(deck.commanders(), deck.mainboard(), deck.sideboard())) {
                        section.forEach(card -> card.printing().ifPresent(reached::add));
                    }
                }
            });
        }
        return reached;
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

}
