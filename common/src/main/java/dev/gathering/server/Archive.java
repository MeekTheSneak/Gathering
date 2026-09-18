package dev.gathering.server;

import dev.gathering.core.booster.ArchiveAudit;
import dev.gathering.core.card.SetRelease;
import dev.gathering.core.sealed.ArchiveDrops;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.service.CardDataService;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Archive Pack: the cards of one set that this server's own faucets cannot reach.
 * <p>The closing move on completeness. A booster is cut from a print sheet and a shop sells what
 * was really sold, which between them leave a remainder - the buy-a-box card, the promo, a set
 * nobody on this server draws from. Those cards are in the catalog and no path reaches them, and a
 * collection that can never be finished is a collection nobody finishes.
 * <p><b>One set to a pack</b>, by the owner's rule: a pack names a set, and holds what is out of
 * reach among that set and the promo and Commander sets released beside it (its family, see
 * {@link ArchiveAudit#familyOf}). Any set in Magic's history can come up, so the whole of it is
 * still findable - but only the family of a pack somebody opens is ever looked up. The archive used
 * to walk every set there has ever been at every start, a search per set, and Scryfall turned the
 * server away partway through.
 * <p>The remainder is computed rather than listed. A set the server draws from reaches what its
 * boosters and, with the shop open, its products hold; a set it does not draw from reaches nothing.
 * A family where everything is reachable has no archive, and a pack for it opens as another's.
 * <p><b>Never sold.</b> Something you can buy is not a long tail, it is a shelf - so this comes out
 * of the three places worth going to and nowhere else. See {@link ArchiveDrops}.
 */
public final class Archive {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** What an archive pack's set code is. See {@link PackComponent#ARCHIVE}. */
    public static final String SET = PackComponent.ARCHIVE;

    /** How many families one opening tries before handing the pack back. */
    static final int ATTEMPTS = 4;

    /** The families a pack can be for, newest first. Decided at start, read on the loot thread, replaced whole. */
    private static volatile List<String> families = List.of();

    /** The sets of each family worth auditing. Replaced whole, with {@link #families}. */
    private static volatile Map<String, List<SetRelease>> members = Map.of();

    /** What each family's archive holds, for this run, once somebody has opened one of its packs. */
    private static final Map<String, CompletableFuture<Audited>> REMAINDERS = new ConcurrentHashMap<>();

    /** Which start of the archive is the current one; the work of any other has been superseded. */
    private static final AtomicLong STARTS = new AtomicLong();

    private Archive() {
    }

    /** An archive pack for no set in particular, which opens as whichever set comes up. */
    public static ItemStack pack() {
        return pack("");
    }

    /** An archive pack for one set's family. */
    public static ItemStack pack(String family) {
        return PackItem.of(new PackComponent(SET, family == null ? "" : family.trim().toLowerCase(java.util.Locale.ROOT)));
    }

    /** How many sets an archive pack can currently be for. */
    public static int size() {
        return families.size();
    }

    /**
     * Works out which sets an archive pack can be for.
     * <p>Scryfall's one list of every set, already asked for at start, and the facts about each set
     * kept on disk from earlier openings - no search at all. A family whose kept facts already show
     * everything in it reachable is left out, so a pack is not found for a set with nothing to give.
     * <p>Does nothing at all unless collecting is on and something can actually be found: an archive
     * pack on a server where no pack is ever found would be the only card faucet in the world.
     */
    public static void warm() {
        long start = STARTS.incrementAndGet();
        families = List.of();
        members = Map.of();
        REMAINDERS.clear();
        var settings = ServerSettings.get();
        if (!settings.modes().collectionEnabled() || settings.collecting().packLootSources().isEmpty()) {
            return;
        }
        CardDataService cards = CardDataService.active().orElse(null);
        CollationService collation = CollationService.active().orElse(null);
        if (cards == null || collation == null) {
            return;
        }
        boolean shopOpen = settings.collecting().sealedStoreEnabled();
        java.nio.file.Path root = dev.gathering.platform.Platform.get().dataDirectory();
        cards.allSets()
                .thenCombine(SetsInPlay.wanted(settings), (everySet, inPlay) -> {
                    Map<String, List<SetRelease>> found = ArchiveAudit.families(everySet.values(), CurrentSet.today());
                    List<String> worthFinding = new ArrayList<>();
                    for (var family : found.entrySet()) {
                        if (!knownToHoldNothing(root, family.getValue(), Set.copyOf(inPlay), shopOpen)) {
                            worthFinding.add(family.getKey());
                        }
                    }
                    return Map.entry(found, List.copyOf(worthFinding));
                })
                .whenComplete(ServerRun.stillThisRun((found, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not list the sets an archive pack can be for, so none are found", failure);
                        return;
                    }
                    if (STARTS.get() != start) {
                        return;
                    }
                    members = found.getKey();
                    families = found.getValue();
                    LOGGER.info("Archive packs can be found for {} set(s) of Magic's history", families.size());
                }));
    }

    /** Whether the facts kept on disk for every set of a family already say none of it is out of reach. */
    private static boolean knownToHoldNothing(java.nio.file.Path root, List<SetRelease> sets, Set<String> inPlay,
            boolean shopOpen) {
        List<ArchiveAudit.SetFacts> kept = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (SetRelease set : sets) {
            Optional<ArchiveAudit.SetFacts> facts = ArchiveFacts.read(root, set.code())
                    .filter(each -> each.stillGood(set.cardCount(), now, inPlay.contains(set.code())))
                    .map(ArchiveFacts.Kept::facts);
            if (facts.isEmpty()) {
                return false;
            }
            kept.add(facts.get());
        }
        return ArchiveAudit.unobtainable(kept, inPlay, shopOpen).isEmpty();
    }

    /** Between servers, so one world's archive is not the next one's. */
    public static void clear() {
        STARTS.incrementAndGet();
        families = List.of();
        members = Map.of();
        REMAINDERS.clear();
    }

    /** For the in-world tests, which have no network: archive packs are for one set holding these. */
    public static void holdForTesting(Set<UUID> printings) {
        STARTS.incrementAndGet();
        REMAINDERS.clear();
        members = Map.of("tst", List.of());
        families = List.of("tst");
        REMAINDERS.put("tst", CompletableFuture.completedFuture(new Audited(List.copyOf(printings), true)));
    }

    /**
     * An archive pack for this loot table, if one comes up.
     * <p>Called while loot is being rolled, so it does nothing that can block and nothing that can
     * throw. Asked before the ordinary pack, and answering means the ordinary one is not asked at
     * all: two packs out of one chest reads as a fault rather than as luck.
     */
    public static Optional<ItemStack> rollFor(String tableId, RandomSource random) {
        return rollFor(tableId, random, false);
    }

    /** The same, saying whether a player had a hand in the kill. */
    public static Optional<ItemStack> rollFor(String tableId, RandomSource random, boolean killedByAPlayer) {
        // Before the string is touched. This runs for every loot table the game rolls.
        List<String> findable = families;
        if (findable.isEmpty() || random == null) {
            return Optional.empty();
        }
        // And asked here as well as at the warm. The list is emptied when collecting goes off, but a
        // read of the switch costs nothing beside a loot roll, and it is the one answer that cannot
        // be stale.
        if (!ServerSettings.get().modes().collectionEnabled()) {
            return Optional.empty();
        }
        ArchiveDrops where = ArchiveDrops.of(tableId).orElse(null);
        if (where == null || (where.needsAPlayer() && !killedByAPlayer)) {
            return Optional.empty();
        }
        if (random.nextInt(where.oneIn()) != 0) {
            return Optional.empty();
        }
        return Optional.of(pack(findable.get(random.nextInt(findable.size()))));
    }

    /**
     * The families to try opening a pack as, in order: the one it names, then others at random.
     * <p>On the server thread, with the level's random, so which set a pack for no set in particular
     * opens as is decided the way every other roll is.
     */
    public static List<String> candidates(String asked, RandomSource random) {
        Set<String> trying = new LinkedHashSet<>();
        String named = asked == null ? "" : asked.trim().toLowerCase(java.util.Locale.ROOT);
        if (!named.isEmpty()) {
            trying.add(named);
        }
        List<String> findable = families;
        for (int roll = 0; roll < ATTEMPTS * 3 && trying.size() < ATTEMPTS && !findable.isEmpty() && random != null; roll++) {
            trying.add(findable.get(random.nextInt(findable.size())));
        }
        return List.copyOf(trying);
    }

    /** One family's archive, when it holds anything. */
    public record Found(String family, List<UUID> printings) {
    }

    /**
     * The first of these families whose archive holds anything, worked out a family at a time.
     * <p>A family found to hold nothing is struck off what packs can be found for, so the next pack
     * is not for it.
     */
    public static CompletableFuture<Optional<Found>> firstWithCards(List<String> candidates) {
        return tryFrom(candidates == null ? List.of() : List.copyOf(candidates), 0);
    }

    private static CompletableFuture<Optional<Found>> tryFrom(List<String> candidates, int at) {
        if (at >= candidates.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String family = candidates.get(at);
        return remainderOf(family).thenCompose(remainder -> {
            List<UUID> printings = remainder.printings();
            if (!printings.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.of(new Found(family, printings)));
            }
            // Struck off only where every set of it was read and there was genuinely nothing left. An
            // audit that could not be done comes back empty too, and striking a family off for that
            // took it out of the archive for the rest of the run - so a bad ten minutes of Scryfall
            // emptied the list one family at a time and the archive quietly stopped existing.
            if (remainder.whole() && members.containsKey(family)) {
                List<String> left = new ArrayList<>(families);
                if (left.remove(family)) {
                    families = List.copyOf(left);
                }
            }
            return tryFrom(candidates, at + 1);
        });
    }

    /**
     * What one family's archive holds, worked out the first time it is asked for in a run.
     * <p>Answers with whether every set of it could be read, because "nothing left" and "could not be
     * worked out" are the same empty list and mean opposite things.
     */
    static CompletableFuture<Audited> remainderOf(String family) {
        CompletableFuture<Audited> known = REMAINDERS.get(family);
        if (known != null) {
            return known;
        }
        List<SetRelease> sets = members.get(family);
        if (sets == null) {
            // Not a family this run knows - a pack from before, for a set since left out, or the list
            // has not arrived yet. Nothing is remembered, so it is asked again once it can be answered.
            return CompletableFuture.completedFuture(new Audited(List.of(), false));
        }
        CompletableFuture<Audited> working = new CompletableFuture<>();
        CompletableFuture<Audited> raced = REMAINDERS.putIfAbsent(family, working);
        if (raced != null) {
            return raced;
        }
        long start = STARTS.get();
        audit(sets).whenComplete((audited, failure) -> {
            // runcheck: the answer is kept only while this start of the archive is still the current
            // one, checked below, and handed to whoever opened the pack through the fence they put
            // round it - PackOpening waits for it with ServerRun.onServerThread.
            if (failure != null || !audited.whole() || STARTS.get() != start) {
                // Something could not be read: answered for this opening, and asked again next time.
                REMAINDERS.remove(family, working);
            }
            if (failure != null) {
                LOGGER.warn("Could not work out the archive of {}", family, failure);
                working.complete(new Audited(List.of(), false));
            } else {
                working.complete(audited);
            }
        });
        return working;
    }

    /** A family's remainder, and whether every one of its sets could be read to work it out. */
    record Audited(List<UUID> printings, boolean whole) {
    }

    /** Works out one family's remainder, one set at a time so the searches never pile up. */
    private static CompletableFuture<Audited> audit(List<SetRelease> sets) {
        var settings = ServerSettings.get();
        CollationService collation = CollationService.active().orElse(null);
        CardDataService cards = CardDataService.active().orElse(null);
        if (collation == null || cards == null) {
            return CompletableFuture.completedFuture(new Audited(List.of(), false));
        }
        boolean shopOpen = settings.collecting().sealedStoreEnabled();
        java.nio.file.Path root = dev.gathering.platform.Platform.get().dataDirectory();
        return SetsInPlay.wanted(settings).thenCompose(wanted -> {
            Set<String> inPlay = Set.copyOf(wanted);
            List<ArchiveAudit.SetFacts> learned = new ArrayList<>();
            boolean[] whole = {true};
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (SetRelease set : sets) {
                chain = chain.thenCompose(ignored -> factsFor(collation, cards, root, set, inPlay.contains(set.code()))
                        .thenAccept(facts -> {
                            // runcheck: gathered into this audit's own list, which nothing outside it reads.
                            facts.facts().ifPresent(learned::add);
                            // Facts that were kept last time and could not be read again this time are
                            // used, because they are better than nothing - but they do not make the
                            // audit whole. Counting them whole memoized an archive worked out from
                            // facts this code had already decided were stale, for the whole run.
                            if (!facts.fresh()) {
                                whole[0] = false;
                            }
                        }));
            }
            return chain.thenApply(ignored -> new Audited(
                    List.copyOf(ArchiveAudit.unobtainable(learned, inPlay, shopOpen)), whole[0]));
        });
    }

    /**
     * One set's facts, and whether they are this run's rather than a stand-in.
     * <p>Not fresh means the set could not be read now and what was kept from before is standing in
     * for it - which is worth answering with and not worth remembering, because the answer is built
     * on facts something has already judged out of date.
     */
    private record Facts(Optional<ArchiveAudit.SetFacts> facts, boolean fresh) {

        static Facts standingIn(Optional<ArchiveFacts.Kept> kept) {
            return new Facts(kept.map(ArchiveFacts.Kept::facts), false);
        }

        static Facts read(Optional<ArchiveAudit.SetFacts> facts) {
            return new Facts(facts, facts.isPresent());
        }
    }

    /** One set's facts: off disk where they still hold, otherwise read again and kept. */
    private static CompletableFuture<Facts> factsFor(CollationService collation,
            CardDataService cards, java.nio.file.Path root, SetRelease set, boolean drawnFrom) {
        Optional<ArchiveFacts.Kept> kept = ArchiveFacts.read(root, set.code());
        if (kept.isPresent() && kept.get().stillGood(set.cardCount(), System.currentTimeMillis(), drawnFrom)) {
            return CompletableFuture.completedFuture(new Facts(kept.map(ArchiveFacts.Kept::facts), true));
        }
        return cards.everyPrintingToAudit(set.code()).<Facts>thenCompose(read -> {
            if (read.isEmpty()) {
                // Short or unreadable: whatever was kept stands until it can be read whole.
                return CompletableFuture.completedFuture(Facts.standingIn(kept));
            }
            // Another language's copies of English cards left out; a set's own printings in another
            // language - a Japanese bonus sheet, a promo only given out in Japan - kept.
            List<UUID> catalog = dev.gathering.core.card.ForeignPrintings.keptIn(read.get(), set.type()).stream()
                    .filter(ArchiveAudit::isACard)
                    .map(dev.gathering.core.card.CardMetadata::scryfallId)
                    .distinct()
                    .toList();
            if (!drawnFrom) {
                return CompletableFuture.completedFuture(Facts.read(Optional.of(keep(root, set,
                        new ArchiveAudit.SetFacts(set.code(), catalog, Set.of(), Set.of(), false)))));
            }
            return collation.collationFor(set.code())
                    .thenCombine(collation.catalogFor(set.code()), (packs, catalogued) -> Facts.read(Optional.of(
                            keep(root, set, new ArchiveAudit.SetFacts(set.code(), catalog, inBoosters(packs),
                                    inProducts(catalogued), true)))));
        }).exceptionally(failure -> {
            LOGGER.warn("Could not audit {} for the archive: {}", set.code(), failure.toString());
            return Facts.standingIn(kept);
        });
    }

    private static ArchiveAudit.SetFacts keep(java.nio.file.Path root, SetRelease set, ArchiveAudit.SetFacts facts) {
        ArchiveFacts.write(root, new ArchiveFacts.Kept(facts, System.currentTimeMillis(), set.cardCount()));
        return facts;
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

    /**
     * What is inside one, out of a family's archive.
     * <p>Drawn with replacement, like every other pack in this mod: a sheet is a sheet, and a
     * remainder of two cards should still give three of them rather than refusing. Server thread,
     * with the level's random.
     */
    public static List<dev.gathering.core.card.CardIdentity> draw(List<UUID> printings, RandomSource random) {
        if (printings == null || printings.isEmpty() || random == null) {
            return List.of();
        }
        List<dev.gathering.core.card.CardIdentity> cards = new ArrayList<>(ArchiveDrops.CARDS);
        for (int index = 0; index < ArchiveDrops.CARDS; index++) {
            cards.add(dev.gathering.core.card.CardIdentity.ofPrinting(printings.get(random.nextInt(printings.size()))));
        }
        return List.copyOf(cards);
    }
}
