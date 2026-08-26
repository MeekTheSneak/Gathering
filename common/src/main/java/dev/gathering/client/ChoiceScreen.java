package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Which one?"
 *
 * <p>A handful of named answers, each a button. For the questions where the answers are known
 * and few - which basic land, and whatever else turns out to be shaped like that - because
 * typing "Plains" into a box is three seconds and a spelling mistake where pressing Plains is
 * neither.
 *
 * <p>Deliberately not a text field with suggestions. The whole value of a question with six
 * answers is that it cannot be answered wrongly, and a box you can type anything into throws
 * that away to save a screen.
 *
 * <p>Client-only.
 */
public final class ChoiceScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;

    private static final int PANEL_WIDTH = 190;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;

    /** Two to a row, which fits a land's name at the width a panel wants to be. */
    private static final int ACROSS = 2;

    /** One answer: what it is called, and what happens when it is pressed. */
    public record Option(Component label, Runnable chosen) {
    }

    private final Component question;
    private final List<Option> options;

    private Rect panel = Rect.NONE;

    public ChoiceScreen(Component question, List<Option> options, Screen back) {
        super(question, back);
        this.question = question;
        this.options = List.copyOf(options);
    }

    @Override
    protected void init() {
        int rows = (options.size() + ACROSS - 1) / ACROSS;
        int height = MARGIN * 2 + ROW * 2 + GAP * 2 + rows * (ROW + GAP);
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int top = panel.y() + MARGIN + ROW;
        int width = (panel.width() - MARGIN * 2 - GAP * (ACROSS - 1)) / ACROSS;
        for (int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            addRenderableWidget(GatheringButtons.of(
                    panel.x() + MARGIN + (index % ACROSS) * (width + GAP),
                    top + (index / ACROSS) * (ROW + GAP), width, ROW,
                    option.label(), () -> choose(option)));
        }

        // A way out that is not the escape key, for the same reason every other panel here
        // has one: a screen you can only leave by a key nobody told you about is a screen
        // that has trapped you.
        int decideTop = top + rows * (ROW + GAP) + GAP;
        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, decideTop, panel.width() - MARGIN * 2, ROW,
                Component.translatable("gui.cancel"), this::onClose));
    }

    private void choose(Option option) {
        this.onClose();
        option.chosen().run();
    }

    /** The panel goes behind the buttons, not over them. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiText.drawCentred(graphics, this.font, question,
                panel.x() + panel.width() / 2, panel.y() + 5, panel.width() - MARGIN * 2, LABEL);
    }
}
