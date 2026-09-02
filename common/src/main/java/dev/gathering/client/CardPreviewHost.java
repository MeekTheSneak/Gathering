package dev.gathering.client;

/**
 * A screen that draws its own card preview, and so wants the global overlay to stay out.
 * <p>The read-a-card overlay is drawn from each loader's after-screen hook, over whatever
 * screen is open. That is right everywhere except a screen that has already found room for
 * a preview of its own: there the overlay would darken the whole screen and cover the very
 * list the player is reading from. A marker rather than a flag, because the answer never
 * changes over a screen's life.
 * <p>Client-only.
 */
public interface CardPreviewHost {
}
