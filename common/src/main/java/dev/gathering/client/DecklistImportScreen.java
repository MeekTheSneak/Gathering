package dev.gathering.client;

import dev.gathering.network.ImportDecklistPayload;
import dev.gathering.network.ImportResultPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Paste a decklist, get a deck.
 *
 * <p>The front door of the mod, and deliberately dull: a box you can paste a hundred lines
 * into, a button, and an honest account of what came back. A list that half worked shows
 * which lines to fix at their line numbers, and still hands over the deck.
 *
 * <p>Client-only.
 */
public final class DecklistImportScreen extends Screen {

    private static final int MARGIN = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 120;
    private static final int GAP = 6;
    private static final int PROBLEM_COLOUR = 0xFFE08A5A;
    private static final int STATUS_COLOUR = 0xFFB8B4AC;
    private static final int MAX_VISIBLE_PROBLEMS = 8;

    private MultiLineEditBox editor;
    private Button importButton;

    private Component status = Component.empty();
    private List<Component> problems = List.of();
    private boolean waiting;

    public DecklistImportScreen() {
        super(Component.translatable("screen.gathering.import"));
    }

    @Override
    protected void init() {
        int boxWidth = Math.min(this.width - MARGIN * 2, 420);
        int left = (this.width - boxWidth) / 2;
        int top = MARGIN + 24;
        int boxHeight = this.height - top - MARGIN - BUTTON_HEIGHT - GAP * 2 - problemAreaHeight();

        String previous = this.editor == null ? "" : this.editor.getValue();

        this.editor = new MultiLineEditBox(
                this.font,
                left,
                top,
                boxWidth,
                Math.max(BUTTON_HEIGHT * 2, boxHeight),
                Component.translatable("screen.gathering.import.placeholder"),
                Component.translatable("screen.gathering.import"));
        this.editor.setValue(previous);
        this.addRenderableWidget(this.editor);

        int buttonTop = this.height - MARGIN - BUTTON_HEIGHT;
        this.importButton = Button.builder(
                        Component.translatable("screen.gathering.import.confirm"), button -> submit())
                .bounds(left + boxWidth - BUTTON_WIDTH, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.importButton);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(left, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void submit() {
        String decklist = this.editor.getValue();
        if (decklist.isBlank()) {
            this.status = Component.translatable("screen.gathering.import.empty");
            return;
        }
        if (decklist.length() > ImportDecklistPayload.MAX_LENGTH) {
            this.status = Component.translatable(
                    "screen.gathering.import.too_long", ImportDecklistPayload.MAX_LENGTH);
            return;
        }

        this.waiting = true;
        this.problems = List.of();
        this.status = Component.translatable("screen.gathering.import.working");
        this.importButton.active = false;

        ClientNetworking.send(new ImportDecklistPayload(decklist));
    }

    /** Called from the payload handler when the server reports back. */
    public void onResult(ImportResultPayload result) {
        this.waiting = false;
        this.importButton.active = true;

        this.status = result.isClean()
                ? Component.translatable("screen.gathering.import.done", result.cardCount())
                : Component.translatable("screen.gathering.import.done_with_problems",
                        result.cardCount(), result.problems().size());

        List<Component> lines = new ArrayList<>();
        for (String problem : result.problems().stream().limit(MAX_VISIBLE_PROBLEMS).toList()) {
            lines.add(Component.literal(problem));
        }
        if (result.problems().size() > MAX_VISIBLE_PROBLEMS) {
            lines.add(Component.translatable(
                    "screen.gathering.import.more_problems", result.problems().size() - MAX_VISIBLE_PROBLEMS));
        }
        this.problems = List.copyOf(lines);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, MARGIN, 0xFFFFFF);

        int line = this.height - MARGIN - BUTTON_HEIGHT - GAP - problemAreaHeight();
        if (!this.status.getString().isEmpty()) {
            graphics.drawString(this.font, this.status, leftEdge(), line, STATUS_COLOUR, false);
        }
        line += this.font.lineHeight + 2;

        for (Component problem : this.problems) {
            for (FormattedCharSequence wrapped : this.font.split(problem, boxWidth())) {
                graphics.drawString(this.font, wrapped, leftEdge(), line, PROBLEM_COLOUR, false);
                line += this.font.lineHeight;
            }
        }
    }

    private int problemAreaHeight() {
        return (MAX_VISIBLE_PROBLEMS + 2) * (this.font == null ? 9 : this.font.lineHeight);
    }

    private int boxWidth() {
        return Math.min(this.width - MARGIN * 2, 420);
    }

    private int leftEdge() {
        return (this.width - boxWidth()) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Losing a pasted hundred-line decklist to a stray Escape would be miserable.
        return !this.waiting;
    }
}
