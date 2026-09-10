package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Five points in a ring, laid out the way the back of a Magic card lays them out.
 * <p>The color pie is a pentagon and everybody who has ever held a Magic card has seen it:
 * white at the top, then blue, black, red and green clockwise. It is the one diagram in the
 * game that a complete newcomer has already looked at hundreds of times without being told
 * what it was, which makes it the right shape for "pick two colors" - the screen is a
 * picture they already half know.
 * <p>Clockwise from the top, because that is the order the letters WUBRG are in and the order
 * the card back has them in. Getting that wrong would be drawing a pentagon that looks nearly
 * like the real one, which is worse than drawing something else entirely.
 * <p>Pure geometry. It knows nothing about mana symbols, screens or clicks - only where five
 * things go and which of them a point is nearest.
 */
public final class ColorWheel {

    /** One point on the ring, and which of the five it is. */
    public record Spoke(int index, int x, int y) {
    }

    /** How many points there are, which is how many colors Magic has. */
    public static final int SPOKES = 5;

    /**
     * A quarter turn, in radians.
     * <p>The angle a point at index zero sits at. Without it the first point would be to the
     * right rather than at the top, because that is where zero radians is - and the pentagon
     * would be the right shape rotated into the wrong one.
     */
    private static final double TOP = -Math.PI / 2;

    private ColorWheel() {
    }

    /**
     * Where one of the five sits, given the middle of the ring and how big it is.
     * <p>Rounded to whole pixels, because two points a third of a pixel apart draw in the same
     * place and a ring that is nearly symmetrical reads as a mistake.
     */
    public static Spoke spoke(int index, int centerX, int centerY, int radius) {
        int at = Math.floorMod(index, SPOKES);
        double angle = TOP + at * (2 * Math.PI / SPOKES);
        return new Spoke(at,
                centerX + (int) Math.round(Math.cos(angle) * radius),
                centerY + (int) Math.round(Math.sin(angle) * radius));
    }

    /** All five, in order, clockwise from the top. */
    public static List<Spoke> spokes(int centerX, int centerY, int radius) {
        List<Spoke> ring = new ArrayList<>(SPOKES);
        for (int at = 0; at < SPOKES; at++) {
            ring.add(spoke(at, centerX, centerY, radius));
        }
        return List.copyOf(ring);
    }

    /**
     * Which of the five a point is on, or -1 for a point on none of them.
     * <p>Round targets rather than rectangles, because these are drawn as circles and a
     * cursor a corner's width outside one should not be pointing at it. The nearest is taken
     * rather than the first, so the answer does not depend on which order they were checked
     * in when two overlap - which they do at small ring sizes.
     *
     * @param reach how far from a point's middle still counts as being on it
     */
    public static int at(int x, int y, int centerX, int centerY, int radius, int reach) {
        int found = -1;
        long nearest = (long) reach * reach;
        for (Spoke spoke : spokes(centerX, centerY, radius)) {
            long across = (long) x - spoke.x();
            long down = (long) y - spoke.y();
            long away = across * across + down * down;
            if (away <= nearest) {
                nearest = away;
                found = spoke.index();
            }
        }
        return found;
    }

    /**
     * How big a ring fits in a box, leaving room for the points themselves.
     * <p>The points sit <em>on</em> the ring, so the ring has to be a point's width smaller
     * than the box or half of every point is outside it. Never negative: a box too small for a
     * ring gets a radius of zero and five points on top of each other, which is ugly and
     * visible rather than an exception in a render.
     */
    public static int radiusIn(int width, int height, int spokeSize) {
        return Math.max(0, (Math.min(width, height) - spokeSize) / 2);
    }
}
