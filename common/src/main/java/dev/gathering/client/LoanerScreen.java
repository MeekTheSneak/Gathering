package dev.gathering.client;

import dev.gathering.network.OpenLoanersPayload;
import dev.gathering.network.TakeLoanerPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Pick a deck the server will lend you.
 * <p>The first screen a lot of players will ever see in this mod, and very likely the first
 * thing they do on the server: sit down at a table with nothing, and be asked which deck they
 * would like. So it is a list of names and nothing else - no card counts, no colors, no
 * preview - because somebody who has been here ninety seconds cannot use any of that, and a
 * screen that asked them to weigh it up would be a screen they close.
 * <p>Closing it is a real answer. Somebody who came to the table with their own plan should
 * not have to take a deck to get out of the way, so Escape leaves and nothing happens.
 * <p>Client-only.
 */
public final class LoanerScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 10;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_WIDTH = 240;

    private static final int DIM = 0xFF9A9690;

    private final BlockPos table;
    private final List<String> names;

    /** Which of them is showing, when there are more than the window has room for. */
    private int scroll;

    private LoanerScreen(BlockPos table, List<String> names) {
        super(Component.translatable("screen.gathering.loaners"));
        this.table = table;
        this.names = List.copyOf(names);
    }

    public static void accept(OpenLoanersPayload offer) {
        if (offer.names().isEmpty()) {
            return;
        }
        Minecraft.getInstance().setScreen(new LoanerScreen(offer.table(), offer.names()));
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int listTop() {
        return panelTop() + PADDING + this.font.lineHeight * 2 + GAP + 2;
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    /**
     * The space between the last deck and the way out.
     * <p>Not decoration. Drawn flush against the list, "No thanks" is a sixth button the same
     * size and color as the five above it, which makes it read as a deck with an odd name.
     */
    private static final int BREAK = GAP * 3;

    private int panelHeight() {
        int wanted = PADDING * 2 + this.font.lineHeight * 2 + GAP + 2
                + rowsThatFit() * (ROW_HEIGHT + GAP) + BREAK + ROW_HEIGHT;
        return Math.min(wanted, this.height - MARGIN * 2);
    }

    /** How many decks the window has room for, never fewer than one. */
    private int rowsThatFit() {
        int room = this.height - MARGIN * 2 - PADDING * 2 - this.font.lineHeight * 2 - GAP - 2
                - ROW_HEIGHT - BREAK;
        return Math.max(1, Math.min(names.size(), room / (ROW_HEIGHT + GAP)));
    }

    @Override
    protected void init() {
        int width = panelWidth() - PADDING * 2;
        int left = panelLeft() + PADDING;
        int showing = rowsThatFit();
        this.scroll = Math.max(0, Math.min(this.scroll, names.size() - showing));

        for (int row = 0; row < showing; row++) {
            String name = names.get(this.scroll + row);
            addRenderableWidget(GatheringButtons.of(
                    left, listTop() + row * (ROW_HEIGHT + GAP), width, ROW_HEIGHT,
                    Component.literal(name), () -> take(name)));
        }

        // The way out that is not taking a deck. A screen whose only buttons all commit you
        // to something is a screen somebody feels trapped by, and Escape alone is not an
        // answer if you have not learned yet that it is one.
        addRenderableWidget(GatheringButtons.of(
                left, listTop() + showing * (ROW_HEIGHT + GAP) + BREAK - GAP, width, ROW_HEIGHT,
                Component.translatable("screen.gathering.loaners.no_thanks"), this::onClose));
    }

    private void take(String name) {
        ClientNetworking.send(new TakeLoanerPayload(this.table, name));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int showing = rowsThatFit();
        if (names.size() > showing) {
            int wanted = Math.max(0,
                    Math.min(this.scroll - (int) Math.signum(scrollY), names.size() - showing));
            if (wanted != this.scroll) {
                this.scroll = wanted;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panelLeft(), panelTop(), panelWidth(), panelHeight());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int inner = panelWidth() - PADDING * 2;
        GuiText.drawCentered(graphics, this.font, getTitle(),
                panelLeft() + panelWidth() / 2, panelTop() + PADDING, inner, 0xFFFFFF);
        GuiText.drawCentered(graphics, this.font,
                Component.translatable("screen.gathering.loaners.note"),
                panelLeft() + panelWidth() / 2,
                panelTop() + PADDING + this.font.lineHeight + 1, inner, DIM);

        if (names.size() > rowsThatFit()) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.loaners.more",
                            names.size() - rowsThatFit()),
                    panelLeft() + panelWidth() / 2,
                    listTop() + rowsThatFit() * (ROW_HEIGHT + GAP) + 1, inner, DIM);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** How many decks are on offer. For the scene that photographs this screen. */
    int listed() {
        return rowsThatFit();
    }
}
