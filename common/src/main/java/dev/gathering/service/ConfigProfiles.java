package dev.gathering.service;

import dev.gathering.core.config.ConfigProfile;
import dev.gathering.core.config.GatheringConfig;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Applying a settings profile, and putting it back.
 * <p>{@link ConfigProfile} says what a profile is; this is the part that touches a disk. Three
 * things matter and each of them is a way an operator could be left worse off than before they
 * tried it:
 *
 * <ul>
 *   <li><b>Every value goes through the ordinary validated path.</b> A profile is applied one
 *       setting at a time through {@link ServerSettings#set}, which parses the whole file after
 *       each edit and refuses a value the config would silently ignore. A profile that wrote
 *       straight to the file could put a server into the state that method exists to prevent:
 *       a file saying one thing and a server doing another.
 *   <li><b>Restoring is exact.</b> What is kept is the file as it was, not a list of the
 *       settings the profile happened to name - so restoring also puts back a comment, a
 *       hand-written key the profile knows nothing about, and the formatting somebody chose.
 *   <li><b>A half-applied profile is reported as one.</b> If a setting is refused the rest are
 *       still applied and the refusals are returned, because stopping halfway leaves exactly
 *       the confusing state this is meant to avoid - and the operator can restore.
 * </ul>
 *
 * <p><b>Nothing anybody owns is touched.</b> These are settings about what happens next. No
 * collection, inventory, deck or table is read or written here, and applying a profile to a
 * running world leaves every card exactly where it was.
 * <p>Server thread only.
 */
public final class ConfigProfiles {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Gathering");

    private static final String FILE_NAME = "gathering-server.toml";

    /**
     * What the settings were before the last profile was applied, for the life of this server.
     * <p>Held rather than written down, deliberately. Restoring is an "undo that", offered to
     * somebody who has just tried a profile and did not like it; a restore point that survived
     * a restart would be a promise to put back settings that may have been edited on purpose
     * a week later.
     */
    private static volatile String restorePoint;

    private ConfigProfiles() {
    }

    /** What the last applied profile would be undone to, if one has been applied. */
    public static java.util.Optional<String> restorePoint() {
        return java.util.Optional.ofNullable(restorePoint);
    }

    /** Between servers: one world's restore point is not the next one's. */
    public static void clear() {
        restorePoint = null;
    }

    /**
     * What happened when a profile was applied: what to put back, and what would not take.
     *
     * @param restoreTo the whole config file as it was, for {@link #restore}
     * @param refused   the settings that were not accepted, each with the reason
     */
    public record Applied(String restoreTo, List<String> refused) {

        public Applied {
            refused = List.copyOf(refused);
        }

        /** Whether every setting in the profile took. */
        public boolean clean() {
            return refused.isEmpty();
        }
    }

    private static Path file(Platform platform) {
        return platform.configDirectory().resolve(FILE_NAME);
    }

    /** The config file as it stands, or the defaults written out when there is not one yet. */
    public static String currentText(Platform platform) {
        Path where = file(platform);
        try {
            return Files.isRegularFile(where)
                    ? Files.readString(where, StandardCharsets.UTF_8)
                    : GatheringConfig.defaultFileText();
        } catch (IOException couldNotRead) {
            return GatheringConfig.defaultFileText();
        }
    }

    /**
     * Applies a profile, and hands back what it would take to undo it.
     * <p>The file is read first and kept whole. Everything after that is ordinary settings
     * changes, so a server that is running picks them up exactly as it would if somebody had
     * typed them one at a time.
     */
    public static Applied apply(Platform platform, ConfigProfile profile) {
        String before = currentText(platform);
        List<String> refused = new ArrayList<>();
        profile.settings().forEach((path, value) -> {
            String problem = ServerSettings.set(platform, path, value);
            if (problem != null) {
                refused.add(path + ": " + problem);
            }
        });
        restorePoint = before;
        LOGGER.info("Applied the {} settings profile; {} of {} settings refused",
                profile.id(), refused.size(), profile.settings().size());
        return new Applied(before, refused);
    }

    /**
     * Puts the config file back exactly as it was, and reloads.
     * <p>Parsed before it is written. What is being restored was a working file a moment ago,
     * so this should never fire - but "should never" is not a reason to write something back
     * unchecked over the one file a server needs in order to start.
     *
     * @return what went wrong, or null if it worked
     */
    public static String restore(Platform platform, String text) {
        if (text == null) {
            return "There is nothing to restore";
        }
        try {
            GatheringConfig.read(dev.gathering.core.config.Toml.read(text));
        } catch (dev.gathering.core.config.TomlException unreadable) {
            return "The settings being restored are not readable: " + unreadable.getMessage();
        }
        Path where = file(platform);
        try {
            Files.createDirectories(where.getParent());
            Files.writeString(where, text, StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            return "Could not write " + FILE_NAME + ": " + couldNotWrite.getMessage();
        }
        ServerSettings.load(platform);
        LOGGER.info("Restored the settings that were in place before a profile was applied");
        return null;
    }
}
