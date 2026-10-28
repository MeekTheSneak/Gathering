package dev.gathering.core.decklist;

/**
 * A line the parser could not turn into an entry.
 * <p>Problems accumulate rather than throwing. A decklist with one bad line should import
 * the other ninety-nine and show the importer exactly which line to fix, at its line
 * number, with its original text.
 */
public record ParseProblem(int lineNumber, String sourceLine, String reason) {

    @Override
    public String toString() {
        return "line " + lineNumber + ": " + reason + " (" + sourceLine + ")";
    }
}
