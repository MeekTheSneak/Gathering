package dev.gathering.client;

import dev.gathering.core.game.CardNote;
import dev.gathering.core.ui.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The pen.
 *
 * <p>What a real table does with a scrap of paper and a biro: "flying until end of turn",
 * "this is the morph", "goes back to their side at upkeep". The mod has no rules engine and
 * never will, so the way a group remembers a rule is by writing it on the card - and until
 * this existed the only way to do that was to say it out loud and hope.
 *
 * <p>One line, typed and confirmed with a key, because writing a note is a thing done in the
 * middle of somebody else's turn and it should take about as long as saying it would.
 *
 * <p>Client-only.
 */
public final class NoteScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int HINT = 0xFF8E8A82;

    private static final int PANEL_WIDTH = 230;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;

    private final Component question;
    private final String before;
    private final Consumer<String> answer;

    private Rect panel = Rect.NONE;
    private EditBox note;

    public NoteScreen(Component question, String before, Consumer<String> answer, Screen back) {
        super(question, back);
        this.question = question;
        this.before = before == null ? "" : before;
        this.answer = answer;
    }

    @Override
    protected void init() {
        int height = MARGIN * 2 + ROW * 3 + GAP * 3;
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int top = panel.y() + MARGIN + ROW;
        note = new EditBox(this.font, panel.x() + MARGIN, top,
                panel.width() - MARGIN * 2, ROW, question);
        note.setValue(before);
        note.setMaxLength(CardNote.LONGEST);
        addRenderableWidget(note);
        setInitialFocus(note);

        // Three, not two. Rubbing a note out is the other half of writing one, and a player
        // who had to select the whole line and delete it to do that would find the pen
        // easier to pick up than to put down.
        int decideTop = top + ROW + GAP * 2;
        int third = (panel.width() - MARGIN * 2 - GAP * 2) / 3;
        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, decideTop, third, ROW,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN + third + GAP, decideTop, third, ROW,
                Component.translatable("screen.gathering.note.rub_out"), () -> confirm("")));
        addRenderableWidget(GatheringButtons.of(
                panel.right() - MARGIN - third, decideTop, third, ROW,
                Component.translatable("gui.ok"), this::confirmTyped));
    }

    private void confirmTyped() {
        confirm(note.getValue());
    }

    /**
     * Sends it and gets out of the way.
     *
     * <p>Nothing is sent when the note has not changed. A note rewritten as itself would put
     * a line in the log saying somebody wrote on a card and leave undo a step to walk back
     * through, for a card that looks exactly as it did.
     */
    private void confirm(String written) {
        this.onClose();
        if (!CardNote.same(written, before)) {
            answer.accept(written);
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

    /** The panel goes behind the widgets, not over them. */
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
        GuiText.drawCentred(graphics, this.font,
                Component.translatable("screen.gathering.note.hint"),
                panel.x() + panel.width() / 2, panel.bottom() - MARGIN + 1,
                panel.width() - MARGIN * 2, HINT);
    }
}
