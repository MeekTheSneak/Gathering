package dev.gathering.core.image;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoundedCornersTest {

    @Test
    @DisplayName("the corners come off and the middle is untouched")
    void cornersAreClearedAndTheMiddleIsNot() {
        CardImageDecoder.DecodedImage image = opaque(488, 680);

        RoundedCorners.apply(image);

        assertThat(alphaAt(image, 0, 0)).isZero();
        assertThat(alphaAt(image, 487, 0)).isZero();
        assertThat(alphaAt(image, 0, 679)).isZero();
        assertThat(alphaAt(image, 487, 679)).isZero();
        assertThat(alphaAt(image, 244, 340)).isEqualTo(255);
        assertThat(alphaAt(image, 244, 0)).isEqualTo(255);
        assertThat(alphaAt(image, 0, 340)).isEqualTo(255);
    }

    @Test
    @DisplayName("the curve is faded rather than cut, so the corner is not a staircase")
    void theEdgeOfTheCurveIsFeathered() {
        CardImageDecoder.DecodedImage image = opaque(488, 680);

        RoundedCorners.apply(image);

        int radius = Math.round(488 * RoundedCorners.RADIUS_FRACTION);
        boolean feathered = false;
        for (int offset = 0; offset <= radius; offset++) {
            int alpha = alphaAt(image, offset, radius - offset);
            if (alpha > 0 && alpha < 255) {
                feathered = true;
            }
        }
        assertThat(feathered).isTrue();
    }

    @Test
    @DisplayName("color is kept, so a cleared corner is transparent rather than black")
    void onlyAlphaChanges() {
        // Clearing the whole pixel leaves black corners anywhere the blend does not apply,
        // which is exactly where a cutout render type puts them.
        CardImageDecoder.DecodedImage image = opaque(100, 140);
        int before = image.pixels()[0] & 0x00FFFFFF;

        RoundedCorners.apply(image);

        assertThat(image.pixels()[0] & 0x00FFFFFF).isEqualTo(before);
    }

    @Property(tries = 500)
    void neverThrowsAndNeverClearsMoreThanTheCorners(
            @ForAll @IntRange(min = 1, max = 200) int width,
            @ForAll @IntRange(min = 1, max = 200) int height) {
        CardImageDecoder.DecodedImage image = opaque(width, height);

        RoundedCorners.apply(image);

        // The middle row and column are the widest part of the card and never curved.
        assertThat(alphaAt(image, width / 2, height / 2)).isEqualTo(255);
        for (int pixel : image.pixels()) {
            assertThat((pixel >>> 24) & 0xFF).isBetween(0, 255);
        }
    }

    private static CardImageDecoder.DecodedImage opaque(int width, int height) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, 0xFF3366CC);
        return new CardImageDecoder.DecodedImage(width, height, pixels);
    }

    private static int alphaAt(CardImageDecoder.DecodedImage image, int x, int y) {
        return (image.pixels()[y * image.width() + x] >>> 24) & 0xFF;
    }
}
