package dev.gathering.core.config;

/**
 * What is wrong with a config file, and which line it is on.
 * <p>Checked, because a server owner editing a config file is the person this message is for
 * and the caller has a real decision to make: say so in the log and start with defaults, or
 * refuse to start. Never a silent shrug - a setting that was quietly ignored is a server
 * running differently from the file its owner is reading.
 */
public class TomlException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int line;

    public TomlException(int line, String message) {
        super(line > 0 ? "line " + line + ": " + message : message);
        this.line = line;
    }

    /** The line it is on, or 0 if the trouble is with the file as a whole. */
    public int line() {
        return line;
    }
}
