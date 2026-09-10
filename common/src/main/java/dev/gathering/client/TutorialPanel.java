package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.tutorial.TutorialProgress;
import dev.gathering.core.tutorial.TutorialStep;
import dev.gathering.core.ui.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One instruction at a time, over the corner of the board.
 * <p>Deliberately small and deliberately in the way of nothing. A tutorial that darkens the
 * screen and points at things is teaching somebody to follow a highlight; this one puts a
 * sentence on the felt and gets out of the way, because the thing being taught is a key press
 * on the real board and the real board has to stay visible and usable while it happens.
 * <p>Every instruction names a key, and the key it names is the one that verb is bound to
 * right now - so somebody who has rebound draw reads their own key rather than mine. See
 * {@link Tutorial#instruction()}.
 * <p>Existing art only: the panel is the same sprite every other panel in the mod uses, and
 * the ticks beside the finished steps are text. Nothing here needs a picture drawn.
 * <p>Client-only.
 */
public final class TutorialPanel {

    /** How wide the panel is, as a fraction of the window, and what it will not go past. */
    private static final double SHARE_OF_WIDTH = 0.34;
    private static final int WIDEST = 260;
    private static final int NARROWEST = 150;

    private static final int PADDING = 6;
    private static final int GAP = 3;

    private static final int TITLE = 0xFFF3EEE4;
    private static final int STEP_NUMBER = 0xFFB6AC9C;
    private static final int INSTRUCTION = 0xFFFFFFFF;
    private static final int WHY = 0xFFC8BFAF;
    private static final int NOTE = 0xFF9C9384;

    private TutorialPanel() {
    }

    /**
     * Where the panel sits, given the window and how tall its text turned out.
     * <p>Top left, under the status strip, because that is the one corner of this board with
     * nothing in it: the hand is along the bottom, the zone column is down one side and the
     * life totals are across the top.
     */
    public static Rect at(Font font, int screenWidth, int screenHeight, int topEdge) {
        int width = Math.clamp((int) (screenWidth * SHARE_OF_WIDTH), NARROWEST, WIDEST);
        int height = heightOf(font, width);
        return new Rect(PADDING, topEdge + PADDING,
                Math.min(width, Math.max(NARROWEST, screenWidth - PADDING * 2)),
                Math.min(height, Math.max(40, screenHeight - topEdge - PADDING * 2)));
    }

    /**
     * How tall the panel needs to be for what is currently on it.
     * <p>Measured, every line of it. An earlier version counted one line for the note about
     * practice cards, which wraps to three at any ordinary GUI scale - so the buttons under
     * the panel were drawn straight over the last sentence, and the scripted run photographed
     * "take Back away" where two overlapping strings had been.
     */
    private static int heightOf(Font font, int width) {
        int room = width - PADDING * 2;
        int high = PADDING * 2;
        if (Tutorial.progress().map(TutorialProgress::isFinished).orElse(false)) {
            high += font.lineHeight + GAP * 2;
            high += lines(font, Component.translatable("tutorial.gathering.done"), room);
            return high;
        }
        // Title, then "step n of six".
        high += font.lineHeight + GAP;
        high += font.lineHeight + GAP;
        high += lines(font, Tutorial.instruction(), room) + GAP;
        high += lines(font, Tutorial.why(), room) + GAP;
        high += lines(font, Component.translatable("tutorial.gathering.practice_note"), room);
        return high;
    }

    /** How tall this text is once it has been wrapped to the room there is. */
    private static int lines(Font font, Component text, int room) {
        return GuiText.linesNeeded(font, text, room) * font.lineHeight;
    }

    /**
     * Draws it, if there is anything to draw.
     * <p>Says nothing when the tutorial is not running, so a caller may call it every frame
     * without asking first.
     */
    public static void render(GuiGraphics graphics, Font font, Rect where) {
        TutorialProgress progress = Tutorial.progress().orElse(null);
        if (progress == null || where == null || where.isEmpty()) {
            return;
        }
        GatheringSprites.draw(graphics, Element.PANEL,
                where.x(), where.y(), where.width(), where.height());

        int room = where.width() - PADDING * 2;
        int left = where.x() + PADDING;
        int y = where.y() + PADDING;

        if (progress.isFinished()) {
            GuiText.draw(graphics, font,
                    Component.translatable("tutorial.gathering.done.title"), left, y, room, TITLE);
            y += font.lineHeight + GAP * 2;
            GuiText.drawWrapped(graphics, font,
                    Component.translatable("tutorial.gathering.done"), left, y, room, INSTRUCTION);
            return;
        }

        GuiText.draw(graphics, font,
                Component.translatable("tutorial.gathering.title"), left, y, room, TITLE);
        y += font.lineHeight + GAP;

        TutorialStep step = progress.showing();
        GuiText.draw(graphics, font,
                Component.translatable("tutorial.gathering.of",
                        step.ordinal() + 1, TutorialStep.count()),
                left, y, room, STEP_NUMBER);
        y += font.lineHeight + GAP;

        Component instruction = Tutorial.instruction();
        GuiText.drawWrapped(graphics, font, instruction, left, y, room, INSTRUCTION);
        y += GuiText.linesNeeded(font, instruction, room) * font.lineHeight + GAP;

        Component why = Tutorial.why();
        GuiText.drawWrapped(graphics, font, why, left, y, room, WHY);
        y += GuiText.linesNeeded(font, why, room) * font.lineHeight + GAP;

        // Said on every step rather than once at the start, because it is the thing somebody
        // most needs to still be true when they wonder whether to keep any of this.
        GuiText.drawWrapped(graphics, font,
                Component.translatable("tutorial.gathering.practice_note"), left, y, room, NOTE);
    }
}
