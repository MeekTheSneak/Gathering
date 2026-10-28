package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.network.ChooseDeckPayload;
import dev.gathering.network.DeckNotLegalPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A deck chosen to play that is not legal in the table's format: what is not legal about it, and whether to
 * use it anyway.
 * <p>The owner's rule. Nothing here stops anybody - the mod enforces no rules - but a player is told before
 * they sit down to a game with a deck the others did not agree to, and the table is told if they go ahead.
 * Choosing another deck is on the left and is what closing does.
 * <p>Client-only.
 */
public final class DeckNotLegalScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;
    private static final int TEXT = 0xFFE8E4DC;
    private static final int WARN = 0xFFE0B15A;

    private final DeckNotLegalPayload asked;
    private final List<Component> lines = new ArrayList<>();
    private Rect panel = Rect.NONE;

    private DeckNotLegalScreen(DeckNotLegalPayload asked) {
        super(Component.translatable("screen.gathering.not_legal", asked.format()));
        this.asked = asked;
    }

    public static void accept(DeckNotLegalPayload asked) {
        Minecraft.getInstance().setScreen(new DeckNotLegalScreen(asked));
    }

    @Override
    protected void init() {
        lines.clear();
        lines.add(Component.translatable("screen.gathering.not_legal.deck", asked.deck()));
        for (String problem : asked.problems()) {
            lines.add(Component.literal("- " + problem));
        }
        if (asked.more() > 0) {
            lines.add(Component.translatable("screen.gathering.not_legal.more", asked.more()));
        }
        lines.add(Component.translatable("screen.gathering.not_legal.question"));
        int width = Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
        int textHeight = 0;
        for (Component line : lines) {
            textHeight += GuiText.linesNeeded(this.font, line, width - MARGIN * 2) * (this.font.lineHeight + 1) + 2;
        }
        int height = MARGIN * 2 + this.font.lineHeight + GAP + textHeight + GAP + ROW;
        panel = new Rect((this.width - width) / 2, Math.max(4, (this.height - height) / 2), width,
                Math.min(height, this.height - 8));
        int decideTop = panel.bottom() - MARGIN - ROW;
        int half = (panel.width() - MARGIN * 2 - GAP) / 2;
        addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, decideTop, half, ROW,
                Component.translatable("screen.gathering.not_legal.another"), this::onClose));
        addRenderableWidget(GatheringButtons.of(panel.right() - MARGIN - half, decideTop, half, ROW,
                Component.translatable("screen.gathering.not_legal.anyway"), () -> {
                    ClientNetworking.send(new ChooseDeckPayload(asked.table(), asked.slot(), true, java.util.Optional.empty()));
                    this.minecraft.setScreen(null);
                }));
    }

    /** Back to the list of decks, to choose another. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(new DeckPickerScreen(asked.table(), DeckPickerScreen.lastLoaners()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Prompts.panel(graphics, panel);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int inner = panel.width() - MARGIN * 2;
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + MARGIN, inner, WARN);
        int y = panel.y() + MARGIN + this.font.lineHeight + GAP;
        int stop = panel.bottom() - MARGIN - ROW - GAP;
        for (Component line : lines) {
            int needed = GuiText.linesNeeded(this.font, line, inner) * (this.font.lineHeight + 1);
            if (y + needed > stop) {
                break;
            }
            GuiText.drawWrapped(graphics, this.font, line, panel.x() + MARGIN, y, inner, TEXT);
            y += needed + 2;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The question's slot, for the scripted client. */
    int slot() {
        return asked.slot();
    }
}
