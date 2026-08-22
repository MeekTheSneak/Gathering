package dev.gathering.client;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.network.CardSummary;
import dev.gathering.network.RequestCardMetadataPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What is actually in a deck.
 *
 * <p>Without this a deck is an opaque item that claims a number, which is a strange thing to
 * hand somebody after they pasted a hundred cards. The list groups by zone, collapses copies
 * into counts the way a decklist does, and lets the zoom overlay read any row - so the deck
 * is browsable before there is a table to play it at.
 *
 * <p>Card names come from the client's metadata cache, which is emptied on disconnect, so
 * the screen asks the server about its printings when it opens. Until the answer arrives
 * rows show as loading rather than as blanks.
 *
 * <p>Client-only.
 */
public final class DeckContentsScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 8;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 12;
    private static final int PANEL_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;

    private static final int HEADING_COLOUR = 0xFFC49E4A;
    private static final int NAME_COLOUR = 0xFFE8E4DC;
    private static final int COUNT_COLOUR = 0xFF9A9690;
    private static final int PENDING_COLOUR = 0xFF6E6A66;

    private final DeckComponent deck;
    private final List<Row> rows = new ArrayList<>();

    private int scroll;
    private int hoveredRow = -1;

    public DeckContentsScreen(DeckComponent deck) {
        super(Component.literal(deck.name()));
        this.deck = deck;
    }

    @Override
    protected void init() {
        buildRows();

        // The client may know none of these cards - a deck from a previous session is a list
        // of ids until the server says otherwise.
        List<java.util.UUID> printings = deck.distinctPrintings();
        if (!printings.isEmpty()) {
            ClientNetworking.send(new RequestCardMetadataPayload(printings));
        }

        int left = panelLeft();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left + panelWidth() - PADDING - 80, this.height - MARGIN - PADDING - BUTTON_HEIGHT,
                        80, BUTTON_HEIGHT)
                .build());
    }

    /** Groups the deck by zone and collapses copies, the way a decklist reads. */
    private void buildRows() {
        rows.clear();
        addSection("screen.gathering.deck.commanders", deck.commanders());
        addSection("screen.gathering.deck.mainboard", deck.entries());
        addSection("screen.gathering.deck.sideboard", deck.sideboard());
    }

    private void addSection(String headingKey, List<CardComponent> cards) {
        if (cards.isEmpty()) {
            return;
        }
        Map<CardComponent, Integer> counts = new LinkedHashMap<>();
        for (CardComponent card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        rows.add(Row.heading(Component.translatable(headingKey, cards.size())));
        counts.forEach((card, count) -> rows.add(Row.card(card, count)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = panelLeft();
        int width = panelWidth();
        GatheringSprites.panel(graphics, left, MARGIN, width, this.height - MARGIN * 2);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, MARGIN + PADDING - 1, 0xFFFFFF);

        int listTop = MARGIN + PADDING + this.font.lineHeight + GAP;
        int listBottom = this.height - MARGIN - PADDING - BUTTON_HEIGHT - GAP;
        int listLeft = left + PADDING;
        int listWidth = width - PADDING * 2;

        GatheringSprites.inset(graphics, listLeft - 2, listTop - 2, listWidth + 4, listBottom - listTop + 4);

        renderRows(graphics, mouseX, mouseY, listLeft, listTop, listWidth, listBottom);

        super.render(graphics, mouseX, mouseY, partialTick);

        // The overlay draws on top of everything, so a row under the cursor can be read.
        CardZoomOverlay.render(graphics, this.width, this.height);
    }

    private void renderRows(
            GuiGraphics graphics, int mouseX, int mouseY, int left, int top, int width, int bottom) {
        graphics.enableScissor(left, top, left + width, bottom);

        hoveredRow = -1;
        ClientHoverState.clear();

        int y = top - scroll;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                boolean hovered = mouseX >= left && mouseX <= left + width && mouseY >= y && mouseY < y + ROW_HEIGHT
                        && mouseY >= top && mouseY < bottom;
                renderRow(graphics, row, left, y, width, hovered);
                if (hovered && row.card() != null) {
                    hoveredRow = index;
                    // Feeds the zoom overlay exactly as an inventory slot would.
                    ClientHoverState.setHovered(dev.gathering.item.CardItem.of(row.card()));
                }
            }
            y += ROW_HEIGHT;
        }

        graphics.disableScissor();
    }

    private void renderRow(GuiGraphics graphics, Row row, int left, int y, int width, boolean hovered) {
        if (row.card() == null) {
            graphics.drawString(this.font, row.heading(), left + 2, y + 2, HEADING_COLOUR, false);
            return;
        }
        if (hovered) {
            GatheringSprites.highlight(graphics, left, y, width, ROW_HEIGHT);
        }

        Optional<CardSummary> summary = ClientCardCache.get().summary(row.card());
        Component name = summary
                .map(found -> Component.literal(found.name()).withStyle(style -> style))
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
        int colour = summary.isPresent() ? NAME_COLOUR : PENDING_COLOUR;

        graphics.drawString(this.font, row.count() + "x", left + 2, y + 2, COUNT_COLOUR, false);
        graphics.drawString(this.font, name, left + 24, y + 2, colour, false);
        if (row.card().foil()) {
            graphics.drawString(this.font,
                    Component.translatable("tooltip." + dev.gathering.Gathering.MOD_ID + ".foil")
                            .withStyle(ChatFormatting.AQUA),
                    left + width - 26, y + 2, 0xFF6FD3E8, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = rows.size() * ROW_HEIGHT;
        int viewHeight = this.height - MARGIN * 2 - PADDING * 2 - this.font.lineHeight - GAP * 2 - BUTTON_HEIGHT;
        int maximum = Math.max(0, contentHeight - viewHeight);
        scroll = Math.max(0, Math.min(maximum, scroll - (int) (scrollY * ROW_HEIGHT * 2)));
        return true;
    }

    @Override
    public void onClose() {
        ClientHoverState.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
    }

    /** Either a section heading or a card with a count. */
    private record Row(Component heading, CardComponent card, int count) {

        static Row heading(Component heading) {
            return new Row(heading, null, 0);
        }

        static Row card(CardComponent card, int count) {
            return new Row(Component.empty(), card, count);
        }
    }
}
