package dev.gathering.core.game;

/**
 * A seat currently looking through somebody's library.
 * <p>A library is a count to everybody, its owner included - that is section 6's table and it
 * is not negotiable. Looking at one is a <em>thing you do</em>: a search, a scry, a surveil.
 * Each is an event, each announces itself in the log, and each opens the library to exactly
 * one seat until something closes it again.
 * <p>Modeling it this way rather than as a client-side "search screen" is the whole point. If
 * the looking were the client's business, the server would have to send the library to a
 * client that might be lying about looking at it, and the answer to "who can see this
 * library" would live in a screen rather than in the rules. Here it lives in the fold of the
 * log, which means it survives undo, it survives a reconnect, and it is checkable: the
 * property suite asserts a library reaches exactly the seat the log says is looking at it.
 *
 * @param at    whose library is open
 * @param depth how many cards from the top, or {@link #ALL} for the whole thing
 */
public record Peek(SeatId at, int depth) {

    /** A search: the whole library, in order, which is what pulling a deck out of a box is. */
    public static final int ALL = -1;

    public Peek {
        if (at == null) {
            throw new IllegalArgumentException("A peek needs a library to be at");
        }
        if (depth < ALL) {
            depth = ALL;
        }
    }

    public static Peek search(SeatId at) {
        return new Peek(at, ALL);
    }

    public static Peek top(SeatId at, int count) {
        return new Peek(at, Math.max(0, count));
    }

    public boolean isWholeLibrary() {
        return depth == ALL;
    }

    /** How many cards of a library of this size are actually open. */
    public int visibleCount(int librarySize) {
        return isWholeLibrary() ? librarySize : Math.min(depth, librarySize);
    }
}
