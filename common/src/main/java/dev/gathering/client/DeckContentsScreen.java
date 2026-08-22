package dev.gathering.client;

import dev.gathering.Gathering;
import dev.gathering.core.ui.DeckScreenLayout;
import dev.gathering.core.ui.Rect;
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
import org.joml.Matrix4f;

/**
 * What is actually in a deck, and the place you change it.
 *
 * <p>Without this a deck is an opaque item that claims a number, which is a strange thing to
 * hand somebody after they pasted a hundred cards. The list groups by zone, collapses copies
 * into counts the way a decklist does, and shows whatever row is under the cursor as a full
 * card beside it - so the deck is browsable and editable before there is a table to play it
 * at.
 *
 * <p>The shape is a decklist panel flush against the left edge with the card and its text to
 * the right of it. Where everything actually goes is {@link DeckScreenLayout}, in the pure
 * module, so it can be checked at every window size rather than at the one it was written
 * at. This class draws what that says.
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

    private static final int ROW_HEIGHT = 12;
    private static final int COUNT_COLUMN = 22;

    private static final int HEADING_COLOUR = 0xFFC49E4A;
    private static final int NAME_COLOUR = 0xFFE8E4DC;
    private static final int COUNT_COLOUR = 0xFF9A9690;
    private static final int PENDING_COLOUR = 0xFF6E6A66;
    private static final int FOIL_COLOUR = 0xFF6FD3E8;
    private static final int TITLE_COLOUR = 0xFFFFFFFF;

    private final InteractionHand hand;
    private final List<Row> rows = new ArrayList<>();

    /** What the rows were built from, so a deck the server changed rebuilds them. */
    private DeckComponent shown;

    private DeckScreenLayout layout;
    private int scroll;
    private boolean draggingThumb;

    /** Open only between a right-click and the next click anywhere. */
    private ContextMenu menu;

    public DeckContentsScreen(InteractionHand hand) {
        super(Component.empty());
        this.hand = hand;
    }

    @Override
    protected void init() {
        this.layout = DeckScreenLayout.of(this.width, this.height, this.font.lineHeight);

        DeckComponent deck = deck().orElse(null);
        if (deck == null) {
            // Nothing to lay out. tick() closes the screen on the next server tick rather
            // than here, because closing a screen from inside its own init is a re-entrant
            // setScreen.
            return;
        }
        rebuild(deck);
        requestNames(deck);

        Rect done = layout.done();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(done.x(), done.y(), done.width(), done.height())
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
     * The deck left the player's hand - dropped, swapped, taken by a hopper, or emptied.
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
        return heldStack()
                .map(ItemStack::getHoverName)
                .map(name -> (Component) name.copy().withStyle(ChatFormatting.BOLD))
                .orElseGet(Component::empty);
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
        Rect panel = layout().panel();
        GatheringSprites.deckPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
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
            // The row this menu was opened on may not exist any more.
            menu = null;
        }

        // Background, panel and widgets, in that order.
        super.render(graphics, mouseX, mouseY, partialTick);

        DeckScreenLayout current = layout();
        Rect title = current.title();
        GuiText.draw(graphics, this.font, getTitle(), title.x(), title.y(), title.width(), TITLE_COLOUR);

        int hovered = rowAt(mouseX, mouseY);
        renderRows(graphics, hovered);
        renderScrollbar(graphics);
        renderHint(graphics, hovered);
        renderPreview(graphics, hovered >= 0 ? rows.get(hovered) : null);

        if (menu != null) {
            menu.render(graphics, this.font, mouseX, mouseY);
        }
    }

    private void renderRows(GuiGraphics graphics, int hovered) {
        Rect area = layout().rows();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());

        int y = area.y() - scroll;
        for (int index = 0; index < rows.size(); index++) {
            if (y + ROW_HEIGHT >= area.y() && y <= area.bottom()) {
                renderRow(graphics, rows.get(index), area, y, index == hovered);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void renderRow(GuiGraphics graphics, Row row, Rect area, int y, boolean hovered) {
        if (row.card() == null) {
            GuiText.draw(graphics, this.font, row.heading(), area.x() + 2, y + 2, area.width() - 4, HEADING_COLOUR);
            return;
        }
        if (hovered) {
            GatheringSprites.highlight(graphics, area.x(), y, area.width(), ROW_HEIGHT);
        }

        Optional<CardSummary> summary = ClientCardCache.get().summary(row.card());
        Component name = summary
                .<Component>map(found -> Component.literal(found.name()))
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));

        Component foil = Component.translatable("tooltip." + Gathering.MOD_ID + ".foil")
                .withStyle(ChatFormatting.AQUA);
        int foilWidth = row.card().foil() ? this.font.width(foil) + 4 : 0;

        GuiText.draw(graphics, this.font, Component.literal(row.count() + "x"),
                area.x() + 2, y + 2, COUNT_COLUMN - 2, COUNT_COLOUR);
        GuiText.draw(graphics, this.font, name, area.x() + COUNT_COLUMN, y + 2,
                area.width() - COUNT_COLUMN - foilWidth - 2, summary.isPresent() ? NAME_COLOUR : PENDING_COLOUR);
        if (row.card().foil()) {
            GuiText.draw(graphics, this.font, foil,
                    area.right() - foilWidth, y + 2, foilWidth, FOIL_COLOUR);
        }
    }

    /**
     * The scrollbar, running down the panel's tapered edge rather than beside it.
     *
     * <p>Drawn as an ordinary vertical bar under a shear, which is the same straight line the
     * texture's edge was drawn along - so the two agree by construction instead of by two
     * pieces of arithmetic that have to be kept in step. The bar comes out a parallelogram,
     * which is what a strip lying on a diagonal edge looks like.
     */
    private void renderScrollbar(GuiGraphics graphics) {
        Rect track = layout().scrollbar();
        int maximum = maximumScroll();

        graphics.pose().pushPose();
        graphics.pose().last().pose().mul(taper());
        GatheringSprites.scrollTrack(graphics, track.x(), track.y(), track.width(), track.height());

        if (maximum > 0) {
            int content = rows.size() * ROW_HEIGHT;
            int thumb = Math.max(16, Math.round((float) track.height() * track.height() / content));
            int travel = track.height() - thumb;
            int top = track.y() + Math.round((float) travel * scroll / maximum);
            GatheringSprites.scrollThumb(graphics, track.x(), top, track.width(), thumb);
        }
        graphics.pose().popPose();
    }

    /**
     * The shear that lays a vertical bar along the panel's tapered edge.
     *
     * <p>{@code m10} multiplies y into x, so a point slides left as it goes down by exactly
     * the amount the edge does.
     */
    private Matrix4f taper() {
        return new Matrix4f().m10(layout().taperSlope());
    }

    /** What each mouse button would do to this row, said plainly under the list. */
    private void renderHint(GuiGraphics graphics, int hovered) {
        if (hovered < 0) {
            return;
        }
        Rect hint = layout().hint();
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.deck.hint_take"),
                hint.x(), hint.y(), hint.width(), PENDING_COLOUR);
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.deck.hint_menu"),
                hint.x(), hint.y() + this.font.lineHeight, hint.width(), PENDING_COLOUR);
    }

    /**
     * The hovered card and what it says, in the two frames beside the list.
     *
     * <p>No key to hold. Reading down a decklist is the whole purpose of this screen, and a
     * modifier you have to keep pressed for a hundred rows is a toll on the one thing the
     * screen exists to do. The space is reserved either way, so the frames stay put and only
     * their contents come and go - a layout that jumped as the cursor moved would be worse
     * than one that is sometimes empty.
     */
    private void renderPreview(GuiGraphics graphics, Row row) {
        DeckScreenLayout current = layout();
        Rect card = current.card();
        Rect info = current.info();
        if (card.isEmpty()) {
            return;
        }

        GatheringSprites.frame(graphics, card.x(), card.y(), card.width(), card.height());
        if (!info.isEmpty()) {
            GatheringSprites.frame(graphics, info.x(), info.y(), info.width(), info.height());
        }

        Optional<CardSummary> summary = row == null || row.card() == null
                ? Optional.empty()
                : ClientCardCache.get().summary(row.card());
        if (summary.isEmpty()) {
            return;
        }

        Rect art = card.shrink(DeckScreenLayout.FRAME);
        CardInspectPanel.renderArt(graphics, summary.get(), art.x(), art.y(), art.width(), art.height());
        if (!info.isEmpty()) {
            Rect text = info.shrink(DeckScreenLayout.FRAME);
            CardInspectPanel.renderText(graphics, summary.get(), text.x(), text.y(), text.width(), text.height());
        }
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
        // An open menu gets first refusal on every click, including the ones that miss it -
        // clicking away is how a menu is dismissed, and that click should not also do
        // whatever it landed on.
        if (menu != null) {
            ContextMenu open = menu;
            menu = null;
            if (open.mouseClicked((int) mouseX, (int) mouseY)) {
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && onScrollbar(mouseX, mouseY)) {
            draggingThumb = true;
            scrollTo(mouseY);
            return true;
        }

        int index = rowAt((int) mouseX, (int) mouseY);
        if (index < 0) {
            return false;
        }
        Row row = rows.get(index);
        if (button == 0) {
            GatheringButtons.clickSound();
            ClientNetworking.send(DeckEditPayload.take(hand, row.section(), row.card()));
            return true;
        }
        if (button == 1) {
            menu = menuFor(row, (int) mouseX, (int) mouseY);
            return true;
        }
        return false;
    }

    /**
     * What you can do with this card, given where it currently is.
     *
     * <p>Every pile it is not already in, plus taking a copy. Moving to the command zone is
     * one entry among them rather than what right-click does, because a deck editor whose
     * right-click means "make commander" is a Commander deck editor - and the formats that
     * live and die on their sideboard are exactly the ones that would notice.
     */
    private ContextMenu menuFor(Row row, int x, int y) {
        List<ContextMenu.Entry> entries = ContextMenu.entries();
        for (DeckComponent.Section destination : DeckComponent.Section.values()) {
            if (destination == row.section()) {
                continue;
            }
            entries.add(ContextMenu.Entry.of(
                    Component.translatable("menu.gathering.move_to_"
                            + destination.name().toLowerCase(java.util.Locale.ROOT)),
                    () -> ClientNetworking.send(
                            DeckEditPayload.move(hand, row.section(), destination, row.card()))));
        }
        entries.add(ContextMenu.Entry.of(
                Component.translatable("menu.gathering.take_a_copy"),
                () -> ClientNetworking.send(DeckEditPayload.take(hand, row.section(), row.card()))));

        return ContextMenu.at(this.font, x, y, this.width, this.height, entries);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingThumb) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingThumb = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Whether this click landed on the scrollbar.
     *
     * <p>The bar is drawn sheared, so the hit test undoes the shear rather than testing the
     * upright rectangle - otherwise the bar you can see and the bar you can grab drift
     * further apart the further down the screen you go.
     */
    private boolean onScrollbar(double mouseX, double mouseY) {
        Rect track = layout().scrollbar();
        if (maximumScroll() <= 0) {
            return false;
        }
        double upright = mouseX - layout().taperSlope() * mouseY;
        return upright >= track.x() && upright < track.right() && mouseY >= track.y() && mouseY < track.bottom();
    }

    private void scrollTo(double mouseY) {
        Rect track = layout().scrollbar();
        if (track.height() <= 0) {
            return;
        }
        double fraction = (mouseY - track.y()) / track.height();
        scroll = (int) Math.round(Math.max(0d, Math.min(1d, fraction)) * maximumScroll());
    }

    /** The card row under this point, or -1 for a heading, a gap, or somewhere else entirely. */
    private int rowAt(int mouseX, int mouseY) {
        Rect area = layout().rows();
        if (!area.contains(mouseX, mouseY)) {
            return -1;
        }
        int index = (mouseY - area.y() + scroll) / ROW_HEIGHT;
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
        return Math.max(0, rows.size() * ROW_HEIGHT - layout().rows().height());
    }

    /** Recomputed lazily so a render before {@code init} - or after a resize - is still safe. */
    private DeckScreenLayout layout() {
        if (layout == null) {
            layout = DeckScreenLayout.of(this.width, this.height, this.font.lineHeight);
        }
        return layout;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
