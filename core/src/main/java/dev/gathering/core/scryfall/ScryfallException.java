package dev.gathering.core.scryfall;

import java.io.IOException;

/** A Scryfall request that could not be completed. Always recoverable at the import screen. */
public class ScryfallException extends IOException {

    private final int status;

    public ScryfallException(String message, int status) {
        super(message);
        this.status = status;
    }

    public ScryfallException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** The HTTP status that caused this, or -1 if the request never got that far. */
    public int status() {
        return status;
    }
}
