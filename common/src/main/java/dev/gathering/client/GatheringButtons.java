package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gathering.client.GatheringSprites.Element;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Buttons that behave like buttons.
 *
 * <p>Everything clickable in this mod used to be a rectangle that happened to react to a
 * click. That is not the same thing: a real button lights up when the cursor is over it, dips
 * when you press it, clicks when it takes, grays out when it will not, and can be reached with
 * the keyboard. Losing any of those makes a screen feel broken even when it works, because you
 * cannot tell whether anything happened.
 *
 * <p>So these are vanilla {@link Button}s: vanilla's states, vanilla's focus, vanilla's click.
 * Only the face is the mod's own, and only because the mod now has looks - a grey vanilla
 * button sitting inside a brown Retro panel is exactly the "nearly like the others" problem
 * this used to avoid by not skinning them at all. Everything a player already knows about
 * these still holds; they are only painted in the look they are sitting in.
 *
 * <p>The one thing vanilla has no notion of is a button that is <em>currently chosen</em>,
 * which a format picker needs, so {@link #toggle} adds that on top and nothing else.
 *
 * <p>Client-only.
 */
public final class GatheringButtons {

    /** The label on a button that will do something, and on one that will not. */
    private static final int LABEL = 0xE8E4DC;
    private static final int LABEL_OFF = 0x8A8681;

    private GatheringButtons() {
    }

    public static Button of(int x, int y, int width, int height, Component label, Runnable action) {
        return new Fitting(x, y, width, height, label, ignored -> action.run());
    }

    /**
     * A button whose label fits inside it.
     *
     * <p>Vanilla scrolls a label too wide for its button, back and forth, forever. On a button
     * you press once that is not reading - it is a word arriving in installments, and the
     * moment you look at it you are as likely to see "ack to printe" as anything. A screenshot
     * of this mod's own pen showed exactly that.
     *
     * <p>So it shrinks to fit and then trims, which is what every other piece of text here
     * does. The one thing it must never do is show a fragment that reads as a different word.
     */
    private static class Fitting extends Button {

        private Fitting(int x, int y, int width, int height, Component label, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        }

        /**
         * The face, in whatever look is on, and then the label.
         *
         * <p>The same three states vanilla draws and in the same order, so that a button
         * behaves identically and only looks different. The alpha is honored because screens
         * fade widgets in and out and a face that ignored it would pop.
         */
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Element face = !active
                    ? Element.BUTTON_OFF
                    : isHoveredOrFocused() ? Element.BUTTON_HOVER : Element.BUTTON;
            RenderSystem.enableBlend();
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            GatheringSprites.draw(graphics, face, getX(), getY(), getWidth(), getHeight());
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            renderString(graphics, Minecraft.getInstance().font,
                    (active ? LABEL : LABEL_OFF) | Mth.ceil(alpha * 255.0F) << 24);
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            GuiText.drawCentered(graphics, font, getMessage(),
                    getX() + getWidth() / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    getWidth() - TEXT_MARGIN * 2, color);
        }
    }

    /**
     * A button whose label is a direction rather than a word.
     *
     * <p>Page turns were the characters "&lt;" and "&gt;" set in the game's font. That is a
     * button labelled with punctuation: at a glance it reads as text somebody forgot to
     * finish rather than as a control, and it says nothing to a screen reader either. This
     * draws the arrow and keeps a real sentence as the message, so the narration and the
     * tooltip both say what pressing it does while the face says which way it goes.
     *
     * @param says what this arrow does, for the tooltip and the narrator - never drawn
     */
    public static Button arrow(
            int x, int y, int width, int height, Element which, Component says, Runnable action) {
        return arrow(x, y, width, height, () -> which, says, action);
    }

    /**
     * The same, for a button whose arrow turns round - a sort order, say.
     *
     * <p>Asks a supplier rather than holding the direction, for the reason {@link #toggle}
     * does: a button that remembers its own answer is a button that can disagree with the
     * screen about which way the list is currently sorted.
     */
    public static Button arrow(int x, int y, int width, int height, Supplier<Element> which,
            Component says, Runnable action) {
        Pointing button = new Pointing(x, y, width, height, says, ignored -> action.run(), which);
        button.setTooltip(Tooltip.create(says));
        return button;
    }

    /**
     * A button whose face is one character but whose message is a sentence.
     *
     * <p>The same problem the arrows solve, for the one control where the mark really is the
     * label: a help button is a question mark everywhere, and drawing an arrow on it would be
     * worse. What it must not do is <em>be</em> a question mark to everything that reads the
     * button - the tooltip, the narrator, and the test that looks for exactly this.
     *
     * @param mark what is drawn on it, one or two characters
     * @param says what pressing it does, for the tooltip and the narrator
     */
    public static Button glyph(
            int x, int y, int width, int height, String mark, Component says, Runnable action) {
        Marked button = new Marked(x, y, width, height, says, ignored -> action.run(), mark);
        button.setTooltip(Tooltip.create(says));
        return button;
    }

    private static final class Marked extends Fitting {

        private final Component mark;

        private Marked(int x, int y, int width, int height, Component says, OnPress onPress,
                String mark) {
            super(x, y, width, height, says, onPress);
            this.mark = Component.literal(mark);
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            GuiText.drawCentered(graphics, font, mark,
                    getX() + getWidth() / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    getWidth() - TEXT_MARGIN * 2, color);
        }
    }

    private static final class Pointing extends Fitting {

        private final Supplier<Element> which;

        private Pointing(int x, int y, int width, int height, Component says, OnPress onPress,
                Supplier<Element> which) {
            super(x, y, width, height, says, onPress);
            this.which = which;
        }

        /**
         * Keeps the tooltip saying whatever the message says.
         *
         * <p>A sort order's arrow turns round and its sentence turns with it; a tooltip set
         * once when the button was made would go on describing the other direction forever.
         */
        @Override
        public void setMessage(Component says) {
            super.setMessage(says);
            setTooltip(Tooltip.create(says));
        }

        /**
         * The arrow instead of the words, dimmed when the button will not do anything.
         *
         * <p>Dimming matters more here than on a worded button: a label greys out on its own
         * because the text is drawn in a second colour, and an arrow blitted at full strength
         * onto a dead button is the one part of it still claiming to work.
         */
        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            float shade = active ? 1.0F : 0.55F;
            graphics.setColor(shade, shade, shade, alpha);
            GatheringSprites.arrow(graphics, which.get(), getX(), getY(), getWidth(), getHeight());
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * A button that shows whether its option is the one currently picked.
     *
     * <p>Asks a supplier rather than holding a flag, so one button cannot disagree with the
     * screen about what is selected - which is exactly what happens when eight buttons each
     * remember their own answer.
     */
    public static Button toggle(
            int x, int y, int width, int height, Component label, BooleanSupplier chosen, Runnable action) {
        return new Toggle(x, y, width, height, label, ignored -> action.run(), chosen);
    }

    /** Named rather than anonymous only because a subclass may reach {@code DEFAULT_NARRATION}. */
    private static final class Toggle extends Fitting {

        private final BooleanSupplier chosen;

        private Toggle(
                int x, int y, int width, int height, Component label, OnPress onPress, BooleanSupplier chosen) {
            super(x, y, width, height, label, onPress);
            this.chosen = chosen;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (chosen.getAsBoolean()) {
                // Thicker than a hover ring: a single-pixel outline on a vanilla button's
                // own border is nearly invisible, and this has to be findable at a glance.
                GatheringSprites.draw(graphics, Element.CHOSEN_RING,
                        getX(), getY(), getWidth(), getHeight());
            }
        }
    }

    /**
     * The click a button makes, for the things that cannot be buttons.
     *
     * <p>A card on the table and a row in a list are not widgets and should not become them -
     * a hundred-row deck list made of a hundred focusable buttons is a screen you cannot tab
     * through. They still have to sound like they were pressed, because that noise is how a
     * player knows the click landed on something.
     */
    public static void clickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
