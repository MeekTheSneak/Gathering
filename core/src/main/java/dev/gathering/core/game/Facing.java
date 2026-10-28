package dev.gathering.core.game;

/** Whether a card is showing its face. */
public enum Facing {
    FACE_UP,
    FACE_DOWN;

    public Facing flipped() {
        return this == FACE_UP ? FACE_DOWN : FACE_UP;
    }

    public boolean isFaceDown() {
        return this == FACE_DOWN;
    }
}
