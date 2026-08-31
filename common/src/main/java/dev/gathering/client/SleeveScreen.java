package dev.gathering.client;

import dev.gathering.core.card.Sleeve;
import dev.gathering.core.ui.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Which sleeves.
 *
 * <p>Every sleeve drawn as the card it produces, at the size a card sits on a table, because
 * that is the only honest way to pick one: a list of names would be twenty-five rows of words
 * for a choice that is entirely about what it looks like.
 *
 * <p>Opened from a deck, and it changes the deck rather than the player - somebody with three
 * decks sleeves them differently, which is most of the point.
 *
 * <p>Client-only.
 */
public final class SleeveScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int MARGIN = 10;
    private static final int GAP = 4;
    private static final int ROW = 18;

    /** The size a swatch wants to be, and the smallest it is worth drawing at. */
    private static final int PREFERRED_WIDTH = 30;
    private static final int SMALLEST_WIDTH = 14;

    /**
     * How many to a row.
     *
     * <p>Nine, because the plain sleeves are the classic one and the sixteen dyes - seventeen,
     * which is nine and eight - and the pictured ones then start a row of their own. A grid
     * that ran the two groups together would put a sword between two shades of blue.
     */
    private static final int ACROSS = 9;

    private final Sleeve current;
    private final Consumer<Sleeve> chosen;

    private Rect panel = Rect.NONE;
    private int hovered = -1;

    /** Worked out in init against the window, because a fixed size does not fit every scale. */
    private int cardWidth = PREFERRED_WIDTH;
    private int cardHeight = PREFERRED_WIDTH * 7 / 5;
    private int plainRows = 2;

    /** How many sleeves carry no picture. Counted once, not once per swatch per frame. */
    private int plainCount = Sleeve.values().length;

    public SleeveScreen(Sleeve current, Consumer<Sleeve> chosen, Screen back) {
        // Without the deck list behind it. Every swatch here is a picture of a card, and a
        // list of card names showing through them is noise at best - see ChildScreen, where
        // it is also what stopped the land buttons drawing over the grid.
        super(Component.translatable("screen.gathering.sleeves"), back, false);
        this.current = current == null ? Sleeve.DEFAULT : current;
        this.chosen = chosen;
    }

    @Override
    protected void init() {
        plainCount = 0;
        for (Sleeve sleeve : Sleeve.values()) {
            if (!sleeve.hasEmblem()) {
                plainCount++;
            }
        }
        plainRows = (plainCount + ACROSS - 1) / ACROSS;
        int rows = plainRows + (Sleeve.values().length - plainCount + ACROSS - 1) / ACROSS;

        // Shrunk to fit rather than clamped. The panel used to be laid out at one size and
        // then cut off at the window's, which on a scaled-up interface put the last row of
        // sleeves underneath the Done button - a row of choices nobody could reach.
        int room = this.height - MARGIN * 2 - ROW - GAP - ROW - MARGIN * 2;
        cardWidth = PREFERRED_WIDTH;
        while (cardWidth > SMALLEST_WIDTH && rows * (cardWidth * 7 / 5 + GAP) > room) {
            cardWidth--;
        }
        cardHeight = cardWidth * 7 / 5;

        int width = MARGIN * 2 + ACROSS * cardWidth + (ACROSS - 1) * GAP;
        int height = MARGIN * 2 + ROW + GAP + rows * (cardHeight + GAP) + ROW;
        panel = new Rect(
                (this.width - width) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                width,
                Math.min(height, this.height - MARGIN * 2));

        addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, panel.bottom() - MARGIN - ROW,
                panel.width() - MARGIN * 2, ROW,
                Component.translatable("gui.done"), this::onClose));
    }

    /**
     * Where the swatch for that sleeve is drawn. Asked by both the drawing and the clicking.
     *
     * <p>The pictured sleeves begin a row of their own, so the grid reads as the two kinds of
     * thing it holds rather than as twenty-five squares in a heap.
     */
    private Rect swatch(int index) {
        int column;
        int row;
        if (index < plainCount) {
            column = index % ACROSS;
            row = index / ACROSS;
        } else {
            column = (index - plainCount) % ACROSS;
            row = plainRows + (index - plainCount) / ACROSS;
        }
        int left = panel.x() + MARGIN + column * (cardWidth + GAP);
        int top = panel.y() + MARGIN + ROW + GAP + row * (cardHeight + GAP);
        return new Rect(left, top, cardWidth, cardHeight);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiText.drawCentered(graphics, this.font, this.title,
                panel.x() + panel.width() / 2, panel.y() + 5, panel.width() - MARGIN * 2, LABEL);

        hovered = -1;
        Sleeve[] sleeves = Sleeve.values();
        for (int index = 0; index < sleeves.length; index++) {
            Rect where = swatch(index);
            boolean under = where.contains((int) mouseX, (int) mouseY);
            if (under) {
                hovered = index;
            }
            CardSleeves.draw(graphics, sleeves[index],
                    where.x(), where.y(), where.width(), where.height());
            // The one in use gets the ring the rest of the mod uses for "this is the one", and
            // the cursor gets the one it uses for "this is what you are about to pick".
            if (sleeves[index] == current) {
                GatheringSprites.draw(graphics, GatheringSprites.Element.CHOSEN_RING,
                        where.x(), where.y(), where.width(), where.height());
            } else if (under) {
                GatheringSprites.draw(graphics, GatheringSprites.Element.HOVER_RING,
                        where.x(), where.y(), where.width(), where.height());
            }
        }
        if (hovered >= 0) {
            graphics.renderTooltip(this.font, nameOf(sleeves[hovered]), mouseX, mouseY);
        }
    }

    /** What a sleeve is called. Its own key, so the sixteen dyes read as dyes and not as enums. */
    private static Component nameOf(Sleeve sleeve) {
        return Component.translatable(
                "sleeve.gathering." + sleeve.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Sleeve[] sleeves = Sleeve.values();
            for (int index = 0; index < sleeves.length; index++) {
                if (swatch(index).contains((int) mouseX, (int) mouseY)) {
                    chosen.accept(sleeves[index]);
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Which swatch the cursor is on, or -1. For the scripted run; see DevScene. */
    public int hoveredSwatch() {
        return hovered;
    }

    /** Where that sleeve's swatch is, so a scripted click can aim at it. */
    public Rect swatchOf(Sleeve sleeve) {
        Sleeve[] sleeves = Sleeve.values();
        for (int index = 0; index < sleeves.length; index++) {
            if (sleeves[index] == sleeve) {
                return swatch(index);
            }
        }
        return Rect.NONE;
    }
}
