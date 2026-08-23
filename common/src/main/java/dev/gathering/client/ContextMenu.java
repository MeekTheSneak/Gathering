package dev.gathering.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The little menu that opens where you right-clicked.
 *
 * <p>Right-click doing one fixed thing is fine until there are two things it could sensibly
 * do, and then it is a guess about which one you meant. A menu says what the options are,
 * which matters most where the options differ by format: "make this a commander" and "move
 * this to the sideboard" are the same gesture for two different kinds of deck, and picking
 * one to be the default picks a format to be the real one.
 *
 * <p>Draws itself and answers clicks; it holds no state beyond what is on it, so a screen
 * opens one, forwards clicks to it, and drops it.
 *
 * <p>Client-only.
 */
public final class ContextMenu {

    private static final int PADDING = 4;
    private static final int ROW_HEIGHT = 12;
    private static final int MIN_WIDTH = 70;
    private static final int SCREEN_EDGE = 4;

    private static final int TEXT = 0xFFE8E4DC;
    private static final int HOVERED = 0xFFFFFFFF;
    private static final int DISABLED = 0xFF6E6A66;

    private final List<Entry> entries;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /** How many entries fit in one column before the menu has to start another. */
    private final int perColumn;

    private final int columnWidth;

    private ContextMenu(
            List<Entry> entries, int x, int y, int width, int height,
            int perColumn, int columnWidth) {
        this.entries = List.copyOf(entries);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.perColumn = Math.max(1, perColumn);
        this.columnWidth = Math.max(1, columnWidth);
    }

    /**
     * Opens a menu at a point, nudged so it stays on screen.
     *
     * <p>Flipped rather than clamped when it would run off, the way every menu does: a menu
     * that slides sideways to fit ends up under the cursor that opened it.
     */
    public static ContextMenu at(
            Font font, int pointX, int pointY, int screenWidth, int screenHeight, List<Entry> entries) {
        int columnWidth = MIN_WIDTH;
        for (Entry entry : entries) {
            columnWidth = Math.max(columnWidth, font.width(entry.label()) + PADDING * 2);
        }

        // A menu taller than the screen has nowhere to go: flipping it up runs off the top
        // instead of the bottom, and clamping it hides the rows that fall off the end. A card
        // has a lot of things you can do to it, and at a GUI scale of two on a small window
        // the list is taller than the window. So it wraps into columns, which is what a long
        // menu does everywhere else and never costs an entry.
        int room = Math.max(1, screenHeight - SCREEN_EDGE * 2 - PADDING * 2);
        int perColumn = Math.max(1, room / ROW_HEIGHT);
        int columns = Math.max(1, (entries.size() + perColumn - 1) / perColumn);
        if (columns > 1) {
            // Spread evenly rather than filling the first column and leaving a stub.
            perColumn = (entries.size() + columns - 1) / columns;
        }

        int width = columnWidth * columns;
        int height = Math.min(entries.size(), perColumn) * ROW_HEIGHT + PADDING * 2;

        int left = pointX;
        if (left + width > screenWidth - SCREEN_EDGE) {
            left = pointX - width;
        }
        int top = pointY;
        if (top + height > screenHeight - SCREEN_EDGE) {
            top = pointY - height;
        }
        return new ContextMenu(entries,
                Math.max(SCREEN_EDGE, Math.min(left, screenWidth - SCREEN_EDGE - width)),
                Math.max(SCREEN_EDGE, Math.min(top, screenHeight - SCREEN_EDGE - height)),
                width, height, perColumn, columnWidth);
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        GatheringSprites.panel(graphics, x, y, width, height);

        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            int left = x + (index / perColumn) * columnWidth;
            int row = y + PADDING + (index % perColumn) * ROW_HEIGHT;
            boolean hovered = entry.enabled() && index == indexAt(mouseX, mouseY);
            if (hovered) {
                GatheringSprites.highlight(graphics, left + 2, row, columnWidth - 4, ROW_HEIGHT);
            }
            // The row under the cursor brightens as well as lighting up, so a menu read at a
            // glance still says which line a click would take.
            int colour = entry.enabled() ? (hovered ? HOVERED : TEXT) : DISABLED;
            GuiText.draw(graphics, font, entry.label(),
                    left + PADDING, row + 2, columnWidth - PADDING * 2, colour);
        }
    }

    /**
     * Which entry a point is on, or -1.
     *
     * <p>One answer shared by the drawing and the clicking, so a row cannot light up under the
     * cursor and then run a different entry when it is clicked.
     */
    private int indexAt(int pointX, int pointY) {
        if (pointX < x || pointX >= x + width || pointY < y + PADDING) {
            return -1;
        }
        int column = (pointX - x) / columnWidth;
        int row = (pointY - y - PADDING) / ROW_HEIGHT;
        if (row < 0 || row >= perColumn) {
            return -1;
        }
        int index = column * perColumn + row;
        return index < entries.size() ? index : -1;
    }

    /**
     * Runs whatever was clicked.
     *
     * @return true if the click belonged to this menu, whether or not it did anything - a
     *         click on a disabled row is still a click on the menu and must not fall through
     *         to whatever is underneath it
     */
    public boolean mouseClicked(int mouseX, int mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        if (index >= 0) {
            Entry entry = entries.get(index);
            if (entry.enabled()) {
                // The same click a button makes. Without it there is no way to tell a menu
                // entry that ran from one that was a pixel outside the row.
                GatheringButtons.clickSound();
                entry.action().run();
            }
        }
        return true;
    }

    /** One line of the menu. */
    public record Entry(Component label, boolean enabled, Runnable action) {

        public static Entry of(Component label, Runnable action) {
            return new Entry(label, true, action);
        }

        /** Shown but not selectable, so the option's absence is visible rather than mysterious. */
        public static Entry disabled(Component label) {
            return new Entry(label, false, () -> { });
        }
    }

    /** A builder, because most callers add entries conditionally. */
    public static List<Entry> entries() {
        return new ArrayList<>();
    }
}
