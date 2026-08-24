package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "How many?"
 *
 * <p>Draw four, mill six, scry two, reveal three. Every one of those is the same question, and
 * a table that answers it with a menu of fixed amounts is a table that cannot do the fifth
 * thing anybody tries. So this asks once, properly, and hands the number back.
 *
 * <p>The common answers get a button, because the overwhelming majority of the time the number
 * is one or two and typing it is worse than pressing it. Anything else gets typed. Enter
 * confirms, so the whole thing is a number and a key.
 *
 * <p>Client-only.
 */
public final class AmountScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;

    private static final int PANEL_WIDTH = 190;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;

    /** More than anybody mills at once outside a combo that has already won. */
    private static final int MAXIMUM = 999;

    /** The answers common enough to be worth a press rather than a keystroke. */
    private static final int[] QUICK = {1, 2, 3, 5, 7, 10};

    private final Component question;
    private final int suggested;
    private final IntConsumer answer;

    private Rect panel = Rect.NONE;
    private EditBox amount;

    public AmountScreen(Component question, int suggested, IntConsumer answer, Screen back) {
        super(question, back);
        this.question = question;
        this.suggested = Math.max(1, suggested);
        this.answer = answer;
    }

    @Override
    protected void init() {
        int quickRows = (QUICK.length + 2) / 3;
        int height = MARGIN * 2 + ROW * 3 + GAP * 3 + quickRows * (ROW + GAP);
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int top = panel.y() + MARGIN + ROW;
        amount = new EditBox(this.font, panel.x() + MARGIN, top,
                panel.width() - MARGIN * 2, ROW, question);
        amount.setValue(Integer.toString(suggested));
        amount.setFilter(AmountScreen::looksLikeANumber);
        amount.setMaxLength(3);
        addRenderableWidget(amount);
        setInitialFocus(amount);

        int quickTop = top + ROW + GAP;
        int width = (panel.width() - MARGIN * 2 - GAP * 2) / 3;
        for (int index = 0; index < QUICK.length; index++) {
            int value = QUICK[index];
            addRenderableWidget(GatheringButtons.of(
                    panel.x() + MARGIN + (index % 3) * (width + GAP),
                    quickTop + (index / 3) * (ROW + GAP), width, ROW,
                    Component.literal(Integer.toString(value)), () -> confirm(value)));
        }

        // Two buttons, not one. Ok commits, so a player who has changed their mind had no
        // way out of this panel but a key nobody told them about - which is exactly what
        // "the menus make you press escape" means.
        int decideTop = quickTop + quickRows * (ROW + GAP) + GAP;
        int half = (panel.width() - MARGIN * 2 - GAP) / 2;
        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, decideTop, half, ROW,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(GatheringButtons.of(
                panel.right() - MARGIN - half, decideTop, half, ROW,
                Component.translatable("gui.ok"), this::confirmTyped));
    }

    /**
     * Whether a part-typed value is still on its way to being a number.
     *
     * <p>Empty counts, because a field you cannot clear is a field you cannot correct.
     */
    private static boolean looksLikeANumber(String typed) {
        return typed.isEmpty() || typed.chars().allMatch(Character::isDigit);
    }

    private void confirmTyped() {
        confirm(parsed());
    }

    private int parsed() {
        try {
            return Math.min(MAXIMUM, Math.max(0, Integer.parseInt(amount.getValue().trim())));
        } catch (NumberFormatException ignored) {
            return suggested;
        }
    }

    /**
     * Answers and gets out of the way.
     *
     * <p>Closes to nothing rather than back to whatever opened it: this was opened from a menu
     * that has already gone, and reinstating the table screen is the caller's business if it
     * wants one.
     */
    private void confirm(int value) {
        this.onClose();
        if (value > 0) {
            answer.accept(value);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirmTyped();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /**
     * The panel goes behind the widgets, not over them.
     *
     * <p>Drawing it in {@code render} after {@code super.render} paints it straight over every
     * button on the screen - which looks like the buttons have vanished, and is exactly the
     * bug it caused.
     */
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
