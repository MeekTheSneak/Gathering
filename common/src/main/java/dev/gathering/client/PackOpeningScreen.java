package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.ui.PackGlow;
import dev.gathering.core.ui.PackLayout;
import dev.gathering.core.ui.PackTear;
import dev.gathering.core.ui.PackWrapper;
import dev.gathering.item.CardComponent;
import dev.gathering.network.CardSummary;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Opening a booster by hand.
 *
 * <p>The pack comes to the middle of the screen and stays sealed until somebody tears it.
 * Take hold of the corner, drag across, and the wrapper comes apart where the cursor goes -
 * and light comes out of the tear before a single card is shown, yellow for a rare inside and
 * orange for a mythic. That moment is what a booster is for, and everything here exists to
 * put it before the cards rather than after them.
 *
 * <p>Nothing on this screen decides anything. The cards were in the inventory before it
 * opened, so closing it early, disconnecting, or never finishing the tear costs nobody a
 * card. What it does is let you find out.
 *
 * <p>Client-only.
 */
public final class PackOpeningScreen extends Screen {

    /** How big the pack is drawn, as a fraction of the shorter side of the window. */
    private static final double PACK_HEIGHT = 0.62;

    /** A booster wrapper is about half again as tall as it is wide. */
    private static final double PACK_SHAPE = 0.66;

    /** How deep the torn strip is, as a fraction of the pack's height. */
    private static final double CRIMP = 0.16;

    /** How the cards are laid out once the wrapper is off. */
    private static final int GAP = 4;
    private static final int MARGIN_PIXELS = 16;

    /**
     * Which rows of the wrapper texture are which.
     *
     * <p>The item texture is one picture of a whole pack, and this screen draws two pieces of
     * one - so it cuts them from where they are. Named here so a new wrapper is these four
     * numbers rather than a hunt through the drawing.
     */
    private static final int WRAPPER_PIXELS = 16;
    private static final int MARGIN = 2;
    private static final int CRIMP_ROW = 1;
    private static final int CRIMP_ROWS = 4;
    private static final int BODY_ROW = 5;
    private static final int BODY_ROWS = 10;

    private final String setCode;
    private final String kind;
    private final List<CardComponent> cards;

    private PackTear tear = PackTear.unopened(1, 0L);
    private List<CardComponent> revealed = List.of();

    // Named for the pack rather than for the screen: Screen has width and height of its own,
    // and a field here called either would shadow it silently.
    private int packX;
    private int packY;
    private int packWidth;
    private int packHeight;

    /**
     * What color comes out of the tear, and whether that answer is final.
     *
     * <p>Worked out from the summaries this client holds, which arrive in the packets just
     * ahead of the pack - so almost always before the screen opens, and not always. Until
     * every card in the pack has one it is worked out again each frame, because a pack that
     * decided it had nothing worth glowing about before its cards arrived would sit there
     * dark through the one moment the whole ceremony exists for.
     */
    private int glow;
    private boolean glowSettled;

    /**
     * Where the revealed cards were last drawn.
     *
     * <p>Kept rather than worked out again, so the scripted run can put a cursor on a card
     * using the numbers the drawing used rather than a second copy of the same arithmetic
     * that could disagree with it.
     */
    private PackLayout grid;
    private int gridLeft;
    private int gridTop;

    public PackOpeningScreen(String setCode, String kind, List<CardComponent> cards) {
        super(Component.translatable("screen.gathering.pack_opening"));
        this.setCode = setCode == null ? "" : setCode;
        this.kind = kind == null ? "" : kind;
        this.cards = cards == null ? List.of() : List.copyOf(cards);
        settleGlow();
    }

    /** The glow, and whether every card has been named yet. */
    private void settleGlow() {
        List<Rarity> rarities = new ArrayList<>(cards.size());
        for (CardComponent card : cards) {
            ClientCardCache.get().summary(card)
                    .map(CardSummary::rarity)
                    .ifPresent(rarities::add);
        }
        glow = PackGlow.forPack(rarities);
        glowSettled = rarities.size() == cards.size();
    }

    @Override
    protected void init() {
        int shorter = Math.min(this.width(), this.height());
        this.packHeight = (int) (shorter * PACK_HEIGHT);
        this.packWidth = (int) (this.packHeight * PACK_SHAPE);
        this.packX = (this.width() - this.packWidth) / 2;
        this.packY = (this.height() - this.packHeight) / 2;
        // Kept across a resize: a pack half torn when somebody dragged the window is still
        // half torn, and starting it again would be the window eating their progress.
        this.tear = new PackTear(this.packWidth, seed(), this.tear.gripped(), this.tear.torn());

        // A way out somebody can see. The cards are already in the inventory the moment the
        // pack opens, so leaving loses nothing at any stage - but the only exit was the
        // escape key, which is a rule nobody was told.
        addRenderableWidget(GatheringButtons.of(
                this.width() - 66, this.height() - 28, 56, 18,
                net.minecraft.network.chat.Component.translatable("gui.done"), this::onClose));
    }

    private int width() {
        return this.minecraft == null ? 320 : this.minecraft.getWindow().getGuiScaledWidth();
    }

    private int height() {
        return this.minecraft == null ? 240 : this.minecraft.getWindow().getGuiScaledHeight();
    }

    /** Every pack tears its own way, and the same pack the same way each frame. */
    private long seed() {
        long mixed = setCode.hashCode() * 31L + kind.hashCode();
        for (CardComponent card : cards) {
            mixed = mixed * 31L + card.hashCode();
        }
        return mixed;
    }

    /** The dark room the pack is opened in, under the widgets rather than over them. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.draw(graphics, Element.PACK_BACKDROP, 0, 0, width(), height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The background first, and only once. Drawing the pack and then calling up to the
        // superclass paints the menu background straight over it - which came out as a pack
        // behind frosted glass, and was only ever going to be found by looking at a picture.
        // The backing lives in renderBackground now, so the Done button is not behind it.
        super.render(graphics, mouseX, mouseY, partialTick);

        if (tear.isOpen()) {
            drawWhatWasInIt(graphics, mouseX, mouseY);
            return;
        }
        if (!glowSettled) {
            settleGlow();
        }

        int tornTo = packX + tear.tornTo();
        int crimp = packY + (int) (packHeight * CRIMP);

        // The body, with its top edge torn away where the tear has reached.
        drawBody(graphics, tornTo, crimp);
        // The crimped strip above it, still attached where the tear has not.
        drawWrapper(graphics, tornTo, packY, packX + packWidth, crimp, CRIMP_ROW, CRIMP_ROWS);
        drawTornEdge(graphics, tornTo, crimp);
        drawSymbol(graphics, crimp);

        // Only before it has been touched. Once somebody is tearing it, the tear is the
        // feedback; a line of text cheering them on is the screen talking for the sake of it.
        if (tear.isUntouched()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.gathering.pack_take_hold"),
                    width() / 2, packY + packHeight + 8, 0xFFBFC7D2);
        }
    }

    /**
     * What was in it, laid out over the torn wrapper.
     *
     * <p>The best card last, wherever it came out of the pack. Every ritual anybody has for
     * opening a booster is about arriving at that card rather than starting from it, and a
     * grid in collation order would hand it over in the middle of the second row.
     */
    private void drawWhatWasInIt(GuiGraphics graphics, int mouseX, int mouseY) {
        // Inset from the window rather than running to its edges: a row of cards touching
        // both sides reads as a screen that ran out of room, not as a pack laid out.
        int room = Math.min(width() - 2 * MARGIN_PIXELS, packWidth * 5);
        // As tall as the pack they came out of, and no taller. A ceiling is wanted - four
        // cards drawn to fill a window is silly - and that is the one worth having: the cards
        // come out the size of the wrapper that was holding them a second ago, so a small
        // pack reads as a handful rather than as four posters.
        PackLayout laid = PackLayout.fit(
                Math.max(1, revealed.size()), room, height() - 40, GAP, packHeight);
        int gridWidth = laid.width(GAP);
        int gridHeight = laid.height(GAP);
        int gridLeft = (width() - gridWidth) / 2;
        int gridTop = (height() - gridHeight) / 2;

        GatheringSprites.draw(graphics, Element.PACK_BACKDROP, 0, 0, width(), height());
        this.grid = laid;
        this.gridLeft = gridLeft;
        this.gridTop = gridTop;
        CardComponent over = null;
        for (int index = 0; index < revealed.size(); index++) {
            int column = index % laid.columns();
            int row = index / laid.columns();
            int x = gridLeft + column * (laid.cardWidth() + GAP);
            int y = gridTop + row * (laid.cardHeight() + GAP);
            CardComponent card = revealed.get(index);
            ClientCardCache.get().summary(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(graphics, summary, card.flipped(),
                            x, y, laid.cardWidth(), laid.cardHeight()),
                    () -> GatheringSprites.inset(
                            graphics, x, y, laid.cardWidth(), laid.cardHeight()));
            // Held over a card, the read-a-card key shows it here exactly as it does over a
            // hand, a pile or a draft pack. A grid of cards you have just been given and
            // cannot look at properly is the one place in the mod that would not answer it.
            if (mouseX >= x && mouseX < x + laid.cardWidth()
                    && mouseY >= y && mouseY < y + laid.cardHeight()) {
                over = card;
            }
            // The one the pack was opened for, ringed in its own color.
            if (index == revealed.size() - 1 && glow != PackGlow.NO_LIGHT) {
                GatheringSprites.draw(graphics, Element.RARITY_RING,
                        x - 1, y - 1, laid.cardWidth() + 2, laid.cardHeight() + 2, glow);
            }
        }
        ClientHoverState.setHovered(over == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : dev.gathering.item.CardItem.of(over));

        graphics.drawCenteredString(this.font, this.title, width() / 2, gridTop - 14, 0xFFBFC7D2);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.gathering.pack_kept", revealed.size()),
                width() / 2, gridTop + gridHeight + 6, 0xFFBFC7D2);
    }

    /**
     * What came out, worst first.
     *
     * <p>Sorted rather than left in collation order so the ceremony ends where it should. A
     * pack's own order puts the rare somewhere in the middle, which is the one place it must
     * not be.
     */
    private List<CardComponent> inRevealOrder() {
        List<CardComponent> order = new ArrayList<>(cards);
        order.sort(java.util.Comparator.comparingInt(card ->
                PackGlow.rankOf(ClientCardCache.get().summary(card)
                        .map(CardSummary::rarity)
                        .orElse(Rarity.UNKNOWN))));
        return List.copyOf(order);
    }

    /**
     * A piece of the wrapper.
     *
     * <p>Taken from a strip of the texture rather than the whole of it. The texture is one
     * picture of a complete pack - crimped top, body, margins - so blitting all of it into
     * both the strip and the body drew two packs stretched over each other, and the crimp's
     * teeth came out as stripes running the length of it. Each piece takes the rows it is.
     *
     * @param fromRow the first texture row this piece is cut from, of sixteen
     * @param rows    how many rows it is cut from
     */
    private void drawWrapper(
            GuiGraphics graphics, int x0, int y0, int x1, int y1, int fromRow, int rows) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        graphics.blit(PackFaceRenderer.WRAPPER, x0, y0, x1 - x0, y1 - y0,
                MARGIN, fromRow, WRAPPER_PIXELS - 2 * MARGIN, rows, 16, 16);
    }

    /**
     * How many columns the torn edge is cut into.
     *
     * <p>The paper and the light are drawn from this same count, because they have to be the
     * same edge. They were not: the wrapper was cut off at a straight line and the light was
     * drawn along a wandering one, so the glow floated above the paper down half the tear and
     * sank into it down the other half. Two edges is one edge too many.
     */
    private int tearSteps() {
        return Math.max(16, Math.min(64, packWidth / 3));
    }

    /** Where one column of the tear starts, so neighbouring columns meet without a seam. */
    private int columnAt(int step, int steps) {
        return packX + Math.round(step * packWidth / (float) steps);
    }

    /**
     * How far the tear may bite into the body, in pixels.
     *
     * <p>Down into the body and never up into the crimp: an edge that wandered both ways
     * would need paper drawn above the line to wander into, and there is none - the strip up
     * there is the piece being torn off. So the whole wander is a bite out of what is left,
     * which is what a tear along a crimp does anyway.
     */
    private float tearBite() {
        return packHeight * (float) CRIMP * 0.4f;
    }

    /**
     * The torn edge, as a screen row per column.
     *
     * <p>One array, handed to whatever is drawing: the paper, the light, and anything else
     * that ever needs to know where the tear is. Columns the tear has not reached sit on the
     * crimp line, which is where the strip above them is still attached.
     */
    private int[] tearLine(int steps, int tornTo, int crimp) {
        float bite = tearBite();
        float[] edge = tear.edge(steps, bite);
        int sink = Math.round(bite * 0.7f);
        int[] line = new int[steps];
        for (int step = 0; step < steps; step++) {
            int middle = (columnAt(step, steps) + columnAt(step + 1, steps)) / 2;
            line[step] = middle > tornTo
                    ? crimp
                    : crimp + sink + Math.round(edge[Math.min(step, edge.length - 1)]);
        }
        return line;
    }

    /**
     * The pack below the tear.
     *
     * <p>Drawn as one wrapper, once per column, with a scissor holding each column to the
     * part of it below the tear. That is what makes the paper end exactly where the light
     * begins: neither of them is a shape somebody drew twice, they are one line read twice.
     *
     * <p>Column by column rather than as one blit with a torn top because a texture cannot be
     * blitted into a shape that is not a rectangle - and rather than by cutting the texture up
     * because a slice three pixels wide off a sixteen-pixel picture is not a slice, it is a
     * rounding error. The scissor costs a draw call per column and buys an edge that is
     * exactly the edge.
     */
    private void drawBody(GuiGraphics graphics, int tornTo, int crimp) {
        int steps = tearSteps();
        int[] line = tearLine(steps, tornTo, crimp);
        int bottom = packY + packHeight;
        for (int step = 0; step < steps; step++) {
            int x0 = columnAt(step, steps);
            int x1 = columnAt(step + 1, steps);
            if (x1 <= x0 || line[step] >= bottom) {
                continue;
            }
            graphics.enableScissor(x0, line[step], x1, bottom);
            GatheringSprites.draw(graphics, Element.PACK_WRAPPER_EDGE,
                    packX, crimp, packWidth, bottom - crimp);
            drawWrapper(graphics, packX, crimp, packX + packWidth, bottom,
                    BODY_ROW, BODY_ROWS);
            graphics.disableScissor();
        }
    }

    /**
     * The light coming out of the tear.
     *
     * <p>Drawn as a run of short bars along the torn edge rather than a line, because the
     * light is what is being drawn: a bar per column, brightest at the paper and fading
     * upward, so the pack looks lit from inside rather than outlined. On the same line the
     * paper was cut along, so there is no gap between them at any point.
     */
    private void drawTornEdge(GuiGraphics graphics, int tornTo, int crimp) {
        if (tear.isUntouched() || glow == PackGlow.NO_LIGHT) {
            return;
        }
        int steps = tearSteps();
        int[] line = tearLine(steps, tornTo, crimp);
        int reach = Math.max(4, packHeight / 10);
        for (int step = 0; step < steps; step++) {
            int x0 = columnAt(step, steps);
            int x1 = columnAt(step + 1, steps);
            if (x0 > tornTo) {
                break;
            }
            for (int up = 0; up < reach; up++) {
                float strength = 1f - up / (float) reach;
                int alpha = Math.round(strength * strength * 190);
                GatheringSprites.draw(graphics, Element.PACK_SPARK,
                        x0, line[step] - up, Math.max(1, x1 - x0), 1,
                        (alpha << 24) | (glow & 0x00FFFFFF));
            }
        }
    }

    /** The set's symbol, printed on the wrapper in the product's color. */
    private void drawSymbol(GuiGraphics graphics, int crimp) {
        int side = (int) (packWidth * 0.42);
        int color = PackWrapper.symbolColor(kind);
        ClientSetSymbols.get().symbol(setCode, color, 128).ifPresent(symbol -> {
            int x = packX + (packWidth - side) / 2;
            int y = crimp + (packY + packHeight - crimp - side) / 2;
            graphics.blit(symbol, x, y, 0f, 0f, side, side, side, side);
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        follow(mouseX);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        follow(mouseX);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void follow(double mouseX) {
        boolean wasSealed = !tear.isOpen();
        tear = tear.followedTo((int) Math.round(mouseX - packX));
        if (wasSealed && tear.isOpen()) {
            // Sorted once, at the moment it comes apart. Doing it every frame would re-sort a
            // list whose order is the whole point, as summaries arrive one packet at a time.
            revealed = inRevealOrder();
        }
    }

    /**
     * Where the pack is on screen, so the scripted run can aim a real cursor at it.
     *
     * <p>Given rather than a "tear it for me" hook on purpose: what is worth checking is that
     * dragging across the wrapper tears it, and a shortcut past the mouse would check that
     * the tear works and not that anything reaches it.
     */
    public int packLeft() {
        return packX;
    }

    public int packWidth() {
        return packWidth;
    }

    public int packMiddleY() {
        return packY + packHeight / 2;
    }

    public PackTear tear() {
        return tear;
    }

    /** What came out, in the order it is being shown. Empty until the wrapper is off. */
    public List<CardComponent> shown() {
        return revealed;
    }

    /**
     * The middle of one revealed card, as it was last drawn, or null before it has been.
     *
     * <p>For the scripted run, which points a real cursor at a real card to find out whether
     * the read-a-card key answers over one.
     */
    public int[] middleOfCard(int index) {
        if (grid == null || index < 0 || index >= revealed.size()) {
            return null;
        }
        int column = index % grid.columns();
        int row = index / grid.columns();
        return new int[] {
                gridLeft + column * (grid.cardWidth() + GAP) + grid.cardWidth() / 2,
                gridTop + row * (grid.cardHeight() + GAP) + grid.cardHeight() / 2};
    }

    public int glow() {
        return glow;
    }

    @Override
    public void removed() {
        // The grid is gone, so nothing is under the cursor any more. Left set, the read-a-card
        // key would keep showing the last card of a pack that is no longer on screen.
        ClientHoverState.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
