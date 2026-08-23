package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.network.CardSummary;
import dev.gathering.network.SideboardEditPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Changing your deck between games.
 *
 * <p>Two columns, deck on the left and sideboard on the right, and a card goes across when you
 * click it. That is the whole interaction, because that is the whole task: nobody sideboarding
 * between games of a match wants a deckbuilder, they want to swap four cards in ninety seconds
 * while the other player shuffles.
 *
 * <p>Grouped by card with a count rather than listed one line per copy, because "3 Lightning
 * Bolt" is how anybody thinks about a deck and eleven identical rows of Mountain is not.
 *
 * <p>Every change is sent as it happens and the server sends the deck back. There is no
 * confirm step: the deck the table is holding is the deck that will be played, so a screen
 * closed halfway through has still done exactly what it looked like it was doing.
 *
 * <p>Client-only.
 */
public final class SideboardScreen extends ChildScreen implements CardPreviewHost {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int ACCENT = 0xFF6FD3E8;

    private static final int MARGIN = 12;
    private static final int HEADER = 26;
    private static final int FOOTER = 24;
    private static final int ROW_HEIGHT = 12;
    private static final int GAP = 8;

    /** Where the card under the cursor is drawn, if there is room beside the columns. */
    private static final int PREVIEW_WIDTH = 130;

    private final BlockPos table;

    private DeckComponent deck;
    private int gameNumber;
    private int bestOf;

    private Rect mainboard = Rect.NONE;
    private Rect sideboard = Rect.NONE;
    private Rect preview = Rect.NONE;

    private int mainScroll;
    private int sideScroll;
    private CardComponent hovered;

    /**
     * Opens the sideboard, or refreshes the one already open.
     *
     * <p>Refreshing rather than reopening matters: every swap sends the deck back, and a
     * screen that was rebuilt each time would lose its scroll position after every single
     * card - which is most of the interaction.
     *
     * <p>Both loaders come through here rather than each keeping their own copy of that rule,
     * and both get the same answer about where closing goes: back to the table if the player
     * was at it, which between games they were.
     */
    public static void open(BlockPos table, DeckComponent deck, int gameNumber, int bestOf) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof SideboardScreen already) {
            already.update(deck, gameNumber, bestOf);
            return;
        }
        Screen back = client.screen instanceof TableScreen board && board.isAbout(table)
                ? client.screen
                : null;
        client.setScreen(new SideboardScreen(table, deck, gameNumber, bestOf, back));
    }

    private SideboardScreen(BlockPos table, DeckComponent deck, int gameNumber, int bestOf,
            Screen back) {
        super(Component.translatable("screen.gathering.sideboard"), back);
        this.table = table;
        this.deck = deck;
        this.gameNumber = gameNumber;
        this.bestOf = bestOf;
    }

    /** A fresh copy of the deck from the server, after an edit landed. */
    private void update(DeckComponent updated, int game, int length) {
        this.deck = updated;
        this.gameNumber = game;
        this.bestOf = length;
    }

    @Override
    protected void init() {
        int top = MARGIN + HEADER;
        int bottom = this.height - MARGIN - FOOTER;
        int usable = this.width - MARGIN * 2;

        // The preview only gets a column when there is room for one. On a narrow window the
        // lists matter more, and the read key still works over any row.
        boolean roomForPreview = usable > PREVIEW_WIDTH + GAP * 2 + 220;
        int listsWidth = roomForPreview ? usable - PREVIEW_WIDTH - GAP : usable;
        int columnWidth = (listsWidth - GAP) / 2;

        mainboard = new Rect(MARGIN, top, columnWidth, bottom - top);
        sideboard = new Rect(mainboard.right() + GAP, top, columnWidth, bottom - top);
        preview = roomForPreview
                ? new Rect(sideboard.right() + GAP, top, PREVIEW_WIDTH, bottom - top)
                : Rect.NONE;

        addRenderableWidget(GatheringButtons.of(
                this.width / 2 - 60, this.height - MARGIN - FOOTER + 4, 120, 18,
                Component.translatable("screen.gathering.sideboard.done"), this::onClose));
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hovered = null;
        ClientHoverState.clear();

        GuiText.drawCentred(graphics, this.font,
                Component.translatable("screen.gathering.sideboard.title", gameNumber, bestOf),
                this.width / 2, MARGIN, this.width - MARGIN * 2, LABEL);

        drawColumn(graphics, mainboard, DeckComponent.Section.MAINBOARD, mainScroll, mouseX, mouseY,
                Component.translatable("screen.gathering.deck.mainboard", deck.entries().size()));
        drawColumn(graphics, sideboard, DeckComponent.Section.SIDEBOARD, sideScroll, mouseX, mouseY,
                Component.translatable("screen.gathering.deck.sideboard", deck.sideboard().size()));

        if (!preview.isEmpty()) {
            GatheringSprites.inset(graphics, preview.x(), preview.y(), preview.width(), preview.height());
            summaryOf(hovered).ifPresent(summary -> CardInspectPanel.renderArt(graphics, summary,
                    preview.x() + 3, preview.y() + 3, preview.width() - 6, preview.height() - 6));
        }

        GuiText.drawCentred(graphics, this.font,
                Component.translatable("screen.gathering.sideboard.hint"),
                this.width / 2, this.height - MARGIN - 8, this.width - MARGIN * 2, DIM);
    }

    private void drawColumn(
            GuiGraphics graphics, Rect area, DeckComponent.Section section, int scroll,
            int mouseX, int mouseY, Component heading) {
        GatheringSprites.panel(graphics, area.x(), area.y(), area.width(), area.height());
        GuiText.draw(graphics, this.font, heading, area.x() + 4, area.y() + 4, area.width() - 8, LABEL);

        int top = area.y() + 4 + this.font.lineHeight + 3;
        List<Entry> entries = groupedFor(section);
        for (int index = 0; index < entries.size(); index++) {
            int line = top + index * ROW_HEIGHT - scroll;
            if (line < top - ROW_HEIGHT || line + ROW_HEIGHT > area.bottom()) {
                continue;
            }
            Entry entry = entries.get(index);
            boolean over = mouseX >= area.x() && mouseX < area.right()
                    && mouseY >= line && mouseY < line + ROW_HEIGHT;
            if (over) {
                GatheringSprites.highlight(graphics, area.x() + 2, line, area.width() - 4, ROW_HEIGHT);
                hovered = entry.card();
                ClientHoverState.setHovered(CardItem.of(entry.card()));
            }
            GuiText.draw(graphics, this.font,
                    Component.literal(entry.count() + "  ").append(nameOf(entry.card())),
                    area.x() + 5, line + 2, area.width() - 10, over ? ACCENT : LABEL);
        }
    }

    private Component nameOf(CardComponent card) {
        return summaryOf(card)
                .map(summary -> Component.literal(summary.name()))
                .map(Component.class::cast)
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickColumn(mainboard, DeckComponent.Section.MAINBOARD, DeckComponent.Section.SIDEBOARD,
                mainScroll, (int) mouseX, (int) mouseY)) {
            return true;
        }
        if (clickColumn(sideboard, DeckComponent.Section.SIDEBOARD, DeckComponent.Section.MAINBOARD,
                sideScroll, (int) mouseX, (int) mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickColumn(
            Rect area, DeckComponent.Section from, DeckComponent.Section to, int scroll, int x, int y) {
        if (!area.contains(x, y)) {
            return false;
        }
        int top = area.y() + 4 + this.font.lineHeight + 3;
        int index = (y - top + scroll) / ROW_HEIGHT;
        List<Entry> entries = groupedFor(from);
        if (index < 0 || index >= entries.size()) {
            // Still ours: a click on a column's empty space should not fall through to
            // whatever is underneath.
            return true;
        }
        GatheringButtons.clickSound();
        ClientNetworking.send(new SideboardEditPayload(table, from, to, entries.get(index).card()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) -scrollY * ROW_HEIGHT;
        if (mainboard.contains((int) mouseX, (int) mouseY)) {
            mainScroll = clampScroll(mainScroll + step, mainboard, DeckComponent.Section.MAINBOARD);
            return true;
        }
        if (sideboard.contains((int) mouseX, (int) mouseY)) {
            sideScroll = clampScroll(sideScroll + step, sideboard, DeckComponent.Section.SIDEBOARD);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int clampScroll(int wanted, Rect area, DeckComponent.Section section) {
        int rows = groupedFor(section).size();
        int visible = (area.height() - 4 - this.font.lineHeight - 7) / ROW_HEIGHT;
        int content = Math.max(0, (rows - visible) * ROW_HEIGHT);
        return Math.max(0, Math.min(content, wanted));
    }

    // -------------------------------------------------------------- grouping

    /**
     * A section as "3 Lightning Bolt" rather than as three lines saying Lightning Bolt.
     *
     * <p>Insertion-ordered, so a card that moves across and back lands where it was rather
     * than at the bottom - which matters when you are doing this against a clock and looking
     * for the row you just clicked.
     */
    private List<Entry> groupedFor(DeckComponent.Section section) {
        Map<CardComponent, Integer> counts = new LinkedHashMap<>();
        for (CardComponent card : deck.section(section)) {
            counts.merge(card, 1, Integer::sum);
        }
        List<Entry> entries = new ArrayList<>(counts.size());
        counts.forEach((card, count) -> entries.add(new Entry(card, count)));
        return entries;
    }

    private Optional<CardSummary> summaryOf(CardComponent card) {
        return card == null ? Optional.empty() : ClientCardCache.get().summary(card);
    }


    private record Entry(CardComponent card, int count) {
    }
}
