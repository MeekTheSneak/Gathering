package dev.gathering.core.decklist;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The result of parsing a decklist: what was understood, and what was not. */
public record ParsedDecklist(String name, List<DecklistEntry> entries, List<ParseProblem> problems) {

    public static final ParsedDecklist EMPTY = new ParsedDecklist(null, List.of(), List.of());

    public ParsedDecklist {
        entries = entries == null ? List.of() : List.copyOf(entries);
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public Optional<String> deckName() {
        return Optional.ofNullable(name);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<DecklistEntry> entriesIn(DeckSection section) {
        return entries.stream().filter(e -> e.section() == section).toList();
    }

    /** Total physical cards in a section, summing quantities rather than counting lines. */
    public int cardCount(DeckSection section) {
        return entriesIn(section).stream().mapToInt(DecklistEntry::quantity).sum();
    }

    /** Total physical cards across every section. */
    public int totalCards() {
        return entries.stream().mapToInt(DecklistEntry::quantity).sum();
    }

    public Map<DeckSection, List<DecklistEntry>> bySection() {
        Map<DeckSection, List<DecklistEntry>> out = new EnumMap<>(DeckSection.class);
        for (DecklistEntry entry : entries) {
            out.computeIfAbsent(entry.section(), s -> new java.util.ArrayList<>()).add(entry);
        }
        out.replaceAll((s, list) -> List.copyOf(list));
        return out;
    }
}
