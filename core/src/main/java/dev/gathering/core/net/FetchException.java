package dev.gathering.core.net;

import java.io.IOException;

/**
 * A fetch that could not be completed.
 *
 * <p>Always recoverable: everything that reaches out from this mod - card data, deck sites,
 * collation - does so on behalf of somebody looking at a screen, and a failure is a sentence
 * for them rather than a crash. The message is written to be read by that person.
 */
public class FetchException extends IOException {

    private final int status;

    public FetchException(String message, int status) {
        super(message);
        this.status = status;
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** The HTTP status that caused this, or -1 if the request never got that far. */
    public int status() {
        return status;
    }
}
