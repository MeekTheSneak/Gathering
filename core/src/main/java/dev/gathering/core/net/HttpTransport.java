package dev.gathering.core.net;

import java.io.IOException;
import java.util.Map;

/**
 * The one place the pure core touches the network, expressed as an interface so it can be
 * replaced by a fake in tests and by a JDK client in the game.
 *
 * <p>Keeping this abstract is what lets the entire Scryfall layer - batching, rate
 * limiting, error handling, codec - be unit tested in milliseconds with no sockets.
 */
public interface HttpTransport {

    HttpReply get(String url, Map<String, String> headers) throws IOException;

    HttpReply post(String url, String body, Map<String, String> headers) throws IOException;

    /** A response reduced to what the client actually reasons about. */
    record HttpReply(int status, String body) {

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        /** Server-side trouble, worth retrying; a 404 is an answer, not a failure. */
        public boolean isRetryable() {
            return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }
    }
}
