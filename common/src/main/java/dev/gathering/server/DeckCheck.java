package dev.gathering.server;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.format.DeckValidator;
import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.ValidatableDeck;
import dev.gathering.core.format.ValidationResult;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The deck check, run against a deck somebody is about to put down.
 * <p>The one referee this mod permits, and until now it was a class nobody called: the
 * validator was written, tested and then never wired to anything, so a thirty-two card deck
 * started a game of Modern without a word. It runs here, once, when a deck is committed to a
 * table with a format on it - and then it is over. Nothing in it is consulted during play.
 * <p><b>Out of memory, or not on this thread at all.</b> This is on the server thread with a
 * player waiting, and a deck check that fetched a hundred cards from Scryfall would hang the
 * server for as long as that took. Every card in a deck somebody built through this mod is in
 * the cache already, because that is where it came from, and the cache's index is warmed on
 * server start - so in practice this is a hundred map lookups.
 * <p>It used to fall through to the cache <em>files</em> when a card was not indexed yet: a
 * deck committed in the first seconds of a server, before the warm finished, was a hundred
 * small reads inside one tick. That is the case {@link #nowOrSoon} exists for. It answers from
 * memory or says it cannot yet, and the caller waits for those files to be read on the card
 * thread instead of reading them here. The only thing this asks a disk is whether a file is
 * there at all - a stat, not a read - because "not indexed yet" is worth waiting for and
 * "never heard of it" is not. Nothing in this class ever touches a network.
 * <p>A card that is <em>not</em> cached is a card this check cannot judge, and an unjudgeable
 * card makes the whole answer unjudgeable rather than making the deck illegal. Refusing a deck
 * because a server had forgotten what one of its cards was would be the mod inventing a rules
 * violation, which is exactly what it promises not to do.
 */
public final class DeckCheck {

    private DeckCheck() {
    }

    /**
     * What this deck comes to against this format, or nothing when it cannot be told.
     * <p>Empty means "no opinion": a free-play table with no format, no metadata service, or a
     * deck with a card the server has never looked up. All three are reasons to let the game
     * start, and none of them is a reason to claim a deck is legal either.
     */
    public static Optional<ValidationResult> of(DeckComponent deck, FormatPreset format) {
        return of(deck, format, null);
    }

    /**
     * The same, against the pool this deck was drafted from.
     * <p>A pool is the one thing a format cannot tell you. Every other check here asks
     * whether a card is legal; this asks whether it is yours, which in limited is the whole
     * format - four copies of the best card in the set is a fine limited deck and impossible
     * because nobody opens four. A deck with no pool is judged on its format alone, which is
     * every deck anybody imported.
     */
    public static Optional<ValidationResult> of(
            DeckComponent deck, FormatPreset format, DraftedPool pool) {
        Answer answer = nowOrSoon(deck, format, pool);
        return answer instanceof Answer.Known known ? known.result() : Optional.empty();
    }

    /**
     * What the check can say without leaving the game thread, and what to wait on if it
     * cannot say anything yet.
     * <p>Two answers rather than one, because "I do not know" and "I do not know <em>yet</em>"
     * are different things to a player putting a deck down. The first is a free-play table or
     * a card nobody has ever looked up, and the right response is to let the game start. The
     * second is a server whose index has not finished warming, and the right response is to
     * wait a moment and ask again - off this thread, because the answer is on a disk.
     */
    public sealed interface Answer {

        /** The check ran. Empty inside means it ran and had no opinion. */
        record Known(Optional<ValidationResult> result) implements Answer {
        }

        /**
         * The cards are being fetched. Ask again when this completes.
         *
         * @param fetched completes on the card executor once the missing printings have been
         *                looked up, whether or not they were found
         */
        record NotYet(java.util.concurrent.CompletableFuture<?> fetched) implements Answer {
        }
    }

    /** The check, from memory only, or what to wait on. Server thread safe. */
    public static Answer nowOrSoon(DeckComponent deck, FormatPreset format, DraftedPool pool) {
        if (deck == null || format == null) {
            return new Answer.Known(Optional.empty());
        }
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            return new Answer.Known(Optional.empty());
        }
        List<UUID> missing = whatIsNotInMemory(cards, deck, pool);
        if (!missing.isEmpty()) {
            // Warmed, not fetched. This class promises never to go to the network - a deck
            // check that waited on Scryfall would hold a player at the table for as long as
            // somebody else's service took to answer, and a card nothing has ever heard of
            // would hold them for the timeout. So the wait is for the disk, and a printing
            // that is not on it stays unknown, which the check reads as no opinion.
            return new Answer.NotYet(cards.warm(missing));
        }
        return new Answer.Known(checked(cards, deck, format, pool));
    }

    /**
     * The printings this deck names that memory cannot answer for.
     * <p>Asked of {@code peek}, which is the in-memory index and nothing else. A printing that
     * is on this disk but not indexed comes back here as missing, which is right: reading it
     * is what must not happen on this thread.
     */
    private static List<UUID> whatIsNotInMemory(
            CardDataService cards, DeckComponent deck, DraftedPool pool) {
        List<UUID> missing = new ArrayList<>();
        List<CardComponent> everything = new ArrayList<>(deck.entries());
        everything.addAll(deck.commanders());
        everything.addAll(deck.sideboard());
        if (pool != null) {
            everything.addAll(pool.cards());
        }
        for (CardComponent card : everything) {
            UUID printing = card.scryfallId().orElse(null);
            if (printing == null || missing.contains(printing) || cards.peek(printing).isPresent()) {
                continue;
            }
            // On this disk but not indexed yet is worth waiting for. Not on it at all never
            // arrives however long anybody waits, and the check's answer for a card it cannot
            // name is "no opinion" - so waiting would be a player standing at a table for a
            // verdict that was already in. A stat call each, which is a microsecond; reading
            // them is what must not happen here.
            if (cards.store().isOnDisk(printing)) {
                missing.add(printing);
            }
        }
        return missing;
    }

    /** The check itself, once everything it needs is in memory. */
    private static Optional<ValidationResult> checked(
            CardDataService cards, DeckComponent deck, FormatPreset format, DraftedPool pool) {
        List<CardMetadata> mainboard = lookUp(cards, deck.entries());
        List<CardMetadata> commanders = lookUp(cards, deck.commanders());
        List<CardMetadata> sideboard = lookUp(cards, deck.sideboard());
        if (mainboard == null || commanders == null || sideboard == null) {
            return Optional.empty();
        }
        ValidatableDeck checkable =
                new ValidatableDeck(deck.name(), mainboard, commanders, sideboard);
        ValidationResult result = withFreshness(cards, deck, DeckValidator.validate(checkable, format));
        if (pool == null || pool.isEmpty()) {
            return Optional.of(result);
        }
        List<CardMetadata> drafted = lookUp(cards, pool.cards());
        if (drafted == null) {
            // A pool with a card the server cannot look up is a pool this check cannot judge,
            // and an unjudgeable pool must not become an accusation. The format check stands.
            return Optional.of(result);
        }
        List<dev.gathering.core.format.ValidationIssue> issues =
                new ArrayList<>(result.issues());
        issues.addAll(dev.gathering.core.format.PoolCheck.against(checkable, drafted));
        return Optional.of(new ValidationResult(format, issues));
    }

    /**
     * How old a cached card may be before what it says about legality is worth a word.
     * <p>Bans and rotations are announced on a Monday and take effect on a Friday, so a
     * fortnight is comfortably longer than "the list has just changed" and far shorter than
     * "this was cached last season".
     */
    private static final java.time.Duration LEGALITY_GOES_OFF = java.time.Duration.ofDays(14);

    /**
     * Says so when the verdict rests on card data that has been sitting here a while.
     * <p>A printing does not change; what is legal in a format does. The cache answers for
     * both, so a deck imported last season is judged against last season's ban list without
     * anything saying that is what happened. The stale printings are asked for again in the
     * background, so the next check is current - and the check itself still stands, because
     * offline play is the reason the cache is there at all.
     */
    private static ValidationResult withFreshness(
            CardDataService cards, DeckComponent deck, ValidationResult result) {
        if (!(cards.store() instanceof dev.gathering.core.scryfall.DiskCardMetadataStore disk)) {
            return result;
        }
        java.time.Instant tooOld = java.time.Instant.now().minus(LEGALITY_GOES_OFF);
        List<UUID> stale = new ArrayList<>();
        for (UUID printing : deck.distinctPrintings()) {
            // Asked of memory, not of the disk. This runs on the game thread, and a hundred
            // stat calls in one tick is a hundred stat calls in one tick for a question whose
            // answer the store already has: every one of these printings was just resolved.
            if (disk.cachedAtInMemory(printing).filter(when -> when.isBefore(tooOld)).isPresent()) {
                stale.add(printing);
            }
        }
        if (stale.isEmpty()) {
            return result;
        }
        // Fetched again, past the cache. findAll was the first attempt at this and it does
        // nothing at all: it is the cache-first path, so a stale entry answers it instantly
        // and no request is ever made. The warning said the cards were being looked up again
        // while nothing was looking anything up, which is worse than not saying it - the next
        // check read exactly the same stale legality. Nothing waits on the result; the point
        // is that the next check is current.
        cards.refresh(List.copyOf(stale));
        List<dev.gathering.core.format.ValidationIssue> issues = new ArrayList<>(result.issues());
        issues.add(dev.gathering.core.format.ValidationIssue.warning("legality_stale",
                stale.size() + " card(s) were last looked up more than "
                        + LEGALITY_GOES_OFF.toDays() + " days ago, so this reads them against"
                        + " the ban list as it was then. They are being looked up again."));
        return new ValidationResult(result.preset(), issues);
    }

    /** Every card in a section, or null if the cache cannot answer for one of them. */
    private static List<CardMetadata> lookUp(CardDataService cards, List<CardComponent> section) {
        List<CardMetadata> found = new ArrayList<>(section.size());
        for (CardComponent card : section) {
            UUID printing = card.scryfallId().orElse(null);
            if (printing == null) {
                // A card somebody named by hand rather than one off Scryfall. There is nothing
                // to check it against, so there is nothing to say about the deck it is in.
                return null;
            }
            // From memory. The disk is not this thread's to read, and by the time this runs
            // everything here has been asked for and answered - see nowOrSoon.
            CardMetadata metadata = cards.peek(printing).orElse(null);
            if (metadata == null) {
                return null;
            }
            found.add(metadata);
        }
        return found;
    }
}
