package dev.gathering.core.collection;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.decklist.DeckSection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Building a deck out of a collection, from a list.
 * <p>Taking cards out one at a time is how a collection works and it is not how a deck gets
 * built. A hundred-card Commander list is a hundred separate clicks, which is long enough that
 * nobody does it twice - so this is the same gesture the rest of the mod is built on, done in
 * one go: hand over a list, get back the deck the collection can build and an honest account
 * of what it is short.
 * <p><strong>By card, not by printing.</strong> A list off a deck site names exact printings,
 * and almost nobody owns the ones it names. Two printings of the same card are the same card
 * to play with, so a list asking for a Sol Ring is answered with whichever Sol Ring is in the
 * box. Which is the opposite of what {@link dev.gathering.core.format.PoolCheck} does, and
 * deliberately: that one asks whether a limited deck is built from the cardboard somebody
 * actually opened, where a foil showcase swapped in for a drafted common is the exact thing
 * being caught. Here nothing is being caught - the cards are already yours.
 * <p><strong>The plain copy first.</strong> Where a collection holds a card both ways, the
 * non-foil goes in the deck. Sleeving somebody's only foil when there was an ordinary one in
 * the same box is the kind of thing a player notices afterwards and cannot undo.
 * <p><strong>Nothing free is taken.</strong> Basic lands are given away everywhere in this mod
 * and a deck built here is no exception, so a line marked free comes back as a count for the
 * caller to conjure rather than as cards out of the box. Taking somebody's Forests when
 * Forests cost nothing would be a charge for something that is not for sale. Whether a card is
 * free is the caller's to say: this layer has no card database and is not going to grow one.
 * <p>Deterministic. The same collection and the same list give the same deck every time, which
 * is what makes "build it again" a thing somebody can rely on.
 * <p>Pure.
 */
public final class DeckFromCollection {

    /**
     * As many of one card as a single line may ask for.
     * <p>A whole deck is a thousand cards at the outside, so no one line is. It exists because
     * the list comes from somebody's clipboard: a line asking for two billion Islands should
     * come back short, not spend the server's memory saying so.
     */
    public static final int MOST_PER_LINE = 1024;

    private DeckFromCollection() {
    }

    /**
     * One line of the list.
     *
     * @param free whether this card is given away rather than owned - basic lands, and nothing
     *             else today. A free line never takes anything out of the collection and is
     *             never short.
     */
    public record Wanted(String name, int howMany, DeckSection section, boolean free) {

        public Wanted {
            name = name == null ? "" : name.trim();
            howMany = Math.max(0, Math.min(MOST_PER_LINE, howMany));
            section = section == null ? DeckSection.MAINBOARD : section;
        }

        public static Wanted of(String name, int howMany, DeckSection section) {
            return new Wanted(name, howMany, section, false);
        }
    }

    /**
     * What one line became.
     *
     * @param cards one entry per physical card, in the order they should go into the deck
     * @param free  how many of this line are to be conjured rather than taken
     */
    public record Line(String name, DeckSection section, List<CardIdentity> cards, int free) {

        public Line {
            name = name == null ? "" : name;
            cards = cards == null ? List.of() : List.copyOf(cards);
            free = Math.max(0, free);
        }

        public int total() {
            return cards.size() + free;
        }
    }

    /** What a line could not get, and how much of it. */
    public record Missing(String name, DeckSection section, int howMany) {
    }

    /** The whole answer: what to build, what to take, and what is short. */
    public record Building(List<Line> lines, List<Missing> missing, CardTally taking) {

        public static final Building NOTHING = new Building(List.of(), List.of(), CardTally.EMPTY);

        public Building {
            lines = lines == null ? List.of() : List.copyOf(lines);
            missing = missing == null ? List.of() : List.copyOf(missing);
            taking = taking == null ? CardTally.EMPTY : taking;
        }

        /** Whether the collection covered the whole list. */
        public boolean isComplete() {
            return missing.isEmpty();
        }

        /** How many cards altogether the collection could not find. */
        public int shortBy() {
            int short0 = 0;
            for (Missing gap : missing) {
                short0 += gap.howMany();
            }
            return short0;
        }

        /** How many cards the deck would come to. */
        public int size() {
            int cards = 0;
            for (Line line : lines) {
                cards += line.total();
            }
            return cards;
        }
    }

    /** What the collection can say about a card in it. */
    @FunctionalInterface
    public interface Naming {

        /** What this printing is called, or empty where the collection cannot say. */
        String nameOf(CardIdentity card);
    }

    /**
     * What this collection can build of this list.
     * <p>Lines are answered in order and the collection is consumed as they go, so two lines
     * naming the same card cannot both take the same copies - which is what a mainboard and a
     * sideboard asking for the same card would otherwise do.
     */
    public static Building from(List<Wanted> wanted, CardTally holding, Naming naming) {
        if (wanted == null || wanted.isEmpty()) {
            return Building.NOTHING;
        }
        Map<String, List<CardIdentity>> byName = shelved(holding, naming);
        Map<CardIdentity, Integer> left = new LinkedHashMap<>();
        if (holding != null) {
            for (CardIdentity card : holding.cards()) {
                left.put(card, holding.of(card));
            }
        }

        List<Line> lines = new ArrayList<>();
        List<Missing> missing = new ArrayList<>();
        CardTally.Builder taking = CardTally.builder();
        for (Wanted line : wanted) {
            if (line.howMany() <= 0 || line.name().isEmpty()) {
                continue;
            }
            if (line.free()) {
                // Given away, so there is nothing to take and nothing to be short of.
                lines.add(new Line(line.name(), line.section(), List.of(), line.howMany()));
                continue;
            }

            List<CardIdentity> took = new ArrayList<>();
            int still = line.howMany();
            for (CardIdentity printing : byName.getOrDefault(key(line.name()), List.of())) {
                if (still <= 0) {
                    break;
                }
                int available = left.getOrDefault(printing, 0);
                int take = Math.min(available, still);
                if (take <= 0) {
                    continue;
                }
                left.put(printing, available - take);
                taking.add(printing, take);
                for (int one = 0; one < take; one++) {
                    took.add(printing);
                }
                still -= take;
            }
            lines.add(new Line(line.name(), line.section(), took, 0));
            if (still > 0) {
                missing.add(new Missing(line.name(), line.section(), still));
            }
        }
        return new Building(lines, missing, taking.build());
    }

    /**
     * The collection laid out by card name, plain copies first.
     * <p>Then by printing, so a box holding four different Sol Rings hands them over in the
     * same order every time. A deck that came out differently on two identical presses would
     * be a deck nobody could rebuild.
     */
    private static Map<String, List<CardIdentity>> shelved(CardTally holding, Naming naming) {
        Map<String, List<CardIdentity>> byName = new LinkedHashMap<>();
        if (holding == null || naming == null) {
            return byName;
        }
        for (CardIdentity card : holding.cards()) {
            String name = naming.nameOf(card);
            if (name == null || name.isBlank()) {
                // A printing this collection cannot name is a printing no line can ask for.
                continue;
            }
            byName.computeIfAbsent(key(name), any -> new ArrayList<>()).add(card);
        }
        Comparator<CardIdentity> plainFirst = Comparator
                .comparing((CardIdentity card) -> card.foil() ? 1 : 0)
                .thenComparing(CardIdentity::cacheKey);
        for (List<CardIdentity> printings : byName.values()) {
            printings.sort(plainFirst);
        }
        return byName;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
