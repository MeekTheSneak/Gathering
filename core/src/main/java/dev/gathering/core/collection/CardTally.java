package dev.gathering.core.collection;

import dev.gathering.core.card.CardIdentity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * How many of each card a collection holds.
 *
 * <p>Counts rather than stacks. A real collection runs to tens of thousands of cards and no
 * slot-based container survives that: forty Forests is one entry with a forty on it, not forty
 * of anything. It keeps the thing compact, it makes searching it instant, and it is what a
 * binder is actually like.
 *
 * <p>Keyed by identity, which already means printing and finish together - so a foil Lightning
 * Bolt and an ordinary one are two entries, as they are two cards. Nothing here knows a card's
 * name, colour or rarity; those are looked up, and looking them up is the job of whatever is
 * showing the collection.
 *
 * <p>Immutable, and its order is the order things were first put in. That matters more than it
 * sounds: this is written to disk and drawn on a screen, and a per-launch hash order would
 * reshuffle somebody's binder every time the server restarted.
 *
 * <p>Pure.
 */
public record CardTally(Map<CardIdentity, Integer> counts) {

    public static final CardTally EMPTY = new CardTally(Map.of());

    public CardTally {
        Map<CardIdentity, Integer> kept = new LinkedHashMap<>();
        if (counts != null) {
            for (Map.Entry<CardIdentity, Integer> entry : counts.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                    // A count of nothing is not an entry. Kept out here rather than guarded
                    // at every reader, so "in the tally" and "you have one" mean the same.
                    continue;
                }
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        counts = java.util.Collections.unmodifiableMap(kept);
    }

    /**
     * A tally of one pile of cards, however many times each appears in it.
     *
     * <p>Not called {@code of}: {@code of(card)} next door asks how many, and one name for
     * "make me one of these" and "how many of these are there" is a name that will be read
     * wrong.
     */
    public static CardTally counting(Collection<CardIdentity> cards) {
        Map<CardIdentity, Integer> counts = new LinkedHashMap<>();
        if (cards != null) {
            for (CardIdentity card : cards) {
                if (card != null) {
                    counts.merge(card, 1, Integer::sum);
                }
            }
        }
        return new CardTally(counts);
    }

    /** How many of this card are here, which is nothing rather than null. */
    public int of(CardIdentity card) {
        return counts.getOrDefault(card, 0);
    }

    public boolean has(CardIdentity card) {
        return of(card) > 0;
    }

    /** How many cards altogether, counting every copy. */
    public int total() {
        int all = 0;
        for (int count : counts.values()) {
            all += count;
        }
        return all;
    }

    /** How many different cards, counting each however many copies. */
    public int distinct() {
        return counts.size();
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    /** Every card here, in the order they were first put in. */
    public Set<CardIdentity> cards() {
        return counts.keySet();
    }

    /**
     * The same collection with some more of a card in it.
     *
     * @param howMany a number of cards; nothing at all where it is not one
     */
    public CardTally plus(CardIdentity card, int howMany) {
        if (card == null || howMany <= 0) {
            return this;
        }
        Map<CardIdentity, Integer> added = new LinkedHashMap<>(counts);
        added.merge(card, howMany, Integer::sum);
        return new CardTally(added);
    }

    /** The same collection with another one poured into it. */
    public CardTally plus(CardTally other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        Map<CardIdentity, Integer> added = new LinkedHashMap<>(counts);
        for (Map.Entry<CardIdentity, Integer> entry : other.counts.entrySet()) {
            added.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return new CardTally(added);
    }

    /**
     * What came out and what is left.
     *
     * <p>Both, because "you asked for four and there were three" is the answer a caller needs
     * and a caller that gets only the remainder has to work it out again. Taking more than is
     * there takes what is there rather than failing: a collection is not a transaction log,
     * and somebody clicking "take four" of a card they have three of means "take my three".
     */
    public record Taking(CardTally left, int took) {
    }

    public Taking take(CardIdentity card, int howMany) {
        if (card == null || howMany <= 0) {
            return new Taking(this, 0);
        }
        int here = of(card);
        if (here == 0) {
            return new Taking(this, 0);
        }
        int taking = Math.min(here, howMany);
        Map<CardIdentity, Integer> left = new LinkedHashMap<>(counts);
        if (taking == here) {
            left.remove(card);
        } else {
            left.put(card, here - taking);
        }
        return new Taking(new CardTally(left), taking);
    }

    /**
     * Whether every card of another tally is here, in at least as many copies.
     *
     * <p>What "you own the cards to sleeve this deck" comes down to.
     */
    public boolean holdsAllOf(CardTally wanted) {
        if (wanted == null) {
            return true;
        }
        for (Map.Entry<CardIdentity, Integer> entry : wanted.counts.entrySet()) {
            if (of(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * What is missing before another tally could be taken out of this one.
     *
     * <p>Empty where it could. A deck that cannot be built out of a collection is a sentence
     * naming the cards it is short of, not a refusal.
     */
    public CardTally shortOf(CardTally wanted) {
        if (wanted == null) {
            return EMPTY;
        }
        Map<CardIdentity, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<CardIdentity, Integer> entry : wanted.counts.entrySet()) {
            int short_ = entry.getValue() - of(entry.getKey());
            if (short_ > 0) {
                missing.put(entry.getKey(), short_);
            }
        }
        return new CardTally(missing);
    }

    /**
     * Takes a whole tally out, as far as it goes.
     *
     * @return what is left and how many cards came out altogether
     */
    public Taking takeAll(CardTally wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return new Taking(this, 0);
        }
        CardTally left = this;
        int took = 0;
        for (Map.Entry<CardIdentity, Integer> entry : wanted.counts.entrySet()) {
            Taking one = left.take(entry.getKey(), entry.getValue());
            left = one.left();
            took += one.took();
        }
        return new Taking(left, took);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CardTally tally && counts.equals(tally.counts);
    }

    @Override
    public int hashCode() {
        return counts.hashCode();
    }

    @Override
    public String toString() {
        return "CardTally[" + distinct() + " cards, " + total() + " copies]";
    }

    /** A tally builder for readers that arrive an entry at a time. */
    public static final class Builder {

        private final Map<CardIdentity, Integer> counts = new LinkedHashMap<>();

        public Builder add(CardIdentity card, int howMany) {
            if (card != null && howMany > 0) {
                counts.merge(card, howMany, Integer::sum);
            }
            return this;
        }

        public CardTally build() {
            return new CardTally(counts);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Never null, so a caller can hand one straight on. */
    public static CardTally orEmpty(CardTally tally) {
        return Objects.requireNonNullElse(tally, EMPTY);
    }
}
