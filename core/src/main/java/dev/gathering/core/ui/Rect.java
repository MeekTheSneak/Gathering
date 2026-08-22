package dev.gathering.core.ui;

/**
 * A rectangle in GUI-scaled screen coordinates.
 *
 * <p>Plain integers with no Minecraft in sight, so screen layout can be worked out - and
 * checked at every window size anyone might have - without a game running.
 */
public record Rect(int x, int y, int width, int height) {

    public static final Rect NONE = new Rect(0, 0, 0, 0);

    public Rect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    public boolean overlaps(Rect other) {
        if (isEmpty() || other.isEmpty()) {
            return false;
        }
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    /** The same rectangle with every edge pulled in by {@code inset}. */
    public Rect shrink(int inset) {
        return new Rect(x + inset, y + inset, width - inset * 2, height - inset * 2);
    }
}
