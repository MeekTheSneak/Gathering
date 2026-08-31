package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.collection.BuildCard;
import dev.gathering.core.collection.CardKind;
import dev.gathering.core.collection.DeckBuild;
import dev.gathering.core.ui.CardShape;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.BuildDeckPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.network.CollectionPagePayload;
import dev.gathering.network.CollectionQuery;
import dev.gathering.network.CollectionSearchPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Building a deck out of a collection, a card at a time.
 *
 * <p>The box down one side and the deck down the other, which is the shape every deck site
 * settled on because it is the shape of the job: you are looking at what you have and
 * deciding what goes in. Click a card to put it in, click a row of the list to take it back
 * out. Nothing is committed until Finish, so the whole session is a thing somebody can back
 * out of - the collection is not touched until the deck is made.
 *
 * <p><b>All the arithmetic is somewhere else.</b> The piles, the counts, the curve and the
 * color-identity check are {@link DeckBuild}'s, which is pure and checked in milliseconds;
 * this class positions rectangles and sends two payloads. That split is the reason a screen
 * this size is worth having at all.
 *
 * <p><b>It refuses nothing.</b> Not a fifth copy, not an off-color card, not a hundred and
 * one cards. Those are reported, which is what a builder is for, and the deck check at the
 * door is this mod's one referee.
 *
 * <p>Client-only.
 */
public final class DeckBuilderScreen extends Screen {

    private static final int MARGIN = 12;
    private static final int GAP = 6;
    private static final int TOP_BAR = 62;
    private static final int BOTTOM_BAR = 30;

    /** How much of the width the box gets. The rest is the deck, which is a list of text. */
    private static final float BOX_SHARE = 0.62f;

    private static final int CARD_WIDTH_WANTED = 62;
    private static final int CARD_WIDTH_LEAST = 34;
    private static final int ROW_HEIGHT = 11;

    private static final int TEXT = 0xFFDDE3EC;
    private static final int DIM = 0xFF8A94A3;
    private static final int ACCENT = 0xFFE8C86A;
    private static final int WARN = 0xFFE07A7A;
    private static final int COUNT_TEXT = 0xFFFFF0D0;

    private final BlockPos where;
    private final String label;

    /** What the deck being built will be sleeved in. Picked here, carried to the deck. */
    private dev.gathering.core.card.Sleeve sleeve = dev.gathering.core.card.Sleeve.DEFAULT;

    private DeckBuild build = DeckBuild.EMPTY;
    private CollectionQuery query = CollectionQuery.EVERYTHING;
    private int page;
    private int pages = 1;
    private List<CollectionPagePayload.Row> rows = List.of();


    private EditBox searchBox;
    private EditBox nameBox;

    private int deckScroll;

    public DeckBuilderScreen(BlockPos where, String label) {
        super(Component.translatable("screen.gathering.builder"));
        this.where = where;
        this.label = label == null ? "" : label;
    }

    @Override
    protected void init() {
        String searching = searchBox == null ? query.text() : searchBox.getValue();
        String named = nameBox == null ? "" : nameBox.getValue();

        int boxWidth = boxPane().width();
        this.nameBox = new EditBox(this.font, MARGIN, MARGIN + 12, Math.min(180, boxWidth), 16,
                Component.translatable("screen.gathering.builder.name"));
        this.nameBox.setHint(Component.translatable("screen.gathering.builder.name_hint"));
        this.nameBox.setValue(named);
        this.nameBox.setMaxLength(BuildDeckPayload.LONGEST_NAME);
        addRenderableWidget(this.nameBox);

        int searchTop = MARGIN + 32;
        this.searchBox = new EditBox(this.font, MARGIN, searchTop, Math.max(60, boxWidth), 16,
                Component.translatable("screen.gathering.collection.search"));
        this.searchBox.setHint(Component.translatable("screen.gathering.collection.search_hint"));
        this.searchBox.setValue(searching);
        this.searchBox.setResponder(text -> askFor(0));
        addRenderableWidget(this.searchBox);


        int buttonTop = this.height - BOTTOM_BAR + 6;
        addRenderableWidget(GatheringButtons.of(MARGIN, buttonTop, 84, 18,
                Component.translatable("screen.gathering.builder.from_list"),
                () -> this.minecraft.setScreen(new DecklistImportScreen(where))));
        // Chosen while the deck is being built rather than after it exists, because a deck
        // handed over in somebody else's sleeves is a deck they have to go and fix.
        addRenderableWidget(GatheringButtons.of(MARGIN + 88, buttonTop, 78, 18,
                Component.translatable("screen.gathering.deck.sleeves"),
                () -> this.minecraft.setScreen(
                        new SleeveScreen(sleeve, picked -> sleeve = picked, this))));
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - 152, buttonTop, 70, 18,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - 78, buttonTop, 78, 18,
                Component.translatable("screen.gathering.builder.finish"), this::finish));

        askFor(page);
    }

    // ------------------------------------------------------------------ panes

    private Rect boxPane() {
        int across = Math.max(80, (int) ((this.width - MARGIN * 2 - GAP) * BOX_SHARE));
        return new Rect(MARGIN, TOP_BAR, across, Math.max(1, this.height - BOTTOM_BAR - TOP_BAR));
    }

    private Rect deckPane() {
        Rect box = boxPane();
        return new Rect(box.right() + GAP, TOP_BAR,
                Math.max(60, this.width - MARGIN - box.right() - GAP), box.height());
    }

    /**
     * The card grid in the box pane, fitted to it.
     *
     * <p>The same rule the collection's own grid uses, for the same reason - the two are the
     * same thing in a narrower column, and a builder whose cards were a different size from
     * the collection's would look like a different feature.
     */
    private Grid grid() {
        Rect pane = boxPane();
        int cardWidth = Math.min(CARD_WIDTH_WANTED, pane.width());
        int cardHeight = CardShape.heightFor(cardWidth);
        if (cardHeight > pane.height()) {
            cardHeight = pane.height();
            cardWidth = CardShape.widthFor(cardHeight);
        }
        int forTwoRows = (pane.height() - GAP) / 2;
        if (cardHeight > forTwoRows && CardShape.heightFor(CARD_WIDTH_LEAST) <= forTwoRows) {
            cardHeight = forTwoRows;
            cardWidth = CardShape.widthFor(cardHeight);
        }
        int columns = Math.max(1, (pane.width() + GAP) / (cardWidth + GAP));
        int rowCount = Math.max(1, (pane.height() + GAP) / (cardHeight + GAP));
        return new Grid(cardWidth, cardHeight, columns, rowCount, pane.x(), pane.y());
    }

    private record Grid(int cardWidth, int cardHeight, int columns, int rows, int left, int top) {

        int cells() {
            return Math.max(1, columns * rows);
        }

        Rect cellAt(int index) {
            return new Rect(
                    left + (index % columns) * (cardWidth + GAP),
                    top + (index / columns) * (cardHeight + GAP),
                    cardWidth, cardHeight);
        }
    }

    // ----------------------------------------------------------------- asking

    private void askFor(int wanted) {
        query = query.searchingFor(searchBox == null ? query.text() : searchBox.getValue());
        ClientNetworking.send(new CollectionSearchPayload(
                where, query, false, Math.max(0, wanted), grid().cells()));
    }

    /** A page came back. Kept whether or not it is the one asked for; the server decides. */
    public void accept(CollectionPagePayload payload) {
        if (!payload.where().equals(where)) {
            return;
        }
        this.rows = payload.rows();
        this.page = payload.page();
        this.pages = payload.pages();
    }


    // ---------------------------------------------------------------- editing

    private void add(CollectionPagePayload.Row row) {
        buildCardOf(row).ifPresent(card -> {
            build = build.with(card);
            GatheringButtons.clickSound();
        });
    }

    private void lead(CollectionPagePayload.Row row) {
        buildCardOf(row).ifPresent(card -> {
            // Pressed on the card that is already leading, this puts it back in the deck -
            // otherwise naming a commander would be a decision with no way out of it.
            build = build.commander()
                    .filter(already -> already.printing().equals(card.printing()))
                    .isPresent()
                    ? build.led(null).with(card)
                    : build.led(card);
            askFor(0);
            GatheringButtons.clickSound();
        });
    }

    /**
     * What the pure layer needs, out of what the server sent.
     *
     * <p>Empty for a card this client has never been told about, which is a card it also
     * cannot draw - so it is never one somebody has clicked on.
     */
    private Optional<BuildCard> buildCardOf(CollectionPagePayload.Row row) {
        CardSummary about = row.about().orElse(null);
        if (about == null) {
            return Optional.empty();
        }
        return row.card().scryfallId().map(printing -> new BuildCard(
                printing, about.oracleId(), about.front().name(), about.front().typeLine(),
                about.front().oracleText(), about.manaValue(), about.colorIdentity(),
                row.card().foil()));
    }

    private boolean isCommander(CollectionPagePayload.Row row) {
        return build.commander()
                .flatMap(card -> row.card().scryfallId().map(card.printing()::equals))
                .orElse(false);
    }

    /** How many of this printing the box still has, given what the deck has already taken. */
    private int leftInTheBox(CollectionPagePayload.Row row) {
        return row.card().scryfallId()
                .map(printing -> row.count() - build.printingsOf(printing)
                        - build.commander()
                                .filter(card -> card.printing().equals(printing))
                                .map(card -> 1).orElse(0))
                .orElse(row.count());
    }

    private void finish() {
        List<CardComponent> cards = new ArrayList<>();
        for (BuildCard card : build.cards()) {
            cards.add(CardComponent.of(
                    dev.gathering.core.card.CardIdentity.ofPrinting(card.printing(), card.foil())));
        }
        ClientNetworking.send(new BuildDeckPayload(
                where,
                nameBox == null ? "" : nameBox.getValue(),
                "",
                cards,
                build.commander().map(card -> CardComponent.of(
                        dev.gathering.core.card.CardIdentity.ofPrinting(
                                card.printing(), card.foil()))),
                sleeve));
        this.onClose();
    }

    // --------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.draw(graphics, Element.SCREEN_BACKDROP, 0, 0, this.width, this.height);
        Rect box = boxPane();
        Rect deck = deckPane();
        GatheringSprites.inset(graphics, box.x() - 4, box.y() - 4, box.width() + 8, box.height() + 8);
        GatheringSprites.panel(graphics, deck.x() - 4, deck.y() - 4, deck.width() + 8, deck.height() + 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font,
                label.isEmpty() ? Component.translatable("screen.gathering.builder") : Component.literal(label),
                MARGIN, MARGIN, TEXT, false);

        drawBox(graphics, mouseX, mouseY);
        drawDeck(graphics, mouseX, mouseY);
        drawFooter(graphics);
    }

    private void drawBox(GuiGraphics graphics, int mouseX, int mouseY) {
        Grid grid = grid();
        CollectionPagePayload.Row over = null;
        int shown = Math.min(rows.size(), grid.cells());
        for (int index = 0; index < shown; index++) {
            CollectionPagePayload.Row row = rows.get(index);
            Rect cell = grid.cellAt(index);
            boolean hovered = cell.contains(mouseX, mouseY);
            int left = leftInTheBox(row);

            CardSummary about = row.about().orElse(null);
            if (about == null) {
                graphics.blit(CardFaceRenderer.CARD_BACK, cell.x(), cell.y(), 0f, 0f,
                        cell.width(), cell.height(), cell.width(), cell.height());
            } else {
                CardInspectPanel.renderArt(graphics, about, row.card().flipped(),
                        cell.x(), cell.y(), cell.width(), cell.height());
            }
            // Everything of this card already in the deck: dimmed rather than removed, so the
            // grid does not reshuffle itself under the cursor every time a card goes in.
            if (left <= 0) {
                GatheringSprites.draw(graphics, Element.GHOST_TINT,
                        cell.x(), cell.y(), cell.width(), cell.height());
            }
            // The commander, marked where it sits in the box rather than only named in the
            // list. It is the one card on this screen with a job, and a player scanning the
            // grid for "which one did I pick" should not have to read the column opposite.
            if (isCommander(row)) {
                GatheringSprites.draw(graphics, Element.CHOSEN_RING,
                        cell.x() - 2, cell.y() - 2, cell.width() + 4, cell.height() + 4);
            }
            if (hovered) {
                GatheringSprites.draw(graphics, Element.HOVER_RING,
                        cell.x() - 2, cell.y() - 2, cell.width() + 4, cell.height() + 4);
                over = row;
            }
            if (left > 1) {
                Component many = Component.literal("x" + left);
                int wide = this.font.width(many) + 6;
                int line = this.font.lineHeight + 2;
                GatheringSprites.draw(graphics, Element.NAME_BACKDROP,
                        cell.right() - wide - 2, cell.bottom() - line - 2, wide, line);
                GuiText.drawFlushRight(graphics, this.font, many,
                        cell.right() - 5, cell.bottom() - line, 1f, COUNT_TEXT);
            }
        }
        ClientHoverState.setHovered(over == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : CardItem.of(over.card()));

        if (rows.isEmpty()) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.collection.nothing_found"),
                    boxPane().x() + boxPane().width() / 2,
                    boxPane().y() + boxPane().height() / 2 - 4, boxPane().width() - 8, DIM);
        }
    }

    /**
     * The deck, in the piles it is read in, with the curve under it.
     *
     * <p>Scrolled rather than paged: a decklist is one thing somebody reads top to bottom, and
     * a hundred-card deck in pages of twenty is four page turns to answer "how many lands".
     */
    private void drawDeck(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect pane = deckPane();
        int curveHeight = 26;
        int listBottom = pane.bottom() - curveHeight;

        graphics.drawString(this.font, Component.translatable(
                "screen.gathering.builder.deck_total", build.total()), pane.x() + 2, pane.y(), TEXT, false);

        List<BuildCard> outside = build.outsideIdentity();
        if (!outside.isEmpty()) {
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.builder.off_color", outside.size()),
                    pane.x() + 2, pane.y() + ROW_HEIGHT, pane.width() - 4, WARN);
        }

        int y = pane.y() + ROW_HEIGHT * 2 - deckScroll;
        deckRows.clear();
        for (Map.Entry<CardKind, List<DeckBuild.Row>> pile : build.byKind().entrySet()) {
            if (y > pane.y() && y < listBottom) {
                GuiText.draw(graphics, this.font,
                        Component.translatable(pile.getKey().translationKey()),
                        pane.x() + 2, y, pane.width() - 4, ACCENT);
            }
            y += ROW_HEIGHT;
            for (DeckBuild.Row row : pile.getValue()) {
                if (y > pane.y() && y < listBottom) {
                    Rect at = new Rect(pane.x(), y, pane.width(), ROW_HEIGHT);
                    if (at.contains(mouseX, mouseY)) {
                        GatheringSprites.draw(graphics, Element.ROW_HOVER,
                                at.x(), at.y() - 1, at.width(), at.height());
                        ClientHoverState.setHovered(CardItem.of(CardComponent.of(
                                dev.gathering.core.card.CardIdentity.ofPrinting(
                                        row.card().printing(), row.card().foil()))));
                    }
                    graphics.drawString(this.font, Component.literal(row.count() + ""),
                            pane.x() + 3, y, DIM, false);
                    GuiText.draw(graphics, this.font, Component.literal(row.card().name()),
                            pane.x() + 16, y, pane.width() - 20, TEXT);
                }
                deckRows.add(new DeckRow(new Rect(pane.x(), y, pane.width(), ROW_HEIGHT), row.card()));
                y += ROW_HEIGHT;
            }
        }
        deckHeight = y + deckScroll - (pane.y() + ROW_HEIGHT * 2);

        drawCurve(graphics, new Rect(pane.x() + 2, listBottom + 4, pane.width() - 4, curveHeight - 8));
    }

    /**
     * The mana curve, as eight columns.
     *
     * <p>Small and unlabeled past the numbers along the bottom: it is read as a shape rather
     * than as data, and a chart with axes on it in the corner of a deck builder is a chart
     * nobody looks at twice.
     */
    private void drawCurve(GuiGraphics graphics, Rect area) {
        if (area.height() <= 0 || area.width() <= 0) {
            return;
        }
        int[] curve = build.curve();
        int tallest = 1;
        for (int count : curve) {
            tallest = Math.max(tallest, count);
        }
        int columnWidth = Math.max(1, area.width() / DeckBuild.CURVE_BUCKETS);
        for (int at = 0; at < DeckBuild.CURVE_BUCKETS; at++) {
            int high = Math.round(area.height() * (curve[at] / (float) tallest));
            int x = area.x() + at * columnWidth;
            GatheringSprites.draw(graphics, Element.BAR_TRACK, x, area.y(), columnWidth - 1, area.height());
            if (high > 0) {
                GatheringSprites.draw(graphics, Element.BAR_FILL,
                        x, area.bottom() - high, columnWidth - 1, high);
            }
        }
    }

    private void drawFooter(GuiGraphics graphics) {
        int y = this.height - BOTTOM_BAR + 10;
        Component how = Component.translatable("screen.gathering.builder.hint");
        // The gap between the button on the left and the pair on the right, measured rather
        // than guessed at: a hint given a fixed share of the window is one that runs under a
        // button on a narrow one and is cut off in the middle of a word on this one.
        int from = MARGIN + 84 + GAP;
        int to = this.width - MARGIN - 152 - GAP;
        GuiText.drawCentered(graphics, this.font, how,
                (from + to) / 2, y, Math.max(0, to - from), DIM);
    }

    /** Where each drawn deck row is, so a click can find the card it named. */
    private record DeckRow(Rect at, BuildCard card) {
    }

    private final List<DeckRow> deckRows = new ArrayList<>();
    private int deckHeight;

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;

        Grid grid = grid();
        for (int index = 0; index < Math.min(rows.size(), grid.cells()); index++) {
            if (!grid.cellAt(index).contains(x, y)) {
                continue;
            }
            CollectionPagePayload.Row row = rows.get(index);
            if (button == 1) {
                lead(row);
            } else if (leftInTheBox(row) > 0) {
                add(row);
            }
            return true;
        }

        for (DeckRow row : deckRows) {
            if (row.at().contains(x, y)) {
                build = build.without(row.card().printing());
                GatheringButtons.clickSound();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (deckPane().contains((int) mouseX, (int) mouseY)) {
            int most = Math.max(0, deckHeight - (deckPane().height() - ROW_HEIGHT * 4));
            deckScroll = Math.clamp(deckScroll - (int) (amountY * ROW_HEIGHT * 2), 0, most);
            return true;
        }
        if (boxPane().contains((int) mouseX, (int) mouseY) && pages > 1) {
            askFor(Math.clamp(page - (int) Math.signum(amountY), 0, pages - 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public void onClose() {
        ClientHoverState.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------- for the harness

    /** How many cards the box pane is showing. */
    int showing() {
        return Math.min(rows.size(), grid().cells());
    }

    /** Clicks the nth card of the box pane, through the same path a mouse takes. */
    void clickCard(int index, int button) {
        Rect cell = grid().cellAt(index);
        mouseClicked(cell.centerX(), cell.centerY(), button);
    }

    int deckSize() {
        return build.total();
    }

    String commanderName() {
        return build.commander().map(BuildCard::name).orElse("");
    }
}
