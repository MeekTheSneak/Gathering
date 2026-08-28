package dev.gathering.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The shine on a foil.
 *
 * <p>A foil is the one thing about a card that a picture of it cannot show. Scryfall's art is
 * the non-foil scan - there is no foil image to fetch, and there never will be, because a foil
 * is not a different picture but the same picture doing something as it moves. So it is drawn
 * rather than fetched: bands of color raked across the card at an angle, sliding as the card
 * turns, with a broad highlight riding over the top of them.
 *
 * <p>Which is also why the inspect screen tilts the card with the cursor. A holographic sheen
 * that never moves is a sticker; the movement is the whole effect, and a still picture of this
 * looks like nothing much on purpose.
 *
 * <p>Clipped to the card with a scissor rather than drawn inside its bounds, because the bands
 * are drawn at an angle and an angled band that stopped where the card stopped would have to
 * be trimmed by hand at both ends. The scissor is in screen space and the bands are drawn
 * under a rotated pose, which is exactly the division of labor that makes this eleven lines
 * instead of a geometry problem.
 *
 * <p>Client-only.
 */
public final class FoilSheen {

    /** How many bands rake across. Enough to read as a spectrum, few enough to stay a sheen. */
    private static final int BANDS = 9;

    /** How far over the card is tipped, which is what makes the bands diagonal. */
    private static final float RAKE = -24f;

    /** How much of the card one band covers. */
    private static final float BAND_SHARE = 0.16f;

    /** How strongly the color shows. A foil is a hint of a rainbow, not a rainbow. */
    private static final int BAND_ALPHA = 0x30;

    /** The broad white catch that rides over the bands, and how wide it is. */
    private static final int GLARE_ALPHA = 0x38;
    private static final float GLARE_SHARE = 0.42f;

    private FoilSheen() {
    }

    /**
     * Rakes the shine across a card.
     *
     * <p>{@code slide} is where the light is: minus one to one, from one edge of the card to
     * the other. The inspect screen feeds it the tilt, so turning the card moves the shine -
     * see {@link CardTilt}.
     */
    public static void draw(GuiGraphics graphics, int x, int y, int width, int height, float slide) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float travel = Mth.clamp(slide, -1f, 1f);
        graphics.enableScissor(x, y, x + width, y + height);
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2f, y + height / 2f, 0f);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(RAKE));

        // Wide enough that a rotated band still crosses the whole card, and long enough that
        // its ends are outside the scissor at any angle. Both are the same number for the
        // same reason: the diagonal of the card is longer than either of its sides.
        int reach = Math.round(Mth.sqrt(width * (float) width + height * (float) height));
        int band = Math.max(2, Math.round(width * BAND_SHARE));
        int step = Math.max(band + 1, reach * 2 / BANDS);
        int drift = Math.round(travel * width);

        for (int index = 0; index < BANDS; index++) {
            int left = -reach + index * step + drift;
            float hue = (index / (float) BANDS + travel * 0.25f) % 1f;
            int color = (BAND_ALPHA << 24) | (Mth.hsvToRgb(hue, 0.62f, 1f) & 0xFFFFFF);
            graphics.fill(left, -reach, left + band, reach, color);
        }

        // The catch: one broad pale band that moves further than the colors do, which is what
        // makes the whole thing read as light on a surface rather than as a pattern printed
        // on it. Twice the travel, so it overtakes them.
        int glare = Math.max(4, Math.round(width * GLARE_SHARE));
        int over = Math.round(travel * width * 2f);
        graphics.fill(over - glare / 2, -reach, over + glare / 2, reach,
                (GLARE_ALPHA << 24) | 0xFFFFFF);

        graphics.pose().popPose();
        graphics.disableScissor();
    }
}
