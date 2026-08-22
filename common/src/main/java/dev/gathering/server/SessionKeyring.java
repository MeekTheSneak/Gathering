package dev.gathering.server;

import dev.gathering.core.game.persistence.SessionKeys;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This server's session key, loaded once.
 *
 * <p>Every table needs it to save or reopen a game, and a table asks at world-load time, so
 * it is read from disk once and held rather than reread per block entity.
 *
 * <p>A key that cannot be loaded is reported as absent rather than thrown, because the
 * consequence has to be "these sessions will not open" and never "this world will not load".
 * Losing a game is bad; losing a world because a game could not be read is worse.
 */
public final class SessionKeyring {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static volatile SecretKey key;
    private static volatile boolean tried;

    private SessionKeyring() {
    }

    public static synchronized Optional<SecretKey> key() {
        if (tried) {
            return Optional.ofNullable(key);
        }
        tried = true;
        try {
            key = SessionKeys.load(Platform.get().configDirectory());
        } catch (IOException | RuntimeException e) {
            // Never the key itself, and never a stack trace that might carry it.
            LOGGER.error("Could not load the session key, so saved games will not open: {}",
                    e.getMessage());
            key = null;
        }
        return Optional.ofNullable(key);
    }

    /** Called when a server stops, so the next one in this JVM re-reads its own. */
    public static synchronized void forget() {
        key = null;
        tried = false;
    }
}
