package dev.gathering.client;

import dev.gathering.core.story.CardStory;
import dev.gathering.core.ui.CardOverviewLayout;
import dev.gathering.core.ui.InterfaceScale;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import dev.gathering.network.CardSummary;
import dev.gathering.network.CollectionCardAskPayload;
import dev.gathering.network.CollectionCardPayload;
import dev.gathering.network.CollectionTakePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * One card out of a collection, looked at properly: the card as large as the window allows, everything
 * it says, how many copies are in the box, and where each copy with a history has been.
 * <p>The owner asked for a better way to see a card's history. It was only ever under the read-key
 * panel of a card in somebody's hand, fitted in below the rules text - and a card put away in a box
 * could not be read at all, because the collection's grid is of printings rather than of copies.
 * The server keeps every copy's history beside the count, and this asks for them.
 * <p>Opened by shift-clicking a card in the collection, or by clicking one where the box is read-only.
 * Taking from here is the same two amounts the grid offers, and the grid is asked again on the way back.
 * <p>Client-only.
 */
public final class CardOverviewScreen extends ChildScreen {

    private static final int TEXT = 0xFFDDE3EC;
    private static final int DIM = 0xFF8A94A3;
    private static final int STORY = 0xFFD9A441;

    private final BlockPos where;
    private final CardComponent card;
    private final CardSummary about;
    private final boolean mayTake;

    /** How many copies the box holds, or -1 until the server has said. */
    private int copies = -1;
    private List<CardStory> stories = List.of();
    private int untold;
    private boolean flipped;

    /** How many lines of the history are scrolled past. */
    private int scrolled;

    private CardOverviewLayout layout = CardOverviewLayout.of(854, 480, 20);

    private CardOverviewScreen(CollectionScreen collection, BlockPos where, CardComponent card, CardSummary about,
            boolean mayTake) {
        super(Component.translatable("screen.gathering.card_overview"), collection, false);
        this.where = where;
        this.card = card;
        this.about = about;
        this.mayTake = mayTake;
        this.flipped = card.flipped();
    }

    /** Opens the overview of one card in a collection, and asks the server about its copies. */
    public static void open(CollectionScreen collection, BlockPos where, CardComponent card, CardSummary about,
            boolean mayTake) {
        Minecraft.getInstance().setScreen(new CardOverviewScreen(collection, where, card, about, mayTake));
        ClientNetworking.send(new CollectionCardAskPayload(where, card));
    }

    /** The server's answer about a card, for the overview showing it. */
    public static void accept(CollectionCardPayload payload) {
        if (Minecraft.getInstance().screen instanceof CardOverviewScreen open
                && open.where.equals(payload.where()) && open.card.equals(payload.card())) {
            open.copies = payload.copies();
            open.stories = payload.stories();
            open.untold = payload.untold();
            open.scrolled = Math.min(open.scrolled, Math.max(0, open.historyLines().size() - 1));
            open.rebuildWidgets();
        }
    }

    /** How many copies the box holds, for the scripted run; -1 until the server has said. */
    int copies() {
        return copies;
    }

    /** The histories shown, for the scripted run. */
    List<CardStory> stories() {
        return stories;
    }

    @Override
    protected void init() {
        int row = Math.max(20, Math.round(20 * InterfaceScale.asFraction(ClientSettings.controlScale())));
        layout = CardOverviewLayout.of(this.width, this.height, row);
        Rect bar = layout.buttons();
        List<Button> buttons = new ArrayList<>();
        if (mayTake) {
            Button one = button("screen.gathering.card_overview.take_one", () -> take(1));
            Button four = button("screen.gathering.card_overview.take_four", () -> take(4));
            one.active = copies != 0;
            four.active = copies != 0;
            buttons.add(one);
            buttons.add(four);
        }
        if (about != null && about.hasAnotherSide()) {
            buttons.add(button("screen.gathering.card_overview.turn_over", () -> flipped = !flipped));
        }
        Button back = button("gui.back", this::onClose);
        // Left to right from the start of the bar, Back at the far end, each as wide as its word asks
        // and shared out where they do not all fit.
        int wanted = 0;
        for (Button each : buttons) {
            wanted += each.getWidth() + CardOverviewLayout.GAP;
        }
        int room = bar.width() - back.getWidth() - CardOverviewLayout.GAP;
        int x = bar.x();
        for (Button each : buttons) {
            int width = wanted <= room ? each.getWidth()
                    : Math.max(1, (room - CardOverviewLayout.GAP * buttons.size()) / Math.max(1, buttons.size()));
            each.setRectangle(width, bar.height(), x, bar.y());
            addRenderableWidget(each);
            x += width + CardOverviewLayout.GAP;
        }
        back.setRectangle(Math.min(back.getWidth(), bar.width()), bar.height(), bar.right() - Math.min(back.getWidth(), bar.width()),
                bar.y());
        addRenderableWidget(back);
    }

    private Button button(String key, Runnable action) {
        Component label = Component.translatable(key);
        int width = Math.max(60, Math.round(this.font.width(label) * GuiText.askedScale()) + 16);
        return GatheringButtons.of(0, 0, width, 20, label, action);
    }

    private void take(int howMany) {
        ClientNetworking.send(new CollectionTakePayload(where, card, howMany));
        // Asked again on the same connection, so the take has been dealt with by the time the answer is
        // worked out. The grid behind asks for its own page when it is returned to.
        ClientNetworking.send(new CollectionCardAskPayload(where, card));
        GatheringButtons.clickSound();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Rect art = layout.card();
        if (about != null && !art.isEmpty()) {
            CardInspectPanel.renderArt(graphics, about, flipped, art.x(), art.y(), art.width(), art.height());
            if (card.foil()) {
                GuiText.drawFlushRight(graphics, this.font, Component.literal("✦"), art.right() - 4, art.y() + 3, 1f,
                        0xFFE8C86A);
            }
        } else if (!art.isEmpty()) {
            graphics.blit(CardFaceRenderer.CARD_BACK, art.x(), art.y(), 0f, 0f, art.width(), art.height(), art.width(),
                    art.height());
        }
        Rect words = layout.words();
        if (about != null && !words.isEmpty()) {
            GatheringSprites.panel(graphics, words.x(), words.y(), words.width(), words.height());
            CardInspectPanel.renderText(graphics, about, words.x() + 6, words.y() + 6, words.width() - 12,
                    words.height() - 12);
        }
        drawHistory(graphics);
    }

    /** How many copies there are, then every history a line at a time, scrolled with the wheel. */
    private void drawHistory(GuiGraphics graphics) {
        Rect box = layout.history();
        if (box.isEmpty()) {
            return;
        }
        GatheringSprites.panel(graphics, box.x(), box.y(), box.width(), box.height());
        int x = box.x() + 6;
        int width = box.width() - 12;
        int line = Math.round(this.font.lineHeight * GuiText.askedScale()) + 2;
        int y = box.y() + 6;
        Component count = copies < 0
                ? Component.translatable("screen.gathering.card_overview.asking")
                : Component.translatable("screen.gathering.card_overview.copies", copies,
                        stories.size() + untold);
        GuiText.draw(graphics, this.font, count, x, y, width, TEXT);
        y += line + 2;
        List<Line> lines = historyLines();
        if (copies >= 0 && lines.isEmpty()) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.card_overview.no_history"), x, y,
                    width, DIM);
            return;
        }
        for (int index = Math.min(scrolled, lines.size()); index < lines.size(); index++) {
            if (y + line > box.bottom() - 4) {
                break;
            }
            Line each = lines.get(index);
            GuiText.draw(graphics, this.font, Component.literal(each.text()), x + each.indent(), y,
                    width - each.indent(), each.color());
            y += line;
        }
    }

    /** One line of the history as drawn. */
    private record Line(String text, int indent, int color) {
    }

    /** Every copy's history, wrapped to the box: a heading per copy, then its chapters under it. */
    private List<Line> historyLines() {
        List<Line> lines = new ArrayList<>();
        int width = Math.max(40, Math.round((layout.history().width() - 24) / GuiText.askedScale()));
        int copy = 1;
        for (CardStory story : stories) {
            lines.add(new Line(Component.translatable("screen.gathering.card_overview.copy", copy++).getString(), 0, TEXT));
            for (String sentence : CardInspectPanel.historyOf(story)) {
                for (FormattedCharSequence wrapped : this.font.split(Component.literal(sentence), width)) {
                    StringBuilder words = new StringBuilder();
                    wrapped.accept((index, style, point) -> {
                        words.appendCodePoint(point);
                        return true;
                    });
                    lines.add(new Line(words.toString(), 8, STORY));
                }
            }
        }
        if (untold > 0) {
            lines.add(new Line(Component.translatable("screen.gathering.card_overview.untold", untold).getString(), 0, DIM));
        }
        return lines;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout.history().contains((int) mouseX, (int) mouseY)) {
            int most = Math.max(0, historyLines().size() - 1);
            scrolled = Math.clamp(scrolled - (int) Math.signum(scrollY), 0, most);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
