package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.CommandSlots;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.ui.CardShape;
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

    private static final int MARGIN = 14;
    private static final int GAP = 4;
    private static final int HEADER = 16;

    /**
     * The room above the cards on this screen, which is the title and sometimes a line more.
     *
     * <p>A scry writes a number on every card and the numbers are an order with no stated
     * direction, so it also writes what the order means - and that line needs room reserved
     * for it or it lands on the title. Every other pile has nothing to say there and gets the
     * title's height and no more.
     */
    private int header = HEADER;

    /** Room under the cards for the hint and the Done button, stacked. */
    private static final int FOOTER = 40;

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

    /**
     * The revealed cards in the order the player has put them.
     *
     * <p>Only a decision screen has one. Reordering the cards you keep is half of what a
     * scry is - the whole point of looking at three is deciding which of them you draw
     * first - and until this existed they went back in the order they came off, which is a
     * legal choice but never the player's.
     */
    private final List<CardInstanceId> order = new java.util.ArrayList<>();

    /** The card a press landed on, and whether the press has become a drag. */
    private int pressed = -1;
    private int pressedX;
    private boolean dragged;

    /**
     * How much of the window a pile may take at its very largest.
     *
     * <p>It is a box holding cards, so it is the size of the cards it holds - a graveyard
     * with one card in it is a small box - and it stops growing here. Past that it scrolls,
     * because a hundred-card library drawn whole would be the whole window again.
     */
    private static final double MOST_OF_THE_WINDOW = 0.72;

    /** How many cards the box was built to hold, so it can notice more arriving. */
    private int sizedFor = -1;

    private Rect panel = Rect.NONE;
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
        header = HEADER + (decision == null ? 0 : this.font.lineHeight + 2);
        int cardWidth = Math.max(8, CardShape.widthFor(CARD_HEIGHT));
        sizedFor = cards().size();
        int held = Math.max(1, sizedFor);

        int roomAcross = Math.max(cardWidth + MARGIN * 2, (int) (this.width * MOST_OF_THE_WINDOW));
        int roomDown = Math.max(CARD_HEIGHT + MARGIN * 2 + header + FOOTER,
                (int) (this.height * MOST_OF_THE_WINDOW));

        columns = Math.max(1, Math.min(held, (roomAcross - MARGIN * 2 + GAP) / (cardWidth + GAP)));
        int rows = (held + columns - 1) / columns;
        int shown = Math.max(1, Math.min(rows,
                (roomDown - MARGIN * 2 - header - FOOTER + GAP) / (CARD_HEIGHT + GAP)));
        // A pile with nothing in it needs room for a sentence, not for a card. Reserving a
        // card row for a card that is not there gave an empty graveyard a box the size of a
        // full one, which is the whole window's worth of nothing this screen was shrunk to
        // stop doing.

        // Wide enough for the cards, and for the writing above and below them, and no wider.
        int wanted = Math.max(
                columns * (cardWidth + GAP) - GAP + MARGIN * 2,
                Math.min(roomAcross, widestLine(rows > shown) + MARGIN * 2));
        int inTheGrid = sizedFor == 0
                ? this.font.lineHeight * 2
                : shown * (CARD_HEIGHT + GAP) - GAP;
        int tall = inTheGrid + MARGIN * 2 + header + FOOTER;
        panel = new Rect(
                (this.width - wanted) / 2, Math.max(0, (this.height - tall) / 2), wanted, tall);
        // Centered rather than packed to the left. The box is usually as wide as the sentence
        // under it rather than as wide as its cards, so a graveyard holding one card put that
        // card in the top-left corner of a box four times its width, and read as a panel that
        // had failed to draw the rest of itself.
        int across = columns * (cardWidth + GAP) - GAP;
        grid = new Rect(panel.x() + (panel.width() - across) / 2, panel.y() + MARGIN + header,
                across, panel.height() - MARGIN * 2 - header - FOOTER);
        scroll = Math.min(scroll, hiddenBelow());

        // At the bottom, centered, like the Done on every other screen in the mod. Beside the
        // title it was a second thing competing with the heading for the same line, and it
        // was the only screen here that put its way out at the top.
        addRenderableWidget(GatheringButtons.of(
                panel.x() + (panel.width() - DONE_WIDTH) / 2,
                panel.bottom() - MARGIN - BUTTON_HEIGHT, DONE_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.done"),
                decision == null ? this::onClose : this::decide));
    }

    private static final int DONE_WIDTH = 60;

    private static final int BUTTON_HEIGHT = 16;

    /**
     * The widest thing this box has to fit, which is what sets how wide it is.
     *
     * <p>Worth knowing that this is usually the sentence under the cards rather than the
     * cards: a graveyard holding one card was given a box four cards wide because every hint
     * used to end by saying Escape closes the box. There is a Done button an inch above it
     * and Escape closes every panel in the mod, so those three words were costing more felt
     * than anything else in here.
     */
    private int widestLine(boolean scrolls) {
        int widest = this.font.width(heading());
        Component hint = footer(scrolls);
        return hint == null ? widest : Math.max(widest, this.font.width(hint));
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
            return;
        }
        // A scry opens before the cards it is about arrive - the look is an event and the
        // screen is the client's answer to it - so a box built to hold what was here when it
        // opened is a box built for nothing. It was the whole window before, which hid this:
        // the two cards that turned up after it opened were clipped off the bottom of a grid
        // one row tall, and a screen titled "Scry 3" showed one card.
        if (sizedFor != cards().size()) {
            rebuildWidgets();
        }
    }

    /**
     * Brings the player's order in line with what is actually revealed.
     *
     * <p>Cards already in it keep where the player put them; anything new goes on the end in
     * the order the library gave it. A scry's cards arrive after the screen opens, so this is
     * how they get here at all.
     */
    private void syncOrder() {
        List<CardInstanceId> revealed = new java.util.ArrayList<>();
        for (CardView card : cards()) {
            if (card instanceof CardView.Visible visible) {
                revealed.add(visible.id());
            }
        }
        order.retainAll(revealed);
        for (CardInstanceId id : revealed) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
    }

    /** The revealed cards, in the order the player has put them. */
    private List<CardView> inOrder() {
        List<CardView> cards = cards();
        if (decision == null) {
            return cards;
        }
        syncOrder();
        List<CardView> sorted = new java.util.ArrayList<>(cards.size());
        for (CardInstanceId id : order) {
            for (CardView card : cards) {
                if (card instanceof CardView.Visible visible && visible.id().equals(id)) {
                    sorted.add(card);
                    break;
                }
            }
        }
        // Anything face down to this client has no id to order by, so it keeps its place.
        for (CardView card : cards) {
            if (!(card instanceof CardView.Visible) && !sorted.contains(card)) {
                sorted.add(card);
            }
        }
        return sorted;
    }

    /** Where in the pile going back on top this card will be, or 0 if it is not going there. */
    private int placeOnTop(CardView card) {
        if (decision == null || isSentAway(card)) {
            return 0;
        }
        int place = 0;
        for (CardView other : inOrder()) {
            if (!isSentAway(other)) {
                place++;
            }
            if (other == card) {
                return place;
            }
        }
        return 0;
    }

    /**
     * What this box is called.
     *
     * <p>While a decision is being made only the revealed cards are on screen, so naming the
     * whole pile and its size would claim to be showing more than is actually there.
     */
    private Component heading() {
        return decision == null
                ? Component.translatable("screen.gathering.pile.title", this.title, count())
                : Component.translatable(decision.title, cards().size());
    }

    /**
     * The line under the cards, or nothing when there is nothing true to say.
     *
     * <p>Nothing to click and nothing to scroll, so the footer says neither. A pile with no
     * cards in it that still reads "click a card to move it" is an instruction for something
     * that cannot be done here, which is how a screen teaches somebody that its own writing
     * is not worth reading - and "scroll for more" goes when there is no more, which is the
     * same small lie in the other direction.
     */
    private Component footer(boolean scrolls) {
        if (decision != null) {
            return Component.translatable("screen.gathering.pile.deciding",
                    Component.translatable(decision.away), sendingAway.size());
        }
        if (cards().isEmpty()) {
            return null;
        }
        // A watcher has no seat, so nothing in here is theirs to move. Offering them the
        // click is the same small lie as offering a scroll with nothing below the fold, and
        // this one they would actually try.
        if (mySeat().isEmpty()) {
            return Component.translatable("screen.gathering.pile.hint_watching");
        }
        return Component.translatable(scrolls
                ? "screen.gathering.pile.hint_scrolling"
                : "screen.gathering.pile.hint");
    }

    // ---------------------------------------------------------------- render

    /** The board behind, then the scrim, then the box this screen actually is. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        List<CardView> cards = inOrder();
        GuiText.drawCentered(graphics, this.font, heading(),
                panel.x() + panel.width() / 2, panel.y() + MARGIN,
                panel.width() - MARGIN * 2, LABEL);

        ClientHoverState.clear();
        if (cards.isEmpty()) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable(count() == 0
                            ? "screen.gathering.pile.empty"
                            : "screen.gathering.pile.not_yours"),
                    panel.x() + panel.width() / 2, grid.y() + grid.height() / 2,
                    grid.width(), DIM);
        }

        // Clipped to the grid. Skipping the rows that are entirely outside it is not enough:
        // a row scrolled half out of view is not entirely outside, so it was drawn whole and
        // hung out over the edge of the panel - which is what "the cards escape the menu"
        // looks like. The deck screen already clips its list; this is the same fix.
        // What the numbers on the cards mean, written where the numbers are. "1, 2, 3" on a
        // row of cards is an order and says nothing about which end of the library it runs
        // to - so a player deciding a scry has to guess whether 1 is the card they draw next
        // or the one that goes back first. One short line removes the guess.
        if (decision != null && !cards.isEmpty()) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.pile.order_hint"),
                    panel.x() + panel.width() / 2, grid.y() - this.font.lineHeight - 1,
                    panel.width() - MARGIN * 2, DIM);
        }
        graphics.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
        for (int index = 0; index < cards.size(); index++) {
            Rect slot = slotOf(index);
            if (slot.isEmpty() || slot.bottom() < grid.y() || slot.y() > grid.bottom()) {
                continue;
            }
            boolean hovered = slot.contains(mouseX, mouseY);
            drawCard(graphics, cards.get(index), slot, hovered);
            // The order they will be drawn in, written on them. A row of cards that go back
            // in the order they are lying in only says so if it says so: without a number,
            // dragging one to a new place looks like it did nothing.
            int place = placeOnTop(cards.get(index));
            if (place > 0) {
                Component said = Component.literal(Integer.toString(place));
                int badge = this.font.width(said) + 4;
                GatheringSprites.draw(graphics, Element.SENT_AWAY, slot.x() + 2, slot.y() + 2,
                        badge, this.font.lineHeight + 1);
                GuiText.drawCentered(graphics, this.font, said,
                        slot.x() + 2 + badge / 2, slot.y() + 3, badge, LABEL);
            }
            if (hovered && cards.get(index) instanceof CardView.Visible visible) {
                ClientHoverState.setHovered(CardItem.of(CardComponent.of(visible.identity())));
            }
        }
        drawWhereItWouldLand(graphics, cards.size());
        graphics.disableScissor();

        Component hint = footer(hiddenBelow() > 0);
        if (hint != null) {
            GuiText.drawCentered(graphics, this.font, hint,
                    panel.x() + panel.width() / 2,
                    panel.bottom() - MARGIN - BUTTON_HEIGHT - this.font.lineHeight - 4,
                    panel.width() - MARGIN * 2, DIM);
        }

        if (menu != null) {
            menu.render(graphics, this.font, mouseX, mouseY);
        }
    }

    /**
     * Sends the decision and closes.
     *
     * <p>The cards staying on top go back in the order the player put them in - dragged into
     * place on this screen, numbered as they will be drawn - and the rest go where this kind
     * of decision sends them.
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
        for (CardView card : inOrder()) {
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

    /** What the owner of this pile has their cards sleeved in. */
    private dev.gathering.core.card.Sleeve sleeve() {
        return CardSleeves.of(ClientTableState.viewOf(table).orElse(null), owner);
    }

    /** Whether this card is currently marked to be sent away rather than kept on top. */
    private boolean isSentAway(CardView card) {
        return card instanceof CardView.Visible visible && sendingAway.contains(visible.id());
    }

    /** The card, and a ring if the cursor is on it. Nothing drawn round it - see TableScreen. */
    private void drawCard(GuiGraphics graphics, CardView card, Rect where, boolean hovered) {
        Rect art = where;
        boolean away = isSentAway(card);

        if (card.isFaceDown()) {
            // The pile's owner's sleeves, because that is whose pile this screen opened.
            CardSleeves.draw(graphics, sleeve(), art.x(), art.y(), art.width(), art.height());
        } else {
            // Whichever side the table has it turned to. A card looked at through this
            // screen and the same card on the felt have to be the same card.
            summaryOf(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, card.turnedOver(),
                            art.x(), art.y(), art.width(), art.height()),
                    () -> PaperFace.drawOrInset(graphics, this.font, card, art));
        }
        // And what somebody wrote on it, across the top, drawn by the same method the felt
        // uses so the two can never come to look like different features - except on blank
        // stock, where the writing is the card and has already been drawn across all of it.
        if (!PaperFace.isPaper(card)) {
            CardInspectPanel.drawNote(graphics, this.font, card.writtenOn().orElse(null), art);
        }
        // And the numbers in the corner, for the same reason: a card looked at through this
        // screen and the same card on the felt have to be the same card.
        CardInspectPanel.drawStrength(
                graphics, this.font, card.writtenStrength().orElse(null), art);
        if (away) {
            // Grayed rather than moved. A card that jumped to another row every time somebody
            // changed their mind would make a scry of three a puzzle about where things went.
            GatheringSprites.draw(graphics, Element.SENT_AWAY,
                    art.x(), art.y(), art.width(), art.height());
        }
        if (hovered) {
            GatheringSprites.draw(graphics, Element.FOCUS_RING,
                    where.x(), where.y(), where.width(), where.height());
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

    // ---------------------------------------------------- the scripted harness
    // Worth asking about because the box is built to the number of cards it had when it
    // opened, and a scry's cards arrive after it opens - so that is exactly the thing that
    // goes wrong when the box forgets to grow.

    /** Where a card in this box is, so the harness can click the card and not a pixel. */
    Rect slotOfCard(int index) {
        return index < 0 || index >= cards().size() ? Rect.NONE : slotOf(index);
    }

    /**
     * The cards going back on top, in the order they will go, by id. For the harness.
     *
     * <p>Ids rather than names, because a name is looked up and a run without a network has
     * none - and what is being checked is an ordering, which ids carry perfectly well.
     */
    List<CardInstanceId> orderOnTop() {
        List<CardInstanceId> kept = new java.util.ArrayList<>();
        for (CardView card : inOrder()) {
            if (!isSentAway(card) && card instanceof CardView.Visible visible) {
                kept.add(visible.id());
            }
        }
        return kept;
    }

    /** What the line under the cards says right now. For the harness, as above. */
    String footerSays() {
        Component said = footer(hiddenBelow() > 0);
        return said == null ? "" : said.getString();
    }

    /**
     * How wide this box came out. For the harness.
     *
     * <p>The one number that says whether it is behaving: the box is meant to be the size of
     * what it holds, and what it holds is usually one or two cards. It is the writing that
     * runs away with it - every hint used to end by saying Escape closes the box, and those
     * three words made a one-card graveyard into a panel covering half the table.
     */
    int panelWidth() {
        return panel.width();
    }

    /** How many cards have been marked to send away. For the harness, as above. */
    int markedToSendAway() {
        return sendingAway.size();
    }

    /** Whether every card this box holds is actually in it. For the harness, as above. */
    boolean everyCardIsOnScreen() {
        int held = cards().size();
        if (held == 0) {
            return true;
        }
        Rect last = slotOf(held - 1);
        return !last.isEmpty() && last.y() >= grid.y() && last.bottom() <= grid.bottom();
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

        List<CardView> cards = inOrder();
        for (int index = 0; index < cards.size(); index++) {
            if (!slotOf(index).contains(x, y)) {
                continue;
            }
            if (cards.get(index) instanceof CardView.Visible visible) {
                if (mySeat().isEmpty()) {
                    // No seat, nothing to move it with. The sound is the answer to "did that
                    // do anything", so it is only made when the answer is yes.
                    return true;
                }
                if (decision != null) {
                    // Held rather than acted on, because this press might be the start of a
                    // drag. Which of the two it was is not known until the button comes up.
                    pressed = index;
                    pressedX = x;
                    dragged = false;
                } else if (mayMoveFromHere()) {
                    GatheringButtons.clickSound();
                    openMenu(visible, x, y);
                }
            }
            return true;
        }
        // A click on the board behind puts the box away, which is what clicking off any
        // popup does and what somebody who has finished reading a graveyard is doing
        // anyway. Not while a decision is open: a scry half-decided is not a thing to
        // dismiss by accident, and it has a Done button that says what it will do.
        if (decision == null && !panel.contains(x, y)) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * A press that has moved far enough sideways is a drag, and drags reorder.
     *
     * <p>Far enough, rather than at all, because a click made with a mouse in somebody's hand
     * moves a pixel or two - and a scry where every click also shuffled the row would be
     * unusable.
     */
    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressed >= 0 && Math.abs(mouseX - pressedX) > A_REAL_DRAG) {
            dragged = true;
        }
        if (dragged) {
            draggingAtX = (int) mouseX;
            draggingAtY = (int) mouseY;
        }
        return pressed >= 0 || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressed < 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        int from = pressed;
        boolean moved = dragged;
        pressed = -1;
        dragged = false;

        List<CardView> cards = inOrder();
        if (from >= cards.size() || !(cards.get(from) instanceof CardView.Visible visible)) {
            return true;
        }
        GatheringButtons.clickSound();
        if (!moved) {
            // A click says which side of the decision this card is on, and clicking it again
            // changes your mind.
            if (!sendingAway.remove(visible.id())) {
                sendingAway.add(visible.id());
            }
            return true;
        }
        // The same gap the bar promised, through the same rule - and with the shift from
        // taking the card out accounted for. Reading the slot directly here dropped the
        // card one gap right of the bar on every rightward drag: after the removal, every
        // index right of the card had moved down one, and the insert landed past the gap.
        int gap = gapUnder((int) mouseX, (int) mouseY, cards.size());
        int insert = gap > from ? gap - 1 : gap;
        order.remove(visible.id());
        order.add(Math.min(insert, order.size()), visible.id());
        return true;
    }

    /**
     * The gap a drag at this point is aimed at.
     *
     * <p>Gap {@code g} is the space before slot {@code g}; {@code howMany} means past the
     * end of the row. One rule, asked by the landing bar and the release alike, because the
     * bar is a promise about the release and two copies of a promise drift.
     */
    private int gapUnder(int x, int y, int howMany) {
        int landing = Math.max(0, Math.min(howMany - 1, slotUnder(x, y)));
        Rect slot = slotOf(landing);
        // Past the card it is over when the cursor is on the last card's right-hand half:
        // dropping "on" the last card has to be able to mean after it, or the far end of
        // the row is unreachable.
        boolean after = landing == howMany - 1 && !slot.isEmpty() && x > slot.centerX();
        return after ? howMany : landing;
    }

    /** How far a press has to travel sideways before it counts as a drag. */
    private static final int A_REAL_DRAG = 4;

    /** Where a drag in progress is, so the row can show where the card would go. */
    private int draggingAtX;

    private int draggingAtY;

    private static final int LANDING_WIDTH = 3;

    /**
     * Where the card being dragged would land, drawn while it is still being dragged.
     *
     * <p>Without it the player is aiming at something they cannot see: the row does not move
     * under the cursor, so a drag looks like nothing at all until it is let go and the
     * numbers jump. A bar in the gap the card would drop into answers the question while it
     * is still being asked, which is the only time the answer is any use.
     *
     * <p>Drawn in the gap rather than over a card on purpose. This reorders - the card goes
     * <em>between</em> two others - and a highlight on a card would read as "swap with this
     * one", which is a different move and not the one that happens.
     */
    private void drawWhereItWouldLand(GuiGraphics graphics, int howMany) {
        if (!dragged || howMany == 0) {
            return;
        }
        int gap = gapUnder(draggingAtX, draggingAtY, howMany);
        boolean after = gap == howMany;
        Rect slot = slotOf(after ? howMany - 1 : gap);
        if (slot.isEmpty()) {
            return;
        }
        int edge = after ? slot.right() + GAP / 2 : slot.x() - GAP / 2;
        GatheringSprites.draw(graphics, Element.DRAG_LANDING, edge - LANDING_WIDTH / 2,
                slot.y() - 2, LANDING_WIDTH, slot.height() + 4);
    }

    /** Which slot of the grid a point is over, whether or not a card is in it. */
    private int slotUnder(int x, int y) {
        int cardWidth = Math.max(8, CardShape.widthFor(CARD_HEIGHT));
        int column = Math.max(0, Math.min(columns - 1, (x - grid.x()) / (cardWidth + GAP)));
        int row = Math.max(0, (y + scroll - grid.y()) / (CARD_HEIGHT + GAP));
        return row * columns + column;
    }

    /**
     * Whether a card read here is one this viewer could actually pick up.
     *
     * <p>A hand somebody has turned toward you is a hand you may read and not one you may
     * reach into - {@link dev.gathering.core.game.Authorization} refuses a move out of
     * somebody else's hidden zone, because naming a card inside one means having seen it. So
     * the menu does not open at all rather than offering seven moves the server will refuse
     * in silence. Nothing promised is nothing broken.
     */
    private boolean mayMoveFromHere() {
        return !zone.isHidden() || mySeat().filter(owner::equals).isPresent();
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
        // The slot it belongs in rather than always the first, now that there are two of
        // them: sending a partner home to a slot the other commander is already in puts two
        // cards in one box and leaves the other empty.
        if (!zone.isCommandSlot() && view().isPresent()) {
            entries.add(move(me, card,
                    CommandSlots.homeFor(view().get().seat(card.owner())),
                    Placement.TOP, "to_command"));
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
        scroll = Math.max(0, Math.min(hiddenBelow(), scroll - (int) scrollY * (CARD_HEIGHT / 3)));
        return true;
    }

    /** How much of the grid is off the bottom, which is how far a scroll can get. */
    private int hiddenBelow() {
        int rows = (cards().size() + columns - 1) / columns;
        return Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP - grid.height());
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
