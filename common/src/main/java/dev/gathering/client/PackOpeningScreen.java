package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.ui.PackGlow;
import dev.gathering.core.ui.PackLayout;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.PackTear;
import dev.gathering.core.ui.PackWrapper;
import dev.gathering.item.CardComponent;
import dev.gathering.network.CardSummary;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
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

    /**
     * How many rows each piece is cut into down the pack, for the turn to be a curve.
     *
     * <p>The body gets more because it is most of the pack and because its top edge is the
     * tear; the strip is a sixth of the height and two are plenty.
     */
    private static final int BODY_DOWN = 6;
    private static final int STRIP_DOWN = 2;

    private final String setCode;
    private final String kind;
    private final List<CardComponent> cards;

    private PackTear tear = PackTear.unopened(1, 0L);
    private List<CardComponent> revealed = List.of();

    /**
     * How far the pack is turned, and how far it is easing towards being turned.
     *
     * <p>Its own rather than {@link CardTilt}'s, which belongs to the inspect panel. Two
     * things easing one value would fight over it the moment a card was hovered on top of a
     * pack, and the numbers are not the same either: a pack held in front of you turns less
     * than a card held up to read.
     */
    private float yaw;
    private float pitch;

    /** How far the pack turns, in degrees. Less than a card, because it is a heavier thing. */
    private static final float MOST_YAW = 7f;
    private static final float MOST_PITCH = 4.5f;

    /** How much of the way to the wanted angle each frame, so it follows rather than snaps. */
    private static final float EASE = 0.18f;

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

        // Turned towards the cursor while it is being looked at, and square while it is being
        // torn. A pack is held still to tear it, and a pack that swung eighteen degrees under
        // the hand doing the tearing would be moving the very edge that hand is aiming at.
        turnTowards(mouseX, mouseY);
        CardLens lens = CardLens.of(
                new Rect(packX, packY, packWidth, packHeight), yaw, pitch);
        Matrix4f matrix = graphics.pose().last().pose();

        int steps = tearSteps();
        float[] top = tearTops(steps, tornTo, crimp);

        // The body, its top edge wherever the tear left it, and the crimped strip above it
        // where the tear has not reached. Two pieces because the wrapper's picture squashes
        // its crimp into the top sixth of the pack and stretches its body over the rest, and
        // a quad's texture coordinates can only run straight.
        TiltedPack.draw(matrix, lens, PackFaceRenderer.WRAPPER, top,
                new TiltedPack.Piece(BODY_ROW, BODY_ROWS, (float) CRIMP, 1f - (float) CRIMP),
                BODY_DOWN, MARGIN, WRAPPER_PIXELS);
        TiltedPack.draw(matrix, lens, PackFaceRenderer.WRAPPER, top,
                new TiltedPack.Piece(CRIMP_ROW, CRIMP_ROWS, 0f, (float) CRIMP),
                STRIP_DOWN, MARGIN, WRAPPER_PIXELS);
        drawSymbol(graphics, lens, matrix);
        drawTornEdge(graphics, lens, matrix, tornTo, steps, top);

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
        if (leans.length != revealed.size()) {
            leans = new float[revealed.size()];
        }
        CardComponent over = null;
        for (int index = 0; index < revealed.size(); index++) {
            int column = index % laid.columns();
            int row = index / laid.columns();
            int x = gridLeft + column * (laid.cardWidth() + GAP);
            int y = gridTop + row * (laid.cardHeight() + GAP);
            CardComponent card = revealed.get(index);
            // Turned towards the cursor, most for the one nearest it. These are the cards
            // somebody has just been given: they are the point of the whole screen, and a
            // grid of them lying flat is a spreadsheet of what was in the pack rather than a
            // handful of cards. The one you are looking at leans towards you, and a foil
            // among them catches the light as it does.
            float toward = leanToward(mouseX, mouseY,
                    x + laid.cardWidth() / 2f, y + laid.cardHeight() / 2f, laid.cardWidth());
            if (index < leans.length) {
                leans[index] = toward;
            }
            float cardYaw = across(mouseX, x + laid.cardWidth() / 2f, laid.cardWidth())
                    * CARD_YAW * toward;
            float cardPitch = across(mouseY, y + laid.cardHeight() / 2f, laid.cardHeight())
                    * CARD_PITCH * toward;
            ClientCardCache.get().summary(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArtTurned(
                            graphics, summary, card.flipped(),
                            x, y, laid.cardWidth(), laid.cardHeight(),
                            cardYaw, cardPitch, card.foil()),
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
            // And one somebody has been chasing, marked in the corner. This is the moment a
            // wants list is for: a pack is a handful of names, and the one you have been
            // after for a month looks like all the others until something says so.
            if (card.scryfallId().filter(ClientWants::wants).isPresent()) {
                GatheringSprites.draw(graphics, Element.WANTED_MARK,
                        x + 2, y + 2, MARK_SIDE, MARK_SIDE);
            }
        }
        ClientHoverState.setHovered(over == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : dev.gathering.item.CardItem.of(over));

        graphics.drawCenteredString(this.font, this.title, width() / 2, gridTop - 14, 0xFFBFC7D2);
        int chased = howManyWereWanted();
        graphics.drawCenteredString(this.font,
                chased > 0
                        ? Component.translatable(
                                "screen.gathering.pack_kept_wanted", revealed.size(), chased)
                        : Component.translatable("screen.gathering.pack_kept", revealed.size()),
                width() / 2, gridTop + gridHeight + 6, chased > 0 ? WANTED : 0xFFBFC7D2);
    }

    /** How much of its turn each card took last frame. For the harness; see {@link #leanOf}. */
    private float[] leans = new float[0];

    /**
     * How far one of the pulled cards is turned towards the cursor, from nought to one.
     *
     * <p>For the scripted run, which cannot photograph this: the cards it opens are made up
     * and have no art, so they take the placeholder path and nothing turns. What can be
     * checked is the arithmetic - the card under the cursor leans most, and one across the
     * grid from it barely at all - and that is the part of this that is new. The turning
     * itself is the inspect panel's, and has its own pictures.
     */
    float leanOf(int index) {
        return index >= 0 && index < leans.length ? leans[index] : 0f;
    }

    /** How big the mark on a card somebody was chasing is, in the corner of it. */
    private static final int MARK_SIDE = 5;

    /** The color of the wants list, so the line under the pack matches the marks above it. */
    private static final int WANTED = 0xFFFFD479;

    /** How many of these were on the wants list. */
    private int howManyWereWanted() {
        int chased = 0;
        for (CardComponent card : revealed) {
            if (card.scryfallId().filter(ClientWants::wants).isPresent()) {
                chased++;
            }
        }
        return chased;
    }

    /** How far a card in the grid turns towards the cursor. */
    private static final float CARD_YAW = 9f;
    private static final float CARD_PITCH = 5.5f;

    /**
     * How far away the cursor has to be before a card stops paying attention to it.
     *
     * <p>In card widths. Without a falloff every card in the grid turns the same amount as
     * the one under the cursor, because a card three widths away is still to its left - which
     * came out as the whole grid leaning in one direction like a stack about to fall over.
     */
    private static final float NOTICES_WITHIN = 2.6f;

    /** How much of its turn a card this far from the cursor takes, from one down to nought. */
    private static float leanToward(int mouseX, int mouseY, float centerX, float centerY, int wide) {
        float span = Math.max(1f, wide * NOTICES_WITHIN);
        float away = (float) Math.hypot(mouseX - centerX, mouseY - centerY) / span;
        float left = Math.max(0f, 1f - away);
        // Squared, so the falloff is gentle near the cursor and quick further out - which is
        // what makes one card read as the one being looked at rather than four of them.
        return left * left;
    }

    /** Where a point sits across a card, minus one to one, clamped at its edges. */
    private static float across(int at, float center, int span) {
        return Math.max(-1f, Math.min(1f, (at - center) / Math.max(1f, span / 2f)));
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
     * The torn edge, as a share of the way down the pack per column.
     *
     * <p>One array, handed to whatever is drawing: the paper, the light, and anything else
     * that ever needs to know where the tear is. In the pack's own space rather than in
     * screen rows, because the pack is turned and a screen row is not a place on it any more.
     *
     * <p>Columns the tear has not reached sit at nought - the top of the pack - because the
     * strip up there is still attached and the whole wrapper is showing.
     */
    private float[] tearTops(int steps, int tornTo, int crimp) {
        float bite = tearBite();
        float[] edge = tear.edge(steps, bite);
        float sink = bite * 0.7f;
        float crimpAt = (float) CRIMP;
        float[] top = new float[steps];
        for (int step = 0; step < steps; step++) {
            int middle = (columnAt(step, steps) + columnAt(step + 1, steps)) / 2;
            if (middle > tornTo) {
                top[step] = 0f;
                continue;
            }
            float down = (crimp - packY + sink + edge[Math.min(step, edge.length - 1)])
                    / Math.max(1f, packHeight);
            top[step] = Math.min(1f, Math.max(crimpAt, down));
        }
        return top;
    }

    /**
     * Turns the pack towards the cursor, a little way, easing rather than snapping.
     *
     * <p>Square while it is being torn: the tear follows the hand across the top edge, and an
     * edge that swung away from the hand aiming at it would be the interface arguing with the
     * gesture. Aimed at its own middle rather than switched off, so it settles over a few
     * frames the same way it arrived.
     */
    private void turnTowards(int mouseX, int mouseY) {
        boolean beingTorn = !tear.isUntouched() && !tear.isOpen();
        float wantedYaw = 0f;
        float wantedPitch = 0f;
        if (!beingTorn) {
            float centerX = packX + packWidth / 2f;
            float centerY = packY + packHeight / 2f;
            float across = Math.max(-1f, Math.min(1f,
                    (mouseX - centerX) / Math.max(1f, packWidth)));
            float down = Math.max(-1f, Math.min(1f,
                    (mouseY - centerY) / Math.max(1f, packHeight)));
            wantedYaw = across * MOST_YAW;
            wantedPitch = down * MOST_PITCH;
        }
        yaw += (wantedYaw - yaw) * EASE;
        pitch += (wantedPitch - pitch) * EASE;
    }

    /**
     * The light coming out of the tear.
     *
     * <p>Drawn as a run of short bars along the torn edge rather than a line, because the
     * light is what is being drawn: a bar per column, brightest at the paper and fading
     * upward, so the pack looks lit from inside rather than outlined.
     *
     * <p>Through the same lens the paper went through, off the same line, so it stays on the
     * tear at every angle. Drawn flat it slid off the moment the pack turned - which is the
     * whole argument for the pack being a thing in space rather than two pictures.
     */
    private void drawTornEdge(
            GuiGraphics graphics, CardLens lens, Matrix4f matrix,
            int tornTo, int steps, float[] top) {
        if (tear.isUntouched() || glow == PackGlow.NO_LIGHT) {
            return;
        }
        float reach = Math.max(4f, packHeight / 10f) / Math.max(1f, packHeight);
        TiltedPack.Glow light = new TiltedPack.Glow(glow & 0x00FFFFFF, GLOW_ALPHA);
        TiltedPack.shine(matrix, lens, top, reach, light,
                columnsTorn(steps, tornTo), GLOW_STEPS);
    }

    /** How brightly the light comes out where it meets the paper. */
    private static final int GLOW_ALPHA = 190;

    /** How many bands the light is faded over. Enough to read as light rather than as a bar. */
    private static final int GLOW_STEPS = 7;

    /** How many columns the tear has passed, which is how much of it is giving off light. */
    private int columnsTorn(int steps, int tornTo) {
        int torn = 0;
        for (int step = 0; step < steps; step++) {
            int middle = (columnAt(step, steps) + columnAt(step + 1, steps)) / 2;
            if (middle > tornTo) {
                break;
            }
            torn = step + 1;
        }
        return torn;
    }

    /**
     * The set's symbol, printed on the wrapper in the product's color.
     *
     * <p>On the pack rather than over it: it goes through the lens like everything else, so
     * it lies on the paper and turns with it instead of hovering in front.
     */
    private void drawSymbol(GuiGraphics graphics, CardLens lens, Matrix4f matrix) {
        float side = 0.42f;
        float acrossFrom = 0.5f - side / 2f;
        // Centred in the body, which is what is left under the crimp.
        float down = (float) CRIMP + (1f - (float) CRIMP) / 2f;
        float tall = side * packWidth / Math.max(1f, packHeight);
        int color = PackWrapper.symbolColor(kind);
        ClientSetSymbols.get().symbol(setCode, color, 128).ifPresent(symbol ->
                TiltedPack.print(matrix, lens, symbol,
                        acrossFrom, down - tall / 2f, acrossFrom + side, down + tall / 2f,
                        SYMBOL_CUTS));
    }

    /** How finely the symbol is cut up, so it lies on the paper rather than across it. */
    private static final int SYMBOL_CUTS = 3;

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
