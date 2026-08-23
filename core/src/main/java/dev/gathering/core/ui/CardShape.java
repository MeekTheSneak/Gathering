package dev.gathering.core.ui;

import dev.gathering.core.card.ImageTier;

/**
 * How wide a card is against how tall it is.
 *
 * <p>One number, and until now six copies of it: the hand fan, the deck screen, the table
 * surface, the pile screen, the inspect panel and the zoom overlay each wrote {@code 488f /
 * 680f} out again. None of them was wrong, and that is the point - a constant spelled out in
 * six places is six chances for five of them to be changed and one to be missed, and the one
 * that is missed draws a card that is very slightly the wrong shape on one screen.
 *
 * <p>Derived from the image tier the art actually arrives in, so the shape a card is drawn in
 * and the shape of the picture inside it cannot come apart.
 *
 * <p>Pure.
 */
public final class CardShape {

    /** Width over height: a card is a little taller than it is wide, and always this much. */
    public static final double ASPECT =
            ImageTier.NORMAL.width() / (double) ImageTier.NORMAL.height();

    private CardShape() {
    }

    /** How wide a card this tall is. */
    public static double widthFor(double height) {
        return height * ASPECT;
    }

    /** How tall a card this wide is. */
    public static double heightFor(double width) {
        return width / ASPECT;
    }

    /** The same, rounded, for the layouts that deal in whole pixels. */
    public static int widthFor(int height) {
        return (int) Math.round(widthFor((double) height));
    }

    public static int heightFor(int width) {
        return (int) Math.round(heightFor((double) width));
    }
}
