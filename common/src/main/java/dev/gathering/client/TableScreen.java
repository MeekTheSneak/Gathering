package dev.gathering.client;

import com.mojang.math.Axis;
import dev.gathering.core.game.CardInstance;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.CommandSlots;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.Placement;
import dev.gathering.SeatNames;
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
import dev.gathering.core.ui.Legibility;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.Shaking;
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
import dev.gathering.network.DiscardAtRandomPayload;
import dev.gathering.network.ToBottomAtRandomPayload;
import dev.gathering.network.RevealUntilPayload;
import dev.gathering.network.UndoPayload;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

    /** The pot's own colour: the stakes gold the consent screen uses, not a board accent. */
    private static final int POT_LABEL = 0xFFE0B15A;
    private static final int TAPPED_TINT = 0x60000000;
    private static final int GHOST_TINT = 0x50000000;

    /** What a tax band is written on, which is card art rather than the empty felt. */
    private static final int TAX_BACKING = 0xB0000000;

    /** What a life total is written on, so it reads against the table rather than into it. */
    private static final int LIFE_BACKING = 0xA0101418;

    /** What the hand strip wrote in itself last frame, when it wrote instead of dealing. */
    private String handSaid = "";

    /** How many seats had a zone column drawn for them last frame. */
    private int boardsDrawn;

    /** What the log panel wrote the last time it drew, so a check can read it back. */
    private String logSaid = "";

    /** The tax band under the cursor: darker still, so a button looks like one. */
    private static final int TAX_LIT = 0xD0000000;
    private static final int COUNTER_TEXT = 0xFFFFE9A8;

    /**
     * The band a counter is written on.
     *
     * <p>Darker than the tint used elsewhere, because this one lands wherever a card's rules
     * box happens to be light and pale text on pale card stock is text nobody reads at a
     * glance - which for the one number saying how big a creature is now is the whole point.
     */
    private static final int COUNTER_BAND = 0xC0000000;

    /** The felt, and a mat on it. Mats are lighter so the table reads as somebody's space. */
    private static final int FELT = 0xFF1E3A2E;
    private static final int MAT = 0x30FFFFFF;

    /** The line round a group of zones: a marking on the mat rather than a piece of interface. */
    private static final int ZONE_BORDER = 0x66FFFFFF;

    /**
     * Behind the zone a card in the air would drop into.
     *
     * <p>Behind rather than over, so whatever is already sitting in the slot stays readable:
     * the question being answered is "which box", and the box is the thing that has to
     * change.
     */
    private static final int AIMED = 0x996FD3E8;

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
     *
     * <p>A contact shadow rather than a drop shadow, which is the difference between a card
     * lying on the felt and a card hovering over it. It used to be the whole card again in
     * black, moved down and right, so what showed was a two-pixel band along two sides -
     * which is what a thing casts when it is some way off the surface, and read as exactly
     * that. What a card resting on a table casts is a hard line right where its edge meets
     * the cloth: one pixel, no daylight under it, and darker to make up for being thin.
     */
    private static final int SHADOW = 0x99000000;

    private static final int SHADOW_OFFSET = 1;

    /**
     * What a card being carried casts on the table underneath it.
     *
     * <p>Softer and wider than the line a resting card casts, because it is further away -
     * and the whole footprint rather than an edge, because this one is doing a second job:
     * it is where the card will come down. A drag that shows nothing until it is let go asks
     * the player to aim at something they cannot see.
     */
    private static final int CAST = 0x59000000;

    /**
     * The line round where a carried card will come down.
     *
     * <p>The same amber the scry box draws its landing bar in, because it is the same
     * sentence: this is where the thing in your hand goes if you let go now.
     */
    private static final int LANDING = 0xBFFFD479;

    /**
     * What is drawn over a card that will not untap.
     *
     * <p>A pale blue wash, and rime caked along the top and bottom edges rather than a ring
     * round the whole card - because a ring is what the cursor draws. Nearly white, so it
     * separates from the cursor's cyan as well as from the warm gold of a written power and
     * toughness: three marks on one card have to be three things or a player reads them as
     * one.
     */
    private static final int FROZEN_TINT = 0x3899D9F2;
    private static final int FROZEN_EDGE = 0xFFE8F7FF;

    /** The badge on a pile, saying how many cards are in it. */
    private static final int PILE_BADGE = 0xE0141210;
    private static final int PILE_TEXT = 0xFFF2EEE6;

    /** The rubber band drawn while picking out several cards at once. */
    private static final int BOX_FILL = 0x206FD3E8;


    /** How far a card is turned by one nudge. Small enough to be a gesture, not a mode. */
    private static final int NUDGE_DEGREES = 15;

    /** An opening hand, for the mulligan the library menu offers. */
    private static final int MULLIGAN_HAND = 7;

    /**
     * The smallest a life counter is drawn at, and the smallest box worth drawing one in.
     *
     * <p>Six-tenths is where the font stops being letters, which is the floor everything else
     * that shrinks in this mod uses. Four pixels is where the box itself stops being a box.
     */
    private static final float SMALLEST_LIFE_SCALE = 0.6f;

    private static final int SMALLEST_LIFE_BOX = 4;

    /** How far the cursor must travel before a press becomes a drag rather than a click. */
    private static final int DRAG_THRESHOLD = 3;

    /**
     * How long a press has to be held still before it means the whole pile, in milliseconds.
     *
     * <p>Long enough that nobody picks up their graveyard by accident on the way to picking
     * up the top card of it, short enough that it is a gesture rather than a wait. Measured
     * against the same monotonic clock the card flights use.
     */
    private static final long LONG_HOLD = 350L;

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
                "screen.gathering.table.key_whole_pile",
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
                "screen.gathering.table.key_draw",
                "screen.gathering.table.key_scry",
                "screen.gathering.table.key_mill",
                "screen.gathering.table.key_reveal",
                "screen.gathering.table.key_surveil",
                "screen.gathering.table.key_to_zones",
                "screen.gathering.table.key_pass",
                "screen.gathering.table.key_shuffle",
                "screen.gathering.table.key_life",
                "screen.gathering.table.key_log",
                // Last, because it is the one that takes you out. Escape closing a screen is
                // the convention and this screen is the table, so escape leaves the table -
                // which is only a surprise if nothing ever said so, and nothing did.
                "screen.gathering.table.key_leave",
            });

    /** How much one wheel notch zooms. A shallow step, because zoom is used constantly. */
    private static final double ZOOM_STEP = 1.18;

    /** How far one press of a pan key slides the table. */
    private static final int PAN_STEP = 60;

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
     * Whether the press currently down ever wandered.
     *
     * <p>Latched, rather than asked of the cursor's position each frame: a drag that happens
     * to pass back over the point it started from is still a drag, and must not turn into a
     * long hold three seconds in because the hand came home.
     */
    private boolean holdStrayed;

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

    /**
     * What the cursor is resting on, if it is something that can say what it is.
     *
     * <p>Collected while the felt is drawn and written out at the very end, because a
     * tooltip drawn where it is discovered would be painted over by the cards on top of it.
     */
    private List<Component> tooltip = List.of();

    /** Measured once per screen: how much room the longest of each set needs. Nought is unasked. */
    private int longestZoneNameWidth;
    private int longestVerbNameWidth;

    /** Frames the board on this seat's own mat, or on the whole table when there is no seat. */
    private void frameTheBoard(SeatId seat) {
        if (seat == null) {
            geometry.showEverything();
        } else {
            geometry.focusOn(seat);
        }
    }

    /**
     * What the hand strip wrote in itself last frame, or empty when it drew cards.
     *
     * <p>For the scripted harness, and recorded rather than worked out again: a strip with
     * nothing drawn in it and a strip with a line in it are the same to anything counting
     * cards, which is exactly why nobody noticed there was no line. Asked of the board state
     * this would answer "the hand is empty" whether or not a word had been drawn.
     */
    String handStripSaid() {
        return handSaid;
    }

    /**
     * How many seats this screen drew a zone column for last frame. For the harness.
     *
     * <p>Counted while drawing rather than worked out from the board, which is what makes it
     * worth asking at all: the run's first attempt at this asked the board how many seats had
     * a board, which is the same question the screen asks, so putting the old condition back
     * left it green while the boards it had stopped drawing sat there missing their zones.
     */
    int boardsDrawn() {
        return boardsDrawn;
    }

    /** Whether the felt reaches this far down the window. For the harness, as below. */
    boolean feltReachesDownTo(int y) {
        return layout().isOnFelt(this.width / 2, y);
    }

    /** Whether a context menu is up. For the scripted harness, which cannot see one. */
    boolean menuIsOpen() {
        return menu != null;
    }

    /** What the last frame's cursor position had to say for itself. For the harness. */
    List<Component> tooltipShowing() {
        return tooltip;
    }

    /**
     * What the last frame made of the card in the air. For the harness, as above.
     *
     * <p>Written from inside the frame because that is the only place the answer exists: the
     * aim is computed while drawing, from the cursor the game passed in, and a harness asking
     * afterwards can only see whether it agreed - not which of the three steps said no.
     */
    String aimReport() {
        return aimReport;
    }

    private String aimReport = "no frame drawn yet";

    /** How many zones this board is drawing per seat. For the harness, as above. */
    int pilesShowing() {
        return pileCount();
    }

    /** Whether a menu is up offering this entry. For the harness, as above. */
    boolean hasMenuEntry(String label) {
        return menu != null && menu.has(label);
    }

    /**
     * Takes the menu entry with this label, if a menu is up and has one. As above.
     *
     * <p>The menu goes away first, exactly as it does when a real click takes an entry - the
     * click path clears it before dispatching so an entry that opens a screen does not leave
     * a menu behind on the board underneath.
     */
    boolean pressMenuEntry(String label) {
        ContextMenu open = menu;
        if (open == null || !open.has(label)) {
            return false;
        }
        menu = null;
        return open.press(label);
    }

    /** Whether a context menu is open at all. For the harness, as above. */
    boolean hasAMenuOpen() {
        return menu != null;
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
            int grabX, int grabY, int pressX, int pressY, long began, boolean whole) {
        // grabX/grabY are in the space the *board* is measured in - pixels on the seated
        // screen, surface units on the block. pressX/pressY stay in pixels, because how far
        // the hand has moved before a press becomes a drag is a question about the mouse.
        //
        // fromPile is the zone the card was lifted off, or null. A press on a pile cannot
        // know yet whether it is a click or the start of a drag, so it becomes a drag either
        // way and the release decides: moved, and the card goes where it was dropped; not
        // moved, and it was a click on the pile after all.
        //
        // card is null only for a pile whose top this client may not name - your own library.
        // There is still something to hold there, because holding a library is how you pick
        // the whole thing up, and the whole thing needs no card named to move.
        //
        // whole is set once the press has been held still long enough to mean the pile or the
        // stack rather than the card off the top of it. began is when the press landed, on
        // the same monotonic clock everything else in the client measures against.

        boolean hasMoved(int mouseX, int mouseY) {
            return Math.abs(mouseX - pressX) >= DRAG_THRESHOLD
                    || Math.abs(mouseY - pressY) >= DRAG_THRESHOLD;
        }

        Held asWhole() {
            return new Held(card, from, fromHand, fromPile, grabX, grabY, pressX, pressY, began, true);
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
        layout = TableScreenLayout.of(this.width, this.height, mySeat().isPresent());
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

    /** This player's own mat on the real table, in surface units, for the camera to frame. */
    private Rect myMatOnTheBlock() {
        // The mat and the life total printed off its far edge. Framed on the mat alone, the
        // counter came out under the status row - the same fault the seated view had, from
        // the same cause, so both ask the surface the same question.
        return mySeat().map(seat -> onBlock.surface().ownBoard(seat.index())).orElse(Rect.NONE);
    }

    /** How much of the bottom of the window the hand is sitting over. */
    private double coveredByTheHand() {
        return this.height <= 0 ? 0 : layout.hand().height() / (double) this.height;
    }

    /** And how much of the top the life totals are. */
    private double coveredByTheStatus() {
        return this.height <= 0 ? 0 : layout.status().height() / (double) this.height;
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
    // Package-private rather than private so the scripted client harness can aim at the same
    // rectangles the screen draws. Replicating the geometry in the harness instead would be a
    // second copy of the layout rules, free to drift from this one - which is the failure this
    // whole project keeps having.
    BoardPlacement board() {
        return playingOnTheBlock ? onBlock : geometry;
    }

    /**
     * What the seated view is framing, for the scripted run to write down beside the block's.
     *
     * <p>The two views are supposed to differ only in whether a point is a pixel or a place
     * on the felt, and the only way to know whether they do is to read the same numbers off
     * both of them at the same moment.
     */
    String framingReport() {
        Rect mine = mySeat().map(seat -> geometry.matRect(seat)).orElse(Rect.NONE);
        return "window=" + this.width + "x" + this.height
                + " status=" + layout.status().height()
                + " hand=" + layout.hand().height()
                + " mat=" + mine
                + " || " + geometry.report();
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
        if (mouseX == askedX && mouseY == askedY) {
            return answered;
        }
        askedX = mouseX;
        askedY = mouseY;
        answered = TablePointer.at(tableTop(), mouseX, mouseY)
                .map(spot -> new double[] {spot.x(), spot.y()})
                .orElse(null);
        return answered;
    }

    /**
     * The last question put to the pointer and the answer it gave.
     *
     * <p>Casting a ray at the table means building a view-projection, inverting it and
     * intersecting a plane, and one frame drawn on the block asks the same question of the
     * same pixel three times over - what card is under the cursor, what button, what pile.
     * The answer cannot change between those three, so it is worked out once.
     *
     * <p>Thrown away at the top of every frame and whenever the view changes, so it can only
     * ever serve the frame that asked for it or a click arriving before the next one - which
     * is the frame the player was looking at when they clicked.
     */
    private double askedX = Double.NaN;
    private double askedY = Double.NaN;
    private double[] answered;

    private void forgetThePointer() {
        askedX = Double.NaN;
        askedY = Double.NaN;
        answered = null;
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
        forgetThePointer();
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
            // The strip along the bottom belongs to a hand, and somebody who has just stood
            // up no longer has one. Laid out again before the camera is framed, because the
            // camera is fitted to what is left of the window after the strip is taken out.
            layout = TableScreenLayout.of(this.width, this.height, sitting != null);
            geometry.reshape(anchors(), this.width, this.height,
                    layout.status().height(), layout.hand().height());
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
        tooltip = List.of();
        handSaid = "";
        forgetThePointer();

        Placed hovered = null;
        if (playingOnTheBlock) {
            // The block draws its own board. What it needs from here is what the cursor is on,
            // because the world renderer has no idea where anybody's mouse is.
            hovered = frontMostAt(everythingOnTheTable(board), mouseX, mouseY);
            ClientTableHighlight.set(idOf(hovered), List.copyOf(selected),
                    held == null ? null : held.card());
            // The mats on the block carry the same buttons and the same piles as the seated
            // board, and until the cursor could light them and name them they were boxes
            // painted on a table - pressable, but only by somebody who already knew.
            SeatId mine = mySeat().orElse(null);
            int verb = hovered == null ? verbSlotAt(mine, mouseX, mouseY) : -1;
            ClientTableHighlight.pointAtVerb(mine, verb);
            if (verb >= 0) {
                tooltip = tipFor(VERB_NAMES[verb], VERB_KEY_NAMES[verb]);
            } else if (hovered == null) {
                List<Component> life = tipForLife(board, mouseX, mouseY);
                if (life != null) {
                    tooltip = life;
                }
                int pile = pileSlotAt(board, mouseX, mouseY);
                if (pile >= 0) {
                    SeatId owner = board.seats().get(pile / pileCount()).seat();
                    Zone zone = Zone.PILES.get(pile % pileCount());
                    List<Component> tax = tipForTax(board, owner, zone, mouseX, mouseY);
                    tooltip = tax == null ? tipForPile(owner, zone) : tax;
                }
            }
        } else {
            renderMats(graphics, board);
            renderVerbs(graphics, mouseX, mouseY);
            renderPiles(graphics, board, mouseX, mouseY);
            // Under the cards in play, over the mats. The pot is on the table rather than in
            // the game, and it should read that way: something lying in the middle that the
            // game goes on on top of.
            renderPot(graphics, mouseX, mouseY);

            List<Placed> onTable = everythingOnTheTable(board);
            hovered = frontMostAt(onTable, mouseX, mouseY);
            long flying = ClientCardFlights.now();
            for (Placed placed : onTable) {
                // Not while it is still crossing. The board that started the flight already
                // has the card at its destination, so drawing it here as well put a copy at
                // the end of the journey the instant it began - the card appearing to
                // teleport, with a ghost of itself trailing behind to where it had already
                // arrived.
                if (idOf(placed) != null
                        && ClientCardFlights.isFlying(table, idOf(placed), flying)) {
                    continue;
                }
                drawCard(graphics, placed.card(), placed.where(), placed.angle(),
                        placed == hovered || isSelected(placed.card()), true);
            }
            renderPileBadges(graphics, board, onTable);
            renderFlights(graphics, board);
            if (hovered == null && tooltip.isEmpty()) {
                List<Component> life = tipForLife(board, mouseX, mouseY);
                if (life != null) {
                    tooltip = life;
                }
            }
        }
        hoveringSomething = hovered != null;
        if (hovered != null) {
            offerToInspector(hovered.card());
            // A note is longer than a card is wide, so the card carries the beginning of it
            // and resting on the card reads the rest. Both views: the board on the block has
            // no room to write on a card at all, and the seated one trims a long note to a
            // word and a half.
            //
            // It takes the tooltip rather than waiting for it. The zone slots claim one from
            // whatever the cursor is inside, before anything knows which card is under it, so
            // a permanent lying across a graveyard was describing the graveyard - and the
            // card is the thing in front.
            hovered.card().writtenOn().ifPresent(written ->
                    tooltip = List.of(Component.literal(written).withStyle(
                            net.minecraft.ChatFormatting.ITALIC)));
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
            return;
        }
        // Nothing is described while something is in the air. A tooltip saying what a click
        // on a library does is an answer to a question nobody is asking any more once the
        // library is being carried - and it is drawn right where the thing being carried is.
        //
        // Nor while the read key is down: that draws the card full size at the cursor, and a
        // tooltip under it is a second answer to a question already being answered better.
        if (!tooltip.isEmpty() && !showingLog && !showingKeys && held == null
                && !CardZoomOverlay.isActive()) {
            // Pushed down far enough that it cannot land on the status bar. Vanilla clamps a
            // tooltip to the window and knows nothing about the one row of this screen that
            // is always there, so a card near the top had its tooltip drawn over the life
            // totals and the turn.
            int tall = tooltip.size() * (this.font.lineHeight + 1) + 8;
            int lowest = layout().status().bottom() + tall + 12;
            graphics.renderComponentTooltip(
                    this.font, tooltip, mouseX, Math.max(mouseY, lowest));
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
            // A chair nobody has sat in keeps its outline and loses everything else. Drawing
            // nothing at all for it leaves a hole in the felt that reads as a fault; drawing
            // it as though somebody were there says a game is going on that is not. An
            // outline says what is true: a board goes here, and nobody has put one down yet.
            //
            // A board, not an occupant. Somebody who walks away mid-game leaves their cards
            // where they were, and a mat still carrying a library and a graveyard is a board
            // - it was drawn as a bare chair with its own battlefield cards floating on it.
            boolean taken = seat.hasABoard();
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
            drawLife(graphics, seat);
        }
    }

    /**
     * A seat's life total, on the table just past the far edge of its own board.
     *
     * <p>The number a game of Magic is played to, and until now it was a word in a strip
     * along the top of the window - the one place on the screen that is not the table. Here
     * it is on the table, where a player looks to read somebody else's, and it is a pair of
     * buttons: press the left of it to take one off and the right of it to put one on.
     *
     * <p>The two halves are marked, faintly until the cursor is on one and brightly when it
     * is, because a number nobody has told you is a button is a number.
     */
    private void drawLife(GuiGraphics graphics, SeatView seat) {
        Rect box = board().lifeRect(seat.seat());
        // Drawn at whatever size there is, down to a hard floor where the box is a smear.
        // It used to stop at a full line of text, so framing the whole table - which is one
        // key - took every life total off the felt and left two mats with no numbers on them
        // anywhere. Everything else printed on a mat is a label and the shapes still read
        // without it; this is the number the game is played to, and there is nothing else on
        // the table that says whose forty is whose.
        if (box.isEmpty() || box.height() < SMALLEST_LIFE_BOX) {
            return;
        }
        float scale = Math.max(SMALLEST_LIFE_SCALE,
                Math.min(1f, (box.height() - 2f) / this.font.lineHeight));
        int way = mySeat().isEmpty() ? 0 : lifeWayUnder(seat.seat(), cursorX, cursorY);
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), LIFE_BACKING);
        // In its own seat's colour, the same way the mat is - and brighter under the cursor,
        // the same way everything pressable here is. Two boards facing each other put their
        // counters in the same strip of table between them, back to back, and in one grey
        // the pair read as a single control with neither saying which board it was for.
        graphics.renderOutline(box.x(), box.y(), box.width(), box.height(),
                SeatColour.at(seat.seat().index(), way == 0 ? 0xAA : 0xFF));
        // In what the two ends leave, not in half the box: half the box plus two ends better
        // than a quarter each comes to more than there is, and two figures filling it ran
        // into both signs.
        Rect middle = TableSurface.lifeMiddle(box);
        Component total = Component.literal(Integer.toString(seat.life()));
        GuiText.drawCentredAt(graphics, this.font, total, (int) middle.centreX(),
                (int) box.centreY() - this.font.lineHeight / 2, scale, LABEL);
        // A minus over the end that takes one off and a plus over the end that puts one on,
        // asked of the same function the press is, so the two cannot end up disagreeing.
        drawLifeEnd(graphics, seat.seat(), box, -1, way, scale);
        drawLifeEnd(graphics, seat.seat(), box, 1, way, scale);
    }

    private void drawLifeEnd(GuiGraphics graphics, SeatId seat, Rect box, int way, int lit,
            float scale) {
        Rect end = TableSurface.lifeEnd(box, lifeIsTurned(seat), way);
        // A sign narrower than the mark itself is a smudge beside a number, so at that size
        // the counter is the number alone. It is still pressable at both ends - the halves
        // are worked out from the box and not from what is written on it.
        if (end.isEmpty() || end.width() < this.font.width("+") * scale) {
            return;
        }
        GuiText.drawCentredAt(graphics, this.font, Component.literal(way < 0 ? "-" : "+"),
                (int) end.centreX(), (int) box.centreY() - this.font.lineHeight / 2,
                scale, lit == way ? ACCENT : ZONE_LABEL);
    }

    /**
     * Whether this seat's counter is drawn turned about in the view being played.
     *
     * <p>Asked of the surface rather than worked out here, because the board drawn in the
     * world asks the same question before it writes the signs on the ends - and the two
     * asking separately is how the end marked plus came to take a life off.
     */
    private boolean lifeIsTurned(SeatId seat) {
        return board().surface().lifeIsTurned(seat.index(), !playingOnTheBlock);
    }

    /** Which way a press at this screen point on a seat's counter would go: -1, 1, or 0. */
    private int lifeWayUnder(SeatId seat, int x, int y) {
        double[] at = pointer(x, y);
        // The board's own rectangle and the board's own pointer, so the answer is in one
        // space. Asked of absolute surface units it would name the wrong end of the counter
        // on the seated board, whose camera turns the felt round on its way to the screen.
        return at == null ? 0
                : TableSurface.lifeWayAt(
                        board().lifeRect(seat), lifeIsTurned(seat), at[0], at[1]);
    }

    /**
     * Where the end of a seat's counter meaning this way is, in the view being played.
     *
     * <p>Package-private for the scripted harness, which has to aim at the end this screen
     * would actually read that way rather than at whichever end of the rectangle it guesses.
     */
    Rect lifeEndFor(SeatId seat, int way) {
        return TableSurface.lifeEnd(board().lifeRect(seat), lifeIsTurned(seat), way);
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
        boardsDrawn = 0;
        for (SeatView seat : board.seats()) {
            // A board rather than an occupant: somebody who walks away mid-game leaves their
            // cards where they were, and their graveyard and exile pile are public and are
            // what anybody watching is there to read.
            if (!seat.hasABoard()) {
                continue;
            }
            boardsDrawn++;
            drawPileGroup(graphics, board().pileGroupRect(seat.seat(), 0, IN_HAND_REACH, count));
            if (count > Zone.PILES_WITHOUT_A_COMMAND_ZONE) {
                drawPileGroup(graphics, board().pileGroupRect(
                        seat.seat(), Zone.PILES_WITHOUT_A_COMMAND_ZONE, count - 1, count));
            }
            for (int index = 0; index < count; index++) {
                Rect where = board().pileRect(seat.seat(), index, count);
                if (where.isEmpty() || where.width() < 4) {
                    continue;
                }
                Zone zone = Zone.PILES.get(index);
                boolean over = where.contains(mouseX, mouseY);
                if (over) {
                    List<Component> tax = tipForTax(board, seat.seat(), zone, mouseX, mouseY);
                    tooltip = tax == null ? tipForPile(seat.seat(), zone) : tax;
                }
                drawPile(graphics, seat, zone, where, over, mouseX, mouseY);
            }
        }
    }

    /**
     * What resting on a pile says: its name, and what a press on it would do.
     *
     * <p>Worked out from the same two facts the press itself is - whose pile it is and which
     * one - so that the tooltip cannot promise a draw where a press would open a screen. Your
     * own library draws; every other pile opens; somebody else's library does nothing at all
     * and says nothing rather than offering a click that would be ignored.
     */
    private List<Component> tipForPile(SeatId owner, Zone zone) {
        Component name = ZoneText.name(zone);
        SeatId me = mySeat().orElse(null);
        boolean mine = owner.equals(me);
        String hint;
        if (zone != Zone.LIBRARY) {
            hint = "screen.gathering.table.pile.hint_look";
        } else if (mine) {
            hint = "screen.gathering.table.pile.hint_draw";
        } else {
            return List.of(name);
        }
        Component said = Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY);
        return mine
                ? List.of(name, said, Component.translatable("screen.gathering.table.pile.hint_more")
                        .withStyle(ChatFormatting.DARK_GRAY))
                : List.of(name, said);
    }

    /**
     * What resting on the tax under a commander says, or null when that is not where the
     * cursor is.
     *
     * <p>One place asks the question for both views, so the board on the block and the board
     * on the screen cannot end up offering different presses on the same box.
     */
    private List<Component> tipForTax(GameView board, SeatId owner, Zone zone, int x, int y) {
        CardInstanceId commander = taxUnderThePointer(board, owner, zone, x, y);
        if (commander == null) {
            return null;
        }
        int casts = board.seat(owner).commanderTax().getOrDefault(commander, 0);
        return List.of(
                Component.translatable("screen.gathering.table.tax", CommandSlots.taxFor(casts)),
                Component.translatable("screen.gathering.table.tax.casts", casts)
                        .withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("screen.gathering.table.tax.hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
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
        int pointed = verbSlotAt(me, mouseX, mouseY);
        for (int index = 0; index < count; index++) {
            Rect where = board().verbRect(me, index, count);
            if (where.isEmpty() || where.width() < 8) {
                continue;
            }
            TableVerb verb = TableVerb.values()[index];
            boolean hovered = index == pointed;
            GatheringSprites.inset(graphics, where.x(), where.y(), where.width(), where.height());
            if (hovered) {
                GatheringSprites.highlight(
                        graphics, where.x(), where.y(), where.width(), where.height());
                graphics.renderOutline(
                        where.x(), where.y(), where.width(), where.height(), ACCENT);
            }
            if (hovered) {
                tooltip = tipFor(VERB_NAMES[index], VERB_KEY_NAMES[index]);
            }
            if (everyVerbNameFits(where.width() - 2)) {
                GuiText.drawCentredAt(graphics, this.font, VERB_NAMES[index],
                        (int) where.centreX(), (int) where.centreY() - this.font.lineHeight / 2,
                        GuiText.scaleForTheSet(
                                this.font, longestOf(VERB_NAMES), where.width() - 2),
                        hovered ? LABEL : ZONE_LABEL);
            }
        }
    }

    /** What a card nobody may name looks like on its way across: the back of one. */
    private static final CardView A_SLEEVE = new CardView.Anonymous(
            null, false, java.util.Map.of(), null, null, null, null, false);

    /**
     * The cards currently crossing the felt on their way somewhere.
     *
     * <p>Drawn over the board and under everything that is not the board, because a card in
     * the air is above the table and below the hand holding the mouse. Face down unless this
     * client was already entitled to know what it is - a card crossing to somebody's hand is
     * a card leaving, and the fact that it left is public while the card is not.
     */
    private void renderFlights(GuiGraphics graphics, GameView board) {
        long now = ClientCardFlights.now();
        for (ClientCardFlights.Flight flight : ClientCardFlights.at(table, now)) {
            Rect where = FlightPath.at(board(), table, pileCount(), flight, now);
            if (where.isEmpty()) {
                continue;
            }
            // A sleeve unless this client was already entitled to the name. A card on its
            // way into somebody's hand has left the table, so it is drawn leaving as what
            // anybody watching would see leaving: the back of a card.
            CardView card = flight.move().card()
                    .flatMap(id -> findCard(board, id))
                    .orElse(A_SLEEVE);
            drawCard(graphics, card, where, 0, false, true);
        }
    }

    /**
     * Which of this seat's mat buttons a point is on, or -1.
     *
     * <p>Asked through the pointer rather than of the raw cursor, so it answers in whichever
     * space the board being played is measured in - pixels on the seated screen and units on
     * the felt when the board is the real one. The buttons are drawn on the block as well as
     * on the screen, and for a while only the screen's could be pressed: four boxes printed
     * on the table that did nothing when clicked, in the one view meant for playing in.
     */
    private int verbSlotAt(SeatId me, int x, int y) {
        if (me == null) {
            return -1;
        }
        double[] at = pointer(x, y);
        if (at == null || !layout().isOnFelt(x, y)) {
            return -1;
        }
        int count = TableVerb.count();
        for (int index = 0; index < count; index++) {
            Rect where = board().verbRect(me, index, count);
            if (!where.isEmpty()
                    && where.contains((int) Math.round(at[0]), (int) Math.round(at[1]))) {
                return index;
            }
        }
        return -1;
    }

    /** Runs the verb whose button is under this point, if one is. */
    private boolean pressVerb(SeatId me, int x, int y) {
        int index = verbSlotAt(me, x, y);
        if (index < 0) {
            return false;
        }
        GatheringButtons.clickSound();
        doVerb(me, TableVerb.values()[index]);
        return true;
    }

    /**
     * A pile somebody has just shuffled, rattling where it stands.
     *
     * <p>A shuffle changes nothing anybody may look at - not a count, not a zone, and the
     * order it changes is the order nobody is entitled to know - so it is the one thing a
     * player can do that the board cannot show. A stack of cards briefly rattling is what it
     * looks like at a real table, and it is enough.
     *
     * <p>The seat and the zone are the seed, so two libraries shuffled at once are two hands
     * shuffling rather than one board vibrating.
     */
    private Rect shakenIfStirred(SeatId seat, Zone zone, Rect slot) {
        long shaking = ClientTableNews.shakingFor(
                table, seat, zone, ClientCardFlights.now());
        if (shaking < 0 || slot.isEmpty()) {
            return slot;
        }
        int reach = Math.max(1, slot.width() / SHAKE_OF_A_SLOT);
        int seed = seat.index() * Zone.values().length + zone.ordinal();
        return new Rect(
                slot.x() + Shaking.wobble(seed, shaking, reach),
                slot.y() + Shaking.wobble(seed + 7, shaking, reach),
                slot.width(), slot.height());
    }

    /** How far a shuffled pile rattles, as a fraction of its own width. */
    private static final int SHAKE_OF_A_SLOT = 8;

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
    private void drawPile(GuiGraphics graphics, SeatView view, Zone zone, Rect pile,
            boolean hovered, int mouseX, int mouseY) {
        pile = shakenIfStirred(view.seat(), zone, pile);
        ZoneView contents = view.zones().get(zone);
        int count = contents == null ? 0 : contents.count();
        if (held != null && held.fromPile() == zone && held.from().equals(view.seat())) {
            // Whatever is in the air is out of the pile as far as anybody looking is
            // concerned - the top card, or on a long hold the whole thing.
            count = held.whole() ? 0 : Math.max(0, count - (held.card() == null ? 0 : 1));
        }

        // Lit when the card in the air would land in this one. The board has always worked
        // out which slot a drag is aimed at - the board on the block has drawn it since it
        // had one - and the seated board computed the same answer every frame and then never
        // looked at it. So a player dragging towards a column of four or five slots had the
        // whole mat outlined and nothing saying which of them they were about to hit, which
        // is a question you could only answer by letting go and reading the log.
        if (ClientTableHighlight.isAimedAt(view.seat(), Zone.PILES.indexOf(zone))) {
            // Two rings and a wash rather than one thin outline. This is answering "which of
            // five", and the slots are a stack of boxes that already have borders - a single
            // line one shade brighter than the ones above and below it is a difference you
            // have to go looking for, which is the opposite of what a player mid-drag needs.
            graphics.fill(pile.x() - 2, pile.y() - 2, pile.right() + 2, pile.bottom() + 2, AIMED);
            graphics.renderOutline(pile.x() - 2, pile.y() - 2,
                    pile.width() + 4, pile.height() + 4, ACCENT);
            graphics.renderOutline(pile.x() - 1, pile.y() - 1,
                    pile.width() + 2, pile.height() + 2, ACCENT);
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
            // Flush against the slot column rather than centred in its own box, so the
            // names line up with each other and with the boxes they name. Which side the
            // column is on is read off the two rectangles: a mat is mirrored for the player
            // opposite, and asking the mat again here would be a second copy of that rule.
            float scale = GuiText.scaleForTheSet(
                    this.font, longestOf(ZONE_NAMES), named.width());
            int baseline = (int) named.centreY() - this.font.lineHeight / 2;
            if (named.x() < art.x()) {
                GuiText.drawFlushRight(graphics, this.font, ZoneText.name(zone),
                        named.right(), baseline, scale, ZONE_LABEL);
            } else {
                GuiText.drawFlushLeft(graphics, this.font, ZoneText.name(zone),
                        named.x(), baseline, scale, ZONE_LABEL);
            }
        }
        // A command slot holds one commander, so a number counting cards there says "1" all
        // game. It says what that commander's next cast costs instead, which is the number a
        // Commander deck actually reads off that box. Asked once, here, rather than by each
        // of the two things that want to know - whether there is a band and what goes in it -
        // because those are one question with one answer.
        //
        // Measured off the rectangle this pile is actually being drawn in, so a slot mid-shake
        // carries its number along instead of leaving it behind on the felt - and so the band
        // is the foot of the box as the player sees it rather than the foot of a rectangle in
        // surface units, which the seated camera turns upside down on its way to the screen.
        CardInstanceId commander = CommandSlots.commanderIn(view, zone);
        Rect taxBand = commander == null || !taxIsWritten(art)
                ? Rect.NONE
                : TableSurface.taxBand(art);
        if (taxBand.isEmpty()) {
            drawCountInTheCorner(graphics, art, count);
        } else {
            drawTaxBand(graphics, taxBand,
                    CommandSlots.taxFor(view.commanderTax().getOrDefault(commander, 0)),
                    taxBand.contains(mouseX, mouseY));
        }
        if (hovered) {
            graphics.renderOutline(pile.x(), pile.y(), pile.width(), pile.height(), ACCENT);
            top.filter(card -> !card.isFaceDown()).ifPresent(this::offerToInspector);
        }
    }

    /**
     * What resting on a life total says: whose it is, what it is, and that it is a button.
     *
     * <p>Named, because a number floating on the table between two boards belongs to one of
     * them and which one is the whole question a four-seat table asks.
     *
     * <p>Named for a watcher too, and that is the case the naming is really for. Somebody
     * sitting down has their own board under their own number and can work the rest out from
     * where it is; somebody standing behind the table has no such anchor, and it was exactly
     * that viewer the tooltip used to say nothing at all to. What a watcher does not get is
     * the two lines about pressing it, because they have no seat to press it from - an
     * offer nobody can take is worse than no offer.
     */
    private List<Component> tipForLife(GameView board, int x, int y) {
        for (SeatView seat : board.seats()) {
            if (!seat.hasABoard() || lifeWayUnder(seat.seat(), x, y) == 0) {
                continue;
            }
            Component whose = Component.translatable("screen.gathering.table.life",
                    CountersScreen.titleForSeat(board, seat.seat()), seat.life());
            if (mySeat().isEmpty()) {
                return List.of(whose);
            }
            return List.of(
                    whose,
                    Component.translatable("screen.gathering.table.life.hint")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    Component.translatable("screen.gathering.table.life.hint_more")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        return null;
    }

    /**
     * A press on somebody's life total: one off the left half, one on the right, or a typed
     * amount on a right-click.
     *
     * <p>Anybody seated may change anybody's, like everything else public at this table. Who
     * did it is in the log, which is how the mod answers that question everywhere rather than
     * by refusing.
     */
    private boolean pressLife(GameView board, int x, int y, int button) {
        SeatId me = mySeat().orElse(null);
        if (me == null || (button != 0 && button != 1)) {
            return false;
        }
        for (SeatView seat : board.seats()) {
            if (!seat.hasABoard()) {
                continue;
            }
            int way = lifeWayUnder(seat.seat(), x, y);
            if (way == 0) {
                continue;
            }
            SeatId whose = seat.seat();
            if (button == 1) {
                // Typed rather than clicked eleven times. A Commander game that opens on
                // forty life and takes eleven off in one swing is the case a counter you can
                // only tick has no answer to. Which half was right-clicked says which way,
                // so the amount asked for is a plain number and the two halves mean the same
                // thing under either button - rather than a typed minus sign, which is a
                // second way to say something the button already said.
                int chosen = way;
                ask(way < 0 ? "life_lose" : "life_gain", 1,
                        amount -> send(new GameEvent.LifeChanged(me, whose, chosen * amount)));
            } else {
                send(new GameEvent.LifeChanged(me, whose, way));
            }
            GatheringButtons.clickSound();
            return true;
        }
        return false;
    }

    /**
     * The commander whose tax the cursor is resting on, or null.
     *
     * <p>One question asked once, because a press and the tooltip that promised it have to
     * agree exactly. Written out twice they agreed on the day they were written and would
     * have parted company the first time either grew a condition - which is a tooltip
     * offering a button that does nothing, or a button nothing told anybody about.
     */
    private CardInstanceId taxUnderThePointer(
            GameView board, SeatId owner, Zone zone, int x, int y) {
        if (!zone.isCommandSlot() || mySeat().isEmpty()) {
            return null;
        }
        CardInstanceId commander = CommandSlots.commanderIn(board.seat(owner), zone);
        if (commander == null) {
            return null;
        }
        Rect band = taxBandFor(owner, zone);
        double[] at = pointer(x, y);
        return !band.isEmpty() && at != null
                && band.contains((int) Math.round(at[0]), (int) Math.round(at[1]))
                ? commander
                : null;
    }

    /**
     * The band a press would land on for this seat's slot, or empty when there is none.
     *
     * <p>Package-private for the scripted harness, which has to aim at the box this screen
     * would actually accept a press on rather than at one worked out a second time - a run
     * that owns its own copy of a rule is a run that goes on passing after the rule moves.
     */
    Rect taxBandFor(SeatId owner, Zone zone) {
        Rect slot = pileSlotOf(owner, zone);
        return taxIsWritten(slot) ? TableSurface.taxBand(slot) : Rect.NONE;
    }

    /**
     * Whether a slot this size has its tax written on it, which is also whether it may be
     * pressed.
     *
     * <p>One rule for both, because a button nobody can see is worse than no button: a board
     * zoomed out far enough that the number will not fit is a board where clicking a command
     * slot should pick the commander up, the same as clicking anywhere else on it.
     *
     * <p>The board on the block writes its numbers at whatever size the slot is, scaling the
     * letters down with it, so there the answer is always yes.
     */
    private boolean taxIsWritten(Rect slot) {
        return !slot.isEmpty() && (playingOnTheBlock || roomForANumber(slot));
    }

    /**
     * How many cards are in this slot, written in its own corner.
     *
     * <p>Shrunk rather than dropped on a board drawn small. A number is not a word: "24" at
     * six-tenths is still twenty-four, and how many cards are left in a library is something
     * a player reads constantly and cannot get at any other way once the board is framed
     * whole. The zone's *name* still crosses out at that size, because the shape of a column
     * of boxes carries the order and a five-pixel word carries nothing.
     */
    private void drawCountInTheCorner(GuiGraphics graphics, Rect art, int count) {
        if (!roomForANumber(art)) {
            return;
        }
        float scale = numberScale(art);
        Component label = Component.literal(Integer.toString(count));
        int width = Math.round(this.font.width(label) * scale) + 4;
        // GuiText keeps a shrunken line on the baseline a full-size one would have used, so
        // the backing has to be measured the same way or a shrunk number sits with its head
        // out over the card art it is supposed to be legible against.
        int top = Math.round(art.bottom() - this.font.lineHeight * (1 + scale) / 2f) - 1;
        graphics.fill(art.right() - width, top, art.right(), art.bottom(), GHOST_TINT);
        GuiText.drawFlushLeft(graphics, this.font, label, art.right() - width + 2,
                art.bottom() - this.font.lineHeight, scale, LABEL);
    }

    /**
     * Whether a box this tall has room for a number at all.
     *
     * <p>The same question for a pile's count and for a commander's tax, so the two numbers
     * on the board appear and disappear together rather than one of them outlasting the
     * other on a board being zoomed out. The floor is where the box stops being a box rather
     * than where a full-size line stops fitting: below that the number is shrunk to suit.
     */
    private boolean roomForANumber(Rect slot) {
        return slot.height() > Math.round(this.font.lineHeight * SMALLEST_LIFE_SCALE) + 2;
    }

    /** How far a number in a box that size has to shrink, down to the usual floor. */
    private float numberScale(Rect slot) {
        return Math.max(SMALLEST_LIFE_SCALE,
                Math.min(1f, (slot.height() - 2f) / this.font.lineHeight));
    }

    /**
     * The tax written across the foot of a command slot.
     *
     * <p>On a backing rather than straight onto the art, the same as a pile's count: a pale
     * commander with a white number over it is a number nobody can read. Lit when the cursor
     * is on it, because it is a button and a button that looks like a label is a button
     * nobody presses.
     */
    private void drawTaxBand(GuiGraphics graphics, Rect band, int tax, boolean lit) {
        // Darker than a pile's count sits on, because this one is written over card art
        // rather than over an empty slot, and a commander with a pale text box under a white
        // number is a number nobody can read.
        graphics.fill(band.x(), band.y(), band.right(), band.bottom(),
                lit ? TAX_LIT : TAX_BACKING);
        Component label = Component.literal("+" + tax);
        int baseline = (int) band.centreY() - this.font.lineHeight / 2;
        GuiText.drawCentredAt(graphics, this.font, label, (int) band.centreX(), baseline,
                numberScale(band), lit ? ACCENT : LABEL);
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
        if (longestZoneNameWidth == 0) {
            longestZoneNameWidth = this.font.width(longestOf(ZONE_NAMES));
        }
        return room >= Legibility.roomToWrite(longestZoneNameWidth, guiScale());
    }

    /**
     * And the same for the buttons down the other side.
     *
     * <p>Measured against its own longest word rather than against the longest word anywhere
     * on the mat. Sharing one measurement was tried, so that the labels on the two sides would
     * come and go together: the buttons are narrower than the strip of felt the zone names are
     * written on, so a threshold set by "Graveyard" left every button blank at a size where
     * "Mulligan" fitted them perfectly well. Two sets of labels, two rooms, two answers.
     */
    private boolean everyVerbNameFits(int room) {
        if (longestVerbNameWidth == 0) {
            longestVerbNameWidth = this.font.width(longestOf(VERB_NAMES));
        }
        return room >= Legibility.roomToWrite(longestVerbNameWidth, guiScale());
    }

    /**
     * Screen pixels per interface pixel, which decides how far a label may be shrunk before
     * it stops being a word. Asked of the window each time rather than kept, because the
     * player can change it from the options screen without this screen being built again.
     */
    private double guiScale() {
        Minecraft client = this.minecraft;
        return client == null ? 1.0 : client.getWindow().getGuiScale();
    }

    /**
     * A name, and under it in grey the key that does the same thing.
     *
     * <p>The key is on the tooltip rather than only in the key list because the moment a
     * player wants to know it is the moment they are already pointing at the button, and a
     * list they have to open and read is a step this saves them for good: they press the
     * button twice more and then stop needing it.
     */
    private static List<Component> tipFor(Component name, Component key) {
        return key == null
                ? List.of(name)
                : List.of(name, key.copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The longest of a set of names, so the whole set can be drawn at one size. */
    private Component longestOf(Component[] names) {
        Component longest = names[0];
        for (Component name : names) {
            if (this.font.width(name) > this.font.width(longest)) {
                longest = name;
            }
        }
        return longest;
    }

    private static final Component[] ZONE_NAMES =
            Zone.PILES.stream().map(ZoneText::name).toArray(Component[]::new);

    /**
     * The key that runs each mat button, in the order of the buttons.
     *
     * <p>-1 where there is no key. Mulligan has none on purpose: it is wanted once a game and
     * putting it on the number row would cost a key that a verb wanted every turn could have
     * had.
     *
     * <p>One table, read three ways: the key press dispatches through it, the tooltip names
     * the key from it, and the button and the key therefore cannot come to mean different
     * things. They were three separate lists for about an hour, which is how long it took to
     * notice that changing one of them would have left the other two lying.
     */
    private static final int[] VERB_KEYS = {
        org.lwjgl.glfw.GLFW.GLFW_KEY_1,
        org.lwjgl.glfw.GLFW.GLFW_KEY_2,
        org.lwjgl.glfw.GLFW.GLFW_KEY_R,
        -1,
    };

    /** What each of those keys is called, in the player's own language and keyboard layout. */
    private static final Component[] VERB_KEY_NAMES = keyNames();

    private static Component[] keyNames() {
        Component[] named = new Component[VERB_KEYS.length];
        for (int index = 0; index < VERB_KEYS.length; index++) {
            named[index] = VERB_KEYS[index] < 0
                    ? null
                    : com.mojang.blaze3d.platform.InputConstants
                            .getKey(VERB_KEYS[index], -1).getDisplayName();
        }
        return named;
    }

    /** The mat button this key press is, or null. */
    private static TableVerb verbForKey(int key) {
        for (int index = 0; index < VERB_KEYS.length; index++) {
            if (VERB_KEYS[index] == key) {
                return TableVerb.values()[index];
            }
        }
        return null;
    }

    /**
     * What a mat button does, wherever it was asked for.
     *
     * <p>One body for the button and for the key, because they are the same verb: pressing
     * Draw and pressing 2 have to reach the same event or one of them is a second, quieter
     * implementation of drawing a card.
     */
    private void doVerb(SeatId me, TableVerb verb) {
        switch (verb) {
            case UNTAP -> send(new GameEvent.SeatUntappedAll(me, me));
            case DRAW -> send(new GameEvent.CardsDrawn(me, me, 1));
            case SHUFFLE -> send(new GameEvent.LibraryShuffled(me, me));
            case MULLIGAN -> send(new GameEvent.Mulliganed(me, me, MULLIGAN_HAND));
        }
    }

    private static final Component[] VERB_NAMES =
            java.util.Arrays.stream(TableVerb.values())
                    .map(verb -> (Component) Component.translatable(verb.key()))
                    .toArray(Component[]::new);

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
            handSaid = Component.translatable("screen.gathering.table.spectating").getString();
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.spectating"),
                    area.x() + area.width() / 2, area.bottom() - 14, area.width(), DIM);
            return;
        }

        List<CardView> hand = board.seat(seat).zone(Zone.HAND).cards();
        if (hand.isEmpty()) {
            handSaid = Component.translatable("screen.gathering.table.hand_empty").getString();
            // A hand with nothing in it said nothing at all, which reads as a strip of the
            // window that has failed rather than as a hand you have played out. A spectator
            // has been told why their strip is empty since the day they had one; a player who
            // has just emptied theirs deserves the same sentence.
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.hand_empty"),
                    area.x() + area.width() / 2, area.bottom() - 14, area.width(), DIM);
            return;
        }
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
        int turnWidth = Math.min(area.width() / 3, 190);
        int column = seats.isEmpty() ? area.width() : (area.width() - turnWidth) / seats.size();
        int line = area.y() + (area.height() - this.font.lineHeight) / 2;

        for (int index = 0; index < seats.size(); index++) {
            SeatView seat = seats.get(index);
            // A chair nobody is in says so and stops. Forty life, no cards and no deck are
            // all true of a player who does not exist, and printing them makes an empty seat
            // look like somebody who is losing badly.
            //
            // A board, not an occupant. The numbers beside a board somebody walked away from
            // are real - that is their library and their life total, sitting on the table -
            // and calling the whole column "free seat" hid a board in play behind an offer of
            // a chair. The chair genuinely is free, so the offer is not wrong; it is just not
            // the whole of what is there, and the name is the part that was missing.
            Component text;
            if (!seat.hasABoard()) {
                text = Component.translatable("screen.gathering.table.free_seat");
            } else {
                text = Component.translatable(
                        "screen.gathering.table.mat_line", SeatNames.of(seat).getString(),
                        seat.life(), count(seat, Zone.HAND), count(seat, Zone.LIBRARY));
                // And said plainly when nobody is in the chair, because a name in this row
                // otherwise means somebody is sitting behind those cards and answering.
                if (seat.occupant().isEmpty()) {
                    text = text.copy().append(
                            Component.translatable("screen.gathering.table.seat_away"));
                }
                if (!seat.counters().isEmpty()) {
                    text = text.copy().append(Component.literal("  " + describeCounters(seat)));
                }
            }
            GuiText.draw(graphics, this.font, text,
                    area.x() + 4 + index * column, line, column - 8,
                    SeatColour.at(seat.seat().index(), 0xFF));
        }

        // A chair nobody is in is named by its number rather than called "(empty)". The
        // columns above can say a chair is free, because that is what they are for; in a
        // sentence about whose turn it is, "(empty)" reads as something having gone wrong.
        String who = board.seat(active).whoseBoard()
                .map(player -> player.name())
                .orElseGet(() -> Component.translatable(
                        "message.gathering.seat_number", active.index() + 1).getString());
        boolean mine = me != null && me.equals(active);
        // Whose turn it is and where in it everyone is. The phase is a marker and nothing
        // more - the mod never advances it, never checks an action suits it and never stops
        // anybody doing anything in any phase - but four people agreeing on where they are
        // without saying it out loud every thirty seconds is most of what it is for.
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.turn",
                        board.turn().turnNumber(), who, phaseName(board.turn().phase())),
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
        // Below the status bar, and below the life controls that hang off the mats into the
        // strip under it. Worked out before anything is sized, because how far down the panel
        // starts is what decides how many lines fit in it - pushing the top down afterwards
        // gave it the right position and one line more than it had room for, which then drew
        // over its own footer.
        //
        // Measured against the widest the panel could be rather than the width it turns out
        // to want. The width depends on the lines and the lines depend on the height, so the
        // conservative span is the one that does not need the answer to find it.
        int top = Math.max(layout().status().bottom() + 4,
                clearOfTheLifeControls(board, margin, this.width - margin,
                        layout().status().bottom() + 4));
        int roomDown = floorOfTheFelt() - top - 6;
        if (!theLogHasRoom() || roomDown < line * 3) {
            return;
        }

        // Sized to what it has to say, not to a fraction of the window. This panel is read
        // against the board - "who did that, and to what" is a question about the board - and
        // two thirds of the window laid over it to show four lines of text answered the
        // question by hiding the thing it was about.
        Component title = Component.translatable("screen.gathering.table.log_title");
        Component close = Component.translatable("screen.gathering.table.log_close");
        List<LogEntry> log = board.log();
        // No + 1. The panel is a title line, the entries, and a footer line, and adding one
        // back after dividing asked for a line more than there was room for - so the footer
        // was drawn on top of the last thing that happened, which is the line anybody opening
        // a log is looking for.
        int room = Math.max(1, (roomDown - line * 2 - 10) / line);
        int from = Math.max(0, log.size() - room);
        Component[] said = new Component[log.size() - from];
        int widest = Math.max(this.font.width(title), this.font.width(close));
        for (int index = from; index < log.size(); index++) {
            said[index - from] = GameLogText.render(board, log.get(index));
            widest = Math.max(widest, this.font.width(said[index - from]));
        }
        if (log.isEmpty()) {
            widest = Math.max(widest, this.font.width(
                    Component.translatable("screen.gathering.table.log_empty")));
        }

        int width = Math.min(this.width - margin * 2, widest + 10);
        int height = 4 + line + 2 + Math.max(1, said.length) * line + line + 4;
        Rect area = new Rect(
                this.width - margin - width, top, width, Math.min(height, roomDown));

        GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());
        GuiText.draw(graphics, this.font, title,
                area.x() + 5, area.y() + 4, area.width() - 10, ACCENT);
        int first = area.y() + 4 + line + 2;
        int last = area.bottom() - line - 4;
        int y = first;
        // Recorded as it is drawn rather than worked out again afterwards. A check that
        // re-renders the log to see what the log says is a check that passes even when the
        // panel draws nothing at all.
        StringBuilder wrote = new StringBuilder();
        for (Component entry : said) {
            GuiText.draw(graphics, this.font, entry, area.x() + 5, y, area.width() - 10, LABEL);
            if (!wrote.isEmpty()) {
                wrote.append(" / ");
            }
            wrote.append(entry.getString());
            y += line;
        }
        logSaid = wrote.toString();
        if (log.isEmpty()) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.table.log_empty"),
                    area.x() + area.width() / 2, (first + last) / 2, area.width() - 10, DIM);
        }
        GuiText.draw(graphics, this.font, close,
                area.x() + 5, area.bottom() - line - 2, area.width() - 10, DIM);
    }

    /** The lines the log panel last actually put on the screen, for the scripted harness. */
    String logSaid() {
        return logSaid;
    }

    /** Whether the game log is open, for the scripted harness. */
    boolean theLogIsShowing() {
        return showingLog;
    }

    /**
     * Whether there is room to open the log at all.
     *
     * <p>A real question rather than a formality: the panel used to measure itself against
     * the hand strip, and a watcher has no hand, so for them the height came out negative
     * and the log silently refused to open.
     *
     * <p>Asked by the panel itself before it draws and by the scripted harness afterwards,
     * from here rather than from two copies of the arithmetic - a harness holding its own
     * copy of a rule goes on saying yes after the rule has moved, which is the shape of every
     * check in this run that has ever been green over a live fault.
     */
    boolean theLogHasRoom() {
        return roomForTheLog() >= (this.font.lineHeight + 1) * 3;
    }

    /**
     * The first line down the window at which a panel across this span covers no life total.
     *
     * <p>A life counter is the one thing on the felt that has to stay readable while
     * something else is open over it - it is the number the thing being read is usually
     * about.
     */
    private int clearOfTheLifeControls(GameView board, int from, int to, int top) {
        int clear = top;
        for (SeatView seat : board.seats()) {
            Rect life = board().lifeRect(seat.seat());
            if (life.isEmpty() || life.right() <= from || life.x() >= to) {
                continue;
            }
            if (life.bottom() > clear && life.y() < clear + this.font.lineHeight * 3) {
                clear = life.bottom() + 4;
            }
        }
        return clear;
    }

    /** How much window the log has to draw itself in, top to bottom. */
    private int roomForTheLog() {
        return floorOfTheFelt() - (layout().status().bottom() + 4) - 6;
    }

    /**
     * How far down the window the felt goes before something else starts.
     *
     * <p>The hand, when there is one. A watcher holds no cards and the strip is not reserved
     * for them, and a panel that measured itself against a hand that is not there came out
     * with no height at all - so the one person at the table who most wants to read the log
     * could not open it.
     */
    private int floorOfTheFelt() {
        Rect hand = layout().hand();
        return hand.isEmpty() ? this.height : hand.y();
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
        // Down to the bottom of the window, over the hand. The list used to stop where the
        // hand starts, which on a short window left room for so few rows that it wrapped into
        // three columns, and three columns of that width are narrower than half the lines in
        // it - so the list that exists to teach the keys could not show what three of them
        // do. Covering a hand nobody is playing while they read the help is the cheaper loss.
        int available = this.height - top - margin;
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

        // One size for the whole list, taken from its longest line. Fitting each line to the
        // column on its own gave a list in as many sizes as it had lengths - three lines
        // shrunk and twenty-two not - which reads as unfinished however correct each line is.
        float scale = keyListScale(columnWidth - 12);

        int column = 0;
        int y = top;
        for (String[] section : KEY_HELP) {
            // A heading with nothing under it is worse than a heading in the next column.
            if (y + line * 2 > bottom && column + 1 < columns) {
                column++;
                y = top;
            }
            int x = area.x() + 5 + column * columnWidth;
            GuiText.drawFlushLeft(graphics, this.font, keyLine(section[0]), x, y, scale, ACCENT);
            y += line;
            for (int index = 1; index < section.length; index++) {
                if (y + line > bottom && column + 1 < columns) {
                    column++;
                    y = top;
                    x = area.x() + 5 + column * columnWidth;
                }
                GuiText.drawFlushLeft(
                        graphics, this.font, keyLine(section[index]), x + 6, y, scale, LABEL);
                y += line;
            }
            y += 2;
        }
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.table.keys_close"),
                area.x() + 5, area.bottom() - line - 2, area.width() - 10, DIM);
    }

    /**
     * The one size every line of the key list is drawn at.
     *
     * <p>Measured off the longest line rather than off each in turn, which is the rule the
     * mat's own labels have followed since they were added. Remembered against the width it
     * was worked out for, because the list is redrawn every frame it is open and the answer
     * only changes when the window does.
     */
    private float keyListScale(int room) {
        if (room != keyListRoom) {
            keyListRoom = room;
            Component longest = null;
            int widest = -1;
            for (String[] section : KEY_HELP) {
                for (String name : section) {
                    Component line = keyLine(name);
                    int width = this.font.width(line);
                    if (width > widest) {
                        widest = width;
                        longest = line;
                    }
                }
            }
            keyListScale = longest == null ? 1f : GuiText.scaleForTheSet(this.font, longest, room);
        }
        return keyListScale;
    }

    private int keyListRoom = -1;
    private float keyListScale = 1f;

    /**
     * One line of the key list.
     *
     * <p>The lines that name a mat button's key take that key as an argument rather than
     * spelling it out, so the list cannot go on saying "2 - draw a card" after 2 has stopped
     * drawing one. Everything else is prose that names no key of its own.
     */
    private static Component keyLine(String name) {
        Component key = KEY_LIST_KEYS.get(name);
        return key == null
                ? Component.translatable(name)
                : Component.translatable(name, key);
    }

    /** Which key-list lines name a mat button's key, and which key that is. */
    private static final java.util.Map<String, Component> KEY_LIST_KEYS = keyListKeys();

    private static java.util.Map<String, Component> keyListKeys() {
        java.util.Map<String, Component> named = new java.util.HashMap<>();
        for (int index = 0; index < VERB_KEYS.length; index++) {
            if (VERB_KEYS[index] >= 0) {
                named.put("screen.gathering.table.key_"
                                + TableVerb.values()[index].name()
                                        .toLowerCase(java.util.Locale.ROOT),
                        VERB_KEY_NAMES[index]);
            }
        }
        return java.util.Collections.unmodifiableMap(named);
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
                widest = Math.max(widest, this.font.width(keyLine(key)));
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
            aimReport = "nothing held";
            ClientTableHighlight.aimAt(null, -1);
            ClientTableHighlight.landingOn(null);
            return;
        }
        checkLongHold(board);
        CardView card = held.card() == null ? null : findCard(board, held.card()).orElse(null);
        if (card == null && !held.whole()) {
            aimReport = "held a card the view has no answer for";
            return;
        }
        double[] at = pointer(mouseX, mouseY);
        SeatId landing = at == null ? null : board().seatAt(at[0], at[1]);

        if (landing != null && at != null) {
            int slot = board().pileAt(landing, pileCount(), at[0], at[1]);
            aimReport = "cursor " + mouseX + "," + mouseY
                    + " -> board " + Math.round(at[0]) + "," + Math.round(at[1])
                    + " seat " + landing.index() + " slot " + slot + " of " + pileCount();
            ClientTableHighlight.aimAt(landing, slot);
        } else {
            aimReport = "cursor " + mouseX + "," + mouseY + " is on nobody's mat";
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
        Rect comingDownOn = playingOnTheBlock
                ? centredOnCursor(mouseX, mouseY, sizedFor)
                : centred(mouseX - held.grabX(), mouseY - held.grabY(),
                        board().cardWidth(sizedFor), board().cardHeight(sizedFor));

        // Where it would come down, cast on the table, and the card itself held up off it.
        // Two things at once: picking a card up looks like picking it up rather than like the
        // card teleporting to the cursor, and the shadow is the footprint - so the answer to
        // "where is this going" is drawn on the felt, at the size and shape it will be, while
        // the question is still being asked.
        int lift = Math.max(3, comingDownOn.height() / 8);
        graphics.fill(comingDownOn.x(), comingDownOn.y(),
                comingDownOn.right(), comingDownOn.bottom(), CAST);
        // And the footprint's own edge, drawn over the shadow so the two sides the card is
        // lifted away from say where it lands as well as the two the shadow shows on. A
        // shadow alone is only ever visible down one corner - which is a drop shadow, which
        // is the thing this is not.
        graphics.renderOutline(comingDownOn.x(), comingDownOn.y(),
                comingDownOn.width(), comingDownOn.height(), LANDING);

        Rect airborne = new Rect(comingDownOn.x() - lift, comingDownOn.y() - lift,
                comingDownOn.width(), comingDownOn.height());
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, LIFT);
        if (held.whole() && held.fromPile() != null) {
            drawHeldPile(graphics, board, card, airborne);
        } else {
            drawCard(graphics, card, airborne, 0, false, false);
        }
        graphics.pose().popPose();
    }

    /**
     * A whole pile in the air: the pile, drawn as a pile, with how many are in it.
     *
     * <p>Offset copies behind the top card rather than one card with a number stuck on it,
     * because the difference between carrying a card and carrying a graveyard has to be
     * visible at a glance - the drop is the same gesture either way and the only warning is
     * what it looks like.
     */
    private void drawHeldPile(GuiGraphics graphics, GameView board, CardView top, Rect airborne) {
        ZoneView contents = board.seat(held.from()).zone(held.fromPile());
        int count = contents == null ? 0 : contents.count();
        int step = Math.max(2, airborne.height() / 24);
        for (int behind = Math.min(3, count - 1); behind >= 1; behind--) {
            int offset = behind * step;
            graphics.blit(CardFaceRenderer.CARD_BACK,
                    airborne.x() + offset, airborne.y() - offset, 0f, 0f,
                    airborne.width(), airborne.height(), airborne.width(), airborne.height());
        }
        if (top != null) {
            drawCard(graphics, top, airborne, 0, false, false);
        } else {
            graphics.blit(CardFaceRenderer.CARD_BACK, airborne.x(), airborne.y(), 0f, 0f,
                    airborne.width(), airborne.height(), airborne.width(), airborne.height());
        }
        drawCountInTheCorner(graphics, airborne, count);
    }

    private static Rect centred(int middleX, int middleY, int width, int height) {
        return new Rect(middleX - width / 2, middleY - height / 2, width, height);
    }

    /**
     * The card in the air over the board on the block, at the size a card is on that board.
     *
     * <p>It used to be drawn the height of a card in your hand, which on the block is roughly
     * two and a half times the size of a card lying on the table - so picking one up made it
     * balloon, and it covered the very place it was about to be put down.
     *
     * <p>Asked of the camera rather than of a constant, for the same reason the drag is. How
     * big a card is on the block depends on how high the eye is, and that now spans an
     * eleven-fold range - so a fixed conversion drew the card in the air right at one zoom
     * and at four times or a quarter of the cards underneath it everywhere else, which is the
     * ballooning again by another route.
     */
    private Rect centredOnCursor(int mouseX, int mouseY, SeatId sizedFor) {
        double blocks = board().surface().cardHeightOn(sizedFor.index())
                / (double) TableSurface.SPAN * TableTop.SPAN_BLOCKS;
        int height = Math.max(24, (int) Math.round(blocks * TableCameraView.pixelsPerBlock()));
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
        if (button == 0 && mine != null && pressVerb(mine, x, y)) {
            return true;
        }
        // The life counters sit off the mats entirely, so nothing else is competing for the
        // point - but they are still asked before the felt, which would otherwise read a
        // press on one as the start of a box selection.
        if (mine != null && pressLife(board, x, y, button)) {
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
        holdStrayed = false;
        long now = ClientCardFlights.now();
        double[] at = fromHand ? null : pointer(x, y);
        if (at == null) {
            // Straight from the hand, or from somewhere the table cannot answer for: the card
            // takes the cursor by the middle, which is where you would expect to be holding it.
            return new Held(card, seat, fromHand, fromPile, 0, 0, x, y, now, false);
        }
        return new Held(card, seat, fromHand, fromPile,
                (int) Math.round(at[0] - where.centreX()),
                (int) Math.round(at[1] - where.centreY()), x, y, now, false);
    }

    /**
     * Turns a press that has been held still into a hold on the whole pile or the whole stack.
     *
     * <p>The gesture a physical table has: a finger on the top card slides that card, a hand
     * flat on the pile for a moment picks the pile up. Nothing is sent here - what changes is
     * only what the release will mean, and what is drawn in the meantime, so a player who
     * decides against it lets go somewhere harmless exactly as before.
     *
     * <p>Asked once a frame from the drawing, which is the only place that runs while a button
     * is simply being held. Whether the hand wandered is not asked of the cursor here: it is
     * latched by the drag events themselves, so a press that never produced one is a press
     * that never moved, whatever the pointer happens to be doing.
     */
    private void checkLongHold(GameView board) {
        if (held == null || held.whole() || held.fromHand()) {
            return;
        }
        if (holdStrayed || ClientCardFlights.now() - held.began() < LONG_HOLD) {
            return;
        }
        if (held.fromPile() != null) {
            // A pile with one card in it is a card; there is nothing for the gesture to mean.
            ZoneView contents = board.seat(held.from()).zone(held.fromPile());
            if (contents == null || contents.count() < 2) {
                holdStrayed = true;
                return;
            }
            // Emptying somebody else's library into the open would show you the whole thing,
            // so the table refuses it. Refused here as well as there: a gesture that arms,
            // draws a pile in the air and then does nothing on release is worse than one that
            // never arms at all.
            if (held.fromPile().isHidden() && !held.from().equals(mySeat().orElse(null))) {
                holdStrayed = true;
                return;
            }
            held = held.asWhole();
            GatheringButtons.clickSound();
            return;
        }
        // On the felt: everything on the same spot, which is what a stack of cards is when
        // there is no stack type in the game - two cards dropped on one spot are a stack, and
        // the rule for how close counts is the same one that draws them leaning.
        List<CardInstanceId> stack = stackedWith(board, held.from(), held.card());
        if (stack.size() < 2) {
            holdStrayed = true;
            return;
        }
        selected.clear();
        selected.addAll(stack);
        held = held.asWhole();
        GatheringButtons.clickSound();
    }

    /**
     * Every card this client may name lying on the same spot as this one, itself included.
     *
     * <p>Asked of where the cards are rather than of where they are drawn. What is drawn is
     * staggered - that is what makes a pile read as a pile - so a hit test against the drawn
     * rectangles finds the top card and misses everything leaning out from under it, which is
     * the whole stack bar one.
     */
    private List<CardInstanceId> stackedWith(GameView board, SeatId seat, CardInstanceId card) {
        TablePosition here = card == null
                ? null
                : findCard(board, card).flatMap(CardView::placedAt).orElse(null);
        if (here == null) {
            return List.of();
        }
        List<CardInstanceId> found = new ArrayList<>();
        for (CardView other : board.seat(seat).zone(Zone.BATTLEFIELD).cards()) {
            if (other instanceof CardView.Visible visible
                    && other.placedAt().filter(spot -> TableStacking.isStackedOn(here, spot))
                            .isPresent()) {
                found.add(visible.id());
            }
        }
        return found;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panFrom != null && button == 2) {
            pan(dragX, dragY);
            return true;
        }
        // A press that has wandered is a drag, and stays one. Latched here rather than read
        // off the cursor while drawing, because a drag that happens to pass back over where
        // it started is still a drag and must not become a long hold when the hand comes home.
        if (held != null && !held.whole() && held.hasMoved((int) mouseX, (int) mouseY)) {
            holdStrayed = true;
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
            if (dropped.whole() && dropped.fromPile() != null) {
                sendWholePile(dropped, me, ZoneRef.of(me, Zone.HAND), Placement.BOTTOM);
            } else if (!dropped.fromHand()) {
                for (CardInstanceId card : movingWith(dropped)) {
                    send(new GameEvent.CardMoved(
                            me, card, ZoneRef.of(me, Zone.HAND), Placement.BOTTOM));
                }
                selected.clear();
            }
            return true;
        }

        // A press on a pile that never moved was a click on the pile after all - unless it
        // was held long enough to have picked the pile up, in which case letting go where it
        // started is putting it back, and putting it back is doing nothing.
        if (dropped.fromPile() != null && !dropped.whole() && !dropped.hasMoved(x, y)) {
            if (clickPile(me, dropped.from(), dropped.fromPile())) {
                GatheringButtons.clickSound();
            }
            return true;
        }

        // A pile whose top this client may not name, dragged rather than held: there is
        // nothing in the air to put down.
        if (dropped.card() == null && !dropped.whole()) {
            return true;
        }

        // A stack lifted off the felt and put straight back down. Nothing moved, so nothing
        // is sent - and it is emphatically not the click below, which would tap the card on
        // top of the stack somebody had just decided not to move.
        if (dropped.whole() && dropped.fromPile() == null && !dropped.hasMoved(x, y)) {
            return true;
        }

        // A press that never moved is a card picked up and put back, and that is all it is.
        // It used to tap, which made the plainest gesture on the table mean two things at
        // once: every mis-click tapped something, and a card could not be picked up and
        // reconsidered without turning it sideways. Tapping is E, untapping is Q, and the
        // card's own menu says so - one gesture per verb, and the menu is where you learn it.
        if (!dropped.fromHand() && dropped.fromPile() == null && !dropped.hasMoved(x, y)) {
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
            ZoneRef into = ZoneRef.of(landing, Zone.PILES.get(zone));
            if (dropped.whole() && dropped.fromPile() != null) {
                sendWholePile(dropped, me, into, Placement.TOP);
            } else {
                // Everything that came with it goes with it. A selection dropped on a zone
                // used to send one move and quietly leave the rest on the felt, which reads
                // as the table having eaten the other four.
                for (CardInstanceId card : movingWith(dropped)) {
                    send(new GameEvent.CardMoved(me, card, into, Placement.TOP));
                }
            }
            selected.clear();
            return true;
        }

        TablePosition where = board().positionOn(
                landing, at[0] - dropped.grabX(), at[1] - dropped.grabY());
        if (dropped.whole() && dropped.fromPile() != null) {
            sendWholePile(dropped, me, ZoneRef.of(landing, Zone.BATTLEFIELD), Placement.at(where));
            selected.clear();
            return true;
        }
        if (!dropped.fromHand() && dropped.fromPile() == null && selected.contains(dropped.card())) {
            dropGroup(dropped, landing, where, me);
        } else {
            send(new GameEvent.CardMoved(
                    me, dropped.card(), ZoneRef.of(landing, Zone.BATTLEFIELD), Placement.at(where)));
        }
        return true;
    }

    /**
     * Which cards a drop is carrying: the selection when this card is part of it, else itself.
     *
     * <p>Only for cards already on the felt. One coming out of a hand or off a pile is the one
     * card that was picked up, whatever else happens to be selected.
     */
    private List<CardInstanceId> movingWith(Held dropped) {
        if (dropped.card() == null) {
            return List.of();
        }
        if (dropped.fromHand() || dropped.fromPile() != null || !selected.contains(dropped.card())) {
            return List.of(dropped.card());
        }
        return List.copyOf(selected);
    }

    /**
     * Puts a whole pile down somewhere, as one act rather than as forty moves.
     *
     * <p>One event, naming the pile and not its contents. That is what lets a library be
     * moved at all: nobody may name the cards in one, so a client that had to list them could
     * not ask - and it is what keeps the log to a line and undo to a step.
     */
    private void sendWholePile(Held dropped, SeatId me, ZoneRef to, Placement placement) {
        if (dropped.fromPile() == null || to.equals(ZoneRef.of(dropped.from(), dropped.fromPile()))) {
            return;
        }
        send(new GameEvent.ZoneMoved(me, dropped.from(), dropped.fromPile(), to, placement));
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
            // Looking straight down, zoom is height - and anchored on what the cursor is
            // pointing at, the same as the seated board's wheel, so leaning in keeps the
            // thing being leaned towards. The cursor is off the felt often enough to matter
            // here in a way it never is on the seated screen: over the hand, over the strip,
            // over the floor beside the table. Then there is nothing to anchor to and the
            // eye simply comes down.
            double[] at = pointer(mouseX, mouseY);
            if (at == null) {
                TableCameraView.zoom(factor);
            } else {
                TableTop top = tableTop();
                TableCameraView.zoomAt(factor, top.worldX(at[0]), top.worldZ(at[1]));
            }
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
        return held != null && held.card() != null
                && card instanceof CardView.Visible visible && visible.id().equals(held.card());
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
            // Tapping is E and untapping is Q; this row exists to say so. A menu entry that
            // names its key teaches the key, where a second gesture would only compete with
            // it - which is what the plain left click used to do.
            entries.add(entry(card.tapped() ? "untap" : "tap",
                    () -> setTap(board, targets, !card.tapped())));
            // Frozen means it stays tapped through the untap step. Next to tap because that
            // is the pair a player is thinking about, and because the one press this changes
            // is the one two rows down on the felt's own menu.
            boolean thawing = card.frozen();
            entries.add(entry(thawing ? "thaw" : "freeze",
                    () -> eachTarget(board, targets, target ->
                            new GameEvent.CardFrozen(me, target, !thawing))));
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
            // Its other printed face, which is a different act from turning it face down: a
            // transformed permanent is public on both sides and a face-down one is a sleeve.
            // Offered only where there is a second face to turn to, because a menu entry that
            // does nothing on nine cards in ten is a menu entry nobody trusts.
            if (hasAnotherSide(card)) {
                boolean showOther = !card.turnedOver();
                entries.add(entry(showOther ? "turn_over" : "turn_back",
                        () -> eachCard(board, targets, seen -> hasAnotherSide(seen)
                                ? new GameEvent.CardTurnedOver(me, seen.id(), showOther)
                                : null)));
            }
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
            // The pen. A group with no rules engine remembers "flying until end of turn" by
            // somebody writing it on the card, and every other player reading it is the
            // point - so it goes on every card in the selection at once, like the rest.
            entries.add(entry("write", () -> openTheNote(card, targets)));
            // The other pen, in the corner where the numbers are. Typed, never worked out -
            // see CardStrength. A creature that is a 6/6 this turn says 6/6 because somebody
            // said so, which is how it works across a table with no computer on it.
            entries.add(entry("strength", () -> openTheStrength(card, targets)));
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
        // A commander dying goes to the command zone, which is the single most common thing
        // anybody does with one and had no entry at all: the only way back was to drag it.
        // Offered on every card rather than only on a commander, because the mod holds no
        // permanent mark saying which cards are commanders - where a card started is all the
        // game knows - and refusing the move would be it inventing a rule.
        if (pileCount() > Zone.PILES_WITHOUT_A_COMMAND_ZONE) {
            entries.add(entry("to_command", () -> eachCard(board, targets, seen ->
                    new GameEvent.CardMoved(me, seen.id(),
                            ZoneRef.of(seen.owner(),
                                    CommandSlots.homeFor(board.seat(seen.owner()))),
                            Placement.TOP))));
        }
        entries.add(entry("to_library_top", () -> eachCard(board, targets, seen ->
                new GameEvent.CardMoved(
                        me, seen.id(), ZoneRef.of(seen.owner(), Zone.LIBRARY), Placement.TOP))));
        entries.add(entry("to_library_bottom", () -> eachCard(board, targets, seen ->
                new GameEvent.CardMoved(
                        me, seen.id(), ZoneRef.of(seen.owner(), Zone.LIBRARY), Placement.BOTTOM))));
        entries.add(ContextMenu.Entry.rule());
        entries.add(entry("ping", () -> send(new GameEvent.CardPinged(me, id))));

        menu = ContextMenu.at(this.font, x, y, this.width, this.height,
                layout().status().bottom() + 2, entries);
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
        // The tax band sits on top of the slot, so it answers first or it never answers at
        // all - the slot underneath it picks a commander up on the same press.
        if (pressTax(board, owner, pile, x, y, button)) {
            return true;
        }
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
            //
            // A pile whose top nobody may name is still held, with no card in hand: your own
            // library is the pile most worth being able to pick up whole, and the release
            // decides between the click that draws and the hold that moves the lot.
            held = grab(liftableFrom(board, owner, pile), owner, false, pile,
                    pileSlotOf(owner, pile), x, y);
            return true;
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

    /**
     * A press on the tax written under a commander: one more cast, or one fewer.
     *
     * <p>Counted in casts and shown in mana, the same as the counters screen does it, because
     * casts are what the rule counts and mana is what the player is about to pay. Left adds a
     * cast, right takes one back - the mistake has to be as cheap to undo as it was to make,
     * and a number that only goes up is a number somebody has to restart a game over.
     *
     * <p>Anybody seated may press it, like everything else in a public zone. Who did it is in
     * the log, which is how this mod answers that question everywhere.
     */
    private boolean pressTax(GameView board, SeatId owner, Zone pile, int x, int y, int button) {
        SeatId me = mySeat().orElse(null);
        if (me == null || (button != 0 && button != 1)) {
            return false;
        }
        CardInstanceId commander = taxUnderThePointer(board, owner, pile, x, y);
        if (commander == null) {
            return false;
        }
        int casts = board.seat(owner).commanderTax().getOrDefault(commander, 0);
        int delta = button == 0 ? 1 : -1;
        if (casts + delta < 0) {
            // Already nothing. Swallowed rather than sent, so the log does not fill up with
            // lines saying a tax of nought was reduced to a tax of nought.
            return true;
        }
        GatheringButtons.clickSound();
        send(new GameEvent.CommanderTaxChanged(me, owner, commander, delta));
        return true;
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
            // Turn cards over until one of them is the one. Both of these ask the server how
            // far down it is, because nobody may know their own library's order - see
            // RevealUntilPayload. What comes back is an ordinary reveal.
            entries.add(entry("cascade", () -> ask("cascade", 3, manaValue ->
                    ClientNetworking.send(new RevealUntilPayload(table,
                            RevealUntilPayload.Until.CHEAPER_THAN, manaValue, "")))));
            entries.add(entry("reveal_until_type", () ->
                    net.minecraft.client.Minecraft.getInstance().setScreen(new TextPromptScreen(
                            Component.translatable("screen.gathering.reveal_until.type"),
                            Component.translatable("screen.gathering.reveal_until.hint"),
                            RevealUntilPayload.LONGEST_TYPE,
                            wanted -> ClientNetworking.send(new RevealUntilPayload(table,
                                    RevealUntilPayload.Until.OF_TYPE, 0, wanted)),
                            this))));
            entries.add(entry("search", () -> {
                send(new GameEvent.LibrarySearched(me, me));
                openPile(me, Zone.LIBRARY, true);
            }));
            entries.add(entry("shuffle", () -> send(new GameEvent.LibraryShuffled(me, me))));
        } else {
            entries.add(entry("open_pile", () -> openPile(me, pile, false)));
        }
        menu = ContextMenu.at(this.font, x, y, this.width, this.height,
                layout().status().bottom() + 2, entries);
    }

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
                // Named, because the second question arrives after the first has gone and
                // "How many?" on its own is a question about something the player can no
                // longer see. Every other amount asked for in the mod names its verb.
                name -> net.minecraft.client.Minecraft.getInstance().setScreen(new AmountScreen(
                        Component.translatable("screen.gathering.amount.tokens", name), 1,
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

    /**
     * Spreads a pile out on its own screen.
     *
     * <p>{@code opensALibrary} is what makes the screen responsible for closing it again. A
     * library is open because an event said so, so it stays open until an event says
     * otherwise - not until a screen happens to go away.
     */
    private void openPile(SeatId owner, Zone pile, boolean opensALibrary) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new PileScreen(table, owner, pile, opensALibrary, this));
    }

    /** The menu for the table itself, for the verbs that are about a seat rather than a card. */
    private void openTableMenu(int x, int y) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            // A watcher has no seat, so every verb below is a verb they cannot make - but the
            // log is not a verb. It is the record of what has happened at this table, it is
            // public, and refusing to open the menu at all left the one person who most needs
            // to catch up with no way to ask for it.
            List<ContextMenu.Entry> watching = ContextMenu.entries();
            watching.add(entry(showingLog ? "hide_log" : "show_log",
                    () -> showingLog = !showingLog));
            menu = ContextMenu.at(this.font, x, y, this.width, this.height,
                    layout().status().bottom() + 2, watching);
            return;
        }
        List<ContextMenu.Entry> entries = ContextMenu.entries();
        entries.add(entry("draw", () -> send(new GameEvent.CardsDrawn(me, me, 1))));
        // The one verb here the server decides. Everything else on this menu is a move you
        // make and the table writes down; a discard at random is only worth anything because
        // you did not choose, and a client that picked its own cards would look exactly like
        // one that picked its worst. See DiscardAtRandomPayload.
        entries.add(entry("discard_at_random", () -> ask("discard_at_random", 1,
                howMany -> ClientNetworking.send(new DiscardAtRandomPayload(table, howMany)))));
        entries.add(entry("sort_hand", () -> sortMyHand(me)));
        entries.add(entry("make_token", this::askForToken));
        entries.add(entry(showingLog ? "hide_log" : "show_log", () -> showingLog = !showingLog));
        view().ifPresent(board -> entries.add(entry("next_phase",
                () -> send(new GameEvent.PhaseSet(me, board.turn().phase().next())))));
        view().ifPresent(board -> entries.add(entry("pass_turn", () -> passTurn(board, me))));
        entries.add(entry("untap_all", () -> send(new GameEvent.SeatUntappedAll(me, me))));
        entries.add(entry("shuffle", () -> send(new GameEvent.LibraryShuffled(me, me))));
        entries.add(entry("gain_life", () -> send(new GameEvent.LifeChanged(me, me, 1))));
        entries.add(entry("lose_life", () -> send(new GameEvent.LifeChanged(me, me, -1))));
        view().ifPresent(board -> entries.add(entry("my_counters",
                () -> openCounters(new CountersScreen.Subject.Seat(
                        me, CountersScreen.titleForSeat(board, me))))));
        // Taking back a misclick. Asked for rather than done: this client does not decide
        // whether a rewind is allowed, and saying so in the interface would only be a second
        // opinion the table is free to disagree with.
        entries.add(entry("undo", this::undoMyLastAction));
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
        menu = ContextMenu.at(this.font, x, y, this.width, this.height,
                layout().status().bottom() + 2, entries);
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

    /**
     * Opens the pen on this card, filled in with whatever it already says.
     *
     * <p>Filled in from the card that was clicked rather than from the selection, because a
     * selection of five cards has five notes and only one of them was under the cursor.
     * Writing goes to all of them; what is offered to edit is the one you pointed at.
     */
    private void openTheNote(CardView.Visible card, List<CardInstanceId> targets) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new NoteScreen(
                Component.translatable("screen.gathering.note.title"),
                card.writtenOn().orElse(""),
                written -> {
                    for (CardInstanceId target : targets) {
                        send(new GameEvent.CardNoted(me, target, written));
                    }
                },
                this));
    }

    /**
     * The box for typing a power and toughness over the printed ones.
     *
     * <p>The same screen the pen uses, because it is the same act: something a player writes
     * on a card and everybody reads. What differs is how much fits and what the words say.
     */
    private void openTheStrength(CardView.Visible card, List<CardInstanceId> targets) {
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new NoteScreen(
                NoteScreen.Pen.STRENGTH,
                Component.translatable("screen.gathering.strength.title"),
                card.writtenStrength().orElse(""),
                written -> {
                    for (CardInstanceId target : targets) {
                        send(new GameEvent.CardStrengthSet(me, target, written));
                    }
                },
                this));
    }

    private void openCounters(CountersScreen.Subject subject) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new CountersScreen(table, subject, this));
    }

    /**
     * Whether this card is printed with a second face to turn to.
     *
     * <p>A question about a printing, not about the game, so it is answered out of the card
     * data this client has been sent. A card whose data has not arrived yet answers no: an
     * entry that appears a second after the menu opens is worse than one that is not there.
     */
    private boolean hasAnotherSide(CardView card) {
        return summaryOf(card).map(CardSummary::hasAnotherSide).orElse(false);
    }

    /** What to call a card on a screen that has to name what it is about to change. */
    private Component nameOf(CardView card) {
        return summaryOf(card)
                .map(summary -> Component.literal(summary.name()))
                .map(Component.class::cast)
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
    }

    /**
     * Asks the table to take back this player's most recent action.
     *
     * <p>One at a time, because that is what a misclick is. Whether it happens is the
     * session's decision - your own action, this table's undo mode, and never across
     * something that let somebody see a card - and the answer comes back as a message when
     * it is no.
     */
    private void undoMyLastAction() {
        ClientNetworking.send(new UndoPayload(table, 1));
    }

    /** What a phase is called, built once each because the status line asks every frame. */
    private static Component phaseName(dev.gathering.core.game.Phase phase) {
        return PHASE_NAMES[phase.ordinal()];
    }

    private static final Component[] PHASE_NAMES =
            java.util.Arrays.stream(dev.gathering.core.game.Phase.values())
                    .map(phase -> (Component) Component.translatable(
                            "phase.gathering." + phase.name().toLowerCase(java.util.Locale.ROOT)))
                    .toArray(Component[]::new);

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
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && somethingIsOpen()) {
            // Escape shuts whatever is open on top of the table, and only leaves the table
            // when nothing is. Two of these used not to be on the list: the log, which is as
            // open as a menu is and left by the same key everywhere else in the game, and a
            // card halfway through being carried - so pressing Escape while holding one put
            // the player outside the table still holding it.
            closeWhatIsOpen();
            return true;
        }
        // The log is above the seat check, because it is the one panel here that is about
        // the table rather than about a board. It is the public record of a public game, and
        // the person most likely to want to read it is somebody watching who did not see the
        // first half - who had no way to open it at all: the key gave up here and the table
        // menu refused to open for them, while the layout went on reserving room for a panel
        // they could not reach.
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_L) {
            showingLog = !showingLog;
            return true;
        }
        SeatId me = mySeat().orElse(null);
        if (me == null) {
            return super.keyPressed(key, scanCode, modifiers);
        }

        // The mat buttons first, so a key and the button printed with it on stay the same
        // verb whichever of the two the player reaches for.
        TableVerb byKey = verbForKey(key);
        if (byKey != null) {
            doVerb(me, byKey);
            return true;
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
     * <p>Pixels either way now. The seated board is already in pixels, and the camera over
     * the real one turns them into blocks itself, from the height it is actually at - which
     * is the only thing that knows. Panning that moves the world by a different amount than
     * the hand is the single most common way a drag feels wrong; moving it the other way is
     * the one worse than that, and the board on the block did exactly that. Both views grab
     * the felt and pull it, which is what the seated one has always done and what the camera
     * over the real table always said it did.
     */
    private void pan(double pixelsX, double pixelsY) {
        if (playingOnTheBlock) {
            TableCameraView.panByPixels(pixelsX, pixelsY);
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
     * <p>Matched to the reference table, key for key, because somebody arriving from that
     * table already knows them and a row that is nearly the same is worse than one that is
     * different: 0 passes, 1 untaps, 2 draws, 3 scries, 4 mills, 5 reveals, 7 exiles, 8 bins
     * and 9 puts cards under the library in a random order.
     *
     * <p>Six is the one deliberate difference. That table splits revealing into a fan and a
     * stack, which is a choice about how the cards it has already turned over are laid out;
     * this one draws revealed cards one way and spends the key on surveil instead, which is a
     * verb rather than a display and comes up in a real game far more often.
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
            // 7 is exile and 8 is the graveyard, which is the way round the reference table
            // has them. They were the other way round here for no reason but the order they
            // were written in, and a player arriving from that table would have binned two
            // cards before noticing.
            case 7 -> sendUnderCursorTo(me, Zone.EXILE, Placement.TOP);
            case 8 -> sendUnderCursorTo(me, Zone.GRAVEYARD, Placement.TOP);
            case 9 -> bottomOfLibraryAtRandom(me);
            default -> false;
        };
    }

    /**
     * Puts your hand in order of what things cost.
     *
     * <p>Worked out here because the mana cost is card data and the client is what has it -
     * the game itself knows a hand as a list of ids and has never heard of a mana value.
     *
     * <p>A card whose data has not arrived yet goes to the end rather than to the front. Both
     * are arbitrary; the end is the one where an unsorted card is obviously unsorted instead
     * of looking like the cheapest thing in the hand.
     */
    private void sortMyHand(SeatId me) {
        GameView board = view().orElse(null);
        if (board == null) {
            return;
        }
        List<CardView> hand = board.seat(me).zone(Zone.HAND).cards();
        List<CardInstanceId> order = hand.stream()
                .filter(card -> card instanceof CardView.Visible)
                .map(card -> (CardView.Visible) card)
                .sorted(java.util.Comparator
                        .comparingInt((CardView.Visible card) -> costOf(card).orElse(Integer.MAX_VALUE))
                        .thenComparing(card -> summaryOf(card)
                                .map(CardSummary::name).orElse("")))
                .map(CardView.Visible::id)
                .toList();
        if (order.isEmpty()) {
            return;
        }
        send(new GameEvent.HandSorted(me, me, order));
    }

    /** What this card costs, if its data has arrived. */
    private java.util.OptionalInt costOf(CardView card) {
        CardSummary summary = summaryOf(card).orElse(null);
        return summary == null
                ? java.util.OptionalInt.empty()
                : java.util.OptionalInt.of(dev.gathering.core.card.ManaValue.of(
                        summary.sideShown(false).manaCost()));
    }

    /**
     * Puts whatever the keys are pointing at back under the library, in no order.
     *
     * <p>Asked of the server rather than done here, because the order cards go back in is a
     * fact about the bottom of a library and a client that chose it would be the only thing
     * at the table that knew it - see {@link ToBottomAtRandomPayload}.
     */
    private boolean bottomOfLibraryAtRandom(SeatId me) {
        List<CardInstanceId> targets = underCursorOrSelected();
        if (targets.isEmpty()) {
            return false;
        }
        ClientNetworking.send(new ToBottomAtRandomPayload(table, targets));
        selected.clear();
        return true;
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
        setTap(board, targets, tapped);
        return !targets.isEmpty();
    }

    /**
     * Turns these cards sideways, or back, whichever they are now.
     *
     * <p>One method for the key and for the menu row, so the two can never come to mean
     * slightly different things - which is the failure the whole "one gesture per verb" pass
     * is about. A card already the way it is asked for sends nothing.
     */
    private void setTap(GameView board, List<CardInstanceId> targets, boolean tapped) {
        mySeat().ifPresent(me -> eachCard(board, targets, seen -> seen.tapped() == tapped
                ? null
                : new GameEvent.CardTapSet(me, seen.id(), tapped)));
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

    /**
     * Sends an event, and notes any card it moves as this client's own doing.
     *
     * <p>A card this player just dragged has already crossed the felt under their own cursor,
     * and a card they clicked into their graveyard was under the cursor when they did it. The
     * board coming back and agreeing is not news, so it is not drawn flying: a second copy
     * setting off from where the first one started would be the table arguing with the hand
     * that moved it.
     */
    private void send(GameEvent event) {
        long now = ClientCardFlights.now();
        if (event instanceof GameEvent.CardMoved moved) {
            ClientCardFlights.movedItOurselves(moved.card(), now);
        }
        ClientTableActions.send(table, event);
    }

    // --------------------------------------------------------------- drawing

    /** Where the pot is drawn, or nothing when there is not one. For the scene, and above. */
    Rect potOnScreen() {
        List<CardComponent> pot = ClientTableState.potOf(this.table);
        if (pot.isEmpty()) {
            return Rect.NONE;
        }
        return board().fromSurface(board().surface().pot(pot.size()));
    }

    /**
     * The pot, face up in the middle of the table.
     *
     * <p>The one thing on the felt that belongs to nobody, which is why it sits where the
     * mats meet rather than on anybody's side. Drawn whenever there is one and taking no room
     * at all when there is not, so a table not playing for keeps looks exactly as it did.
     *
     * <p>Face up, always, with no face-down case to get wrong: a pot everybody agreed to play
     * for is a pot everybody can see, and that is the whole drama of the thing.
     */
    private void renderPot(GuiGraphics graphics, int mouseX, int mouseY) {
        List<CardComponent> pot = ClientTableState.potOf(this.table);
        Rect area = potOnScreen();
        if (pot.isEmpty() || area.isEmpty()) {
            return;
        }
        Rect where = board().surface().pot(pot.size());
        // The tray the surface reserved, drawn at exactly the size it was checked for room
        // at. Working one out here instead is how a thing that fits in the layout ends up
        // drawn over somebody's life total.
        Rect tray = board().fromSurface(TableSurface.potTray(where));
        GatheringSprites.inset(graphics, tray.x(), tray.y(), tray.width(), tray.height());

        for (int index = 0; index < pot.size(); index++) {
            Rect slot = board().fromSurface(
                    TableSurface.potSlot(where, index, pot.size()));
            if (slot.isEmpty()) {
                continue;
            }
            CardComponent card = pot.get(index);
            ClientCardCache.get().summary(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, slot.x(), slot.y(), slot.width(), slot.height()),
                    () -> GatheringSprites.inset(
                            graphics, slot.x(), slot.y(), slot.width(), slot.height()));
            if (mouseX >= slot.x() && mouseX < slot.right()
                    && mouseY >= slot.y() && mouseY < slot.bottom()) {
                ClientHoverState.setHovered(CardItem.of(card));
                graphics.renderOutline(slot.x(), slot.y(), slot.width(), slot.height(), ACCENT);
            }
        }
        // Said rather than assumed, and inside the tray. A row of cards in the middle of a
        // table is not obviously a pot, and somebody who missed the message when it was
        // staked has nothing else to tell them what they are looking at or what it is for.
        GuiText.drawCentred(graphics, this.font,
                Component.translatable("screen.gathering.table.the_pot", pot.size()),
                tray.x() + tray.width() / 2, area.bottom() + 2, tray.width() - 4, POT_LABEL);
    }

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
            // Cast first, under everything, so the card above reads as being above. Only the
            // two edges that would show: filling the whole card again and moving it is the
            // same picture with far more of it hidden under the card, and the part that is
            // not hidden is the part that made it look airborne.
            graphics.fill(where.x() + SHADOW_OFFSET, where.bottom(),
                    where.right() + SHADOW_OFFSET, where.bottom() + SHADOW_OFFSET, SHADOW);
            graphics.fill(where.right(), where.y() + SHADOW_OFFSET,
                    where.right() + SHADOW_OFFSET, where.bottom(), SHADOW);
        }
        if (card.isFaceDown()) {
            // Even to the player who knows what it is. Their board has to look to them the
            // way it looks to everyone else, or they cannot tell what they have given away.
            graphics.blit(CardFaceRenderer.CARD_BACK, where.x(), where.y(), 0f, 0f,
                    where.width(), where.height(), where.width(), where.height());
        } else {
            summaryOf(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, card.turnedOver(),
                            where.x(), where.y(), where.width(), where.height()),
                    () -> GatheringSprites.inset(
                            graphics, where.x(), where.y(), where.width(), where.height()));
        }
        if (onTheFelt && card.tapped()) {
            // A tapped card is already lying sideways; the tint is what tells it apart from
            // one somebody turned by hand, without a word of text over the art.
            graphics.fill(where.x(), where.y(), where.right(), where.bottom(), TAPPED_TINT);
        }
        if (onTheFelt && card.frozen()) {
            // A frozen card looks frozen. Everything about this feature happens on a press
            // made next turn, without looking, so a freeze that is only in the log is a
            // freeze that gets untapped by habit and argued about afterwards.
            drawFrost(graphics, where);
        }
        drawWriting(graphics, card, where);
        // The numbers first, because they sit in the corner and the counters stack up off
        // the top of them. A power and toughness under a pile of counters is a card whose
        // most important number is the one you cannot see.
        drawCounters(graphics, card, where, drawStrength(graphics, card, where));
        if (hovered) {
            graphics.renderOutline(where.x(), where.y(), where.width(), where.height(), ACCENT);
        }
        if (turned) {
            graphics.pose().popPose();
        }
    }

    /**
     * What somebody wrote on the card, across the top of it.
     *
     * <p>At the top because the counters are along the bottom and the two must not fight over
     * the same band: a card with three counters and a note is a card in play that somebody is
     * keeping track of, which is exactly when both have to be readable at once.
     *
     * <p>Over the name rather than over the art. A card's own name is the one thing on it a
     * player already knows - they put it there - and the art is what makes a board readable
     * from across the table.
     */
    private void drawWriting(GuiGraphics graphics, CardView card, Rect art) {
        CardInspectPanel.drawNote(graphics, this.font, card.writtenOn().orElse(null), art);
    }

    /**
     * The power and toughness somebody wrote on it, in the corner where the printed ones are.
     *
     * <p>Where the card already puts them, so a board reads the same whether the numbers are
     * printed or written. Right-hand corner, one line, on a badge dark enough to be read over
     * whatever the art is doing down there.
     *
     * <p>Nothing is worked out here. What is drawn is exactly what somebody typed - see
     * {@link dev.gathering.core.game.CardStrength}, and section 16 of the brief.
     *
     * @return the line the counters may stack up from, which is above this when there is one
     */
    private int drawStrength(GuiGraphics graphics, CardView card, Rect art) {
        return CardInspectPanel.drawStrength(
                graphics, this.font, card.writtenStrength().orElse(null), art);
    }

    /**
     * What a frozen card looks like: a rime along its edges.
     *
     * <p>Round the outside rather than over the art, because a card can already be carrying a
     * note across its top, counters up its bottom and numbers in its corner, and the frame is
     * the last piece of it nothing else has claimed. It reads at any size the board draws a
     * card at, which a mark in a corner does not.
     *
     * <p>Cold on purpose against the warm gold of a written power and toughness and the cyan
     * of the cursor: three marks on one card have to be three colours or they are one mark.
     */
    private void drawFrost(GuiGraphics graphics, Rect where) {
        graphics.fill(where.x(), where.y(), where.right(), where.bottom(), FROZEN_TINT);
        // Caked along the top and bottom rather than ringing the card. A full outline is what
        // the cursor draws, in a blue close enough to this one that a frozen card under the
        // cursor had two rings nobody could tell apart - and the one that matters is the one
        // that is still there when you look away.
        int rime = Math.max(1, where.height() / 16);
        graphics.fill(where.x(), where.y(), where.right(), where.y() + rime, FROZEN_EDGE);
        graphics.fill(where.x(), where.bottom() - rime, where.right(), where.bottom(), FROZEN_EDGE);
    }

    /**
     * The counters on a card, along its bottom edge.
     *
     * <p>On the card rather than beside it, because a counter that lives next to a card stops
     * being on that card the moment somebody moves either of them.
     */
    private void drawCounters(GuiGraphics graphics, CardView card, Rect art, int floor) {
        if (card.counters().isEmpty()) {
            return;
        }
        int room = Math.max(1, art.width() - 4);
        List<Component> lines = new ArrayList<>();
        // The count that goes flush right on the line at the same index, or null. Parallel to
        // the lines rather than folded into them, because the count must never be the part
        // that gets trimmed off - it is written separately so it is fitted separately.
        List<Component> counts = new ArrayList<>();
        for (Map.Entry<String, Integer> counter : card.counters().entrySet()) {
            // Several +1/+1 counters are one bigger counter rather than a count of small
            // ones, so they are written that way: "+2/+2", not "+1/+1 x2".
            String together = CounterText.addedUp(counter.getKey(), counter.getValue());
            if (together != null) {
                lines.add(Component.literal(together));
                counts.add(null);
                continue;
            }
            Component name = Component.literal(CounterText.name(counter.getKey()));
            if (counter.getValue() == 1) {
                lines.add(name);
                counts.add(null);
                continue;
            }
            Component amount = Component.literal("x" + counter.getValue());
            if (this.font.width(name) <= room - this.font.width(amount) - 3) {
                lines.add(name);
                counts.add(amount);
            } else {
                // A card on a crowded table is narrower than "+1/+1 x2", and a name squeezed
                // into what is left of it comes out as "+" - which in Magic is a different
                // thing entirely, and is the whole reason this stopped being one string. So
                // the count drops to its own line rather than the name losing its end.
                lines.add(name);
                counts.add(null);
                lines.add(amount);
                counts.add(null);
            }
        }
        int line = floor - this.font.lineHeight * lines.size();
        for (int index = 0; index < lines.size(); index++) {
            Component amount = counts.get(index);
            int amountRoom = amount == null ? 0 : this.font.width(amount) + 3;
            graphics.fill(art.x(), line - 1, art.right(), line + this.font.lineHeight - 1, COUNTER_BAND);
            GuiText.draw(graphics, this.font, lines.get(index),
                    art.x() + 2, line, room - amountRoom, COUNTER_TEXT);
            if (amount != null) {
                GuiText.draw(graphics, this.font, amount,
                        art.right() - 1 - this.font.width(amount), line, amountRoom, COUNTER_TEXT);
            }
            line += this.font.lineHeight;
        }
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
            // Turned over if the table has turned it over. Reading a transformed permanent
            // and being shown its front is the read key describing a different card from the
            // one under the cursor.
            CardComponent held = CardComponent.of(visible.identity());
            ClientHoverState.setHovered(CardItem.of(
                    visible.turnedOver() ? held.flip() : held));
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
            layout = TableScreenLayout.of(this.width, this.height, mySeat().isPresent());
        }
        return layout;
    }

    /**
     * Whether anything is open on top of the felt.
     *
     * <p>What Escape shuts, and the one list that decides it. Written out at the key it
     * would have grown a copy the first time something else asked - the log's own close
     * button, say - and those two lists parting company is a panel Escape will not close.
     */
    private boolean somethingIsOpen() {
        return menu != null || !attaching.isEmpty() || showingKeys || showingLog || held != null;
    }

    /** Shuts all of it, because Escape is one press and a player pressed it once. */
    private void closeWhatIsOpen() {
        menu = null;
        attaching = List.of();
        showingKeys = false;
        showingLog = false;
        // Put back where it came from, which is what letting go off the table does too: the
        // card never moved as far as the server is concerned, so there is nothing to undo.
        held = null;
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
