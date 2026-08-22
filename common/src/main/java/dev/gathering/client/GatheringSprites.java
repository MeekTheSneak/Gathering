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
}
