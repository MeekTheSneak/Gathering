package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "What is it called?"
 *
 * <p>One field and a button. The same shape as {@link AmountScreen} and for the same reason:
 * the alternative to asking is a menu of the five answers somebody thought of, and the sixth
 * thing anybody tries is not on it.
 *
 * <p>Answers nothing when the field is empty, so a stray press does not send a lookup for the
 * empty string.
 *
 * <p>Client-only.
 */
public final class TextPromptScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;

    private static final int PANEL_WIDTH = 230;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 6;

    private final Component question;
    private final Component hint;
    private final int maxLength;
    private final Consumer<String> answer;

    private Rect panel = Rect.NONE;
    private EditBox field;

    public TextPromptScreen(Component question, Component hint, int maxLength, Consumer<String> answer) {
        super(question);
        this.question = question;
        this.hint = hint;
        this.maxLength = maxLength;
        this.answer = answer;
    }

    @Override
    protected void init() {
        int height = MARGIN * 2 + ROW * 3 + GAP * 2;
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int top = panel.y() + MARGIN + ROW;
        field = new EditBox(this.font, panel.x() + MARGIN, top,
                panel.width() - MARGIN * 2, ROW, question);
        field.setMaxLength(maxLength);
        field.setHint(hint);
        addRenderableWidget(field);
        setInitialFocus(field);

        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, top + ROW + GAP, panel.width() - MARGIN * 2, ROW,
                Component.translatable("gui.ok"), this::confirm));
    }

    private void confirm() {
        String typed = field.getValue().trim();
        this.onClose();
        if (!typed.isEmpty()) {
            answer.accept(typed);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
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
        GuiText.drawCentred(graphics, this.font, question,
                panel.x() + panel.width() / 2, panel.y() + 5, panel.width() - MARGIN * 2, LABEL);
        GuiText.drawCentred(graphics, this.font, hint,
                panel.x() + panel.width() / 2, panel.bottom() - 11, panel.width() - MARGIN * 2, DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
