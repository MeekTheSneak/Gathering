package dev.gathering.core.card;

/**
 * What color a deck box is.
 * <p>A shelf of decks is a row of identical objects, and a name printed on an item you have to
 * hover to read is not identification - you find your deck by picking things up until one of
 * them is it. A color is read across the room, which is what the boxes on a real shelf are
 * doing.
 * <p>Chosen from a wheel of hues rather than from anywhere in RGB. Random red, green and blue
 * is mostly mud: two thirds of that cube is brown or near-black, and a box the color of the
 * table is worse than a white one. Every color here is the same saturation and the same
 * brightness, so they differ only in hue - which is the one axis a person can tell apart at a
 * glance and across a room.
 * <p>The hue is quantized rather than continuous for the same reason. Two decks a few degrees
 * apart are two decks the same color; {@link #HUES} of them are all clearly different.
 * <p>Pure.
 */
public final class DeckColors {

    /**
     * How many colors a deck box can be.
     * <p>Twenty-four is fifteen degrees apart, which is about as close as two colors can be
     * and still be named differently by somebody not looking for the difference.
     */
    public static final int HUES = 24;

    /** Strong enough to read as a color, short of the fluorescence of a fully saturated one. */
    private static final float SATURATION = 0.62f;

    /** Bright, because this multiplies a white box and anything darker reads as grubby. */
    private static final float VALUE = 0.90f;

    private DeckColors() {
    }

    /**
     * A color for this seed, as opaque ARGB.
     * <p>The same seed is always the same color, which is what makes a deck keep its box
     * across a save, a table and a trade.
     *
     * @param seed anything about the deck: a roll for a new one, or a name's hash for a deck
     *             that should look the same to everybody who is handed one
     */
    public static int pick(long seed) {
        // Mixed before it is used. The seeds handed to this are often a name's hashCode, and
        // two decks called "Deck 1" and "Deck 2" hash to consecutive numbers - which without
        // this would be the two hues next to each other on the wheel.
        long mixed = seed * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 29;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 32;
        return ofHue(Math.floorMod(mixed, HUES) / (float) HUES);
    }

    /** The color at one point of the wheel, from zero to one. */
    private static int ofHue(float hue) {
        float scaled = (hue - (float) Math.floor(hue)) * 6f;
        int sector = (int) scaled;
        float along = scaled - sector;
        float dim = VALUE * (1f - SATURATION);
        float falling = VALUE * (1f - SATURATION * along);
        float rising = VALUE * (1f - SATURATION * (1f - along));
        return switch (sector % 6) {
            case 0 -> rgb(VALUE, rising, dim);
            case 1 -> rgb(falling, VALUE, dim);
            case 2 -> rgb(dim, VALUE, rising);
            case 3 -> rgb(dim, falling, VALUE);
            case 4 -> rgb(rising, dim, VALUE);
            default -> rgb(VALUE, dim, falling);
        };
    }

    private static int rgb(float red, float green, float blue) {
        return 0xFF000000 | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }
}
