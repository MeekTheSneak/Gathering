package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.SettingsLayout;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mod's own settings, where somebody can actually reach them.
 * <p>Thirteen preferences existed before this screen did, and the only way to change any of
 * them was to close the game, find a text file and edit it by hand. That is not a setting a
 * player has; it is a setting an administrator has. It matters most for exactly the ones that
 * are about being able to play at all - the text size, the control size, reduced motion,
 * whether reading a card means holding a key down - because somebody who needs those needs
 * them before they can comfortably use anything else.
 * <p>Buttons rather than sliders, and every row is one. A button takes keyboard focus in
 * order, says its own name to a narrator and can be pressed with Enter; a slider dragged with
 * a mouse is none of those things. Pressing a row steps it to the next value and wraps at the
 * end, which is how the game's own options screens behave, so it is a control people already
 * know.
 * <p>Changing the sound volume plays a sound, because a volume you cannot hear while you are
 * setting it is a volume you set twice.
 * <p>Client-only.
 */
public final class SettingsScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;

    /**
     * What each row steps through, asked of the settings rather than written down here.
     * <p>{@link ClientSettings#offeredSteps()} owns these because it owns the bounds that
     * clamp them - see the note there about a row that offered a value the setter refused.
     */
    private static List<Integer> stepsFor(String setting) {
        return ClientSettings.offeredSteps().getOrDefault(setting, List.of());
    }

    private SettingsLayout layout;

    /** Whether a row changed and the panel has to be laid out again. See the press above. */
    private boolean rebuildWanted;

    public SettingsScreen(Screen back) {
        super(Component.translatable("screen.gathering.settings"), back);
    }

    /**
     * One row: what it is called, what it says now, and what pressing it does.
     * <p>A record rather than three parallel lists, because the three have to stay in step and
     * a row whose label belonged to the setting above it would be a settings screen that
     * changes the wrong thing.
     */
    private record Row(String labelKey, java.util.function.Supplier<Component> says, Runnable step) {
    }

    /** The next value along, wrapping at the end. The whole of what a press does. */
    private static int next(List<Integer> values, int current) {
        int at = values.indexOf(current);
        if (at < 0) {
            // A value hand-edited into the file that is not one of the steps. Pressing moves
            // to the first step rather than refusing, so the row is never stuck.
            return values.getFirst();
        }
        return values.get((at + 1) % values.size());
    }

    private static Component percent(int value) {
        return Component.translatable("screen.gathering.settings.percent", value);
    }

    private static Component onOrOff(boolean on) {
        return Component.translatable(on
                ? "screen.gathering.settings.on"
                : "screen.gathering.settings.off");
    }

    private List<Row> rows() {
        return List.of(
                new Row("text_scale",
                        () -> percent(ClientSettings.textScale()),
                        () -> ClientSettings.textScale(next(stepsFor("text_scale"), ClientSettings.textScale()))),
                new Row("control_scale",
                        () -> percent(ClientSettings.controlScale()),
                        () -> ClientSettings.controlScale(
                                next(stepsFor("control_scale"), ClientSettings.controlScale()))),
                new Row("reduced_motion",
                        () -> onOrOff(ClientSettings.reducedMotion()),
                        () -> ClientSettings.reducedMotion(!ClientSettings.reducedMotion())),
                new Row("effect_intensity",
                        () -> percent(ClientSettings.effectIntensity()),
                        () -> ClientSettings.effectIntensity(
                                next(stepsFor("effect_intensity"), ClientSettings.effectIntensity()))),
                new Row("hold_to_inspect",
                        () -> onOrOff(ClientSettings.holdToInspect()),
                        () -> ClientSettings.holdToInspect(!ClientSettings.holdToInspect())),
                new Row("table_sounds",
                        () -> onOrOff(ClientSettings.tableSounds()),
                        () -> {
                            ClientSettings.tableSounds(!ClientSettings.tableSounds());
                            TableSounds.preview();
                        }),
                new Row("sound_volume",
                        () -> percent(ClientSettings.tableSoundVolume()),
                        () -> {
                            ClientSettings.tableSoundVolume(
                                    next(stepsFor("sound_volume"), ClientSettings.tableSoundVolume()));
                            // Heard at the moment it is set, or it is set twice.
                            TableSounds.preview();
                        }),
                new Row("turn_notification",
                        () -> onOrOff(ClientSettings.turnNotification()),
                        () -> ClientSettings.turnNotification(!ClientSettings.turnNotification())),
                new Row("waiting_after",
                        () -> Component.translatable("screen.gathering.settings.milliseconds",
                                ClientSettings.waitingAfterMillis()),
                        () -> ClientSettings.waitingAfterMillis(
                                next(stepsFor("waiting_after"), ClientSettings.waitingAfterMillis()))));
    }

    @Override
    protected void init() {
        List<Row> rows = rows();
        layout = SettingsLayout.of(this.width, this.height, rows.size(),
                ClientSettings.controlScale());

        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            Rect where = layout.row(index);
            addRenderableWidget(GatheringButtons.of(
                    where.x(), where.y(), where.width(), where.height(),
                    labelFor(row),
                    () -> {
                        row.step().run();
                        // Rebuilt, because the control size is one of the things on this
                        // screen: turning it up has to move these very rows, or the setting
                        // would be the one thing it does not apply to. And rebuilt on the next
                        // tick rather than here, because here is inside the press - the click
                        // is still being handed along the list of widgets this would replace,
                        // and every other screen in the mod that rebuilds does it from a tick
                        // or a scroll for the same reason. One tick is fifty milliseconds and
                        // nobody can see it.
                        rebuildWanted = true;
                    }));
        }

        Rect out = layout.wayOut();
        addRenderableWidget(GatheringButtons.of(out.x(), out.y(), out.width(), out.height(),
                Component.translatable("gui.done"), this::onClose));
    }

    /** "Text size: 125%", as one line, so a narrator reads the setting and its value together. */
    private static Component labelFor(Row row) {
        return Component.translatable("screen.gathering.settings.row",
                Component.translatable("screen.gathering.settings." + row.labelKey()),
                row.says().get());
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuildWanted) {
            rebuildWanted = false;
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (layout != null) {
            GatheringSprites.panel(graphics, layout.panel().x(), layout.panel().y(),
                    layout.panel().width(), layout.panel().height());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (layout == null) {
            return;
        }
        Rect title = layout.title();
        GuiText.drawCentered(graphics, this.font, this.title,
                (int) Math.round(title.centerX()), title.y() + 2, title.width(), LABEL);
    }
}
