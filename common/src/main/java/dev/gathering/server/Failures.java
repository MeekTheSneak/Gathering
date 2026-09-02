package dev.gathering.server;

/**
 * What to tell a player about an exception.
 * <p>The root cause's message, because the wrapping layers name executors and futures and the
 * root names the thing that went wrong. One copy: three pack handlers each carried their own,
 * and an error message players actually read is a rule that must not drift.
 */
public final class Failures {

    private Failures() {
    }

    public static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
