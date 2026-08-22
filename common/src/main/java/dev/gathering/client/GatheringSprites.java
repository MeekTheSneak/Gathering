package dev.gathering.client;

import dev.gathering.Gathering;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's GUI art, as real textures rather than rectangles drawn in code.
 *
 * <p>Every panel here is a PNG under {@code assets/gathering/textures/gui/sprites} with a
 * {@code .mcmeta} beside it declaring nine-slice borders. That means the look is editable
 * without touching Java - repaint the PNG and the screens change - and a resource pack can
 * reskin the whole mod. Nine-slice also means one small texture stretches to any panel size
 * without the border smearing, so screens can be laid out freely.
 *
 * <p>Colours come from the design brief's GUI note: readability-first dark felt with an
 * accent, rather than a theme inherited from anything.
 */
public final class GatheringSprites {

    /** The standard raised panel: the background of a screen or a section of one. */
    public static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "panel");

    /** A recessed well, for text areas and scrolling lists. */
    public static final ResourceLocation PANEL_INSET =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "panel_inset");

    /** Selection and hover, in the accent colour. */
    public static final ResourceLocation ROW_HIGHLIGHT =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "row_highlight");

    /** The heavy border a card or its text sits inside. */
    public static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "frame");

    /**
     * The decklist backdrop: flush to the left edge, tapering on the right.
     *
     * <p>Stretched rather than nine-sliced, because the taper is the shape. The angle is
     * baked into the PNG and repeated in {@code DeckScreenLayout.TAPER_BOTTOM}, which is what
     * the scrollbar is drawn along - a theme replacing this texture keeps the angle.
     */
    public static final ResourceLocation DECK_PANEL =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "deck_panel");

    public static final ResourceLocation SCROLL_TRACK =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "scroll_track");

    public static final ResourceLocation SCROLL_THUMB =
            ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "scroll_thumb");

    private GatheringSprites() {
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(PANEL, x, y, width, height);
    }

    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(PANEL_INSET, x, y, width, height);
    }

    public static void highlight(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(ROW_HIGHLIGHT, x, y, width, height);
    }

    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(FRAME, x, y, width, height);
    }

    public static void deckPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(DECK_PANEL, x, y, width, height);
    }

    public static void scrollTrack(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(SCROLL_TRACK, x, y, width, height);
    }

    public static void scrollThumb(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(SCROLL_THUMB, x, y, width, height);
    }
}
