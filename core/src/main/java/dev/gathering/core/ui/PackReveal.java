package dev.gathering.core.ui;

import dev.gathering.core.card.Rarity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Going through a pack one card at a time, which is how a pack is actually opened.
 * <p>A booster is not a spread of cards; it is a stack you thumb through, and the whole of the ritual is
 * the order you go in. You leave the rare until last, and the moment before you turn the last card is the
 * moment the pack is for. Laying every card out at once - which is what this screen did - throws that
 * away: it hands you the answer and the question at the same instant.
 * <p>So: worst first, one card in front at a time, and the card <em>before</em> a good one lit from behind
 * by what is coming. That light is the tell, and it is honest - a pack that glows has something in it, the
 * same promise the light out of the tear already makes. Turning the card is then a thing the player does
 * rather than watches.
 * <p>This is the whole of the ceremony's arithmetic: what is in front, what is next, whether to shine, and
 * what noise the turn should make. The screen owns none of it, so all of it can be checked without one.
 * <p>Pure.
 */
public record PackReveal(List<Tier> order, int shown) {

    /**
     * What a card is worth to the ceremony.
     * <p>Rarity and nothing else. A showcase version used to be a tier of its own, which sounds right
     * and is wrong in the one case that matters: it <em>shadowed</em> the rarity, so pulling a showcase
     * mythic announced a showcase - the smaller noise - instead of a mythic. The owner hit exactly that
     * (2026-09-16). A rare and a mythic are the big moments; a special version of one is that same
     * moment and is announced as what it is.
     */
    public enum Tier {
        /** Nothing to announce. Most of a pack. */
        PLAIN,
        /** A rare, or one of the slots that stands in for one. */
        RARE,
        /** The one the pack was opened for. */
        MYTHIC;

        /** Whether a card of this tier is worth a light and a noise of its own. */
        public boolean worthAnnouncing() {
            return this != PLAIN;
        }

        /** What a card of this rarity is worth. */
        public static Tier of(Rarity rarity) {
            if (rarity == null) {
                return PLAIN;
            }
            return switch (rarity) {
                case MYTHIC -> MYTHIC;
                case RARE, SPECIAL, BONUS -> RARE;
                default -> PLAIN;
            };
        }
    }

    public PackReveal {
        order = order == null ? List.of() : List.copyOf(order);
        shown = Math.max(0, Math.min(shown, order.size()));
    }

    /** A pack about to be gone through, worst card first. */
    public static PackReveal of(List<Tier> cards) {
        return new PackReveal(inOrder(cards), 0);
    }

    /**
     * The order a pack is gone through: least worth announcing first, so it ends where it should.
     * <p>Stable within a tier, so the cards a pack was cut with stay in the order they came in and two
     * openings of the same pack go the same way.
     */
    public static List<Tier> inOrder(List<Tier> cards) {
        if (cards == null) {
            return List.of();
        }
        List<Tier> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparingInt(Enum::ordinal));
        return List.copyOf(sorted);
    }

    public int total() {
        return order.size();
    }

    /** How many are still to be turned, the one in front included. */
    public int left() {
        return order.size() - shown;
    }

    public boolean finished() {
        return shown >= order.size();
    }

    /** What is in front of the player right now. */
    public Tier inFront() {
        return finished() ? Tier.PLAIN : order.get(shown);
    }

    /**
     * What is behind it - the next card, which the one in front is lit by.
     * <p>{@link Tier#PLAIN} when there is nothing behind it worth saying so about, and on the last card,
     * where the thing being announced is the card you are already looking at.
     */
    public Tier nextUp() {
        int next = shown + 1;
        return next < order.size() ? order.get(next) : Tier.PLAIN;
    }

    /** Whether the card in front should be lit from behind by what is coming after it. */
    public boolean tells() {
        return !finished() && nextUp().worthAnnouncing();
    }

    /** The turn: the next card comes to the front. Doing it on a finished pack changes nothing. */
    public PackReveal turned() {
        return finished() ? this : new PackReveal(order, shown + 1);
    }

    /**
     * Whether the card that has just been turned to is worth a noise, and how big a one.
     * <p>Asked after {@link #turned()}, of the pack it hands back: the card in front is the one that has
     * just arrived, and its tier is the impact.
     */
    public Tier arriving() {
        return inFront();
    }
}
