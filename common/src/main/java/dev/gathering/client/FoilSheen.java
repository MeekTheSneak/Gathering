package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The shine on a foil.
 *
 * <p>A foil is the one thing about a card that a picture of it cannot show. Scryfall's scan is
 * the non-foil one and always will be, because a foil is not a different picture but the same
 * picture doing something as it moves. So it is drawn rather than fetched - and that is also
 * why the card turns with the mouse. A holographic sheen that never moves is a sticker; the
 * movement is the whole effect, and a still picture of this is meant to look like not very
 * much.
 *
 * <p><b>What it is made of.</b> Three things:
 *
 * <ul>
 *   <li>Two <b>spectra</b> crossing at different angles, one faint and travelling against the
 *       other. A single sweep, however smooth, reads as a gradient somebody laid over a
 *       picture; what a foil actually shows is two sets of colors interfering.</li>
 *   <li>A <b>catch</b>: one broad soft rise in brightness that slides further than the colors
 *       do, so it overtakes them. That is what reads as light moving over a surface rather
 *       than as a pattern printed on one.</li>
 *   <li><b>Grain</b>: a scatter of tiny bright points that light up only as the catch passes
 *       over them. This is the part that makes people turn the card back and forth. It is
 *       seeded from the printing, so two foils sparkle differently and each one sparkles the
 *       same way every time it is picked up.</li>
 * </ul>
 *
 * <p><b>It cannot leave the card, and it never lands on a sleeve.</b> Two separate guarantees,
 * and neither of them is a clip.
 *
 * <p>The first is that the shine is painted onto the card's own mesh - the same grid of points
 * {@link TiltedFace} draws the picture from, projected through the same {@link CardLens}. Every
 * vertex of it is a point on the card, so there is no angle, no turn and no lens at which any
 * of it can be anywhere else. Nothing is drawn off the card and then trimmed back; nothing is
 * drawn off the card at all.
 *
 * <p>The second is structural: the only caller is {@code CardInspectPanel.drawFace}, on the one
 * branch that has just been handed a printed face's texture. A sleeve is a different texture
 * drawn by different code with no path here, and a card whose art has not arrived gets a
 * placeholder and no shine, because a sheen over a "fetching" box is a shine on a box. Both
 * printed sides of a double-faced card come through that branch, so turning one over shows a
 * foil on its back too - a face rather than a sleeve, and it was foil when it was printed.
 *
 * <p>Client-only.
 */
public final class FoilSheen {

    /** The angle the first spectrum is raked across the card at, in degrees from vertical. */
    private static final float RAKE = -22f;

    /** And the second, crossing it. */
    private static final float CROSS_RAKE = 15f;

    /** How much of the first one's strength the crossing one is drawn at. */
    private static final float CROSS_SHARE = 0.45f;

    /** How many times round the color wheel a spectrum goes across the card. */
    private static final float CYCLES = 1.35f;

    /** How colored it is. Well short of full: a foil is a hint of a rainbow, not a rainbow. */
    private static final float SATURATION = 0.5f;

    /**
     * How strongly a spectrum shows away from the catch.
     *
     * <p>This is drawn over somebody's card art, which is the thing they are trying to look
     * at. An early version fogged the picture badly enough that it stopped reading, which is
     * the whole failure mode of an effect like this: it is meant to catch the eye, not hold it.
     */
    private static final float SPECTRUM_ALPHA = 0.075f;

    /** How much brighter the catch is at its middle, and how broad it is. */
    private static final float CATCH_ALPHA = 0.20f;
    private static final float CATCH_WIDTH = 0.26f;

    /**
     * How far the catch travels, and how far the colors under it do.
     *
     * <p>The catch further, so it overtakes them - and not so far that a full turn puts it off
     * the card altogether. At an earlier number it did exactly that, and a card turned all the
     * way looked like an ordinary card: a foil you can tip until it stops being a foil is
     * worse than one that never moved.
     */
    private static final float CATCH_TRAVEL = 0.30f;
    private static final float SPECTRUM_TRAVEL = 0.18f;

    /** How many points of grain, how bright they get, and how tightly they light up. */
    private static final int GRAINS = 34;
    private static final float GRAIN_ALPHA = 0.78f;
    private static final float GRAIN_WIDTH = 0.10f;

    /** How big one point of grain is, as a share of the card's width. */
    private static final float GRAIN_SIZE = 0.011f;

    private static final float TAU = (float) (Math.PI * 2.0);

    private FoilSheen() {
    }

    /**
     * Paints the shine onto a card's mesh.
     *
     * <p>{@code slide} is where the light is, from minus one to one across the card. The
     * inspect screen feeds it the turn, so tipping the card moves the shine - see
     * {@link CardTilt}.
     */
    static void paint(
            Matrix4f matrix, CardLens lens, float slide, long grain, int columns, int rows) {
        float travel = Mth.clamp(slide, -1f, 1f);
        float middle = 0.5f + travel * CATCH_TRAVEL;
        float crossMiddle = 0.5f - travel * CATCH_TRAVEL;
        float cosRake = (float) Math.cos(Math.toRadians(RAKE));
        float sinRake = (float) Math.sin(Math.toRadians(RAKE));
        float cosCross = (float) Math.cos(Math.toRadians(CROSS_RAKE));
        float sinCross = (float) Math.sin(Math.toRadians(CROSS_RAKE));

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer =
                Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] corner = new float[2];
        for (int column = 0; column < columns; column++) {
            float leftU = column / (float) columns;
            float rightU = (column + 1) / (float) columns;
            for (int row = 0; row < rows; row++) {
                float topV = row / (float) rows;
                float bottomV = (row + 1) / (float) rows;
                lit(buffer, matrix, lens, corner, leftU, topV, travel, middle, crossMiddle,
                        cosRake, sinRake, cosCross, sinCross);
                lit(buffer, matrix, lens, corner, leftU, bottomV, travel, middle, crossMiddle,
                        cosRake, sinRake, cosCross, sinCross);
                lit(buffer, matrix, lens, corner, rightU, bottomV, travel, middle, crossMiddle,
                        cosRake, sinRake, cosCross, sinCross);
                lit(buffer, matrix, lens, corner, rightU, topV, travel, middle, crossMiddle,
                        cosRake, sinRake, cosCross, sinCross);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        grain(matrix, lens, middle, cosRake, sinRake, grain);
        RenderSystem.disableBlend();
    }

    /** One point of the mesh, colored by both spectra laid over each other. */
    private static void lit(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner,
            float u, float v, float travel, float middle, float crossMiddle,
            float cosRake, float sinRake, float cosCross, float sinCross) {
        float along = lens.alongRake(u, v, cosRake, sinRake);
        float across = lens.alongRake(u, v, cosCross, sinCross);

        float[] first = spectrum(along, travel, middle, 1f);
        float[] second = spectrum(across, -travel, crossMiddle, CROSS_SHARE);
        // The faint one under the strong one, composited the ordinary way rather than added,
        // so two spectra crossing stay a sheen instead of stacking up into a fog.
        float alpha = first[3] + second[3] * (1f - first[3]);
        int color = alpha <= 0.001f
                ? 0
                : pack(alpha,
                        blend(first, second, 0, alpha),
                        blend(first, second, 1, alpha),
                        blend(first, second, 2, alpha));
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0], corner[1], 0f).setColor(color);
    }

    private static float blend(float[] over, float[] under, int channel, float alpha) {
        return (over[channel] * over[3] + under[channel] * under[3] * (1f - over[3])) / alpha;
    }

    /**
     * The color one spectrum shows at a point: an iridescence under a soft rise in brightness.
     *
     * <p>Three offset sine waves rather than a trip round the color wheel, because a hue that
     * wraps has a seam in it - and a seam between two points of a mesh is a cell drawn with
     * the whole spectrum running backwards through it. This has no seam anywhere.
     *
     * @return red, green, blue and alpha, each zero to one
     */
    private static float[] spectrum(float along, float travel, float middle, float strength) {
        float phase = along * CYCLES + travel * SPECTRUM_TRAVEL;
        float alpha = strength
                * (SPECTRUM_ALPHA + CATCH_ALPHA * bell(along - middle, CATCH_WIDTH));
        return new float[] {
            tinted(phase, 0f), tinted(phase, 1f / 3f), tinted(phase, 2f / 3f),
            Mth.clamp(alpha, 0f, 1f),
        };
    }

    /** One channel of the iridescence, pulled towards white by however unsaturated it is. */
    private static float tinted(float phase, float offset) {
        float wave = 0.5f + 0.5f * Mth.sin(TAU * (phase + offset));
        return 1f - SATURATION + SATURATION * wave;
    }

    /**
     * The points of grain, lit by how near the catch is to each of them.
     *
     * <p>Placed in the card's own space, so they sit still on the picture while the light moves
     * over them - which is what they are for - and projected through the same lens as
     * everything else, so they travel with the card and cannot land off it.
     */
    private static void grain(
            Matrix4f matrix, CardLens lens, float middle, float cosRake, float sinRake, long seed) {
        java.util.Random scatter = new java.util.Random(seed * 0x9E3779B97F4A7C15L + 0x2545F491L);
        BufferBuilder buffer =
                Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] corner = new float[2];
        boolean any = false;
        for (int index = 0; index < GRAINS; index++) {
            float u = scatter.nextFloat();
            float v = scatter.nextFloat();
            float lit = bell(lens.alongRake(u, v, cosRake, sinRake) - middle, GRAIN_WIDTH);
            if (lit < 0.05f) {
                continue;
            }
            int color = pack(GRAIN_ALPHA * lit * lit, 1f, 1f, 1f);
            // Kept inside the card's own edges, so a point that landed on the very corner is
            // a smaller point rather than one hanging off it.
            float halfU = GRAIN_SIZE / 2f;
            float halfV = halfU * lens.aspect();
            float leftU = Math.max(0f, u - halfU);
            float rightU = Math.min(1f, u + halfU);
            float topV = Math.max(0f, v - halfV);
            float bottomV = Math.min(1f, v + halfV);
            speck(buffer, matrix, lens, corner, leftU, topV, color);
            speck(buffer, matrix, lens, corner, leftU, bottomV, color);
            speck(buffer, matrix, lens, corner, rightU, bottomV, color);
            speck(buffer, matrix, lens, corner, rightU, topV, color);
            any = true;
        }
        if (any) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            buffer.build();
        }
    }

    private static void speck(
            BufferBuilder buffer, Matrix4f matrix, CardLens lens, float[] corner,
            float u, float v, int color) {
        lens.at(u, v, corner);
        buffer.addVertex(matrix, corner[0], corner[1], 0f).setColor(color);
    }

    private static int pack(float alpha, float red, float green, float blue) {
        return (channel(alpha) << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    private static int channel(float value) {
        return Mth.clamp(Math.round(value * 255f), 0, 255);
    }

    /** A soft hump: one at the middle, falling away to nothing either side. */
    private static float bell(float from, float width) {
        float scaled = from / width;
        return (float) Math.exp(-scaled * scaled);
    }
}
