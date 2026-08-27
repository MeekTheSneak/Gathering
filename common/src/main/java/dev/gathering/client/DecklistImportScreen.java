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

    /** The decklist box never gets smaller than this: below it, paste is unreadable. */
    private static final int MIN_LIST_HEIGHT = 40;
    private static final int PROBLEM_COLOR = 0xFFE08A5A;
    private static final int STATUS_COLOR = 0xFFB8B4AC;
    private static final int LABEL_COLOR = 0xFF9A9690;
    private static final int MAX_VISIBLE_PROBLEMS = 6;

    private EditBox nameField;
    private EditBox descriptionField;
    private MultiLineEditBox decklistField;
    private Button importButton;

    private Component status = Component.empty();
    private List<Component> problems = List.of();
    private boolean waiting;

    /** Where the cards come from, or empty to conjure them out of nothing. */
    private final java.util.Optional<net.minecraft.core.BlockPos> from;

    public DecklistImportScreen() {
        super(Component.translatable("screen.gathering.import"));
        this.from = java.util.Optional.empty();
    }

    /**
     * The same screen, building out of a collection.
     *
     * <p>A different title and the same everything else, because it is the same thing: a list
     * goes in and a deck comes out. What differs is where the cards were, and that is worth
     * one line at the top rather than a second screen to learn.
     */
    public DecklistImportScreen(net.minecraft.core.BlockPos collection) {
        super(Component.translatable("screen.gathering.import.from_collection"));
        this.from = java.util.Optional.ofNullable(collection);
    }

    /** Whether this screen is building out of a collection rather than out of nothing. */
    private boolean fromCollection() {
        return this.from.isPresent();
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
                Math.max(MIN_LIST_HEIGHT, listBottom - top),
                Component.translatable("screen.gathering.import.placeholder"),
                Component.translatable("screen.gathering.import"));
        this.decklistField.setValue(previousList);
        this.addRenderableWidget(this.decklistField);

        int buttonTop = this.height - MARGIN - PADDING - BUTTON_HEIGHT;
        // Two buttons side by side, narrowed rather than overlapped on a narrow panel.
        int buttonWidth = Math.max(40, Math.min(BUTTON_WIDTH, (inner - GAP) / 2));
        this.importButton = Button.builder(
                        Component.translatable(fromCollection()
                                ? "screen.gathering.import.build"
                                : "screen.gathering.import.confirm"), button -> submit())
                .bounds(left + inner - buttonWidth, buttonTop, buttonWidth, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.importButton);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(left, buttonTop, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    private void submit() {
        String decklist = this.decklistField.getValue();
        if (decklist.isBlank()) {
            this.status = Component.translatable(fromCollection()
                    ? "screen.gathering.import.empty_build"
                    : "screen.gathering.import.empty");
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
                decklist, this.nameField.getValue(), this.descriptionField.getValue(), this.from));
    }

    /** Called from the payload handler when the server reports back. */
    public void onResult(ImportResultPayload result) {
        this.waiting = false;
        this.importButton.active = true;

        // The same screen says two different things, because it did two different things: a
        // deck out of nothing was imported, and a deck out of a collection was built from
        // cards somebody already had.
        this.status = result.isClean()
                ? Component.translatable(fromCollection()
                        ? "screen.gathering.import.built"
                        : "screen.gathering.import.done", result.cardCount())
                : Component.translatable(fromCollection()
                        ? "screen.gathering.import.built_with_problems"
                        : "screen.gathering.import.done_with_problems",
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
        // The problem list is only given room when there is one, so the decklist box has to
        // be laid out again now that there is.
        this.rebuildWidgets();
    }

    /**
     * The panel goes here, not in {@link #render}.
     *
     * <p>{@code Screen#render} calls this itself and it applies a full-screen blur, so
     * anything drawn before {@code super.render} gets blurred along with the world behind it.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panelLeft(), MARGIN, panelWidth(), this.height - MARGIN * 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = panelLeft();
        int width = panelWidth();

        int inner = width - PADDING * 2;
        GuiText.drawCentered(graphics, this.font, this.title, left + width / 2, MARGIN + PADDING - 1, inner, 0xFFFFFF);

        // Field labels, drawn against the panel rather than as widgets, so the layout above
        // stays a simple stack of boxes.
        int labelLeft = left + PADDING;
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.import.name"),
                labelLeft, this.nameField.getY() - this.font.lineHeight - 1, inner, LABEL_COLOR);
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.import.description"),
                labelLeft, this.descriptionField.getY() - this.font.lineHeight - 1, inner, LABEL_COLOR);
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.import.decklist"),
                labelLeft, this.decklistField.getY() - this.font.lineHeight - 1, inner, LABEL_COLOR);

        renderStatus(graphics, labelLeft, inner);
    }

    private void renderStatus(GuiGraphics graphics, int left, int width) {
        if (problemAreaHeight() == 0) {
            return;
        }
        int top = this.height - MARGIN - PADDING - BUTTON_HEIGHT - GAP - problemAreaHeight();

        GatheringSprites.inset(graphics, left - 2, top - 3, width + 4, problemAreaHeight() + 4);

        int line = top;
        if (!this.status.getString().isEmpty()) {
            GuiText.draw(graphics, this.font, this.status, left, line, width, STATUS_COLOR);
            line += this.font.lineHeight + 1;
        }
        for (Component problem : this.problems) {
            for (FormattedCharSequence wrapped : this.font.split(problem, width)) {
                if (line + this.font.lineHeight > top + problemAreaHeight()) {
                    return;
                }
                graphics.drawString(this.font, wrapped, left, line, PROBLEM_COLOR, false);
                line += this.font.lineHeight;
            }
        }
    }

    /**
     * How much room the status and problem list need, which is none until there is one.
     *
     * <p>Reserving the full eight lines unconditionally is what pushed the decklist box down
     * into the buttons on a short window - the paste area, which is the reason the screen
     * exists, was giving most of its height to a list that was usually empty.
     */
    private int problemAreaHeight() {
        if (this.status.getString().isEmpty() && this.problems.isEmpty()) {
            return 0;
        }
        int lineHeight = this.font == null ? 9 : this.font.lineHeight;
        int wanted = (1 + Math.min(MAX_VISIBLE_PROBLEMS + 1, this.problems.size())) * lineHeight;

        // Never more than the screen can spare while the decklist box keeps its minimum.
        int spare = this.height - MARGIN * 2 - PADDING * 2 - BUTTON_HEIGHT - GAP * 2
                - this.font.lineHeight * 4 - FIELD_HEIGHT * 2 - GAP * 2 - MIN_LIST_HEIGHT;
        return Math.max(0, Math.min(wanted, spare));
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
