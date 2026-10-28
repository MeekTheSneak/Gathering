package dev.gathering.core.ui;

/**
 * A rectangle in GUI-scaled screen coordinates.
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

    public double centerX() {
        return x + width / 2.0;
    }

    public double centerY() {
        return y + height / 2.0;
    }

    /**
     * Whether a point is on this rectangle after it has been turned about its center.
     * <p>A card lying at an angle is still a card-shaped thing, so clicking it means clicking
     * the card and not its bounding box: turn a card forty-five degrees and a quarter of that
     * box is table, where a click should reach whatever is underneath. This spins the point
     * back instead of the rectangle forwards, which is the same test and one rotation rather
     * than four.
     */
    public boolean containsTurned(int degrees, int pointX, int pointY) {
        if (isEmpty()) {
            return false;
        }
        if (Math.floorMod(degrees, 360) == 0) {
            return contains(pointX, pointY);
        }
        double radians = Math.toRadians(-Math.floorMod(degrees, 360));
        double offsetX = pointX - centerX();
        double offsetY = pointY - centerY();
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double localX = offsetX * cos - offsetY * sin + centerX();
        double localY = offsetX * sin + offsetY * cos + centerY();
        return localX >= x && localX < right() && localY >= y && localY < bottom();
    }
}
