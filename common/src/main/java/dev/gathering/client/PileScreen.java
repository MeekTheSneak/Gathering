package dev.gathering.client;

import dev.gathering.core.ui.CardShape;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * A pile, spread out so you can read it and take things out of it.
 *
 * <p>A graveyard is not a number. Half of what makes a game of Magic legible is being able to
 * point at somebody's yard and count the creatures in it, and the other half is being able to
 * reach into your own and get one back. This is both: every card face up in a grid, click one
 * to pick it, right-click for everywhere it can go.
 *
 * <p>Also how a library is read, on the rare occasions anybody is entitled to. It shows what
 * the server sent and nothing more - a library it was not told is simply an empty grid with a
 * count, which is the honest picture of a deck you may not look at. Closing the screen tells
 * the server to shut the library again, because whether it is open is the server's business
 * and a client that merely stopped drawing one would still be being sent it.
 *
 * <p>Client-only.
 */
public final class PileScreen extends ChildScreen implements CardPreviewHost {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int ACCENT = 0xFF6FD3E8;

    /** Over a card the player has said they do not want on top. */
    private static final int SENT_AWAY = 0xB0101418;

    private static final int MARGIN = 14;
    private static final int GAP = 4;
    private static final int HEADER = 16;
    private static final int FOOTER = 14;

    /** Big enough to read the name off the art without opening the inspector. */
    private static final int CARD_HEIGHT = 84;

    private final BlockPos table;
    private final SeatId owner;
    private final Zone zone;

    /** True when this screen opened a library and therefore has to close it again. */
    private final boolean opensALibrary;

    /**
     * What this screen is a decision about, or nothing when it is only a pile being read.
     *
     * <p>Looking at the top of your library is half of a scry. The other half is saying what
     * happens to each card, and until that is said the cards are still sitting where they
     * were - which is what a scry that only ever revealed them amounted to.
     */
    public enum Decision {
        /** Scry: the ones you do not keep go to the bottom of the library. */
        SCRY("screen.gathering.pile.scrying", "screen.gathering.pile.to_bottom"),
        /** Surveil: the ones you do not keep go to the graveyard. */
        SURVEIL("screen.gathering.pile.surveilling", "screen.gathering.pile.to_graveyard");

        /** What to call the screen: the decision being made, not the pile it came off. */
        private final String title;
        /** Where a card the player clicks is going. */
        private final String away;

        Decision(String title, String away) {
            this.title = title;
            this.away = away;
        }
    }

    private final Decision decision;

    /** The cards the player has said they do not want on top. Order is not asked about. */
    private final java.util.Set<CardInstanceId> sendingAway = new java.util.LinkedHashSet<>();

    private Rect grid = Rect.NONE;
    private int columns = 1;
    private int scroll;
    private ContextMenu menu;

    public PileScreen(BlockPos table, SeatId owner, Zone zone, boolean opensALibrary, Screen back) {
        this(table, owner, zone, opensALibrary, null, back);
    }

    public PileScreen(
            BlockPos table, SeatId owner, Zone zone, boolean opensALibrary,
            Decision decision, Screen back) {
        super(ZoneText.name(zone), back);
        this.table = table;
        this.owner = owner;
        this.zone = zone;
        this.opensALibrary = opensALibrary;
        this.decision = decision;
    }

    @Override
    protected void init() {
        int cardWidth = Math.max(8, CardShape.widthFor(CARD_HEIGHT));
        grid = new Rect(MARGIN, MARGIN + HEADER,
                this.width - MARGIN * 2, this.height - MARGIN * 2 - HEADER - FOOTER);
        columns = Math.max(1, (grid.width() + GAP) / (cardWidth + GAP));

        // Escape closes it, but a screen whose only way out is a key you have to know is a
        // screen somebody gets stuck in.
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - 54, MARGIN - 4, 54, 16,
                Component.translatable("gui.done"),
                decision == null ? this::onClose : this::decide));
    }

    // ------------------------------------------------------------- the cards

    private Optional<GameView> view() {
        return ClientTableState.viewOf(table);
    }

    private List<CardView> cards() {
        return view()
                .map(board -> board.seat(owner).zone(zone))
                .map(ZoneView::cards)
                .orElse(List.of());
    }

    private int count() {
        return view()
                .map(board -> board.seat(owner).zone(zone))
                .map(ZoneView::count)
                .orElse(0);
    }

    private Optional<SeatId> mySeat() {
        return ClientTableState.seatAt(table);
    }

    @Override
    public void tick() {
        super.tick();
        if (view().isEmpty()) {
            this.onClose();
        }
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, MARGIN - 6, MARGIN - 6,
                this.width - (MARGIN - 6) * 2, this.height - (MARGIN - 6) * 2);

        List<CardView> cards = cards();
        // While a decision is being made only the revealed cards are on screen, so naming the
        // whole pile and its size would claim to be showing more than is actually there.
        Component heading = decision == null
                ? Component.translatable("screen.gathering.pile.title", this.title, count())
                : Component.translatable(decision.title, cards.size());
        GuiText.draw(graphics, this.font, heading,
                MARGIN, MARGIN, this.width - MARGIN * 2, LABEL);

        ClientHoverState.clear();
        if (cards.isEmpty()) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable(count() == 0
                            ? "screen.gathering.pile.empty"
                            : "screen.gathering.pile.not_yours"),
                    this.width / 2, grid.y() + grid.height() / 2, grid.width(), DIM);
        }

        for (int index = 0; index < cards.size(); index++) {
            Rect slot = slotOf(index);
            if (slot.isEmpty() || slot.bottom() < grid.y() || slot.y() > grid.bottom()) {
                continue;
            }
            boolean hovered = slot.contains(mouseX, mouseY);
            drawCard(graphics, cards.get(index), slot, hovered);
            if (hovered && cards.get(index) instanceof CardView.Visible visible) {
                ClientHoverState.setHovered(CardItem.of(CardComponent.of(visible.identity())));
            }
        }

        // Nothing to click and nothing to scroll, so the footer says neither. A pile with no
        // cards in it that still reads "click a card to move it   scroll for more" is three
        // instructions for things that cannot be done here, which is how a screen teaches
        // somebody that its own writing is not worth reading.
        Component hint = decision != null
                ? Component.translatable("screen.gathering.pile.deciding",
                        Component.translatable(decision.away), sendingAway.size())
                : cards.isEmpty() ? null : Component.translatable("screen.gathering.pile.hint");
        if (hint != null) {
            GuiText.draw(graphics, this.font, hint,
                    MARGIN, this.height - MARGIN - FOOTER + 3, this.width - MARGIN * 2, DIM);
        }

        if (menu != null) {
            menu.render(graphics, this.font, mouseX, mouseY);
        }
    }

    /**
     * Sends the decision and closes.
     *
     * <p>The cards staying on top keep the order they were revealed in, which is what "leave
     * them" means; the rest go where this kind of decision sends them. Reordering the ones you
     * keep is a real part of a scry and is not here yet - the event carries an order, so it is
     * a gesture on this screen and not a change to anything underneath it.
     *
     * <p>Deciding is also the end of looking, which the fold takes care of: the peek is
     * dropped by the same event, so this screen must not also close the library behind it.
     */
    private void decide() {
        SeatId me = mySeat().orElse(null);
        if (me == null || decision == null) {
            onClose();
            return;
        }
        List<CardInstanceId> onTop = new java.util.ArrayList<>();
        List<CardInstanceId> away = new java.util.ArrayList<>();
        for (CardView card : cards()) {
            if (card instanceof CardView.Visible visible) {
                (sendingAway.contains(visible.id()) ? away : onTop).add(visible.id());
            }
        }
        ClientTableActions.send(table, switch (decision) {
            case SCRY -> new GameEvent.LibraryReordered(me, me, onTop, away);
            case SURVEIL -> new GameEvent.Surveiled(me, me, onTop, away);
        });
        decided = true;
        onClose();
    }

    /** Whether the decision has been sent, so closing does not also close the library again. */
    private boolean decided;

    /** Whether this card is currently marked to be sent away rather than kept on top. */
    private boolean isSentAway(CardView card) {
        return card instanceof CardView.Visible visible && sendingAway.contains(visible.id());
    }

    /** The card, and a ring if the cursor is on it. Nothing drawn round it - see TableScreen. */
    private void drawCard(GuiGraphics graphics, CardView card, Rect where, boolean hovered) {
        Rect art = where;
        boolean away = isSentAway(card);

        if (card.isFaceDown()) {
            graphics.blit(CardFaceRenderer.CARD_BACK, art.x(), art.y(), 0f, 0f,
                    art.width(), art.height(), art.width(), art.height());
        } else {
            summaryOf(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, art.x(), art.y(), art.width(), art.height()),
                    () -> GatheringSprites.inset(graphics, art.x(), art.y(), art.width(), art.height()));
        }
        if (away) {
            // Greyed rather than moved. A card that jumped to another row every time somebody
            // changed their mind would make a scry of three a puzzle about where things went.
            graphics.fill(art.x(), art.y(), art.right(), art.bottom(), SENT_AWAY);
        }
        if (hovered) {
            graphics.renderOutline(where.x(), where.y(), where.width(), where.height(), ACCENT);
        }
    }

    private Rect slotOf(int index) {
        int cardWidth = Math.max(8, CardShape.widthFor(CARD_HEIGHT));
        int column = index % columns;
        int row = index / columns;
        return new Rect(
                grid.x() + column * (cardWidth + GAP),
                grid.y() + row * (CARD_HEIGHT + GAP) - scroll,
                cardWidth,
                CARD_HEIGHT);
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (menu != null) {
            ContextMenu open = menu;
            menu = null;
            if (open.mouseClicked(x, y)) {
                return true;
            }
        }

        List<CardView> cards = cards();
        for (int index = 0; index < cards.size(); index++) {
            if (!slotOf(index).contains(x, y)) {
                continue;
            }
            if (cards.get(index) instanceof CardView.Visible visible) {
                GatheringButtons.clickSound();
                if (decision != null) {
                    // Deciding, not moving: a click says which side of the decision this card
                    // is on, and clicking it again changes your mind.
                    if (!sendingAway.remove(visible.id())) {
                        sendingAway.add(visible.id());
                    }
                } else {
                    openMenu(visible, x, y);
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Everywhere one card in a pile can go.
     *
     * <p>The same list on a left-click as on a right-click, because there is no obvious
     * default: taking a card out of your graveyard means the battlefield about as often as it
     * means your hand, and guessing wrong puts a card somewhere everybody watched it go.
     */
    private void openMenu(CardView.Visible card, int x, int y) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return;
        }
        List<ContextMenu.Entry> entries = ContextMenu.entries();
        entries.add(move(me, card, Zone.BATTLEFIELD, Placement.BOTTOM, "to_battlefield"));
        entries.add(move(me, card, Zone.HAND, Placement.BOTTOM, "to_hand"));
        if (zone != Zone.GRAVEYARD) {
            entries.add(move(me, card, Zone.GRAVEYARD, Placement.TOP, "to_graveyard"));
        }
        if (zone != Zone.EXILE) {
            entries.add(move(me, card, Zone.EXILE, Placement.TOP, "to_exile"));
        }
        if (zone != Zone.COMMAND) {
            entries.add(move(me, card, Zone.COMMAND, Placement.TOP, "to_command"));
        }
        entries.add(move(me, card, Zone.LIBRARY, Placement.TOP, "to_library_top"));
        entries.add(move(me, card, Zone.LIBRARY, Placement.BOTTOM, "to_library_bottom"));
        menu = ContextMenu.at(this.font, x, y, this.width, this.height, entries);
    }

    private ContextMenu.Entry move(
            SeatId actor, CardView.Visible card, Zone to, Placement placement, String key) {
        return ContextMenu.Entry.of(
                Component.translatable("menu.gathering.table." + key),
                () -> ClientTableActions.send(table, new GameEvent.CardMoved(
                        actor, card.id(), ZoneRef.of(card.owner(), to), placement)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = (cards().size() + columns - 1) / columns;
        int content = Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP - grid.height());
        scroll = Math.max(0, Math.min(content, scroll - (int) scrollY * (CARD_HEIGHT / 3)));
        return true;
    }

    private Optional<CardSummary> summaryOf(CardView card) {
        if (!(card instanceof CardView.Visible visible)) {
            return Optional.empty();
        }
        return ClientCardCache.get().summary(CardComponent.of(visible.identity()));
    }

    @Override
    public void onClose() {
        // The library was opened by an event and has to be closed by one. A screen that just
        // went away would leave the server sending this client a library nobody is reading.
        // Deciding already ended the look - the same event drops the peek - so closing after
        // a decision must not send a second one.
        if (opensALibrary && !decided) {
            mySeat().ifPresent(me -> ClientTableActions.send(table, new GameEvent.LibraryClosed(me)));
        }
        super.onClose();
    }
}
