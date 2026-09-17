package dev.gathering.core.ui;

/**
 * Where the card overview puts things: the card as large as the window allows, its words beside it,
 * its history under the words, and a row of buttons along the bottom.
 * <p>Side by side where the window is wide enough for the words to be read beside the card, and the
 * card on top of them where it is not - a phone-shaped window or a large GUI scale. Pure.
 */
public record CardOverviewLayout(Rect card, Rect words, Rect history, Rect buttons) {

    public static final int MARGIN = 12;
    public static final int GAP = 8;

    /** A card is 2.5 by 3.5 inches. */
    private static final double TALL_PER_WIDE = 3.5 / 2.5;

    /** The narrowest the words may be beside the card before they go under it instead. */
    public static final int NARROWEST_WORDS = 160;

    /**
     * @param width   the window, in GUI pixels
     * @param height  the window, in GUI pixels
     * @param row     how tall a button is at the control size asked
     */
    public static CardOverviewLayout of(int width, int height, int row) {
        int left = MARGIN;
        int top = MARGIN;
        int right = Math.max(left + 1, width - MARGIN);
        int bottom = Math.max(top + 1, height - MARGIN);
        int buttonsTop = Math.max(top, bottom - row);
        Rect buttons = new Rect(left, buttonsTop, right - left, Math.min(row, bottom - top));
        int contentBottom = Math.max(top, buttonsTop - GAP);
        int contentHeight = contentBottom - top;
        int contentWidth = right - left;

        // Beside: the card takes up to two fifths of the width, as tall as the content allows.
        int cardWide = (int) Math.min(contentWidth * 0.4, contentHeight / TALL_PER_WIDE);
        int besideWords = contentWidth - cardWide - GAP;
        if (besideWords >= NARROWEST_WORDS && cardWide > 0) {
            int cardTall = (int) Math.round(cardWide * TALL_PER_WIDE);
            Rect card = new Rect(left, top, cardWide, Math.min(cardTall, contentHeight));
            int wordsLeft = card.right() + GAP;
            int wordsTall = Math.max(0, (contentHeight - GAP) * 11 / 20);
            Rect words = new Rect(wordsLeft, top, right - wordsLeft, wordsTall);
            int historyTop = words.bottom() + GAP;
            Rect history = new Rect(wordsLeft, historyTop, right - wordsLeft, Math.max(0, contentBottom - historyTop));
            return new CardOverviewLayout(card, words, history, buttons);
        }
        // Stacked: a smaller card at the top left, the words beside it where there is any room and
        // the history under both.
        int cardTall = Math.max(0, contentHeight * 2 / 5);
        cardWide = Math.min(contentWidth / 2, (int) (cardTall / TALL_PER_WIDE));
        cardTall = (int) Math.min(cardTall, Math.round(cardWide * TALL_PER_WIDE));
        Rect card = new Rect(left, top, Math.max(0, cardWide), Math.max(0, cardTall));
        int wordsLeft = card.right() + GAP;
        Rect words = new Rect(wordsLeft, top, Math.max(0, right - wordsLeft), card.height());
        int historyTop = card.bottom() + GAP;
        Rect history = new Rect(left, historyTop, contentWidth, Math.max(0, contentBottom - historyTop));
        return new CardOverviewLayout(card, words, history, buttons);
    }
}
