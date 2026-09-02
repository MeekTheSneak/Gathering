package dev.gathering.core.format;

/**
 * Something the deck check found.
 * <p>Severity matters because the server config decides what to do about a failure: block
 * the game, or warn and let the table owner override. A validator that only said yes or no
 * would make that config impossible.
 */
public record ValidationIssue(ValidationIssue.Severity severity, String code, String message) {

    public enum Severity {
        /** The deck does not meet the format. */
        ERROR,
        /** Probably fine, but worth saying - most often that we have no legality data. */
        WARNING
    }

    public static ValidationIssue error(String code, String message) {
        return new ValidationIssue(Severity.ERROR, code, message);
    }

    public static ValidationIssue warning(String code, String message) {
        return new ValidationIssue(Severity.WARNING, code, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + ": " + message;
    }
}
