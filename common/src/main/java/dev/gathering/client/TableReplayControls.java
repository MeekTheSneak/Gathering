package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.ReplayStrip;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A replay's transport: its four buttons, its bar, the keys that drive it, and drawing them.
 * <p>Where they are is {@link ReplayStrip}'s, so drawing and pointing agree by construction.
 * What is not here stays where it was: the screen keeps panning, the log and key panels and
 * closing; {@link ClientReplay} keeps requests, frames, playback timing and cancellation.
 * <p>Client thread only.
 */
final class TableReplayControls {
    private static final int LABEL = 0xFFE8E4DC;
    /** Whether the bar was grabbed, so a drag along it keeps scrubbing until released. */
    private boolean scrubbing;
    private List<Component> tooltip = List.of();

    /**
     * The transport laid along this strip, with room for "Replay 128 / 340" measured rather
     * than guessed so the bar never runs under it.
     */
    static ReplayStrip layout(Rect area, Font font) {
        int countWidth = font.width(Component.translatable(
                "screen.gathering.replay.at", "0000", "0000")) + 4;
        return new ReplayStrip(area, countWidth);
    }

    void click(ReplayStrip transport, int x, int y) {
        switch (transport.at(x, y)) {
            case START -> ClientReplay.scrubTo(0);
            case BACK -> ClientReplay.nudge(-1);
            case PLAY_PAUSE -> ClientReplay.playPause();
            case ON -> ClientReplay.nudge(1);
            case BAR -> {
                scrubbing = true;
                drag(transport, x);
            }
            case NOTHING -> {
            }
        }
    }

    boolean dragging() {
        return scrubbing;
    }

    void drag(ReplayStrip transport, int x) {
        if (scrubbing) {
            ClientReplay.scrubTo(transport.stepUnder(x, ClientReplay.steps()));
        }
    }

    boolean release() {
        boolean handled = scrubbing;
        scrubbing = false;
        return handled;
    }

    boolean keyPressed(int key, int scanCode) {
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE -> ClientReplay.playPause();
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> ClientReplay.nudge(-1);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> ClientReplay.nudge(1);
            // keycheck: HOME seeks a recording. The TableShortcuts framing verb is not
            // offered while watching; this preserves the transport's existing fixed keys.
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> ClientReplay.scrubTo(0);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> ClientReplay.scrubTo(ClientReplay.steps());
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void panel(GuiGraphics graphics, Rect where) {
        if (!where.isEmpty()) {
            GatheringSprites.panel(graphics, where.x(), where.y(), where.width(), where.height());
        }
    }

    List<Component> render(GuiGraphics graphics, Font font, ReplayStrip transport, int cursorX, int cursorY) {
        Rect strip = transport.strip();
        tooltip = List.of();
        if (strip.isEmpty()) {
            return tooltip;
        }
        panel(graphics, strip);

        drawScrubButton(graphics, font, cursorX, cursorY, transport.button(0), "|<", "start");
        drawScrubButton(graphics, font, cursorX, cursorY, transport.button(1), "<<", "back");
        drawScrubButton(graphics, font, cursorX, cursorY, transport.button(2),
                ClientReplay.playing() ? "||" : ">",
                ClientReplay.playing() ? "pause" : "play");
        drawScrubButton(graphics, font, cursorX, cursorY, transport.button(3), ">>", "on");

        Rect bar = transport.bar();
        // The theme's scrollbar channel and thumb, which are sliced thin enough for a ruler: painted
        // colors here were three things on the table no theme could change.
        GatheringSprites.scrollTrack(graphics, bar.x(), bar.y(), bar.width(), bar.height());
        int steps = ClientReplay.steps();
        int filled = transport.filled(ClientReplay.step(), steps);
        if (filled >= 4) {
            GatheringSprites.scrollThumb(graphics, bar.x(), bar.y(), filled, bar.height());
        }
        // The head, so a paused replay says where it is even when the fill is a hairline.
        // Six wide, not four: the thumb's art is painted eight square with a two pixel border and
        // wants six before its ends meet in the middle.
        int head = bar.x() + Math.clamp(filled - 3, 0, Math.max(0, bar.width() - 6));
        GatheringSprites.scrollThumb(graphics, head, bar.y() - 2, 6, bar.height() + 4);

        GuiText.draw(graphics, font,
                Component.translatable("screen.gathering.replay.at",
                        String.valueOf(ClientReplay.step()), String.valueOf(steps)),
                transport.countX(),
                strip.y() + (strip.height() - font.lineHeight) / 2,
                transport.countWidth(), LABEL);
        return tooltip;
    }

    /**
     * One transport button, and what it says when the cursor rests on it.
     * <p>Four arrows eighteen pixels wide can only be told apart by somebody who already
     * knows what they do, and the moment anybody wants to know is the moment they are already
     * pointing at one. So the name and the key are on the tooltip, exactly as the mat buttons
     * carry theirs.
     */
    private void drawScrubButton(GuiGraphics graphics, Font font, int cursorX, int cursorY,
            Rect where, String face, String name) {
        panel(graphics, where);
        GuiText.drawCentered(graphics, font, Component.literal(face),
                (int) where.centerX(), where.y() + (where.height() - font.lineHeight) / 2,
                where.width(), LABEL);
        if (where.contains(cursorX, cursorY)) {
            tooltip = List.of(
                    Component.translatable("screen.gathering.replay." + name),
                    Component.translatable("screen.gathering.replay." + name + ".key")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

}
