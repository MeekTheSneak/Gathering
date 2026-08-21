package dev.gathering.core.card;

/**
 * Image sizes, with the two the texture budget is built around called out.
 *
 * <p>{@link #SMALL} (146x204) is the table miniature tier; {@link #NORMAL} (488x680) is
 * the overlay and GUI tier. A four-player Commander game touches roughly 450 distinct
 * cards, which at these sizes stays well inside the LRU cap.
 */
public enum ImageTier {
    SMALL(146, 204),
    NORMAL(488, 680),
    LARGE(672, 936),
    PNG(745, 1040),
    ART_CROP(0, 0),
    BORDER_CROP(480, 680);

    private final int width;
    private final int height;

    ImageTier(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
