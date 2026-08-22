package dev.gathering.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardImageDecoderTest {

    @Test
    @DisplayName("a real Scryfall card image decodes, progressive JPEG and all")
    void decodesProgressiveJpeg() throws IOException {
        // The whole reason this class exists. Minecraft's own decoder is stb_image, which
        // cannot read progressive JPEG, and every Scryfall image tier except png is exactly
        // that - so before this, no card art in the mod ever loaded.
        CardImageDecoder.DecodedImage image = CardImageDecoder.decode(fixture("progressive_card.jpg"));

        assertThat(image.width()).isEqualTo(146);
        assertThat(image.height()).isEqualTo(204);
        assertThat(image.pixels()).hasSize(146 * 204);
    }

    @Test
    @DisplayName("the decoded card is a card, not a blank rectangle")
    void theImageHasRealContent() throws IOException {
        CardImageDecoder.DecodedImage image = CardImageDecoder.decode(fixture("progressive_card.jpg"));

        long distinctColours = java.util.Arrays.stream(image.pixels()).distinct().count();
        assertThat(distinctColours).as("a real card has many colours").isGreaterThan(500);
        assertThat(java.util.Arrays.stream(image.pixels()).allMatch(pixel -> (pixel >>> 24) == 0xFF))
                .as("a JPEG has no transparency").isTrue();
    }

    @Test
    @DisplayName("red and blue trade places, because Minecraft wants ABGR and Java gives ARGB")
    void channelOrderIsSwapped() {
        // Getting this backwards produces art that looks plausible and wrong: blue lands
        // render orange, and nobody notices until somebody squints at a Island.
        assertThat(CardImageDecoder.toAbgr(0xFF_FF0000)).isEqualTo(0xFF_0000FF);   // red   -> red in low byte
        assertThat(CardImageDecoder.toAbgr(0xFF_0000FF)).isEqualTo(0xFF_FF0000);   // blue  -> blue in high byte
        assertThat(CardImageDecoder.toAbgr(0xFF_00FF00)).isEqualTo(0xFF_00FF00);   // green stays put
        assertThat(CardImageDecoder.toAbgr(0x80_123456)).isEqualTo(0x80_563412);   // alpha survives
    }

    @Test
    void pixelsAreAddressableByCoordinate() throws IOException {
        CardImageDecoder.DecodedImage image = CardImageDecoder.decode(fixture("progressive_card.jpg"));

        assertThat(image.pixelAt(0, 0)).isEqualTo(image.pixels()[0]);
        assertThat(image.pixelAt(145, 203)).isEqualTo(image.pixels()[image.pixels().length - 1]);
    }

    @Test
    @DisplayName("rubbish in is an exception, not a half-drawn texture")
    void badInputIsRejected() {
        assertThatThrownBy(() -> CardImageDecoder.decode(null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CardImageDecoder.decode(new byte[0])).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CardImageDecoder.decode("<html>not an image</html>".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unrecognised image format");
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = CardImageDecoderTest.class.getResourceAsStream("/images/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + name);
            }
            return in.readAllBytes();
        }
    }
}
