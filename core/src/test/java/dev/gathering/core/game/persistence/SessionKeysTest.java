package dev.gathering.core.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionKeysTest {

    @Test
    @DisplayName("the same server gets the same key, so yesterday's game still opens")
    void theKeyIsStable(@TempDir Path directory) throws Exception {
        SecretKey first = SessionKeys.load(directory);
        byte[] sealed = SessionCipher.seal(first, new byte[] {4, 5, 6});

        SecretKey again = SessionKeys.load(directory);

        assertThat(SessionCipher.open(again, sealed)).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("two servers get different keys")
    void keysAreNotShared(@TempDir Path one, @TempDir Path two) throws Exception {
        assertThat(SessionCipher.rawOf(SessionKeys.load(one)))
                .isNotEqualTo(SessionCipher.rawOf(SessionKeys.load(two)));
    }

    @Test
    @DisplayName("a damaged key file is refused, never quietly replaced")
    void aDamagedKeyIsRefused(@TempDir Path directory) throws IOException {
        // Generating a fresh one would make every session already stored on this server
        // permanently unopenable, which is not a thing to do quietly on somebody's behalf.
        Files.write(directory.resolve(SessionKeys.FILE_NAME), new byte[] {1, 2, 3});

        assertThatThrownBy(() -> SessionKeys.load(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no longer open");
    }

    @Test
    @DisplayName("the key is not readable by anyone else, where the filesystem cares")
    void theKeyIsOwnerOnly(@TempDir Path directory) throws IOException {
        SessionKeys.load(directory);
        Path file = directory.resolve(SessionKeys.FILE_NAME);
        if (!file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }

        assertThat(Files.getPosixFilePermissions(file))
                .containsExactlyInAnyOrder(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
    }
}
