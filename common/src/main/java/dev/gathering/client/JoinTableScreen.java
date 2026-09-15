package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.network.JoinTableAnswerPayload;
import dev.gathering.network.JoinTablePromptPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Sat down at a seat of a game already on: join it, or watch.
 * <p>Two answers of equal weight, because neither is the careful one - watching takes nothing and joining
 * takes a seat nobody else is in. Closing the panel watches: the player is in the chair either way, and
 * the board is what they sat down to see.
 * <p>Client-only.
 */
public final class JoinTableScreen extends Screen {

    private static final int PANEL_WIDTH = 220;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;
    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;

    private final BlockPos table;
    private boolean answered;
    private Rect panel = Rect.NONE;

    private JoinTableScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.join"));
        this.table = table;
    }

    public static void accept(JoinTablePromptPayload prompt) {
        Minecraft.getInstance().setScreen(new JoinTableScreen(prompt.table()));
    }

    @Override
    protected void init() {
        int width = Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
        Component note = Component.translatable("screen.gathering.join.note");
        int lines = GuiText.linesNeeded(this.font, note, width - MARGIN * 2);
        int height = MARGIN * 2 + this.font.lineHeight + GAP + lines * (this.font.lineHeight + 1) + GAP * 2 + ROW;
        panel = new Rect((this.width - width) / 2, Math.max(4, (this.height - height) / 2), width,
                Math.min(height, this.height - 8));
        int decideTop = panel.bottom() - MARGIN - ROW;
        int half = (panel.width() - MARGIN * 2 - GAP) / 2;
        addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, decideTop, half, ROW,
                Component.translatable("screen.gathering.join.watch"), () -> answer(false)));
        addRenderableWidget(GatheringButtons.of(panel.right() - MARGIN - half, decideTop, half, ROW,
                Component.translatable("screen.gathering.join.join"), () -> answer(true)));
    }

    private void answer(boolean join) {
        answered = true;
        ClientNetworking.send(new JoinTableAnswerPayload(table, join));
        super.onClose();
    }

    /** Closing it any other way watches. */
    @Override
    public void onClose() {
        if (!answered) {
            answer(false);
            return;
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Prompts.panel(graphics, panel);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + MARGIN,
                panel.width() - MARGIN * 2, TEXT);
        GuiText.drawWrappedCentered(graphics, this.font, Component.translatable("screen.gathering.join.note"),
                panel.x() + panel.width() / 2, panel.y() + MARGIN + this.font.lineHeight + GAP,
                panel.width() - MARGIN * 2, DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The table this asks about, for the scripted client. */
    BlockPos table() {
        return table;
    }
}
