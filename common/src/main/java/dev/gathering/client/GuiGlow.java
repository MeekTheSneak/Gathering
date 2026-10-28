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

    /** Runs these fills as one flush rather than one each. See {@link GuiGraphics#drawManaged}. */
    @SuppressWarnings("deprecation")
    private static void batched(GuiGraphics graphics, Runnable fills) {
        graphics.drawManaged(fills);
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
        // In one batch. Outside a batch every fill a screen makes is flushed as its own draw call
        // with two depth-state changes, and a glow is hundreds of fills - a card glow at a 1080p
        // window was over a thousand, twice over on the pack screen's reveal, on exactly the
        // frames the ceremony is built around.
        batched(graphics, () -> renderUnbatched(graphics, centerX, centerY, inner, outer, color));
    }

    private static void renderUnbatched(
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
     * A glow around the outside of a rectangle, solid at its edge and gone a little way out.
     * <p>For a pile cards have just landed in: lit in its seat's color, which is a meaning rather
     * than a look, so it is drawn rather than taken from a theme's sprite - tinting a themed ring
     * multiplies the theme's own color into the seat's and comes out a brown nobody chose.
     * <p>Rings rather than discs, each a hair further out and fainter, on the same squared curve
     * as the round glow and for the same reason: a linear falloff shows where it ends.
     *
     * @param spread how many pixels out the glow reaches
     * @param color  the glow at its brightest, alpha included
     */
    public static void around(GuiGraphics graphics, int x, int y, int width, int height, int spread, int color) {
        // In one batch. Outside a batch every fill a screen makes is flushed as its own draw call
        // with two depth-state changes, and a glow is hundreds of fills - a card glow at a 1080p
        // window was over a thousand, twice over on the pack screen's reveal, on exactly the
        // frames the ceremony is built around.
        batched(graphics, () -> aroundUnbatched(graphics, x, y, width, height, spread, color));
    }

    private static void aroundUnbatched(GuiGraphics graphics, int x, int y, int width, int height, int spread, int color) {
        float brightest = ((color >>> 24) & 0xFF) / 255f;
        if (spread <= 0 || brightest <= 0f || width <= 0 || height <= 0) {
            return;
        }
        int rgb = color & 0x00FFFFFF;
        for (int out = 1; out <= spread; out++) {
            float left = 1f - (out - 1) / (float) spread;
            int alpha = Math.round(brightest * left * left * 255f);
            if (alpha <= 0) {
                continue;
            }
            int ring = (alpha << 24) | rgb;
            int left0 = x - out;
            int top = y - out;
            int right = x + width + out;
            int bottom = y + height + out;
            graphics.fill(left0, top, right, top + 1, ring);
            graphics.fill(left0, bottom - 1, right, bottom, ring);
            graphics.fill(left0, top + 1, left0 + 1, bottom - 1, ring);
            graphics.fill(right - 1, top + 1, right, bottom - 1, ring);
        }
    }

    /**
     * A glow that follows a card's cut corners rather than squaring them off.
     * <p>A card is a rectangle with its corners cut, and a square glow puts a hard corner exactly where
     * the card has none - so the card appears to have sharp corners, which is what the owner saw.
     * <p>Ten rounded rectangles, each one step smaller and drawn over the last, outermost first. Ten for
     * the same reason the round glow uses ten: the steps are not countable and the whole thing is a few
     * hundred fills. The first attempt drew one single-pixel outline per pixel of spread with a staircase
     * at each corner, every ring cut to the same size however far out it was - so successive rings did
     * not nest and the corners came out as hatching. That is what "clearly broken" looked like.
     *
     * @param spread how many pixels out the glow reaches
     * @param color  the glow at its brightest, alpha included
     */
    public static void aroundCard(
            GuiGraphics graphics, int x, int y, int width, int height, int spread, int color) {
        // In one batch. Outside a batch every fill a screen makes is flushed as its own draw call
        // with two depth-state changes, and a glow is hundreds of fills - a card glow at a 1080p
        // window was over a thousand, twice over on the pack screen's reveal, on exactly the
        // frames the ceremony is built around.
        batched(graphics, () -> aroundCardUnbatched(graphics, x, y, width, height, spread, color));
    }

    private static void aroundCardUnbatched(
            GuiGraphics graphics, int x, int y, int width, int height, int spread, int color) {
        float brightest = ((color >>> 24) & 0xFF) / 255f;
        if (spread <= 0 || brightest <= 0f || width <= 0 || height <= 0) {
            return;
        }
        int rgb = color & 0x00FFFFFF;
        int corner = Math.max(1, Math.round(width * dev.gathering.core.ui.CardMesh.cornerAcross()));
        // Each layer adds the same little, so ten of them build the curve by piling up rather than by
        // anybody working out what each one should be worth.
        int each = Math.max(1, Math.round(brightest * 255f / STEPS));
        for (int step = STEPS; step >= 1; step--) {
            int out = Math.round(spread * step / (float) STEPS);
            roundedRect(graphics, x - out, y - out, x + width + out, y + height + out,
                    corner + out, (each << 24) | rgb);
        }
    }

    /**
     * One filled rectangle with its corners rounded off, as a handful of spans.
     * <p>A row at a time down each corner and one tall band through the middle, which is the whole shape
     * in about forty fills however big it is.
     */
    private static void roundedRect(GuiGraphics graphics, int left, int top, int right, int bottom,
            int radius, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        int across = Math.max(0, Math.min(radius, (right - left) / 2));
        int down = Math.max(0, Math.min(radius, (bottom - top) / 2));
        graphics.fill(left, top + down, right, bottom - down, color);
        for (int row = 0; row < down; row++) {
            float up = (down - row) / (float) Math.max(1, down);
            int inset = across - (int) Math.round(across * Math.sqrt(Math.max(0f, 1f - up * up)));
            graphics.fill(left + inset, top + row, right - inset, top + row + 1, color);
            graphics.fill(left + inset, bottom - row - 1, right - inset, bottom - row, color);
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
