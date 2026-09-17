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
 * <p>The pack comes to the middle of the screen and stays sealed until somebody tears it.
 * Take hold of the corner, drag across, and the wrapper comes apart where the cursor goes -
 * and light comes out of the tear before a single card is shown, yellow for a rare inside and
 * orange for a mythic. That moment is what a booster is for, and everything here exists to
 * put it before the cards rather than after them.
 * <p>Nothing on this screen decides anything. The cards were drawn and written down as the
 * player's before it opened, and come out into the inventory as the wrapper comes off - or when
 * the screen closes, or on the next join after a disconnect - so nothing done here costs anybody
 * a card. What it does is let you find out.
 * <p>Client-only.
 */
public final class PackOpeningScreen extends Screen {

    /** How big the pack is drawn, as a fraction of the shorter side of the window. */
    private static final double PACK_HEIGHT = 0.62;

    /**
     * The shape of the wrapper, which is the shape of the wrapper's own picture: twelve pixels of printed
     * bag across sixteen down. It was 0.66, so opening a pack squashed it a tenth narrower than the one in
     * the hand that was just clicked, which is what the owner saw stretch (2026-09-16).
     */
    private static final double PACK_SHAPE = dev.gathering.core.ui.PackWrapper.shape();

    /**
     * How deep the torn strip is, as a fraction of the pack's height - and so where the crimp ends and the
     * body begins, because that is where a pack tears. Four of the wrapper's sixteen rows are crimp.
     */
    private static final double CRIMP = dev.gathering.core.ui.PackWrapper.crimp();

    /** How the cards are laid out once the wrapper is off. */
    private static final int GAP = 4;

    /** Done, centered under everything else, in a strip of its own the cards are laid out above. */
    private static final int DONE_WIDTH = 80;
    private static final int DONE_HEIGHT = 20;

    /** How much of the bottom of the window is kept for the count line and Done. */
    private static final int BELOW_THE_CARDS = DONE_HEIGHT + 30;

    private net.minecraft.client.gui.components.Button doneButton;
    private static final int MARGIN_PIXELS = 16;

    /**
     * Which rows of the wrapper texture are which.
     * <p>The item texture is one picture of a whole pack, and this screen draws two pieces of
     * one - so it cuts them from where they are. Named here so a new wrapper is these four
     * numbers rather than a hunt through the drawing.
     * <p>All sixteen rows, and each piece laid along the same share of the pack it takes up in the
     * picture. They used to start a row late and stop a row early - the white top of the crimp and the
     * fold at the bottom were never drawn, the first row of the body was drawn as crimp, and the
     * fourteen rows that were left were stretched over a pack the wrong shape for them.
     */
    private static final int WRAPPER_PIXELS = dev.gathering.core.ui.PackWrapper.PIXELS;
    private static final int MARGIN = dev.gathering.core.ui.PackWrapper.MARGIN;
    private static final int CRIMP_ROW = dev.gathering.core.ui.PackWrapper.CRIMP_ROW;
    private static final int CRIMP_ROWS = dev.gathering.core.ui.PackWrapper.CRIMP_ROWS;
    private static final int BODY_ROW = dev.gathering.core.ui.PackWrapper.BODY_ROW;
    private static final int BODY_ROWS = dev.gathering.core.ui.PackWrapper.BODY_ROWS;

    /**
     * How many rows each piece is cut into down the pack, for the turn to be a curve.
     * <p>The body gets more because it is most of the pack and because its top edge is the
     * tear; the strip is a sixth of the height and two are plenty.
     */
    private static final int BODY_DOWN = 6;
    private static final int STRIP_DOWN = 2;

    private final String setCode;
    private final String kind;
    private final List<CardComponent> cards;

    /**
     * The wrapper itself, as a sheet of foil that tears where it is pulled.
     * <p>It was a tear that ran along a line the mod drew, with the hand only saying how far along it had
     * got. The owner asked for the real thing (2026-09-16): take hold of the wrapper and pull, and it
     * comes apart where you pulled it. See {@link dev.gathering.core.ui.PackCloth}, which is all of the
     * arithmetic and none of the pixels.
     */
    private dev.gathering.core.ui.PackCloth cloth = new dev.gathering.core.ui.PackCloth(0L);

    /** When the last frame was, so the sheet is run forward by however long actually passed. */
    private long lastFrame;
    private List<CardComponent> revealed = List.of();

    /**
     * Going through the pack a card at a time, between the wrapper coming off and the spread.
     * <p>Null until the pack is open. The owner asked for the whole ceremony (2026-09-16): a stack you
     * thumb through rather than a grid that hands you every answer at once.
     */
    private PackTurning turning;

    /**
     * How far the pack is turned, and how far it is easing toward being turned.
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
     * <p>Kept rather than worked out again, so the scripted run can put a cursor on a card
     * using the numbers the drawing used rather than a second copy of the same arithmetic
     * that could disagree with it.
     */
    private PackLayout grid;
    private int gridLeft;
    private int gridTop;

    public PackOpeningScreen(String setCode, String kind, List<CardComponent> cards) {
        this(setCode, kind, cards, "");
    }

    /**
     * The same, for a pack whose cards wait under its wrapper until it is torn.
     *
     * @param wrapper the server's token for them, or blank when they were handed over already
     */
    public PackOpeningScreen(String setCode, String kind, List<CardComponent> cards, String wrapper) {
        super(Component.translatable("screen.gathering.pack_opening"));
        this.wrapper = wrapper == null ? "" : wrapper;
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
        // A resize keeps the wrapper as it stands: the sheet lives in its own space, nought to one
        // across and down, so where it has been torn to does not depend on how big the window is.
        if (cloth.isUntouched()) {
            cloth = new dev.gathering.core.ui.PackCloth(seed());
        }

        // A way out somebody can see. Leaving loses nothing at any stage - closing the screen
        // tells the server the pack is open, and the cards come - but the only exit was the
        // escape key, which is a rule nobody was told.
        doneButton = addRenderableWidget(GatheringButtons.of(
                (this.width() - DONE_WIDTH) / 2, this.height() - DONE_HEIGHT - 8, DONE_WIDTH, DONE_HEIGHT,
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
        // The backing lives in renderBackground now; the buttons go down last, over the cards, so
        // Done is never behind the pack or anything that came out of it.
        renderBackground(graphics, mouseX, mouseY, partialTick);
        runTheWrapper();
        if (cloth.isOpen()) {
            // Torn: the cards come out now, into the inventory, as the wrapper comes off.
            tellTheServerItIsOpen();
            if (turning != null && !turning.finished()) {
                // One card at a time, which is what a pack is opened for.
                turning.tick();
                turning.draw(graphics, this.font, cardInHand(), mouseX, mouseY,
                        ClientSettings.reducedMotion());
            } else {
                // And then all of it at once, which is what you got.
                drawWhatWasInIt(graphics, mouseX, mouseY);
            }
        } else {
            drawThePack(graphics, mouseX, mouseY);
        }
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    /** How far in front of the glow behind it a revealed card is drawn: more than a turned card leans back. */
    private static final float IN_FRONT_OF_ITS_GLOW = 60f;

    /**
     * Where the card being turned sits: where the wrapper was, at a card's shape rather than a pack's.
     * <p>The same place the pack was a moment ago, so the cards come out of it rather than appearing
     * somewhere else on the screen.
     */
    private Rect cardInHand() {
        int tall = Math.min(packHeight, height() - BELOW_THE_CARDS - 40);
        int wide = (int) Math.round(dev.gathering.core.ui.CardShape.widthFor(tall));
        return new Rect((width() - wide) / 2, Math.max(24, (height() - BELOW_THE_CARDS - tall) / 2),
                wide, tall);
    }

    /**
     * Runs the sheet forward, and notices the moment it comes apart.
     * <p>Here rather than in the drawing of the wrapper, because the drawing stops the instant the
     * wrapper opens - so the one frame that matters would be the one frame nothing asked about. And here
     * rather than in the drag, because a hand that lets go mid-tear still has the foil come apart under
     * its own weight a moment later, which no input handler ever sees.
     */
    private void runTheWrapper() {
        if (turning != null) {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        float since = lastFrame == 0 ? 0f : (now - lastFrame) / 1000f;
        lastFrame = now;
        cloth.advance(since);
        soundedAt = PackSounds.tearing(soundedAt, cloth.torn());
        // Whether it is open, not whether it became open on this frame. The sheet can be run from
        // anywhere - a scripted hand does it outside the drawing entirely - so an edge that happened
        // between two frames is an edge nothing here ever saw, and the pack stayed shut for ever.
        if (cloth.isOpen()) {
            PackSounds.opened();
            // Built once, at the moment it comes apart. Doing it every frame would re-sort a list whose
            // order is the whole point, as summaries arrive one packet at a time.
            turning = PackTurning.of(cards);
            revealed = turning.inOrder();
        }
    }

    /** The sealed pack, torn as far as it has been - which is wherever the hand has pulled it. */
    private void drawThePack(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!glowSettled) {
            settleGlow();
        }
        Matrix4f matrix = graphics.pose().last().pose();
        Rect where = new Rect(packX, packY, packWidth, packHeight);

        // The light out of the tear, under the foil, so what is coming through the hole is behind the
        // wrapper rather than painted over it.
        if (!cloth.isUntouched() && glow != PackGlow.NO_LIGHT) {
            int alpha = Math.round(GLOW_ALPHA * Math.min(1f, 0.4f + cloth.torn()));
            // Hugging the seam rather than haloing a wide band: a glow spread a quarter of the pack's
            // width around a short rectangle puts most of itself off the left and right ends, which
            // reads as two smudges beside the pack rather than light coming out of it.
            GuiGlow.around(graphics, packX + packWidth / 8, packY + Math.round(packHeight * (float) CRIMP),
                    packWidth - packWidth / 4, Math.max(1, packHeight / 12),
                    Math.max(4, packHeight / 12), (alpha << 24) | (glow & 0x00FFFFFF));
        }

        PackClothRenderer.draw(matrix, cloth, PackFaceRenderer.WRAPPER, where);
        drawSymbol(matrix, where);

        // Only before it has been touched. Once somebody is pulling at it, the wrapper is the feedback;
        // a line of text cheering them on is the screen talking for the sake of it.
        if (cloth.isUntouched()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.gathering.pack_take_hold"),
                    width() / 2, packY + packHeight + 8, 0xFFBFC7D2);
        }
    }

    /**
     * What was in it, laid out over the torn wrapper.
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
        int above = 18;
        PackLayout laid = PackLayout.fit(
                Math.max(1, revealed.size()), room, height() - above - BELOW_THE_CARDS, GAP, packHeight);
        int gridWidth = laid.width(GAP);
        int gridHeight = laid.height(GAP);
        int gridLeft = (width() - gridWidth) / 2;
        int gridTop = above + (height() - above - BELOW_THE_CARDS - gridHeight) / 2;
        long now = net.minecraft.Util.getMillis();
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
            // Turned toward the cursor, most for the one nearest it. These are the cards
            // somebody has just been given: they are the point of the whole screen, and a
            // grid of them lying flat is a spreadsheet of what was in the pack rather than a
            // handful of cards. The one you are looking at leans toward you, and a foil
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
            // Every rare and mythic lit from behind, and a special version - showcase, borderless,
            // extended art - in its own purple, pulsing slowly. Behind the card rather than a
            // border round it: a border is a frame drawn on the card, a glow is the card being
            // the one worth looking at. Steady for somebody who asked for less motion.
            int lit = RevealGlow.colorFor(ClientCardCache.get().summary(card).orElse(null));
            if (lit != 0) {
                float strength = RevealGlow.pulse(now, index, ClientSettings.reducedMotion());
                GuiGlow.around(graphics, x, y, laid.cardWidth(), laid.cardHeight(),
                        Math.max(4, laid.cardWidth() / 5),
                        (Math.round(0xD0 * strength) << 24) | (lit & 0x00FFFFFF));
            }
            // In front of its glow in depth, not only drawn after it: a card turned toward the cursor
            // leans its far edges back, and the glow, flat at the card's own depth, was drawn over them.
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, IN_FRONT_OF_ITS_GLOW);
            ClientCardCache.get().summary(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArtTurned(
                            graphics, summary, card.flipped(),
                            x, y, laid.cardWidth(), laid.cardHeight(),
                            cardYaw, cardPitch, card.foil()),
                    () -> GatheringSprites.inset(
                            graphics, x, y, laid.cardWidth(), laid.cardHeight()));
            graphics.pose().popPose();
            // Held over a card, the read-a-card key shows it here exactly as it does over a
            // hand, a pile or a draft pack. A grid of cards you have just been given and
            // cannot look at properly is the one place in the mod that would not answer it.
            if (mouseX >= x && mouseX < x + laid.cardWidth()
                    && mouseY >= y && mouseY < y + laid.cardHeight()) {
                over = card;
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
     * How far one of the pulled cards is turned toward the cursor, from nought to one.
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

    /** How far a card in the grid turns toward the cursor. */
    private static final float CARD_YAW = 9f;
    private static final float CARD_PITCH = 5.5f;

    /**
     * How far away the cursor has to be before a card stops paying attention to it.
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

    /** How far the freed strip leans back as it comes away, in degrees at a whole tear. */
    private static final float PEEL_BACK = 30f;

    /** How far it lifts clear of the pack, as a multiple of its own height, at a whole tear. */
    private static final float PEEL_LIFT = 1.15f;

    /** How much of it has faded by the time the tear is across. */
    private static final float PEEL_FADE = 0.55f;

    /**
     * What came out, worst first.
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

    /** How brightly the light comes out where it meets the paper. */
    private static final int GLOW_ALPHA = 190;

    /** How many bands the light is faded over. Enough to read as light rather than as a bar. */
    private static final int GLOW_STEPS = 7;

    /**
     * The set's symbol on the body of the wrapper, creasing and tearing with it.
     * <p>It was printed through the lens the whole pack used to be drawn with, and when the wrapper
     * became a sheet that lens went - taking the only call to this with it, silently, while the
     * comments went on describing a symbol nobody was drawing. On the cloth now, which is better
     * than where it was: it moves with the paper.
     */
    private void drawSymbol(Matrix4f matrix, Rect where) {
        // The archive is not a set, so there is no symbol to print on it and asking would
        // spend a request on a URL that cannot exist. Plain paper is the right answer.
        if (dev.gathering.item.PackComponent.ARCHIVE.equals(setCode)) {
            return;
        }
        int color = PackWrapper.symbolColor(kind);
        ClientSetSymbols.get().symbol(setCode, color, 128).ifPresent(symbol ->
                PackClothRenderer.drawSymbol(matrix, cloth, symbol, where, SYMBOL_ACROSS, color));
    }

    /** How wide the symbol is printed, as a fraction of the wrapper. */
    private static final float SYMBOL_ACROSS = 0.42f;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (turning != null && !turning.finished() && turning.grabbed(mouseX, mouseY, cardInHand())) {
            return true;
        }
        if (!cloth.isOpen() && cloth.grab(acrossSheet(mouseX), downSheet(mouseY))) {
            PackSounds.gripped();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (turning != null && !turning.finished()) {
            turning.draggedTo(dragX);
            return true;
        }
        if (cloth.isHeld()) {
            cloth.dragTo(acrossSheet(mouseX), downSheet(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (turning != null && !turning.finished()) {
            turning.letGo(cardInHand());
            return true;
        }
        if (cloth.isHeld()) {
            // Let go and it falls or springs back, depending on what is left holding it.
            cloth.letGo();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * The keyboard's way through the stack, for anybody not dragging with a mouse.
     * <p>A ceremony only a mouse can perform is a ceremony some players cannot have.
     */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (turning != null && !turning.finished() && isTurnKey(key)) {
            turning.turn();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private static boolean isTurnKey(int key) {
        return key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
    }

    /** How far along the tear was when it last made a noise. */
    private float soundedAt;

    /**
     * Where the hand is, in the sheet's own space.
     * <p>The solver works in nought to one across the wrapper and down it, whatever size the window is,
     * so this is the one place the two meet.
     */
    private float acrossSheet(double mouseX) {
        return (float) ((mouseX - packX) / Math.max(1, packWidth));
    }

    private float downSheet(double mouseY) {
        return (float) ((mouseY - packY) / Math.max(1, packHeight));
    }

    /**
     * Where the pack is on screen, so the scripted run can aim a real cursor at it.
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

    public int packTop() {
        return packY;
    }

    public int packHeight() {
        return packHeight;
    }

    public int packMiddleY() {
        return packY + packHeight / 2;
    }

    /** How many cards have been turned, or -1 where the pack is not being turned at all. For the tour. */
    public int turnedSoFar() {
        return turning == null ? -1 : turning.shown();
    }

    /** Whether every card has been turned and the spread is showing. For the tour. */
    public boolean turnedThrough() {
        return turning == null || turning.finished();
    }

    /** Whether the card in front is lit by something worth announcing behind it. For the tour. */
    public boolean promisesSomething() {
        return turning != null && turning.nextUp().worthAnnouncing();
    }

    /** The wrapper itself, for the scripted run. */
    public dev.gathering.core.ui.PackCloth cloth() {
        return cloth;
    }

    /** What came out, in the order it is being shown. Empty until the wrapper is off. */
    public List<CardComponent> shown() {
        return revealed;
    }

    /**
     * The middle of one revealed card, as it was last drawn, or null before it has been.
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

    /** Where the revealed cards were last drawn, together, or an empty rectangle before they were. */
    dev.gathering.core.ui.Rect cardsDrawn() {
        return grid == null ? dev.gathering.core.ui.Rect.NONE
                : new dev.gathering.core.ui.Rect(gridLeft, gridTop, grid.width(GAP), grid.height(GAP));
    }

    /** Where Done is. */
    dev.gathering.core.ui.Rect done() {
        return doneButton == null ? dev.gathering.core.ui.Rect.NONE
                : new dev.gathering.core.ui.Rect(doneButton.getX(), doneButton.getY(), doneButton.getWidth(), doneButton.getHeight());
    }

    /** The token the cards wait under, or blank. */
    private final String wrapper;

    /** Whether the server has been told this pack is open. Once is all it takes, and all it tears. */
    private boolean told;

    /**
     * Tells the server the wrapper is off, so the cards waiting under it are handed over. On the tear
     * finishing, or on the screen closing first: leaving a pack unopened on the screen is still having
     * opened it.
     */
    private void tellTheServerItIsOpen() {
        if (told || wrapper.isEmpty()) {
            return;
        }
        told = true;
        ClientNetworking.send(new dev.gathering.network.PackTornPayload(wrapper));
    }

    @Override
    public void removed() {
        tellTheServerItIsOpen();
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
