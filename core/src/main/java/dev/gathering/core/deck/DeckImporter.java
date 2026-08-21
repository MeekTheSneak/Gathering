package dev.gathering.core.deck;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.decklist.DecklistEntry;
import dev.gathering.core.decklist.DecklistParser;
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
 *
 * <p>Two passes, both batched. The first asks for exactly what each line said - a printing
 * where the line named one, a card where it did not. The second retries the lines whose
 * printing hint matched nothing, this time by name alone, because a stale set code from a
 * three-year-old export should cost the player a different printing and not a missing card.
 *
 * <p>A hundred-card Commander decklist costs two requests on a cold cache and none on a warm
 * one.
 */
public final class DeckImporter {

    private final CardSource source;

    public DeckImporter(CardSource source) {
        this.source = java.util.Objects.requireNonNull(source, "source");
    }

    /** Parse and resolve in one step, which is what the import screen actually calls. */
    public ResolvedDeck importText(String decklistText) throws IOException {
        return resolve(DecklistParser.parse(decklistText));
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
