package dev.gathering.core.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * A page of the how-to-play guide, read from the small piece of Markdown it is written in.
 * <p>Written as Markdown so the words live in a file a translator or a resource pack can replace
 * whole, the way Charta keeps its how-to-play pages, rather than in a hundred language keys that
 * only make sense in order. Only the part of Markdown a page of instructions uses: headings,
 * paragraphs, bulleted and numbered lists, bold, and one thing of the mod's own - a key written as
 * {@code {key:draw}}, which the screen fills with whatever that key is bound to now, so a player
 * who moved draw onto Z reads Z.
 * <p>Anything else is kept as the text it was, rather than dropped: a page that loses a sentence
 * because somebody used a feature this does not read is worse than one that shows an asterisk.
 * <p>Pure: the screen draws what this returns.
 */
public final class GuidePage {

    /** One block of a page, in reading order. */
    public sealed interface Block {
    }

    /** A heading: one for a page's sections, two for the parts of a section. */
    public record Heading(int level, List<Span> text) implements Block {
    }

    /** A paragraph: consecutive lines of text, run together. */
    public record Paragraph(List<Span> text) implements Block {
    }

    /** One item of a bulleted list. */
    public record Bullet(List<Span> text) implements Block {
    }

    /** One step of a numbered list, with the number it was written with. */
    public record Step(int number, List<Span> text) implements Block {
    }

    /** A run of text within a block. */
    public sealed interface Span {
    }

    /** Plain or bold words. */
    public record Words(String text, boolean bold) implements Span {
    }

    /** A key, by the name of the action it performs - filled in with its current binding. */
    public record Key(String action) implements Span {
    }

    private final List<Block> blocks;

    private GuidePage(List<Block> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    public List<Block> blocks() {
        return blocks;
    }

    /** Reads a page. Never fails: text it does not understand is text. */
    public static GuidePage parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        String source = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        for (String raw : source.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                flush(blocks, paragraph);
                continue;
            }
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                if (level <= 3 && level < line.length() && line.charAt(level) == ' ') {
                    flush(blocks, paragraph);
                    blocks.add(new Heading(level, spans(line.substring(level + 1).strip())));
                    continue;
                }
            }
            if ((line.startsWith("- ") || line.startsWith("* ")) && line.length() > 2) {
                flush(blocks, paragraph);
                blocks.add(new Bullet(spans(line.substring(2).strip())));
                continue;
            }
            int dot = line.indexOf(". ");
            if (dot > 0 && dot <= 3 && line.substring(0, dot).chars().allMatch(Character::isDigit)) {
                flush(blocks, paragraph);
                blocks.add(new Step(Integer.parseInt(line.substring(0, dot)), spans(line.substring(dot + 2).strip())));
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }
        flush(blocks, paragraph);
        return new GuidePage(blocks);
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new Paragraph(spans(paragraph.toString())));
            paragraph.setLength(0);
        }
    }

    /** The bold runs and keys in a line of text. An unclosed marker is kept as written. */
    static List<Span> spans(String text) {
        List<Span> spans = new ArrayList<>();
        StringBuilder words = new StringBuilder();
        boolean bold = false;
        int index = 0;
        while (index < text.length()) {
            if (text.startsWith("**", index) && (bold || text.indexOf("**", index + 2) > 0)) {
                add(spans, words, bold);
                bold = !bold;
                index += 2;
                continue;
            }
            if (text.startsWith("{key:", index)) {
                int close = text.indexOf('}', index);
                String action = close < 0 ? "" : text.substring(index + 5, close).strip();
                if (close > 0 && !action.isEmpty() && action.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
                    add(spans, words, bold);
                    spans.add(new Key(action));
                    index = close + 1;
                    continue;
                }
            }
            words.append(text.charAt(index));
            index++;
        }
        add(spans, words, bold);
        return List.copyOf(spans);
    }

    private static void add(List<Span> spans, StringBuilder words, boolean bold) {
        if (!words.isEmpty()) {
            spans.add(new Words(words.toString(), bold));
            words.setLength(0);
        }
    }
}
