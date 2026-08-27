package dev.gathering.client;

import dev.gathering.network.AnteAnswerPayload;
import dev.gathering.network.AnteConsentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Playing for keeps: the question, before the game.
 *
 * <p>The one screen in the mod that stands between somebody and losing a card, so it says
 * what it costs in the first line and gives equal weight to both answers. No default, no
 * button that is easier to hit than the other, and no way to answer it by pressing Escape:
 * the mistake this exists to prevent is somebody agreeing without reading.
 *
 * <p>Once said, an answer can be changed for as long as anybody is still deciding - which is
 * the honest window, because the pot does not exist until the last seat answers.
 *
 * <p>Client-only.
 */
public final class AnteConsentScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 12;
    private static final int GAP = 8;
    private static final int ROW = 20;
    private static final int PANEL_WIDTH = 350;

    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int STAKE = 0xFFE0B15A;

    private final BlockPos table;
    private AnteConsentPayload asked;

    private Button inButton;

    private AnteConsentScreen(AnteConsentPayload asked) {
        super(Component.translatable("screen.gathering.ante"));
        this.table = asked.table();
        this.asked = asked;
    }

    /** Opens the question, updates the one already up, or takes it away when it is over. */
    public static void accept(AnteConsentPayload asked) {
        Minecraft client = Minecraft.getInstance();
        if (asked.over()) {
            if (client.screen instanceof AnteConsentScreen) {
                client.setScreen(null);
            }
            return;
        }
        if (client.screen instanceof AnteConsentScreen already) {
            already.update(asked);
            return;
        }
        client.setScreen(new AnteConsentScreen(asked));
    }

    private void update(AnteConsentPayload next) {
        this.asked = next;
        if (this.inButton != null) {
            this.inButton.active = !next.iAmIn();
        }
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelHeight() {
        return PADDING * 2 + this.font.lineHeight * 5 + GAP * 2 + ROW;
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    @Override
    protected void init() {
        int inner = panelWidth() - PADDING * 2;
        int left = panelLeft() + PADDING;
        int top = panelTop() + panelHeight() - PADDING - ROW;
        // Equal halves. A bigger yes than no on a question about losing a card would be the
        // interface taking a side.
        int half = (inner - GAP) / 2;

        addRenderableWidget(GatheringButtons.of(left, top, half, ROW,
                Component.translatable("screen.gathering.ante.out"), () -> answer(false)));
        this.inButton = GatheringButtons.of(left + inner - half, top, half, ROW,
                Component.translatable("screen.gathering.ante.in"), () -> answer(true));
        this.inButton.active = !asked.iAmIn();
        addRenderableWidget(this.inButton);
    }

    private void answer(boolean in) {
        ClientNetworking.send(new AnteAnswerPayload(this.table, in));
        if (!in) {
            // Saying no ends the question for the whole table, and the server will take this
            // screen away. Closing now means the refusal is not sitting there being re-read.
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panelLeft(), panelTop(), panelWidth(), panelHeight());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int inner = panelWidth() - PADDING * 2;
        int center = panelLeft() + panelWidth() / 2;
        int line = panelTop() + PADDING;

        GuiText.drawCentered(graphics, this.font, getTitle(), center, line, inner, TEXT);
        line += this.font.lineHeight + GAP;

        // The cost, in the first line, in its own color. A question about ante that made
        // somebody go and look up what ante means is a question answered by guessing.
        // One card and several cards are two sentences, not one with a bracket in it. The
        // game's translation format has no plural rule, so the screen picks the wording.
        GuiText.drawCentered(graphics, this.font,
                Component.translatable(asked.cardsEach() == 1
                        ? "screen.gathering.ante.stakes_one"
                        : "screen.gathering.ante.stakes", asked.cardsEach()),
                center, line, inner, STAKE);
        line += this.font.lineHeight + 2;
        GuiText.drawCentered(graphics, this.font,
                Component.translatable("screen.gathering.ante.detail"), center, line, inner, DIM);
        line += this.font.lineHeight + 2;

        GuiText.drawCentered(graphics, this.font,
                asked.iAmIn()
                        ? Component.translatable("screen.gathering.ante.waiting", asked.waitingOn())
                        : Component.translatable("screen.gathering.ante.your_call"),
                center, line, inner, DIM);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Escape is not an answer. Whichever way it were read - as a yes or as a no - it
        // would be a card changing hands because somebody reached for the wrong key.
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Whether this player has already said yes. For the scene that photographs it. */
    boolean saidYes() {
        return asked.iAmIn();
    }
}
