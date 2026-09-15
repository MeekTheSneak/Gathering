package dev.gathering.client;

import dev.gathering.core.ui.ListScroll;
import dev.gathering.core.ui.Rect;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A scrollbar down the side of a list: its track, and a thumb as tall as the share showing and as far
 * down as the list is scrolled. The look's own art for both; the arithmetic is {@link ListScroll}'s.
 * <p>Client-only.
 */
final class ListScrollbar {

    private ListScrollbar() {
    }

    static void draw(GuiGraphics graphics, Rect track, int first, int showing, int total) {
        if (track.isEmpty()) {
            return;
        }
        GatheringSprites.scrollTrack(graphics, track.x(), track.y(), track.width(), track.height());
        int[] thumb = ListScroll.thumb(track.y(), track.height(), first, showing, total);
        GatheringSprites.scrollThumb(graphics, track.x(), thumb[0], track.width(), thumb[1]);
    }
}
