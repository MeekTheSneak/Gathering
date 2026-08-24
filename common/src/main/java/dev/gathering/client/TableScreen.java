package dev.gathering.client;

import com.mojang.math.Axis;
import dev.gathering.core.game.CardInstance;
import dev.gathering.block.TableBlockEntity;
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
import dev.gathering.core.ui.CardShape;
import dev.gathering.core.ui.HandFan;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.SeatColour;
import dev.gathering.core.ui.TableAttachments;
import dev.gathering.core.ui.TableDrag;
import dev.gathering.core.ui.TableScreenLayout;
import dev.gathering.core.ui.SurfaceBoard;
import dev.gathering.core.ui.TableStacking;
import dev.gathering.core.ui.TableSurface;
import dev.gathering.core.ui.TableVerb;
import dev.gathering.core.ui.TableTop;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import dev.gathering.network.CreateTokenPayload;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /** The line round a group of zones: a marking on the mat rather than a piece of interface. */
    private static final int ZONE_BORDER = 0x66FFFFFF;

    /** Zone names printed on the felt: quieter than a card, loud enough to read. */
    private static final int ZONE_LABEL = 0xFFB9C4C0;

    /** How solid a free chair's outline is: there, and clearly not a board in play. */
    private static final int FREE_SEAT_EDGE = 0x44;
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
                "screen.gathering.table.key_zones",
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
                "screen.gathering.table.key_untap",
                "screen.gathering.table.key_draw_one",
                "screen.gathering.table.key_scry",
                "screen.gathering.table.key_mill",
                "screen.gathering.table.key_reveal",
                "screen.gathering.table.key_surveil",
                "screen.gathering.table.key_to_zones",
                "screen.gathering.table.key_pass",
                "screen.gathering.table.key_shuffle",
                "screen.gathering.table.key_life",
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

    /** How many zones the column holds, refreshed each tick - see pileCount. */
    private int piles = Zone.PILES.size();

    /** Whether the last frame had a card under the cursor. Read by the scripted harness. */
    private boolean hoveringSomething;

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

    /** Whether this screen is showing that table, for anything deciding where to go back to. */
    public boolean isAbout(BlockPos which) {
        return table.equals(which);
    }

    /** The seat the camera is currently framed for, or null for the whole table. */
    private SeatId framedFor;

    /** Frames the board on this seat's own mat, or on the whole table when there is no seat. */
    private void frameTheBoard(SeatId seat) {
        if (seat == null) {
            geometry.showEverything();
        } else {
            geometry.focusOn(seat);
        }
    }

    /** Whether a context menu is up. For the scripted harness, which cannot see one. */
    boolean menuIsOpen() {
        return menu != null;
    }

    /** How many zones this board is drawing per seat. For the harness, as above. */
    int pilesShowing() {
        return pileCount();
    }

    /** Whether a menu is up offering this entry. For the harness, as above. */
    boolean hasMenuEntry(String label) {
        return menu != null && menu.has(label);
    }

    /** Takes the menu entry with this label, if a menu is up and has one. As above. */
    boolean pressMenuEntry(String label) {
        return menu != null && menu.press(label);
    }

    /** Whether the last frame drawn had a card under the cursor. For the harness, as above. */
    boolean isHoveringSomething() {
        return hoveringSomething;
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
            CardInstanceId card, SeatId from, boolean fromHand, Zone fromPile,
            int grabX, int grabY, int pressX, int pressY) {
        // grabX/grabY are in the space the *board* is measured in - pixels on the seated
        // screen, surface units on the block. pressX/pressY stay in pixels, because how far
        // the hand has moved before a press becomes a drag is a question about the mouse.
        //
        // fromPile is the zone the card was lifted off, or null. A press on a pile cannot
        // know yet whether it is a click or the start of a drag, so it becomes a drag either
        // way and the release decides: moved, and the card goes where it was dropped; not
        // moved, and it was a click on the pile after all.

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
            geometry = new BoardGeometry(anchors(), this.width, this.height,
                    layout.status().height(), layout.hand().height());
            onBlock = new SurfaceBoard(anchors());
            // Opened on your own board rather than on the whole table: see focusOn. Somebody
            // with no seat has no own board to open on, and used to get whatever the camera
            // happened to be constructed with - which put the far player's zones off the top
            // of the window. Watching a game you are not in shows the whole table.
            framedFor = mySeat().orElse(null);
            frameTheBoard(framedFor);
        } else {
            geometry.reshape(anchors(), this.width, this.height,
                    layout.status().height(), layout.hand().height());
            onBlock.reshape(anchors());
        }
        // A screen this one opened - a graveyard, a counters panel - took the camera back to
        // the player on its way in. Coming back to the same instance has to take it over the
        // table again, or the player is left holding a board they cannot see.
        if (playingOnTheBlock) {
            TableCameraView.resume(table, myMatIsOnTheSouthHalf(),
                    coveredByTheStatus(), coveredByTheHand());
        }
    }

    /**
     * Whether this player's own mat is the far half of the table from the block's corner.
     *
     * <p>Asked of the mats rather than of the seat's side, because the mats are what is drawn
     * and a seat that ended up on a different half than its side suggested would turn the
     * camera the wrong way round without anything else noticing.
     */
    /** This player's own mat on the real table, in surface units, for the camera to frame. */
    private Rect myMatOnTheBlock() {
        return mySeat().map(seat -> onBlock.matRect(seat)).orElse(Rect.NONE);
    }

    /** How much of the bottom of the window the hand is sitting over. */
    private double coveredByTheHand() {
        return this.height <= 0 ? 0 : layout.hand().height() / (double) this.height;
    }

    /** And how much of the top the life totals are. */
    private double coveredByTheStatus() {
        return this.height <= 0 ? 0 : layout.status().height() / (double) this.height;
    }

    private boolean myMatIsOnTheSouthHalf() {
        return mySeat()
                .map(seat -> onBlock.matRect(seat).centreY() > onBlock.surface().height() / 2.0)
                .orElse(false);
    }

    /** Whichever board is being played, which is the only thing the two views differ on. */
    // Package-private rather than private so the scripted client harness can aim at the same
    // rectangles the screen draws. Replicating the geometry in the harness instead would be a
    // second copy of the layout rules, free to drift from this one - which is the failure this
    // whole project keeps having.
    BoardPlacement board() {
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
            TableCameraView.focusOn(table, myMatIsOnTheSouthHalf(), myMatOnTheBlock(),
                    coveredByTheStatus(), coveredByTheHand());
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
        piles = Zone.pilesFor(
                net.minecraft.client.Minecraft.getInstance().level != null
                        && net.minecraft.client.Minecraft.getInstance().level
                                .getBlockEntity(table) instanceof TableBlockEntity entity
                        && entity.hasCommandZone());
        // The game ended, or this client stopped being told about it.
        if (view().isEmpty()) {
            this.onClose();
            return;
        }
        if (geometry.surface().seatCount() != view().get().seats().size()) {
            geometry.reshape(anchors(), this.width, this.height,
                    layout().status().height(), layout().hand().height());
            onBlock.reshape(anchors());
            // Framed the same way opening it frames it: on your own mat if you have one. It
            // used to show the whole table, so somebody else sitting down pulled your camera
            // off your own board and out to a view of everybody's - in the middle of your
            // turn, without you touching anything.
            frameTheBoard(mySeat().orElse(null));
        }
        // The board can arrive before this client knows which chair it is in - a spectator who
        // sits down gets the seat a moment after the view - and a player can stop having a
        // chair while still looking at the board. Either way the camera has to follow, and it
        // used to follow in one direction only: framing on your own mat when a seat appeared,
        // and then staying framed on a mat that was no longer yours when it went away, with
        // the far player's zones off the top of the window and no way back but Home.
        SeatId sitting = mySeat().orElse(null);
        if (!java.util.Objects.equals(sitting, framedFor)) {
            framedFor = sitting;
            frameTheBoard(sitting);
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
            ClientTableHighlight.set(idOf(hovered), List.copyOf(selected),
                    held == null ? null : held.card());
        } else {
            renderMats(graphics, board);
            renderVerbs(graphics, mouseX, mouseY);
            renderPiles(graphics, board, mouseX, mouseY);

            List<Placed> onTable = everythingOnTheTable(board);
            hovered = frontMostAt(onTable, mouseX, mouseY);
            for (Placed placed : onTable) {
                drawCard(graphics, placed.card(), placed.where(), placed.angle(),
                        placed == hovered || isSelected(placed.card()), true);
            }
            renderPileBadges(graphics, board, onTable);
        }
        hoveringSomething = hovered != null;
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
            boolean taken = seat.occupant().isPresent();
            // A free chair keeps its outline and loses everything else. Drawing nothing at all
            // for it leaves a hole in the felt that reads as a fault; drawing it as though
            // somebody were there says a game is going on that is not. An outline says what is
            // true: a board goes here, and nobody has put one down yet.
            if (taken) {
                boolean mine = me != null && me.equals(seat.seat());
                graphics.fill(mat.x(), mat.y(), mat.right(), mat.bottom(), mine ? MAT_MINE : MAT);
            }
            // The seat's own colour, which is how four identical rectangles become four
            // boards. Brighter for whoever's turn it is, faint for a chair nobody is in.
            graphics.renderOutline(mat.x(), mat.y(), mat.width(), mat.height(),
                    SeatColour.at(seat.seat().index(), !taken ? FREE_SEAT_EDGE
                            : seat.seat().equals(board.turn().activeSeat()) ? 0xFF : 0xAA));
            if (!taken) {
                continue;
            }

            // And the line marking off the row nearest its player, which is where lands go.
            Rect divider = board().matDividerRect(seat.seat(), pileCount());
            if (!divider.isEmpty()) {
                graphics.fill(divider.x(), divider.y(), divider.right(), divider.bottom(),
                        ZONE_BORDER);
            }
        }
    }

    /**
     * Everybody's zones, in a column down the outer edge of their own mat.
     *
     * <p>Two boxes rather than four loose slots: the three a hand is in and out of all game
     * grouped together, and the command zone on its own past a gap. That is how the tables
     * people already play on are marked out, and it is what makes a glance at somebody's
     * board answer "where is their graveyard" without counting.
     */
    private void renderPiles(GuiGraphics graphics, GameView board, int mouseX, int mouseY) {
        int count = pileCount();
        for (SeatView seat : board.seats()) {
            if (seat.occupant().isEmpty()) {
                continue;
            }
            drawPileGroup(graphics, board().pileGroupRect(seat.seat(), 0, IN_HAND_REACH, count));
            if (count > Zone.PILES_WITHOUT_A_COMMAND_ZONE) {
                drawPileGroup(graphics,
                        board().pileGroupRect(seat.seat(), count - 1, count - 1, count));
            }
            for (int index = 0; index < count; index++) {
                Rect where = board().pileRect(seat.seat(), index, count);
                if (where.isEmpty() || where.width() < 4) {
                    continue;
                }
                drawPile(graphics, seat, Zone.PILES.get(index), where,
                        where.contains(mouseX, mouseY));
            }
        }
    }

    /** The last zone in the group a hand actually reaches for: exile. */
    private static final int IN_HAND_REACH = Zone.PILES_WITHOUT_A_COMMAND_ZONE - 1;

    /**
     * The buttons printed on each seated player's own mat.
     *
     * <p>Only on their own mat: pressing somebody else's untap button is not a thing anybody
     * does at a table, and drawing four boxes on every mat that only work on one of them
     * would be four lies per opponent.
     */
    private void renderVerbs(GuiGraphics graphics, int mouseX, int mouseY) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return;
        }
        int count = TableVerb.count();
        Rect group = board().verbGroupRect(me, count);
        if (group.isEmpty() || group.width() < 6) {
            return;
        }
        graphics.renderOutline(group.x(), group.y(), group.width(), group.height(), ZONE_BORDER);
        for (int index = 0; index < count; index++) {
            Rect where = board().verbRect(me, index, count);
            if (where.isEmpty() || where.width() < 8) {
                continue;
            }
            TableVerb verb = TableVerb.values()[index];
            boolean hovered = where.contains(mouseX, mouseY);
            GatheringSprites.inset(graphics, where.x(), where.y(), where.width(), where.height());
            if (hovered) {
                GatheringSprites.highlight(
                        graphics, where.x(), where.y(), where.width(), where.height());
                graphics.renderOutline(
                        where.x(), where.y(), where.width(), where.height(), ACCENT);
            }
            GuiText.drawCentred(graphics, this.font, Component.translatable(verb.key()),
                    (int) where.centreX(), (int) where.centreY() - this.font.lineHeight / 2,
                    where.width() - 2, hovered ? LABEL : ZONE_LABEL);
        }
    }

    /** Runs the verb whose button is under this point, if one is. */
    private boolean pressVerb(SeatId me, int x, int y) {
        int count = TableVerb.count();
        for (int index = 0; index < count; index++) {
            Rect where = board().verbRect(me, index, count);
            if (where.isEmpty() || !where.contains(x, y)) {
                continue;
            }
            GatheringButtons.clickSound();
            switch (TableVerb.values()[index]) {
                case UNTAP -> send(new GameEvent.SeatUntappedAll(me, me));
                case DRAW -> send(new GameEvent.CardsDrawn(me, me, 1));
                case SHUFFLE -> send(new GameEvent.LibraryShuffled(me, me));
                case MULLIGAN -> send(new GameEvent.Mulliganed(me, me, MULLIGAN_HAND));
            }
            return true;
        }
        return false;
    }

    /** The line round a group of zones. Nothing inside it - the slots draw themselves. */
    private void drawPileGroup(GuiGraphics graphics, Rect group) {
        if (group.isEmpty() || group.width() < 6) {
            return;
        }
        graphics.renderOutline(group.x(), group.y(), group.width(), group.height(), ZONE_BORDER);
    }

    /**
     * How many zones this table's column holds.
     *
     * <p>Three, or four where the format has a command zone. Read once a tick rather than per
     * call: it comes off the block entity, every loop over the column asks, and the answer
     * changes twice a match.
     */
    private int pileCount() {
        return piles;
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
        if (held != null && held.fromPile() == zone && held.from().equals(view.seat())) {
            // The one in the air is out of the pile as far as anybody looking is concerned.
            count = Math.max(0, count - 1);
        }

        // The slot itself, and then whatever is sitting in it. No frame round the card: an
        // empty zone is a recess and a full one is the card, the same as everywhere else.
        Rect art = pile;
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

        // Name on the felt beside the slot, count in the slot's own corner. Four unlabelled
        // boxes stacked in a column are only readable by somebody who already knows the order
        // they come in, and the order is the one thing a player coming from a physical table
        // has no reason to know.
        Rect named = board().pileLabelRect(view.seat(), Zone.PILES.indexOf(zone), pileCount());
        if (!named.isEmpty() && everyZoneNameFits(named.width())) {
            GuiText.drawCentred(graphics, this.font, ZoneText.name(zone),
                    (int) named.centreX(),
                    (int) named.centreY() - this.font.lineHeight / 2,
                    named.width(), ZONE_LABEL);
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

    /**
     * Whether there is room for the name of every zone in the column, not just this one.
     *
     * <p>Asked of the whole column because the answer is about the column. "Exile" is four
     * letters shorter than "Graveyard", so asking each name for itself named exactly one zone
     * in four on a board drawn small - which reads as that zone being special rather than as
     * a board too small to write on.
     */
    private boolean everyZoneNameFits(int room) {
        for (Zone zone : Zone.PILES) {
            if (!GuiText.fits(this.font, ZoneText.name(zone), room)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The card showing on top of a pile, which is not the one currently in the air.
     *
     * <p>A card lifted off a graveyard has not moved yet - the server has not been told, and
     * will not be until it lands - so the pile still lists it. Drawing it anyway leaves a copy
     * of the card sitting in the zone while its twin follows the cursor, which reads as the
     * drag having failed.
     */
    private Optional<CardView> topOf(ZoneView zone) {
        if (zone == null) {
            return Optional.empty();
        }
        for (CardView card : zone.cards()) {
            if (!isHeld(card)) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
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
            Map<CardInstanceId, List<CardView>> attachments = attachmentsBy(cards);

            for (int index = 0; index < cards.size(); index++) {
                CardView card = cards.get(index);
                if (isHeld(card) || card.host().isPresent()) {
                    continue;
                }
                Rect where = spotOf(seat.seat(), card, depths.get(index));
                placed.add(new Placed(seat.seat(), card, where, angleOf(seat.seat(), card)));

                List<CardView> attached = attachmentsOf(attachments, card);
                if (attached.isEmpty()) {
                    continue;
                }
                boolean left = TableAttachments.fansLeft(where, new Rect(0, 0, this.width, this.height));
                for (int slot = 0; slot < attached.size(); slot++) {
                    if (isHeld(attached.get(slot))) {
                        continue;
                    }
                    Rect at = left
                            ? TableAttachments.slot(where, slot)
                            : TableAttachments.slotOnTheRight(where, slot);
                    placed.add(new Placed(seat.seat(), attached.get(slot), at,
                            angleOf(seat.seat(), attached.get(slot))));
                }
            }
        }
        return placed;
    }

    /**
     * Everything on the battlefield that is sitting on something else, grouped by what it is
     * sitting on.
     *
     * <p>One pass rather than one scan per card. This was a search of the whole battlefield
     * for every permanent on it, run on every frame, which on a board with sixty permanents is
     * three and a half thousand comparisons and sixty throwaway lists a frame to find the
     * handful of auras anybody actually has out.
     */
    private static Map<CardInstanceId, List<CardView>> attachmentsBy(List<CardView> all) {
        Map<CardInstanceId, List<CardView>> byHost = null;
        for (CardView card : all) {
            CardInstanceId host = card.host().orElse(null);
            if (host == null) {
                continue;
            }
            if (byHost == null) {
                byHost = new java.util.HashMap<>();
            }
            byHost.computeIfAbsent(host, ignored -> new ArrayList<>()).add(card);
        }
        return byHost == null ? Map.of() : byHost;
    }

    /** Everything currently sitting on this card, in the board's own order. */
    private static List<CardView> attachmentsOf(
            Map<CardInstanceId, List<CardView>> byHost, CardView host) {
        if (byHost.isEmpty() || !(host instanceof CardView.Visible visible)) {
            return List.of();
        }
        return byHost.getOrDefault(visible.id(), List.of());
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
    private int angleOf(SeatId seat, CardView card) {
        int resting = card.placedAt().map(TablePosition::rotation).orElse(0);
        int tapped = card.tapped() ? resting + TablePosition.QUARTER_TURN : resting;
        // Turned with the board, exactly as the table in the world turns it: a card lying in
        // front of its owner reads the right way up to them and upside down from the chair
        // opposite, which is what a card on a table between two people does. The view itself
        // is turned for the player at the far edge, so their own cards come back upright.
        return tapped + board().facingDegrees(seat);
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
                drawCard(graphics, hand.get(index), slot.where(), slot.angle(), false, false);
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
            // A chair nobody is in says so and stops. Forty life, no cards and no deck are
            // all true of a player who does not exist, and printing them makes an empty seat
            // look like somebody who is losing badly.
            Component text = seat.occupant()
                    .<Component>map(player -> Component.translatable(
                            "screen.gathering.table.mat_line", player.name(), seat.life(),
                            count(seat, Zone.HAND), count(seat, Zone.LIBRARY)))
                    .orElseGet(() -> Component.translatable("screen.gathering.table.free_seat"));
            if (seat.occupant().isPresent() && !seat.counters().isEmpty()) {
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
        int line = this.font.lineHeight + 1;
        int margin = Math.max(4, this.width / 24);
        int top = layout().status().bottom() + 4;
        // Below the life totals and inside the margin, the same as the key list. It used to
        // start at the top of the window, which covered the one row telling you how much life
        // everybody has with a record of how they got there.
        Rect area = new Rect(this.width / 3, top,
                this.width - this.width / 3 - margin, layout().hand().y() - top - 6);
        if (area.height() < line * 3) {
            return;
        }
        GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());

        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.log_title"),
                area.x() + 5, area.y() + 4, area.width() - 10, ACCENT);
        int first = area.y() + 4 + line + 2;
        int last = area.bottom() - line - 4;

        List<LogEntry> log = board.log();
        int lines = Math.max(1, (last - first) / line + 1);
        int from = Math.max(0, log.size() - lines);
        int y = first;
        for (int index = from; index < log.size(); index++) {
            GuiText.draw(graphics, this.font, GameLogText.render(board, log.get(index)),
                    area.x() + 5, y, area.width() - 10, LABEL);
            y += line;
        }
        if (log.isEmpty()) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.log_empty"),
                    area.x() + area.width() / 2, (first + last) / 2, area.width() - 10, DIM);
        }
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.log_close"),
                area.x() + 5, area.bottom() - line - 2, area.width() - 10, DIM);
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
        int line = this.font.lineHeight + 1;
        int margin = Math.max(4, this.width / 24);
        int top = layout().status().bottom() + 4;
        int available = layout().hand().y() - top - 6;
        // Room for a heading, two lines under it and the close hint, or there is no point.
        // This used to floor `available` at forty and then test it against four lines, which
        // with the vanilla font is exactly forty - so the guard could never fire and a
        // forty-pixel panel drew twenty-two lines of help across the board.
        if (available < line * 4) {
            return;
        }

        // Columns sized to the text rather than by dividing the panel up. Dividing it made
        // every line that did not fit shrink to suit, so the list came out in three different
        // sizes - which is exactly the sort of thing that reads as unfinished however correct
        // it is. Measured, the panel is as wide as it needs to be and nothing shrinks.
        // How many lines a column really holds, measured the same way the draw loop stops:
        // from the first line to `bottom`. Estimating it from the panel height instead
        // over-counted, and the last column went on drawing past the close hint and out of
        // the panel.
        int firstLine = top + line + 2;
        int lastLine = top + available - line - 6;
        int perColumn = Math.max(1, (lastLine - firstLine) / line + 1);
        int columns = Math.max(1, (linesOfKeyHelp() + perColumn - 1) / perColumn);
        int columnWidth = widestKeyLine() + 14;
        int wanted = Math.min(this.width - margin * 2, columns * columnWidth + 10);
        columnWidth = Math.max(40, (wanted - 10) / columns);

        Rect area = new Rect((this.width - wanted) / 2, top - 4, wanted, available + 4);
        GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());

        top = area.y() + 4;
        int bottom = area.bottom() - line - 4;

        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.keys_title"),
                area.x() + 5, top, area.width() - 10, ACCENT);
        top += line + 2;

        int column = 0;
        int y = top;
        for (String[] section : KEY_HELP) {
            // A heading with nothing under it is worse than a heading in the next column.
            if (y + line * 2 > bottom && column + 1 < columns) {
                column++;
                y = top;
            }
            int x = area.x() + 5 + column * columnWidth;
            GuiText.draw(graphics, this.font, Component.translatable(section[0]),
                    x, y, columnWidth - 6, ACCENT);
            y += line;
            for (int index = 1; index < section.length; index++) {
                if (y + line > bottom && column + 1 < columns) {
                    column++;
                    y = top;
                    x = area.x() + 5 + column * columnWidth;
                }
                GuiText.draw(graphics, this.font, Component.translatable(section[index]),
                        x + 6, y, columnWidth - 12, LABEL);
                y += line;
            }
            y += 2;
        }
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.keys_close"),
                area.x() + 5, area.bottom() - line - 2, area.width() - 10, DIM);
    }

    /** How many lines the whole key list wants, headings included. */
    private static int linesOfKeyHelp() {
        int lines = 0;
        for (String[] section : KEY_HELP) {
            lines += section.length;
        }
        return lines;
    }

    /** The longest line in the key list, so a column can be built to hold it whole. */
    private int widestKeyLine() {
        int widest = 0;
        for (String[] section : KEY_HELP) {
            for (String key : section) {
                widest = Math.max(widest, this.font.width(Component.translatable(key)));
            }
        }
        return widest;
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
            ClientTableHighlight.landingOn(null);
            return;
        }
        CardView card = findCard(board, held.card()).orElse(null);
        if (card == null) {
            return;
        }
        double[] at = pointer(mouseX, mouseY);
        SeatId landing = at == null ? null : board().seatAt(at[0], at[1]);

        if (landing != null && at != null) {
            ClientTableHighlight.aimAt(landing, board().pileAt(landing, pileCount(), at[0], at[1]));
        } else {
            ClientTableHighlight.aimAt(null, -1);
        }
        // Whose side of the table it would land on, which the board on the block draws as a
        // lit mat. Most of a mat is not a zone, so aiming alone left a card being dragged
        // across the felt with nothing at all saying where it was about to go.
        ClientTableHighlight.landingOn(landing);

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
                ? centredOnCursor(mouseX, mouseY, sizedFor)
                : centred(mouseX - held.grabX(), mouseY - held.grabY(),
                        board().cardWidth(sizedFor), board().cardHeight(sizedFor));
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, LIFT);
        drawCard(graphics, card, airborne, 0, false, false);
        graphics.pose().popPose();
    }

    private static Rect centred(int middleX, int middleY, int width, int height) {
        return new Rect(middleX - width / 2, middleY - height / 2, width, height);
    }

    /** A hand-sized card taken by the middle, for the view where the board is not on screen. */
    /**
     * The card in the air over the board on the block, at the size a card is on that board.
     *
     * <p>It used to be drawn the height of a card in your hand, which on the block is roughly
     * two and a half times the size of a card lying on the table - so picking one up made it
     * balloon, and it covered the very place it was about to be put down.
     */
    private Rect centredOnCursor(int mouseX, int mouseY, SeatId sizedFor) {
        double blocks = board().surface().cardHeightOn(sizedFor.index())
                / (double) TableSurface.SPAN * TableTop.SPAN_BLOCKS;
        int height = Math.max(24, (int) Math.round(blocks * PIXELS_PER_BLOCK));
        int width = Math.max(16, CardShape.widthFor(height));
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

        // Before the piles and before the felt: a button is a small target on top of a mat,
        // and anything that answered first would swallow it.
        SeatId mine = mySeat().orElse(null);
        if (button == 0 && mine != null && !playingOnTheBlock && pressVerb(mine, x, y)) {
            return true;
        }

        SeatId pileSeat = pileSeatAt(board, x, y);
        if (pileSeat != null) {
            return pressPile(board, pileSeat, pileZoneAt(board, x, y), x, y, button);
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
        return grab(card, seat, fromHand, null, where, x, y);
    }

    private Held grab(
            CardInstanceId card, SeatId seat, boolean fromHand, Zone fromPile,
            Rect where, int x, int y) {
        double[] at = fromHand ? null : pointer(x, y);
        if (at == null) {
            // Straight from the hand, or from somewhere the table cannot answer for: the card
            // takes the cursor by the middle, which is where you would expect to be holding it.
            return new Held(card, seat, fromHand, fromPile, 0, 0, x, y);
        }
        return new Held(card, seat, fromHand, fromPile,
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

        // A press on a pile that never moved was a click on the pile after all.
        if (dropped.fromPile() != null && !dropped.hasMoved(x, y)) {
            if (clickPile(me, dropped.from(), dropped.fromPile())) {
                GatheringButtons.clickSound();
            }
            return true;
        }

        // A press that never moved is a click, and a click on a card already on the table taps
        // it - the most common thing anyone does, and the one gesture worth the plain left click.
        if (!dropped.fromHand() && dropped.fromPile() == null && !dropped.hasMoved(x, y)) {
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
        int zone = board().pileAt(landing, pileCount(), at[0], at[1]);
        if (zone >= 0) {
            send(new GameEvent.CardMoved(me, dropped.card(),
                    ZoneRef.of(landing, Zone.PILES.get(zone)), Placement.TOP));
            selected.clear();
            return true;
        }

        TablePosition where = board().positionOn(
                landing, at[0] - dropped.grabX(), at[1] - dropped.grabY());
        if (!dropped.fromHand() && dropped.fromPile() == null && selected.contains(dropped.card())) {
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
        return slot < 0 ? null : board.seats().get(slot / pileCount()).seat();
    }

    private Zone pileZoneAt(GameView board, int x, int y) {
        int slot = pileSlotAt(board, x, y);
        return slot < 0 ? Zone.LIBRARY : Zone.PILES.get(slot % pileCount());
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
            for (int index = 0; index < pileCount(); index++) {
                Rect pile = board().pileRect(board.seats().get(seat).seat(), index, pileCount());
                if (pile.contains((int) Math.round(at[0]), (int) Math.round(at[1]))) {
                    return seat * pileCount() + index;
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
            // Everything above happens to the card where it lies; everything below sends it
            // somewhere else. Two groups a player can see without reading either of them.
            entries.add(ContextMenu.Entry.rule());
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
        entries.add(ContextMenu.Entry.rule());
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
     * Pressing one of the piles.
     *
     * <p>Left does the obvious thing and right offers the rest, which is the same bargain as
     * everywhere else on this screen. The obvious thing for a library is to draw a card,
     * because that is what a library is for; for every other pile it is to open it, because a
     * pile you cannot look through is a number.
     *
     * <p>But a left press might also be the start of a drag. A zone that only ever swallowed
     * cards was half a zone: putting something in the graveyard was a drag and getting it back
     * out was a screen and two clicks, for a thing that on a real table is picking it up. So a
     * press on a pile whose top card this player can see picks that card up, and the release
     * decides which gesture it was.
     */
    private boolean pressPile(GameView board, SeatId owner, Zone pile, int x, int y, int button) {
        SeatId me = mySeat().orElse(null);
        // Anybody may open anybody's graveyard - it is public - but the verbs that only make
        // sense on your own library are offered only there, because the mod refuses a search
        // of somebody else's anyway and a menu full of refusals is worse than a short one.
        if (button == 1 && owner.equals(me)) {
            GatheringButtons.clickSound();
            openPileMenu(me, pile, x, y);
            return true;
        }
        if (button == 0 && me != null) {
            // Somebody with no seat cannot move anything, so a drag that starts here would be
            // a card following their cursor and snapping back when they let go. They get the
            // click, which opens the pile - watching a graveyard is a thing a spectator does.
            CardInstanceId top = liftableFrom(board, owner, pile);
            if (top != null) {
                held = grab(top, owner, false, pile, pileSlotOf(owner, pile), x, y);
                return true;
            }
        }
        // The sound is the answer to "did that do anything", so it is only made when the
        // answer is yes. Clicking a face-down library belonging to somebody else has nothing
        // it could mean, and a click that sounds like it worked is worse than a quiet one.
        if (clickPile(me, owner, pile)) {
            GatheringButtons.clickSound();
        }
        return true;
    }

    /**
     * What a press on a pile does when it turns out to have been a click, and whether that
     * was anything at all.
     *
     * <p>Opening a public pile asks nothing of the viewer's seat: a graveyard is public
     * information, and somebody watching a game is exactly the person who wants to read one.
     * What is actually in it is still the server's decision - the screen shows what this
     * client was sent and says so when that is nothing.
     */
    private boolean clickPile(SeatId me, SeatId owner, Zone pile) {
        if (pile != Zone.LIBRARY) {
            openPile(owner, pile, false);
            return true;
        }
        if (owner.equals(me)) {
            send(new GameEvent.CardsDrawn(me, me, 1));
            return true;
        }
        return false;
    }

    /**
     * The card a press on this pile would pick up, or null.
     *
     * <p>Only a card this client has actually been sent. The top of a library is face down and
     * has no identity here - which is the visibility rule doing its job, and the reason a
     * library stays a click that draws rather than a card you can lift off.
     */
    private CardInstanceId liftableFrom(GameView board, SeatId owner, Zone pile) {
        ZoneView contents = board.seat(owner).zone(pile);
        if (contents == null) {
            return null;
        }
        for (CardView card : contents.cards()) {
            if (card instanceof CardView.Visible visible && !isHeld(card)) {
                return visible.id();
            }
        }
        return null;
    }

    /** Where a pile is on screen, for a card being lifted off it to know where it started. */
    private Rect pileSlotOf(SeatId owner, Zone pile) {
        int index = Zone.PILES.indexOf(pile);
        return index < 0 ? Rect.NONE : board().pileRect(owner, index, pileCount());
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
                decideOnLibrary(me, PileScreen.Decision.SCRY);
            })));
            entries.add(entry("surveil", () -> ask("surveil", 1, count -> {
                send(new GameEvent.LibraryLooked(me, me, count));
                decideOnLibrary(me, PileScreen.Decision.SURVEIL);
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
                        count -> ClientNetworking.send(new CreateTokenPayload(table, name, count)),
                        this)),
                this));
    }

    /**
     * Asks before doing something that cannot be taken back, then does it.
     *
     * <p>The question, what it means and the word on the button all come off one key, so a
     * verb that needs asking about cannot end up asking half a question.
     */
    private void confirm(String key, Runnable action) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new ConfirmScreen(
                Component.translatable("confirm.gathering." + key),
                Component.translatable("confirm.gathering." + key + ".detail"),
                Component.translatable("confirm.gathering." + key + ".yes"),
                action, this));
    }

    /** Asks how many, then does it. The answer arrives after the table is back. */
    private void ask(String key, int suggested, java.util.function.IntConsumer action) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new AmountScreen(
                Component.translatable("screen.gathering.amount." + key), suggested, action, this));
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

    /**
     * Opens the top of your own library with a decision attached.
     *
     * <p>Looking is half of a scry. Without the other half the cards are revealed and then
     * left exactly where they were, which is a scry that did nothing.
     */
    private void decideOnLibrary(SeatId me, PileScreen.Decision decision) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new PileScreen(table, me, Zone.LIBRARY, true, decision, this));
    }

    private void openPile(SeatId owner, Zone pile, boolean opensALibrary) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new PileScreen(table, owner, pile, opensALibrary, this));
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
        entries.add(ContextMenu.Entry.rule());
        // The one verb that ends a game. Everything else the table does is a move somebody can
        // make again; this one settles the match, records the score and takes the board away,
        // so it asks first. Without it a game could be started and never finished.
        entries.add(entry("concede", () -> confirm(
                "concede",
                () -> send(new GameEvent.Conceded(me)))));
        // Standing up lives here rather than on the table block. Clicking your own edge of
        // the table is how a seated player opens their board, so it cannot also be how they
        // give up their chair - and a verb about your seat belongs with the other verbs about
        // your seat, where somebody can find it without being told.
        entries.add(entry("leave_table", () -> {
            send(new GameEvent.SeatReleased(me));
            onClose();
        }));
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
                .setScreen(new CountersScreen(table, subject, this));
    }

    /** What to call a card on a screen that has to name what it is about to change. */
    private Component nameOf(CardView card) {
        return summaryOf(card)
                .map(summary -> Component.literal(summary.name()))
                .map(Component.class::cast)
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
    }

    private static ContextMenu.Entry entry(String key, Runnable action) {
        Component shortcut = SHORTCUTS.get(key);
        Component label = Component.translatable("menu.gathering.table." + key);
        return shortcut == null
                ? ContextMenu.Entry.of(label, action)
                : ContextMenu.Entry.of(label, shortcut, action);
    }

    /**
     * The key that does the same thing as each menu entry, for the menu to say so.
     *
     * <p>Keyed off the same name the entry is, so an entry and its key cannot drift apart, and
     * an entry with no key simply has none here. This is the only place a player is looking
     * straight at a verb, so it is the only place worth telling them there is a faster way.
     */
    private static final java.util.Map<String, Component> SHORTCUTS = java.util.Map.ofEntries(
            java.util.Map.entry("untap_all", Component.literal("1")),
            java.util.Map.entry("draw", Component.literal("2")),
            java.util.Map.entry("scry", Component.literal("3")),
            java.util.Map.entry("mill", Component.literal("4")),
            java.util.Map.entry("reveal", Component.literal("5")),
            java.util.Map.entry("surveil", Component.literal("6")),
            java.util.Map.entry("to_graveyard", Component.literal("7")),
            java.util.Map.entry("to_exile", Component.literal("8")),
            java.util.Map.entry("to_library_bottom", Component.literal("9")),
            java.util.Map.entry("pass_turn", Component.literal("0")),
            java.util.Map.entry("shuffle", Component.literal("R")),
            java.util.Map.entry("turn_face_down", Component.literal("F")),
            java.util.Map.entry("turn_face_up", Component.literal("F")),
            java.util.Map.entry("untap", Component.literal("Q")),
            java.util.Map.entry("tap", Component.literal("E")),
            java.util.Map.entry("show_log", Component.literal("L")),
            java.util.Map.entry("hide_log", Component.literal("L")));

    // ------------------------------------------------------------ hit-testing


    /**
     * The keys, matched to Tabletop Simulator's defaults.
     *
     * <p>Anybody arriving at this table has played on that one, and a key that does something
     * else here is a key they will press by accident all evening. So F flips, Q and E turn,
     * G groups, R is the deck verb and Alt reads a card - Tabletop Simulator's own object
     * keys - while the number row carries the nine verbs the Magic table binds it to: untap,
     * draw, scry, mill, reveal, and the three that send a card to a zone.
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

        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_0 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            return verbKey(me, key - org.lwjgl.glfw.GLFW.GLFW_KEY_0);
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

    /**
     * The number row: one press does one thing to the game, or to the card being pointed at.
     *
     * <p>The same nine verbs the reference table binds them to, because they are the nine
     * things a game of Magic asks for over and over and because somebody arriving from that
     * table already knows them. It used to be "draw that many cards", which spent the whole
     * row on one verb and left the other eight behind a right-click and a menu.
     *
     * <p>Drawing a named number of cards is still on the library's menu, where a thing done
     * once a game belongs.
     */
    private boolean verbKey(SeatId me, int number) {
        return switch (number) {
            case 0 -> {
                view().ifPresent(board -> passTurn(board, me));
                yield true;
            }
            case 1 -> {
                send(new GameEvent.SeatUntappedAll(me, me));
                yield true;
            }
            case 2 -> {
                send(new GameEvent.CardsDrawn(me, me, 1));
                yield true;
            }
            case 3 -> {
                send(new GameEvent.LibraryLooked(me, me, 1));
                decideOnLibrary(me, PileScreen.Decision.SCRY);
                yield true;
            }
            case 4 -> {
                send(new GameEvent.LibraryMilled(me, me, 1));
                yield true;
            }
            case 5 -> {
                send(new GameEvent.LibraryRevealed(me, me, 1));
                yield true;
            }
            case 6 -> {
                send(new GameEvent.LibraryLooked(me, me, 1));
                decideOnLibrary(me, PileScreen.Decision.SURVEIL);
                yield true;
            }
            case 7 -> sendUnderCursorTo(me, Zone.GRAVEYARD, Placement.TOP);
            case 8 -> sendUnderCursorTo(me, Zone.EXILE, Placement.TOP);
            case 9 -> sendUnderCursorTo(me, Zone.LIBRARY, Placement.BOTTOM);
            default -> false;
        };
    }

    /**
     * Sends whatever the keys are pointing at to one of its owner's zones.
     *
     * <p>Its owner's, not the presser's: a creature somebody stole dies to its own owner's
     * graveyard, which is the rule the card menu already follows and the reason this asks the
     * card who owns it rather than assuming.
     */
    private boolean sendUnderCursorTo(SeatId me, Zone zone, Placement placement) {
        GameView board = view().orElse(null);
        if (board == null) {
            return false;
        }
        List<CardInstanceId> targets = underCursorOrSelected();
        eachCard(board, targets, seen -> new GameEvent.CardMoved(
                me, seen.id(), ZoneRef.of(seen.owner(), zone), placement));
        return !targets.isEmpty();
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

    /**
     * A card: its picture, turned to its own angle, and nothing drawn round it.
     *
     * <p>No border, anywhere. A card is a picture before it is a token, and a frame drawn
     * round every one of them turns a hand into a row of lines with slivers of art between,
     * and a pile into a stack of boxes. The art has its own black border printed on it - it
     * is a card - so a second one is somebody else's idea of a card drawn over the real one.
     *
     * <p>What is left is feedback rather than decoration: a shadow under a card lying on the
     * felt, so a stack reads as a stack; a tint on a tapped one; a ring on the one under the
     * cursor.
     *
     * @param onTheFelt whether this is a card lying on the table, which is what earns it a
     *     shadow and a tapped tint - a card in a hand or in a list has neither
     */
    private void drawCard(
            GuiGraphics graphics, CardView card, Rect where, int angle,
            boolean hovered, boolean onTheFelt) {
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
        if (onTheFelt) {
            // Cast first, under everything, so the card above reads as being above.
            graphics.fill(where.x() + SHADOW_OFFSET, where.y() + SHADOW_OFFSET,
                    where.right() + SHADOW_OFFSET, where.bottom() + SHADOW_OFFSET, SHADOW);
        }
        if (card.isFaceDown()) {
            // Even to the player who knows what it is. Their board has to look to them the
            // way it looks to everyone else, or they cannot tell what they have given away.
            graphics.blit(CardFaceRenderer.CARD_BACK, where.x(), where.y(), 0f, 0f,
                    where.width(), where.height(), where.width(), where.height());
        } else {
            summaryOf(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, where.x(), where.y(), where.width(), where.height()),
                    () -> GatheringSprites.inset(
                            graphics, where.x(), where.y(), where.width(), where.height()));
        }
        if (onTheFelt && card.tapped()) {
            // A tapped card is already lying sideways; the tint is what tells it apart from
            // one somebody turned by hand, without a word of text over the art.
            graphics.fill(where.x(), where.y(), where.right(), where.bottom(), TAPPED_TINT);
        }
        drawCounters(graphics, card, where);
        if (hovered) {
            graphics.renderOutline(where.x(), where.y(), where.width(), where.height(), ACCENT);
        }
        if (turned) {
            graphics.pose().popPose();
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
