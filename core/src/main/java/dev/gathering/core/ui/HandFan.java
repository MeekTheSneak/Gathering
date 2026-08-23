package dev.gathering.core.ui;

/**
 * The cards in your hand, fanned along the bottom of the screen.
 *
 * <p>Modelled on what every digital card game has settled on, because it settles two problems
 * at once. A hand has no fixed size - it is three cards on turn ten and fifteen after a
 * Windfall - so the cards overlap rather than shrink, which keeps a card the size of a card.
 * And overlapping hides most of each one, so the card under the cursor rises out of the fan at
 * full size, which is how you read it without opening anything.
 *
 * <p>The arc is slight. A real hand splays because it is held in a fist; a drawn one only has
 * to look held, and past a few degrees the cards at the ends start lying down.
 */
public final class HandFan {

    /** How far the whole fan turns from end to end, at most. */
    private static final int SPREAD_DEGREES = 14;

    /** And how much of a turn any one card takes, so a big hand does not curl up. */
    private static final int MAX_STEP_DEGREES = 3;

    /** How much bigger the card under the cursor is drawn. */
    private static final double LIFTED_SCALE = 1.5;

    /** How far a card may overlap the one before it: at most this much of it stays showing. */
    private static final double TIGHTEST_STEP = 0.22;

    /** How high the middle of the arc rides above its ends, as a fraction of a card's height. */
    private static final double ARC_RISE = 0.07;

    /**
     * How much of the strip's width the fan is allowed to take.
     *
     * <p>A hand sits in the middle of the screen and stays there. Letting it spread to the
     * full width - which is what happens if the cards are simply shared out evenly across the
     * space - draws fifteen cards as a wall from edge to edge, which looks like a card
     * catalogue rather than a hand somebody is holding.
     */
    private static final double FAN_SHARE_OF_WIDTH = 0.7;


    private HandFan() {
    }

    /** One card in the fan: where it is drawn and which way it is turned. */
    public record Slot(Rect where, int angle) {
    }

    /**
     * Where the nth card of a hand sits.
     *
     * @param lifted which card the cursor is on, or -1 - it is drawn larger and higher
     */
    public static Slot slot(Rect area, int count, int index, int lifted) {
        if (area.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return new Slot(Rect.NONE, 0);
        }
        int width = widthFor(area, count);
        int height = Math.max(8, CardShape.heightFor(width));

        int step = stepFor(area, count, width);
        int total = width + step * (count - 1);
        int left = area.x() + (area.width() - total) / 2;

        // The arc: the ends turn out and the middle rides a little higher, which is what makes
        // a row of cards read as a hand rather than as a shelf. The middle rises rather than
        // the ends dropping, because the strip has a bottom and anything pushed past it is
        // drawn off the screen.
        double fromMiddle = count <= 1 ? 0 : index - (count - 1) / 2.0;
        int spread = Math.min(MAX_STEP_DEGREES, count <= 1 ? 0 : SPREAD_DEGREES / count);
        int angle = (int) Math.round(fromMiddle * spread);
        double away = count <= 1 ? 0 : Math.abs(fromMiddle) / ((count - 1) / 2.0);
        int rise = (int) Math.round((1 - away * away) * height * ARC_RISE);

        int bottom = area.bottom() - 2;
        if (index != lifted) {
            return new Slot(new Rect(left + index * step, bottom - height - rise, width, height), angle);
        }

        // The one under the cursor comes out of the fan: bigger, straight, and high enough to
        // read, still hinged on where it was so it does not jump sideways as you sweep along.
        int tallerHeight = (int) Math.round(height * LIFTED_SCALE);
        int tallerWidth = (int) Math.round(width * LIFTED_SCALE);
        int middle = left + index * step + width / 2;
        return new Slot(
                new Rect(middle - tallerWidth / 2, bottom - tallerHeight, tallerWidth, tallerHeight),
                0);
    }

    /**
     * Which card the cursor is on, front-most first, or -1.
     *
     * <p>Tested against the fan as it sits <em>unlifted</em>. Testing against the drawn shapes
     * instead is the obvious thing and it flickers: the card grows the moment it is picked,
     * which changes what the cursor is over, which un-picks it.
     *
     * <p>Front to back, because later cards are drawn over earlier ones and the card you can
     * see is the one you meant.
     */
    public static int at(Rect area, int count, int x, int y) {
        for (int index = count - 1; index >= 0; index--) {
            Slot slot = slot(area, count, index, -1);
            if (slot.where().containsTurned(slot.angle(), x, y)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * How big a card is drawn.
     *
     * <p>As tall as the strip allows, until there are so many that even at the tightest
     * overlap they would not fit across it - at which point the cards themselves give way.
     * Something has to: a floor on the overlap and a fixed card size cannot both hold, and a
     * hand that runs off the side of the screen is worse than one drawn small.
     */
    private static int widthFor(Rect area, int count) {
        // The whole strip, less the arc and a margin. The card the cursor is on grows *above*
        // the strip, over the table, where nothing clips it - so no room has to be kept back
        // for it here, and a resting card can be as big as the space allows.
        int fromHeight = (int) Math.round(
                CardShape.widthFor(area.height() * (1 - ARC_RISE) - 4));
        if (count <= 1) {
            return Math.max(6, fromHeight);
        }
        int fromRoom = (int) (room(area) / (1 + TIGHTEST_STEP * (count - 1)));
        return Math.max(6, Math.min(fromHeight, fromRoom));
    }

    /** How wide the fan itself may be, which is not how wide the strip is. */
    private static int room(Rect area) {
        return Math.max(1, (int) (area.width() * FAN_SHARE_OF_WIDTH));
    }

    /**
     * How far apart two cards sit: side by side while they fit, overlapping once they do not.
     *
     * <p>No floor here, deliberately. The obvious way to keep a card from becoming a sliver is
     * to clamp this, and it is dead code: {@link #widthFor} has already shrunk the card far
     * enough that spreading the hand evenly leaves at least {@link #TIGHTEST_STEP} of each one
     * showing. A clamp on top of that never fires, and a guard that cannot fire is worse than
     * no guard - it reads as the thing keeping the fan honest while the real work happens
     * somewhere else.
     */
    private static int stepFor(Rect area, int count, int width) {
        if (count <= 1) {
            return 0;
        }
        int room = Math.max(width, room(area));
        return Math.max(1, Math.min(width + 4, (room - width) / (count - 1)));
    }
}
