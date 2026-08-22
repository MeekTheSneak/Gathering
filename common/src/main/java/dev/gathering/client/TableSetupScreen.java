package dev.gathering.client;

import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.StartTablePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * What kind of game this is going to be.
 *
 * <p>Two questions and a button. Which format, because a table that only knows how to run
 * Commander is a Commander mod with a table in it - and best of how many, because
 * best-of-three is not a nicety for the sixty-card formats, it is how they are played. A deck
 * with fifteen cards it never gets to use is a deck missing a quarter of itself.
 *
 * <p>Commander answers "one" and answers it first, which is the only concession this screen
 * makes to it being the format most people are here for. Every other format defaults to three
 * because that is what a match of it is.
 *
 * <p>The screen sends a format <em>id</em> and a length. The server looks both up against its
 * own copy of the presets, so nothing here can invent a format with a two-card minimum.
 *
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

    private FormatPreset format = FormatPresets.defaultPreset();
    private int bestOf = MatchRules.single(FormatPresets.defaultPreset()).bestOf();

    private Rect panel = Rect.NONE;

    public TableSetupScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.setup"));
        this.table = table;
    }

    @Override
    protected void init() {
        List<FormatPreset> formats = FormatPresets.all();
        int height = MARGIN * 2 + ROW_HEIGHT * 3 + GAP * 3
                + rowsFor(formats.size()) * (ROW_HEIGHT + GAP)
                + (ROW_HEIGHT + GAP) * 2;
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int y = panel.y() + MARGIN + ROW_HEIGHT;
        int columns = 3;
        int buttonWidth = (panel.width() - MARGIN * 2 - GAP * (columns - 1)) / columns;

        for (int index = 0; index < formats.size(); index++) {
            FormatPreset preset = formats.get(index);
            int column = index % columns;
            int row = index / columns;
            addRenderableWidget(Button.builder(
                            Component.literal(preset.displayName()), ignored -> chooseFormat(preset))
                    .bounds(panel.x() + MARGIN + column * (buttonWidth + GAP),
                            y + row * (ROW_HEIGHT + GAP), buttonWidth, ROW_HEIGHT)
                    .build());
        }

        int lengthsTop = y + rowsFor(formats.size()) * (ROW_HEIGHT + GAP) + ROW_HEIGHT;
        List<Integer> lengths = MatchRules.SUPPORTED_LENGTHS;
        int lengthWidth = (panel.width() - MARGIN * 2 - GAP * (lengths.size() - 1)) / lengths.size();
        for (int index = 0; index < lengths.size(); index++) {
            int length = lengths.get(index);
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.gathering.setup.best_of", length),
                            ignored -> bestOf = length)
                    .bounds(panel.x() + MARGIN + index * (lengthWidth + GAP),
                            lengthsTop, lengthWidth, ROW_HEIGHT)
                    .build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.gathering.setup.start"), ignored -> start())
                .bounds(panel.x() + MARGIN, lengthsTop + ROW_HEIGHT + GAP * 2,
                        panel.width() - MARGIN * 2, ROW_HEIGHT)
                .build());
    }

    private static int rowsFor(int formats) {
        return (formats + 2) / 3;
    }

    /**
     * Picking a format also picks the length it is usually played at.
     *
     * <p>Overwriting a choice the player has already made would be rude; this is not that.
     * Commander is a single game and everything else is a match, and a player who wants
     * something else says so afterwards. The alternative - defaulting everything to one -
     * quietly makes every sideboard in the mod decorative.
     */
    private void chooseFormat(FormatPreset preset) {
        format = preset;
        bestOf = preset.hasSideboard() ? 3 : 1;
    }

    private void start() {
        ClientNetworking.send(new StartTablePayload(table, format.id(), bestOf));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());

        GuiText.drawCentred(graphics, this.font, this.title,
                panel.x() + panel.width() / 2, panel.y() + 4, panel.width() - MARGIN * 2, LABEL);

        // What is currently chosen, spelled out, because eight buttons with one of them
        // selected is only legible if the selection is written down somewhere.
        GuiText.drawCentred(graphics, this.font,
                Component.translatable("screen.gathering.setup.chosen",
                        format.displayName(), bestOf, format.startingLife()),
                panel.x() + panel.width() / 2, panel.bottom() - 12, panel.width() - MARGIN * 2, DIM);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // The table is behind this and worth seeing; a blur makes it look like a pause menu.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
