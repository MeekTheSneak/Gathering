package dev.gathering.client;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.CardSummary;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.network.RequestCardMetadataPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What is actually in a deck, and the place you change it.
 *
 * <p>Without this a deck is an opaque item that claims a number, which is a strange thing to
 * hand somebody after they pasted a hundred cards. The list groups by zone, collapses copies
 * into counts the way a decklist does, and reads any row on demand - so the deck is browsable
 * and editable before there is a table to play it at.
 *
 * <p>The list sits against the left edge rather than centred, because holding the read key
 * over a row shows that card full size on the right. Centring the list would put the card on
 * top of it.
 *
 * <p>The screen owns no copy of the deck. It reads the stack in the player's hand every
 * frame, so an edit the server applies appears here as soon as the held item syncs, and a
 * deck that stops being in that hand closes the screen instead of showing a ghost.
 *
 * <p>Card names come from the client's metadata cache, which is emptied on disconnect, so
 * the screen asks the server about its printings when it opens. Until the answer arrives
 * rows show as loading rather than as blanks.
 *
 * <p>Client-only.
 */
public final class DeckContentsScreen extends Screen implements CardPreviewHost {

    private static final int MARGIN = 16;
    private static final int PADDING = 8;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 12;
    private static final int PANEL_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;

    /** Below this the preview would be a thumbnail, so it is not worth the space it takes. */
    private static final int MINIMUM_PREVIEW_WIDTH = 140;

    /**
     * Above this the oracle text stops being readable.
     *
     * <p>A card preview that fills a 21:9 monitor is a line of text a metre wide over a
     * postage-stamp image, because the art is bounded by the panel's height and the text is
     * not. Capping the width and centring what is left keeps the proportions of a card.
     */
    private static final int MAXIMUM_PREVIEW_WIDTH = 420;

    private static final int HEADING_COLOUR = 0xFFC49E4A;
    private static final int NAME_COLOUR = 0xFFE8E4DC;
    private static final int COUNT_COLOUR = 0xFF9A9690;
    private static final int PENDING_COLOUR = 0xFF6E6A66;

    private final InteractionHand hand;
    private final List<Row> rows = new ArrayList<>();

    /** What the rows were built from, so a deck the server changed rebuilds them. */
    private DeckComponent shown;

    private int scroll;

    public DeckContentsScreen(InteractionHand hand) {
        super(Component.empty());
        this.hand = hand;
    }

    @Override
    protected void init() {
        DeckComponent deck = deck().orElse(null);
        if (deck == null) {
            // Nothing to lay out. tick() closes the screen on the next server tick rather
            // than here, because closing a screen from inside its own init is a re-entrant
            // setScreen.
            return;
        }
        rebuild(deck);
        requestNames(deck);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(panelLeft() + panelWidth() - PADDING - 80,
                        this.height - MARGIN - PADDING - BUTTON_HEIGHT, 80, BUTTON_HEIGHT)
                .build());
    }

    /** The deck the player is holding right now, which is the only one this screen shows. */
    private Optional<DeckComponent> deck() {
        return heldStack().flatMap(DeckItem::deckOf);
    }

    private Optional<ItemStack> heldStack() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return Optional.empty();
        }
        ItemStack stack = player.getItemInHand(hand);
        return stack.getItem() instanceof DeckItem ? Optional.of(stack) : Optional.empty();
    }

    /**
     * The client may know none of these cards - a deck from a previous session is a list of
     * ids until the server says otherwise.
     */
    private void requestNames(DeckComponent deck) {
        List<UUID> printings = deck.distinctPrintings();
        if (!printings.isEmpty()) {
            ClientNetworking.send(new RequestCardMetadataPayload(printings));
        }
    }

    private void rebuild(DeckComponent deck) {
        shown = deck;
        rows.clear();
        addSection(DeckComponent.Section.COMMANDERS, "screen.gathering.deck.commanders", deck);
        addSection(DeckComponent.Section.MAINBOARD, "screen.gathering.deck.mainboard", deck);
        addSection(DeckComponent.Section.SIDEBOARD, "screen.gathering.deck.sideboard", deck);
        scroll = Math.min(scroll, maximumScroll());
    }

    /** Groups the deck by zone and collapses copies, the way a decklist reads. */
    private void addSection(DeckComponent.Section section, String headingKey, DeckComponent deck) {
        List<CardComponent> cards = deck.section(section);
        if (cards.isEmpty()) {
            return;
        }
        Map<CardComponent, Integer> counts = new LinkedHashMap<>();
        for (CardComponent card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        rows.add(Row.heading(Component.translatable(headingKey, cards.size())));
        counts.forEach((card, count) -> rows.add(Row.card(section, card, count)));
    }

    /**
     * The panel goes here, not in {@link #render}.
     *
     * <p>{@code Screen#render} calls this itself, and this applies a full-screen blur to
     * everything already drawn. Drawing the panel in {@code render} before calling
     * {@code super.render} therefore blurs the panel and every hand-drawn label on it -
     * widgets survive because they are drawn afterwards, which makes the bug look like
     * "some of the screen is fuzzy" rather than like a render-order mistake.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panelLeft(), MARGIN, panelWidth(), this.height - MARGIN * 2);
    }

    /**
     * The deck left the player's hand - dropped, swapped, taken by a hopper.
     *
     * <p>There is then nothing here to show and nothing an edit could safely land on, so the
     * screen goes away rather than showing a deck that is no longer there.
     */
    @Override
    public void tick() {
        super.tick();
        if (deck().isEmpty()) {
            this.onClose();
        }
    }

    /**
     * The item's own name, read live, because the player may rename the deck elsewhere.
     *
     * <p>Through the stack rather than through {@code DeckComponent#name}, so a deck with no
     * name - one started by putting two cards together - is headed "Deck" rather than by an
     * empty line.
     */
    @Override
    public Component getTitle() {
        return heldStack().map(ItemStack::getHoverName).orElseGet(Component::empty);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DeckComponent deck = deck().orElse(null);
        if (deck == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (!deck.equals(shown)) {
            rebuild(deck);
        }

        // Background, panel and widgets, in that order.
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(
                this.font, getTitle(), panelLeft() + panelWidth() / 2, MARGIN + PADDING - 1, 0xFFFFFF);

        GatheringSprites.inset(graphics, listLeft() - 2, listTop() - 2, listWidth() + 4, listHeight() + 4);
        renderRows(graphics, mouseX, mouseY);

        int hovered = rowAt(mouseX, mouseY);
        renderHint(graphics, hovered);
        if (hovered >= 0) {
            renderPreview(graphics, rows.get(hovered));
        }
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = listLeft();
        int top = listTop();
        int width = listWidth();
        int bottom = top + listHeight();
        int hovered = rowAt(mouseX, mouseY);

        graphics.enableScissor(left, top, left + width, bottom);
        int y = top - scroll;
        for (int index = 0; index < rows.size(); index++) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                renderRow(graphics, rows.get(index), left, y, width, index == hovered);
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
                .<Component>map(found -> Component.literal(found.name()))
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));

        graphics.drawString(this.font, row.count() + "x", left + 2, y + 2, COUNT_COLOUR, false);
        graphics.drawString(this.font, name, left + 24, y + 2,
                summary.isPresent() ? NAME_COLOUR : PENDING_COLOUR, false);
        if (row.card().foil()) {
            graphics.drawString(this.font,
                    Component.translatable("tooltip." + Gathering.MOD_ID + ".foil")
                            .withStyle(ChatFormatting.AQUA),
                    left + width - 26, y + 2, 0xFF6FD3E8, false);
        }
    }

    /** What a click on this row would do, said plainly under the list. */
    private void renderHint(GuiGraphics graphics, int hovered) {
        if (hovered < 0) {
            return;
        }
        String key = rows.get(hovered).section() == DeckComponent.Section.COMMANDERS
                ? "screen.gathering.deck.hint_commander"
                : "screen.gathering.deck.hint_card";
        graphics.drawString(this.font, Component.translatable(key),
                listLeft(), listTop() + listHeight() + GAP, PENDING_COLOUR, false);
    }

    /**
     * The hovered card, full size, in the space to the right of the list.
     *
     * <p>No key to hold. Reading down a decklist is the whole purpose of this screen, and a
     * modifier you have to keep pressed for a hundred rows is a toll on the one thing the
     * screen exists to do. The space is already reserved, so nothing is covered by showing
     * it.
     */
    private void renderPreview(GuiGraphics graphics, Row row) {
        int regionLeft = panelLeft() + panelWidth() + GAP * 2;
        int regionWidth = this.width - MARGIN - regionLeft;
        if (regionWidth < MINIMUM_PREVIEW_WIDTH) {
            return;
        }
        int width = Math.min(regionWidth, MAXIMUM_PREVIEW_WIDTH);
        int left = regionLeft + (regionWidth - width) / 2;
        ClientCardCache.get().summary(row.card()).ifPresent(summary ->
                CardInspectPanel.renderInto(graphics, summary, left, MARGIN, width, this.height - MARGIN * 2));
    }

    /**
     * Left-click takes a card out of the deck; right-click moves it to or from the command
     * zone.
     *
     * <p>Both are requests, not edits: the server owns the deck and this screen only shows
     * what it is told. The card is named rather than the row, so a click that arrives after
     * the list has shifted still means the card the player was pointing at.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int index = rowAt((int) mouseX, (int) mouseY);
        if (index < 0) {
            return false;
        }
        Row row = rows.get(index);
        if (button == 0) {
            ClientNetworking.send(DeckEditPayload.take(hand, row.section(), row.card()));
            return true;
        }
        if (button == 1) {
            ClientNetworking.send(DeckEditPayload.toggleCommander(hand, row.section(), row.card()));
            return true;
        }
        return false;
    }

    /** The card row under this point, or -1 for a heading, a gap, or somewhere else entirely. */
    private int rowAt(int mouseX, int mouseY) {
        int left = listLeft();
        int top = listTop();
        int bottom = top + listHeight();
        if (mouseX < left || mouseX >= left + listWidth() || mouseY < top || mouseY >= bottom) {
            return -1;
        }
        int index = (mouseY - top + scroll) / ROW_HEIGHT;
        if (index < 0 || index >= rows.size() || rows.get(index).card() == null) {
            return -1;
        }
        return index;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maximumScroll(), scroll - (int) (scrollY * ROW_HEIGHT * 2)));
        return true;
    }

    private int maximumScroll() {
        return Math.max(0, rows.size() * ROW_HEIGHT - listHeight());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelLeft() {
        return MARGIN;
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
    }

    private int listLeft() {
        return panelLeft() + PADDING;
    }

    private int listWidth() {
        return panelWidth() - PADDING * 2;
    }

    private int listTop() {
        return MARGIN + PADDING + this.font.lineHeight + GAP;
    }

    private int listHeight() {
        // Room under the list for the click hint, then the Done button.
        int bottom = this.height - MARGIN - PADDING - BUTTON_HEIGHT - GAP * 2 - this.font.lineHeight;
        return Math.max(ROW_HEIGHT, bottom - listTop());
    }

    /** Either a section heading or a card with a count and the pile it came from. */
    private record Row(Component heading, DeckComponent.Section section, CardComponent card, int count) {

        static Row heading(Component heading) {
            return new Row(heading, null, null, 0);
        }

        static Row card(DeckComponent.Section section, CardComponent card, int count) {
            return new Row(Component.empty(), section, card, count);
        }
    }
}
