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
     * The smallest a box wearing a border of this thickness may be.
     * <p>Both edges, and a middle no smaller than one of them. The game itself only refuses a
     * nine-slice with no middle at all, and that is not enough: a card at the far end of the
     * zoom is twenty-four pixels tall and about seventeen across, so a border of eight leaves
     * a middle one pixel wide. What that draws is a solid block of border with a hairline of
     * picture down the middle of it, which is what "look really bad when zoomed out" was.
     * <p>Below this the sprite is squashed whole instead, which is the picture somebody
     * painted, made small - and a frame is never more than two thirds border.
     */
    public static int smallestFor(int frame) {
        return frame <= 0 ? 0 : frame * 3;
    }

    /**
     * What a layout assumes a panel's border needs, having no way to ask.
     * <p>The strip along the top of the table is laid out in core, where there is no theme to
     * ask and no atlas to read. The client passes in the real number for the look somebody is
     * wearing; this is what everything else gets - the preview renderer, the tests, and the
     * one frame after a resource reload when the atlas has not been stitched yet.
     * <p>What the ten looks that draw a panel border at eight pixels need, which is also room
     * enough for the writing that sits in it. The four that draw it at sixteen ask for more
     * and get it.
     */
    public static final int ROOMY_ENOUGH = 24;

    private SpriteFrames() {
    }
}
