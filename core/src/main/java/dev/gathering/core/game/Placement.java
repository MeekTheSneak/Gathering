package dev.gathering.core.game;

/**
 * Where in a zone a card lands.
 *
 * <p>Two kinds of zone want two different answers, so this is a sealed type rather than a
 * pair of booleans. Piles - libraries, graveyards - care about order and not about geometry:
 * top or bottom is the whole vocabulary. The battlefield is the reverse: it is a surface, and
 * what matters is the spot a card was dropped on.
 *
 * <p>Sealed so that adding a third kind later is a compile error everywhere it needs to be
 * thought about, rather than a silent default.
 */
public sealed interface Placement {

    /** Onto the top of a pile. For a surface, wherever the board has room to fan one out. */
    record Top() implements Placement {
    }

    /** Onto the bottom of a pile. For a surface, wherever the board has room to fan one out. */
    record Bottom() implements Placement {
    }

    /** Onto a specific spot on the surface, which is what a drag produces. */
    record At(TablePosition position) implements Placement {

        public At {
            if (position == null) {
                throw new IllegalArgumentException("A placement needs a position");
            }
        }
    }

    Placement TOP = new Top();
    Placement BOTTOM = new Bottom();

    static Placement at(TablePosition position) {
        return new At(position);
    }

    static Placement at(int x, int y) {
        return new At(TablePosition.of(x, y));
    }

    /** Onto a fraction of the way across and down the surface, which is how a screen thinks. */
    static Placement atFraction(double across, double down) {
        return new At(TablePosition.fraction(across, down));
    }

    default boolean isTop() {
        return this instanceof Top;
    }

    /** The spot this placement names, if it names one. */
    default java.util.Optional<TablePosition> chosenPosition() {
        return this instanceof At at ? java.util.Optional.of(at.position()) : java.util.Optional.empty();
    }

    /** A short label for the event log: "top", "bottom", or the spot. */
    default String label() {
        return switch (this) {
            case Top ignored -> "top";
            case Bottom ignored -> "bottom";
            case At at -> at.position().toString();
        };
    }
}
