package dev.gathering.core.ui;

/**
 * Where everything goes on the deck screen, at whatever size the window happens to be.
 *
 * <p>A GUI laid out from constants looks right at the size it was written at and wrong
 * everywhere else, and "everywhere else" includes the smallest window Minecraft allows
 * (320x240 in GUI-scaled units) and every GUI scale between 1 and 4. So the arithmetic lives
 * here, in the pure module, where it can be checked against every size rather than against
 * the one the author happened to be running.
 *
 * <p>The shape, from the design: the decklist is a panel flush against the left edge, its
 * right side tapering inward, with a scrollbar running down that tapered edge. To the right
 * of it sit two framed boxes, the hovered card and its text. As the window narrows the text
 * box goes first, then the card, then the screen is a decklist and nothing else - which is
 * still the thing the screen is for.
 *
 * <p>Coordinates are GUI-scaled screen units, origin top left.
 */
public record DeckScreenLayout(
        Rect panel,
        Rect title,
        Rect rows,
        Rect scrollbar,
        Rect hint,
        Rect done,
        Rect card,
        Rect info) {

    /**
     * Where the panel's right edge sits at the top and at the bottom, as fractions of the
     * rectangle it is drawn into.
     *
     * <p>Shared with {@code tools/gui_textures.py}, which draws the taper into
     * {@code deck_panel.png}. A theme that replaces that texture keeps these, or the
     * scrollbar stops sitting on the edge it is drawn along.
     *
     * <p>The top is deliberately short of the full width. The edge line and the shadow
     * outside it need somewhere to go, and at 1.0 they fall off the right of the texture -
     * which is not a subtle artefact, it is the top corner of the panel arriving unfinished.
     */
    public static final float TAPER_TOP = 0.90f;
    public static final float TAPER_BOTTOM = 0.74f;

    /** Width of the edge line down the taper, as a fraction of panel width. */
    public static final float EDGE_FRACTION = 0.010f;

    private static final int PAD = 8;
    private static final int GAP = 8;
    private static final int MARGIN = 8;

    private static final float PANEL_FRACTION = 0.30f;
    private static final int PANEL_MIN = 150;
    private static final int PANEL_MAX = 320;

    private static final int SCROLL_WIDTH = 6;

    /** The printed aspect ratio, 2.5 by 3.5 inches. */
    private static final float CARD_ASPECT = 488f / 680f;

    /** The frame border a card or its text sits inside. */
    public static final int FRAME = 6;

    private static final int CARD_MIN = 96;
    private static final int CARD_MAX = 420;
    private static final int INFO_MIN = 140;

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 72;

    /**
     * @param lineHeight the font's line height, which is the one thing about the layout that
     *                   this module cannot know on its own
     */
    public static DeckScreenLayout of(int screenWidth, int screenHeight, int lineHeight) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);
        int line = Math.max(1, lineHeight);

        // The panel runs off the top, bottom and left of the screen, as a backdrop rather
        // than a box sitting on one.
        int panelWidth = clamp(Math.round(width * PANEL_FRACTION), PANEL_MIN, PANEL_MAX);
        panelWidth = Math.min(panelWidth, width);
        Rect panel = new Rect(0, 0, panelWidth, height);

        // Everything inside has to fit the narrowest part of the taper, which is the bottom.
        int narrow = Math.round(panelWidth * TAPER_BOTTOM);
        int edge = Math.max(1, Math.round(panelWidth * EDGE_FRACTION));
        int contentRight = Math.max(PAD + 1, narrow - edge - SCROLL_WIDTH - 2);
        int contentWidth = contentRight - PAD;

        Rect title = new Rect(PAD, PAD, contentWidth, line);

        int buttonTop = height - PAD - BUTTON_HEIGHT;
        Rect done = new Rect(PAD, buttonTop, Math.min(BUTTON_WIDTH, contentWidth), BUTTON_HEIGHT);
        Rect hint = new Rect(PAD, buttonTop - GAP - line, contentWidth, line);

        int rowsTop = title.bottom() + GAP;
        Rect rows = new Rect(PAD, rowsTop, contentWidth, Math.max(line, hint.y() - GAP - rowsTop));
        // Anchored to where the edge is at the top, because the shear that lays the bar along
        // the taper carries it the rest of the way in. Anchoring it to the bottom edge and
        // then shearing it as well tapers it twice, which walks it off the panel.
        int scrollLeft = Math.round(panelWidth * TAPER_TOP) - edge - SCROLL_WIDTH;
        Rect scrollbar = new Rect(scrollLeft, rows.y(), SCROLL_WIDTH, rows.height());

        return new DeckScreenLayout(
                panel, title, rows, scrollbar, hint, done,
                cardOf(panel, width, height), infoOf(panel, width, height));
    }

    private static Rect cardOf(Rect panel, int width, int height) {
        int left = panel.right() + GAP;
        int available = width - left - MARGIN;
        int tall = height - MARGIN * 2;
        if (available < CARD_MIN || tall < CARD_MIN) {
            return Rect.NONE;
        }

        // Half the space when the text box is coming too, all of it when it is not.
        boolean withInfo = available >= CARD_MIN + GAP + INFO_MIN;
        int wanted = clamp(withInfo ? Math.round(available * 0.46f) : available, CARD_MIN, CARD_MAX);

        // Fix the height to the card's own proportions, then take the width back from it, so
        // the art inside is never stretched whichever dimension ran out first.
        int cardHeight = Math.min(tall, Math.round((wanted - FRAME * 2) / CARD_ASPECT) + FRAME * 2);
        int cardWidth = Math.round((cardHeight - FRAME * 2) * CARD_ASPECT) + FRAME * 2;
        if (cardWidth > available) {
            cardWidth = available;
            cardHeight = Math.round((cardWidth - FRAME * 2) / CARD_ASPECT) + FRAME * 2;
        }
        if (cardWidth < CARD_MIN || cardHeight < CARD_MIN || cardHeight > tall) {
            return Rect.NONE;
        }
        return new Rect(left, (height - cardHeight) / 2, cardWidth, cardHeight);
    }

    private static Rect infoOf(Rect panel, int width, int height) {
        Rect card = cardOf(panel, width, height);
        if (card.isEmpty()) {
            // Never text without the card it describes: the card is the thing being read.
            return Rect.NONE;
        }
        int left = card.right() + GAP;
        int infoWidth = width - MARGIN - left;
        return infoWidth < INFO_MIN ? Rect.NONE : new Rect(left, card.y(), infoWidth, card.height());
    }

    /**
     * How far in the panel's right edge has come at this height, in screen coordinates.
     *
     * <p>What the scrollbar is drawn along, so it sits on the edge rather than beside it.
     */
    public int edgeAt(int y) {
        return Math.round(panel.width() * TAPER_TOP + taperSlope() * y);
    }

    /**
     * How far left a point slides for every unit it goes down the panel.
     *
     * <p>The scrollbar is drawn and hit-tested as an upright bar under a shear by exactly
     * this much, which is how it and the texture's edge stay on the same line without two
     * separate pieces of arithmetic having to agree.
     */
    public float taperSlope() {
        return panel.height() == 0 ? 0f : panel.width() * (TAPER_BOTTOM - TAPER_TOP) / panel.height();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
