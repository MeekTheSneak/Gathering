package dev.gathering.core.ui;

/**
 * Where the card and its words go when a player is reading one.
 * <p>The full-window read: the card down the left at as close to the height of the window as
 * a card can be, and everything it says beside it. That is the shape somebody already has in
 * their hands - you hold a card up and read down it - and it is why this is a two-column
 * layout rather than a card with a caption. A card drawn small so its text has room is a
 * reading tool that has got the priority backwards: the picture is the card.
 * <p>The words get whatever is left, down to a floor. Past that floor the card gives way
 * instead, because a column too narrow to hold a line of oracle text is a column that has
 * stopped being text and started being a shape - and a window that narrow is a window where
 * the card was never going to be readable at full height either.
 * <p>Pure, so the arithmetic is checked in milliseconds rather than looked at in a
 * screenshot. Which matters here more than usual: this is drawn over a running game at every
 * window size a player might have, and the failure mode is a column of text off the edge of
 * the screen that nobody sees until somebody plays at 4:3.
 */
public record InspectLayout(Rect card, Rect text) {

    /** How much of the window's height a card fills when there is room for it. */
    private static final double CARD_SHARE = 0.86d;

    /** The air around the whole thing, as a share of the window's width. */
    private static final double MARGIN_SHARE = 0.05d;

    /** Between the card and its words. Generous: they are two things, not one. */
    private static final double GAP_SHARE = 0.035d;

    /** Narrower than this and a line of oracle text stops being a line. */
    public static final int NARROWEST_TEXT = 150;

    /** And this is as small as the card is allowed to get to give the text that width. */
    public static final int SHORTEST_CARD = 120;

    /**
     * The two boxes, for a window this size.
     * <p>Centered as a pair rather than each in its own half, so a card with little to say and
     * a card with a lot are drawn in the same place - a picture that jumps sideways depending
     * on how wordy the card is reads as the interface being unsure of itself.
     */
    public static InspectLayout of(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return new InspectLayout(Rect.NONE, Rect.NONE);
        }
        int margin = (int) Math.round(screenWidth * MARGIN_SHARE);
        int gap = Math.max(8, (int) Math.round(screenWidth * GAP_SHARE));
        int room = Math.max(0, screenWidth - margin * 2);

        int cardHeight = (int) Math.round(screenHeight * CARD_SHARE);
        int cardWidth = CardShape.widthFor(cardHeight);

        // The card gives way before the text does, down to a floor of its own. A window too
        // small for both is one where the card wins what is left, because a card and no words
        // is still a card and words with no card is a tooltip.
        int wanted = cardWidth + gap + NARROWEST_TEXT;
        if (wanted > room) {
            int shorter = Math.max(0, room - gap - NARROWEST_TEXT);
            cardWidth = Math.max(0, Math.min(cardWidth, shorter));
            cardHeight = CardShape.heightFor(cardWidth);
            if (cardHeight < SHORTEST_CARD) {
                cardHeight = Math.min(SHORTEST_CARD, (int) Math.round(screenHeight * CARD_SHARE));
                cardWidth = CardShape.widthFor(cardHeight);
            }
        }
        int textWidth = Math.max(0, room - cardWidth - gap);
        int wide = cardWidth + gap + textWidth;
        int left = margin + Math.max(0, (room - wide) / 2);
        int top = Math.max(0, (screenHeight - cardHeight) / 2);

        return new InspectLayout(
                new Rect(left, top, cardWidth, cardHeight),
                new Rect(left + cardWidth + gap, top, textWidth, cardHeight));
    }
}
