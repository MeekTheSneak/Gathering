package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Buttons that behave like buttons.
 *
 * <p>Everything clickable in this mod used to be a rectangle that happened to react to a
 * click. That is not the same thing: a real button lights up when the cursor is over it, dips
 * when you press it, clicks when it takes, grays out when it will not, and can be reached with
 * the keyboard. Losing any of those makes a screen feel broken even when it works, because you
 * cannot tell whether anything happened.
 *
 * <p>So these are vanilla {@link Button}s, drawn by vanilla, sounding like vanilla. Not a
 * reskin - a player already knows what these do, and a bespoke button that looks nearly like
 * the ones in every other screen is worse than one that looks exactly like them.
 *
 * <p>The one thing vanilla has no notion of is a button that is <em>currently chosen</em>,
 * which a format picker needs, so {@link #toggle} adds that on top and nothing else.
 *
 * <p>Client-only.
 */
public final class GatheringButtons {

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

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            GuiText.drawCentered(graphics, font, getMessage(),
                    getX() + getWidth() / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    getWidth() - TEXT_MARGIN * 2, color);
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
