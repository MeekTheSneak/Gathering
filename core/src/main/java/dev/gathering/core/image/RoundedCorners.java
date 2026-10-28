package dev.gathering.core.image;

/**
 * Rounds the corners of a decoded card image.
 * <p>A real card has rounded corners, and card art from Scryfall does not: it is a rectangle
 * with the corners printed on it, so drawing it produces a sharp-cornered rectangle that
 * reads as a screenshot of a card rather than as a card.
 * <p>Done to the pixels once at decode rather than by drawing something over the corners
 * every frame, because the thing behind a card is different everywhere it appears - a frame
 * on the deck screen, a panel by the cursor, a dimmed world. A corner mask painted in one
 * background color would be right in exactly one of those places.
 * <p>Pixels are <b>ABGR</b> ({@code 0xAABBGGRR}), matching {@link CardImageDecoder}.
 */
public final class RoundedCorners {

    /**
     * The corner radius as a fraction of the card's width.
     * <p>A Magic card is 63mm across with a 3mm corner radius, so this is the real thing
     * rather than a number that looked right.
     */
    public static final float RADIUS_FRACTION = 3f / 63f;

    private RoundedCorners() {
    }

    /**
     * Clears the alpha outside the rounded rectangle, in place.
     * <p>The last pixel of the curve is faded rather than cut, because a hard alpha edge on a
     * diagonal is a staircase and the corner is the one part of a card everybody looks at.
     */
    public static void apply(CardImageDecoder.DecodedImage image) {
        // Width is the reference dimension because that is what the 63mm is across.
        apply(image, image.width() * RADIUS_FRACTION);
    }

    static void apply(CardImageDecoder.DecodedImage image, float radius) {
        int width = image.width();
        int height = image.height();
        if (radius < 1f || width < 2 || height < 2) {
            return;
        }
        int span = (int) Math.ceil(radius);
        int[] pixels = image.pixels();

        for (int y = 0; y < height; y++) {
            boolean nearTop = y < span;
            boolean nearBottom = y >= height - span;
            if (!nearTop && !nearBottom) {
                continue;
            }
            float centerY = nearTop ? radius - 0.5f : height - radius - 0.5f;

            for (int x = 0; x < width; x++) {
                boolean nearLeft = x < span;
                boolean nearRight = x >= width - span;
                if (!nearLeft && !nearRight) {
                    continue;
                }
                float centerX = nearLeft ? radius - 0.5f : width - radius - 0.5f;

                float dx = x - centerX;
                float dy = y - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance <= radius - 1f) {
                    continue;
                }

                int index = y * width + x;
                int alpha = (pixels[index] >>> 24) & 0xFF;
                float coverage = distance >= radius ? 0f : radius - distance;
                pixels[index] = (pixels[index] & 0x00FFFFFF) | (Math.round(alpha * coverage) << 24);
            }
        }
    }
}
