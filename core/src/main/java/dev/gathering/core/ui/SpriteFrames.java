package dev.gathering.core.ui;

/**
 * How thick each framed sprite's border is, in one place.
 * <p>A framed sprite is a nine-slice: a border that keeps its size and a middle that is tiled
 * to fill whatever is left. That has a smallest useful size - twice the border, plus something
 * for the middle to be - and below it the game tiles what it can and runs the corners into
 * each other. What comes out is not the picture anybody drew.
 * <p>Reported from a real session, twice over: "the top bar on in the table when using the
 * future sight theme" looks bad, and "GUI borders also scale with zoom, that makes certain
 * zones (like graveyard or exile) look really bad when zoomed out on certain themes". The top
 * bar was exactly twice the panel's border tall, so its border met itself with no middle at
 * all; a zone pulled far enough out goes the same way. Future Sight is where it shows first
 * because its border is the heaviest of the fourteen.
 * <p>So the numbers live here, where a layout can ask how much room a frame needs before it
 * decides how tall to be, and {@code tools/spritecheck.py} checks each one against every
 * theme's own metadata so the number and the art cannot drift apart.
 * <p>Pure: no Minecraft, so a layout in core can use it.
 */
public final class SpriteFrames {

    /** Panels and the recesses inside them: the widest border in the set. */
    public static final int PANEL = 8;

    /** A seat's own half of the table. */
    public static final int SEAT_MAT = 8;

    /** The line round a zone or a group of them. */
    public static final int ZONE_BORDER = 4;

    private SpriteFrames() {
    }

    /**
     * The smallest a box wearing this frame may be before the frame stops being itself.
     * <p>Twice the border for the two edges, and one more so there is a middle rather than
     * two borders meeting.
     */
    public static int smallestFor(int frame) {
        return frame <= 0 ? 0 : frame * 2 + 1;
    }
}
