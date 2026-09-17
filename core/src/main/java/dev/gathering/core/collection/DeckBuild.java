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
 * <p>Everything a deck builder screen shows is worked out here: what is in the deck, grouped
 * into the piles a person reads it in, how many of each card, the mana curve, and whether the
 * commander's color identity has anything to say about it. The screen adds and removes cards
 * and draws what comes back, which keeps the part that can be checked in milliseconds as large
 * as it can be - the screen has no arithmetic of its own to get wrong.
 * <p><strong>It refuses nothing.</strong> Not the fifth copy of a card, not a card outside the
 * commander's colors, not a hundred and one cards in a hundred-card format. Those are all
 * reported and none of them are prevented, which is the same rule the rest of the mod plays
 * by: the deck check at the door is the one referee, and a builder that argues with somebody
 * about their own deck while they are still building it is a worse tool than one that tells
 * them what it noticed.
 * <p>Immutable, and every change returns a new one. A builder screen that can be undone is a
 * list of these, and that is worth more than saving an allocation per click.
 * <p>Pure.
 */
public record DeckBuild(
        List<BuildCard> cards, List<BuildCard> sideboard, Optional<BuildCard> commander) {

    /**
     * Which pile of a build a card is in.
     * <p>Named as the deck item's own sections are, so the one screen that shows both calls
     * the same pile by the same word. {@code :core} cannot see the item, which is why this is
     * a second enum rather than that one.
     */
    public enum Pile {
        MAINBOARD,
        SIDEBOARD,
        COMMANDERS
    }

    /** A deck is not unbounded. Far past any real one, and it is a clipboard away from a screen. */
    public static final int MOST_CARDS = 1024;

    public static final DeckBuild EMPTY = new DeckBuild(List.of(), List.of(), Optional.empty());

    public DeckBuild {
        cards = cards == null ? List.of() : List.copyOf(cards);
        sideboard = sideboard == null ? List.of() : List.copyOf(sideboard);
        commander = commander == null ? Optional.empty() : commander;
        // Both halves against one bound, because both halves become one deck item and that is
        // what the item is bounded at. A sideboard counted separately would let a build past
        // the limit in two pieces that each fit.
        if (cards.size() + sideboard.size() > MOST_CARDS) {
            throw new IllegalArgumentException(
                    "A build of " + (cards.size() + sideboard.size()) + " is past " + MOST_CARDS);
        }
    }

    /**
     * Adds one copy, at the end.
     * <p>At the end rather than sorted in, because the groups are worked out when they are
     * asked for and the order within a group is the order cards arrived. A list that
     * re-sorted itself on every click would move the card somebody was about to click again.
     */
    public DeckBuild with(BuildCard card) {
        if (card == null || held() >= MOST_CARDS) {
            return this;
        }
        List<BuildCard> added = new ArrayList<>(cards);
        added.add(card);
        return new DeckBuild(added, sideboard, commander);
    }

    /**
     * The same, into the sideboard.
     * <p>A copy of its own rather than a card taken out of the deck: this is what a click on
     * the box means, and somebody who owns four of a card may want three in the deck and the
     * fourth beside it. Moving a copy already picked is {@link #moved}.
     */
    public DeckBuild aside(BuildCard card) {
        if (card == null || held() >= MOST_CARDS) {
            return this;
        }
        List<BuildCard> added = new ArrayList<>(sideboard);
        added.add(card);
        return new DeckBuild(cards, added, commander);
    }

    /** How many cards this build is holding in its two lists, which is what the bound is on. */
    private int held() {
        return cards.size() + sideboard.size();
    }

    /**
     * Takes one copy back out - the last one that went in, of that printing.
     * <p>The last rather than the first, so adding four and removing one leaves the three that
     * were already sitting in the list where they were.
     * <p>Out of the deck where the deck has one, and out of the sideboard otherwise, so a
     * caller that only knows the printing never has to ask which list it landed in.
     */
    public DeckBuild without(UUID printing) {
        DeckBuild left = without(printing, Pile.MAINBOARD);
        return left == this ? without(printing, Pile.SIDEBOARD) : left;
    }

    /**
     * Takes one copy out of the pile it was clicked in.
     * <p>Named rather than searched for, because the screen knows which row was pressed and a
     * search would take the deck's copy when somebody pointed at the sideboard's.
     */
    public DeckBuild without(UUID printing, Pile from) {
        if (printing == null || from == null) {
            return this;
        }
        if (from == Pile.COMMANDERS) {
            return commander.filter(card -> card.printing().equals(printing)).isPresent()
                    ? new DeckBuild(cards, sideboard, Optional.empty())
                    : this;
        }
        List<BuildCard> pile = from == Pile.SIDEBOARD ? sideboard : cards;
        for (int index = pile.size() - 1; index >= 0; index--) {
            if (pile.get(index).printing().equals(printing)) {
                List<BuildCard> left = new ArrayList<>(pile);
                left.remove(index);
                return from == Pile.SIDEBOARD
                        ? new DeckBuild(cards, left, commander)
                        : new DeckBuild(left, sideboard, commander);
            }
        }
        return this;
    }

    /**
     * Moves one copy of a card already picked from one pile to another.
     * <p>One operation in every direction, rather than a verb per destination: a builder whose
     * right-click means "make commander" is a Commander deck builder, and the formats that
     * live on their sideboard are the ones that would notice. The commander takes whatever is
     * moved into the command zone and there is only room for one, so the card that was there
     * goes back to the deck rather than vanishing.
     */
    public DeckBuild moved(BuildCard card, Pile from, Pile to) {
        if (card == null || from == null || to == null || from == to) {
            return this;
        }
        DeckBuild taken = without(card.printing(), from);
        if (taken == this) {
            // Nothing of that card in the pile it was said to be in. The list has moved under
            // the click, and putting a copy in anyway would be the build conjuring a card.
            return this;
        }
        return switch (to) {
            case MAINBOARD -> taken.with(card);
            case SIDEBOARD -> taken.aside(card);
            // Not led: that takes a copy out of the build for itself, and this copy is already out.
            // A build holding two of a card lost the second the moment the first was made commander.
            case COMMANDERS -> taken.leading(card);
        };
    }

    /**
     * Names the commander, and takes it out of the deck or the sideboard if it was in one.
     * <p>Out, because it is in the command zone now and a card cannot be in two places. This
     * is the one place the builder moves a card the player did not ask it to move, and it is
     * the move they meant.
     * <p>Whoever was leading goes back to the deck rather than out of the build: a commander
     * replaced is a card somebody owns and picked, and dropping it would be the builder
     * quietly putting a card back in the box.
     */
    public DeckBuild led(BuildCard card) {
        if (card == null) {
            return commander.map(this::backToTheDeck)
                    .orElseGet(() -> new DeckBuild(cards, sideboard, Optional.empty()));
        }
        return without(card.printing()).leading(card);
    }

    /**
     * The same, for a card already out of the pile it came from: nothing is taken out here.
     * <p>Whoever was leading still goes back to the deck.
     */
    private DeckBuild leading(BuildCard card) {
        DeckBuild room = commander()
                .filter(already -> !already.printing().equals(card.printing()))
                .map(this::backToTheDeck)
                .orElse(this);
        return new DeckBuild(room.cards(), room.sideboard(), Optional.of(card));
    }

    /** The commander out of the command zone and into the ninety-nine, where it can be seen. */
    private DeckBuild backToTheDeck(BuildCard leading) {
        List<BuildCard> added = new ArrayList<>(cards);
        if (cards.size() + sideboard.size() < MOST_CARDS) {
            added.add(leading);
        }
        return new DeckBuild(added, sideboard, Optional.empty());
    }

    /**
     * How many copies of this card - by oracle id, because every copy limit in Magic is.
     * <p>The sideboard counted with the deck, because every copy limit in Magic counts it too.
     */
    public int copiesOf(UUID oracle) {
        int found = 0;
        for (BuildCard card : cards) {
            if (card.oracle().equals(oracle)) {
                found++;
            }
        }
        for (BuildCard card : sideboard) {
            if (card.oracle().equals(oracle)) {
                found++;
            }
        }
        return found;
    }

    /**
     * How many copies of this exact printing, which is what a collection can hand over.
     * <p>Both lists, because both come out of the same box. Counting the deck alone let a
     * card moved to the sideboard be taken out of the collection a second time.
     */
    public int printingsOf(UUID printing) {
        int found = 0;
        for (BuildCard card : cards) {
            if (card.printing().equals(printing)) {
                found++;
            }
        }
        for (BuildCard card : sideboard) {
            if (card.printing().equals(printing)) {
                found++;
            }
        }
        return found;
    }

    /**
     * The commander, the deck and the sideboard. What actually leaves the collection.
     * <p>The sideboard among them, because a sideboard is real cards out of a real box - the
     * deck that arrives holds them and the collection is that many lighter.
     */
    public List<BuildCard> everything() {
        if (commander.isEmpty() && sideboard.isEmpty()) {
            return cards;
        }
        List<BuildCard> all = new ArrayList<>(held() + 1);
        commander.ifPresent(all::add);
        all.addAll(cards);
        all.addAll(sideboard);
        return List.copyOf(all);
    }

    /** Every card picked, wherever it is going. What the deck it becomes will weigh. */
    public int total() {
        return held() + (commander.isPresent() ? 1 : 0);
    }

    /** The deck proper: the mainboard and the command zone, and never the sideboard. */
    public int deckTotal() {
        return cards.size() + (commander.isPresent() ? 1 : 0);
    }

    /**
     * The deck laid out in the piles it is read in, each pile's cards collapsed into rows.
     * <p>One row per card rather than per copy, with the count on it, because a decklist that
     * printed "Lightning Bolt" four times is a decklist nobody can scan. Rows are sorted by
     * mana value and then by name, which is the order every deck site lays a pile out in and
     * the order that makes a curve legible without drawing one.
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

    /**
     * The sideboard, collapsed and sorted exactly as a pile of the deck is.
     * <p>Its own list rather than a pile among the kinds, because it is a section of the list
     * and not a kind of card: fifteen cards under one heading, in the order the piles above it
     * are in, so the eye reads down the whole column the same way.
     */
    public List<Row> sideboardRows() {
        Map<UUID, Row> gathered = new LinkedHashMap<>();
        for (BuildCard card : sideboard) {
            Row already = gathered.get(card.printing());
            gathered.put(card.printing(),
                    already == null ? new Row(card, 1) : new Row(card, already.count() + 1));
        }
        List<Row> rows = new ArrayList<>(gathered.values());
        rows.sort(Comparator
                .comparingDouble((Row row) -> row.card().manaValue())
                .thenComparing(row -> row.card().name()));
        return List.copyOf(rows);
    }

    /** One line of a laid-out deck: a card, and how many of it are in there. */
    public record Row(BuildCard card, int count) {
    }

    /** How many buckets the curve has. Seven and up share the last one, as every site does. */
    public static final int CURVE_BUCKETS = 8;

    /**
     * The mana curve, as a count per mana value with everything from seven up in the last.
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
     * The color identity the commander gives this deck, or empty when there is no commander.
     * <p>Straight off the commander. A deck with no commander has no identity to be inside or
     * outside of, which is different from having a colorless one - hence the empty optional
     * rather than an empty set.
     */
    public Optional<Set<String>> identity() {
        return commander.map(BuildCard::colorIdentity);
    }

    /**
     * The cards that fall outside the commander's color identity.
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
