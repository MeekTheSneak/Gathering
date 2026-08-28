package dev.gathering.client;

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
    private static final int ROW_HEIGHT = 22;

    /** Over the collection, because this screen is the size of the one it covers. */
    private static final int BACKDROP = 0xC0000000;

    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int WARNING = 0xFFFFD98A;

    /** The bar: what is there, what is not, and the one drawn when a set is finished. */
    private static final int BAR_EMPTY = 0x66000000;
    private static final int BAR_FULL = 0xFF4E9A6A;
    private static final int BAR_DONE = 0xFFD9A441;

    private final BlockPos where;
    private final CollectionScreen collection;
    private final List<SetCompletion> sets;
    private final int stillLooking;

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
     * Opens it, or refreshes it if it is already up.
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
        CollectionScreen collection = client.screen instanceof SetProgressScreen open
                ? open.collection
                : client.screen instanceof CollectionScreen showing ? showing : null;
        if (collection == null) {
            return;
        }
        client.setScreen(new SetProgressScreen(
                collection, payload.collection(), List.copyOf(rows), payload.stillLooking()));
    }

    @Override
    protected void init() {
        addRenderableWidget(GatheringButtons.of(
                this.width - MARGIN - 56, this.height - BOTTOM_BAR + 6, 56, 18,
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
        graphics.fill(0, 0, this.width, this.height, BACKDROP);
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
        if (hiddenBelow() > 0) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.sets.more", hiddenBelow()),
                    this.width / 2, this.height - BOTTOM_BAR + 10, this.width / 2, DIM);
        }
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
                row.x(), row.y() + 1, nameRoom, set.isComplete() ? BAR_DONE : TEXT);

        Component count = set.extras() > 0
                ? Component.translatable("screen.gathering.sets.count_with_extras",
                        set.owned(), set.size(), set.extras())
                : Component.translatable("screen.gathering.sets.count", set.owned(), set.size());
        GuiText.drawFlushRight(graphics, this.font, count, row.right(), row.y() + 1, 1f,
                set.isComplete() ? BAR_DONE : DIM);

        int barTop = row.y() + this.font.lineHeight + 3;
        int barHigh = 3;
        graphics.fill(row.x(), barTop, row.right(), barTop + barHigh, BAR_EMPTY);
        int full = Math.round(row.width() * set.share());
        if (full > 0) {
            graphics.fill(row.x(), barTop, row.x() + full, barTop + barHigh,
                    set.isComplete() ? BAR_DONE : BAR_FULL);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0 && hovered < sets.size()) {
            GatheringButtons.clickSound();
            // Back to the collection, showing only this set. The whole point of the row.
            SetCompletion set = sets.get(hovered);
            Minecraft.getInstance().setScreen(collection);
            collection.showOnly(set.code());
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
