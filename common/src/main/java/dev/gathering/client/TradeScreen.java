package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
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
 * <p>Your side is what you are carrying, with how much of each is on the table beside it -
 * one list rather than two, because "what I have" and "what I have offered" are the same
 * cards and a screen with both would make you read the same names twice. Left-click puts one
 * up, right-click takes one back down.
 * <p>Their side is what they have put up, and it is read-only in the way that matters: this
 * screen never decides anything. Every click is a question to the server, and what is drawn
 * is the answer it sent back - so the two people are looking at one table rather than at two
 * screens that agree most of the time.
 * <p>The agreement lights are the whole point of the screen. Changing anything clears both,
 * which is the server's rule and shows up here as both lights going out at once - so the
 * thing you cannot do, agreeing to something that then changed, is also the thing you can
 * see not happening.
 * <p>Client-only.
 */
public final class TradeScreen extends Screen implements CardPreviewHost {

    private static final int MARGIN = 16;

    /**
     * How far the count on the right of a row is held off the pane's own edge.
     * <p>Without it the number sits against the frame with nothing between them, and beside
     * the second pane - whose names start a few pixels in - it reads as one run of characters
     * rather than as two columns.
     */
    private static final int COUNT_INSET = 4;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 14;
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 20;

    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int UP = 0xFF7FD08A;
    private static final int WAITING = 0xFFE0B15A;

    private TradeViewPayload view;
    private Button agreeButton;
    private Button takeBackButton;

    /** My carried cards, worked out from the inventory the client already has. */
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
        rebuildMine();
    }

    /**
     * Twenty times a second, not once a frame.
     * <p>The row list is read out of the inventory, and the inventory changes when the server
     * says so - which is a tick at best. Rebuilding it in {@link #render} walked thirty-seven
     * stacks and built two maps on every frame drawn, which on a screen that is mostly sitting
     * still waiting for somebody else to click is all of it wasted.
     */
    @Override
    public void tick() {
        super.tick();
        rebuildMine();
    }

    @Override
    protected void init() {
        int buttonTop = buttonTop();
        int inner = panelWidth() - PADDING * 2;
        // Three across the bottom, each a third of the room. Narrowed together rather than
        // stacked, so the row reads left to right in the order it is used: say yes, undo,
        // walk out.
        int buttonWidth = Math.max(40, Math.min(120, (inner - GAP * 2) / 3));
        this.agreeButton = GatheringButtons.of(columnLeft(), buttonTop, buttonWidth,
                BUTTON_HEIGHT, Component.empty(), this::flipAgreement);
        addRenderableWidget(this.agreeButton);

        // Taking an offer back down was one right-click per card, which for a pile of ten is
        // ten clicks to undo one decision. Sends the CLEAR the protocol always had.
        this.takeBackButton = GatheringButtons.of(
                columnLeft() + buttonWidth + GAP, buttonTop, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("screen.gathering.trade.take_it_back"),
                () -> ClientNetworking.send(
                        TradeActionPayload.of(TradeActionPayload.Action.CLEAR)));
        addRenderableWidget(this.takeBackButton);

        addRenderableWidget(GatheringButtons.of(
                columnLeft() + inner - buttonWidth, buttonTop, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("screen.gathering.trade.walk_away"), this::onClose));
        updateButtons();
        rebuildMine();
    }

    private void updateButtons() {
        if (this.agreeButton != null) {
            this.agreeButton.setMessage(Component.translatable(view.iAgreed()
                    ? "screen.gathering.trade.think_again"
                    : "screen.gathering.trade.agree"));
        }
        if (this.takeBackButton != null) {
            // Nothing to take back is not a button worth pressing, and one that does nothing
            // when pressed is worse than one that is visibly not for now.
            this.takeBackButton.active = !view.mine().isEmpty();
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

    // ------------------------------------------------------------------ layout

    private int panelLeft() {
        return MARGIN;
    }

    private int panelWidth() {
        return this.width - MARGIN * 2;
    }

    private int columnLeft() {
        return panelLeft() + PADDING;
    }

    private int columnWidth() {
        return (panelWidth() - PADDING * 2 - GAP) / 2;
    }

    private int theirColumnLeft() {
        return columnLeft() + columnWidth() + GAP;
    }

    private int titleTop() {
        return MARGIN + PADDING;
    }

    private int hintTop() {
        return titleTop() + this.font.lineHeight + 2;
    }

    private int headingTop() {
        return hintTop() + this.font.lineHeight + GAP;
    }

    private int lightTop() {
        return headingTop() + this.font.lineHeight + 1;
    }

    /** Where the first card row is drawn, under both headings. */
    private int rowsTop() {
        return lightTop() + this.font.lineHeight + GAP;
    }

    private int buttonTop() {
        return this.height - MARGIN - PADDING - BUTTON_HEIGHT;
    }

    /**
     * The panel goes here, not in {@link #render}.
     * <p>{@code Screen#render} calls this itself and applies a full-screen blur, so anything
     * drawn before {@code super.render} is blurred with the world behind it. Without a panel
     * at all - which is how this screen shipped - the two columns were white text over a
     * blurred sky, and which side a name was on took a moment to work out.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panelLeft(), MARGIN, panelWidth(), this.height - MARGIN * 2);

        // One recess per side, so the table has two halves you can see the edge of rather
        // than two lists that happen to start at different x.
        int top = headingTop() - 3;
        int bottom = buttonTop() - GAP;
        GatheringSprites.inset(graphics, columnLeft() - 3, top, columnWidth() + 6, bottom - top);
        GatheringSprites.inset(graphics, theirColumnLeft() - 3, top, columnWidth() + 6, bottom - top);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = columnLeft();
        int right = theirColumnLeft();
        int column = columnWidth();

        graphics.drawString(this.font, getTitle(), left, titleTop(), TEXT, false);
        // The instruction, once, across the whole panel. It was the column heading, which put
        // a sentence into a space one card name wide and drew it over the first row.
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.trade.how"),
                left, hintTop(), panelWidth() - PADDING * 2, DIM);

        graphics.drawString(this.font,
                Component.translatable("screen.gathering.trade.yours"),
                left, headingTop(), TEXT, false);
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.trade.theirs", view.other()),
                right, headingTop(), column, TEXT);

        drawLight(graphics, left, lightTop(), column, view.iAgreed(),
                "screen.gathering.trade.you_agreed", "screen.gathering.trade.you_have_not");
        drawLight(graphics, right, lightTop(), column, view.theyAgreed(),
                "screen.gathering.trade.they_agreed", "screen.gathering.trade.they_have_not");

        drawMine(graphics, left, rowsTop(), column, mouseX, mouseY);
        drawTheirs(graphics, right, rowsTop(), column);
    }

    private void drawLight(GuiGraphics graphics, int x, int y, int width, boolean agreed,
            String yes, String no) {
        GuiText.draw(graphics, this.font, Component.translatable(agreed ? yes : no), x, y, width,
                agreed ? UP : WAITING);
    }

    private void drawMine(GuiGraphics graphics, int x, int top, int width,
            int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        if (mine.isEmpty()) {
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.trade.nothing_carried"),
                    x, top + 3, width, DIM);
        }
        for (int index = 0; index < mine.size() && index < rowsThatFit(); index++) {
            Row row = mine.get(index);
            int y = top + index * ROW_HEIGHT;
            String count = row.up() + "/" + row.carried();
            int countWidth = this.font.width(count);
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                GatheringSprites.draw(graphics, Element.ROW_HOVER,
                        x - 2, y, width + 2, ROW_HEIGHT);
                hovered = CardItem.of(row.card());
            }
            // Trimmed to what is left after the count. A long name used to be drawn straight
            // through it, and the number - which is the thing being changed by clicking - was
            // the half that lost.
            GuiText.draw(graphics, this.font, nameOf(row.card()), x, y + 3,
                    width - countWidth - GAP - COUNT_INSET, row.up() > 0 ? TEXT : DIM);
            graphics.drawString(this.font, count, x + width - countWidth - COUNT_INSET, y + 3,
                    row.up() > 0 ? UP : DIM, false);
        }
        ClientHoverState.setHovered(hovered);
    }

    private void drawTheirs(GuiGraphics graphics, int x, int top, int width) {
        if (view.theirs().isEmpty()) {
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.trade.nothing_yet"),
                    x, top + 3, width, DIM);
            return;
        }
        for (int index = 0; index < view.theirs().size() && index < rowsThatFit(); index++) {
            TradeViewPayload.Pile pile = view.theirs().get(index);
            int y = top + index * ROW_HEIGHT;
            String count = String.valueOf(pile.count());
            int countWidth = this.font.width(count);
            GuiText.draw(graphics, this.font, nameOf(pile.card()), x, y + 3,
                    width - countWidth - GAP - COUNT_INSET, TEXT);
            graphics.drawString(this.font, count, x + width - countWidth - COUNT_INSET, y + 3,
                    UP, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int column = columnWidth();
        // The cast truncates toward zero, so a click just above the first row still made
        // index 0 and adjusted the top pile; anything above the rows is not a row.
        if (mouseY < rowsTop()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int index = (int) ((mouseY - rowsTop()) / ROW_HEIGHT);
        if (mouseX >= columnLeft() && mouseX < columnLeft() + column
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
     * <p>Read off the client's own inventory rather than sent: it is already here, it is
     * already correct, and a copy on the wire would be a second answer to drift from it.
     */
    private void rebuildMine() {
        mine.clear();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Map<CardComponent, Integer> carried = new LinkedHashMap<>();
        // The off hand too, and for the same reason the server counts it: a card held out
        // toward somebody must not be the one card the trade cannot see.
        List<ItemStack> holding = new ArrayList<>(this.minecraft.player.getInventory().items);
        holding.addAll(this.minecraft.player.getInventory().offhand);
        for (ItemStack stack : holding) {
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

    /** How many of my rows are drawn. For the scene that photographs this screen. */
    int listedMine() {
        return Math.min(mine.size(), rowsThatFit());
    }

    /** How many of their piles are drawn. */
    int listedTheirs() {
        return Math.min(view.theirs().size(), rowsThatFit());
    }

    private int rowsThatFit() {
        return Math.max(1, (buttonTop() - GAP - rowsTop()) / ROW_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
