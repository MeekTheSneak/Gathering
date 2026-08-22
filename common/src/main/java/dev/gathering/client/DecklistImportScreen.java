package dev.gathering.client;

import dev.gathering.network.ImportDecklistPayload;
import dev.gathering.network.ImportResultPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Paste a decklist or a link to one, name it, and get a deck.
 *
 * <p>The front door of the mod. Deliberately dull, and deliberately honest: a list that half
 * worked shows which lines to fix at their line numbers and still hands over the deck.
 *
 * <p>Client-only.
 */
public final class DecklistImportScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 8;
    private static final int GAP = 6;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 110;
    private static final int PANEL_WIDTH = 340;
    private static final int PROBLEM_COLOUR = 0xFFE08A5A;
    private static final int STATUS_COLOUR = 0xFFB8B4AC;
    private static final int LABEL_COLOUR = 0xFF9A9690;
    private static final int MAX_VISIBLE_PROBLEMS = 6;

    private EditBox nameField;
    private EditBox descriptionField;
    private MultiLineEditBox decklistField;
    private Button importButton;

    private Component status = Component.empty();
    private List<Component> problems = List.of();
    private boolean waiting;

    public DecklistImportScreen() {
        super(Component.translatable("screen.gathering.import"));
    }

    private int panelLeft() {
        return (this.width - Math.min(PANEL_WIDTH, this.width - MARGIN * 2)) / 2;
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
    }

    @Override
    protected void init() {
        String previousList = this.decklistField == null ? "" : this.decklistField.getValue();
        String previousName = this.nameField == null ? "" : this.nameField.getValue();
        String previousNote = this.descriptionField == null ? "" : this.descriptionField.getValue();

        int left = panelLeft() + PADDING;
        int inner = panelWidth() - PADDING * 2;
        int top = MARGIN + PADDING + this.font.lineHeight + GAP;

        this.nameField = new EditBox(this.font, left, top, inner, FIELD_HEIGHT,
                Component.translatable("screen.gathering.import.name"));
        this.nameField.setMaxLength(ImportDecklistPayload.MAX_NAME_LENGTH);
        this.nameField.setHint(Component.translatable("screen.gathering.import.name_hint"));
        this.nameField.setValue(previousName);
        this.addRenderableWidget(this.nameField);

        top += FIELD_HEIGHT + GAP + this.font.lineHeight;
        this.descriptionField = new EditBox(this.font, left, top, inner, FIELD_HEIGHT,
                Component.translatable("screen.gathering.import.description"));
        this.descriptionField.setMaxLength(ImportDecklistPayload.MAX_DESCRIPTION_LENGTH);
        this.descriptionField.setHint(Component.translatable("screen.gathering.import.description_hint"));
        this.descriptionField.setValue(previousNote);
        this.addRenderableWidget(this.descriptionField);

        top += FIELD_HEIGHT + GAP + this.font.lineHeight;
        int listBottom = this.height - MARGIN - PADDING - BUTTON_HEIGHT - GAP - problemAreaHeight() - GAP;
        this.decklistField = new MultiLineEditBox(this.font, left, top, inner,
                Math.max(FIELD_HEIGHT * 2, listBottom - top),
                Component.translatable("screen.gathering.import.placeholder"),
                Component.translatable("screen.gathering.import"));
        this.decklistField.setValue(previousList);
        this.addRenderableWidget(this.decklistField);

        int buttonTop = this.height - MARGIN - PADDING - BUTTON_HEIGHT;
        this.importButton = Button.builder(
                        Component.translatable("screen.gathering.import.confirm"), button -> submit())
                .bounds(left + inner - BUTTON_WIDTH, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.importButton);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(left, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void submit() {
        String decklist = this.decklistField.getValue();
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

        ClientNetworking.send(new ImportDecklistPayload(
                decklist, this.nameField.getValue(), this.descriptionField.getValue()));
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
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = panelLeft();
        int width = panelWidth();
        GatheringSprites.panel(graphics, left, MARGIN, width, this.height - MARGIN * 2);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, MARGIN + PADDING - 1, 0xFFFFFF);

        // Field labels, drawn against the panel rather than as widgets, so the layout above
        // stays a simple stack of boxes.
        int labelLeft = left + PADDING;
        graphics.drawString(this.font, Component.translatable("screen.gathering.import.name"),
                labelLeft, this.nameField.getY() - this.font.lineHeight - 1, LABEL_COLOUR, false);
        graphics.drawString(this.font, Component.translatable("screen.gathering.import.description"),
                labelLeft, this.descriptionField.getY() - this.font.lineHeight - 1, LABEL_COLOUR, false);
        graphics.drawString(this.font, Component.translatable("screen.gathering.import.decklist"),
                labelLeft, this.decklistField.getY() - this.font.lineHeight - 1, LABEL_COLOUR, false);

        renderStatus(graphics, labelLeft, width - PADDING * 2);
    }

    private void renderStatus(GuiGraphics graphics, int left, int width) {
        int top = this.height - MARGIN - PADDING - BUTTON_HEIGHT - GAP - problemAreaHeight();
        if (this.status.getString().isEmpty() && this.problems.isEmpty()) {
            return;
        }

        GatheringSprites.inset(graphics, left - 2, top - 3, width + 4, problemAreaHeight() + 4);

        int line = top;
        if (!this.status.getString().isEmpty()) {
            graphics.drawString(this.font, this.status, left, line, STATUS_COLOUR, false);
            line += this.font.lineHeight + 1;
        }
        for (Component problem : this.problems) {
            for (FormattedCharSequence wrapped : this.font.split(problem, width)) {
                if (line + this.font.lineHeight > top + problemAreaHeight()) {
                    return;
                }
                graphics.drawString(this.font, wrapped, left, line, PROBLEM_COLOUR, false);
                line += this.font.lineHeight;
            }
        }
    }

    private int problemAreaHeight() {
        int lineHeight = this.font == null ? 9 : this.font.lineHeight;
        return (MAX_VISIBLE_PROBLEMS + 2) * lineHeight;
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
