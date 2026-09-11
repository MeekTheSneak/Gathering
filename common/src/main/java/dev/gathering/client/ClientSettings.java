package dev.gathering.client;

import dev.gathering.core.config.Toml;
import dev.gathering.core.config.TomlException;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The things one player decided about how the mod looks and sounds, kept on their own machine.
 * <p>Separate from {@link dev.gathering.service.ServerSettings} on purpose and not by
 * accident of layering. A server decides what a game is; a player decides what it looks like.
 * Putting the theme in the server's file would mean a player could not pick their own, and
 * putting it in the game's state would mean it traveled over the network - a preference
 * about PNGs has no business in either. Nothing here can grant an item, change a rule, or be
 * seen by anybody else.
 * <p>One file, versioned. A file written by an older build has no {@code schema} line and
 * carries only a theme; it is read, kept, and grown into the current shape without losing the
 * one line it had. That is the whole of the migration and it is tested.
 * <p>Values apply the moment they are set. The file is written from the client tick, at most
 * once every {@link #TICKS_BEFORE_WRITING} ticks after the last change - because a slider is
 * dragged sixty times a second and a file written sixty times a second is a stutter the
 * player can feel while they are adjusting the setting that was supposed to help.
 * <p>A file that cannot be read is a line in the log and the defaults in memory. Nobody's game
 * fails to start over a stray bracket in a file about colors.
 * <p>Client thread only, which is where every screen runs.
 */
public final class ClientSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final String FILE_NAME = "gathering-client.toml";

    /**
     * What shape this file is in.
     * <p>1 was the theme and nothing else, and had no version line at all - so an absent
     * schema means 1 rather than meaning broken. 2 added the accessibility, feedback and
     * tutorial sections.
     */
    private static final int SCHEMA = 2;

    /** How long after the last change the file is written, in client ticks. */
    private static final int TICKS_BEFORE_WRITING = 20;

    // ------------------------------------------------------------------ keys

    private static final String SCHEMA_KEY = "file.schema";
    private static final String THEME_KEY = "gui.theme";
    private static final String TEXT_SCALE_KEY = "accessibility.text_scale";
    private static final String CONTROL_SCALE_KEY = "accessibility.control_scale";
    private static final String REDUCED_MOTION_KEY = "accessibility.reduced_motion";
    private static final String EFFECTS_KEY = "accessibility.effect_intensity";
    private static final String HOLD_TO_INSPECT_KEY = "accessibility.hold_to_inspect";
    private static final String TABLE_SOUNDS_KEY = "feedback.table_sounds";
    private static final String SOUND_VOLUME_KEY = "feedback.table_sound_volume";
    private static final String TURN_NOTICE_KEY = "feedback.turn_notification";
    private static final String WAITING_AFTER_KEY = "feedback.waiting_after_ms";
    private static final String TUTORIAL_OFFERED_KEY = "tutorial.offered";
    private static final String TUTORIAL_FINISHED_KEY = "tutorial.finished";
    private static final String TUTORIAL_SKIPPED_KEY = "tutorial.skipped";

    /** What is drawn when this file has never been written. */
    private static final String DEFAULT_THEME = "gathering:basic";

    /**
     * Scales, as hundredths.
     * <p>Integers rather than doubles because that is what the TOML reader in core answers
     * with, and because a percentage is the thing a player is actually choosing: nobody wants
     * a text scale of 1.1749999999999998.
     */
    public static final int SMALLEST_SCALE = 75;
    public static final int LARGEST_SCALE = 200;

    /** Milliseconds before a request that has not been answered says so. */
    public static final int SOONEST_WAITING_NOTICE = 100;
    public static final int LATEST_WAITING_NOTICE = 3000;

    private static String theme;
    private static int textScale = 100;
    private static int controlScale = 100;
    private static boolean reducedMotion;
    private static int effectIntensity = 100;
    private static boolean holdToInspect = true;
    private static boolean tableSounds = true;
    private static int soundVolume = 100;
    private static boolean turnNotification = true;
    private static int waitingAfterMillis = 300;
    private static boolean tutorialOffered;
    private static boolean tutorialFinished;
    private static boolean tutorialSkipped;

    /** Whether anything has changed since the file was last written. */
    private static boolean unsaved;

    /** How many ticks since the last change, so a drag is written once rather than sixty times. */
    private static int sinceChanged;

    private static boolean loaded;

    private ClientSettings() {
    }

    // ---------------------------------------------------------------- asking

    /**
     * Which art the screens are drawing with, as an id.
     * <p>An id rather than a theme, because which themes exist is a question about the
     * resource packs and this is a question about one line of one file. {@link GuiThemes}
     * puts the two together; a name nothing recognizes draws the default rather than failing.
     */
    public static String themeId() {
        load();
        return theme;
    }

    /** How much bigger than usual to draw words, as a percentage. */
    public static int textScale() {
        load();
        return textScale;
    }

    /** How much bigger than usual to draw the things you click, as a percentage. */
    public static int controlScale() {
        load();
        return controlScale;
    }

    /**
     * Whether to hold everything still.
     * <p>Cards arrive where they are going rather than flying there, packs do not tilt, and
     * the camera cuts rather than slides. What it does not do is take away a state anybody
     * needs: a tapped card still turns, because that is the game rather than decoration.
     */
    public static boolean reducedMotion() {
        load();
        return reducedMotion;
    }

    /** How strong the foil shimmer and the glows are, as a percentage. Zero is none. */
    public static int effectIntensity() {
        load();
        return effectIntensity;
    }

    /** Whether reading a card means holding the key, rather than pressing it to toggle. */
    public static boolean holdToInspect() {
        load();
        return holdToInspect;
    }

    /** Whether this client plays the table's own sounds at all. */
    public static boolean tableSounds() {
        load();
        return tableSounds;
    }

    /** How loud the table's own sounds are for this player, as a percentage. */
    public static int tableSoundVolume() {
        load();
        return soundVolume;
    }

    /** Whether to say something when the turn comes round to this player. */
    public static boolean turnNotification() {
        load();
        return turnNotification;
    }

    /** How long a request may go unanswered before the screen says it is waiting. */
    public static int waitingAfterMillis() {
        load();
        return waitingAfterMillis;
    }

    /** Whether this player has already been offered the guided first game. */
    public static boolean tutorialOffered() {
        load();
        return tutorialOffered;
    }

    /** Whether they finished it. Kept apart from skipping on purpose - they are not the same. */
    public static boolean tutorialFinished() {
        load();
        return tutorialFinished;
    }

    /** Whether they said no. Recorded as skipped, never as finished. */
    public static boolean tutorialSkipped() {
        load();
        return tutorialSkipped;
    }

    // ---------------------------------------------------------------- saying

    public static void themeId(String wanted) {
        load();
        if (wanted == null || wanted.equals(theme)) {
            return;
        }
        theme = wanted;
        changed();
    }

    public static void textScale(int percent) {
        load();
        int wanted = clampScale(percent);
        if (wanted != textScale) {
            textScale = wanted;
            changed();
        }
    }

    public static void controlScale(int percent) {
        load();
        int wanted = clampScale(percent);
        if (wanted != controlScale) {
            controlScale = wanted;
            changed();
        }
    }

    public static void reducedMotion(boolean wanted) {
        load();
        if (wanted != reducedMotion) {
            reducedMotion = wanted;
            changed();
        }
    }

    public static void effectIntensity(int percent) {
        load();
        int wanted = Math.clamp(percent, 0, 100);
        if (wanted != effectIntensity) {
            effectIntensity = wanted;
            changed();
        }
    }

    public static void holdToInspect(boolean wanted) {
        load();
        if (wanted != holdToInspect) {
            holdToInspect = wanted;
            changed();
        }
    }

    public static void tableSounds(boolean wanted) {
        load();
        if (wanted != tableSounds) {
            tableSounds = wanted;
            changed();
        }
    }

    public static void tableSoundVolume(int percent) {
        load();
        int wanted = Math.clamp(percent, 0, 100);
        if (wanted != soundVolume) {
            soundVolume = wanted;
            changed();
        }
    }

    public static void turnNotification(boolean wanted) {
        load();
        if (wanted != turnNotification) {
            turnNotification = wanted;
            changed();
        }
    }

    public static void waitingAfterMillis(int millis) {
        load();
        int wanted = Math.clamp(millis, SOONEST_WAITING_NOTICE, LATEST_WAITING_NOTICE);
        if (wanted != waitingAfterMillis) {
            waitingAfterMillis = wanted;
            changed();
        }
    }

    /** They have been shown the offer, whatever they said to it. */
    public static void tutorialOffered(boolean wanted) {
        load();
        if (wanted != tutorialOffered) {
            tutorialOffered = wanted;
            changed();
        }
    }

    /** They got to the end. Only ever set by the last step actually completing. */
    public static void tutorialFinished(boolean wanted) {
        load();
        if (wanted != tutorialFinished) {
            tutorialFinished = wanted;
            changed();
        }
    }

    /** They said no, or left partway. Never written as finished. */
    public static void tutorialSkipped(boolean wanted) {
        load();
        if (wanted != tutorialSkipped) {
            tutorialSkipped = wanted;
            changed();
        }
    }

    /**
     * Puts one section back the way it shipped.
     * <p>Per category rather than all at once, because a player who wants their motion
     * settings back should not lose their theme to get them.
     */
    public static void resetAccessibility() {
        load();
        textScale = 100;
        controlScale = 100;
        reducedMotion = false;
        effectIntensity = 100;
        holdToInspect = true;
        changed();
    }

    /** The same, for the noises and the waiting notice. */
    public static void resetFeedback() {
        load();
        tableSounds = true;
        soundVolume = 100;
        turnNotification = true;
        waitingAfterMillis = 300;
        changed();
    }

    // ----------------------------------------------------------------- disk

    /**
     * The values a settings row steps through, by the setting it is about.
     * <p>Here rather than in the screen, because here is what clamps them. Written down beside
     * the bounds instead, the two drift: the screen once offered 50% while this floor was 75%,
     * so choosing it showed 50, stored 75 and drew at 75 - a control that lies about what it
     * just did, and one nobody notices because every part of it looks right on its own.
     * <p>A screen cannot be loaded in a game test at all - a dedicated server refuses every
     * client class one is built from - so a list kept in the screen is a list that can only be
     * checked by reading it. Kept here, it is checked by running it.
     */
    public static java.util.Map<String, java.util.List<Integer>> offeredSteps() {
        return java.util.Map.of(
                "text_scale", stepsBetween(SMALLEST_SCALE, LARGEST_SCALE, 25),
                "control_scale", stepsBetween(SMALLEST_SCALE, LARGEST_SCALE, 25),
                "effect_intensity", java.util.List.of(0, 25, 50, 75, 100),
                "sound_volume", java.util.List.of(0, 25, 50, 75, 100),
                "waiting_after", java.util.List.of(
                        SOONEST_WAITING_NOTICE, 300, 1000, LATEST_WAITING_NOTICE));
    }

    /** Every step from one bound to the other, both ends included. */
    private static java.util.List<Integer> stepsBetween(int from, int to, int by) {
        java.util.List<Integer> made = new java.util.ArrayList<>();
        for (int at = from; at <= to; at += by) {
            made.add(at);
        }
        if (made.getLast() != to) {
            made.add(to);
        }
        return java.util.List.copyOf(made);
    }

    private static int clampScale(int percent) {
        return Math.clamp(percent, SMALLEST_SCALE, LARGEST_SCALE);
    }

    private static void changed() {
        unsaved = true;
        sinceChanged = 0;
    }

    /**
     * One client tick. Writes the file if it is due.
     * <p>Called from {@link ClientTicks}, which both loaders drive. The count restarts on
     * every change, so a slider being dragged writes once when the player lets go rather than
     * once per frame while they are still choosing.
     */
    static void tick() {
        if (!unsaved) {
            return;
        }
        if (++sinceChanged < TICKS_BEFORE_WRITING) {
            return;
        }
        unsaved = false;
        sinceChanged = 0;
        write();
    }

    /**
     * Writes now, whatever the count says.
     * <p>For leaving the game, where there is no next tick to wait for.
     */
    public static void flush() {
        if (unsaved) {
            unsaved = false;
            sinceChanged = 0;
            write();
        }
    }

    /**
     * Where the file is, or a temporary one a test has pointed this at.
     * <p>Null in the game, always. It exists because the in-world tests for this file run in a
     * real world, and Minecraft's game tests run several at once in a grid - so tests that all
     * wrote the one real settings file were racing each other, and which of them failed
     * depended on how many other tests happened to be in the run.
     */
    private static Path insteadForTesting;

    private static Path file() {
        Path chosen = insteadForTesting;
        return chosen != null ? chosen : Platform.get().configDirectory().resolve(FILE_NAME);
    }

    /**
     * Points this at another file, for a test that wants one of its own.
     * <p>Null puts it back. Nothing in the game calls it; a player has one settings file and
     * it is the one in their config folder.
     */
    public static void fileForTesting(Path where) {
        insteadForTesting = where;
        forgetForTesting();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        // Set first. A platform that is not there, or a file that will not parse, both end
        // here with the defaults in memory rather than trying again on every single getter.
        loaded = true;
        theme = DEFAULT_THEME;

        Path where;
        try {
            where = file();
        } catch (RuntimeException noPlatform) {
            // Nothing has registered a platform yet, which happens in bare unit tests. The
            // defaults are a correct answer, and a screen asking about art is not the place
            // to fail a game over it.
            return;
        }
        if (!Files.isRegularFile(where)) {
            writeFresh(where);
            return;
        }
        Toml read;
        try {
            read = Toml.read(Files.readString(where, StandardCharsets.UTF_8));
        } catch (IOException | TomlException couldNotRead) {
            LOGGER.warn("Could not read {}: {}. Using the defaults.",
                    FILE_NAME, couldNotRead.getMessage());
            return;
        }
        try {
            theme = read.string(THEME_KEY, DEFAULT_THEME);
            textScale = clampScale(read.number(TEXT_SCALE_KEY, 100));
            controlScale = clampScale(read.number(CONTROL_SCALE_KEY, 100));
            reducedMotion = read.flag(REDUCED_MOTION_KEY, false);
            effectIntensity = Math.clamp(read.number(EFFECTS_KEY, 100), 0, 100);
            holdToInspect = read.flag(HOLD_TO_INSPECT_KEY, true);
            tableSounds = read.flag(TABLE_SOUNDS_KEY, true);
            soundVolume = Math.clamp(read.number(SOUND_VOLUME_KEY, 100), 0, 100);
            turnNotification = read.flag(TURN_NOTICE_KEY, true);
            waitingAfterMillis = Math.clamp(read.number(WAITING_AFTER_KEY, 300),
                    SOONEST_WAITING_NOTICE, LATEST_WAITING_NOTICE);
            tutorialOffered = read.flag(TUTORIAL_OFFERED_KEY, false);
            tutorialFinished = read.flag(TUTORIAL_FINISHED_KEY, false);
            tutorialSkipped = read.flag(TUTORIAL_SKIPPED_KEY, false);
        } catch (TomlException wrongType) {
            // One line of the wrong type - a scale somebody wrote as a word - should not throw
            // away the lines around it. Whatever was read before the bad line is kept.
            LOGGER.warn("Could not read part of {}: {}. Using the defaults for the rest.",
                    FILE_NAME, wrongType.getMessage());
        }

        // A file written before this shape existed says nothing about a schema, which is not
        // an error: it is a theme and the defaults for everything that had not been invented
        // yet. Growing it into the current shape now means the comments explaining the new
        // settings are there the first time somebody goes looking for them.
        int was = 1;
        try {
            was = read.number(SCHEMA_KEY, 1);
        } catch (TomlException notANumber) {
            LOGGER.warn("{} has a schema that is not a number; treating it as the oldest shape",
                    FILE_NAME);
        }
        if (was < SCHEMA) {
            LOGGER.info("Growing {} from shape {} into shape {}, keeping what it said",
                    FILE_NAME, was, SCHEMA);
            write();
        }
    }

    private static void writeFresh(Path where) {
        try {
            Files.createDirectories(where.getParent());
            Files.writeString(where, template(), StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            LOGGER.warn("Could not write {}: {}", FILE_NAME, couldNotWrite.getMessage());
        }
    }

    /**
     * What is written into a fresh file, so the way to find the settings is to open it.
     * <p>Built from the values in memory rather than being a constant, so a fresh file and a
     * file this has just rewritten say the same things in the same order.
     */
    private static String template() {
        StringBuilder text = new StringBuilder();
        text.append("""
                # Gathering, as seen from this computer. Server settings are in gathering-server.toml.
                #
                # Nothing in this file can give you an item, change a rule, or be seen by anybody
                # else at the table. It is what the mod looks and sounds like on this machine.

                [file]
                # Which shape this file is in. Left alone; the mod grows an older file into the
                # current shape by itself and keeps what it said.
                """);
        text.append("schema = ").append(SCHEMA).append("\n\n");
        text.append("""
                [gui]
                # Which set of GUI art to draw with. There is a picker in Options, Video Settings.
                #
                # A theme is a folder of textures and a small file naming it - no colors and no
                # sizes live in this file or in the code. The ones that ship are gathering:basic,
                # gathering:slate, gathering:walnut and gathering:template, the last of which draws
                # every element as a labeled diagram so you can see what you are painting over.
                #
                # To make your own, copy a folder of art in a resource pack, repaint it, and put a
                # file beside it saying what it is called. Anything a theme leaves out falls back
                # to felt, so a pack may repaint six elements and inherit the rest. See
                # docs/themes.md.
                """);
        text.append("theme = \"").append(theme).append("\"\n\n");
        text.append("""
                [accessibility]
                # Words and controls scale apart from each other, and both apart from the theme's
                # own art, so bigger text does not have to mean bigger buttons over your hand.
                # Percentages, 75 to 200.
                """);
        text.append("text_scale = ").append(textScale).append("\n");
        text.append("control_scale = ").append(controlScale).append("\n");
        text.append("""
                # Holds everything still: cards arrive where they are going rather than flying,
                # packs do not tilt, the camera cuts rather than slides. A tapped card still
                # turns - that is the game, not decoration.
                """);
        text.append("reduced_motion = ").append(reducedMotion).append("\n");
        text.append("# How strong foil shimmer and glows are, 0 to 100. Zero is none.\n");
        text.append("effect_intensity = ").append(effectIntensity).append("\n");
        text.append("# Whether reading a card means holding the key, or pressing it to toggle.\n");
        text.append("hold_to_inspect = ").append(holdToInspect).append("\n\n");
        text.append("""
                [feedback]
                # The table's own sounds, and how loud they are here. Minecraft's own volume
                # sliders still apply on top of this.
                """);
        text.append("table_sounds = ").append(tableSounds).append("\n");
        text.append("table_sound_volume = ").append(soundVolume).append("\n");
        text.append("# Whether to say something when the turn comes round to you.\n");
        text.append("turn_notification = ").append(turnNotification).append("\n");
        text.append("""
                # How long a request may go unanswered before the screen says it is waiting.
                # Milliseconds, 100 to 3000. Lower makes a busy server feel busier.
                """);
        text.append("waiting_after_ms = ").append(waitingAfterMillis).append("\n\n");
        text.append("""
                [tutorial]
                # The guided first game. "offered" means you have been asked; "finished" means
                # you got to the end; "skipped" means you said no. Skipping is never written as
                # finishing. Set all three to false to be offered it again.
                """);
        text.append("offered = ").append(tutorialOffered).append("\n");
        text.append("finished = ").append(tutorialFinished).append("\n");
        text.append("skipped = ").append(tutorialSkipped).append("\n");
        return text.toString();
    }

    /**
     * Puts the current values back in the file, keeping everything a player wrote around them.
     * <p>Line by line rather than rewriting the file, so the comments explaining what each
     * setting is survive a button press - and so does anything the player added that this
     * version has never heard of. A key with no line to replace is appended under its own
     * section, which is the case where there is nothing of theirs to lose.
     */
    private static void write() {
        Path where;
        try {
            where = file();
        } catch (RuntimeException noPlatform) {
            return;
        }
        try {
            String text = Files.isRegularFile(where)
                    ? Files.readString(where, StandardCharsets.UTF_8)
                    : template();
            for (Map.Entry<String, String> entry : lines().entrySet()) {
                text = replaced(text, entry.getKey(), entry.getValue());
            }
            Files.createDirectories(where.getParent());
            Files.writeString(where, text, StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            LOGGER.warn("Could not save {}: {}", FILE_NAME, couldNotWrite.getMessage());
        }
    }

    /** Every setting, as the dotted key it lives under and the text of its value. */
    private static Map<String, String> lines() {
        Map<String, String> written = new LinkedHashMap<>();
        written.put(SCHEMA_KEY, Integer.toString(SCHEMA));
        written.put(THEME_KEY, "\"" + theme + "\"");
        written.put(TEXT_SCALE_KEY, Integer.toString(textScale));
        written.put(CONTROL_SCALE_KEY, Integer.toString(controlScale));
        written.put(REDUCED_MOTION_KEY, Boolean.toString(reducedMotion));
        written.put(EFFECTS_KEY, Integer.toString(effectIntensity));
        written.put(HOLD_TO_INSPECT_KEY, Boolean.toString(holdToInspect));
        written.put(TABLE_SOUNDS_KEY, Boolean.toString(tableSounds));
        written.put(SOUND_VOLUME_KEY, Integer.toString(soundVolume));
        written.put(TURN_NOTICE_KEY, Boolean.toString(turnNotification));
        written.put(WAITING_AFTER_KEY, Integer.toString(waitingAfterMillis));
        written.put(TUTORIAL_OFFERED_KEY, Boolean.toString(tutorialOffered));
        written.put(TUTORIAL_FINISHED_KEY, Boolean.toString(tutorialFinished));
        written.put(TUTORIAL_SKIPPED_KEY, Boolean.toString(tutorialSkipped));
        return written;
    }

    /**
     * Puts one dotted key's value into the file text, wherever that key lives.
     * <p>A dotted key is a section and a name: {@code accessibility.text_scale} is
     * {@code text_scale} inside {@code [accessibility]}. So the section has to be found first
     * and the name replaced only inside it - a plain search for {@code text_scale} would
     * happily rewrite a line of the same name under another heading.
     */
    private static String replaced(String text, String dotted, String value) {
        int dot = dotted.lastIndexOf('.');
        String section = dotted.substring(0, dot);
        String name = dotted.substring(dot + 1);

        List<String> out = new ArrayList<>();
        boolean inSection = false;
        boolean done = false;
        String here = "";
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                here = trimmed.substring(1, trimmed.length() - 1).strip().toLowerCase(Locale.ROOT);
                inSection = here.equals(section);
            } else if (inSection && !done && startsWithKey(trimmed, name)) {
                out.add(name + " = " + value);
                done = true;
                continue;
            }
            out.add(line);
        }
        if (done) {
            return String.join("\n", out);
        }
        // No such line. Appended under its own heading, which is what a file written by an
        // older build looks like for every setting that build had never heard of.
        StringBuilder grown = new StringBuilder(String.join("\n", out));
        if (grown.length() > 0 && grown.charAt(grown.length() - 1) != '\n') {
            grown.append('\n');
        }
        grown.append('\n').append('[').append(section).append("]\n")
                .append(name).append(" = ").append(value).append('\n');
        return grown.toString();
    }

    /** Whether this line assigns that name, rather than merely starting with those letters. */
    private static boolean startsWithKey(String line, String name) {
        if (!line.startsWith(name)) {
            return false;
        }
        String rest = line.substring(name.length()).stripLeading();
        return rest.startsWith("=");
    }

    /**
     * Forgets everything, so the next ask reads the file again.
     * <p>For the tests, which write a file and then want it read. Nothing in the game calls
     * this: a player's settings do not change under them while they are playing, and the
     * in-world tests that exercise the file live in the loader modules rather than beside it.
     */
    public static void forgetForTesting() {
        loaded = false;
        unsaved = false;
        sinceChanged = 0;
    }
}
