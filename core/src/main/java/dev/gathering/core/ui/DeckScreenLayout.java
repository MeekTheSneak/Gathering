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
        Rect lands,
        Rect done,
        Rect sleeves,
        Rect gather,
        Rect card,
        Rect info) {

    /**
     * Where the panel's right edge sits at the top and at the bottom, as fractions of the
     * rectangle it is drawn into.
     *
     * <p>Shared with {@code tools/gui_art.py}, which draws the taper into
     * {@code deck_panel.png}. A theme that replaces that texture keeps these, or the
     * scrollbar stops sitting on the edge it is drawn along.
     *
     * <p>The top is deliberately short of the full width. The edge line and the shadow
     * outside it need somewhere to go, and at 1.0 they fall off the right of the texture -
     * which is not a subtle artifact, it is the top corner of the panel arriving unfinished.
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


    /** The frame border a card or its text sits inside. */
    public static final int FRAME = 6;

    private static final int CARD_MIN = 96;
    private static final int CARD_MAX = 420;
    private static final int INFO_MIN = 140;

    /** One hint line per mouse button. */
    public static final int HINT_LINES = 2;

    /** How tall the row of basic-land buttons is. One button, and they are small ones. */
    public static final int LAND_HEIGHT = 14;

    /**
     * Six, one per basic land, Wastes included. This was five, which was the layout quietly
     * deciding a colorless deck could not have its lands - the list itself lives in
     * {@link dev.gathering.core.card.BasicLand} and this only has to match its length.
     */
    public static final int LAND_BUTTONS = dev.gathering.core.card.BasicLand.values().length;

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
        // Two buttons on the bottom row, sharing it. Done keeps its own width where there is
        // room for both at full size and gives half the row away where there is not, because
        // the choice is between two buttons that fit and one that does not: a deck whose
        // sleeves can only be picked on a wide window is a deck most people cannot sleeve.
        int row = Math.max(1, contentWidth - GAP);
        // Not down the middle. "Done" is one short word and the other button's label is three
        // times as long, so an even split leaves one button half empty and the other with its
        // text touching both edges - which is what shipped, and it read as a mistake.
        int doneWidth = Math.min(BUTTON_WIDTH, row * 2 / 5);
        Rect done = new Rect(PAD, buttonTop, doneWidth, BUTTON_HEIGHT);
        // Beside Done rather than above it, because it belongs to the deck as a whole the way
        // the name does - and because the rows above are the cards, which is what the rest of
        // this panel is for.
        int sleevesLeft = done.right() + GAP;
        Rect sleeves = new Rect(sleevesLeft, buttonTop,
                Math.max(1, PAD + contentWidth - sleevesLeft), BUTTON_HEIGHT);
        // Two lines, one per mouse button. One line has to say what both buttons do, and in
        // a panel this narrow that is more text than there is room for - it shrank to the
        // smallest readable size and was then cut off anyway.
        int hintHeight = line * HINT_LINES;
        int rowsTop = title.bottom() + GAP;

        // A row of its own above the other two, because its label is a sentence and because
        // what it does is neither of the things they do: it is the way in to picking cards
        // out of your own pockets, which before this was a right-click per stack.
        //
        // The first thing to go when the window is short. Everything above it is the deck
        // itself and every one of those has to be there; this one has another way in, at the
        // collection block, so a panel too short for both keeps the cards.
        Rect gather = new Rect(
                PAD, buttonTop - GAP - BUTTON_HEIGHT, contentWidth, BUTTON_HEIGHT);
        Rect hint = new Rect(PAD, gather.y() - GAP - hintHeight, contentWidth, hintHeight);
        Rect lands = new Rect(PAD, hint.y() - GAP - LAND_HEIGHT, contentWidth, LAND_HEIGHT);
        if (lands.y() - GAP - rowsTop < line) {
            gather = Rect.NONE;
            hint = new Rect(PAD, buttonTop - GAP - hintHeight, contentWidth, hintHeight);
            lands = new Rect(PAD, hint.y() - GAP - LAND_HEIGHT, contentWidth, LAND_HEIGHT);
        }

        Rect rows = new Rect(PAD, rowsTop, contentWidth, Math.max(line, lands.y() - GAP - rowsTop));
        // Anchored to where the edge is at the top, because the shear that lays the bar along
        // the taper carries it the rest of the way in. Anchoring it to the bottom edge and
        // then shearing it as well tapers it twice, which walks it off the panel.
        int scrollLeft = Math.round(panelWidth * TAPER_TOP) - edge - SCROLL_WIDTH;
        Rect scrollbar = new Rect(scrollLeft, rows.y(), SCROLL_WIDTH, rows.height());

        return new DeckScreenLayout(
                panel, title, rows, scrollbar, hint, lands, done, sleeves, gather,
                cardOf(panel, width, height), infoOf(panel, width, height));
    }

    /**
     * Where the nth basic-land button goes, so the screen and anything checking it agree.
     *
     * <p>Evenly across the strip rather than at a fixed width: the panel is a fraction of
     * the window, so on a small one the five buttons have to share whatever there is.
     */
    public Rect landButton(int index) {
        if (index < 0 || index >= LAND_BUTTONS || lands.isEmpty()) {
            return Rect.NONE;
        }
        int gap = 2;
        int each = Math.max(1, (lands.width() - gap * (LAND_BUTTONS - 1)) / LAND_BUTTONS);
        return new Rect(lands.x() + index * (each + gap), lands.y(), each, lands.height());
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
        int cardHeight = Math.min(tall, CardShape.heightFor(wanted - FRAME * 2) + FRAME * 2);
        int cardWidth = CardShape.widthFor(cardHeight - FRAME * 2) + FRAME * 2;
        if (cardWidth > available) {
            cardWidth = available;
            cardHeight = CardShape.heightFor(cardWidth - FRAME * 2) + FRAME * 2;
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
