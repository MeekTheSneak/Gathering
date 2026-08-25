package dev.gathering.service;

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
 *
 * <p>Written out with its own explanations on first start, so the way to find out what this
 * mod can be told is to open the file rather than to go looking for a wiki.
 *
 * <p>A server always starts. A file that cannot be read is an error in the log and the
 * defaults in memory - which are play mode with collection off, the conservative pair - never
 * a refusal to boot somebody's world over a stray bracket.
 *
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
