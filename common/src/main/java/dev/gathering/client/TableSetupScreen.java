package dev.gathering.client;

import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.StartTablePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * What kind of game this is going to be.
 * <p>Two questions and a button. Which format, because a table that only knows how to run
 * Commander is a Commander mod with a table in it - and best of how many, because
 * best-of-three is not a nicety for the sixty-card formats, it is how they are played. A deck
 * with fifteen cards it never gets to use is a deck missing a quarter of itself.
 * <p>Commander answers "one" and answers it first, which is the only concession this screen
 * makes to it being the format most people are here for. Every other format defaults to three
 * because that is what a match of it is.
 * <p>The screen sends a format <em>id</em> and a length. The server looks both up against its
 * own copy of the presets, so nothing here can invent a format with a two-card minimum.
 * <p>Client-only.
 */
public final class TableSetupScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;

    private static final int PANEL_WIDTH = 260;
    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 18;
    private static final int GAP = 4;

    private final BlockPos table;

    /** Null while free play is chosen, which is not a format and has no preset. */
    private FormatPreset format = FormatPresets.defaultPreset();
    private int bestOf = MatchRules.single(FormatPresets.defaultPreset()).bestOf();

    private Rect panel = Rect.NONE;
    private int formatsHeading;
    private int lengthsHeading;

    public TableSetupScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.setup"));
        this.table = table;
    }

    @Override
    protected void init() {
        List<FormatPreset> formats = FormatPresets.all();
        int height = MARGIN * 2 + ROW_HEIGHT * 3 + GAP * 3
                + rowsFor(formats.size() + 1) * (ROW_HEIGHT + GAP)
                + (ROW_HEIGHT + GAP) * 2;
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int y = panel.y() + MARGIN + ROW_HEIGHT;
        formatsHeading = y - this.font.lineHeight - 2;
        int columns = 3;
        int buttonWidth = (panel.width() - MARGIN * 2 - GAP * (columns - 1)) / columns;

        // Free play first, because it is the shorter answer to "what kind of game" and the
        // one a table full of people who just want to put cards down wants. Without it this
        // screen could only start a game somebody would be held to, and the only way to a
        // game with no format was to close the screen and walk up to the table holding a
        // deck - a route nothing here mentions.
        addRenderableWidget(GatheringButtons.toggle(
                panel.x() + MARGIN, y, buttonWidth, ROW_HEIGHT,
                Component.translatable("screen.gathering.setup.free_play"),
                () -> format == null,
                this::chooseFreePlay));
        for (int index = 0; index < formats.size(); index++) {
            FormatPreset preset = formats.get(index);
            int column = (index + 1) % columns;
            int row = (index + 1) / columns;
            addRenderableWidget(GatheringButtons.toggle(
                    panel.x() + MARGIN + column * (buttonWidth + GAP),
                    y + row * (ROW_HEIGHT + GAP), buttonWidth, ROW_HEIGHT,
                    Component.literal(preset.displayName()),
                    () -> preset.equals(format),
                    () -> chooseFormat(preset)));
        }

        int lengthsTop = y + rowsFor(formats.size() + 1) * (ROW_HEIGHT + GAP) + ROW_HEIGHT;
        lengthsHeading = lengthsTop - this.font.lineHeight - 2;
        List<Integer> lengths = MatchRules.SUPPORTED_LENGTHS;
        int lengthWidth = (panel.width() - MARGIN * 2 - GAP * (lengths.size() - 1)) / lengths.size();
        for (int index = 0; index < lengths.size(); index++) {
            int length = lengths.get(index);
            addRenderableWidget(GatheringButtons.toggle(
                    panel.x() + MARGIN + index * (lengthWidth + GAP),
                    lengthsTop, lengthWidth, ROW_HEIGHT,
                    Component.translatable("screen.gathering.setup.best_of", length),
                    () -> bestOf == length,
                    () -> bestOf = length));
        }

        // Two buttons, because starting is the thing this screen does and there has to be a
        // way to arrive here and change your mind that is not a key nobody was told about.
        // Every other panel the table opens offers one.
        int decideTop = lengthsTop + ROW_HEIGHT + GAP * 2;
        int half = (panel.width() - MARGIN * 2 - GAP) / 2;
        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, decideTop, half, ROW_HEIGHT,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(GatheringButtons.of(
                panel.right() - MARGIN - half, decideTop, half, ROW_HEIGHT,
                Component.translatable("screen.gathering.setup.start"), this::start));
    }

    private static int rowsFor(int formats) {
        return (formats + 2) / 3;
    }

    /**
     * Picking a format also picks the length it is usually played at.
     * <p>Overwriting a choice the player has already made would be rude; this is not that.
     * Commander is a single game and everything else is a match, and a player who wants
     * something else says so afterwards. The alternative - defaulting everything to one -
     * quietly makes every sideboard in the mod decorative.
     */
    private void chooseFormat(FormatPreset preset) {
        format = preset;
        bestOf = preset.hasSideboard() ? 3 : 1;
    }

    /**
     * No format: one game, nobody's deck refused.
     * <p>A single game rather than a match, because a best-of-three with no format to
     * sideboard against is three games with a screen between them.
     */
    private void chooseFreePlay() {
        format = null;
        bestOf = 1;
    }

    private void start() {
        ClientNetworking.send(new StartTablePayload(
                table, format == null ? StartTablePayload.FREE_PLAY : format.id(), bestOf));
        this.onClose();
    }

    /** Behind the widgets. Drawn in render(), the panel covers every button on the screen. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        GuiText.drawCentered(graphics, this.font, this.title,
                panel.x() + panel.width() / 2, panel.y() + 4, panel.width() - MARGIN * 2, LABEL);
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.setup.format"),
                panel.x() + MARGIN, formatsHeading, panel.width() - MARGIN * 2, DIM);
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.setup.length"),
                panel.x() + MARGIN, lengthsHeading, panel.width() - MARGIN * 2, DIM);

        // What is currently chosen, spelled out, because eight buttons with one of them
        // selected is only legible if the selection is written down somewhere.
        Component chosen = format == null
                ? Component.translatable("screen.gathering.setup.chosen_free",
                        FormatPresets.defaultPreset().startingLife())
                : Component.translatable("screen.gathering.setup.chosen",
                        format.displayName(), bestOf, format.startingLife());
        GuiText.drawCentered(graphics, this.font, chosen,
                panel.x() + panel.width() / 2, panel.bottom() - 12, panel.width() - MARGIN * 2, DIM);
        chosenSaid = chosen.getString();
    }

    /**
     * What the line spelling out the chosen game said last frame. For the scripted harness.
     * <p>Recorded while drawing rather than worked out from the fields, because the fields
     * are what the buttons set and a check that reads them back is a check that the button
     * assigned a variable - which it plainly did. What is worth knowing is that the screen
     * then told the player, since eight buttons with one of them chosen are only legible if
     * the choice is written down.
     */
    String chosenSaid() {
        return chosenSaid;
    }

    private String chosenSaid = "";

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // The table is behind this and worth seeing; a blur makes it look like a pause menu.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
