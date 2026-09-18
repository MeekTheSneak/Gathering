package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * How a held hand of cards sits: the angle, slide and depth of each card in it.
 * <p>One place for it because it is asked twice and the two answers have to match. A player
 * sitting at the table sees everyone's hand drawn on the felt below their mat; a player standing
 * beside the table sees the same hand in that player's off hand, in the world. Two fans built
 * from two pieces of arithmetic would be two different hands, and the one thing a fan of card
 * backs says is <em>how many</em> - which is public, and which has to be the same number from
 * both chairs.
 * <p><b>Nothing here knows what a card is.</b> It is given a count. That is deliberate and it is
 * the visibility invariant written as a signature: a renderer that cannot be handed an identity
 * cannot leak one.
 * <p>Not {@code HandFan}, which is taken and is a different thing: that one lays your own hand
 * along the bottom of the seated screen, in pixels, with a card lifted under the cursor. This one
 * is the fan a body holds in the world, in card widths, and nobody hovers it.
 */
public final class HeldFan {

    /**
     * How far apart two cards are, at most, in degrees.
     * <p>What a hand of four or five looks like. Past about a dozen cards the spread below takes
     * over and the cards start overlapping more, which is also what happens to a real hand.
     */
    private static final double PER_CARD = 9;

    /**
     * How wide the whole fan may get, in degrees.
     * <p>A hand does not become a wheel. Forty cards - which happens, at the start of a game
     * somebody has not mulliganed into shape - fans across the same arc as twelve, just thinner.
     */
    private static final double WIDEST = 74;

    /**
     * How far apart two cards sit along the line of sight, as a share of a card's width.
     * <p>Small, and not zero: two coplanar quads z-fight, and a fan that flickers is a fan
     * somebody files a bug about. This is the same reason the card renderer draws a front and a
     * back with a hair between them rather than one double-sided quad.
     */
    private static final double DEPTH_STEP = 0.004;

    /**
     * How far the fan slides sideways per card, as a share of a card's width.
     * <p>The rotation alone pivots every card about one point, which is a fan held at the very
     * bottom corner. A little slide moves the pivot down the hand as it grows, which is what a
     * hand held in a fist looks like.
     */
    private static final double SLIDE_STEP = 0.06;

    /** How far the slide may get, in card widths, however many cards arrive. */
    private static final double WIDEST_SLIDE = 1.1;

    private HeldFan() {
    }

    /**
     * One card's place in the fan.
     *
     * @param angle  degrees from the middle of the fan, positive toward the far end
     * @param slide  sideways, in card widths, from the middle of the fan
     * @param lift   upward, in card widths - the ends of a fan ride a little higher than its middle
     * @param depth  toward the viewer, in card widths, so no two cards are coplanar
     */
    public record Card(float angle, float slide, float lift, float depth) {
    }

    /**
     * The whole fan, in the order the cards are drawn, nearest last.
     * <p>An empty hand is an empty list rather than a fan of nothing: a hand with no cards in it
     * has nothing in it, and a renderer that drew a zero-card fan drew a sliver at the wrist.
     */
    public static List<Card> of(int cards) {
        if (cards <= 0) {
            return List.of();
        }
        if (cards == 1) {
            // One card is held straight, not at half of nothing. The general case below divides by
            // the gaps between cards, and one card has none.
            return List.of(new Card(0, 0, 0, 0));
        }
        double spread = Math.min(WIDEST, PER_CARD * (cards - 1));
        double step = spread / (cards - 1);
        double slideStep = Math.min(SLIDE_STEP, WIDEST_SLIDE * 2 / (cards - 1));
        List<Card> fan = new ArrayList<>(cards);
        for (int at = 0; at < cards; at++) {
            double fromMiddle = at - (cards - 1) / 2.0;
            double angle = fromMiddle * step;
            // The ends of a fan rise, because they are rotated about a pivot below the hand. A
            // cosine of the angle is what that rise is, and it is small enough to be worth stating
            // rather than modeling: a card at the end of a 37-degree sweep sits about a fifth of
            // its height higher than the one in the middle.
            double lift = (1 - Math.cos(Math.toRadians(angle))) * 0.5;
            fan.add(new Card(
                    (float) angle,
                    (float) (fromMiddle * slideStep),
                    (float) lift,
                    (float) (at * DEPTH_STEP)));
        }
        return List.copyOf(fan);
    }

    /**
     * How wide the drawn fan is, in card widths, for a caller that has to fit it somewhere.
     * <p>The board view draws each seat's hand below that seat's mat, and a mat is a fixed share of
     * the table; a fan that did not know its own width would be scaled by eye until somebody
     * noticed it overlapping the mat below it.
     */
    public static double widthOf(int cards) {
        if (cards <= 0) {
            return 0;
        }
        double spread = Math.min(WIDEST, PER_CARD * Math.max(0, cards - 1));
        // The ends of the fan, plus half a card either side because a card has width of its own.
        return 2 * Math.sin(Math.toRadians(spread / 2)) + 1;
    }
}
