package dev.gathering.service;

import dev.gathering.core.config.ConfigEdit;
import dev.gathering.core.config.GatheringConfig;
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
 * The running server's settings, read from one file next to every other mod's.
 * <p>Written out with its own explanations on first start, so the way to find out what this
 * mod can be told is to open the file rather than to go looking for a wiki.
 * <p>A server always starts. A file that cannot be read is an error in the log and the
 * defaults in memory - which are play mode with collection off, the conservative pair - never
 * a refusal to boot somebody's world over a stray bracket.
 * <p>Read once at start and held. Settings do not change under a running server: a booster
 * that could be opened at one moment and not the next, halfway through somebody opening it,
 * is worse than one that waits for a restart.
 */
public final class ServerSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final String FILE_NAME = "gathering-server.toml";

    private static volatile GatheringConfig active = GatheringConfig.defaults();

    private ServerSettings() {
    }

    /** What this server is running as. Never null, even before a server has started. */
    public static GatheringConfig get() {
        return active;
    }

    /** Reads the file, writing a fresh one first if there is not one yet. */
    public static GatheringConfig load(Platform platform) {
        Path file = platform.configDirectory().resolve(FILE_NAME);
        active = readOrDefault(file);
        return active;
    }

    /**
     * Changes one setting, in the file and in memory.
     * <p>The file first, because the file is what the server comes back up on: a setting
     * changed in game that a restart forgot would be worse than one that could not be changed
     * at all. Then the whole file is read again rather than the one value patched into what is
     * held, so what the server is running on is always what somebody would read off the disk.
     * <p>What has to be re-read after a change - the shop's shelf, the loot pool, which set is
     * current - is the caller's, because those live in a layer this one knows nothing about.
     *
     * @return what went wrong, or null if it worked
     */
    public static String set(Platform platform, String path, String value) {
        Path file = platform.configDirectory().resolve(FILE_NAME);
        String text;
        try {
            text = Files.isRegularFile(file)
                    ? Files.readString(file, StandardCharsets.UTF_8)
                    : GatheringConfig.defaultFileText();
        } catch (IOException couldNotRead) {
            return "Could not read " + FILE_NAME + ": " + couldNotRead.getMessage();
        }

        // What the file already says, so a note this edit did not cause is not blamed on it:
        // a config with a pre-existing complaint in it would otherwise refuse every set.
        GatheringConfig before;
        try {
            before = GatheringConfig.read(Toml.read(text));
        } catch (TomlException alreadyBroken) {
            before = GatheringConfig.defaults();
        }

        ConfigEdit.Edited edited = ConfigEdit.set(text, path, value);
        if (!edited.worked()) {
            return edited.problem();
        }
        // Read the file back before writing it, not after. ConfigEdit only checks that the
        // path has a dot in it and the value is not blank - it has no idea what the key is
        // supposed to hold - so a value TOML cannot express went straight to disk, the reload
        // failed, and the server dropped every setting it had back to the defaults. Import
        // mode, collection mode, ante, loot: all of it, from one typed word. And it stayed
        // that way, because the broken file is still there the next time the server starts.
        //
        // The command said it had worked, because writing the file had.
        GatheringConfig would;
        try {
            would = GatheringConfig.read(Toml.read(edited.text()));
        } catch (TomlException notReadable) {
            return path + " cannot be set to \"" + value + "\": " + notReadable.getMessage();
        }
        // Parsing is not the same as taking. A value of the wrong shape for its key - a word
        // where a number goes - parses as TOML and is then quietly ignored, with a note. If
        // this edit produced one, the setting did not take, and writing it would leave a file
        // saying one thing and a server doing another.
        for (String note : would.notes()) {
            if (!before.notes().contains(note)) {
                return note;
            }
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, edited.text(), StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            return "Could not write " + FILE_NAME + ": " + couldNotWrite.getMessage();
        }
        active = would;
        LOGGER.info("{} was set to {}", path, value);
        return null;
    }

    /** Back to the defaults between servers, rather than one world's settings in the next. */
    public static void clear() {
        active = GatheringConfig.defaults();
    }

    private static GatheringConfig readOrDefault(Path file) {
        String text;
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GatheringConfig.defaultFileText(), StandardCharsets.UTF_8);
                LOGGER.info("Wrote a fresh {} with everything explained in it", FILE_NAME);
            }
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException couldNotRead) {
            LOGGER.error("Could not read {}, so this server is running on the defaults: {}",
                    FILE_NAME, couldNotRead.getMessage());
            return GatheringConfig.defaults();
        }

        GatheringConfig config;
        try {
            config = GatheringConfig.read(Toml.read(text));
        } catch (TomlException notReadable) {
            LOGGER.error("{} says: {}. This server is running on the defaults until that is fixed.",
                    FILE_NAME, notReadable.getMessage());
            return GatheringConfig.defaults();
        }

        // Warnings rather than debug lines: every one of these is the file and the server
        // disagreeing about something, which is exactly what an owner needs to see.
        for (String note : config.notes()) {
            LOGGER.warn("{}: {}", FILE_NAME, note);
        }
        LOGGER.info("Gathering is running with import {} and collection {}",
                config.modes().importEnabled() ? "on" : "off",
                config.modes().collectionEnabled() ? "on" : "off");
        return config;
    }
}
