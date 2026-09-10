package dev.gathering.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A round glow behind a round thing.
 * <p>Drawn rather than painted, because there is no glow texture and there is not going to be
 * one - the artwork is somebody else's to make, and a halo is geometry rather than art.
 * <p>Two things make it a glow rather than a disc, and the first attempt had neither. It has to
 * be centered on the thing's <em>ink</em>, which for a mana symbol is not where the draw call
 * was told to put it - see {@link ManaText#drawYForMiddle}. And it has to fade out to nothing:
 * a stack of discs at one alpha leaves a visible rim wherever the largest of them ends, which
 * reads as a coin sitting behind the orb.
 * <p>And the curve has to live in the band <em>outside</em> the thing being lit, not from its
 * middle out: the orb is opaque, so a glow brightest at the middle spends all of it underneath
 * and shows only its faintest tail.
 * <p>So the alpha follows a squared curve from the orb's edge to the rim. Each disc is given
 * the share that lifts the total to where the curve says it should be at that radius, and the
 * curve reaches zero exactly at the rim. A couple of hundred fills, on a screen that is not
 * the board.
 * <p>Client-only.
 */
public final class GuiGlow {

    /**
     * How many discs make the falloff.
     * <p>Ten is enough that the steps are not countable at any GUI scale and few enough that
     * the whole thing is a hundred-odd fills. Two looks like a target; fifty is a gradient
     * nobody asked to pay for.
     */
    private static final int STEPS = 10;

    private GuiGlow() {
    }

    /**
     * Draws a glow around something round.
     * <p>The band between the two radii is where the glow is: solid at {@code inner} and gone
     * at {@code outer}. That is the point of taking two - the thing being lit is opaque, so a
     * glow that is brightest at the middle spends all of its brightness underneath the orb
     * and shows only its faintest tail. Measured off the first attempt at this: eighteen of
     * two hundred and fifty-five, in the only four pixels anybody could see.
     *
     * @param inner as bright as the color asks for, and everything inside it the same
     * @param outer nothing at all, so there is no rim to read as an edge
     * @param color the glow at its brightest, alpha included
     */
    public static void render(
            GuiGraphics graphics, int centerX, int centerY, int inner, int outer, int color) {
        int band = outer - inner;
        if (outer <= 0 || band <= 0) {
            return;
        }
        float brightest = ((color >>> 24) & 0xFF) / 255f;
        if (brightest <= 0f) {
            return;
        }
        int rgb = color & 0x00FFFFFF;

        // Largest disc first, so each one adds to what is already under it. "Covered" is how
        // much alpha has piled up so far; "wanted" is how much the curve says there should be
        // by this radius. The share between them is what this disc has to carry - which is not
        // the difference, because alpha does not add, it blends: laying a over c leaves
        // c + a(1 - c).
        float covered = 0f;
        for (int step = 1; step <= STEPS; step++) {
            // Squared, so it comes off the orb strongly and thins out towards nothing. Linear
            // falloff still shows an edge, because the eye finds where the slope changes.
            float across = step / (float) STEPS;
            float wanted = brightest * across * across;
            int share = Math.round((wanted - covered) / (1f - covered) * 255f);
            covered = wanted;
            if (share <= 0) {
                continue;
            }
            disc(graphics, centerX, centerY, outer - band * step / STEPS, (share << 24) | rgb);
        }
    }

    /**
     * One filled circle, a row at a time.
     * <p>A row rather than a pixel: a circle's half-width at each row is one square root, and
     * a row is one fill. Per pixel it would be a thousand calls for a thing the size of a
     * thumbnail.
     */
    private static void disc(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        for (int down = -radius; down <= radius; down++) {
            int across = (int) Math.round(Math.sqrt((double) radius * radius - (double) down * down));
            if (across <= 0) {
                continue;
            }
            graphics.fill(centerX - across, centerY + down, centerX + across, centerY + down + 1, color);
        }
    }
}
