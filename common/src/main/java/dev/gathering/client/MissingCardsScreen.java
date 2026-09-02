package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.collection.MissingCards;
import dev.gathering.core.ui.ListScreenLayout;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.SetMissingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The cards of one set that are not in this collection.
 * <p>What was behind the number. The set list says a collection is one card into a set of
 * three hundred and seventy-three and then, until this existed, had nothing to answer the only
 * question that raises. A count is a scoreboard; this is a list to go and find, which is what
 * somebody sitting at their binder actually wanted.
 * <p>Rows rather than a grid of art, because the shape of the question is "what do I need"
 * rather than "what does it look like" - a hundred pictures is a lovely thing to scroll and a
 * poor thing to read down. The art is still a glance away: the cursor offers each row to the
 * inspect panel, the same as every other list in the mod, so a card can be read without
 * leaving the list.
 * <p>Client-only.
 */
public final class MissingCardsScreen extends ChildScreen {

    /** Taken from the shared layout, so the heading lines up with the rows under it. */
    private static final int MARGIN = ListScreenLayout.margin();

    private static final int ROW_HEIGHT = 12;

    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;

    /** A card's rarity, in the colors the game already prints rarities in. */
    private static final int COMMON = 0xFFB8B8B8;
    private static final int UNCOMMON = 0xFFB9C7D4;
    private static final int RARE = 0xFFD9C07A;
    private static final int MYTHIC = 0xFFD98C4A;

    private final MissingCards missing;
    private final int total;

    private int scroll;
    private int hovered = -1;

    private MissingCardsScreen(SetProgressScreen sets, MissingCards missing, int total) {
        super(Component.translatable("screen.gathering.missing.title", missing.name()), sets);
        this.missing = missing;
        this.total = total;
    }

    /**
     * Which collection somebody asked about, if they are still waiting to be shown.
     * <p>The same rule the set list follows: only the answer somebody asked for opens a
     * screen. Without it a list that arrived after they had moved on would open itself on
     * top of whatever they were doing.
     */
    private static String waitingFor;

    /** Somebody pressed a set. The next answer for it is theirs to be shown. */
    static void asked(String setCode) {
        waitingFor = setCode;
    }

    /** Opens it on the answer that was asked for, and ignores any other. */
    public static void accept(SetMissingPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof SetProgressScreen sets)
                || !payload.code().equals(waitingFor)) {
            return;
        }
        waitingFor = null;
        client.setScreen(new MissingCardsScreen(sets, payload.asMissing(), payload.missing()));
    }

    @Override
    protected void init() {
        addRenderableWidget(GatheringButtons.of(layout(0).done(),
                Component.translatable("gui.done"), this::onClose));
    }

    /**
     * Where everything on this screen goes - the same shape the set progress list has, and
     * the same arithmetic, checked in the pure module against every window size.
     *
     * @param moreWidth how wide the "N more" line is, or 0 when nothing is out of sight
     */
    private ListScreenLayout layout(int moreWidth) {
        return ListScreenLayout.of(this.width, this.height, ROW_HEIGHT, moreWidth);
    }

    private int rowsThatFit() {
        return layout(0).rowsThatFit();
    }

    private int hiddenBelow() {
        return Math.max(0, missing.count() - rowsThatFit());
    }

    private Rect rowAt(int index) {
        return layout(0).rowAt(index);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.draw(graphics, Element.SETS_BACKDROP, 0, 0, this.width, this.height);
        GatheringSprites.panel(graphics, MARGIN - 8, MARGIN - 8,
                this.width - (MARGIN - 8) * 2, this.height - (MARGIN - 8) * 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.missing.heading", missing.name(), total),
                MARGIN, MARGIN, this.width - MARGIN * 2, TEXT);

        if (missing.count() == 0) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.missing.complete"),
                    this.width / 2, this.height / 2 - 4, this.width - MARGIN * 2, DIM);
            return;
        }

        hovered = -1;
        int showing = Math.min(rowsThatFit(), missing.count() - scroll);
        for (int index = 0; index < showing; index++) {
            MissingCards.Card card = missing.cards().get(scroll + index);
            Rect row = rowAt(index);
            if (row.contains(mouseX, mouseY)) {
                hovered = scroll + index;
                GatheringSprites.draw(graphics, Element.ROW_HOVER,
                        row.x() - 2, row.y(), row.width() + 4, row.height());
            } else if ((scroll + index) % 2 == 1) {
                GatheringSprites.draw(graphics, Element.ROW_ODD,
                        row.x() - 2, row.y(), row.width() + 4, row.height());
            }
            drawRow(graphics, card, row);
        }

        // Whatever the cursor is on, offered to the inspect panel - so reading one of these
        // is the same gesture as reading a card anywhere else in the mod.
        offerToInspector();

        // Three things share the foot: the hint, the count of what is out of sight, and the
        // way out. Laid out right to left in ListScreenLayout, so the hint is what gives way.
        Component more = hiddenBelow() > 0
                ? Component.translatable("screen.gathering.missing.more", hiddenBelow())
                : null;
        ListScreenLayout foot = layout(more == null ? 0 : this.font.width(more));
        if (more != null) {
            GuiText.drawFlushRight(graphics, this.font, more,
                    foot.more().right(), foot.more().y(), 1f, DIM);
        }
        if (foot.hint().width() > 0) {
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.missing.hint"),
                    foot.hint().x(), foot.hint().y(), foot.hint().width(), DIM);
        }
    }

    /**
     * One card: its number, its name, and its rarity.
     * <p>The number first and in a column, because a set is laid out in that order and
     * somebody working through one is reading down it. Rarity as the color of the name rather
     * than a word, because it is a column of three hundred and a word would be a column of
     * three hundred words nobody reads.
     */
    private void drawRow(GuiGraphics graphics, MissingCards.Card card, Rect row) {
        int numberRoom = 34;
        GuiText.drawFlushRight(graphics, this.font,
                Component.literal(Integer.toString(card.number())),
                row.x() + numberRoom - 6, row.y() + 1, 1f, DIM);
        boolean chasing = ClientWants.wants(card.printing());
        int marked = chasing ? WANTED_MARK : 0;
        if (chasing) {
            GatheringSprites.draw(graphics, Element.WANTED_MARK,
                    row.x() + numberRoom, row.y() + 2, MARK_WIDTH, this.font.lineHeight - 2);
        }
        GuiText.draw(graphics, this.font, Component.literal(card.name()),
                row.x() + numberRoom + marked, row.y() + 1, row.width() - numberRoom - marked,
                chasing ? WANTED_TEXT : colorOf(card.rarity()));
    }

    /** How much room the mark beside a wanted card's name takes. */
    private static final int MARK_WIDTH = 5;
    private static final int WANTED_MARK = MARK_WIDTH + 3;

    /** A card on the list is named in the color of the list rather than of its rarity. */
    private static final int WANTED_TEXT = 0xFFFFD479;

    /**
     * Pressing a card puts it on the wants list, or takes it off.
     * <p>The one thing to do to a card on this screen, so it is the plain click. A list of
     * three hundred cards to find is a list somebody reads once; a list they can tick down is
     * one they come back to - and the ticking has to happen here, where they are already
     * looking at the names.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0 && hovered < missing.count()) {
            GatheringButtons.clickSound();
            ClientWants.toggle(missing.cards().get(hovered).printing());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int colorOf(Rarity rarity) {
        return switch (rarity) {
            case MYTHIC -> MYTHIC;
            case RARE -> RARE;
            case UNCOMMON -> UNCOMMON;
            default -> COMMON;
        };
    }

    /** The card under the cursor, as an item, which is what the inspect panel reads. */
    private void offerToInspector() {
        if (hovered < 0 || hovered >= missing.count()) {
            ClientHoverState.clear();
            return;
        }
        ClientHoverState.setHovered(CardItem.of(CardComponent.of(
                CardIdentity.ofPrinting(missing.cards().get(hovered).printing()))));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, hiddenBelow());
        return true;
    }

    /** The middle of one row, for the scripted run to put a cursor on. */
    int[] middleOfRow(int index) {
        if (index < 0 || index >= missing.count() || index < scroll
                || index - scroll >= rowsThatFit()) {
            return null;
        }
        Rect row = rowAt(index - scroll);
        return new int[] {(int) row.centerX(), (int) row.centerY()};
    }

    /** Which card one row is about, so the run can check what it asked for came back. */
    java.util.UUID printingOfRow(int index) {
        return index >= 0 && index < missing.count()
                ? missing.cards().get(index).printing() : null;
    }

    /** What the cursor is on, for the scripted run. */
    String hoveredName() {
        return hovered >= 0 && hovered < missing.count()
                ? missing.cards().get(hovered).name() : "";
    }

    /** How many this screen was told are missing, for the scripted run. */
    int total() {
        return total;
    }

    @Override
    public void removed() {
        ClientHoverState.clear();
        super.removed();
    }
}
