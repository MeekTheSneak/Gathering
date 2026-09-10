package dev.gathering.client;

import dev.gathering.core.card.MagicColor;
import dev.gathering.core.ui.ColorWheel;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.StarterPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Pick two colors, for the two boosters that finishing the guided first game earns.
 * <p>The five colors in a ring, laid out the way the back of a Magic card lays them out -
 * white at the top, then blue, black, red and green clockwise. It is the one diagram in the
 * game a newcomer has already looked at hundreds of times without being told what it was, so
 * the screen where they first choose something is a picture they already half know.
 * <p>Hovering one says what that color is <em>about</em>: peace and law, or knowledge and
 * deceit, or power and sacrifice. Those five sentences are the shortest true answer to "what
 * is Magic", and the control tutorial deliberately does not teach them - so here is where they
 * belong, on the screen where somebody is already deciding.
 * <p>The orbs are the mod's existing mana font. No new artwork: a mana symbol drawn large is a
 * mana orb, and it is already drawn on every card in the game.
 * <p>What a color buys is decided at the other end. This sends two letters; the server picks
 * the packs out of its own settings and the published collation, and what is inside them is
 * still decided when they are torn open.
 * <p>Client-only.
 */
public final class StarterColorsScreen extends Screen {

    private static final int LABEL = 0xFFF3EEE4;
    private static final int DIM = 0xFF9A9690;
    private static final int PHILOSOPHY = 0xFFD8D0C2;

    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 12;
    private static final int ROW_HEIGHT = 18;
    private static final int GAP = 6;

    /**
     * How big an orb is drawn.
     * <p>The largest the mod draws any text at, named rather than written as a number so it
     * cannot drift past the limit everything else keeps to.
     */
    private static final float ORB_SCALE = dev.gathering.core.ui.TextScale.LARGEST;

    /** How far an orb reaches from its own middle, asked of the font rather than guessed. */
    private static final int ORB_HALF = ManaText.halfHeight(ORB_SCALE);

    /**
     * Where the glow starts and where it has faded to nothing.
     * <p>It starts just inside the orb and reaches a little past it, so it reads as the orb
     * being lit rather than as a disc behind it. Everything on a spoke - the glow, the symbol,
     * the word, what counts as pointing at it - is placed from the same middle, which is the
     * whole of why it lines up. The first version drew the symbol again at a larger scale and
     * hoped; a glyph's advance width and the ink inside it are different measurements, so the
     * two copies were centered on two different points, and the orb itself was six pixels
     * above the circle you had to click.
     */
    private static final int GLOW_INNER = ORB_HALF - 1;
    private static final int GLOW_OUTER = ORB_HALF + 7;

    /**
     * How far from a spoke's middle still counts as pointing at that orb.
     * <p>The orb and a little, not the whole glow: the outer half of the glow is a few units
     * of alpha, and a click landing there is a click on nothing anybody can see.
     */
    private static final int REACH = ORB_HALF + 4;

    /**
     * How far below a spoke's middle that color's name is drawn.
     * <p>Where the glow has run out. Named once because three things need it: the label
     * itself, the room the panel keeps under the ring, and where the sentence under the wheel
     * starts.
     */
    private static final int LABEL_DROP = GLOW_OUTER - 1;

    /** How much room a spoke needs, so five of them can be spaced without touching. */
    private static final int ORB = REACH * 2;

    /** The glow behind a chosen orb, and the fainter one behind the orb under the cursor. */
    private static final int CHOSEN_GLOW = 0xC0FFF0C0;
    private static final int HOVER_GLOW = 0x50FFF0C0;

    /** Two, which is what the product is: two halves shuffle into one deck. */
    private static final int HOW_MANY = 2;

    private final List<MagicColor> picked = new ArrayList<>();

    private Rect panel = Rect.NONE;
    private int wheelCenterX;
    private int wheelCenterY;
    private int wheelRadius;

    /** Which orb the cursor is on this frame, or -1. Read by the render and by a click. */
    private int hovered = -1;

    /** How tall the words above the wheel come out, and the words below it. Measured in init. */
    private int headerHigh;
    private int footerHigh;

    /**
     * How many lines the wordiest of the five colors needs.
     * <p>Room is kept for the longest rather than for whichever is being hovered, so the panel
     * does not grow and shrink as the cursor crosses the wheel - which reads as the screen
     * twitching rather than as text changing.
     */
    private int longestPhilosophy(int room) {
        int most = 1;
        for (MagicColor color : MagicColor.values()) {
            most = Math.max(most, GuiText.linesNeeded(this.font,
                    Component.translatable(color.philosophyKey()), room));
        }
        return most;
    }

    private net.minecraft.client.gui.components.Button take;

    public StarterColorsScreen() {
        super(Component.translatable("screen.gathering.starter.title"));
    }

    @Override
    protected void init() {
        int wide = Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
        int room = wide - MARGIN * 2;

        // Measured, not counted off by eye. The line above the wheel wraps to three lines at
        // one window size and two at another, and a header guessed at three drew the white orb
        // straight through the sentence - which the scripted run photographed.
        headerHigh = this.font.lineHeight + GAP
                + GuiText.linesNeeded(this.font,
                        Component.translatable("screen.gathering.starter.why"), room)
                        * this.font.lineHeight;
        // The tallest the text under the wheel gets: a color's name and its five words. Room
        // is kept for the longest of the five rather than for whichever is showing, so the
        // panel does not change size as the cursor moves across it.
        footerHigh = longestPhilosophy(room) * this.font.lineHeight;

        // A little more than the ring itself, because every orb has a word under it and the
        // bottom two words have to sit inside the panel rather than on its edge.
        int wheel = Math.min(room, Math.max(110, this.height / 3));
        // The bottom two names hang below the ring, so the panel keeps a line for them.
        int belowTheRing = this.font.lineHeight + LABEL_DROP;
        int high = MARGIN * 2 + headerHigh + GAP + wheel + belowTheRing
                + GAP + footerHigh + GAP + ROW_HEIGHT;
        panel = new Rect(
                (this.width - wide) / 2,
                Math.max(MARGIN, (this.height - high) / 2),
                wide,
                Math.min(high, this.height - MARGIN * 2));

        wheelRadius = ColorWheel.radiusIn(wheel, wheel, ORB);
        wheelCenterX = panel.x() + panel.width() / 2;
        wheelCenterY = panel.y() + MARGIN + headerHigh + GAP + wheel / 2;

        take = this.addRenderableWidget(GatheringButtons.of(
                panel.x() + MARGIN, panel.bottom() - MARGIN - ROW_HEIGHT,
                panel.width() - MARGIN * 2, ROW_HEIGHT,
                Component.translatable("screen.gathering.starter.confirm"), this::take));
        take.active = picked.size() == HOW_MANY;
    }

    /**
     * Sends the two letters and gets out of the way.
     * <p>Closed on the press rather than waiting for the packs: what comes back is two items
     * and a line of chat, and a screen sitting over the world waiting to be told it worked is
     * a screen somebody has to dismiss.
     */
    private void take() {
        if (picked.size() != HOW_MANY) {
            return;
        }
        ClientNetworking.send(new StarterPayload(
                picked.stream().map(MagicColor::code).toList()));
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int on = ColorWheel.at((int) mouseX, (int) mouseY,
                    wheelCenterX, wheelCenterY, wheelRadius, REACH);
            if (on >= 0) {
                choose(MagicColor.values()[on]);
                GatheringButtons.clickSound();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Takes a color, or puts it back.
     * <p>Clicking one that is already chosen unchooses it, which is the only way to change
     * your mind that does not need a second control saying so. Past two, the oldest choice
     * makes room - because the alternative is a screen that stops responding to clicks and
     * does not say why.
     */
    private void choose(MagicColor color) {
        if (picked.remove(color)) {
            take.active = false;
            return;
        }
        picked.add(color);
        while (picked.size() > HOW_MANY) {
            picked.remove(0);
        }
        take.active = picked.size() == HOW_MANY;
    }

    /**
     * The keyboard route: the number keys one to five, and Enter to take them.
     * <p>Five orbs in a ring have no natural tab order and a focus ring on a circle is hard to
     * see, so the numbers are the honest way to do this without a mouse - and they are printed
     * beside each color's name rather than left to be discovered.
     */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1
                && key < org.lwjgl.glfw.GLFW.GLFW_KEY_1 + MagicColor.count()) {
            choose(MagicColor.values()[key - org.lwjgl.glfw.GLFW.GLFW_KEY_1]);
            GatheringButtons.clickSound();
            return true;
        }
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
                && picked.size() == HOW_MANY) {
            take();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovered = ColorWheel.at(mouseX, mouseY,
                wheelCenterX, wheelCenterY, wheelRadius, REACH);
        super.render(graphics, mouseX, mouseY, partialTick);

        int room = panel.width() - MARGIN * 2;
        int y = panel.y() + MARGIN;
        GuiText.drawCentered(graphics, this.font, this.title,
                panel.x() + panel.width() / 2, y, room, LABEL);
        y += this.font.lineHeight + GAP;
        GuiText.drawWrappedCentered(graphics, this.font,
                Component.translatable("screen.gathering.starter.why"),
                panel.x() + panel.width() / 2, y, room, DIM);

        renderWheel(graphics);
        renderWhatIsChosen(graphics);
    }

    /** The five orbs, and a ring behind the ones that have been picked. */
    private void renderWheel(GuiGraphics graphics) {
        List<ColorWheel.Spoke> ring =
                ColorWheel.spokes(wheelCenterX, wheelCenterY, wheelRadius);
        for (ColorWheel.Spoke spoke : ring) {
            MagicColor color = MagicColor.values()[spoke.index()];
            Component symbol = ManaText.of("{" + color.code() + "}");

            // A round glow for a round thing, drawn from the orb's own middle. Chosen glows
            // brightest; hovered glows faintly, so the two are told apart by how much rather
            // than only by hue - the same thing said twice for somebody who cannot see the
            // difference in color.
            int glow = picked.contains(color) ? CHOSEN_GLOW
                    : spoke.index() == hovered ? HOVER_GLOW : 0;
            if (glow != 0) {
                GuiGlow.render(graphics, ManaText.inkCenterX(spoke.x(), ORB_SCALE), spoke.y(),
                        GLOW_INNER, GLOW_OUTER, glow);
            }

            // The mod's own mana font, drawn large. A mana symbol at this size is a mana orb,
            // and it is already on every card in the game - so there is no new artwork here.
            // Placed by its middle rather than by the top of its line, so it lands on the
            // spoke the glow and the hit test are already using.
            GuiText.drawCenteredAt(graphics, this.font, symbol,
                    spoke.x(), ManaText.drawYForMiddle(this.font, spoke.y(), ORB_SCALE),
                    ORB_SCALE, 0xFFFFFFFF);
            // The name under each orb, always. A newcomer who does not yet read a mana symbol
            // needs the word, and a name is also what makes the ring say something in a
            // screenshot. It sits tight under the orb - a label further down grouped itself
            // with the row below instead, which the scripted run photographed.
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable(color.key()),
                    spoke.x(), spoke.y() + LABEL_DROP, ORB * 3,
                    picked.contains(color) ? LABEL : DIM);
        }
    }

    /**
     * What the cursor is on, or what has been chosen, under the wheel.
     * <p>One block of text that says three things in order of what the player needs: the
     * color they are pointing at and what it stands for while they are choosing, then which
     * two they have, then how many more to pick.
     */
    private void renderWhatIsChosen(GuiGraphics graphics) {
        int room = panel.width() - MARGIN * 2;
        int middle = panel.x() + panel.width() / 2;
        int y = wheelCenterY + wheelRadius + LABEL_DROP + this.font.lineHeight + GAP;

        if (hovered >= 0) {
            // The philosophy alone: the name is already under the orb the cursor is on, and
            // printing it twice a finger apart says the screen is repeating itself.
            GuiText.drawWrappedCentered(graphics, this.font,
                    Component.translatable(MagicColor.values()[hovered].philosophyKey()),
                    middle, y, room, PHILOSOPHY);
            return;
        }
        if (picked.size() == HOW_MANY) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.starter.chosen",
                            Component.translatable(picked.get(0).key()),
                            Component.translatable(picked.get(1).key())),
                    middle, y, room, LABEL);
            return;
        }
        GuiText.drawCentered(graphics, this.font,
                Component.translatable(picked.isEmpty()
                        ? "screen.gathering.starter.hover_hint"
                        : "screen.gathering.starter.pick_one"),
                middle, y, room, DIM);
    }

    /**
     * Where the wheel's middle is, for the scripted run.
     * <p>Read off the screen rather than worked out again, so the run clicks where the render
     * actually drew - a wheel drawn in one place and clicked in another is exactly the fault
     * that asking twice would let through.
     */
    int[] wheelMiddleForTesting() {
        return new int[] {wheelCenterX, wheelCenterY};
    }

    /** How big it is, as above. */
    int wheelRadiusForTesting() {
        return wheelRadius;
    }

    /** How many colors have been picked, for the scripted run. */
    int chosen() {
        return picked.size();
    }

    /** Whether the confirm button is live, for the scripted run. */
    boolean canTake() {
        return take != null && take.active;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
