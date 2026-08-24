package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A question with two answers, one of which cannot be taken back.
 *
 * <p>For the handful of things at a table that end something: conceding a game, ending a set.
 * Every other verb here is a move somebody can undo or simply do again, and putting those
 * behind a question would be asking players to confirm shuffling. These are not those.
 *
 * <p>The safe answer is on the left and is what closing the panel does, so a player who
 * arrived here by accident gets out by any of the three ways out.
 *
 * <p>Client-only.
 */
public final class ConfirmScreen extends ChildScreen {

    private static final int PANEL_WIDTH = 220;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;
    private static final int TEXT = 0xFFE8E4DC;

    private final Component question;
    private final Component detail;
    private final Component confirmLabel;
    private final Runnable confirmed;

    private Rect panel = Rect.NONE;

    public ConfirmScreen(
            Component question, Component detail, Component confirmLabel,
            Runnable confirmed, Screen back) {
        super(question, back);
        this.question = question;
        this.detail = detail;
        this.confirmLabel = confirmLabel;
        this.confirmed = confirmed;
    }

    @Override
    protected void init() {
        int lines = GuiText.linesNeeded(this.font, detail, PANEL_WIDTH - MARGIN * 2);
        int height = MARGIN * 2 + ROW + GAP + lines * (this.font.lineHeight + 1) + GAP + ROW;
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int decideTop = panel.bottom() - MARGIN - ROW;
        int half = (panel.width() - MARGIN * 2 - GAP) / 2;
        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, decideTop, half, ROW,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(GatheringButtons.of(
                panel.right() - MARGIN - half, decideTop, half, ROW,
                confirmLabel, () -> {
                    onClose();
                    confirmed.run();
                }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        GuiText.drawCentred(graphics, this.font, question,
                panel.x() + panel.width() / 2, panel.y() + MARGIN, panel.width() - MARGIN * 2, TEXT);
        GuiText.drawWrapped(graphics, this.font, detail,
                panel.x() + MARGIN, panel.y() + MARGIN + ROW,
                panel.width() - MARGIN * 2, TEXT);
    }
}
