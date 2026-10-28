package dev.gathering.core.deck;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.decklist.DecklistEntry;
import dev.gathering.core.decklist.DecklistParser;
import dev.gathering.core.decklist.ParseProblem;
import dev.gathering.core.decklist.ParsedDecklist;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.CollectionResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pasted text in, a real deck out.
 * <p>Two passes, both batched. The first asks for exactly what each line said - a printing
 * where the line named one, a card where it did not. The second retries the lines whose
 * printing hint matched nothing, this time by name alone, because a stale set code from a
 * three-year-old export should cost the player a different printing and not a missing card.
 * <p>A hundred-card Commander decklist costs two requests on a cold cache and none on a warm
 * one.
 */
public final class DeckImporter {

    private final CardSource source;
    private final ArchidektDeckSource archidekt;

    public DeckImporter(CardSource source) {
        this(source, null);
    }

    /**
     * @param archidekt the deck-site reader, or null on a build with no outbound access;
     *                  a link pasted without one is reported rather than silently ignored
     */
    public DeckImporter(CardSource source, ArchidektDeckSource archidekt) {
        this.source = java.util.Objects.requireNonNull(source, "source");
        this.archidekt = archidekt;
    }

    /**
     * Parse and resolve in one step, which is what the import screen actually calls.
     * <p>Accepts either a decklist or a single link to one. A link is the thing people
     * actually have in their clipboard, and a full text export does not fit in a chat
     * command, so pasting the link has to work.
     */
    public ResolvedDeck importText(String decklistText) throws IOException {
        Optional<DeckLink> link = DeckLink.parse(decklistText == null ? "" : decklistText.strip());
        if (link.isPresent()) {
            return resolve(fetchLinked(link.get()));
        }
        return resolve(DecklistParser.parse(decklistText));
    }

    /** Reads a deck from the site it lives on, or explains why it cannot. */
    private ParsedDecklist fetchLinked(DeckLink link) throws IOException {
        if (!link.provider().isFetchable()) {
            return problemOnly(link.describeUnfetchable());
        }
        if (archidekt == null) {
            return problemOnly("Reading decks from " + link.provider().displayName()
                    + " is not available on this server. Paste the text export instead.");
        }
        return archidekt.fetch(link);
    }

    private static ParsedDecklist problemOnly(String reason) {
        return new ParsedDecklist(null, List.of(), List.of(new ParseProblem(1, "", reason)));
    }

    public ResolvedDeck resolve(ParsedDecklist parsed) throws IOException {
        if (parsed == null || parsed.isEmpty()) {
            return new ResolvedDeck(
                    parsed == null ? null : parsed.name(),
                    List.of(),
                    List.of(),
                    parsed == null ? List.of() : parsed.problems());
        }

        List<DecklistEntry> entries = parsed.entries();

        Map<Integer, CardQuery> primaryQueries = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            primaryQueries.put(index, primaryQuery(entries.get(index)));
        }
        CollectionResult primary = source.resolve(List.copyOf(primaryQueries.values()));

        // Second pass: lines whose printing hint found nothing, retried by name.
        Map<Integer, CardQuery> fallbackQueries = new LinkedHashMap<>();
        for (Map.Entry<Integer, CardQuery> entry : primaryQueries.entrySet()) {
            if (primary.get(entry.getValue()).isEmpty() && !(entry.getValue() instanceof CardQuery.ByName)) {
                fallbackQueries.put(entry.getKey(), CardQuery.byName(entries.get(entry.getKey()).name()));
            }
        }
        CollectionResult fallback = fallbackQueries.isEmpty()
                ? CollectionResult.empty()
                : source.resolve(List.copyOf(fallbackQueries.values()));

        List<ResolvedCard> resolved = new ArrayList<>();
        List<UnresolvedEntry> unresolved = new ArrayList<>();

        for (int index = 0; index < entries.size(); index++) {
            DecklistEntry entry = entries.get(index);
            CardQuery query = primaryQueries.get(index);

            Optional<CardMetadata> match = primary.get(query);
            boolean printingWasChosenForUs = !entry.hasPrintingHint();

            if (match.isEmpty() && fallbackQueries.containsKey(index)) {
                match = fallback.get(fallbackQueries.get(index));
                // The line asked for a printing that does not exist, so whatever we found
                // instead is our choice and the chooser should offer alternatives.
                printingWasChosenForUs = true;
            }

            if (match.isEmpty()) {
                unresolved.add(new UnresolvedEntry(entry, describeMiss(entry)));
                continue;
            }

            CardMetadata card = match.get();
            resolved.add(new ResolvedCard(
                    CardIdentity.ofPrinting(card.scryfallId(), entry.foil()),
                    card,
                    entry.quantity(),
                    entry.section(),
                    entry,
                    printingWasChosenForUs));
        }

        return new ResolvedDeck(parsed.name(), resolved, unresolved, parsed.problems());
    }

    private static CardQuery primaryQuery(DecklistEntry entry) {
        // An exact printing beats every hint: a deck site that told us the Scryfall id has
        // left nothing to resolve, including which half of a split card we meant.
        if (entry.scryfallId() != null) {
            return CardQuery.byId(entry.scryfallId());
        }
        if (entry.setCode() != null && entry.collectorNumber() != null) {
            return CardQuery.byPrinting(entry.setCode(), entry.collectorNumber());
        }
        if (entry.setCode() != null) {
            return CardQuery.byNameInSet(entry.name(), entry.setCode());
        }
        return CardQuery.byName(entry.name());
    }

    private static String describeMiss(DecklistEntry entry) {
        if (entry.hasPrintingHint()) {
            return "no card named \"" + entry.name() + "\" was found, in " + entry.setCode() + " or anywhere else";
        }
        return "no card named \"" + entry.name() + "\" was found";
    }
}
