package dev.gathering.client;

import dev.gathering.core.card.Rarity;
import dev.gathering.core.collection.CollectionSearch;
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
 * A collection, read.
 *
 * <p>Rows rather than card faces. Ten thousand cards is a list you scan, and a grid of art
 * shows nine of them; what somebody opening a collection wants to know is whether a card is
 * in there and how many, which is a line of text.
 *
 * <p>Searching happens on the server, so nothing here filters anything: the box and the
 * buttons ask, and a page comes back. Which means the screen behaves the same on a collection
 * of ten cards and one of ten thousand.
 *
 * <p>Client-only.
 */
public final class CollectionScreen extends Screen implements CardPreviewHost {

    private static final int MARGIN = 16;

    /** What "Build deck..." needs, and what the narrowest window has room for beside the pips. */
    private static final int BUILD_WIDTH = 86;
    private static final int ROW_HEIGHT = 14;
    private static final int TOP_BAR = 80;
    private static final int BOTTOM_BAR = 34;
    private static final int BACKING = 0xE8080B10;
    private static final int ROW_ODD = 0x18FFFFFF;
    private static final int ROW_HOVER = 0x30FFFFFF;
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

    /** A page arriving for the collection that is open. Ignored for any other. */
    public static void accept(CollectionPagePayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().screen
                instanceof CollectionScreen screen && screen.where.equals(payload.where())) {
            screen.take(payload);
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
        searchBox = new EditBox(this.font, MARGIN, MARGIN + 20, this.width - 2 * MARGIN - 148, 18,
                Component.translatable("screen.gathering.collection.search"));
        searchBox.setMaxLength(CollectionQuery.MOST_CHARACTERS);
        searchBox.setHint(Component.translatable("screen.gathering.collection.search_hint"));
        searchBox.setValue(query.text());
        // Asked when the search is finished rather than while it is being typed: every
        // keystroke would be a packet and a pass over the whole collection.
        searchBox.setResponder(text -> { });
        addRenderableWidget(searchBox);

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
                () -> this.minecraft.setScreen(new DecklistImportScreen(where))));

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
                new CollectionSearchPayload(where, query, descending, wanted, rowsThatFit()));
    }

    /**
     * How many rows this window has room for.
     *
     * <p>Sent with every search, because a page bigger than the box is rows nobody can see -
     * and, worse, rows somebody can click on without seeing, since a click is a position and
     * the list below the fold is still under the cursor.
     */
    private int rowsThatFit() {
        return Math.max(1, (this.height - BOTTOM_BAR - TOP_BAR) / ROW_HEIGHT);
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
        graphics.fill(0, 0, this.width, this.height, BACKING);
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
                graphics.fill(x + 2, y, x + 16, y + 1, 0xFFE8C86A);
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

        CollectionPagePayload.Row over = null;
        for (int index = 0; index < rows.size() && index < rowsThatFit(); index++) {
            CollectionPagePayload.Row row = rows.get(index);
            int y = top + index * ROW_HEIGHT;
            boolean hovered = mouseX >= MARGIN && mouseX < this.width - MARGIN
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(MARGIN - 2, y, this.width - MARGIN + 2, y + ROW_HEIGHT, ROW_HOVER);
                over = row;
            } else if (index % 2 == 1) {
                graphics.fill(MARGIN - 2, y, this.width - MARGIN + 2, y + ROW_HEIGHT, ROW_ODD);
            }
            drawRow(graphics, row, y);
        }
        ClientHoverState.setHovered(over == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : CardItem.of(over.card()));
    }

    private void drawRow(GuiGraphics graphics, CollectionPagePayload.Row row, int y) {
        int text = y + 3;
        graphics.drawString(this.font, Component.literal(row.count() + "x"),
                MARGIN, text, DIM, false);

        CardSummary about = row.about().orElse(null);
        Component name = about == null
                ? Component.translatable("screen.gathering.collection.unnamed")
                : Component.literal(about.front().name());
        graphics.drawString(this.font, name, MARGIN + 26, text,
                about == null ? DIM : TEXT, false);

        if (about == null) {
            return;
        }
        String right = about.front().typeLine();
        int rightWidth = this.font.width(right);
        int rightX = this.width - MARGIN - rightWidth;
        // Only where there is room for it. A type line running under the name is worse than
        // no type line.
        if (rightX > MARGIN + 32 + this.font.width(name)) {
            graphics.drawString(this.font, right, rightX, text, DIM, false);
        }
        if (row.card().foil()) {
            graphics.drawString(this.font, "✦", MARGIN + 18, text, 0xFFE8C86A, false);
        }
    }

    private void drawFooter(GuiGraphics graphics) {
        int y = this.height - BOTTOM_BAR + 6;
        Component found = Component.translatable(
                "screen.gathering.collection.page", matched, page + 1, pages);
        graphics.drawString(this.font, found, MARGIN, y, DIM, false);

        Component how = mayTake ? whatAClickDoes()
                : Component.translatable("screen.gathering.collection.hint_look");
        graphics.drawString(this.font, how,
                this.width - MARGIN - this.font.width(how), y, DIM, false);

        // The other half of the gesture, said where somebody is holding the deck it applies
        // to. It is the only place it can be found, and a tooltip five lines long is not one.
        if (heldDeckName() != null) {
            Component back = Component.translatable("screen.gathering.collection.hint_dissolve");
            graphics.drawString(this.font, back,
                    this.width - MARGIN - this.font.width(back), y + 11, DIM, false);
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
        int index = rowUnder(mouseY);
        if (index < 0 || index >= rows.size() || mouseX < MARGIN || mouseX > this.width - MARGIN) {
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

    private int rowUnder(double mouseY) {
        int top = TOP_BAR;
        if (mouseY < top || mouseY >= this.height - BOTTOM_BAR) {
            return -1;
        }
        return (int) ((mouseY - top) / ROW_HEIGHT);
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
