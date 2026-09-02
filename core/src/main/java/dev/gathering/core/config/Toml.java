package dev.gathering.core.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A config file, read.
 * <p>Deliberately a small corner of TOML rather than the whole language: sections, comments,
 * and four kinds of value - true or false, a whole number, a quoted string, and a list of
 * quoted strings. That covers every setting this mod has and every setting the design brief
 * describes, and the one file it has to read is the one it writes itself.
 * <p>TOML rather than JSON because this file is for a server owner to edit in a text editor
 * with a comment above every line, and because it is what every other mod on their server
 * puts in the same folder. A parser small enough to read in one sitting is the price, and a
 * far better price than a JSON file nobody can annotate.
 * <p><b>Loud about what it does not understand.</b> A line it cannot read is an error with a
 * line number, and a key nobody asked for is reported by {@link #unknownKeys}. Both matter
 * for the same reason: a setting that was silently dropped is a server running differently
 * from the file its owner is reading, and there is no way to find that out from inside the
 * game.
 * <p>Pure.
 */
public final class Toml {

    private final Map<String, Object> values;

    private Toml(Map<String, Object> values) {
        this.values = values;
    }

    /** An empty file, which is every setting at its default. */
    public static Toml empty() {
        return new Toml(new LinkedHashMap<>());
    }

    public static Toml read(String text) throws TomlException {
        Map<String, Object> values = new LinkedHashMap<>();
        String section = "";
        int line = 0;
        for (String raw : (text == null ? "" : text).split("\n", -1)) {
            line++;
            String trimmed = strip(raw);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("[")) {
                section = section(trimmed, line);
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                throw new TomlException(line, "'" + trimmed + "' is neither a section nor a setting");
            }
            String key = trimmed.substring(0, equals).trim();
            if (!isKey(key)) {
                throw new TomlException(line, "'" + key + "' is not a setting name");
            }
            // A dotted key is a path of its own - "modes.import_enabled = true" outside any
            // section says exactly what "[modes]" then "import_enabled = true" says, and
            // somebody quoting one line of the file at somebody else will write it that way.
            String path = section.isEmpty() ? key : section + "." + key;
            if (values.containsKey(path)) {
                throw new TomlException(line, "'" + path + "' is set twice");
            }
            values.put(path, value(trimmed.substring(equals + 1).trim(), path, line));
        }
        return new Toml(values);
    }

    /** Whether the file mentions this setting at all, which is not whether it is true. */
    public boolean has(String path) {
        return values.containsKey(path);
    }

    public boolean flag(String path, boolean fallback) throws TomlException {
        Object value = values.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean found)) {
            throw new TomlException(0, "'" + path + "' should be true or false");
        }
        return found;
    }

    public int number(String path, int fallback) throws TomlException {
        Object value = values.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Long found)) {
            throw new TomlException(0, "'" + path + "' should be a whole number");
        }
        if (found > Integer.MAX_VALUE || found < Integer.MIN_VALUE) {
            throw new TomlException(0, "'" + path + "' is bigger than this setting can be");
        }
        return found.intValue();
    }

    public String string(String path, String fallback) throws TomlException {
        Object value = values.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String found)) {
            throw new TomlException(0, "'" + path + "' should be in quotes");
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    public List<String> strings(String path, List<String> fallback) throws TomlException {
        Object value = values.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof List<?> found)) {
            throw new TomlException(0, "'" + path + "' should be a list, like [\"one\", \"two\"]");
        }
        return List.copyOf((List<String>) found);
    }

    /**
     * Settings in the file that nobody asked about.
     * <p>Almost always a typo, and the one mistake a config file cannot report on its own: a
     * misspelled key looks exactly like a key for a feature you have not enabled.
     */
    public List<String> unknownKeys(Collection<String> known) {
        Set<String> asked = new LinkedHashSet<>(known);
        List<String> unknown = new ArrayList<>();
        for (String path : values.keySet()) {
            if (!asked.contains(path)) {
                unknown.add(path);
            }
        }
        return List.copyOf(unknown);
    }

    // ------------------------------------------------------------------- bits

    private static String section(String line, int at) throws TomlException {
        if (!line.endsWith("]")) {
            throw new TomlException(at, "'" + line + "' opens a section and never closes it");
        }
        String name = line.substring(1, line.length() - 1).trim();
        if (name.isEmpty()) {
            throw new TomlException(at, "a section with no name");
        }
        for (String part : name.split("\\.", -1)) {
            if (!isBareKey(part.trim())) {
                throw new TomlException(at, "'" + name + "' is not a section name");
            }
        }
        return name.replace(" ", "");
    }

    private static Object value(String raw, String path, int at) throws TomlException {
        if (raw.isEmpty()) {
            throw new TomlException(at, "'" + path + "' has no value");
        }
        if (raw.equals("true")) {
            return Boolean.TRUE;
        }
        if (raw.equals("false")) {
            return Boolean.FALSE;
        }
        if (raw.startsWith("\"")) {
            return string(raw, path, at);
        }
        if (raw.startsWith("[")) {
            return list(raw, path, at);
        }
        try {
            return Long.parseLong(raw.replace("_", ""));
        } catch (NumberFormatException notANumber) {
            throw new TomlException(at,
                    "'" + path + "' is set to " + raw + ", which is not true, false, a number, "
                            + "a quoted string or a list");
        }
    }

    private static String string(String raw, String path, int at) throws TomlException {
        if (raw.length() < 2 || !raw.endsWith("\"")) {
            throw new TomlException(at, "'" + path + "' opens a quote and never closes it");
        }
        StringBuilder out = new StringBuilder();
        for (int index = 1; index < raw.length() - 1; index++) {
            char character = raw.charAt(index);
            if (character != '\\') {
                if (character == '"') {
                    throw new TomlException(at, "'" + path + "' has a quote inside it; write \\\"");
                }
                out.append(character);
                continue;
            }
            index++;
            if (index >= raw.length() - 1) {
                throw new TomlException(at, "'" + path + "' ends in a backslash");
            }
            char escaped = raw.charAt(index);
            switch (escaped) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                default -> throw new TomlException(at,
                        "'" + path + "' contains \\" + escaped + ", which means nothing here");
            }
        }
        return out.toString();
    }

    private static List<String> list(String raw, String path, int at) throws TomlException {
        if (!raw.endsWith("]")) {
            throw new TomlException(at, "'" + path + "' opens a list and never closes it");
        }
        String inside = raw.substring(1, raw.length() - 1).trim();
        List<String> items = new ArrayList<>();
        if (inside.isEmpty()) {
            return items;
        }
        for (String item : splitOutsideQuotes(inside, at, path)) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("\"")) {
                throw new TomlException(at,
                        "'" + path + "' has " + trimmed + " in it, which needs quotes round it");
            }
            items.add(string(trimmed, path, at));
        }
        return items;
    }

    private static List<String> splitOutsideQuotes(String inside, int at, String path)
            throws TomlException {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < inside.length(); index++) {
            char character = inside.charAt(index);
            if (character == '\\' && quoted) {
                current.append(character);
                if (index + 1 < inside.length()) {
                    current.append(inside.charAt(++index));
                }
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                current.append(character);
                continue;
            }
            if (character == ',' && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        if (quoted) {
            throw new TomlException(at, "'" + path + "' opens a quote and never closes it");
        }
        parts.add(current.toString());
        return parts;
    }

    /**
     * A line with its comment taken off.
     * <p>A '#' inside quotes is part of the value, not the start of a comment - which is the
     * difference between a price item called "minecraft:diamond # the good one" being a typo
     * and being a crash.
     */
    private static String strip(String raw) {
        boolean quoted = false;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (character == '\\' && quoted) {
                index++;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                continue;
            }
            if (character == '#' && !quoted) {
                return raw.substring(0, index).trim();
            }
        }
        return raw.trim();
    }

    /** A setting name, or several joined by dots, which TOML allows and means the same. */
    private static boolean isKey(String key) {
        if (key.isEmpty() || key.startsWith(".") || key.endsWith(".") || key.contains("..")) {
            return false;
        }
        for (String part : key.split("\\.", -1)) {
            if (!isBareKey(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBareKey(String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Toml" + values.keySet();
    }
}
