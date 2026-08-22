package dev.gathering.client;

import com.mojang.math.Axis;
import dev.gathering.core.game.CardInstance;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.TableScreenLayout;
import dev.gathering.core.ui.TableDrag;
import dev.gathering.core.ui.TableStacking;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The seat you play from.
 *
 * <p>Four bands: the other seats across the top, the table surface in the middle, your zones
 * beside it and your hand along the bottom. The surface shows one seat's board at a time -
 * yours to begin with, anybody's if you click them - because a legible board somebody is
 * actually looking at beats every board at once rendered too small to read. Any public card
 * on any board can be moved from here, which is the paper-Magic rule the design keeps: the
 * mod never says no, and the log says who did it.
 *
 * <p><b>There is no grid.</b> A card is an object you pick up and put down: it goes exactly
 * where you dropped it, at exactly the angle you left it at, over or under whatever is
 * already there. Overlapping a creature with an aura, fanning a pile out, turning something
 * sideways to mean "this one is attacking" - none of those are features, they are what
 * happens when the table stops having opinions about where things go. Everything else lives
 * in a menu on the card, because the alternative is fourteen keyboard shortcuts nobody
 * remembers.
 *
 * <p>The screen draws only what it was sent. It never asks the game anything, because it has
 * not been told the game - it has been told a {@code GameView}, which is the board with
 * everything this player is not entitled to already removed. An opponent's hand is a number
 * here because a number is all that arrived.
 *
 * <p>Client-only.
 */
public final class TableScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int ACCENT = 0xFF6FD3E8;
    private static final int TAPPED_TINT = 0x60000000;
    private static final int GHOST_TINT = 0x50000000;
    private static final int COUNTER_TEXT = 0xFFFFE9A8;

    /**
     * The shadow every card on the table casts.
     *
     * <p>Nothing here has thickness, so without this a card lying across another one is two
     * flat pictures sharing an edge and you cannot tell which is on top. A shadow is the
     * cheapest possible answer and the one every real table already gives you.
     */
    private static final int SHADOW = 0x70000000;
    private static final int SHADOW_OFFSET = 2;

    /** The badge on a pile, saying how many cards are in it. */
    private static final int PILE_BADGE = 0xE0141210;
    private static final int PILE_TEXT = 0xFFF2EEE6;

    /** The rubber band drawn while picking out several cards at once. */
    private static final int BOX_FILL = 0x206FD3E8;

    /** How far a card is turned by one nudge. Small enough to be a gesture, not a mode. */
    private static final int NUDGE_DEGREES = 15;

    /** An opening hand, for the mulligan the library menu offers. */
    private static final int MULLIGAN_HAND = 7;

    /** How far the cursor must travel before a press becomes a drag rather than a click. */
    private static final int DRAG_THRESHOLD = 3;

    /** Where the dragged card is drawn relative to the cursor while it is in the air. */
    private static final float LIFT = 300f;

    private final BlockPos table;

    private TableScreenLayout layout;
    private SeatId focused;

    /** The card in the air, if any, and where on it the cursor took hold. */
    private Held held;

    /**
     * The cards a player has picked out to act on together.
     *
     * <p>Purely this client's idea. Nothing about a selection reaches the server: what gets
     * sent is the same events one card at a time would have sent, so a selection cannot do
     * anything a sequence of ordinary moves could not, and the log reads the same either way.
     */
    private final Set<CardInstanceId> selected = new LinkedHashSet<>();

    /** The corner a box-select started from, while one is being dragged out. */
    private int[] boxFrom;

    private ContextMenu menu;

    public TableScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.table"));
        this.table = table;
    }

    /**
     * A card that has been picked up.
     *
     * <p>The grab offset is the whole reason this is a record rather than an id: a card that
     * snaps its corner to the cursor jumps out from under your finger the moment you touch
     * it, and putting something down where you are pointing is the one thing a table has to
     * get right.
     */
    private record Held(CardInstanceId card, boolean fromHand, int grabX, int grabY, int pressX, int pressY) {

        boolean hasMoved(int mouseX, int mouseY) {
            return Math.abs(mouseX - pressX) >= DRAG_THRESHOLD || Math.abs(mouseY - pressY) >= DRAG_THRESHOLD;
        }
    }

    @Override
    protected void init() {
        layout = TableScreenLayout.of(this.width, this.height, otherSeats().size());
        if (focused == null) {
            focused = mySeat().orElseGet(() -> view().map(v -> v.seats().get(0).seat()).orElse(new SeatId(0)));
        }
    }

    // ------------------------------------------------------------- the board

    private Optional<GameView> view() {
        return ClientTableState.viewOf(table);
    }

    private Optional<SeatId> mySeat() {
        return view().map(GameView::viewer)
                .filter(Viewer.Seated.class::isInstance)
                .map(Viewer.Seated.class::cast)
                .map(Viewer.Seated::seat);
    }

    private List<SeatView> otherSeats() {
        return view().map(board -> board.seats().stream()
                        .filter(seat -> mySeat().map(mine -> !mine.equals(seat.seat())).orElse(true))
                        .toList())
                .orElse(List.of());
    }

    @Override
    public void tick() {
        super.tick();
        // The game ended, or this client stopped being told about it.
        if (view().isEmpty()) {
            this.onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        TableScreenLayout current = layout();
        panel(graphics, current.opponents());
        panel(graphics, current.surface());
        panel(graphics, current.zones());
        panel(graphics, current.hand());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GameView board = view().orElse(null);
        if (board == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientHoverState.clear();
        renderOpponents(graphics, board);
        renderSurface(graphics, board, mouseX, mouseY);
        renderZones(graphics, board, mouseX, mouseY);
        renderHand(graphics, board, mouseX, mouseY);
        renderActions(graphics, board);
        renderHeldCard(graphics, board, mouseX, mouseY);

        if (boxFrom != null) {
            Rect box = boxBetween(boxFrom[0], boxFrom[1], mouseX, mouseY);
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), BOX_FILL);
            graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), ACCENT);
        }
        if (menu != null) {
            menu.render(graphics, this.font, mouseX, mouseY);
        }
    }

    /** The rectangle between two corners, whichever way round they were dragged. */
    private static Rect boxBetween(int fromX, int fromY, int toX, int toY) {
        return new Rect(
                Math.min(fromX, toX), Math.min(fromY, toY),
                Math.abs(toX - fromX), Math.abs(toY - fromY));
    }

    private void renderOpponents(GuiGraphics graphics, GameView board) {
        Rect area = layout().opponents();
        int line = area.y() + 3;
        for (SeatView seat : board.seats()) {
            if (line + this.font.lineHeight > area.bottom()) {
                break;
            }
            boolean isFocused = seat.seat().equals(focused);
            boolean mine = mySeat().map(seat.seat()::equals).orElse(false);

            String who = seat.occupant().map(player -> player.name())
                    .orElseGet(() -> Component.translatable("message.gathering.seat_empty").getString());
            Component text = Component.translatable(
                    "screen.gathering.table.seat_line",
                    who, seat.life(),
                    count(seat, Zone.HAND), count(seat, Zone.LIBRARY),
                    count(seat, Zone.BATTLEFIELD), count(seat, Zone.GRAVEYARD));
            // Poison and energy belong on the seat line rather than in a screen: they change
            // the game as much as life does and nobody goes looking for a number.
            if (!seat.counters().isEmpty()) {
                text = text.copy().append(Component.literal("  " + describeCounters(seat)));
            }

            if (isFocused) {
                GatheringSprites.highlight(graphics, area.x() + 1, line - 2, area.width() - 2,
                        this.font.lineHeight + 3);
            }
            GuiText.draw(graphics, this.font, text, area.x() + 4, line, area.width() - 8,
                    mine ? ACCENT : LABEL);
            line += this.font.lineHeight + 3;
        }
    }

    /**
     * The board, drawn back to front.
     *
     * <p>The zone's own order is the stacking order, so a card played onto another one lies on
     * top of it and the last thing you touched is the thing you can pick up again. Hit-testing
     * runs the other way for the same reason.
     */
    private void renderSurface(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        List<CardView> cards = board.seat(focused).zone(Zone.BATTLEFIELD).cards();
        List<TablePosition> spots = spotsIn(cards);
        List<Integer> depths = TableStacking.depths(spots);
        CardView hovered = cardOnSurfaceAt(board, mouseX, mouseY).orElse(null);

        for (int index = 0; index < cards.size(); index++) {
            CardView card = cards.get(index);
            if (isHeld(card)) {
                continue;
            }
            drawTableCard(graphics, card, spotOf(card, depths.get(index)),
                    card == hovered || isSelected(card));

            // Only the top card of a pile says how many are in it. Every card in the pile
            // agrees on the number, so saying it four times would be four badges on one stack.
            if (!TableStacking.isBuriedAt(spots, index)) {
                int pile = TableStacking.pileSizeAt(spots, index);
                if (pile > 1) {
                    drawPileBadge(graphics, spotOf(card, depths.get(index)), pile);
                }
            }
        }
        if (hovered != null) {
            offerToInspector(hovered);
        }
    }

    private static List<TablePosition> spotsIn(List<CardView> cards) {
        List<TablePosition> spots = new ArrayList<>(cards.size());
        for (CardView card : cards) {
            spots.add(card.placedAt().orElse(null));
        }
        return spots;
    }

    /**
     * Where a card on the surface is drawn.
     *
     * <p>Its own place, leaned up and to the left by however many cards are under it. The card
     * has not moved - that is state - but a pile that draws every card in exactly the same
     * pixels is a pile that looks like one card.
     */
    private Rect spotOf(CardView card, int depth) {
        Rect where = layout().cardAt(card.placedAt().orElse(TablePosition.ORIGIN));
        int lean = TableStacking.offsetFor(depth);
        return lean == 0 ? where : new Rect(where.x() + lean, where.y() + lean, where.width(), where.height());
    }

    /** The pile count, on the corner nearest the top of the stack. */
    private void drawPileBadge(GuiGraphics graphics, Rect where, int size) {
        Component label = Component.literal("x" + size);
        int width = this.font.width(label) + 5;
        int left = where.right() - width - 1;
        int top = where.y() + 1;
        graphics.fill(left, top, left + width, top + this.font.lineHeight + 1, PILE_BADGE);
        GuiText.draw(graphics, this.font, label, left + 2, top + 1, width, PILE_TEXT);
    }

    /**
     * The angle a card is drawn at: the angle it was left at, plus a quarter turn if tapped.
     *
     * <p>Tapping being a quarter turn on top of whatever angle the card already has is what
     * makes the two independent - a card you angled thirty degrees taps to a hundred and
     * twenty and untaps back to thirty, rather than forgetting you ever touched it.
     */
    private static int angleOf(CardView card) {
        int resting = card.placedAt().map(TablePosition::rotation).orElse(0);
        return card.tapped() ? resting + TablePosition.QUARTER_TURN : resting;
    }

    /**
     * Your piles, beside the table.
     *
     * <p>Card-shaped and stacked, with a count on each, because a pile of cards is a thing you
     * reach for and a row of numbers is a status bar. Clicking one does the obvious thing -
     * draw from the library, open everything else - and right-clicking one offers the rest.
     */
    private void renderZones(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        Rect area = layout().zones();
        if (area.isEmpty()) {
            return;
        }
        SeatId seat = mySeat().orElse(focused);
        SeatView view = board.seat(seat);

        for (Zone zone : TableScreenLayout.PILES) {
            Rect pile = layout().pile(zone);
            if (pile.isEmpty()) {
                continue;
            }
            drawPile(graphics, view, zone, pile, pile.contains(mouseX, mouseY));
        }
    }

    /**
     * One pile: the top card if anyone may see it, the sleeve if not, and the count.
     *
     * <p>Showing the graveyard's top card rather than a generic stack is what makes a board
     * readable from across the table - "he has a Bolt on top of his yard" is information the
     * rules already give everyone, and hiding it behind a number just makes people click.
     */
    private void drawPile(GuiGraphics graphics, SeatView view, Zone zone, Rect pile, boolean hovered) {
        ZoneView contents = view.zone(zone);
        int count = contents == null ? 0 : contents.count();

        GatheringSprites.frame(graphics, pile.x(), pile.y(), pile.width(), pile.height());
        Rect art = pile.shrink(2);

        Optional<CardView> top = topOf(contents);
        if (count == 0) {
            GatheringSprites.inset(graphics, art.x(), art.y(), art.width(), art.height());
        } else if (top.isPresent() && !top.get().isFaceDown()) {
            summaryOf(top.get()).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, art.x(), art.y(), art.width(), art.height()),
                    () -> GatheringSprites.inset(graphics, art.x(), art.y(), art.width(), art.height()));
        } else {
            graphics.blit(CardFaceRenderer.CARD_BACK, art.x(), art.y(), 0f, 0f,
                    art.width(), art.height(), art.width(), art.height());
        }

        // The count sits on the pile rather than under it, so a short window that squeezes the
        // piles does not squeeze the one number that has to be there.
        Component label = Component.literal(Integer.toString(count));
        int labelWidth = this.font.width(label) + 4;
        graphics.fill(art.right() - labelWidth, art.bottom() - this.font.lineHeight - 1,
                art.right(), art.bottom(), GHOST_TINT);
        GuiText.draw(graphics, this.font, label, art.right() - labelWidth + 2,
                art.bottom() - this.font.lineHeight, labelWidth, LABEL);

        if (hovered) {
            graphics.renderOutline(pile.x(), pile.y(), pile.width(), pile.height(), ACCENT);
            top.filter(card -> !card.isFaceDown()).ifPresent(this::offerToInspector);
        }
    }

    private static Optional<CardView> topOf(ZoneView zone) {
        if (zone == null || zone.cards().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(zone.cards().get(0));
    }

    private void renderHand(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        Rect area = layout().hand();
        SeatId seat = mySeat().orElse(null);
        if (seat == null) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.spectating"),
                    area.x() + area.width() / 2, area.y() + area.height() / 2 - 4, area.width(), DIM);
            return;
        }

        List<CardView> hand = board.seat(seat).zone(Zone.HAND).cards();
        if (hand.isEmpty()) {
            return;
        }
        for (int index = 0; index < hand.size(); index++) {
            if (isHeld(hand.get(index))) {
                continue;
            }
            Rect slot = handSlot(area, hand.size(), index);
            boolean hovered = slot.contains(mouseX, mouseY);
            drawCard(graphics, hand.get(index), slot, hovered, false);
            if (hovered) {
                offerToInspector(hand.get(index));
            }
        }
    }

    /**
     * Where the nth card of a hand sits.
     *
     * <p>Cards overlap when there are more of them than there is room for, the way a hand of
     * cards does, rather than shrinking until none of them can be told apart.
     */
    private Rect handSlot(Rect area, int handSize, int index) {
        int height = area.height() - 8;
        int width = Math.max(8, Math.round(height * 488f / 680f));
        int room = area.width() - 8;
        int step = handSize <= 1 ? 0 : Math.min(width + 3, (room - width) / (handSize - 1));
        int total = width + step * (handSize - 1);
        int left = area.x() + Math.max(4, (area.width() - total) / 2);
        return new Rect(left + index * step, area.y() + 4, width, height);
    }

    private void renderActions(GuiGraphics graphics, GameView board) {
        Rect area = layout().actions();
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.actions"),
                area.x() + 2, area.y() + 7, area.width() - 4, DIM);
    }

    /**
     * The card in the air, drawn under the cursor exactly where it would land.
     *
     * <p>Same size and same angle as it will have once dropped, so the drag is a preview
     * rather than a promise. A ghost is left behind at the drop point when the cursor has
     * strayed off the table, because a card that would go back where it came from should say
     * so before you let go of it.
     */
    private void renderHeldCard(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        if (held == null) {
            return;
        }
        CardView card = findCard(board, held.card()).orElse(null);
        if (card == null) {
            return;
        }
        TableScreenLayout current = layout();
        if (current.isOnSurface(mouseX, mouseY)) {
            Rect landing = current.cardAt(current.positionForDrop(mouseX, mouseY, held.grabX(), held.grabY()));
            graphics.renderOutline(landing.x(), landing.y(), landing.width(), landing.height(), ACCENT);
        }

        Rect airborne = new Rect(mouseX - held.grabX(), mouseY - held.grabY(),
                current.cardWidth(), current.cardHeight());
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, LIFT);
        drawCard(graphics, card, airborne, false, false);
        graphics.pose().popPose();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;

        // An open menu eats every click, including the one that dismisses it - otherwise
        // clicking away from a menu also does whatever was underneath.
        if (menu != null) {
            ContextMenu open = menu;
            menu = null;
            if (open.mouseClicked(x, y)) {
                return true;
            }
        }

        GameView board = view().orElse(null);
        if (board == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (layout().opponents().contains(x, y)) {
            focusSeatAt(board, y);
            return true;
        }

        Zone pile = layout().pileAt(x, y);
        if (pile != null) {
            return clickPile(pile, x, y, button);
        }

        Optional<CardView> onSurface = cardOnSurfaceAt(board, x, y);
        if (onSurface.isPresent()) {
            return pressCard(board, onSurface.get(), surfaceRectOf(board, onSurface.get()),
                    false, x, y, button);
        }

        Optional<CardView> inHand = cardInHandAt(board, x, y);
        if (inHand.isPresent()) {
            return pressCard(board, inHand.get(), handSlotOf(board, inHand.get()), true, x, y, button);
        }

        if (layout().isOnSurface(x, y)) {
            if (button == 1) {
                openTableMenu(x, y);
                return true;
            }
            // Empty table: drag out a box to pick several cards, or click to let go of them.
            if (button == 0) {
                selected.clear();
                boxFrom = new int[] {x, y};
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean pressCard(
            GameView board, CardView card, Rect where, boolean fromHand, int x, int y, int button) {
        if (!(card instanceof CardView.Visible visible) || mySeat().isEmpty()) {
            return true;
        }
        if (button == 1) {
            openCardMenu(board, visible, fromHand, x, y);
            return true;
        }
        if (button == 0) {
            // Shift picks cards out one at a time rather than picking one up, which is the
            // gesture everything else with a selection uses.
            if (hasShiftDown() && !fromHand) {
                if (!selected.remove(visible.id())) {
                    selected.add(visible.id());
                }
                return true;
            }
            if (!fromHand && !selected.isEmpty() && !selected.contains(visible.id())) {
                // Grabbing something outside the selection means that was the selection you
                // meant, not the one you forgot to clear.
                selected.clear();
            }
            held = new Held(visible.id(), fromHand, x - where.x(), y - where.y(), x, y);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (boxFrom != null) {
            int[] from = boxFrom;
            boxFrom = null;
            view().ifPresent(board ->
                    selectWithin(board, boxBetween(from[0], from[1], (int) mouseX, (int) mouseY)));
            return true;
        }
        if (held == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        Held dropped = held;
        held = null;

        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return true;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;
        TableScreenLayout current = layout();

        if (current.isOnSurface(x, y)) {
            // A press that never moved is a click, and a click on a card already on the table
            // taps it - the single most common thing anyone does, and the one gesture worth
            // spending the plain left click on.
            if (!dropped.fromHand() && !dropped.hasMoved(x, y)) {
                findCard(view().orElse(null), dropped.card())
                        .ifPresent(card -> send(new GameEvent.CardTapSet(me, dropped.card(), !card.tapped())));
                return true;
            }
            TablePosition landing = current.positionForDrop(x, y, dropped.grabX(), dropped.grabY());
            if (!dropped.fromHand() && selected.contains(dropped.card())) {
                dropGroup(dropped.card(), landing, me);
            } else {
                send(new GameEvent.CardMoved(me, dropped.card(), ZoneRef.of(focused, Zone.BATTLEFIELD),
                        Placement.at(landing)));
            }
            return true;
        }
        if (!dropped.fromHand() && current.hand().contains(x, y)) {
            // Dragged off the table and back into your hand.
            send(new GameEvent.CardMoved(me, dropped.card(), ZoneRef.of(me, Zone.HAND), Placement.BOTTOM));
        }
        return true;
    }

    /**
     * Puts a whole selection down, keeping the arrangement it was in.
     *
     * <p>One delta for all of them, trimmed until every card fits on the table - so a group
     * shoved into a corner slides along the edge instead of collapsing into a single pile,
     * which is what clamping each card on its own would do to a board somebody spent the game
     * building.
     */
    private void dropGroup(CardInstanceId grabbed, TablePosition landing, SeatId me) {
        GameView board = view().orElse(null);
        if (board == null) {
            return;
        }
        List<CardView> moving = selectedOn(board);
        TablePosition before = findCard(board, grabbed).flatMap(CardView::placedAt).orElse(null);
        if (before == null || moving.isEmpty()) {
            send(new GameEvent.CardMoved(me, grabbed, ZoneRef.of(focused, Zone.BATTLEFIELD),
                    Placement.at(landing)));
            return;
        }

        List<TablePosition> spots = new ArrayList<>(moving.size());
        for (CardView card : moving) {
            spots.add(card.placedAt().orElse(null));
        }
        List<TablePosition> after = TableDrag.movedTogether(
                spots, landing.x() - before.x(), landing.y() - before.y());

        for (int index = 0; index < moving.size(); index++) {
            if (!(moving.get(index) instanceof CardView.Visible visible) || after.get(index) == null) {
                continue;
            }
            send(new GameEvent.CardMoved(me, visible.id(), ZoneRef.of(focused, Zone.BATTLEFIELD),
                    Placement.at(after.get(index))));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        GameView board = view().orElse(null);
        SeatId me = mySeat().orElse(null);
        if (board == null || me == null || scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        // Scrolling over a card turns it. It is the gesture a mouse already has for "a bit
        // more of this", and it is what makes angling a card cheap enough to bother doing.
        Optional<CardView> under = cardOnSurfaceAt(board, (int) mouseX, (int) mouseY);
        if (under.isPresent() && under.get() instanceof CardView.Visible visible) {
            int step = scrollY > 0 ? NUDGE_DEGREES : -NUDGE_DEGREES;
            send(new GameEvent.CardRotated(me, visible.id(), restingAngle(visible) + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ----------------------------------------------------------------- menus

    /**
     * Everything you can do to one card, in one place.
     *
     * <p>Every entry here is a verb somebody would otherwise have to remember a key for, and
     * the list is deliberately flat: a table where "put a counter on it" is two levels deep is
     * a table where nobody uses counters.
     */
    private void openCardMenu(GameView board, CardView.Visible card, boolean fromHand, int x, int y) {
        SeatId me = mySeat().orElseThrow();
        CardInstanceId id = card.id();
        // Every verb below applies to the whole selection when this card is part of it, and to
        // this card alone otherwise. What goes on the wire is the same events one card at a
        // time would have sent, so a selection can do nothing an ordinary sequence of moves
        // could not, and the log reads the same either way.
        List<CardInstanceId> targets = fromHand ? List.of(id) : targetsFor(id);
        List<ContextMenu.Entry> entries = ContextMenu.entries();
        if (targets.size() > 1) {
            entries.add(ContextMenu.Entry.disabled(Component.translatable(
                    "menu.gathering.table.selected", targets.size())));
        }

        if (fromHand) {
            entries.add(entry("play", () -> send(new GameEvent.CardMoved(
                    me, id, ZoneRef.of(me, Zone.BATTLEFIELD), Placement.BOTTOM))));
            entries.add(entry("play_face_down", () -> {
                send(new GameEvent.CardMoved(me, id, ZoneRef.of(me, Zone.BATTLEFIELD), Placement.BOTTOM));
                send(new GameEvent.CardFacingSet(me, id, Facing.FACE_DOWN));
            }));
        } else {
            boolean tapping = !card.tapped();
            entries.add(entry(card.tapped() ? "untap" : "tap",
                    () -> eachTarget(board, targets, target ->
                            new GameEvent.CardTapSet(me, target, tapping))));
            entries.add(entry("turn_right", () -> eachCard(board, targets, seen ->
                    new GameEvent.CardRotated(me, seen.id(), restingAngle(seen) + NUDGE_DEGREES))));
            entries.add(entry("turn_left", () -> eachCard(board, targets, seen ->
                    new GameEvent.CardRotated(me, seen.id(), restingAngle(seen) - NUDGE_DEGREES))));
            if (restingAngle(card) != 0) {
                entries.add(entry("straighten", () -> eachTarget(board, targets, target ->
                        new GameEvent.CardRotated(me, target, 0))));
            }
            Facing turnTo = card.facing() == Facing.FACE_UP ? Facing.FACE_DOWN : Facing.FACE_UP;
            entries.add(entry(card.facing() == Facing.FACE_UP ? "turn_face_down" : "turn_face_up",
                    () -> eachTarget(board, targets, target ->
                            new GameEvent.CardFacingSet(me, target, turnTo))));
            // The single most common one keeps its own line, because a menu that makes you
            // open a screen to put a +1/+1 on something is a menu nobody uses for counters.
            entries.add(entry("add_counter", () -> eachTarget(board, targets, target ->
                    new GameEvent.CounterChanged(
                            me, target, CardInstance.Counters.PLUS_ONE_PLUS_ONE, 1))));
            if (card.counter(CardInstance.Counters.PLUS_ONE_PLUS_ONE) != 0) {
                entries.add(entry("remove_counter", () -> eachTarget(board, targets, target ->
                        new GameEvent.CounterChanged(
                                me, target, CardInstance.Counters.PLUS_ONE_PLUS_ONE, -1))));
            }
            entries.add(entry("counters", () -> openCounters(new CountersScreen.Subject.Cards(
                    targets, CountersScreen.titleFor(targets, nameOf(card))))));
            entries.add(entry("copy", () -> eachTarget(board, targets, target ->
                    new GameEvent.TokenCopyCreated(me, target, focused))));
            if (card.token()) {
                entries.add(entry("remove_token", () -> eachCard(board, targets, seen ->
                        seen.token() ? new GameEvent.TokenRemoved(me, seen.id()) : null)));
            }
            entries.add(entry("to_hand", () -> eachCard(board, targets, seen ->
                    new GameEvent.CardMoved(
                            me, seen.id(), ZoneRef.of(seen.owner(), Zone.HAND), Placement.BOTTOM))));
        }
        entries.add(entry("to_graveyard", () -> eachCard(board, targets, seen ->
                new GameEvent.CardMoved(
                        me, seen.id(), ZoneRef.of(seen.owner(), Zone.GRAVEYARD), Placement.TOP))));
        entries.add(entry("to_exile", () -> eachCard(board, targets, seen ->
                new GameEvent.CardMoved(
                        me, seen.id(), ZoneRef.of(seen.owner(), Zone.EXILE), Placement.TOP))));
        entries.add(entry("to_library_top", () -> eachCard(board, targets, seen ->
                new GameEvent.CardMoved(
                        me, seen.id(), ZoneRef.of(seen.owner(), Zone.LIBRARY), Placement.TOP))));
        entries.add(entry("to_library_bottom", () -> eachCard(board, targets, seen ->
                new GameEvent.CardMoved(
                        me, seen.id(), ZoneRef.of(seen.owner(), Zone.LIBRARY), Placement.BOTTOM))));
        entries.add(entry("ping", () -> send(new GameEvent.CardPinged(me, id))));

        menu = ContextMenu.at(this.font, x, y, this.width, this.height, entries);
    }

    /**
     * Sends one event per target, built from the id alone.
     *
     * <p>For verbs where every card gets the same instruction - tap them all, turn them all
     * face down - which is what makes a selection useful rather than just faster clicking.
     */
    private void eachTarget(
            GameView board, List<CardInstanceId> targets,
            java.util.function.Function<CardInstanceId, GameEvent> verb) {
        for (CardInstanceId target : targets) {
            GameEvent event = verb.apply(target);
            if (event != null) {
                send(event);
            }
        }
        selected.clear();
    }

    /**
     * Sends one event per target, built from what that card currently is.
     *
     * <p>For verbs whose answer differs per card - which angle it is at, whose graveyard it
     * goes to. Cards that have already left the board are skipped rather than guessed at.
     */
    private void eachCard(
            GameView board, List<CardInstanceId> targets,
            java.util.function.Function<CardView.Visible, GameEvent> verb) {
        for (CardInstanceId target : targets) {
            findCard(board, target)
                    .filter(CardView.Visible.class::isInstance)
                    .map(CardView.Visible.class::cast)
                    .map(verb)
                    .ifPresent(this::send);
        }
        selected.clear();
    }

    /**
     * Clicking one of your own piles.
     *
     * <p>Left-click does the obvious thing and right-click offers the rest, which is the same
     * bargain as everywhere else on this screen. The obvious thing for a library is to draw a
     * card, because that is what a library is for; for every other pile it is to open it,
     * because a pile you cannot look through is a number.
     */
    private boolean clickPile(Zone pile, int x, int y, int button) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return true;
        }
        GatheringButtons.clickSound();
        if (button == 1) {
            openPileMenu(me, pile, x, y);
            return true;
        }
        if (pile == Zone.LIBRARY) {
            send(new GameEvent.CardsDrawn(me, me, 1));
        } else {
            openPile(me, pile, false);
        }
        return true;
    }

    private void openPileMenu(SeatId me, Zone pile, int x, int y) {
        List<ContextMenu.Entry> entries = ContextMenu.entries();
        if (pile == Zone.LIBRARY) {
            boolean showing = revealedFromMyLibrary() > 0;
            entries.add(entry("draw", () -> send(new GameEvent.CardsDrawn(me, me, 1))));
            entries.add(entry("draw_many", () -> ask("draw_many", 1,
                    count -> send(new GameEvent.CardsDrawn(me, me, count)))));
            entries.add(entry("mulligan", () -> send(new GameEvent.Mulliganed(me, me, MULLIGAN_HAND))));
            entries.add(entry("scry", () -> ask("scry", 1, count -> {
                send(new GameEvent.LibraryLooked(me, me, count));
                openPile(me, Zone.LIBRARY, true);
            })));
            entries.add(entry("mill", () -> ask("mill", 1,
                    count -> send(new GameEvent.LibraryMilled(me, me, count)))));
            entries.add(showing
                    ? entry("stop_revealing", () -> send(new GameEvent.LibraryRevealed(me, me, 0)))
                    : entry("reveal", () -> ask("reveal", 1,
                            count -> send(new GameEvent.LibraryRevealed(me, me, count)))));
            entries.add(entry("search", () -> {
                send(new GameEvent.LibrarySearched(me, me));
                openPile(me, Zone.LIBRARY, true);
            }));
            entries.add(entry("shuffle", () -> send(new GameEvent.LibraryShuffled(me, me))));
        } else {
            entries.add(entry("open_pile", () -> openPile(me, pile, false)));
        }
        menu = ContextMenu.at(this.font, x, y, this.width, this.height, entries);
    }

    /**
     * Spreads a pile out on its own screen.
     *
     * <p>{@code opensALibrary} is what makes the screen responsible for closing it again. A
     * library is open because an event said so, so it stays open until an event says
     * otherwise - not until a screen happens to go away.
     */
    /** Asks how many, then does it. The screen closes itself before the answer arrives. */
    private void ask(String key, int suggested, java.util.function.IntConsumer action) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new AmountScreen(
                Component.translatable("screen.gathering.amount." + key), suggested, action));
    }

    /** How much of my own library is currently face up to the table. */
    private int revealedFromMyLibrary() {
        SeatId me = mySeat().orElse(null);
        GameView board = view().orElse(null);
        if (me == null || board == null) {
            return 0;
        }
        // What the server sent is the truth about what is showing: a revealed card arrives as
        // a card in the library's card list, and a hidden one does not arrive at all.
        return board.seat(me).zone(Zone.LIBRARY).cards().size();
    }

    private void openPile(SeatId me, Zone pile, boolean opensALibrary) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new PileScreen(table, me, pile, opensALibrary));
    }

    /** The menu for the table itself, for the verbs that are about a seat rather than a card. */
    private void openTableMenu(int x, int y) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return;
        }
        List<ContextMenu.Entry> entries = ContextMenu.entries();
        entries.add(entry("draw", () -> send(new GameEvent.CardsDrawn(me, me, 1))));
        entries.add(entry("untap_all", () -> send(new GameEvent.SeatUntappedAll(me, me))));
        entries.add(entry("shuffle", () -> send(new GameEvent.LibraryShuffled(me, me))));
        entries.add(entry("gain_life", () -> send(new GameEvent.LifeChanged(me, me, 1))));
        entries.add(entry("lose_life", () -> send(new GameEvent.LifeChanged(me, me, -1))));
        view().ifPresent(board -> entries.add(entry("my_counters",
                () -> openCounters(new CountersScreen.Subject.Seat(
                        me, CountersScreen.titleForSeat(board, me))))));
        menu = ContextMenu.at(this.font, x, y, this.width, this.height, entries);
    }

    private void openCounters(CountersScreen.Subject subject) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new CountersScreen(table, subject));
    }

    /** What to call a card on a screen that has to name what it is about to change. */
    private Component nameOf(CardView card) {
        return summaryOf(card)
                .map(summary -> Component.literal(summary.name()))
                .map(Component.class::cast)
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
    }

    private static ContextMenu.Entry entry(String key, Runnable action) {
        return ContextMenu.Entry.of(Component.translatable("menu.gathering.table." + key), action);
    }

    // ------------------------------------------------------------ hit-testing

    private void focusSeatAt(GameView board, int y) {
        Rect area = layout().opponents();
        int index = (y - area.y() - 3) / (this.font.lineHeight + 3);
        if (index >= 0 && index < board.seats().size()) {
            focused = board.seats().get(index).seat();
        }
    }

    /**
     * The card under the cursor, front-most first.
     *
     * <p>Front to back, because the card you can see is the card you meant. Turned cards are
     * tested at the angle they are drawn at, so the empty corner of an angled card is table
     * and a click there reaches whatever is underneath it.
     */
    private Optional<CardView> cardOnSurfaceAt(GameView board, int x, int y) {
        if (!layout().isOnSurface(x, y)) {
            return Optional.empty();
        }
        List<CardView> cards = board.seat(focused).zone(Zone.BATTLEFIELD).cards();
        List<Integer> depths = TableStacking.depths(spotsIn(cards));
        for (int index = cards.size() - 1; index >= 0; index--) {
            CardView card = cards.get(index);
            if (!isHeld(card)
                    && spotOf(card, depths.get(index)).containsTurned(angleOf(card), x, y)) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    private Optional<CardView> cardInHandAt(GameView board, int x, int y) {
        SeatId seat = mySeat().orElse(null);
        if (seat == null) {
            return Optional.empty();
        }
        Rect area = layout().hand();
        List<CardView> hand = board.seat(seat).zone(Zone.HAND).cards();
        for (int index = hand.size() - 1; index >= 0; index--) {
            if (!isHeld(hand.get(index)) && handSlot(area, hand.size(), index).contains(x, y)) {
                return Optional.of(hand.get(index));
            }
        }
        return Optional.empty();
    }

    /** Where a card on the focused board is currently drawn, lean included. */
    private Rect surfaceRectOf(GameView board, CardView card) {
        List<CardView> cards = board.seat(focused).zone(Zone.BATTLEFIELD).cards();
        int index = cards.indexOf(card);
        if (index < 0) {
            return layout().cardAt(card.placedAt().orElse(TablePosition.ORIGIN));
        }
        return spotOf(card, TableStacking.depths(spotsIn(cards)).get(index));
    }

    private Rect handSlotOf(GameView board, CardView card) {
        SeatId seat = mySeat().orElse(null);
        if (seat == null) {
            return Rect.NONE;
        }
        List<CardView> hand = board.seat(seat).zone(Zone.HAND).cards();
        int index = hand.indexOf(card);
        return index < 0 ? Rect.NONE : handSlot(layout().hand(), hand.size(), index);
    }

    private boolean isSelected(CardView card) {
        return card instanceof CardView.Visible visible && selected.contains(visible.id());
    }

    /**
     * The cards a verb should apply to.
     *
     * <p>The selection when the card acted on is part of it, and just that card otherwise.
     * Right-clicking a card outside the selection is somebody addressing that card, not
     * forgetting what they had picked - so it acts on the one under the cursor and leaves the
     * selection alone.
     */
    private List<CardInstanceId> targetsFor(CardInstanceId card) {
        if (!selected.contains(card)) {
            return List.of(card);
        }
        return List.copyOf(selected);
    }

    /** Every selected card on the focused board, in the board's own stacking order. */
    private List<CardView> selectedOn(GameView board) {
        List<CardView> found = new ArrayList<>();
        for (CardView card : board.seat(focused).zone(Zone.BATTLEFIELD).cards()) {
            if (isSelected(card)) {
                found.add(card);
            }
        }
        return found;
    }

    /** Picks out every card the box touches, in place of whatever was picked before. */
    private void selectWithin(GameView board, Rect box) {
        selected.clear();
        List<CardView> cards = board.seat(focused).zone(Zone.BATTLEFIELD).cards();
        List<Integer> depths = TableStacking.depths(spotsIn(cards));
        for (int index = 0; index < cards.size(); index++) {
            if (cards.get(index) instanceof CardView.Visible visible
                    && spotOf(cards.get(index), depths.get(index)).overlaps(box)) {
                selected.add(visible.id());
            }
        }
    }

    private boolean isHeld(CardView card) {
        return held != null && card instanceof CardView.Visible visible && visible.id().equals(held.card());
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (menu != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            menu = null;
            return true;
        }
        SeatId me = mySeat().orElse(null);
        if (me != null) {
            switch (key) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_D -> {
                    send(new GameEvent.CardsDrawn(me, me, 1));
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_S -> {
                    send(new GameEvent.LibraryShuffled(me, me));
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_U -> {
                    send(new GameEvent.SeatUntappedAll(me, me));
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL -> {
                    send(new GameEvent.LifeChanged(me, me, 1));
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS -> {
                    send(new GameEvent.LifeChanged(me, me, -1));
                    return true;
                }
                default -> { }
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void send(GameEvent event) {
        ClientTableActions.send(table, event);
    }

    // --------------------------------------------------------------- drawing

    /** A card on the table: at its own spot, turned to its own angle, counters and all. */
    private void drawTableCard(GuiGraphics graphics, CardView card, Rect where, boolean hovered) {
        int angle = angleOf(card);

        if (angle == 0) {
            drawCard(graphics, card, where, hovered, card.tapped());
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate((float) where.centreX(), (float) where.centreY(), 0f);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.pose().translate((float) -where.centreX(), (float) -where.centreY(), 0f);
        drawCard(graphics, card, where, hovered, card.tapped());
        graphics.pose().popPose();
    }

    private void drawCard(GuiGraphics graphics, CardView card, Rect where, boolean hovered, boolean dimmed) {
        if (where.isEmpty()) {
            return;
        }
        // Cast first, under everything, so the card above reads as being above.
        graphics.fill(where.x() + SHADOW_OFFSET, where.y() + SHADOW_OFFSET,
                where.right() + SHADOW_OFFSET, where.bottom() + SHADOW_OFFSET, SHADOW);
        GatheringSprites.frame(graphics, where.x(), where.y(), where.width(), where.height());
        Rect art = where.shrink(2);

        if (card.isFaceDown()) {
            // Even to the player who knows what it is. Their board has to look to them the
            // way it looks to everyone else, or they cannot tell what they have given away.
            graphics.blit(CardFaceRenderer.CARD_BACK, art.x(), art.y(), 0f, 0f,
                    art.width(), art.height(), art.width(), art.height());
        } else {
            summaryOf(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, art.x(), art.y(), art.width(), art.height()),
                    () -> GatheringSprites.inset(graphics, art.x(), art.y(), art.width(), art.height()));
        }

        if (dimmed) {
            // A tapped card is already lying sideways; the tint is what tells it apart from
            // one somebody turned by hand, without a word of text over the art.
            graphics.fill(art.x(), art.y(), art.right(), art.bottom(), TAPPED_TINT);
        }
        drawCounters(graphics, card, art);
        if (hovered) {
            graphics.renderOutline(where.x(), where.y(), where.width(), where.height(), ACCENT);
        }
    }

    /**
     * The counters on a card, along its bottom edge.
     *
     * <p>On the card rather than beside it, because a counter that lives next to a card stops
     * being on that card the moment somebody moves either of them.
     */
    private void drawCounters(GuiGraphics graphics, CardView card, Rect art) {
        List<Component> labels = new ArrayList<>();
        card.counters().forEach((name, amount) -> labels.add(Component.literal(shortCounter(name) + amount)));
        if (labels.isEmpty()) {
            return;
        }
        int line = art.bottom() - 2 - this.font.lineHeight * labels.size();
        for (Component label : labels) {
            graphics.fill(art.x(), line - 1, art.right(), line + this.font.lineHeight - 1, GHOST_TINT);
            GuiText.draw(graphics, this.font, label, art.x() + 2, line, art.width() - 4, COUNTER_TEXT);
            line += this.font.lineHeight;
        }
    }

    /** "+1/+1" is wider than most cards are; on the card it is a sign and a number. */
    private static String shortCounter(String name) {
        return switch (name) {
            case CardInstance.Counters.PLUS_ONE_PLUS_ONE -> "+";
            case CardInstance.Counters.MINUS_ONE_MINUS_ONE -> "-";
            case CardInstance.Counters.LOYALTY -> "L";
            default -> name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        };
    }

    /** The angle a card was left at, ignoring whatever tapping is doing on top of it. */
    private static int restingAngle(CardView card) {
        return card.placedAt().map(TablePosition::rotation).orElse(0);
    }

    /** What this client knows about the card, which for an anonymous one is nothing at all. */
    private Optional<CardSummary> summaryOf(CardView card) {
        if (!(card instanceof CardView.Visible visible)) {
            return Optional.empty();
        }
        return ClientCardCache.get().summary(CardComponent.of(visible.identity()));
    }

    /** Lets the read key show this card, exactly as it does over an inventory slot. */
    private void offerToInspector(CardView card) {
        if (card instanceof CardView.Visible visible) {
            ClientHoverState.setHovered(CardItem.of(CardComponent.of(visible.identity())));
        }
    }

    private static Optional<CardView> findCard(GameView board, CardInstanceId id) {
        if (board == null) {
            return Optional.empty();
        }
        return board.allCardViews().stream()
                .filter(CardView.Visible.class::isInstance)
                .map(CardView.Visible.class::cast)
                .filter(visible -> visible.id().equals(id))
                .map(CardView.class::cast)
                .findFirst();
    }

    /** A seat's counters as one short run of text, for the line across the top. */
    private static String describeCounters(SeatView seat) {
        StringBuilder text = new StringBuilder();
        seat.counters().forEach((name, count) -> {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(count).append(' ').append(name);
        });
        return text.toString();
    }

    private static int count(SeatView seat, Zone zone) {
        ZoneView view = seat.zone(zone);
        return view == null ? 0 : view.count();
    }

    private void panel(GuiGraphics graphics, Rect area) {
        if (!area.isEmpty()) {
            GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());
        }
    }

    private TableScreenLayout layout() {
        if (layout == null) {
            layout = TableScreenLayout.of(this.width, this.height, otherSeats().size());
        }
        return layout;
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
}
