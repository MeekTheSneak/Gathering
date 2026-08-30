package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.collection.CollectionSearch;
import dev.gathering.core.ui.CardShape;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import dev.gathering.network.CollectionPagePayload;
import dev.gathering.network.CollectionQuery;
import dev.gathering.network.CollectionSearchPayload;
import dev.gathering.network.CollectionTakePayload;
import dev.gathering.network.OpenCollectionPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * A collection, as the cards themselves.
 *
 * <p>This was a list of names, on the argument that ten thousand cards is a thing you scan
 * and a grid of art shows nine of them. Playtesters put it plainly: "you can see the card
 * names, but no card art... it would be better to only show the actual cards and you can see
 * them in stacks based on how many of each card you have." They are right, and the old
 * argument was answering the wrong question - a collection is a box you look through, and a
 * box of cards you cannot see is a spreadsheet. Scanning is what the search box is for, and
 * it was already there.
 *
 * <p>So: a grid of card faces, each drawn as a stack as deep as the number of copies, with
 * the count on it. Resting on one puts it in {@link ClientHoverState}, so the read key opens
 * the same panel it opens everywhere else in the mod - a collection is one of the places
 * somebody most wants to read a card, and it should not be the one place that cannot.
 *
 * <p>Searching happens on the server, so nothing here filters anything: the box and the
 * buttons ask, and a page comes back. Which means the screen behaves the same on a collection
 * of ten cards and one of ten thousand - and the page is sized from how many cards this
 * window has room for, so the server never sends a card nobody can see.
 *
 * <p>Deliberately <b>not</b> a {@link CardPreviewHost}. It used to say it was, which is the
 * marker telling the read overlay to stay out of a screen that draws a preview of its own -
 * and this one never drew one. So the read key did nothing at all in the collection, which is
 * one of the two places in the mod somebody most wants to read a card. Reported as part of
 * "holding alt should still open the card info screen".
 *
 * <p>Client-only.
 */
public final class CollectionScreen extends Screen {

    private static final int MARGIN = 16;

    /** What "Build deck..." needs, and what the narrowest window has room for beside the pips. */
    private static final int BUILD_WIDTH = 86;
    private static final int TOP_BAR = 80;

    /** The width a card wants in the grid, before the columns are fitted around it. */
    private static final int CARD_WIDTH_WANTED = 78;

    /** Never narrower than this: below it a card is a colored smudge rather than a card. */
    private static final int CARD_WIDTH_LEAST = 34;

    private static final int GRID_GAP = 6;

    /** How far each card of a stack sits behind the one in front, so depth reads as depth. */
    private static final int STACK_STEP = 3;

    /** The most cards drawn behind the front one. Four copies and forty look the same anyway. */
    private static final int STACK_DEEPEST = 3;

    private static final int COUNT_TEXT = 0xFFFFF0D0;
    private static final int FOIL_MARK = 0xFFE8C86A;
    private static final int BOTTOM_BAR = 34;
    private static final int TEXT = 0xFFDDE3EC;
    private static final int DIM = 0xFF8A94A3;

    private final BlockPos where;
    private final String label;
    private final boolean mayTake;
    private final boolean mayAdd;
    private int total;
    private int distinct;

    private CollectionQuery query = CollectionQuery.EVERYTHING;
    private boolean descending;
    private int page;
    private int pages = 1;
    private int matched;
    private List<CollectionPagePayload.Row> rows = List.of();

    /** Whether the syntax card is up over the grid. Off by default: it is a reminder, not a page. */
    private boolean showingSyntax;

    private EditBox searchBox;
    private Button sortButton;
    private Button directionButton;
    private Button rarityButton;
    private Button previousButton;
    private Button nextButton;

    private CollectionScreen(OpenCollectionPayload opened) {
        super(Component.translatable("screen.gathering.collection"));
        this.where = opened.where();
        this.label = opened.label();
        this.total = opened.total();
        this.distinct = opened.distinct();
        this.mayTake = opened.mayTake();
        this.mayAdd = opened.mayAdd();
    }

    /** Opens one, replacing whatever collection was open before. */
    public static void show(OpenCollectionPayload opened) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new CollectionScreen(opened));
    }

    /**
     * A page arriving for whichever screen asked for it.
     *
     * <p>Two screens read a collection - this one and the deck builder - and both ask through
     * the same payload, so the answer goes to whichever is open. Routed here rather than each
     * screen registering itself, because a page is answered by exactly one screen at a time
     * and the alternative is two handlers racing to claim it.
     */
    public static void accept(CollectionPagePayload payload) {
        net.minecraft.client.gui.screens.Screen open =
                net.minecraft.client.Minecraft.getInstance().screen;
        if (open instanceof CollectionScreen screen && screen.where.equals(payload.where())) {
            screen.take(payload);
        } else if (open instanceof DeckBuilderScreen builder) {
            builder.accept(payload);
        }
    }

    private void take(CollectionPagePayload payload) {
        this.page = payload.page();
        this.pages = Math.max(1, payload.pages());
        this.total = payload.counts().total();
        this.distinct = payload.counts().distinct();
        this.matched = payload.counts().matched();
        this.rows = payload.rows();
        updateButtons();
    }

    @Override
    protected void init() {
        searchBox = new EditBox(this.font, MARGIN, MARGIN + 20, this.width - 2 * MARGIN - 166, 18,
                Component.translatable("screen.gathering.collection.search"));
        searchBox.setMaxLength(CollectionQuery.MOST_CHARACTERS);
        searchBox.setHint(Component.translatable("screen.gathering.collection.search_hint"));
        searchBox.setValue(query.text());
        // Asked when the search is finished rather than while it is being typed: every
        // keystroke would be a packet and a pass over the whole collection.
        searchBox.setResponder(text -> { });
        addRenderableWidget(searchBox);

        // The one thing that says the box takes more than a name. A search language nobody is
        // told about is a search language nobody uses, and the hint in the box only has room
        // for one example - so the rest is behind a button beside it, which is also the only
        // place a player who half-remembers "is it t: or type:" can go and check.
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - 164, MARGIN + 20, 16, 18,
                Component.literal("?"), () -> showingSyntax = !showingSyntax));

        sortButton = GatheringButtons.of(this.width - MARGIN - 144, MARGIN + 20, 70, 18,
                sortLabel(), this::nextSort);
        addRenderableWidget(sortButton);
        // Its own button rather than a wrap-around on the first one. Turning the order round
        // is a thing people do constantly and it should not take five presses to reach.
        directionButton = GatheringButtons.of(this.width - MARGIN - 72, MARGIN + 20, 18, 18,
                directionLabel(), this::flipDirection);
        addRenderableWidget(directionButton);

        previousButton = GatheringButtons.of(this.width - MARGIN - 52, MARGIN + 20, 24, 18,
                Component.literal("<"), () -> turnTo(page - 1));
        addRenderableWidget(previousButton);
        nextButton = GatheringButtons.of(this.width - MARGIN - 26, MARGIN + 20, 26, 18,
                Component.literal(">"), () -> turnTo(page + 1));
        addRenderableWidget(nextButton);

        // Color and rarity are buttons rather than something to type, because a search box
        // that understands "c:wu" is a query language, and a query language is a thing to
        // learn. Six pips and a cycle say the same and can be found by looking.
        int pipsX = MARGIN;
        int pipsY = MARGIN + 42;
        for (String color : COLORS) {
            addRenderableWidget(GatheringButtons.of(pipsX, pipsY, 18, 16,
                    Component.literal(color), () -> toggleColor(color)));
            pipsX += 20;
        }
        rarityButton = GatheringButtons.of(pipsX + 6, pipsY, 92, 16, rarityLabel(), this::nextRarity);
        addRenderableWidget(rarityButton);

        // The other way to take cards out, and the one worth finding. Sleeving a hundred-card
        // list a card at a time is a hundred clicks; this is one, and it is here rather than
        // on the import screen because the cards are here.
        //
        // Sized for the narrowest window the game draws. At 320 the pips and the rarity
        // button end at 214 and the margin leaves 304, which is exactly the eighty-six this
        // takes; every wider window is slack. Clamped as well, so a longer rarity label some
        // day pushes this off the edge where it can be seen rather than underneath it.
        int afterRarity = rarityButton.getX() + rarityButton.getWidth() + 4;
        addRenderableWidget(GatheringButtons.of(
                Math.max(afterRarity, this.width - MARGIN - BUILD_WIDTH), pipsY, BUILD_WIDTH, 16,
                Component.translatable("screen.gathering.collection.build_deck"),
                () -> this.minecraft.setScreen(new DeckBuilderScreen(where, label))));

        // How much of each set is in here, which is the one question a binder cannot answer
        // by being looked at. Beside the way out because it is a place to go rather than a
        // filter: everything else on this screen changes the list, and this leaves it.
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - 118, this.height - BOTTOM_BAR + 8, 58, 18,
                Component.translatable("screen.gathering.collection.sets"),
                () -> {
                    // Said out loud, because the answer is what opens the screen and the
                    // server sends more than one: only the answer somebody asked for opens
                    // it, the rest refresh it. See SetProgressScreen.asked.
                    SetProgressScreen.asked(where);
                    ClientNetworking.send(
                            new dev.gathering.network.AskSetProgressPayload(where));
                }));

        // A way out somebody can see. Every other panel in the mod has one, and this one
        // relied on the escape key - which is a rule nobody was told. Bottom right, in the
        // bar the page count already lives in.
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - 56, this.height - BOTTOM_BAR + 8, 56, 18,
                Component.translatable("gui.done"), this::onClose));

        setInitialFocus(searchBox);
        updateButtons();
        // Here rather than when the collection opened: this is the first moment anything
        // knows how tall the window is, and it runs again on every resize, so the page always
        // fits the box it is drawn in.
        askFor(page);
    }

    /** WUBRG and colorless, in the order a player reads them. */
    private static final String[] COLORS = {"W", "U", "B", "R", "G", "C"};

    /** The rarities worth filtering by, and off. */
    private static final Rarity[] RARITIES = {
            null, Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.MYTHIC};

    /**
     * Turns one color on or off.
     *
     * <p>Several on means at least all of them, which is how anybody already reads a color
     * filter: white and blue finds the Azorius card, not the mono-white ones.
     */
    private void toggleColor(String color) {
        String now = query.colors();
        query = query.inColors(now.contains(color)
                ? now.replace(color, "")
                : now + color);
        askFor(0);
    }

    private void nextRarity() {
        int at = 0;
        for (int index = 0; index < RARITIES.length; index++) {
            if (RARITIES[index] == query.rarity()) {
                at = index;
                break;
            }
        }
        query = query.ofRarity(RARITIES[(at + 1) % RARITIES.length]);
        askFor(0);
    }

    private Component rarityLabel() {
        return query.rarity() == null
                ? Component.translatable("screen.gathering.collection.rarity_any")
                : Component.translatable("screen.gathering.collection.rarity",
                        Component.translatable("screen.gathering.collection.rarity."
                                + query.rarity().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void updateButtons() {
        if (sortButton != null) {
            sortButton.setMessage(sortLabel());
        }
        if (directionButton != null) {
            directionButton.setMessage(directionLabel());
        }
        if (rarityButton != null) {
            rarityButton.setMessage(rarityLabel());
        }
        if (previousButton != null) {
            previousButton.active = page > 0;
        }
        if (nextButton != null) {
            nextButton.active = page + 1 < pages;
        }
    }

    private Component sortLabel() {
        return Component.translatable("screen.gathering.collection.sort."
                + query.sort().name().toLowerCase(java.util.Locale.ROOT));
    }

    private Component directionLabel() {
        return Component.literal(descending ? "▲" : "▼");
    }

    /**
     * Cycles what the list is ordered by.
     *
     * <p>One button rather than five, because a collection has one order at a time and five
     * buttons of which four are off is four buttons doing nothing.
     */
    private void nextSort() {
        CollectionSearch.Sort[] all = CollectionSearch.Sort.values();
        query = query.orderedBy(all[(query.sort().ordinal() + 1) % all.length]);
        askFor(0);
    }

    private void flipDirection() {
        descending = !descending;
        askFor(0);
    }

    private void turnTo(int newPage) {
        askFor(Math.clamp(newPage, 0, pages - 1));
    }

    private void askFor(int wanted) {
        query = query.searchingFor(searchBox == null ? query.text() : searchBox.getValue());
        ClientNetworking.send(
                new CollectionSearchPayload(where, query, descending, wanted, cellsThatFit()));
    }

    /**
     * The grid this window has room for: how wide a card is, and how many fit across and down.
     *
     * <p>Worked out once and asked for by everything, because the click test and the drawing
     * have to agree exactly - a grid drawn to one rule and hit-tested against another is a
     * screen that takes the card next to the one you pointed at.
     *
     * <p>Cards are fitted to a wanted width rather than a fixed column count, so a wide window
     * shows more of the collection rather than the same nine cards blown up, and a narrow one
     * falls to a single column rather than to slivers.
     */
    private record Grid(int cardWidth, int cardHeight, int columns, int rows, int left, int top) {

        int cells() {
            return Math.max(1, columns * rows);
        }

        Rect cellAt(int index) {
            int column = index % columns;
            int row = index / columns;
            return new Rect(
                    left + column * (cardWidth + GRID_GAP),
                    top + row * (cardHeight + GRID_GAP),
                    cardWidth, cardHeight);
        }
    }

    private Grid grid() {
        int room = Math.max(CARD_WIDTH_LEAST, this.width - MARGIN * 2);
        int down = Math.max(1, this.height - BOTTOM_BAR - TOP_BAR);

        int cardWidth = Math.min(CARD_WIDTH_WANTED, room);
        int cardHeight = CardShape.heightFor(cardWidth);
        // Never taller than the whole grid, whatever the width says.
        if (cardHeight > down) {
            cardHeight = down;
            cardWidth = CardShape.widthFor(cardHeight);
        }
        // And small enough for two rows wherever a card that size is still a card. A
        // collection showing one row of four is a page turn every four cards, which is the
        // list this replaced with extra steps. Height drives the width here rather than the
        // other way round, because the vertical room is what is short - deriving the height
        // from a width fitted to fill the row grows the card straight back past the budget.
        int forTwoRows = (down - GRID_GAP) / 2;
        if (cardHeight > forTwoRows && CardShape.heightFor(CARD_WIDTH_LEAST) <= forTwoRows) {
            cardHeight = forTwoRows;
            cardWidth = CardShape.widthFor(cardHeight);
        }

        int columns = Math.max(1, (room + GRID_GAP) / (cardWidth + GRID_GAP));
        int rows = Math.max(1, (down + GRID_GAP) / (cardHeight + GRID_GAP));

        // Centered in what is left over, so the grid sits in its panel rather than against one
        // edge of it with a wide gutter down the other.
        int usedAcross = columns * cardWidth + GRID_GAP * (columns - 1);
        return new Grid(cardWidth, cardHeight, columns, rows,
                MARGIN + (room - usedAcross) / 2, TOP_BAR);
    }

    /**
     * How many cards this window has room for.
     *
     * <p>Sent with every search, because a page bigger than the grid is cards nobody can see -
     * and, worse, cards somebody can click on without seeing, since a click is a position and
     * anything below the fold is still under the cursor.
     */
    private int cellsThatFit() {
        return grid().cells();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            askFor(0);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /**
     * The felt and the panel, under the widgets.
     *
     * <p>Drawn here rather than in {@code render} because {@code super.render} paints the
     * menu background and then the widgets: anything drawn before it is blurred over, and
     * anything drawn after it sits on top of the buttons.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.draw(graphics, Element.SCREEN_BACKDROP,
                0, 0, this.width, this.height);
        GatheringSprites.inset(graphics, MARGIN - 4, TOP_BAR - 4,
                this.width - 2 * MARGIN + 8, this.height - BOTTOM_BAR - TOP_BAR + 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Component title = label.isEmpty()
                ? Component.translatable("screen.gathering.collection")
                : Component.literal(label);
        graphics.drawString(this.font, title, MARGIN, MARGIN, TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("screen.gathering.collection.holding", total, distinct),
                MARGIN + this.font.width(title) + 8, MARGIN, DIM, false);

        drawColorsOn(graphics);
        drawRows(graphics, mouseX, mouseY);
        drawFooter(graphics);
        if (showingSyntax) {
            drawSyntax(graphics);
        }
    }

    /**
     * A line under the colors that are on.
     *
     * <p>A vanilla button has no on state, and six buttons that all look the same whether
     * they are doing anything is a filter nobody can read.
     */
    private void drawColorsOn(GuiGraphics graphics) {
        int x = MARGIN;
        int y = MARGIN + 42 + 15;
        for (String color : COLORS) {
            if (query.colors().contains(color)) {
                GatheringSprites.draw(graphics, Element.FILTER_ON, x + 2, y, 14, 1);
            }
            x += 20;
        }
    }

    private void drawRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = TOP_BAR;
        int bottom = this.height - BOTTOM_BAR;
        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable(total == 0
                            ? "screen.gathering.collection.empty"
                            : "screen.gathering.collection.nothing_found"),
                    this.width / 2, (top + bottom) / 2 - 4, DIM);
            ClientHoverState.clear();
            return;
        }

        Grid grid = grid();
        CollectionPagePayload.Row over = null;
        int shown = Math.min(rows.size(), grid.cells());
        for (int index = 0; index < shown; index++) {
            CollectionPagePayload.Row row = rows.get(index);
            Rect cell = grid.cellAt(index);
            boolean hovered = cell.contains(mouseX, mouseY);
            drawCard(graphics, row, cell, hovered);
            if (hovered) {
                over = row;
            }
        }
        // Whatever the cursor is on, offered to the read key - the same door every other
        // screen in the mod puts a card through, so Alt opens the panel here too.
        ClientHoverState.setHovered(over == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : CardItem.of(over.card()));
    }

    /**
     * One card, drawn as a stack as deep as the number of copies.
     *
     * <p>The depth is the count, which is the thing the old list said as "4x" and the thing a
     * person actually reading a binder gets from the thickness of the pile. It stops at three
     * behind the front card because four copies and forty look the same in a stack anyway -
     * the number in the corner is what says which, and it is only drawn where there is more
     * than one, since "1" on every card in a collection is noise on every card.
     */
    private void drawCard(
            GuiGraphics graphics, CollectionPagePayload.Row row, Rect cell, boolean hovered) {
        int behind = Math.min(STACK_DEEPEST, Math.max(0, row.count() - 1));
        for (int depth = behind; depth >= 1; depth--) {
            int offset = depth * STACK_STEP;
            graphics.blit(CardFaceRenderer.CARD_BACK,
                    cell.x() + offset, cell.y() - offset, 0f, 0f,
                    cell.width(), cell.height(), cell.width(), cell.height());
        }

        CardSummary about = row.about().orElse(null);
        if (about == null) {
            // The server has the card and this client has never looked it up. Its own sleeve
            // rather than an empty box, and the word for it, so a collection that is still
            // fetching reads as one that is still fetching.
            graphics.blit(CardFaceRenderer.CARD_BACK, cell.x(), cell.y(), 0f, 0f,
                    cell.width(), cell.height(), cell.width(), cell.height());
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.collection.unnamed"),
                    (int) cell.centerX(), cell.y() + cell.height() / 2 - 4, cell.width() - 4, DIM);
        } else {
            CardInspectPanel.renderArt(graphics, about, row.card().flipped(),
                    cell.x(), cell.y(), cell.width(), cell.height());
        }

        if (hovered) {
            GatheringSprites.draw(graphics, Element.HOVER_RING,
                    cell.x() - 2, cell.y() - 2, cell.width() + 4, cell.height() + 4);
        }
        if (row.card().foil()) {
            GuiText.drawFlushRight(graphics, this.font, Component.literal("\u2726"),
                    cell.right() - 3, cell.y() + 2, 1f, FOIL_MARK);
        }
        if (row.count() > 1) {
            Component many = Component.literal("x" + row.count());
            int wide = this.font.width(many) + 6;
            int line = this.font.lineHeight + 2;
            GatheringSprites.draw(graphics, Element.NAME_BACKDROP,
                    cell.right() - wide - 2, cell.bottom() - line - 2, wide, line);
            GuiText.drawFlushRight(graphics, this.font, many,
                    cell.right() - 5, cell.bottom() - line, 1f, COUNT_TEXT);
        }
    }

    /**
     * What the search box understands, over the grid.
     *
     * <p>Every line is an example rather than a rule, because a search language is learned by
     * copying a line that looks like the question being asked and changing a word. The list is
     * short on purpose: these are the ones worth knowing, and the box takes more.
     */
    private void drawSyntax(GuiGraphics graphics) {
        int line = this.font.lineHeight + 2;
        int wide = 0;
        for (int at = 0; at < SYNTAX_LINES; at++) {
            wide = Math.max(wide, this.font.width(
                    Component.translatable("screen.gathering.search.help_" + at)));
        }
        Rect panel = new Rect(
                MARGIN, TOP_BAR, Math.min(this.width - MARGIN * 2, wide + 16),
                Math.min(this.height - BOTTOM_BAR - TOP_BAR, SYNTAX_LINES * line + 12));
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        for (int at = 0; at < SYNTAX_LINES; at++) {
            int y = panel.y() + 6 + at * line;
            if (y + line > panel.bottom()) {
                break;
            }
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.search.help_" + at),
                    panel.x() + 8, y, panel.width() - 16, at == 0 ? TEXT : DIM);
        }
    }

    /** How many lines the syntax card has. Written out so the strings and the loop agree. */
    private static final int SYNTAX_LINES = 10;

    private void drawFooter(GuiGraphics graphics) {
        int y = this.height - BOTTOM_BAR + 6;

        // Three things want this line: what was found, what a click does, and two buttons in
        // the corner. They are laid out right to left so none of them can land on another -
        // the buttons own their corner, the hint ends short of them, and the count gets
        // whatever is left and shrinks into it. Drawn in any order they all fit on a wide
        // window and all three overlapped on a narrow one.
        int hintRight = this.width - MARGIN - 124;
        Component how = mayTake ? whatAClickDoes()
                : Component.translatable("screen.gathering.collection.hint_look");
        int hintWidth = Math.min(this.font.width(how), Math.max(0, hintRight - MARGIN));
        graphics.drawString(this.font, how, hintRight - hintWidth, y, DIM, false);

        Component found = Component.translatable(
                "screen.gathering.collection.page", matched, page + 1, pages);
        GuiText.draw(graphics, this.font, found, MARGIN, y,
                Math.max(0, hintRight - hintWidth - MARGIN - 8), DIM);

        // The other half of the gesture, said where somebody is holding the deck it applies
        // to. It is the only place it can be found, and a tooltip five lines long is not one.
        if (heldDeckName() != null) {
            Component back = Component.translatable("screen.gathering.collection.hint_dissolve");
            graphics.drawString(this.font, back,
                    hintRight - this.font.width(back), y + 11, DIM, false);
        }
    }

    /**
     * What clicking a row will do, said in the words of what is in hand.
     *
     * <p>Read off this client's own hand rather than sent: it is holding the deck, and the
     * server checks the same thing before it moves a card. Which means the line changes the
     * moment somebody swaps hands, with no round trip and nothing to keep in step.
     */
    private Component whatAClickDoes() {
        String deck = heldDeckName();
        return deck == null
                ? Component.translatable("screen.gathering.collection.hint_take")
                : Component.translatable("screen.gathering.collection.hint_sleeve", deck);
    }

    /** The name of the deck in hand, or null where there is not one. */
    private String heldDeckName() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return dev.gathering.item.DeckItem.deckOf(player.getMainHandItem())
                .map(deck -> deck.name().isBlank()
                        ? Component.translatable("item.gathering.deck").getString()
                        : deck.name())
                .orElse(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!mayTake) {
            return false;
        }
        int index = cardUnder(mouseX, mouseY);
        // Bounded by what is drawn, not only by what the page holds: between a window shrink
        // and the re-asked page arriving, cards past the grid exist but are not on the
        // screen, and a click in the blank strip below took an unseen card.
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        CardComponent card = rows.get(index).card();
        // Left takes one, right takes four - a playset, which is what anybody taking more
        // than one card out of a binder is taking.
        int howMany = button == 1 ? 4 : 1;
        ClientNetworking.send(new CollectionTakePayload(where, card, howMany));
        // And then ask for the page again. Same connection, so the take is dealt with first;
        // asking from here rather than being told keeps the search somebody is looking at in
        // the one place that knows it.
        askFor(page);
        GatheringButtons.clickSound();
        return true;
    }

    /** Which card of the grid this point is on, or -1 for the gaps and the margins. */
    private int cardUnder(double mouseX, double mouseY) {
        Grid grid = grid();
        for (int index = 0; index < grid.cells(); index++) {
            if (grid.cellAt(index).contains((int) mouseX, (int) mouseY)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public void removed() {
        ClientHoverState.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Shows only one set, from the top.
     *
     * <p>What pressing a row of the set-progress screen does. Straight to the first page
     * rather than wherever the last search was: the set being asked for is a different list,
     * and page nine of it is not where anybody wants to arrive.
     */
    public void showOnly(String setCode) {
        query = new CollectionQuery(
                "", setCode == null ? "" : setCode, "", "", null, query.sort());
        if (searchBox != null) {
            searchBox.setValue("");
        }
        askFor(0);
    }

    /** What the screen is looking for, for the scripted run. */
    public CollectionQuery query() {
        return query;
    }

    /** What is on the page, for the scripted run. */
    public List<CollectionPagePayload.Row> shown() {
        return rows;
    }

    /** Whether this player may take from it, for the scripted run. */
    public boolean mayTake() {
        return mayTake;
    }

    /** Types a search and asks for it, for the scripted run. */
    public void searchFor(String text) {
        if (searchBox != null) {
            searchBox.setValue(text == null ? "" : text);
        }
        askFor(0);
    }
}
