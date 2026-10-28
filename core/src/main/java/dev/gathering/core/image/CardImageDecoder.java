package dev.gathering.core.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Turns downloaded card art into raw pixels.
 * <p>This exists because of a specific incompatibility that is invisible until you try it.
 * Minecraft decodes images with stb_image, which handles <b>baseline</b> JPEG and not
 * <b>progressive</b> JPEG - and Scryfall serves progressive JPEG for every image tier except
 * {@code png}. So {@code NativeImage.read} fails on essentially every card in the game, and
 * the only symptom is art that never appears.
 * <p>Java's own ImageIO reads progressive JPEG perfectly well, so the decode happens here,
 * in the pure core, where it can be tested against a real Scryfall image instead of hoped
 * about. The client's only job is to copy the result into a texture.
 * <p>Output is <b>ABGR</b>, packed as {@code 0xAABBGGRR}, which is the order Minecraft's
 * {@code NativeImage.setPixelRGBA} expects despite its name. Java hands out ARGB, so red and
 * blue are swapped on the way through - getting that backwards produces art that looks
 * plausible and wrong, with blue lands rendering orange.
 */
public final class CardImageDecoder {

    /** No card image is anywhere near this; anything that is, is not a card image. */
    public static final int MAX_DIMENSION = 4096;

    private CardImageDecoder() {
    }

    public static DecodedImage decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("No image data");
        }

        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            throw new IOException("Could not decode image (" + bytes.length + " bytes)", e);
        }
        if (source == null) {
            throw new IOException("Unrecognized image format (" + bytes.length + " bytes)");
        }

        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("Implausible image size " + width + "x" + height);
        }

        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        int[] abgr = new int[argb.length];
        for (int index = 0; index < argb.length; index++) {
            abgr[index] = toAbgr(argb[index]);
        }
        return new DecodedImage(width, height, abgr);
    }

    /** {@code 0xAARRGGBB} to {@code 0xAABBGGRR}: alpha and green stay, red and blue trade places. */
    static int toAbgr(int argb) {
        int alpha = argb & 0xFF00_0000;
        int red = (argb >> 16) & 0xFF;
        int green = argb & 0x0000_FF00;
        int blue = argb & 0x0000_00FF;
        return alpha | (blue << 16) | green | red;
    }

    /** Raw pixels, ready to be poured into a texture. */
    public record DecodedImage(int width, int height, int[] pixels) {

        public DecodedImage {
            if (pixels.length != width * height) {
                throw new IllegalArgumentException(
                        "Expected " + width * height + " pixels for " + width + "x" + height
                                + ", got " + pixels.length);
            }
        }

        public int pixelAt(int x, int y) {
            return pixels[y * width + x];
        }
    }
}
