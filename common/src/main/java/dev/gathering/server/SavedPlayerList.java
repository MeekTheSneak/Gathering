package dev.gathering.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A list of players written in the save, one id to a line: who has had their starter boosters, who has finished
 * the lesson.
 * <p>Added to through a temporary file and a move, so a crash mid-write leaves the old list rather than half of a
 * new one. What a list that cannot be read means is the caller's to say, because the safe answer differs: "has
 * already had theirs" for a grant, "has not finished" for a requirement.
 */
final class SavedPlayerList {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** A ceiling, so a server that has gone wrong cannot write a file without end. Far past any real player count. */
    static final int MOST_REMEMBERED = 100_000;

    private final String folder;
    private final String file;
    private final String heading;

    SavedPlayerList(String folder, String file, String heading) {
        this.folder = folder;
        this.file = file;
        this.heading = heading;
    }

    /** Whether this player is on the list, or {@code unreadable} when the list cannot be read. */
    boolean contains(UUID player, boolean unreadable) {
        Path list = path().orElse(null);
        if (list == null || !Files.isRegularFile(list)) {
            return false;
        }
        try {
            String wanted = player.toString();
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                if (wanted.equals(line.strip())) {
                    return true;
                }
            }
            return false;
        } catch (IOException couldNotRead) {
            LOGGER.error("Could not read {}: {}", file, couldNotRead.getMessage());
            return unreadable;
        }
    }

    /** Adds a player, and says whether it is safely on disk. */
    boolean add(UUID player) {
        Path list = path().orElse(null);
        if (list == null) {
            return false;
        }
        try {
            List<String> lines = Files.isRegularFile(list)
                    ? new ArrayList<>(Files.readAllLines(list, StandardCharsets.UTF_8))
                    : new ArrayList<>(List.of(heading));
            if (lines.size() > MOST_REMEMBERED) {
                LOGGER.error("{} has passed {} lines; nothing more is being written to it until somebody looks at it",
                        file, MOST_REMEMBERED);
                return false;
            }
            lines.add(player.toString());
            Files.createDirectories(list.getParent());
            Path writing = list.resolveSibling(list.getFileName() + ".writing");
            Files.write(writing, lines, StandardCharsets.UTF_8);
            Files.move(writing, list, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | UnsupportedOperationException couldNotWrite) {
            LOGGER.error("Could not write {}: {}", file, couldNotWrite.getMessage());
            return false;
        }
    }

    /** Takes a player off the list, and says whether they were on it. */
    boolean remove(UUID player) {
        Path list = path().orElse(null);
        if (list == null || player == null || !Files.isRegularFile(list)) {
            return false;
        }
        try {
            String wanted = player.toString();
            List<String> kept = new ArrayList<>();
            boolean found = false;
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                if (wanted.equals(line.strip())) {
                    found = true;
                } else {
                    kept.add(line);
                }
            }
            if (found) {
                // Through a temporary file and a move, which is what this class's own doc says it
                // does - and what adding to the list does. Taking somebody off wrote in place, so a
                // crash mid-write left half a list rather than the old one.
                Path partly = list.resolveSibling(list.getFileName() + ".writing");
                Files.write(partly, kept, StandardCharsets.UTF_8);
                try {
                    Files.move(partly, list, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException notHere) {
                    Files.move(partly, list, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return found;
        } catch (IOException couldNotWrite) {
            LOGGER.error("Could not take a player off {}: {}", file, couldNotWrite.getMessage());
            return false;
        }
    }

    private Optional<Path> path() {
        return ServerRun.inSave(folder).map(dir -> dir.resolve(file));
    }
}
