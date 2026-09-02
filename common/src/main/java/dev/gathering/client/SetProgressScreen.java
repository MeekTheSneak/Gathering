package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.collection.SetCompletion;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.SetProgressPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * How much of each set is in this collection.
 *
 * <p>The one question a binder cannot answer by being looked at. A collection screen already
 * says what is in it; this says what is missing from it, which is the thing that makes the
 * next pack worth opening.
 *
 * <p>Sorted by what you are nearest to finishing rather than alphabetically or by date,
 * because "what am I close to?" is the question somebody opens it to ask - see
 * {@link SetCompletion}. A row is a set, a bar and two numbers, and pressing one takes the
 * collection behind it down to that set: seeing that you are at a hundred and forty of two
 * hundred and eighty-one and then having to type the set code would be the screen showing you
 * something and then making you find it again.
 *
 * <p>Client-only.
 */
public final class SetProgressScreen extends ChildScreen {

    private static final int MARGIN = 16;
    private static final int TOP_BAR = 34;
    private static final int BOTTOM_BAR = 30;

    /** The way out, and the space kept clear around everything on the bottom row. */
    private static final int DONE_WIDTH = 56;
    private static final int GAP = 8;
    /**
     * How tall a row is, and the bar that sits under its words.
     *
     * <p>Twenty-six rather than twenty-two: the words take ten and the bar is eleven, which
     * is the height its art was drawn at. The wall is how thick the track's box is, and is
     * what the fill is inset by so it runs inside the box rather than over it.
     */
    private static final int ROW_HEIGHT = 26;
    private static final int BAR_HIGH = 11;
    private static final int BAR_WALL = 3;

    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int WARNING = 0xFFFFD98A;

    private static final int DONE_TEXT = 0xFFD9A441;

    private final BlockPos where;
    private final CollectionScreen collection;
    /** Replaced when a later answer arrives, which is why neither of these is final. */
    private List<SetCompletion> sets;
    private int stillLooking;

    private int scroll;
    private int hovered = -1;

    private SetProgressScreen(
            CollectionScreen collection, BlockPos where,
            List<SetCompletion> sets, int stillLooking) {
        super(Component.translatable("screen.gathering.sets.title"), collection);
        this.collection = collection;
        this.where = where;
        this.sets = sets;
        this.stillLooking = stillLooking;
    }

    /**
     * Which collection somebody has asked about and not yet been shown, if any.
     *
     * <p>The answer to the first ask opens this screen; every answer after it only refreshes
     * one that is already open. Without that distinction the second answer - and the server
     * sends one every second while it is still naming cards - reopened the list in front of
     * somebody who had just pressed a set and been taken back to their collection, which is
     * the interface undoing a press the player had made.
     */
    private static BlockPos waitingFor;

    /** Somebody pressed the button. The next answer for this block is theirs to be shown. */
    static void asked(BlockPos collection) {
        waitingFor = collection;
    }

    /**
     * Opens it if it was asked for, refreshes it if it is already up, and otherwise leaves
     * the screen alone.
     *
     * <p>Refreshing matters because the server looks up whatever it could not name and the
     * count settles a moment later: a screen that could only be opened once would show the
     * unsettled number for as long as somebody left it open.
     */
    public static void accept(SetProgressPayload payload) {
        Minecraft client = Minecraft.getInstance();
        List<SetCompletion> rows = new ArrayList<>(payload.sets().size());
        for (SetProgressPayload.Row row : payload.sets()) {
            rows.add(row.asProgress());
        }
        if (client.screen instanceof SetProgressScreen open
                && open.where.equals(payload.collection())) {
            // The same screen with new numbers in it, rather than a new screen. Building a
            // fresh one lost the scroll and the row under the cursor every time an answer
            // landed - and while the server is still naming cards an answer lands every
            // second, so a long list could not be read at all.
            open.sets = List.copyOf(rows);
            open.stillLooking = payload.stillLooking();
            open.scroll = Math.clamp(open.scroll, 0, open.hiddenBelow());
            return;
        }
        if (client.screen instanceof CollectionScreen showing
                && payload.collection().equals(waitingFor)) {
            waitingFor = null;
            client.setScreen(new SetProgressScreen(
                    showing, payload.collection(), List.copyOf(rows), payload.stillLooking()));
        }
    }

    @Override
    protected void init() {
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - DONE_WIDTH, this.height - BOTTOM_BAR + 6, DONE_WIDTH, 18,
                Component.translatable("gui.done"), this::onClose));
    }

    /** How many rows the window has room for. */
    private int rowsThatFit() {
        return Math.max(1, (this.height - TOP_BAR - BOTTOM_BAR) / ROW_HEIGHT);
    }

    /** How far the list can be scrolled, in rows. */
    private int hiddenBelow() {
        return Math.max(0, sets.size() - rowsThatFit());
    }

    private Rect rowAt(int index) {
        return new Rect(MARGIN, TOP_BAR + index * ROW_HEIGHT,
                this.width - MARGIN * 2, ROW_HEIGHT - 2);
    }

    /**
     * The collection behind, then a panel of this screen's own over the top of it.
     *
     * <p>Its own panel rather than the scrim a small child screen sits on, because this one
     * is a full window of rows: on a scrim, the list underneath showed straight through it
     * and neither list could be read. A detour is allowed to cover what it was opened from
     * when the detour is the size of the thing behind it.
     *
     * <p>Here rather than in {@link #render}, which is where the first attempt put it - and
     * a panel drawn after the screen renders is a panel drawn over this screen's own buttons.
     * The way out was underneath it, which is the one control that must never be.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.draw(graphics, Element.SETS_BACKDROP,
                0, 0, this.width, this.height);
        GatheringSprites.panel(graphics, MARGIN - 8, MARGIN - 8,
                this.width - (MARGIN - 8) * 2, this.height - (MARGIN - 8) * 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        GuiText.draw(graphics, this.font, Component.translatable(
                        "screen.gathering.sets.heading", sets.size()),
                MARGIN, MARGIN, this.width - MARGIN * 2, TEXT);
        if (stillLooking > 0) {
            // Said out loud rather than swallowed. The count is short by this many cards
            // until the lookups land, and a total quietly wrong is the screen lying about the
            // one number somebody opened it for.
            GuiText.draw(graphics, this.font, Component.translatable(
                            "screen.gathering.sets.still_looking", stillLooking),
                    MARGIN, MARGIN + 11, this.width - MARGIN * 2, WARNING);
        }
        if (sets.isEmpty()) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.sets.nothing"),
                    this.width / 2, this.height / 2 - 4, this.width - MARGIN * 2, DIM);
            return;
        }

        hovered = -1;
        int showing = Math.min(rowsThatFit(), sets.size() - scroll);
        for (int index = 0; index < showing; index++) {
            SetCompletion set = sets.get(scroll + index);
            Rect row = rowAt(index);
            boolean under = row.contains(mouseX, mouseY);
            if (under) {
                hovered = scroll + index;
                GatheringSprites.highlight(graphics, row.x(), row.y(), row.width(), row.height());
            }
            drawRow(graphics, set, row);
        }
        // Three things share the foot: the hint on the left, the Done button on the right,
        // and this between them. Laid out from halves of the screen the first two ran into
        // each other on any window narrow enough - which is every window at GUI scale 4.
        int footY = this.height - BOTTOM_BAR + 10;
        int rightEdge = this.width - MARGIN - DONE_WIDTH - GAP;
        int hintRoom = rightEdge - MARGIN;
        if (hiddenBelow() > 0) {
            Component more = Component.translatable("screen.gathering.sets.more", hiddenBelow());
            int wide = this.font.width(more);
            GuiText.drawFlushRight(graphics, this.font, more, rightEdge, footY, 1f, DIM);
            hintRoom -= wide + GAP;
        }
        // Said, because the two buttons now do two different things and a row that answers
        // one question on the left and another on the right is a row nobody would guess at.
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.sets.hint"),
                MARGIN, footY, Math.max(1, hintRoom), DIM);
    }

    /**
     * One set: its name, how far along it is, and the bar.
     *
     * <p>The numbers on the right where a column of them lines up, the name on the left where
     * a column of those does. A bar under both rather than beside them, so the shape of the
     * whole list reads at a glance and the eye can skip the numbers entirely.
     */
    private void drawRow(GuiGraphics graphics, SetCompletion set, Rect row) {
        int numbers = 96;
        int nameRoom = Math.max(20, row.width() - numbers - 8);
        GuiText.draw(graphics, this.font, Component.literal(set.name()),
                row.x(), row.y() + 1, nameRoom, set.isComplete() ? DONE_TEXT : TEXT);

        Component count = set.extras() > 0
                ? Component.translatable("screen.gathering.sets.count_with_extras",
                        set.owned(), set.size(), set.extras())
                : Component.translatable("screen.gathering.sets.count", set.owned(), set.size());
        GuiText.drawFlushRight(graphics, this.font, count, row.right(), row.y() + 1, 1f,
                set.isComplete() ? DONE_TEXT : DIM);

        // Eleven pixels, which is the height the track was drawn at. It used to be three -
        // shorter than the nine-slice's own borders, so the cut ends, the wall and the
        // outline all met in the middle and none of them reached the screen, and the bar
        // came out a flat line whatever look was on.
        int barTop = row.y() + this.font.lineHeight + 3;
        int barHigh = BAR_HIGH;
        GatheringSprites.draw(graphics, Element.BAR_TRACK,
                row.x(), barTop, row.width(), barHigh);
        // Inside the track's wall rather than over it. The track is a hollow box and the
        // fill is a separate bar that runs down the inside of it, so a fill drawn at the
        // same rectangle would cover the box it is meant to be filling.
        int room = row.width() - BAR_WALL * 2;
        int full = Math.round(room * set.share());
        if (full > 0) {
            GatheringSprites.draw(graphics,
                    set.isComplete() ? Element.BAR_DONE : Element.BAR_FILL,
                    row.x() + BAR_WALL, barTop + BAR_WALL, full, barHigh - BAR_WALL * 2);
        }
    }

    /**
     * The set the cursor is on, for the scripted run.
     *
     * <p>Which row that is depends on what the server answered, and the scripted run cannot
     * know that - a machine with a network gets real sets and one without gets whatever the
     * run fed it. So the check is that pressing a row narrows the collection to <em>that</em>
     * row, which is a question the screen can answer about itself.
     */
    String hoveredCode() {
        return hovered >= 0 && hovered < sets.size() ? sets.get(hovered).code() : "";
    }

    /**
     * Pressing a set.
     *
     * <p>Left for the list of what is missing, because that is the question the row raises:
     * a number saying one of three hundred and seventy-three is not an answer to anything on
     * its own. Right for the other half of it - back to the collection showing only this set,
     * which is what you own rather than what you do not.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hovered >= 0 && hovered < sets.size() && (button == 0 || button == 1)) {
            GatheringButtons.clickSound();
            SetCompletion set = sets.get(hovered);
            if (button == 1) {
                Minecraft.getInstance().setScreen(collection);
                collection.showOnly(set.code());
                return true;
            }
            // Asked out loud, because the answer is what opens the screen and only the
            // answer somebody asked for may. See MissingCardsScreen.asked.
            MissingCardsScreen.asked(set.code());
            ClientNetworking.send(
                    new dev.gathering.network.AskSetMissingPayload(where, set.code()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, hiddenBelow());
        return true;
    }

    /** Which collection this is about, so a refresh can be told apart from another block's. */
    BlockPos where() {
        return where;
    }
}
