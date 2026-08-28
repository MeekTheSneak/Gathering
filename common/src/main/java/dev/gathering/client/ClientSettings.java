package dev.gathering.client;

import dev.gathering.core.config.Toml;
import dev.gathering.core.config.TomlException;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The things one player decided about how the mod looks, kept on their own machine.
 *
 * <p>Separate from {@link dev.gathering.service.ServerSettings} on purpose and not by
 * accident of layering. A server decides what a game is; a player decides what it looks like.
 * Putting the theme in the server's file would mean a player could not pick their own, and
 * putting it in the game's state would mean it travelled over the network - a preference
 * about PNGs has no business in either.
 *
 * <p>Read once, lazily, on the first screen that draws. Written whenever something changes,
 * because a theme picked in game and forgotten on restart is worse than no button at all.
 *
 * <p>A file that cannot be read is a line in the log and the defaults in memory. Nobody's
 * game fails to start over a stray bracket in a file about colors.
 *
 * <p>Client thread only, which is where every screen runs.
 */
public final class ClientSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final String FILE_NAME = "gathering-client.toml";

    private static final String THEME_KEY = "gui.theme";

    /** What is written into a fresh file, so the way to find the settings is to open it. */
    private static final String TEMPLATE = """
            # Gathering, as seen from this computer. Server settings are in gathering-server.toml.

            [gui]
            # Which set of GUI art to draw with. There is a picker in Options, Video Settings.
            #
            # A theme is a folder of textures and a small file naming it - no colors and no
            # sizes live in this file or in the code. The ones that ship are gathering:felt,
            # gathering:slate, gathering:walnut and gathering:template, the last of which draws
            # every element as a labelled diagram so you can see what you are painting over.
            #
            # To make your own, copy a folder of art in a resource pack, repaint it, and put a
            # file beside it saying what it is called. Anything a theme leaves out falls back
            # to felt, so a pack may repaint six elements and inherit the rest. See
            # docs/themes.md.
            theme = "gathering:felt"
            """;

    /** What is drawn when this file has never been written. */
    private static final String DEFAULT_THEME = "gathering:felt";

    private static String theme;

    private ClientSettings() {
    }

    /**
     * Which art the screens are drawing with, as an id.
     *
     * <p>An id rather than a theme, because which themes exist is a question about the
     * resource packs and this is a question about one line of one file. {@link GuiThemes}
     * puts the two together; a name nothing recognizes draws the default rather than failing.
     */
    public static String themeId() {
        String known = theme;
        if (known == null) {
            known = readTheme();
            theme = known;
        }
        return known;
    }

    /** Picks a theme and remembers it, on disk as well as in memory. */
    public static void themeId(String wanted) {
        if (wanted == null || wanted.equals(theme)) {
            return;
        }
        theme = wanted;
        write(wanted);
    }

    private static Path file() {
        return Platform.get().configDirectory().resolve(FILE_NAME);
    }

    private static String readTheme() {
        Path where;
        try {
            where = file();
        } catch (RuntimeException noPlatform) {
            // Nothing has registered a platform yet, which happens in bare unit tests. The
            // default theme is a correct answer, and a screen asking about art is not the
            // place to fail a game over it.
            return DEFAULT_THEME;
        }
        if (!Files.isRegularFile(where)) {
            writeFresh(where);
            return DEFAULT_THEME;
        }
        try {
            Toml read = Toml.read(Files.readString(where, StandardCharsets.UTF_8));
            return read.string(THEME_KEY, DEFAULT_THEME);
        } catch (IOException | TomlException couldNotRead) {
            LOGGER.warn("Could not read {}: {}. Drawing with the {} theme.",
                    FILE_NAME, couldNotRead.getMessage(), DEFAULT_THEME);
            return DEFAULT_THEME;
        }
    }

    private static void writeFresh(Path where) {
        try {
            Files.createDirectories(where.getParent());
            Files.writeString(where, TEMPLATE, StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            LOGGER.warn("Could not write {}: {}", FILE_NAME, couldNotWrite.getMessage());
        }
    }

    /**
     * Puts the chosen theme back in the file, keeping everything a player wrote around it.
     *
     * <p>The one line is replaced rather than the file rewritten, so the comments explaining
     * what the setting is survive a button press. A file with no line to replace gets a fresh
     * one - which is the case where there is nothing of the player's to lose.
     */
    private static void write(String wanted) {
        Path where;
        try {
            where = file();
        } catch (RuntimeException noPlatform) {
            return;
        }
        try {
            String text = Files.isRegularFile(where)
                    ? Files.readString(where, StandardCharsets.UTF_8)
                    : TEMPLATE;
            String replaced = text.replaceAll(
                    "(?m)^\\s*theme\\s*=.*$", "theme = \"" + wanted + "\"");
            if (replaced.equals(text)) {
                replaced = text + System.lineSeparator()
                        + "[gui]" + System.lineSeparator()
                        + "theme = \"" + wanted + "\"" + System.lineSeparator();
            }
            Files.createDirectories(where.getParent());
            Files.writeString(where, replaced, StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            LOGGER.warn("Could not save the theme to {}: {}", FILE_NAME, couldNotWrite.getMessage());
        }
    }
}
