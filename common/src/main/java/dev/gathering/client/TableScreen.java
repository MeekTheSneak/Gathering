package dev.gathering.client;

import dev.gathering.core.card.CardIdentity;
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
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
    private static final int TAPPED_TINT = 0x80000000;

    private final BlockPos table;

    private TableScreenLayout layout;
    private SeatId focused;

    /** The card being dragged, and where from. Null when nothing is in hand. */
    private CardInstanceId dragging;
    private boolean draggingFromHand;

    public TableScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.table"));
        this.table = table;
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
        renderOpponents(graphics, board, mouseX, mouseY);
        renderSurface(graphics, board, mouseX, mouseY);
        renderZones(graphics, board);
        renderHand(graphics, board, mouseX, mouseY);
        renderActions(graphics, board);

        if (dragging != null) {
            findCard(board, dragging).ifPresent(card ->
                    drawCard(graphics, card, new Rect(mouseX - layout().squareWidth() / 2,
                            mouseY - layout().squareHeight() / 2,
                            layout().squareWidth(), layout().squareHeight()), false));
        }
    }

    private void renderOpponents(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
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

            if (isFocused) {
                GatheringSprites.highlight(graphics, area.x() + 1, line - 2, area.width() - 2,
                        this.font.lineHeight + 3);
            }
            GuiText.draw(graphics, this.font, text, area.x() + 4, line, area.width() - 8,
                    mine ? ACCENT : LABEL);
            line += this.font.lineHeight + 3;
        }
    }

    private void renderSurface(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        TableScreenLayout current = layout();
        List<CardView> cards = board.seat(focused).zone(Zone.BATTLEFIELD).cards();

        for (CardView card : cards) {
            Rect square = squareFor(current, card, cards.indexOf(card));
            if (square == null) {
                continue;
            }
            boolean hovered = square.contains(mouseX, mouseY);
            drawCard(graphics, card, square, hovered);
            if (hovered) {
                offerToInspector(card);
            }
        }

        // The square under the cursor, so a drop lands somewhere you aimed at.
        if (dragging != null) {
            int[] square = current.squareOf(mouseX, mouseY);
            if (square != null) {
                Rect target = current.squareAt(square[0], square[1]);
                graphics.renderOutline(target.x(), target.y(), target.width(), target.height(), ACCENT);
            }
        }
    }

    /**
     * Where a card on the surface is drawn.
     *
     * <p>Its own square if it has one and the square is on screen, otherwise the next free
     * spot in reading order. A card whose position is off the visible grid must still be
     * reachable: it is somebody's permanent, and "you cannot see it because the window is
     * small" is not something a game may do.
     */
    private Rect squareFor(TableScreenLayout current, CardView card, int fallbackIndex) {
        Optional<TablePosition> position = card.square();
        if (position.isPresent()
                && position.get().column() < current.columns()
                && position.get().row() < current.rows()) {
            return current.squareAt(position.get().column(), position.get().row());
        }
        int index = Math.max(0, fallbackIndex) % Math.max(1, current.visibleSquares());
        return current.squareAt(index % current.columns(), index / current.columns());
    }

    private void renderZones(GuiGraphics graphics, GameView board) {
        Rect area = layout().zones();
        if (area.isEmpty()) {
            return;
        }
        SeatId seat = mySeat().orElse(focused);
        SeatView view = board.seat(seat);
        int line = area.y() + 4;

        for (Zone zone : List.of(Zone.LIBRARY, Zone.HAND, Zone.GRAVEYARD, Zone.EXILE, Zone.COMMAND)) {
            if (line + this.font.lineHeight > area.bottom()) {
                break;
            }
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.table.zone",
                            Component.translatable("zone.gathering." + zone.name().toLowerCase(
                                    java.util.Locale.ROOT)),
                            count(view, zone)),
                    area.x() + 4, line, area.width() - 8, LABEL);
            line += this.font.lineHeight + 4;
        }
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
            Rect slot = handSlot(area, hand.size(), index);
            if (hand.get(index) instanceof CardView.Visible visible
                    && visible.id().equals(dragging)) {
                continue;
            }
            boolean hovered = slot.contains(mouseX, mouseY);
            drawCard(graphics, hand.get(index), slot, hovered);
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

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GameView board = view().orElse(null);
        if (board == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = (int) mouseX;
        int y = (int) mouseY;

        if (layout().opponents().contains(x, y)) {
            focusSeatAt(board, y);
            return true;
        }

        Optional<CardView> onSurface = cardOnSurfaceAt(board, x, y);
        if (onSurface.isPresent()) {
            return clickSurfaceCard(board, onSurface.get(), button);
        }

        Optional<CardView> inHand = cardInHandAt(board, x, y);
        if (inHand.isPresent() && button == 0) {
            if (inHand.get() instanceof CardView.Visible visible) {
                dragging = visible.id();
                draggingFromHand = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickSurfaceCard(GameView board, CardView card, int button) {
        if (!(card instanceof CardView.Visible visible)) {
            return true;
        }
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return true;
        }
        if (button == 0) {
            dragging = visible.id();
            draggingFromHand = false;
            return true;
        }
        if (button == 1) {
            // Right-click turns it over, which is the one verb worth a click of its own.
            send(new GameEvent.CardFacingSet(me, visible.id(), Facing.FACE_DOWN));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        CardInstanceId card = dragging;
        boolean fromHand = draggingFromHand;
        dragging = null;

        SeatId me = mySeat().orElse(null);
        int[] square = layout().squareOf((int) mouseX, (int) mouseY);
        if (me != null && square != null) {
            send(new GameEvent.CardMoved(me, card, ZoneRef.of(focused, Zone.BATTLEFIELD),
                    new Placement.At(TablePosition.of(square[0], square[1]))));
        } else if (me != null && !fromHand && layout().hand().contains((int) mouseX, (int) mouseY)) {
            // Dragged off the table and back into your hand.
            send(new GameEvent.CardMoved(me, card, ZoneRef.of(me, Zone.HAND), Placement.BOTTOM));
        }
        return true;
    }

    private void focusSeatAt(GameView board, int y) {
        Rect area = layout().opponents();
        int index = (y - area.y() - 3) / (this.font.lineHeight + 3);
        if (index >= 0 && index < board.seats().size()) {
            focused = board.seats().get(index).seat();
        }
    }

    private Optional<CardView> cardOnSurfaceAt(GameView board, int x, int y) {
        TableScreenLayout current = layout();
        List<CardView> cards = board.seat(focused).zone(Zone.BATTLEFIELD).cards();
        for (int index = cards.size() - 1; index >= 0; index--) {
            Rect square = squareFor(current, cards.get(index), index);
            if (square != null && square.contains(x, y)) {
                return Optional.of(cards.get(index));
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
            if (handSlot(area, hand.size(), index).contains(x, y)) {
                return Optional.of(hand.get(index));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
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

    private void drawCard(GuiGraphics graphics, CardView card, Rect where, boolean hovered) {
        GatheringSprites.frame(graphics, where.x(), where.y(), where.width(), where.height());
        Rect art = where.shrink(2);

        summaryOf(card).ifPresentOrElse(
                summary -> CardInspectPanel.renderArt(
                        graphics, summary, art.x(), art.y(), art.width(), art.height()),
                () -> GatheringSprites.inset(graphics, art.x(), art.y(), art.width(), art.height()));

        if (isTapped(card)) {
            graphics.fill(art.x(), art.y(), art.right(), art.bottom(), TAPPED_TINT);
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.table.tapped"),
                    art.x() + 2, art.bottom() - this.font.lineHeight - 2, art.width() - 4, ACCENT);
        }
        if (hovered) {
            graphics.renderOutline(where.x(), where.y(), where.width(), where.height(), ACCENT);
        }
    }

    private static boolean isTapped(CardView card) {
        return switch (card) {
            case CardView.Visible visible -> visible.tapped();
            case CardView.Anonymous anonymous -> anonymous.tapped();
        };
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
        return board.allCardViews().stream()
                .filter(CardView.Visible.class::isInstance)
                .map(CardView.Visible.class::cast)
                .filter(visible -> visible.id().equals(id))
                .map(CardView.class::cast)
                .findFirst();
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
