package dev.gathering.core.game.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import javax.crypto.SecretKey;

/**
 * The key that seals sessions, kept where the sessions are not.
 * <p>The design's requirement is that the key is sealed server-side and never stored beside
 * the data. So it lives in the server's own configuration directory while sessions live in
 * the world save - copy a world folder and you have taken the ciphertext and left the key
 * behind, which is the case this is for: a save file opened with external tools by somebody
 * who was never at the table.
 * <p>One key per server rather than one per world. A per-world key would have to be stored
 * somewhere that says which world it belongs to, and the obvious place to put that is beside
 * the world.
 * <p>What this does not defend against, stated plainly: whoever runs the server has both
 * halves. That is true of every online card game, and the design says so.
 */
public final class SessionKeys {

    public static final String FILE_NAME = "session.key";

    private SessionKeys() {
    }

    /**
     * The server's session key, generated on first use.
     * <p>Created read-and-write for its owner only where the filesystem has an opinion about
     * that. Where it does not - Windows, most notably - the file is still only as protected
     * as the directory it is in, which is the same protection the world save has.
     */
    public static SecretKey load(Path directory) throws IOException {
        Path file = directory.resolve(FILE_NAME);
        if (Files.isRegularFile(file)) {
            byte[] raw = Files.readAllBytes(file);
            try {
                return SessionCipher.keyFrom(raw);
            } catch (IllegalArgumentException e) {
                // Refusing beats silently generating a new one: a new key makes every stored
                // session on this server permanently unopenable, which is not a thing to do
                // quietly on somebody's behalf.
                throw new IOException("The session key at " + file + " is not a valid key. "
                        + "Move it aside to start again, but every stored session sealed with "
                        + "it will no longer open.", e);
            }
        }

        Files.createDirectories(directory);
        SecretKey key = SessionCipher.newKey();
        Files.write(file, SessionCipher.rawOf(key),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        restrictToOwner(file);
        return key;
    }

    private static void restrictToOwner(Path file) {
        try {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (IOException | UnsupportedOperationException e) {
            // Best effort. A key that exists with default permissions beats no key at all,
            // and the directory it is in is already no more exposed than the world save.
        }
    }
}
