package dev.gathering.core.card;

/**
 * What the card images cost in video memory, worked out rather than assumed.
 * <p>The design brief carried a budget - "well under 200 MB worst case" - and the cap that was
 * meant to hold it, and the two had never been multiplied together. They do not agree: the
 * shipped cap allows 324 MiB. The arithmetic is here so that the claim and the number are one
 * thing, and so the test below fails the day somebody raises the cap past what is written down.
 * <p>A card texture is uploaded as {@code NativeImage.Format.RGBA} through a
 * {@code DynamicTexture}, which is four bytes a pixel and no mipmaps - so the cost is exactly
 * the pixels, and nothing here is an estimate.
 * <p>Pure.
 */
public final class TextureBudget {

    /** Four bytes a pixel: RGBA, uploaded without mipmaps. */
    private static final int BYTES_PER_PIXEL = 4;

    private TextureBudget() {
    }

    /** The three tiers the mod fetches, as Scryfall serves them. */
    public enum Tier {

        /** What the table miniatures are drawn from. */
        SMALL(146, 204),

        /** What every card on a board and in every list is drawn from. */
        NORMAL(488, 680),

        /**
         * What a card being read is drawn from, and nothing else.
         * <p>Two and a half times a normal one. Reported from a real session: "GUI mode when
         * you zoom in gets extremely laggy, like almost crashes" - which is this tier being
         * chosen by how large a card was being drawn and by nothing else, so a board zoomed
         * in past the threshold asked for one of these per permanent. See
         * {@link #CRISP_AT_ONCE} for how many may be resident at once and why.
         */
        CRISP(745, 1040);

        private final int width;
        private final int height;

        Tier(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /** What one texture of this tier costs, in bytes. */
        public long bytes() {
            return (long) width * height * BYTES_PER_PIXEL;
        }
    }

    /** What this many textures of one tier cost, in bytes. */
    public static long bytesFor(Tier tier, int howMany) {
        return tier == null || howMany <= 0 ? 0L : tier.bytes() * howMany;
    }

    /** The same, in whole mebibytes, which is the unit the budget is written in. */
    public static long mebibytesFor(Tier tier, int howMany) {
        return bytesFor(tier, howMany) / (1024 * 1024);
    }

    /**
     * The most video memory the resident set may cost before it is worth arguing about.
     * <p>What the code does today rather than what the brief wished for. Written down so that
     * raising the cap fails a test rather than quietly costing a player another hundred
     * megabytes, and so the two numbers cannot drift apart again.
     */
    public static final long CEILING_MEBIBYTES = 325;

    /**
     * How many crisp textures can honestly be on screen at once.
     * <p>Two: the card being read, and the one it is a transform of. That is the whole of what
     * asks for the tier, and it is why the ceiling above is worked out in normal ones - a
     * board's worth of crisp textures is not a case the budget covers, it is a fault. A
     * hundred of them is seven hundred and ninety megabytes, which is not a slow frame, it is
     * a client that stops.
     */
    public static final int CRISP_AT_ONCE = 2;
}
