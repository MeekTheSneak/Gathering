package dev.gathering.client;

import dev.gathering.Gathering;
import dev.gathering.core.text.ManaSymbols;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Card text with its mana and tap symbols drawn as symbols.
 * <p>Braced codes become private-use characters styled with the mod's symbol font, which
 * means the result is an ordinary {@link Component}: the game's own text layout wraps it,
 * measures it and draws it, symbols included, with no separate layout pass to write and
 * nothing that could disagree with how the rest of the text is handled.
 * <p>Client-only.
 */
public final class ManaText {

    /** {@code assets/gathering/font/mana.json}, generated alongside the glyph textures. */
    public static final ResourceLocation FONT =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "mana");

    /**
     * The ascent every glyph in this font declares, from {@code tools/mana_art.py}.
     * <p>Held here because it decides where a symbol's ink lands, and nothing else in the game
     * will tell you: {@link Font} reports a line height, not a glyph box. {@code spritecheck}
     * fails if the generator and this number ever disagree.
     */
    public static final int ASCENT = 8;

    /** The height every glyph in this font declares, from the same generator. */
    public static final int HEIGHT = 9;

    /**
     * How far below a draw origin Minecraft puts the baseline, from {@code SheetGlyphInfo}.
     * <p>{@code getTop()} is {@code 7 - ascent} and {@code getBottom()} is one height further
     * down. A font whose ascent is larger than seven - this one, by one - therefore draws
     * <em>above</em> the point it was asked to draw at.
     */
    private static final float BASELINE = 7f;

    private ManaText() {
    }

    /**
     * Where to draw a symbol so its ink is centered on {@code middleY}.
     * <p>The reason this exists: a symbol drawn at a line's top is not a symbol whose middle
     * is anywhere in particular. Placing a mana orb by its line box and then putting anything
     * else - a glow, a ring, a label - at the same point leaves the two six pixels apart, and
     * the gap grows with the scale, so it looks like a misplaced copy rather than an offset.
     * <p>Given the middle, the draw point falls out, and both things then agree by
     * construction rather than by a number somebody measured off a screenshot.
     */
    public static int drawYForMiddle(Font font, int middleY, float scale) {
        float belowDrawY = (font.lineHeight - font.lineHeight * scale) / 2f
                + (BASELINE - ASCENT + HEIGHT / 2f) * scale;
        return Math.round(middleY - belowDrawY);
    }

    /**
     * Where a symbol's ink really sits when it is centered on {@code centerX}.
     * <p>A bitmap glyph's advance is its ink plus one pixel of space on the right, so text
     * centered on its advance puts the ink half a pixel to the left of the middle - a pixel
     * at the size an orb is drawn at, which is enough to see.
     */
    public static int inkCenterX(int centerX, float scale) {
        return Math.round(centerX - scale / 2f);
    }

    /** How far a symbol reaches above and below its middle, at this scale. */
    public static int halfHeight(float scale) {
        return Math.round(HEIGHT * scale / 2f);
    }

    public static Component of(String text) {
        List<ManaSymbols.Segment> segments = ManaSymbols.segments(text);
        if (segments.isEmpty()) {
            return Component.empty();
        }
        if (segments.size() == 1 && !segments.get(0).symbols()) {
            return Component.literal(segments.get(0).text());
        }

        MutableComponent built = Component.empty();
        for (ManaSymbols.Segment segment : segments) {
            MutableComponent piece = Component.literal(segment.text());
            if (segment.symbols()) {
                piece = piece.withStyle(style -> style.withFont(FONT));
            }
            built.append(piece);
        }
        return built;
    }
}
