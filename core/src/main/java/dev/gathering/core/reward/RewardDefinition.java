package dev.gathering.core.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A named thing a pack author can have this mod hand somebody.
 * <p>Deliberately the smallest contract that is useful. It says <b>what is given</b> and
 * <b>what has to be installed for it to mean anything</b>, and nothing at all about when. A
 * pack already has advancements, loot tables and recipes for deciding when; a second trigger
 * system inside this mod would be a worse copy of the one the game ships with, and every pack
 * author would have to learn it to use any of this.
 * <p>So the shape is: give this sealed product, under this name, if these mods are present.
 * The pack wires it to whatever it likes and calls it by name.
 *
 * <p><b>There is no code and no address in here.</b> A definition names a set, a product, a
 * color and a count. It cannot name a URL, a class, a command or a file, and a value that
 * looks like an address is refused rather than ignored - a data file that can reach the
 * network is a data file that can be a vulnerability, and "nothing ever does anything with
 * that field" is not a property anybody can check two years later.
 *
 * <p>Every field is bounded. A definition is a file somebody else wrote, so its strings have
 * lengths and its numbers have ranges, and one that breaks a bound is refused with the field
 * named rather than clamped quietly into something the author did not ask for.
 *
 * <p>Pure. Parsing JSON, finding files and knowing which mods are loaded all live elsewhere.
 */
public record RewardDefinition(
        String id, String set, String product, Optional<String> color, int count,
        List<String> requiredMods) {

    /** Long enough for any real set or product code, short enough not to be a payload. */
    public static final int LONGEST_NAME = 64;

    /** The most one reward may hand over at once. */
    public static final int MOST_AT_ONCE = 64;

    /** The most mods one reward may depend on. */
    public static final int MOST_REQUIRED_MODS = 16;

    /** The five colors a booster may be restricted to. */
    private static final String COLORS = "WUBRG";

    public RewardDefinition {
        color = color == null ? Optional.empty() : color;
        requiredMods = requiredMods == null ? List.of() : List.copyOf(requiredMods);
    }

    /** What is wrong with a definition, and which field it is wrong in. */
    public record Problem(String field, String said) {

        @Override
        public String toString() {
            return field + ": " + said;
        }
    }

    /**
     * Whether a value is trying to be an address rather than a name.
     * <p>Checked rather than trusted. Nothing here would fetch one, but a field that silently
     * accepts a URL today is a field somebody wires to a fetch in two years, and the data
     * files saying so will already be in a thousand packs by then.
     */
    private static boolean looksLikeAnAddress(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("://") || lower.startsWith("//")
                || lower.contains("\\") || lower.contains("..");
    }

    private static void checkName(List<Problem> problems, String field, String value) {
        if (value == null || value.isBlank()) {
            problems.add(new Problem(field, "is required"));
            return;
        }
        if (value.length() > LONGEST_NAME) {
            problems.add(new Problem(field, "is longer than " + LONGEST_NAME + " characters"));
            return;
        }
        if (looksLikeAnAddress(value)) {
            problems.add(new Problem(field,
                    "looks like an address. A reward names a set and a product; it cannot name "
                            + "somewhere to fetch one from"));
        }
    }

    /**
     * Everything wrong with this definition, each naming its own field.
     * <p>All of them rather than the first: a pack author who fixes one problem per reload is
     * a pack author who gives up on the third.
     */
    public List<Problem> problems() {
        List<Problem> problems = new ArrayList<>();
        checkName(problems, "id", id);
        checkName(problems, "set", set);
        checkName(problems, "product", product);
        color.ifPresent(chosen -> {
            if (chosen.length() != 1
                    || COLORS.indexOf(Character.toUpperCase(chosen.charAt(0))) < 0) {
                problems.add(new Problem("color",
                        "is '" + chosen + "'. A color is one of W, U, B, R or G, or absent"));
            }
        });
        if (count < 1 || count > MOST_AT_ONCE) {
            problems.add(new Problem("count",
                    "is " + count + ". It has to be between 1 and " + MOST_AT_ONCE));
        }
        if (requiredMods.size() > MOST_REQUIRED_MODS) {
            problems.add(new Problem("required_mods",
                    "names " + requiredMods.size() + " mods; at most " + MOST_REQUIRED_MODS
                            + " are allowed"));
        }
        for (String mod : requiredMods) {
            if (mod == null || mod.isBlank() || mod.length() > LONGEST_NAME
                    || looksLikeAnAddress(mod)) {
                problems.add(new Problem("required_mods",
                        "contains '" + mod + "', which is not a mod id"));
            }
        }
        return List.copyOf(problems);
    }

    /** Whether this definition is usable at all. */
    public boolean isSound() {
        return problems().isEmpty();
    }

    /**
     * Whether everything this reward needs is installed.
     * <p>A reward for a mod nobody has is not an error. One pack ships rewards for four boss
     * mods and expects people to install two of them; refusing to load would punish exactly
     * the packs that are being careful about it.
     *
     * @param installed answers whether a mod id is present
     */
    public boolean appliesWith(java.util.function.Predicate<String> installed) {
        if (requiredMods.isEmpty()) {
            return true;
        }
        for (String mod : requiredMods) {
            if (installed == null || !installed.test(mod)) {
                return false;
            }
        }
        return true;
    }
}
