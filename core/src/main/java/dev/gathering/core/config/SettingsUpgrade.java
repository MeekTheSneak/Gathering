package dev.gathering.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Bringing a settings file written by an older version up to this one.
 * <p>A settings file is written once, with the defaults of the day, and then kept - so a default this
 * project changes afterwards never reaches a server that has played a single session. The owner found
 * it twice in one afternoon: the card shop still wanted emeralds and villages still hardly built a shop,
 * because his file said so and his file was written before either changed.
 * <p>So the file carries the version of the settings it was written for, and a value still sitting at an
 * older version's default is moved to this one's. A value somebody chose is never touched: that is the
 * whole discipline here - "still at the old default" is the only thing that is upgraded, and everything
 * else is left exactly as the file has it, including values this version would now write differently.
 * <p>Pure: the text in, the text out, and what changed.
 */
public final class SettingsUpgrade {

    /**
     * The settings this version writes.
     * <p>Raise it, and add the list below, whenever a default changes. A file with no version in it was
     * written before there were any, which is version one.
     */
    public static final int VERSION = 2;

    /** Where the version is written: its own section, because it is about the file and not about play. */
    public static final String VERSION_KEY = "settings.version";

    /** One default that moved: what it used to be, and what it is now. */
    public record Change(String key, String was, String now) {
    }

    /**
     * What each version changed, from the version before it.
     * <p>Version two, 2026-09-17: sealed product is bought with the Mana Coin rather than with farmed
     * emeralds, a village builds a card shop about four times as often, packs come off mobs as well as
     * out of chests, and the shelf turns over every hour rather than every four - at four, a session saw
     * one shelf and never watched it move, which reads as a shop whose stock never changes. And the one
     * that is not about shopping: a finished casual game is watched back by the people who played it
     * rather than by anybody, which changed before this class existed - so every file older than it was
     * still showing every hand and every library of every game to the whole server.
     */
    private static final List<List<Change>> CHANGES = List.of(
            List.of(),
            List.of(
                    new Change("collection.sealed_price_item", "\"minecraft:emerald\"", "\"gathering:mana_coin\""),
                    new Change("collection.sealed_price_block", "\"minecraft:emerald_block\"", "\"gathering:mana_coin\""),
                    new Change("collection.sealed_price_block_worth", "9", "1"),
                    new Change("collection.sealed_price_booster", "2", "1"),
                    new Change("collection.village_shop_weight", "8", "20"),
                    new Change("collection.sealed_rotation_hours", "4", "1"),
                    new Change("modes.replays", "\"public\"", "\"participants\""),
                    new Change("collection.pack_loot_sources",
                            "[\"fishing\", \"structures\", \"archaeology\"]",
                            "[\"structures\", \"mobs\", \"fishing\", \"archaeology\"]")));

    private SettingsUpgrade() {
    }

    /** What a file says it was written for: one, where it does not say, and one where it will not say. */
    public static int versionOf(String text) {
        String said = valueOf(text == null ? "" : text, VERSION_KEY);
        try {
            return said == null ? 1 : Integer.parseInt(said.trim());
        } catch (NumberFormatException notANumber) {
            return 1;
        }
    }

    /** Every change a file of this version has not had yet, oldest first. */
    public static List<Change> since(int version) {
        List<Change> changes = new ArrayList<>();
        for (int at = Math.max(1, version); at < VERSION && at < CHANGES.size() + 1; at++) {
            changes.addAll(CHANGES.get(at));
        }
        return List.copyOf(changes);
    }

    /** A file brought up to date, and what was moved in it. */
    public record Upgraded(String text, List<String> changed) {
    }

    /**
     * Moves every value still sitting at an older default, and stamps the version.
     * <p>Values somebody set are left alone: a file saying emeralds because its owner wants emeralds
     * reads exactly the same as one saying emeralds because nobody ever changed it, and the only honest
     * way to tell them apart is what the default was when the file was written.
     */
    public static Upgraded upgrade(String text, int from) {
        String said = text == null ? "" : text;
        List<String> changed = new ArrayList<>();
        for (Change change : since(from)) {
            String has = valueOf(said, change.key());
            if (has == null || !has.equals(change.was())) {
                continue;
            }
            ConfigEdit.Edited edited = ConfigEdit.set(said, change.key(), change.now());
            if (edited.worked()) {
                said = edited.text();
                changed.add(change.key() + " " + change.was() + " -> " + change.now());
            }
        }
        ConfigEdit.Edited stamped = ConfigEdit.set(said, VERSION_KEY, String.valueOf(VERSION));
        return new Upgraded(stamped.worked() ? stamped.text() : said, List.copyOf(changed));
    }

    /**
     * What one key's line says, exactly as written, or null where the file does not have it.
     * <p>Read off the text rather than out of a parsed file, because what matters is whether the line is
     * still the one an older version wrote - and a parser hands back a value, not the writing.
     */
    static String valueOf(String text, String path) {
        String inside = null;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inside = trimmed.substring(1, trimmed.length() - 1).trim();
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals < 0 || trimmed.startsWith("#")) {
                continue;
            }
            // The whole path rather than the key under a heading, and either spelling of it, which
            // is what ConfigEdit matches when it writes. The two disagreeing meant this looked at a
            // file one way and wrote it another: a setting the owner had put at the top level as
            // "modes.replays" was read as absent here and then written a second time by the editor.
            String named = trimmed.substring(0, equals).trim();
            String here = inside == null || inside.isEmpty() ? named : inside + "." + named;
            if (here.equalsIgnoreCase(path)) {
                return trimmed.substring(equals + 1).trim();
            }
        }
        return null;
    }
}
