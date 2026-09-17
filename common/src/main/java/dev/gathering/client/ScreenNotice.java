package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.ui.NoticeLine;
import dev.gathering.core.ui.Rect;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The mod's one voice for an answer the player needs now.
 * <p>Reported as "any pop-ups or info that is given to you while in a menu needs to be
 * presented in a way you can see it even with the menu open" - the example being the practice
 * button on a Scorekeeper's Desk, which answers "hold a deck" over the hotbar, underneath the
 * screen the button is on. The answer was always sent; it was drawn where the screen covers.
 * <p>So the choice is made here, once, at the moment of speaking: with a screen open the line
 * is drawn on the screen, and with no screen open it goes over the hotbar exactly as it did.
 * Nothing about the second case changes, which is why every notice can be routed through this
 * rather than only the ones somebody remembered were said from a screen.
 * <p>One line at a time. These are answers to a click, not a log: a second answer replaces the
 * first, because the first has been overtaken by whatever the player did next. The log is the
 * chat window, and what belongs there still goes there.
 * <p>Client-only.
 */
public final class ScreenNotice {

    /** The writing, in the color the mod's own headings are in. */
    private static final int TEXT = 0x00E0B15A;

    /**
     * How far the notice sits above everything else drawn on the screen.
     * <p>A slot's item is drawn at depth 150 and the inspect panel sits with the tooltips at
     * 400; a notice drawn at the ordinary depth would come out behind both, which for the one
     * thing on the screen that has to be read is exactly wrong.
     */
    private static final float OVER_EVERYTHING = 500f;

    private static Component line;
    private static long shownAt;

    private ScreenNotice() {
    }

    /**
     * Says one thing to this player, where they are looking.
     * <p>Safe from any client-side code: with no player and no screen there is nobody to tell
     * and it does nothing.
     */
    public static void tell(Component what) {
        if (what == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null) {
            if (client.player != null) {
                client.player.displayClientMessage(what, true);
            }
            return;
        }
        line = what;
        shownAt = Util.getMillis();
    }

    /**
     * Draws it over whatever is on the screen.
     * <p>Called from each loader's screen hook and its HUD hook, so a notice said inside a
     * screen carries on fading where the player can see it when they close the screen.
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Component said = line;
        if (said == null) {
            return;
        }
        int alpha = NoticeLine.alphaAt(shownAt, Util.getMillis());
        if (alpha <= 0) {
            line = null;
            return;
        }
        Font font = Minecraft.getInstance().font;
        int room = NoticeLine.roomForWriting(screenWidth);
        Rect box = NoticeLine.placeIn(
                screenWidth, screenHeight, GuiText.width(font, said, room), font.lineHeight);
        if (box.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, OVER_EVERYTHING);
        GatheringSprites.draw(graphics, Element.PANEL,
                box.x(), box.y(), box.width(), box.height(), (alpha << 24) | 0xFFFFFF);
        GuiText.drawCentered(graphics, font, said,
                (int) Math.round(box.centerX()), box.y() + NoticeLine.PADDING,
                box.width() - NoticeLine.PADDING * 2, (alpha << 24) | TEXT);
        graphics.pose().popPose();
    }

    /**
     * Drops whatever is up, for a server changing underneath it.
     * <p>Named {@code clear} because that is the name {@code tools/statecheck.py} looks for. A
     * refusal from the server we have just left is not news on the next one.
     */
    public static void clear() {
        line = null;
        shownAt = 0;
    }
}
