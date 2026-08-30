package dev.gathering.core.collection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The deck somebody is building, as a value.
 *
 * <p>Everything a deck builder screen shows is worked out here: what is in the deck, grouped
 * into the piles a person reads it in, how many of each card, the mana curve, and whether the
 * commander's colour identity has anything to say about it. The screen adds and removes cards
 * and draws what comes back, which keeps the part that can be checked in milliseconds as large
 * as it can be - the screen has no arithmetic of its own to get wrong.
 *
 * <p><strong>It refuses nothing.</strong> Not the fifth copy of a card, not a card outside the
 * commander's colours, not a hundred and one cards in a hundred-card format. Those are all
 * reported and none of them are prevented, which is the same rule the rest of the mod plays
 * by: the deck check at the door is the one referee, and a builder that argues with somebody
 * about their own deck while they are still building it is a worse tool than one that tells
 * them what it noticed.
 *
 * <p>Immutable, and every change returns a new one. A builder screen that can be undone is a
 * list of these, and that is worth more than saving an allocation per click.
 *
 * <p>Pure.
 */
public record DeckBuild(List<BuildCard> cards, Optional<BuildCard> commander) {

    /** A deck is not unbounded. Far past any real one, and it is a clipboard away from a screen. */
    public static final int MOST_CARDS = 1024;

    public static final DeckBuild EMPTY = new DeckBuild(List.of(), Optional.empty());

    public DeckBuild {
        cards = cards == null ? List.of() : List.copyOf(cards);
        commander = commander == null ? Optional.empty() : commander;
        if (cards.size() > MOST_CARDS) {
            throw new IllegalArgumentException("A deck of " + cards.size() + " is past " + MOST_CARDS);
        }
    }

    /**
     * Adds one copy, at the end.
     *
     * <p>At the end rather than sorted in, because the groups are worked out when they are
     * asked for and the order within a group is the order cards arrived. A list that
     * re-sorted itself on every click would move the card somebody was about to click again.
     */
    public DeckBuild with(BuildCard card) {
        if (card == null || cards.size() >= MOST_CARDS) {
            return this;
        }
        List<BuildCard> added = new ArrayList<>(cards);
        added.add(card);
        return new DeckBuild(added, commander);
    }

    /**
     * Takes one copy back out - the last one that went in, of that printing.
     *
     * <p>The last rather than the first, so adding four and removing one leaves the three that
     * were already sitting in the list where they were.
     */
    public DeckBuild without(UUID printing) {
        for (int index = cards.size() - 1; index >= 0; index--) {
            if (cards.get(index).printing().equals(printing)) {
                List<BuildCard> left = new ArrayList<>(cards);
                left.remove(index);
                return new DeckBuild(left, commander);
            }
        }
        return this;
    }

    /**
     * Names the commander, and takes it out of the deck proper if it was in it.
     *
     * <p>Out, because it is in the command zone now and a card cannot be in two places. This
     * is the one place the builder moves a card the player did not ask it to move, and it is
     * the move they meant.
     */
    public DeckBuild led(BuildCard card) {
        if (card == null) {
            return new DeckBuild(cards, Optional.empty());
        }
        return new DeckBuild(without(card.printing()).cards(), Optional.of(card));
    }

    /** How many copies of this card - by oracle id, because every copy limit in Magic is. */
    public int copiesOf(UUID oracle) {
        int found = 0;
        for (BuildCard card : cards) {
            if (card.oracle().equals(oracle)) {
                found++;
            }
        }
        return found;
    }

    /** How many copies of this exact printing, which is what a collection can hand over. */
    public int printingsOf(UUID printing) {
        int found = 0;
        for (BuildCard card : cards) {
            if (card.printing().equals(printing)) {
                found++;
            }
        }
        return found;
    }

    /** The cards, plus the commander if there is one. What actually leaves the collection. */
    public List<BuildCard> everything() {
        if (commander.isEmpty()) {
            return cards;
        }
        List<BuildCard> all = new ArrayList<>(cards.size() + 1);
        all.add(commander.get());
        all.addAll(cards);
        return List.copyOf(all);
    }

    public int total() {
        return cards.size() + (commander.isPresent() ? 1 : 0);
    }

    /**
     * The deck laid out in the piles it is read in, each pile's cards collapsed into rows.
     *
     * <p>One row per card rather than per copy, with the count on it, because a decklist that
     * printed "Lightning Bolt" four times is a decklist nobody can scan. Rows are sorted by
     * mana value and then by name, which is the order every deck site lays a pile out in and
     * the order that makes a curve legible without drawing one.
     *
     * <p>Empty piles are left out entirely: a heading with nothing under it is a heading that
     * makes the list longer and says nothing.
     */
    public Map<CardKind, List<Row>> byKind() {
        Map<CardKind, Map<UUID, Row>> gathered = new EnumMap<>(CardKind.class);
        commander.ifPresent(card -> gathered
                .computeIfAbsent(CardKind.COMMANDER, ignored -> new LinkedHashMap<>())
                .put(card.printing(), new Row(card, 1)));
        for (BuildCard card : cards) {
            Map<UUID, Row> pile =
                    gathered.computeIfAbsent(card.kind(), ignored -> new LinkedHashMap<>());
            Row already = pile.get(card.printing());
            pile.put(card.printing(),
                    already == null ? new Row(card, 1) : new Row(card, already.count() + 1));
        }

        Map<CardKind, List<Row>> sorted = new EnumMap<>(CardKind.class);
        gathered.forEach((kind, pile) -> {
            List<Row> rows = new ArrayList<>(pile.values());
            rows.sort(Comparator
                    .comparingDouble((Row row) -> row.card().manaValue())
                    .thenComparing(row -> row.card().name()));
            sorted.put(kind, List.copyOf(rows));
        });
        return sorted;
    }

    /** One line of a laid-out deck: a card, and how many of it are in there. */
    public record Row(BuildCard card, int count) {
    }

    /** How many buckets the curve has. Seven and up share the last one, as every site does. */
    public static final int CURVE_BUCKETS = 8;

    /**
     * The mana curve, as a count per mana value with everything from seven up in the last.
     *
     * <p>Lands are left out, which is the convention and is also the only reading that means
     * anything: a curve is a picture of what the deck spends mana on, and lands are what it
     * spends. Including them puts a spike at zero that is the same height in every deck and
     * tells nobody anything.
     */
    public int[] curve() {
        int[] buckets = new int[CURVE_BUCKETS];
        for (BuildCard card : cards) {
            if (card.kind() == CardKind.LAND) {
                continue;
            }
            int at = (int) Math.min(CURVE_BUCKETS - 1, Math.max(0, Math.round(card.manaValue())));
            buckets[at]++;
        }
        return buckets;
    }

    /**
     * The colour identity the commander gives this deck, or empty when there is no commander.
     *
     * <p>Straight off the commander. A deck with no commander has no identity to be inside or
     * outside of, which is different from having a colourless one - hence the empty optional
     * rather than an empty set.
     */
    public Optional<Set<String>> identity() {
        return commander.map(BuildCard::colorIdentity);
    }

    /**
     * The cards that fall outside the commander's colour identity.
     *
     * <p>Reported, never prevented. This is the deck builder noticing something, which is what
     * a deck builder is for; refusing the card would be it deciding it knows the format better
     * than the person building the deck, and the check at the door already exists for the one
     * moment that judgment is wanted.
     */
    public List<BuildCard> outsideIdentity() {
        Set<String> identity = identity().orElse(null);
        if (identity == null) {
            return List.of();
        }
        List<BuildCard> outside = new ArrayList<>();
        for (BuildCard card : cards) {
            if (!card.insideIdentity(identity)) {
                outside.add(card);
            }
        }
        return List.copyOf(outside);
    }
}
