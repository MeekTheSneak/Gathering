package dev.gathering.client;

/**
 * A screen that draws its own card preview, and so wants the global overlay to stay out.
 * <p>The read-a-card overlay is drawn from each loader's after-screen hook, over whatever
 * screen is open. That is right everywhere except a screen that has already found room for
 * a preview of its own: there a second copy chasing the cursor would cover the very list the
 * player is reading from.
 * <p>Only as far as the preview goes, though. The owner: "the alt screen that opens when
 * hovering over a card should have the entire card's text ... No matter what screen it is
 * in." Four screens carried this marker while drawing a picture or nothing, so the read key
 * showed no words at all in them. A screen whose preview is only the picture, or is not
 * there at this window size, says so, and the overlay brings what is missing.
 * <p>Client-only.
 */
public interface CardPreviewHost {

    /** Whether this screen's own preview is showing everything the card says, right now. */
    default boolean previewsTheText() {
        return true;
    }

    /** Whether it is showing the card's picture, right now. */
    default boolean previewsTheArt() {
        return true;
    }
}
