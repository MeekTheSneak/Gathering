package dev.gathering.core.ui;

/**
 * Whether what is written straight onto a table's felt should be dark or light.
 * <p>The felt was a dark cloth, and everything the board on a table writes on it without a backing
 * of its own - a zone's name, a button's word, the line round a group - was drawn light to read on
 * it. The owner asked for the top of the table to be white wool, which dyes to any of sixteen colors
 * and is undyed white, and light writing on white wool is no writing at all. So the writing follows
 * the felt: dark on a light cloth, light on a dark one.
 * <p>Pure, so the line between the two is tested rather than eyeballed.
 */
public final class FeltContrast {

    /** How light a felt has to be, on a scale to 255, for dark writing to read better than light. */
    public static final int LIGHT_FROM = 140;

    private FeltContrast() {
    }

    /** Whether a felt of this color, as 0xRRGGBB, is light enough to write on in a dark color. */
    public static boolean isLight(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        // How bright the eye finds it, which is mostly green and hardly blue.
        return red * 299 + green * 587 + blue * 114 >= LIGHT_FROM * 1000;
    }
}
