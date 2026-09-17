package dev.gathering.client;

import dev.gathering.core.ui.ListScroll;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.ChooseDeckPayload;
import dev.gathering.network.OpenDeckPickerPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Choose the deck to play: every deck in the player's inventory, one row each, and borrowing one where the
 * server lends them.
 * <p>How a deck goes into a game that is on. It replaced right-clicking the table with the right deck in
 * hand, which meant finding it, putting it in a hotbar slot and aiming. The list is read from this client's
 * own inventory; a row sends only its slot, and the server reads the deck out of the slot itself.
 * <p>Closing it chooses nothing and goes to the board. Right-clicking the table from the seat opens it again.
 * <p>Client-only.
 */
public final class DeckPickerScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 10;
    private static final int GAP = 4;
    private static final int ROW = 20;
    private static final int PANEL_WIDTH = 260;
    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;

    private final BlockPos table;
    private final boolean loaners;
    private final List<Row> decks = new ArrayList<>();
    private int scroll;
    private Rect panel = Rect.NONE;
    private Rect track = Rect.NONE;

    /** A deck in the inventory: which slot, and what the row says. */
    record Row(int slot, Component label) {
    }

    DeckPickerScreen(BlockPos table, boolean loaners) {
        super(Component.translatable("screen.gathering.deck_picker"));
        this.table = table;
        this.loaners = loaners;
    }

    /** Whether the last list offered borrowing, for coming back to it from the question about a deck. */
    private static boolean lastLoaners;

    public static void accept(OpenDeckPickerPayload offer) {
        lastLoaners = offer.loaners();
        Minecraft.getInstance().setScreen(new DeckPickerScreen(offer.table(), offer.loaners()));
    }

    static boolean lastLoaners() {
        return lastLoaners;
    }

    /** Forgets whether the last server lent decks, for a disconnect. */
    public static void clear() {
        lastLoaners = false;
    }

    @Override
    protected void init() {
        readTheInventory();
        int width = Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
        int showing = rowsThatFit();
        scroll = ListScroll.within(scroll, decks.size(), showing);
        int listed = Math.max(1, showing);
        int height = PADDING * 2 + this.font.lineHeight + GAP + listed * (ROW + GAP) + GAP * 2 + ROW;
        panel = new Rect((this.width - width) / 2, Math.max(4, (this.height - height) / 2), width,
                Math.min(height, this.height - 8));
        int listTop = panel.y() + PADDING + this.font.lineHeight + GAP;
        boolean scrolls = ListScroll.scrolls(decks.size(), showing);
        int rowWidth = panel.width() - PADDING * 2
                - (scrolls ? dev.gathering.core.ui.ListScreenLayout.SCROLLBAR + dev.gathering.core.ui.ListScreenLayout.SCROLLBAR_GAP : 0);
        for (int index = 0; index < showing; index++) {
            Row row = decks.get(scroll + index);
            addRenderableWidget(GatheringButtons.of(panel.x() + PADDING, listTop + index * (ROW + GAP), rowWidth, ROW,
                    row.label(), () -> choose(row.slot())));
        }
        track = scrolls
                ? new Rect(panel.right() - PADDING - dev.gathering.core.ui.ListScreenLayout.SCROLLBAR, listTop,
                        dev.gathering.core.ui.ListScreenLayout.SCROLLBAR, showing * (ROW + GAP) - GAP)
                : Rect.NONE;
        int decideTop = panel.bottom() - PADDING - ROW;
        int inner = panel.width() - PADDING * 2;
        if (loaners) {
            int half = (inner - GAP) / 2;
            addRenderableWidget(GatheringButtons.of(panel.x() + PADDING, decideTop, half, ROW,
                    Component.translatable("screen.gathering.deck_picker.not_now"), this::onClose));
            addRenderableWidget(GatheringButtons.of(panel.right() - PADDING - half, decideTop, half, ROW,
                    Component.translatable("screen.gathering.deck_picker.borrow"), () -> choose(ChooseDeckPayload.BORROW)));
        } else {
            addRenderableWidget(GatheringButtons.of(panel.x() + PADDING, decideTop, inner, ROW,
                    Component.translatable("screen.gathering.deck_picker.not_now"), this::onClose));
        }
    }

    private void readTheInventory() {
        decks.clear();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        var inventory = this.minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            DeckComponent deck = DeckItem.deckOf(stack).orElse(null);
            if (deck != null) {
                decks.add(new Row(slot, Component.translatable("screen.gathering.deck_picker.row",
                        deck.name().isBlank() ? stack.getHoverName() : Component.literal(deck.name()), deck.deckSize())));
            }
        }
    }

    /** How many decks the window has room for. */
    private int rowsThatFit() {
        int room = this.height - 8 - PADDING * 2 - this.font.lineHeight - GAP * 3 - ROW;
        return Math.max(0, Math.min(decks.size(), room / (ROW + GAP)));
    }

    private void choose(int slot) {
        ClientNetworking.send(new ChooseDeckPayload(table, slot, false));
        // Borrowing opens the loaner list; a deck going down opens the board. Neither needs this screen.
        this.minecraft.setScreen(null);
    }

    /** Closing it goes to the board, if the game is still on. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(ClientTableState.viewOf(table).isPresent() ? new TableScreen(table) : null);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int showing = rowsThatFit();
        if (ListScroll.scrolls(decks.size(), showing)) {
            int wanted = ListScroll.after(scroll, decks.size(), showing, scrollY);
            if (wanted != scroll) {
                scroll = wanted;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Prompts.panel(graphics, panel);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int inner = panel.width() - PADDING * 2;
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + PADDING,
                inner, TEXT);
        if (decks.isEmpty()) {
            GuiText.drawCentered(graphics, this.font, Component.translatable("screen.gathering.deck_picker.none"),
                    panel.x() + panel.width() / 2, panel.y() + PADDING + this.font.lineHeight + GAP + (ROW - this.font.lineHeight) / 2,
                    inner, DIM);
        }
        if (track != Rect.NONE) {
            ListScrollbar.draw(graphics, track, scroll, rowsThatFit(), decks.size());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The rows, for the scripted client. */
    List<Row> rows() {
        return List.copyOf(decks);
    }

    BlockPos table() {
        return table;
    }
}
