package dev.gathering.core.ui;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.table.SeatAnchor;
import java.util.List;

/**
 * Everything the seated view needs to turn a card's position into a rectangle, and back.
 *
 * <p>Two steps, and both of them already exist: {@link TableSurface} says where a seat's mat is
 * on the shared table and where a position on that mat lands, and {@link TableCamera} says
 * which part of the table is on screen. This is the pair of them held together, so that
 * drawing a card and working out what the cursor is over cannot go through different
 * arithmetic.
 *
 * <p>Mutable, unlike everything under it. The camera changes several times a second while
 * somebody pans, and threading a new one back through the screen on every scroll wheel tick
 * would be ceremony around a field.
 *
 * <p>Pure: no Minecraft in here, which is what lets the round trip between a card's position
 * and its rectangle be tested directly rather than looked at.
 */
public final class BoardGeometry implements BoardPlacement {

    private TableSurface surface;
    private TableCamera camera;
    private int width;
    private int height;

    /**
     * Whether the viewer is one of the players sitting at the far side of the surface.
     *
     * <p>Held here so that every camera this class builds carries it: a view that forgot which
     * chair it was for would be right until the first pan and wrong afterwards.
     */
    private boolean turned;

    /**
     * How much of the screen something else is sitting on, top and bottom.
     *
     * <p>The hand along the bottom and the life totals along the top. The felt runs under both
     * - a table that stopped where your cards begin would have a strip you could see across
     * and never put anything on - but framing the board has to leave them out, or the first
     * thing a player sees is a board with its near edge behind their own hand and its far
     * edge behind the score.
     */
    private int coveredAtTheTop;

    private int coveredAtTheBottom;

    /**
     * How far the opening view leans off your own board towards the middle of the table.
     *
     * <p>Enough to keep the near edge of the board opposite in view, not enough to push your
     * own zones off the bottom. A quarter of the way is the most that holds both.
     */
    private static final double LEAN_TOWARDS_THE_TABLE = 0.25;

    public BoardGeometry(List<SeatAnchor> anchors, int width, int height) {
        this(anchors, width, height, 0);
    }

    public BoardGeometry(List<SeatAnchor> anchors, int width, int height, int coveredAtTheBottom) {
        this(anchors, width, height, 0, coveredAtTheBottom);
    }

    public BoardGeometry(
            List<SeatAnchor> anchors, int width, int height,
            int coveredAtTheTop, int coveredAtTheBottom) {
        this.surface = TableSurface.forSeats(anchors);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.coveredAtTheTop = Math.max(0, coveredAtTheTop);
        this.coveredAtTheBottom = Math.max(0, coveredAtTheBottom);
        showEverything();
    }

    /**
     * Takes a new table shape without moving the view.
     *
     * <p>Somebody sitting down adds a mat and rearranges the rest. Resetting the camera then
     * would yank the table out from under whoever was mid-turn, so the view stays where it is.
     */
    public void reshape(List<SeatAnchor> anchors, int newWidth, int newHeight) {
        reshape(anchors, newWidth, newHeight, coveredAtTheTop, coveredAtTheBottom);
    }

    public void reshape(
            List<SeatAnchor> anchors, int newWidth, int newHeight,
            int newCoveredAtTheTop, int newCoveredAtTheBottom) {
        int wasVisible = visible();
        this.surface = TableSurface.forSeats(anchors);
        this.width = Math.max(1, newWidth);
        this.height = Math.max(1, newHeight);
        this.coveredAtTheTop = Math.max(0, newCoveredAtTheTop);
        this.coveredAtTheBottom = Math.max(0, newCoveredAtTheBottom);

        // A window that changed size keeps showing the same amount of table rather than the
        // same number of pixels of it: a player who had their own mat filling the screen and
        // then turned their interface scale down should have a bigger mat, not the same mat
        // adrift in a bigger window with somebody else's board coming into view above it.
        // The point the view is centred on does not move, so nothing slides out from under
        // whoever is mid-turn - which is the thing this method exists not to do.
        if (visible() != wasVisible) {
            camera = new TableCamera(
                    camera.centreX(), camera.centreY(),
                    camera.scale() * visible() / (double) wasVisible,
                    surface.width(), surface.height(), turned);
        }
    }

    @Override
    public TableSurface surface() {
        return surface;
    }

    public TableCamera camera() {
        return camera;
    }

    /**
     * The view a player starts at: their own board, as large as the window allows.
     *
     * <p>Not the whole table. Fitting everything in sounds like the friendly default and plays
     * terribly - a two-seat table squeezed into a short window draws a card twenty-seven pixels
     * wide, which is a coloured smudge, while the same card in the player's hand is ninety. You
     * cannot play on a board you cannot read, and the board you need to read is your own.
     *
     * <p>So: fitted across the width, which is the dimension a widescreen window has to spare,
     * and centred on this seat's own mat. The opponent's board is still there, just above or
     * below - and Home still steps back to show all of it.
     */
    public void focusOn(SeatId seat) {
        seenFrom(seat);
        Rect mat = surface.matOf(seat.index());
        if (mat.isEmpty()) {
            showEverything();
            return;
        }
        // Scaled so the seat's own mat fills the width, not the whole surface. Fitting the
        // surface makes a card's size depend on how many people are at the table: a four-seat
        // pod is two tables wide, so it opened at half the size, and an eight-seat cluster at
        // a quarter - which is exactly the unreadable board this method exists to avoid. A
        // playmat is a playmat whoever else is sitting down.
        // Whichever way round is binding. A mat is twice as wide as it is deep and a window
        // is not, so filling the width alone made it taller than the strip above the hand and
        // pushed its own near edge off the screen - which is the thing this is for keeping on.
        double fit = Math.min(
                width / (double) Math.max(1, mat.width()),
                visible() / (double) Math.max(1, mat.height()));
        camera = new TableCamera(mat.centreX(), mat.centreY(), fit,
                surface.width(), surface.height(), turned);

        // Leaned towards the middle of the table, so the board opposite comes into view as
        // soon as the window has room for it. Bounded by the room there actually is: a mat
        // three hundred pixels deep in a strip three hundred and fifty deep has fifty pixels
        // of slack, and leaning further than that pushes the near edge of your own board -
        // your zones, the part you use most - off the top of the screen to show somebody
        // else's. Both boards at once and a readable card are not both possible on a small
        // window, and of the two it is your own board that has to win.
        double slack = Math.max(0, visible() - mat.height() * camera.scale()) / 2.0;
        double wanted = (surface.height() / 2.0 - mat.centreY()) * LEAN_TOWARDS_THE_TABLE;
        double lean = Math.max(-slack, Math.min(slack, wanted * camera.scale())) / camera.scale();
        camera = new TableCamera(
                camera.centreX(), camera.centreY() + lean,
                camera.scale(), surface.width(), surface.height(), turned);
    }

    /**
     * Which chair this view belongs to.
     *
     * <p>Called before anything is framed, because turning the surface around changes what
     * "up" means and every rectangle after it. A spectator is not sitting anywhere, so they
     * get the table the way it is laid out, which is also what an empty seat gets.
     */
    public void seenFrom(SeatId seat) {
        boolean farSide = seat != null && surface.isTurned(seat.index());
        if (farSide != turned) {
            turned = farSide;
            camera = camera.seenFrom(farSide);
        }
    }

    /** Whether this view is being drawn from the far side of the table. */
    public boolean isTurned() {
        return turned;
    }

    /** How much of the window is board rather than furniture, which is what it is framed into. */
    private int visible() {
        return Math.max(1, height - coveredAtTheTop - coveredAtTheBottom);
    }

    /**
     * The viewport the camera is told about vertically, which is not the window.
     *
     * <p>A camera puts what it is centred on in the middle of the viewport it is handed. The
     * middle of the <em>window</em> is the wrong place: there is a status row across the top
     * and a hand across the bottom, and the middle of what is left is lower than the middle of
     * the window by half the difference. Handing over a viewport whose midpoint is the strip's
     * midpoint puts it in the right place with no arithmetic anywhere else.
     *
     * <p>Done here rather than baked into the camera as a pan, which is what it used to be.
     * A pan is a number of pixels at a scale, so a window that changed size afterwards kept
     * the old offset and the board drifted off the middle of the new strip - which is exactly
     * what a player sees the first time they change their interface scale mid-game.
     */
    private int viewportDown() {
        return Math.max(1, coveredAtTheTop + height - coveredAtTheBottom);
    }

    public void showEverything() {
        camera = TableCamera.showingAll(surface.width(), surface.height(), width, visible())
                .seenFrom(turned);
    }

    public void pan(double pixelsX, double pixelsY) {
        camera = camera.pannedBy(pixelsX, pixelsY);
    }

    public void zoom(double factor, double atX, double atY) {
        camera = camera.zoomedAt(factor, atX, atY, width, viewportDown());
    }

    // ---------------------------------------------------------- card to screen

    /** Where a card sitting at this position on this seat's mat is drawn. */
    @Override
    public Rect rectOf(SeatId seat, TablePosition position) {
        double surfaceX = surface.surfaceX(seat.index(), position.x());
        double surfaceY = surface.surfaceY(seat.index(), position.y());
        int cardWidth = cardWidth(seat);
        int cardHeight = cardHeight(seat);
        // Measured from the middle - see BoardPlacement.
        return new Rect(
                (int) Math.round(camera.toScreenX(surfaceX, width)) - cardWidth / 2,
                (int) Math.round(camera.toScreenY(surfaceY, viewportDown())) - cardHeight / 2,
                cardWidth,
                cardHeight);
    }

    /**
     * How big a card is drawn, which depends on whose mat it is on.
     *
     * <p>A mat is a fraction of the table and a card is a fraction of a mat, so a table with
     * eight seats draws smaller cards than one with two - exactly as a real one would, and for
     * the same reason.
     *
     * <p>The rule itself lives on {@link TableSurface}, because the table in the world draws
     * this same board and two answers to how big a card is would be two different boards.
     */
    @Override
    public int cardWidth(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardWidthOn(seat.index()) * camera.scale()));
    }

    @Override
    public int cardHeight(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardHeightOn(seat.index()) * camera.scale()));
    }

    /**
     * Turning the coordinates turns where things are, not which way up they are drawn.
     *
     * <p>So the viewer's own half turn goes on top of the seat's: a card on your own mat is
     * laid out facing you and then drawn from your chair, which is two half turns and no turn
     * at all, and one on the mat opposite is laid out facing away and comes out upside down.
     */
    @Override
    public int facingDegrees(SeatId seat) {
        return (surface.facingDegrees(seat.index()) + (turned ? 180 : 0)) % 360;
    }

    // ---------------------------------------------------------- screen to card

    /** The position a card's corner would have if dropped here on this seat's mat. */
    @Override
    public TablePosition positionOn(SeatId seat, double screenX, double screenY) {
        return surface.positionOn(
                seat.index(),
                camera.toTableX(screenX, width),
                camera.toTableY(screenY, viewportDown()));
    }

    /** Whose mat is under this screen point, or null for the felt between them. */
    @Override
    public SeatId seatAt(double screenX, double screenY) {
        int seat = surface.seatAt(
                camera.toTableX(screenX, width), camera.toTableY(screenY, viewportDown()));
        return seat < 0 ? null : new SeatId(seat);
    }

    /** A seat's mat, as a rectangle on screen. */
    @Override
    public Rect matRect(SeatId seat) {
        return surfaceRect(surface.matOf(seat.index()));
    }

    /** One of a seat's piles, as a rectangle on screen. */
    @Override
    public Rect pileRect(SeatId seat, int index, int count) {
        return surfaceRect(surface.pileSlot(seat.index(), index, count));
    }

    @Override
    public Rect pileGroupRect(SeatId seat, int fromIndex, int toIndex, int count) {
        return surfaceRect(surface.pileGroup(seat.index(), fromIndex, toIndex, count));
    }

    @Override
    public Rect verbRect(SeatId seat, int index, int count) {
        return surfaceRect(surface.verbSlot(seat.index(), index, count));
    }

    @Override
    public Rect verbGroupRect(SeatId seat, int count) {
        return surfaceRect(surface.verbGroup(seat.index(), count));
    }

    @Override
    public Rect pileLabelRect(SeatId seat, int index, int count) {
        return surfaceRect(surface.pileLabel(seat.index(), index, count));
    }

    @Override
    public Rect handEdgeRect(SeatId seat) {
        return surfaceRect(surface.handEdge(seat.index()));
    }

    @Override
    public Rect matDividerRect(SeatId seat, int count) {
        return surfaceRect(surface.matDivider(seat.index(), count));
    }

    @Override
    public int pileAt(SeatId seat, int count, double screenX, double screenY) {
        return surface.pileAt(seat.index(), count,
                camera.toTableX(screenX, width), camera.toTableY(screenY, viewportDown()));
    }

    /**
     * A rectangle on the surface, as a rectangle on the screen.
     *
     * <p>Both corners are mapped and then sorted, because a view seen from the far side of the
     * table maps the low corner to the high one: taking the first as the origin and
     * subtracting gave a rectangle with negative width, which draws as nothing and contains no
     * point - a mat that was simply not there for half the players.
     */
    private Rect surfaceRect(Rect onSurface) {
        if (onSurface.isEmpty()) {
            return Rect.NONE;
        }
        int oneX = (int) Math.round(camera.toScreenX(onSurface.x(), width));
        int oneY = (int) Math.round(camera.toScreenY(onSurface.y(), viewportDown()));
        int otherX = (int) Math.round(camera.toScreenX(onSurface.right(), width));
        int otherY = (int) Math.round(camera.toScreenY(onSurface.bottom(), viewportDown()));
        return new Rect(
                Math.min(oneX, otherX), Math.min(oneY, otherY),
                Math.abs(otherX - oneX), Math.abs(otherY - oneY));
    }
}
