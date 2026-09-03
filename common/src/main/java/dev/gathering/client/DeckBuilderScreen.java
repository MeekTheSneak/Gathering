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
import dev.gathering.network.PocketCardsPayload;
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
import net.minecraft.world.InteractionHand;

/**
 * Building a deck out of a collection, a card at a time.
 * <p>The box down one side and the deck down the other, which is the shape every deck site
 * settled on because it is the shape of the job: you are looking at what you have and
 * deciding what goes in. Click a card to put it in, click a row of the list to take it back
 * out. Nothing is committed until Finish, so the whole session is a thing somebody can back
 * out of - the collection is not touched until the deck is made.
 * <p><b>All the arithmetic is somewhere else.</b> The piles, the counts, the curve and the
 * color-identity check are {@link DeckBuild}'s, which is pure and checked in milliseconds;
 * this class positions rectangles and sends two payloads. That split is the reason a screen
 * this size is worth having at all.
 * <p><b>It refuses nothing.</b> Not a fifth copy, not an off-color card, not a hundred and
 * one cards. Those are reported, which is what a builder is for, and the deck check at the
 * door is this mod's one referee.
 * <p>Client-only.
 */
public final class DeckBuilderScreen extends ChildScreen {

    private static final int MARGIN = 12;
    private static final int GAP = 6;
    private static final int TOP_BAR = 62;

    /**
     * How much room the head of the screen wants.
     * <p>The name box and the search box, or neither. Adding cards to a deck you are already
     * holding has nothing to type, and a head kept at its full height for two boxes that are
     * not there is fifty pixels of nothing above the cards.
     */
    private int topBar() {
        return fromPockets() ? MARGIN + 14 : TOP_BAR;
    }
    /**
     * The foot is as tall as what has to go in it.
     * <p>The hint used to be squeezed into the gap between the buttons at either end, which
     * is not a width anybody chose - it is whatever those buttons left - and on an ordinary
     * window it was not enough, so the line was cut off mid-word. A line that cannot be read
     * is not a hint. It gets its own row now, across the whole foot, over as many lines as it
     * needs, and the foot is built around that rather than the other way round.
     */
    private int bottomBar() {
        return footer().height() + GAP * 3 + hintLines() * (this.font.lineHeight + 1);
    }

    private dev.gathering.core.ui.BuilderFooter footer() {
        return dev.gathering.core.ui.BuilderFooter.of(this.width, this.height, !fromPockets());
    }

    private int hintLines() {
        return GuiText.linesNeeded(this.font, hint(), Math.max(1, this.width - MARGIN * 2));
    }

    private Component hint() {
        return Component.translatable(fromPockets()
                ? "screen.gathering.builder.pockets_hint"
                : "screen.gathering.builder.hint");
    }

    /** How much of the width the box gets. The rest is the deck, which is a list of text. */
    private static final float BOX_SHARE = 0.62f;

    private static final int CARD_WIDTH_WANTED = 62;
    private static final int CARD_WIDTH_LEAST = 34;

    /** How far outside a cell the commander's ring and the hover ring are drawn. */
    private static final int RING_REACH = 2;

    /**
     * How far a panel's own frame reaches in: its nine-slice border, which is eight.
     * <p>Not four. Four is what the neutral theme's wall happens to measure; Future Sight's
     * chrome band is the full eight and Retro's pressed border reads wider, so a box drawn
     * only four pixels outside its cards put the top row of them on the frame.
     */
    private static final int BOX_WALL = 8;
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

    /** Set when the cards come out of the player's own pockets - see the second constructor. */
    private final InteractionHand hand;

    private int deckScroll;

    public DeckBuilderScreen(Screen back, BlockPos where, String label) {
        super(Component.translatable("screen.gathering.builder"), back, false);
        this.where = where;
        this.hand = null;
        this.label = label == null ? "" : label;
    }

    /**
     * The same screen, over the cards in your pockets instead of a box.
     * <p>Adding rather than building: the deck already exists and is in your hand, so there
     * is no name to type, no commander to name and no sleeve to pick - only which of the
     * cards you are carrying go in. Which is the whole of what took so long before, when the
     * only way to do it was to carry the deck and right-click each stack in turn.
     * <p>No search box and no paging beyond what the grid needs. A collection is thousands of
     * cards and has to be searched; an inventory is thirty-six slots and can simply be shown.
     */
    public DeckBuilderScreen(Screen back, InteractionHand hand) {
        super(Component.translatable("screen.gathering.builder.pockets"), back, false);
        this.where = null;
        this.hand = hand;
        this.label = "";
    }

    /** Whether this is adding to a deck in hand rather than building one out of a box. */
    private boolean fromPockets() {
        return hand != null;
    }

    /**
     * How many more cards the deck in hand has room for, less what is already picked.
     * <p>Only asked in pockets mode, where the deck already exists and is already part full.
     * Without it the screen would happily take a hundred picks against a deck with sixty
     * cards in it and let the server refuse forty of them afterwards, which is a screen that
     * said yes to something that was never going to happen.
     */
    private int room() {
        if (!fromPockets()) {
            return dev.gathering.item.DeckComponent.MAX_CARDS;
        }
        return Math.max(0,
                dev.gathering.item.DeckComponent.MAX_CARDS - alreadyInTheDeck() - build.total());
    }

    /** How many cards the deck in hand is already holding. Nothing outside pockets mode. */
    private int alreadyInTheDeck() {
        if (!fromPockets()) {
            return 0;
        }
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null
                ? 0
                : dev.gathering.item.DeckItem.deckOf(player.getItemInHand(hand))
                        .map(dev.gathering.item.DeckComponent::totalCards)
                        .orElse(0);
    }

    @Override
    protected void init() {
        String searching = searchBox == null ? query.text() : searchBox.getValue();
        String named = nameBox == null ? "" : nameBox.getValue();
        this.nameBox = null;
        this.searchBox = null;

        dev.gathering.core.ui.BuilderFooter footer = footer();
        if (!fromPockets()) {
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
            this.searchBox.setHint(
                    Component.translatable("screen.gathering.collection.search_hint"));
            this.searchBox.setValue(searching);
            this.searchBox.setResponder(text -> askFor(0));
            addRenderableWidget(this.searchBox);

            addRenderableWidget(GatheringButtons.of(footer.fromList(),
                    Component.translatable("screen.gathering.builder.from_list"),
                    () -> this.minecraft.setScreen(new DecklistImportScreen(where))));
            // Chosen while the deck is being built rather than after it exists, because a deck
            // handed over in somebody else's sleeves is a deck they have to go and fix.
            addRenderableWidget(GatheringButtons.of(footer.sleeves(),
                    Component.translatable("screen.gathering.deck.sleeves"),
                    () -> this.minecraft.setScreen(
                            new SleeveScreen(sleeve, picked -> sleeve = picked, this))));
        }
        addRenderableWidget(GatheringButtons.of(footer.cancel(),
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(GatheringButtons.of(footer.finish(),
                Component.translatable(fromPockets()
                        ? "screen.gathering.builder.add_them"
                        : "screen.gathering.builder.finish"),
                this::finish));

        askFor(page);
    }

    // ------------------------------------------------------------------ panes

    private Rect boxPane() {
        int across = Math.max(80, (int) ((this.width - MARGIN * 2 - GAP) * BOX_SHARE));
        int top = topBar();
        return new Rect(MARGIN, top, across, Math.max(1, this.height - bottomBar() - top));
    }

    /**
     * The part of the deck pane the list itself occupies.
     * <p>The pane less the mana curve along its foot, which is what the drawing already uses
     * to decide whether a row is on the screen. Clicks ask the same question, because a row
     * that is not drawn is not there to be clicked.
     */
    private Rect deckList() {
        Rect pane = deckPane();
        return new Rect(pane.x(), pane.y(), pane.width(),
                Math.max(0, pane.height() - CURVE_HEIGHT));
    }

    /**
     * How much of the deck column the curve takes, including what it is called and the mana
     * values along its foot.
     * <p>Reported from a real session: "the bars in the deck building menu have seemingly no
     * purpose... it isn't immediately obvious what they are communicating." They were eight
     * bare columns, on the theory that a curve is read as a shape rather than as data. A
     * shape nobody can name is not read at all, so it says what it is and what its axis
     * counts, and costs eight more pixels of list to do it.
     */
    private static final int CURVE_HEIGHT = 40;

    /**
     * The line the curve's name sits on, and the shorter one its mana values sit on.
     * <p>A whole line for the name, because anything less and the letters come down over the
     * tops of the columns; the values are written half size, so they need half as much.
     */
    private static final int CURVE_TITLE = 9;
    private static final int CURVE_FOOT = 6;

    /** How small the mana values under the columns are written. */
    private static final float CURVE_FOOT_SCALE = 0.5f;

    private Rect deckPane() {
        Rect box = boxPane();
        return new Rect(box.right() + GAP, topBar(),
                Math.max(60, this.width - MARGIN - box.right() - GAP), box.height());
    }

    /**
     * The card grid in the box pane, fitted to it.
     * <p>The same rule the collection's own grid uses, for the same reason - the two are the
     * same thing in a narrower column, and a builder whose cards were a different size from
     * the collection's would look like a different feature.
     */
    private Grid grid() {
        // Inside the room the rings need, not flush with the pane. A commander's ring and a
        // hover ring are both drawn two pixels outside the cell, and a grid that started at
        // the pane's own edge put them on the box's wall - the same mistake the collection's
        // grid made with its stacks, in a screen where the ring is the only thing saying
        // which card is the commander.
        Rect pane = boxPane().shrink(RING_REACH);
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

    /** The recessed box the card grid is drawn in, which nothing may spill out of. */
    Rect boxOnScreen() {
        Rect box = boxPane();
        return new Rect(box.x() - BOX_WALL, box.y() - BOX_WALL,
                box.width() + BOX_WALL * 2, box.height() + BOX_WALL * 2);
    }

    /** Everything one cell really covers: the cell, and the rings drawn outside it. */
    Rect drawnAt(int index) {
        Rect cell = grid().cellAt(index);
        return new Rect(cell.x() - RING_REACH, cell.y() - RING_REACH,
                cell.width() + RING_REACH * 2, cell.height() + RING_REACH * 2);
    }

    /** How many cells the grid is showing, for the same check. */
    int cellsThatFit() {
        return grid().cells();
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
        if (fromPockets()) {
            readThePockets(Math.max(0, wanted));
            return;
        }
        query = query.searchingFor(searchBox == null ? query.text() : searchBox.getValue());
        ClientNetworking.send(new CollectionSearchPayload(
                where, query, false, Math.max(0, wanted), grid().cells(), true));
    }

    /**
     * The loose cards this player is carrying, as rows for the grid.
     * <p>Read straight off the client's own inventory rather than asked for: it is already
     * here, it is at most thirty-six slots, and a round trip to be told what is in your own
     * pockets is a round trip that can be wrong.
     * <p>What is picked is still only a request. The server checks every card against that
     * player's real inventory before it moves anything - see PocketCards.
     */
    private void readThePockets(int wanted) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            rows = List.of();
            return;
        }
        // One row per distinct card, counted, so four copies read as one entry saying four
        // rather than as four entries - which is how the collection shows them.
        java.util.Map<CardComponent, Integer> counted = new java.util.LinkedHashMap<>();
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || !(stack.getItem() instanceof dev.gathering.item.CardItem)) {
                continue;
            }
            dev.gathering.item.CardItem.cardOf(stack)
                    .map(CardComponent::faceUp)
                    .ifPresent(card -> counted.merge(card, stack.getCount(), Integer::sum));
        }
        List<CollectionPagePayload.Row> all = new ArrayList<>();
        counted.forEach((card, count) -> all.add(new CollectionPagePayload.Row(
                card, count, ClientCardCache.get().summary(card), count)));
        all.sort(java.util.Comparator.comparing(row -> row.about()
                .map(about -> about.front().name())
                .orElse("")));

        int perPage = Math.max(1, grid().cells());
        this.pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(wanted, pages - 1));
        int from = Math.min(page * perPage, all.size());
        this.rows = List.copyOf(all.subList(from, Math.min(from + perPage, all.size())));
    }

    /** A page came back. Kept whether or not it is the one asked for; the server decides. */
    public void accept(CollectionPagePayload payload) {
        if (where == null || !payload.where().equals(where)) {
            return;
        }
        this.rows = payload.rows();
        this.page = payload.page();
        this.pages = payload.pages();
    }


    // ---------------------------------------------------------------- editing

    private void add(CollectionPagePayload.Row row) {
        if (room() <= 0) {
            return;
        }
        buildCardOf(row).ifPresent(card -> {
            build = build.with(card);
            GatheringButtons.clickSound();
        });
    }

    /**
     * Every copy of this card that is still to be had, in one press.
     * <p>Shift-click, the same modifier the basic-land buttons use for the same reason: a
     * pool from a booster box has four of things in it, and four presses per card is what
     * made putting a deck together take all evening. It stops where the deck stops, so a
     * shift-click near the limit adds what fits rather than nothing.
     */
    private void addEvery(CollectionPagePayload.Row row) {
        int wanted = Math.min(leftInTheBox(row), room());
        if (wanted <= 0) {
            return;
        }
        buildCardOf(row).ifPresent(card -> {
            for (int one = 0; one < wanted; one++) {
                build = build.with(card);
            }
            GatheringButtons.clickSound();
        });
    }

    private void lead(CollectionPagePayload.Row row) {
        if (fromPockets()) {
            // The deck already exists and already has whatever command zone it has. Naming a
            // commander here would be naming one for a deck this screen is not building.
            return;
        }
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

    /**
     * How many of this printing are still to be had, given what the deck has already taken.
     * <p>The box and the player's own pockets together, because that is the pool the server
     * built this page out of - see CollectionView.search. A count that only spoke for the box
     * would dim a card somebody is holding four of.
     */
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
        if (fromPockets()) {
            // Adding to the deck already in hand rather than making one. Same screen, same
            // picking, different verb at the end of it.
            ClientNetworking.send(PocketCardsPayload.of(hand, cards));
            this.onClose();
            return;
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
        Rect box = boxPane();
        Rect deck = deckPane();
        GatheringSprites.inset(graphics, box.x() - BOX_WALL, box.y() - BOX_WALL,
                box.width() + BOX_WALL * 2, box.height() + BOX_WALL * 2);
        GatheringSprites.panel(graphics, deck.x() - BOX_WALL, deck.y() - BOX_WALL,
                deck.width() + BOX_WALL * 2, deck.height() + BOX_WALL * 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font,
                fromPockets() ? getTitle()
                        : label.isEmpty()
                                ? Component.translatable("screen.gathering.builder")
                                : Component.literal(label),
                MARGIN, MARGIN, TEXT, false);

        drawBox(graphics, mouseX, mouseY);
        drawDeck(graphics, mouseX, mouseY);
        drawFooter(graphics);

        // Last, so it goes over everything, and after drawDeck because that is what says
        // where the columns ended up this frame.
        List<Component> tip = tipForCurve(mouseX, mouseY);
        if (!tip.isEmpty()) {
            graphics.renderTooltip(this.font, tip, java.util.Optional.empty(), mouseX, mouseY);
        }
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
     * <p>Scrolled rather than paged: a decklist is one thing somebody reads top to bottom, and
     * a hundred-card deck in pages of twenty is four page turns to answer "how many lands".
     */
    private void drawDeck(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect pane = deckPane();
        int curveHeight = CURVE_HEIGHT;
        int listBottom = pane.bottom() - curveHeight;

        graphics.drawString(this.font,
                fromPockets()
                        ? Component.translatable("screen.gathering.builder.picked",
                                build.total(), alreadyInTheDeck())
                        : Component.translatable(
                                "screen.gathering.builder.deck_total", build.total()),
                pane.x() + 2, pane.y(), TEXT, false);

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
     * The mana curve: what it is called, eight columns, and the mana value under each.
     * <p>Named and labeled, because it was neither and a player could not tell what it was
     * saying. Still small - the point of a curve is the shape - but a shape with "Mana curve"
     * over it and 0 to 7+ along its foot answers "what am I looking at" in one glance, which
     * is the whole of what it was failing to do. How many cards are in a column is a hover
     * away rather than printed, because eight numbers over eight columns this size is a
     * thicket.
     */
    private void drawCurve(GuiGraphics graphics, Rect area) {
        if (area.height() <= CURVE_TITLE + CURVE_FOOT || area.width() <= 0) {
            return;
        }
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.builder.curve"),
                area.x(), area.y(), area.width(), DIM);

        Rect columns = new Rect(area.x(), area.y() + CURVE_TITLE,
                area.width(), area.height() - CURVE_TITLE - CURVE_FOOT);
        int[] curve = build.curve();
        int tallest = 1;
        for (int count : curve) {
            tallest = Math.max(tallest, count);
        }
        int columnWidth = Math.max(1, columns.width() / DeckBuild.CURVE_BUCKETS);
        for (int at = 0; at < DeckBuild.CURVE_BUCKETS; at++) {
            int high = Math.round(columns.height() * (curve[at] / (float) tallest));
            int x = columns.x() + at * columnWidth;
            // The curve's own elements, not the progress bar's. A column stands up, and the
            // bar the set list draws only reads one way round - its ends are cut on the
            // diagonal and its light runs along the top.
            GatheringSprites.draw(graphics, Element.CURVE_TRACK,
                    x, columns.y(), columnWidth - 1, columns.height());
            if (high > 0) {
                GatheringSprites.draw(graphics, Element.CURVE_FILL,
                        x, columns.bottom() - high, columnWidth - 1, high);
            }
            GuiText.drawCenteredAt(graphics, this.font, footOf(at),
                    x + (columnWidth - 1) / 2, columns.bottom() + 1, CURVE_FOOT_SCALE, DIM);
        }
        curveArea = columns;
    }

    /** What goes under one column: its mana value, and "7+" under the one that shares. */
    private static Component footOf(int bucket) {
        return bucket == DeckBuild.CURVE_BUCKETS - 1
                ? Component.translatable("screen.gathering.builder.curve_top")
                : Component.literal(Integer.toString(bucket));
    }

    /**
     * How many cards are in the column under the cursor, said in words.
     * <p>The count is the one thing the shape cannot tell you, and it is only ever wanted for
     * the column you are looking at - so it is a hover rather than eight numbers printed over
     * eight columns a few pixels wide.
     */
    private List<Component> tipForCurve(int mouseX, int mouseY) {
        if (curveArea == null || !curveArea.contains(mouseX, mouseY)) {
            return List.of();
        }
        int columnWidth = Math.max(1, curveArea.width() / DeckBuild.CURVE_BUCKETS);
        int at = Math.clamp((mouseX - curveArea.x()) / columnWidth,
                0, DeckBuild.CURVE_BUCKETS - 1);
        return List.of(Component.translatable("screen.gathering.builder.curve_bucket",
                footOf(at), build.curve()[at]));
    }

    /** Where the columns were last drawn, so the cursor can be asked which one it is over. */
    private Rect curveArea;

    private void drawFooter(GuiGraphics graphics) {
        GuiText.drawWrappedCentered(graphics, this.font, hint(), this.width / 2,
                this.height - bottomBar() + GAP, this.width - MARGIN * 2, DIM);
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
        return clickAt(mouseX, mouseY, button, hasShiftDown());
    }

    /**
     * A click on the box or the deck list, with the modifier said out loud.
     * <p>Split out from {@link #mouseClicked} so the harness can press shift as well as the
     * mouse: {@code hasShiftDown()} reads the window's real keyboard, which a scene driving
     * the client cannot set - and a shortcut nothing can press is a shortcut nothing checks.
     */
    boolean clickAt(double mouseX, double mouseY, int button, boolean shift) {
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
                if (shift) {
                    addEvery(row);
                } else {
                    add(row);
                }
            }
            return true;
        }

        // Inside the list before any row is asked about. Every row of the deck gets a hit
        // rectangle, including the ones scrolled above the top or below the fold - they are
        // only skipped when drawing - so a row nobody can see kept a strip of the screen and
        // the mana curve and the footer are drawn on top of exactly that strip. A click on
        // either removed a card from the deck, silently, with nothing under the pointer that
        // looked like a card at all.
        if (deckList().contains(x, y)) {
            for (DeckRow row : deckRows) {
                if (row.at().contains(x, y)) {
                    build = build.without(row.card().printing());
                    GatheringButtons.clickSound();
                    return true;
                }
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

    // ------------------------------------------------------- for the harness

    /** How many cards the box pane is showing. */
    int showing() {
        return Math.min(rows.size(), grid().cells());
    }

    /** Clicks the nth card of the box pane, through the same path a mouse takes. */
    void clickCard(int index, int button) {
        clickCard(index, button, false);
    }

    /** The same, with shift held. */
    void clickCard(int index, int button, boolean shift) {
        Rect cell = grid().cellAt(index);
        clickAt(cell.centerX(), cell.centerY(), button, shift);
    }

    /** How many copies of the nth card are still to be had. For the harness. */
    int leftOf(int index) {
        return index < 0 || index >= rows.size() ? 0 : leftInTheBox(rows.get(index));
    }

    /** Whether this builder is over the player's own pockets rather than a box. */
    boolean overThePockets() {
        return fromPockets();
    }

    int deckSize() {
        return build.total();
    }

    /** How many cards the curve is counting - everything in the deck that is not a land. */
    int curveTotal() {
        int total = 0;
        for (int count : build.curve()) {
            total += count;
        }
        return total;
    }

    String commanderName() {
        return build.commander().map(BuildCard::name).orElse("");
    }
}
