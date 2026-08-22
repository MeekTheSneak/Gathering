package dev.gathering.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns {@code {T}: Add {C}{C}} into something that can be drawn as symbols.
 *
 * <p>Oracle text writes mana and tap symbols as braced codes, and read as literal text they
 * are noise in the middle of every sentence a card says. Each recognised code maps to a
 * private-use character in the mod's own symbol font, so the text can be handed to the game's
 * ordinary text layout - which then does wrapping, width and styling for symbols exactly as
 * it does for letters, instead of this having to reimplement all three.
 *
 * <p>Anything unrecognised is left exactly as written, braces and all. A symbol nobody has
 * drawn yet should read as {@code {W/P}} rather than disappear or become a blank box.
 *
 * <p>The symbols are the mod's own art - lettered discs, not Wizards' pictographs - for the
 * same reason the card back is.
 */
public final class ManaSymbols {

    /** Where the symbol font's glyphs start, in the Unicode private use area. */
    public static final int FIRST_CODEPOINT = 0xE000;

    /**
     * Every symbol, in the order their codepoints are assigned.
     *
     * <p>The name is also the texture name, so this list is the one place the font, the
     * generator and the parser have to agree. Adding one means appending here - never
     * inserting, which would renumber every glyph after it.
     */
    public static final List<String> NAMES = List.of(
            // Colours, colourless, snow.
            "w", "u", "b", "r", "g", "c", "s",
            // Generic costs.
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "x", "y", "z",
            // Actions.
            "tap", "untap", "energy",
            // Hybrids, in the conventional order.
            "wu", "wb", "ub", "ur", "br", "bg", "rg", "rw", "gw", "gu",
            // Two-generic hybrids.
            "2w", "2u", "2b", "2r", "2g",
            // Phyrexian.
            "wp", "up", "bp", "rp", "gp");

    /**
     * Codes whose letter is not their texture name.
     *
     * <p>Oracle text writes the tap symbol as {@code {T}}, and a texture called {@code t}
     * next to {@code w} and {@code u} would read as a colour. The alias keeps the file names
     * saying what they are.
     */
    private static final java.util.Map<String, String> ALIASES = java.util.Map.of(
            "t", "tap",
            "q", "untap",
            "e", "energy");

    private ManaSymbols() {
    }

    /** One run of a card's text: either letters, or symbols to be drawn in the symbol font. */
    public record Segment(String text, boolean symbols) {
    }

    /**
     * Splits text into runs of letters and runs of symbol glyphs.
     *
     * <p>Adjacent symbols end up in one run - {@code {C}{C}} is one segment of two glyphs -
     * because that is one styled component instead of two.
     */
    public static List<Segment> segments(String text) {
        List<Segment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        StringBuilder letters = new StringBuilder();
        StringBuilder glyphs = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf('{', index);
            int close = open < 0 ? -1 : text.indexOf('}', open);
            if (open < 0 || close < 0) {
                letters.append(text, index, text.length());
                break;
            }

            String glyph = glyphFor(text.substring(open + 1, close));
            if (glyph == null) {
                // Not a symbol we draw. Leave it as written rather than swallowing it.
                letters.append(text, index, close + 1);
            } else {
                letters.append(text, index, open);
                flush(segments, letters, false);
                glyphs.append(glyph);
                // Peek ahead: a run of symbols stays one segment.
                if (!startsWithSymbol(text, close + 1)) {
                    flush(segments, glyphs, true);
                }
            }
            index = close + 1;
        }

        flush(segments, letters, false);
        flush(segments, glyphs, true);
        return segments;
    }

    private static boolean startsWithSymbol(String text, int at) {
        if (at >= text.length() || text.charAt(at) != '{') {
            return false;
        }
        int close = text.indexOf('}', at);
        return close >= 0 && glyphFor(text.substring(at + 1, close)) != null;
    }

    private static void flush(List<Segment> into, StringBuilder buffer, boolean symbols) {
        if (buffer.length() > 0) {
            into.add(new Segment(buffer.toString(), symbols));
            buffer.setLength(0);
        }
    }

    /** The glyph for a braced code's contents, or null if this is not a symbol we draw. */
    private static String glyphFor(String code) {
        String name = nameFor(code);
        if (name == null) {
            return null;
        }
        int index = NAMES.indexOf(name);
        return index < 0 ? null : String.valueOf((char) (FIRST_CODEPOINT + index));
    }

    /**
     * The texture name for a braced code, or null.
     *
     * <p>Hybrid codes are normalised to the conventional order, so {@code {U/W}} and
     * {@code {W/U}} are the same symbol rather than one symbol and one gap.
     */
    public static String nameFor(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        String normalised = code.toLowerCase(Locale.ROOT).replace("/", "");
        String alias = ALIASES.get(normalised);
        if (alias != null) {
            return alias;
        }
        if (NAMES.contains(normalised)) {
            return normalised;
        }
        if (normalised.length() == 2) {
            String flipped = "" + normalised.charAt(1) + normalised.charAt(0);
            if (NAMES.contains(flipped)) {
                return flipped;
            }
        }
        return null;
    }
}
