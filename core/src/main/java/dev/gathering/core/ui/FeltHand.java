package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A seat's hand of cards drawn on the table in front of that seat, as a fan: where each card is,
 * and at what angle.
 * <p>Asked by both boards, the flat one on the screen and the one drawn on the block, so the two
 * cannot fan one hand two ways. The fan itself is {@link HeldFan}, the one a seated body holds,
 * so a hand on the felt and the same hand in a fist agree about what a hand of that many cards
 * looks like.
 * <p><b>It is given a number.</b> Not a hand, not a view - a count, and the band to lay it in. The
 * count of another player's hand is public; what is in it is not, and a layout that cannot be
 * handed a card cannot place one where it would give it away. Whether a card is drawn face up is
 * the painter's business, decided by what the view already carries.
 * <p>Pure, and remembered: each fan is worked out once per band and count and then shared, because
 * both boards ask for every seat on every frame and the answer never changes.
 */
public final class FeltHand {

    /**
     * The most cards a fan on the felt shows.
     * <p>Ten reads as "a lot" already, and the count is in the strip along the top for anybody who
     * needs the number. Past ten the fan stays the fan of ten rather than growing thinner.
     */
    public static final int MOST_SHOWN = 10;

    /**
     * How much of the band is left clear at each side of the fan, as a share of the band.
     * <p>So a fan never touches the edge of the mat behind it, where two lines meeting read as one
     * thing, and a hand as part of the board.
     */
    private static final double CLEARANCE = 0.06;

    /** Fans already worked out, by the band they were fitted to. */
    private static final Map<Fit, List<List<Slot>>> FANS = new ConcurrentHashMap<>();

    /**
     * How many fitted bands are remembered. A table has a band per seat and there are only so many
     * table shapes, so this is never reached in play; it is a ceiling rather than a budget.
     */
    private static final int MOST_REMEMBERED = 512;

    private FeltHand() {
    }

    /**
     * One card of the fan.
     *
     * @param where the card's rectangle before it is turned, in the band's own units; it turns
     *     about its middle
     * @param angle degrees clockwise seen from above, in the seat's own frame - a painter adds the
     *     seat's facing, exactly as it does for a card lying on that seat's mat
     */
    public record Slot(Rect where, int angle) {
    }

    /** The band, the way it faces and the widest card allowed: everything a fitted fan depends on. */
    private record Fit(Rect band, boolean turned, double widestCard) {
    }

    /**
     * A hand of this many cards, laid in this band, in the order they are drawn: the one on top last.
     * <p>Empty for no cards, or for a band with no room in it. Past {@link #MOST_SHOWN} it is the fan
     * of {@link #MOST_SHOWN}.
     * <p>The cards are one size whatever the count, fitted so the widest fan there is fits the band:
     * a hand whose cards shrank as it grew would read as cards moving further away.
     *
     * @param band where the hand may lie
     * @param turned whether the seat is laid out the other way up, so its player sits at the band's
     *     top edge rather than its bottom one
     * @param widestCard how wide a card may be at most - the size of a card on that seat's mat, so a
     *     hand is never drawn bigger than the table's own cards
     */
    public static List<Slot> of(Rect band, boolean turned, int cards, double widestCard) {
        int shown = Math.min(cards, MOST_SHOWN);
        if (shown <= 0 || band == null || band.isEmpty() || !(widestCard > 0)) {
            return List.of();
        }
        Fit fit = new Fit(band, turned, widestCard);
        List<List<Slot>> fans = FANS.get(fit);
        if (fans == null) {
            if (FANS.size() >= MOST_REMEMBERED) {
                FANS.clear();
            }
            fans = FANS.computeIfAbsent(fit, FeltHand::fitted);
        }
        return fans.get(shown - 1);
    }

    /**
     * Where a card going into or out of this hand is: the card a hand of one would be.
     * <p>So a card drawn flies into the fan rather than to a spot beside it, and at the fan's size.
     */
    public static Rect landing(Rect band, boolean turned, double widestCard) {
        List<Slot> one = of(band, turned, 1, widestCard);
        return one.isEmpty() ? Rect.NONE : one.get(0).where();
    }

    /** Every fan from one card to {@link #MOST_SHOWN}, fitted to this band. */
    private static List<List<Slot>> fitted(Fit fit) {
        double tall = CardShape.heightFor(1.0);
        // How far the fans reach about their middle card, in card widths and at every count at once,
        // so one card size and one anchor serve them all and no count spills out of the band.
        double left = 0;
        double right = 0;
        double near = 0;
        double far = 0;
        for (int count = 1; count <= MOST_SHOWN; count++) {
            for (HeldFan.Card card : HeldFan.of(count)) {
                double turn = Math.toRadians(Math.round(card.angle()));
                double halfAcross = Math.abs(Math.cos(turn)) / 2 + Math.abs(Math.sin(turn)) * tall / 2;
                double halfDown = Math.abs(Math.sin(turn)) / 2 + Math.abs(Math.cos(turn)) * tall / 2;
                left = Math.min(left, card.slide() - halfAcross);
                right = Math.max(right, card.slide() + halfAcross);
                far = Math.min(far, card.lift() - halfDown);
                near = Math.max(near, card.lift() + halfDown);
            }
        }
        Rect band = fit.band();
        double roomAcross = band.width() * (1 - 2 * CLEARANCE);
        double roomDown = band.height() * (1 - 2 * CLEARANCE);
        int width = (int) Math.floor(Math.min(fit.widestCard(),
                Math.min(roomAcross / (right - left), roomDown / (near - far))));
        int height = CardShape.heightFor(width);
        List<List<Slot>> fans = new ArrayList<>(MOST_SHOWN);
        if (width <= 0 || height <= 0) {
            for (int count = 1; count <= MOST_SHOWN; count++) {
                fans.add(List.of());
            }
            return List.copyOf(fans);
        }
        // The reach, centered in the band. The middle card is not in the middle of it: the ends of a
        // fan come round toward the hand holding it, so the fan reaches further that way.
        double middleAcross = (left + right) / 2;
        double middleDown = (far + near) / 2;
        // Toward the player is down the band for a seat facing the usual way and up it for one laid
        // out the other way up, which is the whole of the difference: a half turn about the band's
        // middle.
        double toward = fit.turned() ? -1 : 1;
        for (int count = 1; count <= MOST_SHOWN; count++) {
            List<Slot> fan = new ArrayList<>(count);
            for (HeldFan.Card card : HeldFan.of(count)) {
                double x = band.centerX() + toward * (card.slide() - middleAcross) * width;
                double y = band.centerY() + toward * (card.lift() - middleDown) * width;
                fan.add(new Slot(
                        new Rect((int) Math.round(x - width / 2.0), (int) Math.round(y - height / 2.0),
                                width, height),
                        Math.round(card.angle())));
            }
            fans.add(List.copyOf(fan));
        }
        return List.copyOf(fans);
    }
}
