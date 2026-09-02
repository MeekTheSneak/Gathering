package dev.gathering.core.game;

/**
 * What the undo rules make of a rewind request.
 * <p>Three outcomes rather than a boolean, because "everyone has to agree" is a genuinely
 * different answer from "no" and the table needs to be able to ask.
 */
public sealed interface UndoDecision {

    /** Goes ahead now. */
    record Allowed() implements UndoDecision {
    }

    /** Needs every seated player to say yes first. */
    record NeedsUnanimousConsent(String reason) implements UndoDecision {
    }

    /** Not happening, however many people agree. */
    record Denied(String reason) implements UndoDecision {
    }

    static UndoDecision allowed() {
        return new Allowed();
    }

    static UndoDecision needsUnanimousConsent(String reason) {
        return new NeedsUnanimousConsent(reason);
    }

    static UndoDecision denied(String reason) {
        return new Denied(reason);
    }

    default boolean isAllowed() {
        return this instanceof Allowed;
    }

    default boolean needsConsent() {
        return this instanceof NeedsUnanimousConsent;
    }
}
