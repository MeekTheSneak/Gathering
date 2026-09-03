package dev.gathering.core.ui;

/**
 * How small a framed box may be before its frame stops being a frame.
 * <p>A framed sprite is a nine-slice: a border that keeps its size and a middle that is tiled
 * to fill whatever is left. That has a smallest useful size - its two edges, plus something
 * for the middle to be - and below it the game tiles what it can and runs the corners into
 * each other. What comes out is not the picture anybody drew.
 * <p>Reported from a real session, twice over: "the top bar on in the table when using the
 * future sight theme" looks bad, and "GUI borders also scale with zoom, that makes certain
 * zones (like graveyard or exile) look really bad when zoomed out on certain themes". The top
 * bar was exactly twice the panel's border tall, so its border met itself with no middle at
 * all; a zone pulled far enough out goes the same way.
 * <p>Only the arithmetic lives here. How thick any particular border is belongs to the art,
 * and the art is a resource pack's to change: four of the fourteen shipped looks draw a panel
 * at sixteen pixels where the other ten draw it at eight, and a number written down in the
 * mod would have been wrong for one of those groups whichever way it was written. So the
 * client reads each sprite's real border off the sprite as it draws it, and this says what
 * that number means.
 * <p>Pure: no Minecraft, so a layout in core can use it.
 */
public final class SpriteFrames {

    /**
     * The smallest a box may be for a border of these two thicknesses to be itself.
     * <p>Both edges, and a middle no smaller than the wider of them. The game itself only
     * refuses a nine-slice with no middle at all, and that is not enough: a card at the far
     * end of the zoom is twenty-four pixels tall and about seventeen across, so a border of
     * eight leaves a middle one pixel wide. What that draws is a solid block of border with a
     * hairline of picture down the middle of it, which is what "look really bad when zoomed
     * out" was.
     * <p>Below this the sprite is squashed whole instead, which is the picture somebody
     * painted, made small - and a frame is never more than two thirds border.
     * <p>Two thicknesses because a nine-slice's border need not be square: the art declares
     * left, top, right and bottom separately, and the client asks this once for each axis.
     */
    public static int smallestFor(int nearEdge, int farEdge) {
        int edges = Math.max(0, nearEdge) + Math.max(0, farEdge);
        return edges <= 0 ? 0 : edges + Math.max(nearEdge, farEdge);
    }

    /** The same for a border the same thickness all the way round, which most art is. */
    public static int smallestFor(int frame) {
        return smallestFor(frame, frame);
    }

    private SpriteFrames() {
    }
}
