package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The three things every small question panel does, written once.
 * <p>The note prompt, the amount prompt, the text prompt and the choice panel each drew a panel
 * behind their widgets, put their question across its top, and took Enter as yes - four copies
 * of the same few lines, found by an exact-sequence scan. Four copies of a rule are four places
 * for it to drift: one of them getting Enter wrong, or drawing its panel in {@code render}
 * rather than behind the widgets (which paints straight over every button and is a bug this
 * mod has already had once), would be a panel that behaves differently from its neighbours for
 * no reason anybody chose.
 * <p>Helpers rather than a base class. The four panels differ in everything else - what they
 * hold, how they lay it out, what a confirmation means - and a common parent would have to
 * know about all of that or be bypassed. These are the parts that are genuinely the same, and
 * nothing more.
 * <p>Client-only.
 */
final class Prompts {

    /** Where the question sits below the panel's top edge. */
    private static final int QUESTION_TOP = 5;

    private Prompts() {
    }

    /**
     * Whether this key means "yes, that".
     * <p>Both Enter keys. A panel that answered only the main one would ignore anybody typing a
     * number on a keypad and then reaching for the key beside it.
     */
    static boolean confirms(int key) {
        return key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
    }

    /**
     * The panel, behind everything.
     * <p>Called from {@code renderBackground}, never from {@code render}. Drawn after
     * {@code super.render} it paints over every button on the screen - which looks exactly like
     * the buttons have vanished, and is the bug the first of these panels shipped with.
     */
    static void panel(GuiGraphics graphics, Rect panel) {
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    /** The question, centered across the top of the panel and fitted to its width. */
    static void question(GuiGraphics graphics, Font font, Component question, Rect panel,
            int margin, int color) {
        GuiText.drawCentered(graphics, font, question,
                panel.x() + panel.width() / 2, panel.y() + QUESTION_TOP,
                panel.width() - margin * 2, color);
    }

    /** A line of guidance, centered across the panel at the height the caller chose. */
    static void hint(GuiGraphics graphics, Font font, Component hint, Rect panel, int y,
            int margin, int color) {
        GuiText.drawCentered(graphics, font, hint,
                panel.x() + panel.width() / 2, y, panel.width() - margin * 2, color);
    }
}
