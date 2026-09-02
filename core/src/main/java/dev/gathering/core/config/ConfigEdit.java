package dev.gathering.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Changing one setting in a config file, without losing the rest of it.
 * <p>The file is the source of truth and stays that way: a setting changed in game is a
 * setting changed in the file, so a server that comes back up runs on what its owner last
 * said rather than on what the file still says. That means editing text rather than writing
 * a fresh file from a parsed model, because the file has a server owner's comments in it and
 * regenerating it would throw them away.
 * <p>Only ever one line. Everything else in the file - comments, spacing, key order, sections
 * this version has never heard of - comes out exactly as it went in.
 */
public final class ConfigEdit {

    /** What an edit produced, or why it could not be made. */
    public record Edited(String text, String problem) {

        public boolean worked() {
            return problem == null;
        }

        static Edited failed(String why) {
            return new Edited(null, why);
        }
    }

    private ConfigEdit() {
    }

    /**
     * Sets {@code section.key} to this value.
     * <p>The key is written where it already is, or added at the end of its section, or the
     * section is added at the end of the file - in that order, so a file somebody has
     * rearranged keeps its arrangement.
     *
     * @param value already in TOML form: {@code true}, {@code 12}, {@code "auto"},
     *              {@code ["basic lands"]}. Quoting is the caller's, because what a value is
     *              allowed to be is the setting's business rather than the file format's.
     */
    public static Edited set(String text, String path, String value) {
        if (path == null || !path.contains(".")) {
            return Edited.failed("A setting is named section.key, like modes.collection_enabled.");
        }
        if (value == null || value.isBlank()) {
            return Edited.failed("No value given.");
        }
        String section = path.substring(0, path.indexOf('.'));
        String key = path.substring(path.indexOf('.') + 1);

        List<String> lines = new ArrayList<>(List.of((text == null ? "" : text).split("\n", -1)));
        String inside = null;
        int endOfSection = -1;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String bare = line.strip();
            if (bare.startsWith("[") && bare.endsWith("]")) {
                if (section.equalsIgnoreCase(inside)) {
                    endOfSection = index;
                }
                inside = bare.substring(1, bare.length() - 1).strip();
                continue;
            }
            if (bare.isEmpty() || bare.startsWith("#")) {
                continue;
            }
            int equals = bare.indexOf('=');
            if (equals < 0) {
                continue;
            }
            // Against the whole path rather than the key alone, because TOML lets a setting
            // be written either way - under a [modes] heading, or as "modes.collection_enabled"
            // with no heading at all. Only the first was recognised, so setting one already
            // written the second way appended a second copy under a heading of its own and
            // left the one the owner could see doing nothing.
            String named = bare.substring(0, equals).strip();
            String here = inside == null || inside.isEmpty() ? named : inside + "." + named;
            if (here.equalsIgnoreCase(path)) {
                // Written back with whatever indentation and whatever spelling of the path
                // the line already had, so a file laid out one way does not come back laid
                // out another.
                lines.set(index, line.substring(0, line.indexOf(line.strip()))
                        + named + " = " + value);
                return new Edited(String.join("\n", lines), null);
            }
        }

        if (section.equalsIgnoreCase(inside)) {
            endOfSection = lines.size();
        }
        if (endOfSection < 0) {
            // No such section. Added at the end, with a blank line before it, which is what
            // the written-out default looks like.
            lines.add("");
            lines.add("[" + section + "]");
            lines.add(key + " = " + value);
            return new Edited(String.join("\n", lines), null);
        }
        // Backed up past any trailing blank lines, so the key lands under its own section
        // rather than after the gap that separates it from the next one.
        int at = endOfSection;
        while (at > 0 && lines.get(at - 1).isBlank()) {
            at--;
        }
        lines.add(at, key + " = " + value);
        return new Edited(String.join("\n", lines), null);
    }

    /**
     * What to write into the file for a value somebody typed at a command line.
     * <p>A number stays a number, true and false stay flags, a comma-separated run becomes a
     * list, and anything else is quoted. A setting's own reader decides whether the result
     * means anything; this only decides what shape it is.
     */
    public static Optional<String> asToml(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        String value = typed.strip();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("false")) {
            return Optional.of(lower);
        }
        if (value.matches("-?\\d+")) {
            return Optional.of(value);
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return Optional.of(value);
        }
        if (value.contains(",")) {
            List<String> items = new ArrayList<>();
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    items.add(quoted(item.strip()));
                }
            }
            return Optional.of("[" + String.join(", ", items) + "]");
        }
        return Optional.of(quoted(value));
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
