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
import dev.gathering.core.game.event.LogEntry;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCluster;
import dev.gathering.core.ui.BoardGeometry;
import dev.gathering.core.ui.BoardPlacement;
import dev.gathering.core.ui.HandFan;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.SeatColour;
import dev.gathering.core.ui.TableAttachments;
import dev.gathering.core.ui.TableDrag;
import dev.gathering.core.ui.TableScreenLayout;
import dev.gathering.core.ui.SurfaceBoard;
import dev.gathering.core.ui.TableStacking;
import dev.gathering.core.ui.TableSurface;
import dev.gathering.core.ui.TableTop;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import dev.gathering.network.CreateTokenPayload;
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
 * The table, seen from above.
 *
 * <p>The whole screen is the felt. Every seat has a mat on it, everybody's board is visible at
 * once, and a camera says which part you are looking at - scroll to zoom, middle-drag to pan,
 * the way every table simulator works. This replaced four bands with a play surface panelled
 * into the middle of them, which was a menu with a game in it: you could only look at one
 * player's board at a time, which is the one thing a table is for.
 *
 * <p><b>There is no grid and no focused seat.</b> A card goes exactly where you dropped it, at
 * the angle you left it, on whosever mat you dropped it on - so stealing a creature is
 * literally dragging it to your side of the table, and the move event falls out of where it
 * landed rather than out of a mode somebody had to set first.
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

    /** The felt, and a mat on it. Mats are lighter so the table reads as somebody's space. */
    private static final int FELT = 0xFF1E3A2E;
    private static final int MAT = 0x30FFFFFF;
    private static final int MAT_MINE = 0x406FD3E8;

    /**
     * The shadow every card on the table casts.
     *
     * <p>Nothing here has thickness, so without this a card lying across another one is two
     * flat pictures sharing an edge and you cannot tell which is on top.
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

    /**
     * The key list, in the order it reads best rather than the order the switch handles them.
     *
     * <p>Translation keys only - the strings themselves live in the language file, because a
     * control list is exactly the thing somebody translating the mod has to be able to reword.
     */
    private static final List<String[]> KEY_HELP = List.of(
            new String[] {
                "screen.gathering.table.keys_camera",
                "screen.gathering.table.key_zoom",
                "screen.gathering.table.key_pan",
                "screen.gathering.table.key_frame",
                "screen.gathering.table.key_view",
            },
            new String[] {
                "screen.gathering.table.keys_cards",
                "screen.gathering.table.key_pick",
                "screen.gathering.table.key_tap",
                "screen.gathering.table.key_menu",
                "screen.gathering.table.key_flip",
                "screen.gathering.table.key_turn",
                "screen.gathering.table.key_select",
                "screen.gathering.table.key_group",
                "screen.gathering.table.key_delete",
                "screen.gathering.table.key_read",
            },
            new String[] {
                "screen.gathering.table.keys_game",
                "screen.gathering.table.key_draw",
                "screen.gathering.table.key_shuffle",
                "screen.gathering.table.key_untap",
                "screen.gathering.table.key_life",
                "screen.gathering.table.key_pass",
                "screen.gathering.table.key_log",
            });

    /** How much one wheel notch zooms. A shallow step, because zoom is used constantly. */
    private static final double ZOOM_STEP = 1.18;

    /** How far one press of a pan key slides the table. */
    private static final int PAN_STEP = 60;

    /**
     * Roughly how many pixels of screen a block of table covers, for turning a drag into a
     * slide when the table is in the world.
     *
     * <p>Approximate on purpose. Exact would mean the height, the field of view and the
     * window, recomputed every frame, to decide how fast a drag scrolls - and nobody has ever
     * noticed that a pan was five per cent fast. What people notice is the direction.
     */
    private static final double PIXELS_PER_BLOCK = 220.0;

    private final BlockPos table;

    private TableScreenLayout layout;

    /**
     * The two ways of looking at this board, and which one is being used.
     *
     * <p>Both exist at once on purpose. The seated screen is a felt drawn on the window and it
     * works; playing on the block is the same game seen through the game's own camera, and
     * whether it is nicer to play is not a thing that can be decided by reading it. So there
     * is a key that swaps them, and the answer comes from playing both.
     *
     * <p>They cost almost nothing to keep side by side, because the only thing they disagree
     * about is what a point means - see {@link BoardPlacement}. Everything from working out
     * which card is under the cursor onwards is the same code either way.
     */
    private BoardGeometry geometry;

    private SurfaceBoard onBlock;

    /** Whether the board being played is the one on the table in the world. */
    private boolean playingOnTheBlock;

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

    /** Where a middle-drag pan started, so the table follows the hand. */
    private int[] panFrom;

    /**
     * The cards waiting to be put onto something, once their host has been picked.
     *
     * <p>A mode rather than a gesture, because dragging already means "put this here" and a
     * drop that sometimes attached and sometimes stacked would be a coin flip.
     */
    private List<CardInstanceId> attaching = List.of();

    private ContextMenu menu;

    /** Whether the log panel is open. Off by default: the table is the thing you came for. */
    private boolean showingLog;

    /** Whether the key list is open. */
    private boolean showingKeys;

    /**
     * Where the cursor was last frame.
     *
     * <p>Key handlers are not given a mouse position, and every TTS object key acts on
     * whatever is under the cursor - so the screen has to remember where that was.
     */
    private int cursorX;
    private int cursorY;

    public TableScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.table"));
        this.table = table;
    }

    /**
     * A card that has been picked up.
     *
     * <p>The grab offset is the whole reason this is a record rather than an id: a card that
     * snaps its corner to the cursor jumps out from under your finger the moment you touch it,
     * and putting something down where you are pointing is the one thing a table has to get
     * right.
     */
    private record Held(
            CardInstanceId card, SeatId from, boolean fromHand,
            int grabX, int grabY, int pressX, int pressY) {
        // grabX/grabY are in the space the *board* is measured in - pixels on the seated
        // screen, surface units on the block. pressX/pressY stay in pixels, because how far
        // the hand has moved before a press becomes a drag is a question about the mouse.

        boolean hasMoved(int mouseX, int mouseY) {
            return Math.abs(mouseX - pressX) >= DRAG_THRESHOLD
                    || Math.abs(mouseY - pressY) >= DRAG_THRESHOLD;
        }
    }

    /**
     * One card as it is currently drawn: whose it is, where on screen, and which way round.
     *
     * <p>Built once a frame and used by both the drawing and the hit-testing, which is what
     * stops them disagreeing about where anything is. In back-to-front order, so drawing walks
     * it forwards and picking walks it backwards.
     */
    private record Placed(SeatId seat, CardView card, Rect where, int angle) {
    }

    @Override
    protected void init() {
        layout = TableScreenLayout.of(this.width, this.height);
        if (geometry == null) {
            geometry = new BoardGeometry(anchors(), this.width, this.height);
            onBlock = new SurfaceBoard(anchors());
        } else {
            geometry.reshape(anchors(), this.width, this.height);
            onBlock.reshape(anchors());
        }
    }

    /**
     * Whether this player's own mat is the far half of the table from the block's corner.
     *
     * <p>Asked of the mats rather than of the seat's side, because the mats are what is drawn
     * and a seat that ended up on a different half than its side suggested would turn the
     * camera the wrong way round without anything else noticing.
     */
    private boolean myMatIsOnTheSouthHalf() {
        return mySeat()
                .map(seat -> onBlock.matRect(seat).centreY() > onBlock.surface().height() / 2.0)
                .orElse(false);
    }

    /** Whichever board is being played, which is the only thing the two views differ on. */
    private BoardPlacement board() {
        return playingOnTheBlock ? onBlock : geometry;
    }

    /** The table's surface in the world, for turning a cursor into a place on the felt. */
    private TableTop tableTop() {
        return TableTop.forCorner(table.getX(), table.getY(), table.getZ());
    }

    /**
     * Where the cursor is, in whatever space the board being played is measured in.
     *
     * <p>Pixels on the seated screen. On the block it is a ray cast against the table, so it
     * is empty whenever the pointer is not over the felt at all - which is a real answer and
     * the reason a card let go over the floor goes back where it came from.
     */
    private double[] pointer(double mouseX, double mouseY) {
        if (!playingOnTheBlock) {
            return new double[] {mouseX, mouseY};
        }
        return TablePointer.at(tableTop(), mouseX, mouseY)
                .map(spot -> new double[] {spot.x(), spot.y()})
                .orElse(null);
    }

    /**
     * Swaps which board is being played.
     *
     * <p>The camera goes over the table on the way in and back to the player on the way out.
     * Nothing about the game moves: both views are showing the same board, so the swap is
     * only ever a change of where it is being looked at from.
     */
    private void useTheBlock(boolean wanted) {
        playingOnTheBlock = wanted;
        held = null;
        boxFrom = null;
        panFrom = null;
        if (wanted) {
            TableCameraView.lookAt(table, myMatIsOnTheSouthHalf());
        } else {
            TableCameraView.release();
            ClientTableHighlight.clear();
        }
    }

    @Override
    public void removed() {
        // Both doors out. onClose is the polite one and removed is the one that catches
        // everything else - the screen being replaced, the world going away - and the camera
        // has to go back to the player through either. A view left looking at a table after
        // its screen has gone is a player who cannot see where they are.
        TableCameraView.release();
        TablePointer.forget();
        ClientTableHighlight.clear();
        super.removed();
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

    /**
     * The shape of the table, as the mat layout needs it.
     *
     * <p>Derived from how many seats the session has rather than from the blocks in the world:
     * the client is told a board, not a building, and the session froze the cluster's shape
     * when it started. Seats come in facing pairs, which is what {@code SEATS_PER_TABLE} means,
     * so the cells fall out of the count.
     */
    private List<SeatAnchor> anchors() {
        return TableCluster.assumedSeating(view().map(board -> board.seats().size()).orElse(2));
    }

    @Override
    public void tick() {
        super.tick();
        // The game ended, or this client stopped being told about it.
        if (view().isEmpty()) {
            this.onClose();
            return;
        }
        if (geometry.surface().seatCount() != view().get().seats().size()) {
            geometry.reshape(anchors(), this.width, this.height);
            onBlock.reshape(anchors());
        }
    }

    // ------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately empty, and it has to stay that way. Screen.render draws the background
        // first and the widgets after it, so anything painted here lands on top of the mats,
        // piles and cards that render() has already drawn - the felt would swallow the whole
        // board. The felt is laid down by render() itself instead, before anything sits on it.
        // (No vanilla dim either: the table is the screen, and a darkened world behind a
        // full-screen table is a smear nobody can see.)
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The felt, edge to edge, under everything including the buttons - unless the felt
        // being played is the real one, in which case the world shows through and the only
        // things drawn here are the ones that were never on the table anyway.
        if (!playingOnTheBlock) {
            graphics.fill(0, 0, this.width, this.height, FELT);
        }

        GameView board = view().orElse(null);
        if (board == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        cursorX = mouseX;
        cursorY = mouseY;
        ClientHoverState.clear();

        Placed hovered = null;
        if (playingOnTheBlock) {
            // The block draws its own board. What it needs from here is what the cursor is on,
            // because the world renderer has no idea where anybody's mouse is.
            hovered = frontMostAt(everythingOnTheTable(board), mouseX, mouseY);
            ClientTableHighlight.set(idOf(hovered), List.copyOf(selected));
        } else {
            renderMats(graphics, board);
            renderPiles(graphics, board, mouseX, mouseY);

            List<Placed> onTable = everythingOnTheTable(board);
            hovered = frontMostAt(onTable, mouseX, mouseY);
            for (Placed placed : onTable) {
                drawTableCard(graphics, placed.card(), placed.where(), placed.angle(),
                        placed == hovered || isSelected(placed.card()));
            }
            renderPileBadges(graphics, board, onTable);
        }
        if (hovered != null) {
            offerToInspector(hovered.card());
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        renderStatus(graphics, board);
        renderHand(graphics, board, mouseX, mouseY);
        renderHeldCard(graphics, board, mouseX, mouseY);

        if (!attaching.isEmpty()) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.attaching", attaching.size()),
                    this.width / 2, 6, this.width - 16, ACCENT);
        }
        if (boxFrom != null) {
            Rect box = boxBetween(boxFrom[0], boxFrom[1], mouseX, mouseY);
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), BOX_FILL);
            graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), ACCENT);
        }
        if (showingLog) {
            renderLog(graphics, board);
        }
        if (showingKeys) {
            renderKeys(graphics);
        }
        if (menu != null) {
            menu.render(graphics, this.font, mouseX, mouseY);
        }
    }

    private static CardInstanceId idOf(Placed placed) {
        return placed != null && placed.card() instanceof CardView.Visible visible ? visible.id() : null;
    }

    /** The rectangle between two corners, whichever way round they were dragged. */
    private static Rect boxBetween(int fromX, int fromY, int toX, int toY) {
        return new Rect(
                Math.min(fromX, toX), Math.min(fromY, toY),
                Math.abs(toX - fromX), Math.abs(toY - fromY));
    }

    /**
     * Everybody's mat, with their name and life on it.
     *
     * <p>On the mat rather than in a list across the top, because that is where the
     * information belongs: you look at somebody's board and their life total is right there
     * with it, the way a life pad sits beside a real one.
     */
    private void renderMats(GuiGraphics graphics, GameView board) {
        SeatId me = mySeat().orElse(null);
        for (SeatView seat : board.seats()) {
            Rect mat = board().matRect(seat.seat());
            if (mat.isEmpty()) {
                continue;
            }
            boolean mine = me != null && me.equals(seat.seat());
            graphics.fill(mat.x(), mat.y(), mat.right(), mat.bottom(), mine ? MAT_MINE : MAT);
            // The seat's own colour, which is how four identical rectangles become four
            // boards. Brighter for whoever's turn it is.
            graphics.renderOutline(mat.x(), mat.y(), mat.width(), mat.height(),
                    SeatColour.at(seat.seat().index(),
                            seat.seat().equals(board.turn().activeSeat()) ? 0xFF : 0xAA));

        }
    }

    /** Everybody's piles, in a row along the near edge of their own mat. */
    private void renderPiles(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        for (SeatView seat : board.seats()) {
            for (int index = 0; index < Zone.PILES.size(); index++) {
                Rect where = board().pileRect(seat.seat(), index, Zone.PILES.size());
                if (where.isEmpty() || where.width() < 4) {
                    continue;
                }
                drawPile(graphics, seat, Zone.PILES.get(index), where.shrink(1),
                        where.contains(mouseX, mouseY));
            }
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
        Rect art = pile.shrink(1);
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

        if (art.height() > this.font.lineHeight + 2) {
            Component label = Component.literal(Integer.toString(count));
            int labelWidth = this.font.width(label) + 4;
            graphics.fill(art.right() - labelWidth, art.bottom() - this.font.lineHeight - 1,
                    art.right(), art.bottom(), GHOST_TINT);
            GuiText.draw(graphics, this.font, label, art.right() - labelWidth + 2,
                    art.bottom() - this.font.lineHeight, labelWidth, LABEL);
        }
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

    /**
     * Every card on every mat, back to front, ready to draw or to click.
     *
     * <p>Built once a frame and handed to both, which is what stops them disagreeing about
     * where anything is. Attachments come immediately after the card they are on, so they draw
     * over their host and are found before it - otherwise the creature underneath swallows
     * every click meant for its equipment.
     */
    private List<Placed> everythingOnTheTable(GameView board) {
        List<Placed> placed = new ArrayList<>();
        for (SeatView seat : board.seats()) {
            List<CardView> cards = seat.zone(Zone.BATTLEFIELD).cards();
            List<TablePosition> spots = spotsIn(cards);
            List<Integer> depths = TableStacking.depths(spots);

            for (int index = 0; index < cards.size(); index++) {
                CardView card = cards.get(index);
                if (isHeld(card) || card.host().isPresent()) {
                    continue;
                }
                Rect where = spotOf(seat.seat(), card, depths.get(index));
                placed.add(new Placed(seat.seat(), card, where, angleOf(card)));

                List<CardView> attached = attachmentsOf(cards, card);
                boolean left = TableAttachments.fansLeft(where, new Rect(0, 0, this.width, this.height));
                for (int slot = 0; slot < attached.size(); slot++) {
                    if (isHeld(attached.get(slot))) {
                        continue;
                    }
                    Rect at = left
                            ? TableAttachments.slot(where, slot)
                            : TableAttachments.slotOnTheRight(where, slot);
                    placed.add(new Placed(seat.seat(), attached.get(slot), at, angleOf(attached.get(slot))));
                }
            }
        }
        return placed;
    }

    /** Everything currently sitting on this card, in the board's own order. */
    private static List<CardView> attachmentsOf(List<CardView> all, CardView host) {
        if (!(host instanceof CardView.Visible visible)) {
            return List.of();
        }
        List<CardView> found = new ArrayList<>();
        for (CardView card : all) {
            if (card.host().filter(visible.id()::equals).isPresent()) {
                found.add(card);
            }
        }
        return found;
    }

    private static List<TablePosition> spotsIn(List<CardView> cards) {
        List<TablePosition> spots = new ArrayList<>(cards.size());
        for (CardView card : cards) {
            spots.add(card.placedAt().orElse(null));
        }
        return spots;
    }

    /**
     * Where a card on a mat is drawn.
     *
     * <p>Its own place, leaned up and to the left by however many cards are under it. The card
     * has not moved - that is state - but a pile that draws every card in exactly the same
     * pixels is a pile that looks like one card.
     */
    private Rect spotOf(SeatId seat, CardView card, int depth) {
        Rect where = board().rectOf(seat, card.placedAt().orElse(TablePosition.ORIGIN));
        int lean = TableStacking.offsetFor(depth, board().cardWidth(seat));
        return lean == 0
                ? where
                : new Rect(where.x() + lean, where.y() + lean, where.width(), where.height());
    }

    /** The pile counts, drawn last so a stack of four says four over whatever is on top of it. */
    private void renderPileBadges(GuiGraphics graphics, GameView board, List<Placed> onTable) {
        for (SeatView seat : board.seats()) {
            List<CardView> cards = seat.zone(Zone.BATTLEFIELD).cards();
            List<TablePosition> spots = spotsIn(cards);
            for (int index = 0; index < cards.size(); index++) {
                if (TableStacking.isBuriedAt(spots, index)) {
                    continue;
                }
                int pile = TableStacking.pileSizeAt(spots, index);
                if (pile <= 1) {
                    continue;
                }
                CardView card = cards.get(index);
                for (Placed placed : onTable) {
                    if (placed.card() == card) {
                        drawPileBadge(graphics, placed.where(), pile);
                        break;
                    }
                }
            }
        }
    }

    /** The pile count, on the corner nearest the top of the stack. */
    private void drawPileBadge(GuiGraphics graphics, Rect where, int size) {
        if (where.height() < this.font.lineHeight + 3) {
            return;
        }
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
     * Your hand, fanned along the bottom, with the card under the cursor risen out of it.
     *
     * <p>No panel behind it and no frame around each card. Both were clutter over the only
     * part of the screen that is nothing but pictures: a hand is read by looking at the art,
     * and a border on every card in a fan is a row of borders.
     */
    private void renderHand(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        Rect area = layout().hand();
        SeatId seat = mySeat().orElse(null);
        if (seat == null) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.spectating"),
                    area.x() + area.width() / 2, area.bottom() - 14, area.width(), DIM);
            return;
        }

        List<CardView> hand = board.seat(seat).zone(Zone.HAND).cards();
        int lifted = handIndexAt(board, mouseX, mouseY);

        // The lifted one last, so it is drawn over the cards it has risen in front of.
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < hand.size(); index++) {
                if (isHeld(hand.get(index)) || (index == lifted) != (pass == 1)) {
                    continue;
                }
                HandFan.Slot slot = HandFan.slot(area, hand.size(), index, lifted);
                drawBareCard(graphics, hand.get(index), slot.where(), slot.angle());
            }
        }
        if (lifted >= 0 && lifted < hand.size()) {
            offerToInspector(hand.get(lifted));
        }
    }

    /** Which card of the hand the cursor is on, or -1. */
    private int handIndexAt(GameView board, int x, int y) {
        SeatId seat = mySeat().orElse(null);
        if (seat == null || !layout().hand().contains(x, y)) {
            return -1;
        }
        return HandFan.at(layout().hand(),
                board.seat(seat).zone(Zone.HAND).cards().size(), x, y);
    }

    /**
     * The strip along the top: everybody's name and life, and whose turn it is.
     *
     * <p>In both views now. On the block the mats are two blocks away and a life total painted
     * on one would be unreadable at any height worth playing at; on the screen it frees the
     * mats to be nothing but board, which is what they are for.
     */
    private void renderStatus(GuiGraphics graphics, GameView board) {
        Rect area = layout().status();
        if (area.isEmpty()) {
            return;
        }
        GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());

        List<SeatView> seats = board.seats();
        SeatId me = mySeat().orElse(null);
        SeatId active = board.turn().activeSeat();
        int turnWidth = Math.min(area.width() / 4, 120);
        int column = seats.isEmpty() ? area.width() : (area.width() - turnWidth) / seats.size();
        int line = area.y() + (area.height() - this.font.lineHeight) / 2;

        for (int index = 0; index < seats.size(); index++) {
            SeatView seat = seats.get(index);
            String who = seat.occupant().map(player -> player.name())
                    .orElseGet(() -> Component.translatable("message.gathering.seat_empty").getString());
            Component text = Component.translatable(
                    "screen.gathering.table.mat_line", who, seat.life(),
                    count(seat, Zone.HAND), count(seat, Zone.LIBRARY));
            if (!seat.counters().isEmpty()) {
                text = text.copy().append(Component.literal("  " + describeCounters(seat)));
            }
            GuiText.draw(graphics, this.font, text,
                    area.x() + 4 + index * column, line, column - 8,
                    SeatColour.at(seat.seat().index(), 0xFF));
        }

        String who = board.seat(active).occupant()
                .map(player -> player.name())
                .orElseGet(() -> Component.translatable("message.gathering.seat_empty").getString());
        boolean mine = me != null && me.equals(active);
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.turn", board.turn().turnNumber(), who),
                area.right() - turnWidth, line, turnWidth - 4, mine ? ACCENT : DIM);
    }

    /**
     * What has happened, most recent last.
     *
     * <p>Over the table rather than beside it. The log is read in bursts - somebody asks
     * "wait, what did you just do" - and giving it a permanent column would cost table space
     * every turn to answer a question asked twice a game.
     */
    private void renderLog(GuiGraphics graphics, GameView board) {
        Rect area = new Rect(this.width / 3, 0, this.width - this.width / 3, layout().hand().y());
        GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());

        int lines = Math.max(1, (area.height() - 8) / (this.font.lineHeight + 1));
        List<LogEntry> log = board.log();
        int from = Math.max(0, log.size() - lines);

        int y = area.y() + 4;
        for (int index = from; index < log.size(); index++) {
            GuiText.draw(graphics, this.font, GameLogText.render(board, log.get(index)),
                    area.x() + 4, y, area.width() - 8, LABEL);
            y += this.font.lineHeight + 1;
        }
        if (log.isEmpty()) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.log_empty"),
                    area.x() + area.width() / 2, area.y() + area.height() / 2, area.width() - 8, DIM);
        }
    }

    /**
     * Every key, in one place.
     *
     * <p>A Tabletop Simulator control scheme is a good one and a completely undiscoverable
     * one: nothing on screen suggests that F turns a card over or that Home finds the table
     * again. The bar along the bottom carries the handful people need in the first minute and
     * this carries the rest, on the key every game uses for exactly this.
     */
    private void renderKeys(GuiGraphics graphics) {
        Rect area = new Rect(this.width / 8, this.height / 12,
                Math.min(this.width - this.width / 4, 420),
                layout().hand().y() - this.height / 12 - 4);
        if (area.width() < 80 || area.height() < 60) {
            return;
        }
        GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());

        int y = area.y() + 5;
        int line = this.font.lineHeight + 1;
        int inner = area.width() - 10;
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.keys_title"),
                area.x() + 5, y, inner, ACCENT);
        y += line + 2;

        for (String[] section : KEY_HELP) {
            if (y + line > area.bottom() - line - 4) {
                break;
            }
            GuiText.draw(graphics, this.font, Component.translatable(section[0]),
                    area.x() + 5, y, inner, ACCENT);
            y += line;
            for (int index = 1; index < section.length; index++) {
                if (y + line > area.bottom() - line - 4) {
                    break;
                }
                GuiText.draw(graphics, this.font, Component.translatable(section[index]),
                        area.x() + 11, y, inner - 6, LABEL);
                y += line;
            }
            y += 2;
        }
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.keys_close"),
                area.x() + 5, area.bottom() - line - 3, inner, DIM);
    }

    /**
     * The card in the air, drawn under the cursor exactly where it would land.
     *
     * <p>Same size and angle as it will have once dropped, so the drag is a preview rather
     * than a promise, and an outline on the mat it would land on so you can see whose side you
     * are about to put it on.
     */
    private void renderHeldCard(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        if (held == null) {
            ClientTableHighlight.aimAt(null, -1);
            return;
        }
        CardView card = findCard(board, held.card()).orElse(null);
        if (card == null) {
            return;
        }
        double[] at = pointer(mouseX, mouseY);
        SeatId landing = at == null ? null : board().seatAt(at[0], at[1]);

        if (landing != null && at != null) {
            ClientTableHighlight.aimAt(landing, board().pileAt(landing, Zone.PILES.size(), at[0], at[1]));
        } else {
            ClientTableHighlight.aimAt(null, -1);
        }

        // The mat it would land on, outlined, so you can see whose side you are about to put
        // it on. Only on the seated screen: the mats on the block are measured on the table
        // and there is nothing honest to draw them over here.
        if (landing != null && !playingOnTheBlock) {
            Rect mat = board().matRect(landing);
            graphics.renderOutline(mat.x(), mat.y(), mat.width(), mat.height(), ACCENT);
        }

        // The card in the air is always drawn on the screen, at the size a card is on the
        // screen. It is the one thing that is genuinely in the player's hand rather than on
        // the table, and a card held over a table is not lying on it.
        SeatId sizedFor = landing != null ? landing : held.from();
        Rect airborne = playingOnTheBlock
                ? centredOnCursor(mouseX, mouseY)
                : centred(mouseX - held.grabX(), mouseY - held.grabY(),
                        board().cardWidth(sizedFor), board().cardHeight(sizedFor));
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, LIFT);
        drawCard(graphics, card, airborne, false, false);
        graphics.pose().popPose();
    }

    private static Rect centred(int middleX, int middleY, int width, int height) {
        return new Rect(middleX - width / 2, middleY - height / 2, width, height);
    }

    /** A hand-sized card taken by the middle, for the view where the board is not on screen. */
    private Rect centredOnCursor(int mouseX, int mouseY) {
        int height = Math.max(24, layout().hand().height() - 8);
        int width = Math.max(16, Math.round(height * 488f / 680f));
        return new Rect(mouseX - width / 2, mouseY - height / 2, width, height);
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

        if ((showingLog || showingKeys) && layout().isOnFelt(x, y)) {
            // Either panel is covering the table; the first click puts it away rather than
            // doing something to a card the player cannot see.
            showingLog = false;
            showingKeys = false;
            return true;
        }

        // Middle-drag pans, which is what it does in every table simulator.
        if (button == 2) {
            panFrom = new int[] {x, y};
            return true;
        }

        Placed onTable = frontMostAt(everythingOnTheTable(board), x, y);
        if (!attaching.isEmpty()) {
            // Anywhere but a card cancels, which is what clicking off a half-finished thing
            // should always do.
            if (onTable != null) {
                finishAttaching(onTable.card());
            } else {
                attaching = List.of();
            }
            return true;
        }
        if (onTable != null) {
            return pressCard(board, onTable.card(), onTable.seat(), onTable.where(), false, x, y, button);
        }

        Optional<CardView> inHand = cardInHandAt(board, x, y);
        if (inHand.isPresent()) {
            return pressCard(board, inHand.get(), mySeat().orElseThrow(),
                    handSlotOf(board, inHand.get()), true, x, y, button);
        }

        SeatId pileSeat = pileSeatAt(board, x, y);
        if (pileSeat != null) {
            return clickPile(pileSeat, pileZoneAt(board, x, y), x, y, button);
        }

        if (layout().isOnFelt(x, y)) {
            if (button == 1) {
                openTableMenu(x, y);
                return true;
            }
            // Empty table: drag out a box to pick several cards, or click to let them go.
            if (button == 0) {
                selected.clear();
                boxFrom = new int[] {x, y};
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean pressCard(
            GameView board, CardView card, SeatId seat, Rect where,
            boolean fromHand, int x, int y, int button) {
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
            held = grab(visible.id(), seat, fromHand, where, x, y);
            return true;
        }
        return true;
    }

    /**
     * Picks a card up, remembering where on it the cursor took hold.
     *
     * <p>The offset is the whole reason a drag feels right: a card that snaps its corner to
     * the cursor jumps out from under your finger the moment you touch it. A card coming out
     * of the hand has no such offset to keep - its slot in the fan is not where it is going -
     * so it takes the cursor by the middle, which is where you would expect to be holding it.
     */
    private Held grab(CardInstanceId card, SeatId seat, boolean fromHand, Rect where, int x, int y) {
        double[] at = fromHand ? null : pointer(x, y);
        if (at == null) {
            // Straight from the hand, or from somewhere the table cannot answer for: the card
            // takes the cursor by the middle, which is where you would expect to be holding it.
            return new Held(card, seat, fromHand, 0, 0, x, y);
        }
        return new Held(card, seat, fromHand,
                (int) Math.round(at[0] - where.centreX()),
                (int) Math.round(at[1] - where.centreY()), x, y);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panFrom != null && button == 2) {
            pan(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panFrom != null && button == 2) {
            panFrom = null;
            return true;
        }
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

        if (layout().hand().contains(x, y)) {
            if (!dropped.fromHand()) {
                send(new GameEvent.CardMoved(me, dropped.card(), ZoneRef.of(me, Zone.HAND), Placement.BOTTOM));
            }
            return true;
        }

        // A press that never moved is a click, and a click on a card already on the table taps
        // it - the most common thing anyone does, and the one gesture worth the plain left click.
        if (!dropped.fromHand() && !dropped.hasMoved(x, y)) {
            findCard(view().orElse(null), dropped.card())
                    .ifPresent(card -> send(new GameEvent.CardTapSet(me, dropped.card(), !card.tapped())));
            return true;
        }

        // Whose mat it landed on is where it goes. Stealing a creature is dragging it to your
        // own side of the table, and the move event falls out of that rather than out of a
        // mode somebody had to set first.
        double[] at = pointer(x, y);
        if (at == null) {
            // Let go somewhere that is not the table at all. The card stays where it was.
            return true;
        }

        // Whose mat it landed on is decided by where the cursor is, not by where the card's
        // middle would end up. Asking about the card meant a drop near an edge - where the
        // card overhangs and its middle is off the mat - was silently refused, which reads as
        // the table ignoring you.
        SeatId landing = board().seatAt(at[0], at[1]);
        if (landing == null) {
            return true;
        }

        // A zone catches the card before the felt does, so putting something in the graveyard
        // is dropping it on the graveyard.
        int zone = board().pileAt(landing, Zone.PILES.size(), at[0], at[1]);
        if (zone >= 0) {
            send(new GameEvent.CardMoved(me, dropped.card(),
                    ZoneRef.of(landing, Zone.PILES.get(zone)), Placement.TOP));
            selected.clear();
            return true;
        }

        TablePosition where = board().positionOn(
                landing, at[0] - dropped.grabX(), at[1] - dropped.grabY());
        if (!dropped.fromHand() && selected.contains(dropped.card())) {
            dropGroup(dropped, landing, where, me);
        } else {
            send(new GameEvent.CardMoved(
                    me, dropped.card(), ZoneRef.of(landing, Zone.BATTLEFIELD), Placement.at(where)));
        }
        return true;
    }

    /**
     * Puts a whole selection down, keeping the arrangement it was in.
     *
     * <p>One delta for all of them, trimmed until every card fits on the mat - so a group
     * shoved into a corner slides along the edge instead of collapsing into a single pile,
     * which is what clamping each card on its own would do to a board somebody spent the game
     * building.
     */
    private void dropGroup(Held dropped, SeatId landing, TablePosition where, SeatId me) {
        GameView board = view().orElse(null);
        if (board == null) {
            return;
        }
        List<CardView> moving = selectedOn(board);
        TablePosition before = findCard(board, dropped.card()).flatMap(CardView::placedAt).orElse(null);
        if (before == null || moving.isEmpty()) {
            send(new GameEvent.CardMoved(
                    me, dropped.card(), ZoneRef.of(landing, Zone.BATTLEFIELD), Placement.at(where)));
            return;
        }

        List<TablePosition> spots = new ArrayList<>(moving.size());
        for (CardView card : moving) {
            spots.add(card.placedAt().orElse(null));
        }
        List<TablePosition> after = TableDrag.movedTogether(
                spots, where.x() - before.x(), where.y() - before.y());

        for (int index = 0; index < moving.size(); index++) {
            if (!(moving.get(index) instanceof CardView.Visible visible) || after.get(index) == null) {
                continue;
            }
            send(new GameEvent.CardMoved(me, visible.id(),
                    ZoneRef.of(landing, Zone.BATTLEFIELD), Placement.at(after.get(index))));
        }
        selected.clear();
    }

    /**
     * The wheel zooms, anchored to the cursor.
     *
     * <p>Tabletop Simulator's wheel, and every map's: turning a card is Q and E, which leaves
     * the wheel free for the thing a wheel is for.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        double factor = scrollY > 0 ? ZOOM_STEP : 1 / ZOOM_STEP;
        if (playingOnTheBlock) {
            // Looking straight down, zoom is height. There is no cursor anchoring here: the
            // eye comes down towards the middle of what it is already looking at, which is
            // what moving your head towards a table does.
            TableCameraView.zoom(factor);
        } else {
            geometry.zoom(factor, mouseX, mouseY);
        }
        return true;
    }

    // ------------------------------------------------------------ hit-testing

    /**
     * The card under a point, front-most first.
     *
     * <p>Front to back, because the card you can see is the card you meant, and turned cards
     * are tested at the angle they are drawn at - so the empty corner of an angled card is
     * table and a click there reaches whatever is underneath it.
     */
    private Placed frontMostAt(List<Placed> onTable, int x, int y) {
        // Two spaces, and the guard belongs to the screen either way: the hand and the bar sit
        // over the table in both views, and a click on your own hand must not also reach the
        // felt underneath it.
        if (!layout().isOnFelt(x, y)) {
            return null;
        }
        double[] at = pointer(x, y);
        if (at == null) {
            return null;
        }
        int pointX = (int) Math.round(at[0]);
        int pointY = (int) Math.round(at[1]);
        for (int index = onTable.size() - 1; index >= 0; index--) {
            Placed placed = onTable.get(index);
            if (placed.where().containsTurned(placed.angle(), pointX, pointY)) {
                return placed;
            }
        }
        return null;
    }

    private Optional<CardView> cardInHandAt(GameView board, int x, int y) {
        SeatId seat = mySeat().orElse(null);
        int index = handIndexAt(board, x, y);
        if (seat == null || index < 0) {
            return Optional.empty();
        }
        CardView card = board.seat(seat).zone(Zone.HAND).cards().get(index);
        return isHeld(card) ? Optional.empty() : Optional.of(card);
    }

    private Rect handSlotOf(GameView board, CardView card) {
        SeatId seat = mySeat().orElse(null);
        if (seat == null) {
            return Rect.NONE;
        }
        List<CardView> hand = board.seat(seat).zone(Zone.HAND).cards();
        int index = hand.indexOf(card);
        return index < 0
                ? Rect.NONE
                : HandFan.slot(layout().hand(), hand.size(), index, index).where();
    }

    /** Whose pile row a point is in, or null. */
    private SeatId pileSeatAt(GameView board, int x, int y) {
        int slot = pileSlotAt(board, x, y);
        return slot < 0 ? null : board.seats().get(slot / Zone.PILES.size()).seat();
    }

    private Zone pileZoneAt(GameView board, int x, int y) {
        int slot = pileSlotAt(board, x, y);
        return slot < 0 ? Zone.LIBRARY : Zone.PILES.get(slot % Zone.PILES.size());
    }

    /**
     * Which pile a point is on, as one number covering both which seat and which zone.
     *
     * <p>One walk rather than two, because the two used to walk the piles separately and
     * could in principle stop at different ones - the seat from the first and the zone from
     * the second, which is a click that shuffles somebody else's library.
     */
    private int pileSlotAt(GameView board, int x, int y) {
        double[] at = pointer(x, y);
        if (at == null || !layout().isOnFelt(x, y)) {
            return -1;
        }
        for (int seat = 0; seat < board.seats().size(); seat++) {
            for (int index = 0; index < Zone.PILES.size(); index++) {
                Rect pile = board().pileRect(board.seats().get(seat).seat(), index, Zone.PILES.size());
                if (pile.contains((int) Math.round(at[0]), (int) Math.round(at[1]))) {
                    return seat * Zone.PILES.size() + index;
                }
            }
        }
        return -1;
    }

    private boolean isSelected(CardView card) {
        return card instanceof CardView.Visible visible && selected.contains(visible.id());
    }

    /**
     * The cards a verb should apply to.
     *
     * <p>The selection when the card acted on is part of it, and just that card otherwise.
     * Right-clicking a card outside the selection is somebody addressing that card, not
     * forgetting what they had picked.
     */
    private List<CardInstanceId> targetsFor(CardInstanceId card) {
        return selected.contains(card) ? List.copyOf(selected) : List.of(card);
    }

    /** Every selected card on the table, wherever it is, in board order. */
    private List<CardView> selectedOn(GameView board) {
        List<CardView> found = new ArrayList<>();
        for (SeatView seat : board.seats()) {
            for (CardView card : seat.zone(Zone.BATTLEFIELD).cards()) {
                if (isSelected(card)) {
                    found.add(card);
                }
            }
        }
        return found;
    }

    /**
     * Picks out every card the box touches, in place of whatever was picked before.
     *
     * <p>The box is dragged on the screen and the cards may not be measured there, so it is
     * carried across corner by corner. A corner that lands off the table takes the selection
     * with it: a box half over the floor has no honest answer, and picking whatever happened
     * to fall inside the half that was on the felt is not one.
     */
    private void selectWithin(GameView board, Rect box) {
        selected.clear();
        double[] from = pointer(box.x(), box.y());
        double[] to = pointer(box.right(), box.bottom());
        if (from == null || to == null) {
            return;
        }
        Rect within = boxBetween(
                (int) Math.round(from[0]), (int) Math.round(from[1]),
                (int) Math.round(to[0]), (int) Math.round(to[1]));
        for (Placed placed : everythingOnTheTable(board)) {
            if (placed.card() instanceof CardView.Visible visible && placed.where().overlaps(within)) {
                selected.add(visible.id());
            }
        }
    }

    private boolean isHeld(CardView card) {
        return held != null && card instanceof CardView.Visible visible && visible.id().equals(held.card());
    }

    // ----------------------------------------------------------------- menus

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
            if (card.host().isPresent()) {
                entries.add(entry("detach", () -> eachTarget(board, targets, target ->
                        new GameEvent.CardAttached(me, target, null))));
            } else {
                entries.add(entry("attach", () -> {
                    attaching = targets;
                    selected.clear();
                }));
            }
            entries.add(entry("copy", () -> eachTarget(board, targets, target ->
                    new GameEvent.TokenCopyCreated(me, target, me))));
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
    private boolean clickPile(SeatId owner, Zone pile, int x, int y, int button) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return true;
        }
        GatheringButtons.clickSound();
        // Anybody may open anybody's graveyard - it is public - but the verbs that only make
        // sense on your own library are offered only there, because the mod refuses a search
        // of somebody else's anyway and a menu full of refusals is worse than a short one.
        if (button == 1 && owner.equals(me)) {
            openPileMenu(me, pile, x, y);
            return true;
        }
        if (pile == Zone.LIBRARY && owner.equals(me)) {
            send(new GameEvent.CardsDrawn(me, me, 1));
        } else if (pile != Zone.LIBRARY) {
            openPile(owner, pile, false);
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
    /**
     * Asks what token, then how many.
     *
     * <p>Two questions rather than one screen with two fields, because they are answered in
     * that order and the second one is usually "one". The name goes to the server, which does
     * the looking up - a token is a real printing from Scryfall and not something a client
     * describes.
     */
    private void askForToken() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new TextPromptScreen(
                Component.translatable("screen.gathering.token.name"),
                Component.translatable("screen.gathering.token.hint"),
                CreateTokenPayload.MAX_NAME,
                name -> net.minecraft.client.Minecraft.getInstance().setScreen(new AmountScreen(
                        Component.translatable("screen.gathering.amount.tokens"), 1,
                        count -> ClientNetworking.send(new CreateTokenPayload(table, name, count))))));
    }

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

    private void openPile(SeatId owner, Zone pile, boolean opensALibrary) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new PileScreen(table, owner, pile, opensALibrary));
    }

    /** The menu for the table itself, for the verbs that are about a seat rather than a card. */
    private void openTableMenu(int x, int y) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return;
        }
        List<ContextMenu.Entry> entries = ContextMenu.entries();
        entries.add(entry("draw", () -> send(new GameEvent.CardsDrawn(me, me, 1))));
        entries.add(entry("make_token", this::askForToken));
        entries.add(entry(showingLog ? "hide_log" : "show_log", () -> showingLog = !showingLog));
        view().ifPresent(board -> entries.add(entry("pass_turn", () -> passTurn(board, me))));
        entries.add(entry("untap_all", () -> send(new GameEvent.SeatUntappedAll(me, me))));
        entries.add(entry("shuffle", () -> send(new GameEvent.LibraryShuffled(me, me))));
        entries.add(entry("gain_life", () -> send(new GameEvent.LifeChanged(me, me, 1))));
        entries.add(entry("lose_life", () -> send(new GameEvent.LifeChanged(me, me, -1))));
        view().ifPresent(board -> entries.add(entry("my_counters",
                () -> openCounters(new CountersScreen.Subject.Seat(
                        me, CountersScreen.titleForSeat(board, me))))));
        entries.add(entry("show_everything", this::showEverything));
        menu = ContextMenu.at(this.font, x, y, this.width, this.height, entries);
    }

    /** Puts the waiting cards onto the one just clicked, and leaves the mode either way. */
    private void finishAttaching(CardView host) {
        List<CardInstanceId> cards = attaching;
        attaching = List.of();
        SeatId me = mySeat().orElse(null);
        if (me == null || !(host instanceof CardView.Visible visible)) {
            return;
        }
        GatheringButtons.clickSound();
        for (CardInstanceId card : cards) {
            if (!card.equals(visible.id())) {
                send(new GameEvent.CardAttached(me, card, visible.id()));
            }
        }
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


    /**
     * The keys, matched to Tabletop Simulator's defaults.
     *
     * <p>Anybody arriving at this table has played on that one, and a key that does something
     * else here is a key they will press by accident all evening. So F flips, Q and E turn,
     * G groups, R is the deck verb, Alt reads a card, and the number row draws that many -
     * all straight from TTS.
     *
     * <p>Where TTS has no equivalent - passing the turn, the log, life - the key is ours and
     * chosen not to collide with one of theirs.
     */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_F1) {
            showingKeys = !showingKeys;
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                && (menu != null || !attaching.isEmpty() || showingKeys)) {
            menu = null;
            attaching = List.of();
            showingKeys = false;
            return true;
        }
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return super.keyPressed(key, scanCode, modifiers);
        }

        // The number row draws that many cards, exactly as it does in TTS.
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            send(new GameEvent.CardsDrawn(me, me, key - org.lwjgl.glfw.GLFW.GLFW_KEY_0));
            return true;
        }

        switch (key) {
            // --- TTS object keys, applied to whatever is under the cursor or selected ---
            case org.lwjgl.glfw.GLFW.GLFW_KEY_F -> {
                return flipUnderCursor(me);
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_Q -> {
                return setTapUnderCursor(me, false);
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_E -> {
                return setTapUnderCursor(me, true);
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_G -> {
                // TTS groups the selection into a stack; the nearest thing here is putting the
                // selected cards onto one another, which is what a stack of cards is.
                return groupSelection(me);
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_R -> {
                send(new GameEvent.LibraryShuffled(me, me));
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE -> {
                return deleteSelection(me);
            }

            // --- TTS camera keys ---
            // WASD and the arrows slide the view, which is how TTS moves its camera and the
            // only way to pan at all on a mouse without a wheel to press. The sign is the
            // table's, not the camera's: pressing right looks further right, so the table
            // slides left under you - the same direction dragging the felt leftwards would.
            case org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                pan(0, PAN_STEP);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_S, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                pan(0, -PAN_STEP);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> {
                pan(PAN_STEP, 0);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> {
                pan(-PAN_STEP, 0);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> {
                // The way back when you have zoomed into a corner and lost the table.
                showEverything();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_V -> {
                // Between the two boards. Same game either way; only the place you are
                // looking at it from changes.
                useTheBlock(!playingOnTheBlock);
                return true;
            }

            // --- ours, chosen to keep clear of theirs ---
            case org.lwjgl.glfw.GLFW.GLFW_KEY_U -> {
                send(new GameEvent.SeatUntappedAll(me, me));
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_L -> {
                showingLog = !showingLog;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> {
                view().ifPresent(board -> passTurn(board, me));
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
        return super.keyPressed(key, scanCode, modifiers);
    }

    /**
     * Slides the view, whichever view it is.
     *
     * <p>Pixels on the seated screen and blocks on the table, which are not the same quantity
     * at all - so the one that is not pixels is scaled by how far a pixel gets you at this
     * height. Panning that moves the world by a different amount than the hand is the single
     * most common way a drag feels wrong.
     */
    private void pan(double pixelsX, double pixelsY) {
        if (playingOnTheBlock) {
            TableCameraView.pan(-pixelsX / PIXELS_PER_BLOCK, -pixelsY / PIXELS_PER_BLOCK);
        } else {
            geometry.pan(pixelsX, pixelsY);
        }
    }

    private void showEverything() {
        if (playingOnTheBlock) {
            TableCameraView.showEverything();
        } else {
            geometry.showEverything();
        }
    }

    /**
     * What a key press acts on: the selection if there is one, else the card under the cursor.
     *
     * <p>TTS's rule exactly, and the reason its keys feel immediate - you never have to click
     * something first to act on it.
     */
    private List<CardInstanceId> underCursorOrSelected() {
        if (!selected.isEmpty()) {
            return List.copyOf(selected);
        }
        GameView board = view().orElse(null);
        if (board == null) {
            return List.of();
        }
        Placed under = frontMostAt(everythingOnTheTable(board), cursorX, cursorY);
        return under != null && under.card() instanceof CardView.Visible visible
                ? List.of(visible.id())
                : List.of();
    }

    private boolean flipUnderCursor(SeatId me) {
        GameView board = view().orElse(null);
        if (board == null) {
            return false;
        }
        List<CardInstanceId> targets = underCursorOrSelected();
        eachCard(board, targets, seen -> new GameEvent.CardFacingSet(me, seen.id(),
                seen.facing() == Facing.FACE_UP ? Facing.FACE_DOWN : Facing.FACE_UP));
        return !targets.isEmpty();
    }

    /**
     * Taps or untaps whatever the keys are pointing at, which is a quarter turn on screen.
     *
     * <p>These used to nudge the resting angle instead, which drew the same picture and meant
     * something else entirely: a card turned that way looks tapped, is not tapped, and does
     * not come back when the player untaps everything. Tapping is a state the whole table can
     * see and reason about; an angle is decoration. Free rotation is still on the card's own
     * menu, where a thing nobody does twice a turn belongs.
     */
    private boolean setTapUnderCursor(SeatId me, boolean tapped) {
        GameView board = view().orElse(null);
        if (board == null) {
            return false;
        }
        List<CardInstanceId> targets = underCursorOrSelected();
        eachCard(board, targets, seen -> seen.tapped() == tapped
                ? null
                : new GameEvent.CardTapSet(me, seen.id(), tapped));
        return !targets.isEmpty();
    }

    /**
     * Puts every selected card onto the first one, which is what a stack is.
     *
     * <p>TTS's group makes a deck out of a selection. Here the equivalent is dropping them all
     * on the same spot: the pile reads as a pile, and picking the top one off works, because
     * the stacking code already handles exactly this.
     */
    private boolean groupSelection(SeatId me) {
        GameView board = view().orElse(null);
        if (board == null || selected.size() < 2) {
            return false;
        }
        List<CardView> cards = selectedOn(board);
        TablePosition onto = cards.isEmpty()
                ? null
                : cards.get(0).placedAt().orElse(null);
        if (onto == null) {
            return false;
        }
        for (CardView card : cards) {
            if (card instanceof CardView.Visible visible) {
                send(new GameEvent.CardMoved(me, visible.id(),
                        ZoneRef.of(me, Zone.BATTLEFIELD), Placement.at(onto)));
            }
        }
        selected.clear();
        return true;
    }

    /**
     * Removes what is under the cursor, for the one kind of card that can be removed.
     *
     * <p>Only tokens cease to exist. A real card has an owner and a deck to go back to, so
     * deleting one would be losing somebody's card - which is why this sends the token verb
     * and silently passes over anything else.
     */
    private boolean deleteSelection(SeatId me) {
        GameView board = view().orElse(null);
        if (board == null) {
            return false;
        }
        List<CardInstanceId> targets = underCursorOrSelected();
        eachCard(board, targets, seen ->
                seen.token() ? new GameEvent.TokenRemoved(me, seen.id()) : null);
        return !targets.isEmpty();
    }


    /**
     * Hands the turn on, and untaps whoever is receiving it.
     *
     * <p>Two events, because they are two things and the log should say both. Untapping on
     * arrival rather than making the next player do it is the one piece of turn structure
     * worth automating: it is unambiguous, everybody does it, and forgetting it is the single
     * most common way a paper game goes wrong.
     */
    private void passTurn(GameView board, SeatId me) {
        SeatId next = nextSeatAfter(board, board.turn().activeSeat());
        send(new GameEvent.TurnPassed(me, next));
        send(new GameEvent.SeatUntappedAll(me, next));
    }

    private static SeatId nextSeatAfter(GameView board, SeatId seat) {
        List<SeatView> seats = board.seats();
        for (int index = 0; index < seats.size(); index++) {
            if (seats.get(index).seat().equals(seat)) {
                return seats.get((index + 1) % seats.size()).seat();
            }
        }
        return seat;
    }

    private void send(GameEvent event) {
        ClientTableActions.send(table, event);
    }

    // --------------------------------------------------------------- drawing

    /** A card on the table: at its own spot, turned to its own angle, counters and all. */
    /**
     * A card as just its picture, turned, with nothing drawn round it.
     *
     * <p>What the hand uses. The framed version earns its border on the table, where a card
     * has to be told apart from the felt and from the cards under it; in a fan every card is
     * against another card and the borders become a row of lines with slivers of art between
     * them. A card is a picture before it is a token.
     */
    private void drawBareCard(GuiGraphics graphics, CardView card, Rect where, int angle) {
        if (where.isEmpty()) {
            return;
        }
        boolean turned = Math.floorMod(angle, 360) != 0;
        if (turned) {
            graphics.pose().pushPose();
            graphics.pose().translate((float) where.centreX(), (float) where.centreY(), 0f);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            graphics.pose().translate((float) -where.centreX(), (float) -where.centreY(), 0f);
        }
        if (card.isFaceDown()) {
            graphics.blit(CardFaceRenderer.CARD_BACK, where.x(), where.y(), 0f, 0f,
                    where.width(), where.height(), where.width(), where.height());
        } else {
            summaryOf(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, where.x(), where.y(), where.width(), where.height()),
                    () -> GatheringSprites.inset(
                            graphics, where.x(), where.y(), where.width(), where.height()));
        }
        drawCounters(graphics, card, where);
        if (turned) {
            graphics.pose().popPose();
        }
    }

    private void drawTableCard(GuiGraphics graphics, CardView card, Rect where, int angle, boolean hovered) {
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
            layout = TableScreenLayout.of(this.width, this.height);
        }
        return layout;
    }

    @Override
    public void onClose() {
        ClientHoverState.clear();
        TableCameraView.release();
        TablePointer.forget();
        ClientTableHighlight.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
