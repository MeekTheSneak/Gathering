package dev.gathering.client;

import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import dev.gathering.network.TradeActionPayload;
import dev.gathering.network.TradeViewPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Two people and what they are putting up.
 *
 * <p>Your side is what you are carrying, with how much of each is on the table beside it -
 * one list rather than two, because "what I have" and "what I have offered" are the same
 * cards and a screen with both would make you read the same names twice. Left-click puts one
 * up, right-click takes one back down.
 *
 * <p>Their side is what they have put up, and it is read-only in the way that matters: this
 * screen never decides anything. Every click is a question to the server, and what is drawn
 * is the answer it sent back - so the two people are looking at one table rather than at two
 * screens that agree most of the time.
 *
 * <p>The agreement lights are the whole point of the screen. Changing anything clears both,
 * which is the server's rule and shows up here as both lights going out at once - so the
 * thing you cannot do, agreeing to something that then changed, is also the thing you can
 * see not happening.
 *
 * <p>Client-only.
 */
public final class TradeScreen extends Screen implements CardPreviewHost {

    private static final int MARGIN = 16;
    private static final int ROW_HEIGHT = 14;
    private static final int HEADER = 44;
    private static final int BUTTON_HEIGHT = 20;

    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int UP = 0xFF7FD08A;
    private static final int WAITING = 0xFFE0B15A;
    private static final int ROW_HOVER = 0x30FFFFFF;

    private TradeViewPayload view;
    private Button agreeButton;

    /** My carried cards, worked out each frame from the inventory the client already has. */
    private final List<Row> mine = new ArrayList<>();

    /** One of my cards: what it is, how many I carry, how many are on the table. */
    private record Row(CardComponent card, int carried, int up) {
    }

    private TradeScreen(TradeViewPayload view) {
        super(Component.translatable("screen.gathering.trade", view.other()));
        this.view = view;
    }

    /** Opens the trade, or brings the one on screen up to date. */
    public static void accept(TradeViewPayload view) {
        Minecraft client = Minecraft.getInstance();
        if (view.closed()) {
            if (client.screen instanceof TradeScreen) {
                client.setScreen(null);
            }
            return;
        }
        if (client.screen instanceof TradeScreen open) {
            open.update(view);
            return;
        }
        client.setScreen(new TradeScreen(view));
    }

    private void update(TradeViewPayload next) {
        this.view = next;
        updateButtons();
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(120, (this.width - MARGIN * 3) / 2);
        int buttonTop = this.height - MARGIN - BUTTON_HEIGHT;
        this.agreeButton = GatheringButtons.of(MARGIN, buttonTop, buttonWidth, BUTTON_HEIGHT,
                Component.empty(), this::flipAgreement);
        addRenderableWidget(this.agreeButton);
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - buttonWidth, buttonTop, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("screen.gathering.trade.walk_away"), this::onClose));
        updateButtons();
    }

    private void updateButtons() {
        if (this.agreeButton != null) {
            this.agreeButton.setMessage(Component.translatable(view.iAgreed()
                    ? "screen.gathering.trade.think_again"
                    : "screen.gathering.trade.agree"));
        }
    }

    private void flipAgreement() {
        ClientNetworking.send(TradeActionPayload.of(view.iAgreed()
                ? TradeActionPayload.Action.THINK_AGAIN
                : TradeActionPayload.Action.AGREE));
    }

    @Override
    public void onClose() {
        ClientNetworking.send(TradeActionPayload.of(TradeActionPayload.Action.CLOSE));
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        rebuildMine();
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(this.font, getTitle(), MARGIN, MARGIN, TEXT, false);
        int column = (this.width - MARGIN * 3) / 2;

        drawLight(graphics, MARGIN, MARGIN + 14, view.iAgreed(),
                "screen.gathering.trade.you_agreed", "screen.gathering.trade.you_have_not");
        drawLight(graphics, MARGIN * 2 + column, MARGIN + 14, view.theyAgreed(),
                "screen.gathering.trade.they_agreed", "screen.gathering.trade.they_have_not");

        graphics.drawString(this.font,
                Component.translatable("screen.gathering.trade.yours"),
                MARGIN, MARGIN + 30, DIM, false);
        graphics.drawString(this.font,
                Component.translatable("screen.gathering.trade.theirs", view.other()),
                MARGIN * 2 + column, MARGIN + 30, DIM, false);

        drawMine(graphics, MARGIN, HEADER, column, mouseX, mouseY);
        drawTheirs(graphics, MARGIN * 2 + column, HEADER, column);
    }

    private void drawLight(GuiGraphics graphics, int x, int y, boolean agreed,
            String yes, String no) {
        graphics.drawString(this.font, Component.translatable(agreed ? yes : no), x, y,
                agreed ? UP : WAITING, false);
    }

    private void drawMine(GuiGraphics graphics, int x, int top, int width,
            int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        for (int index = 0; index < mine.size() && index < rowsThatFit(); index++) {
            Row row = mine.get(index);
            int y = top + index * ROW_HEIGHT;
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                graphics.fill(x - 2, y, x + width, y + ROW_HEIGHT, ROW_HOVER);
                hovered = CardItem.of(row.card());
            }
            graphics.drawString(this.font, nameOf(row.card()), x, y + 3,
                    row.up() > 0 ? TEXT : DIM, false);
            String count = row.up() + "/" + row.carried();
            graphics.drawString(this.font, count,
                    x + width - this.font.width(count), y + 3,
                    row.up() > 0 ? UP : DIM, false);
        }
        ClientHoverState.setHovered(hovered);
    }

    private void drawTheirs(GuiGraphics graphics, int x, int top, int width) {
        if (view.theirs().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.gathering.trade.nothing_yet"),
                    x, top + 3, DIM, false);
            return;
        }
        for (int index = 0; index < view.theirs().size() && index < rowsThatFit(); index++) {
            TradeViewPayload.Pile pile = view.theirs().get(index);
            int y = top + index * ROW_HEIGHT;
            graphics.drawString(this.font, nameOf(pile.card()), x, y + 3, TEXT, false);
            String count = String.valueOf(pile.count());
            graphics.drawString(this.font, count,
                    x + width - this.font.width(count), y + 3, UP, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int column = (this.width - MARGIN * 3) / 2;
        int index = (int) ((mouseY - HEADER) / ROW_HEIGHT);
        if (mouseX >= MARGIN && mouseX < MARGIN + column
                && index >= 0 && index < mine.size() && index < rowsThatFit()) {
            Row row = mine.get(index);
            // Left puts one up, right takes one back down. The same two buttons the deck
            // screen uses for moving a card between piles, because it is the same gesture.
            int wanted = button == 0
                    ? Math.min(row.carried(), row.up() + 1)
                    : Math.max(0, row.up() - 1);
            if (wanted != row.up()) {
                GatheringButtons.clickSound();
                ClientNetworking.send(TradeActionPayload.put(row.card(), wanted));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * What I am carrying, with how much of each is already up.
     *
     * <p>Read off the client's own inventory rather than sent: it is already here, it is
     * already correct, and a copy on the wire would be a second answer to drift from it.
     */
    private void rebuildMine() {
        mine.clear();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Map<CardComponent, Integer> carried = new LinkedHashMap<>();
        for (ItemStack stack : this.minecraft.player.getInventory().items) {
            CardItem.cardOf(stack)
                    .map(CardComponent::faceUp)
                    .ifPresent(card -> carried.merge(card, stack.getCount(), Integer::sum));
        }
        Map<CardComponent, Integer> up = new LinkedHashMap<>();
        for (TradeViewPayload.Pile pile : view.mine()) {
            up.merge(pile.card().faceUp(), pile.count(), Integer::sum);
        }
        carried.forEach((card, count) ->
                mine.add(new Row(card, count, up.getOrDefault(card, 0))));
    }

    private Component nameOf(CardComponent card) {
        return ClientCardCache.get().summary(card)
                .map(CardSummary::front)
                .<Component>map(face -> Component.literal(face.name()))
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
    }

    private int rowsThatFit() {
        return Math.max(1, (this.height - HEADER - MARGIN * 2 - BUTTON_HEIGHT) / ROW_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
