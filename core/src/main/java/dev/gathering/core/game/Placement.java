package dev.gathering.core.game;

/**
 * Where in a zone a card lands.
 *
 * <p>Only libraries really care, but "put it on the bottom" has to be expressible for scry
 * and for the send-to-library verbs, and it costs nothing to let every zone accept it.
 */
public enum Placement {
    TOP,
    BOTTOM;

    public boolean isTop() {
        return this == TOP;
    }
}
