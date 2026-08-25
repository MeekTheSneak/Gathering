package dev.gathering.client;

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

    private static final int BACKING = 0xC8060A0E;

    /** How the cards are laid out once the wrapper is off. */
    private static final int GAP = 4;
    private static final int CARD_HEIGHT = 96;
    private static final int WRAPPER_EDGE = 0xFF161A20;

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
    private final int glow;

    private PackTear tear = PackTear.unopened(1, 0L);
    private List<CardComponent> revealed = List.of();
    private int left;
    private int top;
    private int width;
    private int height;

    public PackOpeningScreen(String setCode, String kind, List<CardComponent> cards) {
        super(Component.translatable("screen.gathering.pack_opening"));
        this.setCode = setCode == null ? "" : setCode;
        this.kind = kind == null ? "" : kind;
        this.cards = cards == null ? List.of() : List.copyOf(cards);
        this.glow = PackGlow.forPack(raritiesOf(this.cards));
    }

    /**
     * What is in the pack, as rarities.
     *
     * <p>Read off the summaries this client was sent with the cards. A card whose summary has
     * not landed is unknown rather than common, so a pack does not briefly claim to hold
     * nothing worth glowing about and then change its mind.
     */
    private static List<Rarity> raritiesOf(List<CardComponent> cards) {
        List<Rarity> rarities = new ArrayList<>(cards.size());
        for (CardComponent card : cards) {
            ClientCardCache.get().summary(card)
                    .map(CardSummary::rarity)
                    .ifPresent(rarities::add);
        }
        return rarities;
    }

    @Override
    protected void init() {
        int shorter = Math.min(this.width(), this.height());
        this.height = (int) (shorter * PACK_HEIGHT);
        this.width = (int) (this.height * PACK_SHAPE);
        this.left = (this.width() - this.width) / 2;
        this.top = (this.height() - this.height) / 2;
        // Kept across a resize: a pack half torn when somebody dragged the window is still
        // half torn, and starting it again would be the window eating their progress.
        this.tear = new PackTear(this.width, seed(), this.tear.gripped(), this.tear.torn());
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The background first, and only once. Drawing the pack and then calling up to the
        // superclass paints the menu background straight over it - which came out as a pack
        // behind frosted glass, and was only ever going to be found by looking at a picture.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width(), height(), BACKING);

        if (tear.isOpen()) {
            drawWhatWasInIt(graphics, mouseX, mouseY);
            return;
        }

        int tornTo = left + tear.tornTo();
        int crimp = top + (int) (height * CRIMP);

        // The wrapper below the tear, which is the pack proper.
        graphics.fill(left, crimp, left + width, top + height, WRAPPER_EDGE);
        drawWrapper(graphics, left, crimp, left + width, top + height, BODY_ROW, BODY_ROWS);

        // The strip above it, still attached where the tear has not reached.
        if (!tear.isOpen()) {
            drawWrapper(graphics, tornTo, top, left + width, crimp, CRIMP_ROW, CRIMP_ROWS);
        }
        drawTornEdge(graphics, tornTo, crimp);
        drawSymbol(graphics, crimp);

        Component say = tear.isUntouched()
                ? Component.translatable("screen.gathering.pack_take_hold")
                : tear.isOpen()
                        ? Component.translatable("screen.gathering.pack_kept", cards.size())
                        : Component.translatable("screen.gathering.pack_keep_going");
        graphics.drawCenteredString(this.font, say, width() / 2, top + height + 8, 0xFFBFC7D2);
    }

    /**
     * What was in it, laid out over the torn wrapper.
     *
     * <p>The best card last, wherever it came out of the pack. Every ritual anybody has for
     * opening a booster is about arriving at that card rather than starting from it, and a
     * grid in collation order would hand it over in the middle of the second row.
     */
    private void drawWhatWasInIt(GuiGraphics graphics, int mouseX, int mouseY) {
        int room = Math.min(width() - 16, width() * 5);
        PackLayout laid = PackLayout.fit(
                Math.max(1, revealed.size()), room, height() - 40, GAP, CARD_HEIGHT);
        int gridWidth = laid.width(GAP);
        int gridHeight = laid.height(GAP);
        int gridLeft = (width() - gridWidth) / 2;
        int gridTop = (height() - gridHeight) / 2;

        graphics.fill(0, 0, width(), height(), BACKING);
        for (int index = 0; index < revealed.size(); index++) {
            int column = index % laid.columns();
            int row = index / laid.columns();
            int x = gridLeft + column * (laid.cardWidth() + GAP);
            int y = gridTop + row * (laid.cardHeight() + GAP);
            CardComponent card = revealed.get(index);
            ClientCardCache.get().summary(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, x, y, laid.cardWidth(), laid.cardHeight()),
                    () -> GatheringSprites.inset(
                            graphics, x, y, laid.cardWidth(), laid.cardHeight()));
            // The one the pack was opened for, ringed in its own colour.
            if (index == revealed.size() - 1 && glow != PackGlow.NO_LIGHT) {
                graphics.renderOutline(x - 1, y - 1, laid.cardWidth() + 2, laid.cardHeight() + 2,
                        glow);
            }
        }
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
                rankOf(ClientCardCache.get().summary(card)
                        .map(CardSummary::rarity)
                        .orElse(Rarity.UNKNOWN))));
        return List.copyOf(order);
    }

    private static int rankOf(Rarity rarity) {
        return switch (rarity) {
            case MYTHIC -> 5;
            case SPECIAL, BONUS -> 4;
            case RARE -> 3;
            case UNCOMMON -> 2;
            case COMMON -> 1;
            default -> 0;
        };
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
     * The tear, and the light coming out of it.
     *
     * <p>Drawn as a run of short bars along the torn edge rather than a line, because the
     * light is what is being drawn: a bar per step, brightest at the edge and fading upward,
     * so the pack looks lit from inside rather than outlined.
     */
    private void drawTornEdge(GuiGraphics graphics, int tornTo, int crimp) {
        if (tear.isUntouched() || glow == PackGlow.NO_LIGHT) {
            return;
        }
        int steps = Math.max(8, width / 2);
        float[] edge = tear.edge(steps, height * (float) CRIMP * 0.5f);
        int reach = Math.max(4, height / 10);
        for (int step = 0; step < steps; step++) {
            int x = left + Math.round(step * width / (float) (steps - 1));
            if (x > tornTo) {
                break;
            }
            int y = crimp + Math.round(edge[step]);
            for (int up = 0; up < reach; up++) {
                float strength = 1f - up / (float) reach;
                int alpha = Math.round(strength * strength * 190);
                graphics.fill(x, y - up, x + 2, y - up + 1, (alpha << 24) | (glow & 0x00FFFFFF));
            }
        }
    }

    /** The set's symbol, printed on the wrapper in the product's colour. */
    private void drawSymbol(GuiGraphics graphics, int crimp) {
        int side = (int) (width * 0.42);
        int colour = PackWrapper.symbolColour(kind);
        ClientSetSymbols.get().symbol(setCode, colour, 128).ifPresent(symbol -> {
            int x = left + (width - side) / 2;
            int y = crimp + (top + height - crimp - side) / 2;
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
        tear = tear.followedTo((int) Math.round(mouseX - left));
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
        return left;
    }

    public int packWidth() {
        return width;
    }

    public int packMiddleY() {
        return top + height / 2;
    }

    public PackTear tear() {
        return tear;
    }

    /** What came out, in the order it is being shown. Empty until the wrapper is off. */
    public List<CardComponent> shown() {
        return revealed;
    }

    public int glow() {
        return glow;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
