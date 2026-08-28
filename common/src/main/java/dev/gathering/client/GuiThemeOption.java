package dev.gathering.client;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

/**
 * The look, as a row of the game's own video settings.
 *
 * <p>Where a setting about how the game looks goes. The table's menu can change it too, and
 * that is the discoverable place while playing - but somebody who has just installed the mod
 * and wants to look at what it offers goes to Options, and a mod whose only setting lives
 * three clicks inside a table nobody has built yet is a mod whose setting nobody finds.
 *
 * <p>Added to the list vanilla already built rather than replacing the screen, so it looks and
 * behaves like every other row: same button, same cycling, same click sound. The change lands
 * the instant it is pressed, which is why it is worth being here at all - the screen behind
 * the options screen repaints while you are still on the button.
 *
 * <p>Both loaders call this from their own screen-opened hook, which is the only part of it
 * that differs between them.
 *
 * <p>Client-only.
 */
public final class GuiThemeOption {

    /** The width vanilla gives one of the two columns of small options. */
    private static final int WIDTH = 150;

    private static final int HEIGHT = 20;

    private GuiThemeOption() {
    }

    /**
     * Puts the picker on this screen, if this screen is the video settings.
     *
     * <p>Asked about every screen that opens, so the check is here rather than in each
     * loader: two copies of one condition are two chances for the loaders to disagree about
     * where a setting lives.
     */
    public static void addTo(Screen screen) {
        if (!(screen instanceof VideoSettingsScreen)) {
            return;
        }
        OptionsList list = listOf(screen);
        if (list == null) {
            // A resource pack or another mod has rebuilt the screen. Nothing to add to, and
            // nothing worth complaining about: the setting is still in the table's menu and
            // in the config file.
            return;
        }
        list.addSmall(button(), null);
    }

    /** The row itself: every installed theme, cycled, saved as it is pressed. */
    private static AbstractWidget button() {
        List<GuiTheme> themes = GuiThemes.all();
        return CycleButton.<GuiTheme>builder(GuiTheme::name)
                .withValues(themes)
                .withInitialValue(GuiThemes.active())
                .withTooltip(theme -> net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("options.gathering.look.tip")))
                .create(0, 0, WIDTH, HEIGHT,
                        Component.translatable("options.gathering.look"),
                        (pressed, chosen) -> GuiThemes.wear(chosen));
    }

    /** The scrolling list of rows vanilla built, which is a child of the screen. */
    private static OptionsList listOf(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof OptionsList list) {
                return list;
            }
        }
        return null;
    }
}
