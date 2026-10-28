package dev.gathering.client;

import dev.gathering.core.ui.ListScroll;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.CollectionKeyPayload;
import dev.gathering.network.CollectionKeysAskPayload;
import dev.gathering.network.CollectionKeysPayload;
import dev.gathering.network.CollectionLockPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Who may use this collection: the lock, and the list of people let in.
 * <p>The owner's screen and nobody else's - the server answers the question to nobody else, so a client
 * that opened this by other means would be a client looking at an empty list.
 * <p>Three rights, one row each. <b>Look</b> is what a locked collection withholds; <b>take</b> and
 * <b>add</b> are what an open one still withholds. Every one of them off is the same as never having
 * been let in, which is what the row's cross does, because "remove" and "allow nothing" being different
 * things is how a list of people grows entries that mean nothing.
 * <p>Nothing here decides anything: each press sends what was pressed, and the list is redrawn from what
 * the server sends back. A screen that moved a row before the server agreed is a screen that can be
 * wrong about who has a key.
 * <p>Client-only.
 */
public final class CollectionKeysScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 10;
    private static final int GAP = 4;
    private static final int ROW = 20;
    private static final int PANEL_WIDTH = 320;

    /** How wide each of Look, Take and Add would like to be, and the least they will fit in. */
    private static final int RIGHT_BUTTON = 46;
    private static final int NARROWEST_RIGHT = 26;

    /** The cross that shuts somebody out, and the least room a name gets beside the three rights. */
    private static final int CROSS = 20;
    private static final int NAME_ROOM = 40;
    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;

    private final BlockPos where;
    private CollectionKeysPayload.Key everyone = new CollectionKeysPayload.Key("", true, false, false);
    private final List<CollectionKeysPayload.Key> keys = new ArrayList<>();
    private int scroll;
    private Rect panel = Rect.NONE;
    private Rect track = Rect.NONE;
    private EditBox nameBox;

    /**
     * How wide the three rights are drawn at this window size.
     * <p>They are anchored to the right of the row, so at their full width in a narrow window the
     * leftmost one hangs off the left edge of the panel and over the name it belongs to. Squeezed to fit
     * instead, down to a floor - below which a row is not a row anybody can read anyway.
     */
    private int rightButton = RIGHT_BUTTON;

    private CollectionKeysScreen(BlockPos where) {
        super(Component.translatable("screen.gathering.collection_keys"));
        this.where = where;
    }

    /** Opens it and asks the server who is let in; the list arrives a moment later. */
    public static void show(BlockPos where) {
        Minecraft.getInstance().setScreen(new CollectionKeysScreen(where));
        ClientNetworking.send(new CollectionKeysAskPayload(where));
    }

    /** The server's answer, which is the only thing that ever fills this list. */
    public static void accept(CollectionKeysPayload keys) {
        if (Minecraft.getInstance().screen instanceof CollectionKeysScreen screen
                && screen.where.equals(keys.where())) {
            screen.everyone = keys.everyone();
            screen.keys.clear();
            screen.keys.addAll(keys.keys());
            screen.scroll = ListScroll.within(screen.scroll, screen.keys.size(), screen.rowsThatFit());
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int width = Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
        int showing = rowsThatFit();
        // Clamped here, not only where a new list arrives. How many rows fit is worked out from the
        // window, so growing it - or dropping the GUI scale - raises the count while the scroll
        // stays where the player left it, and the row loops below would read past the end of the
        // list. That threw out of Screen#init, which nothing catches.
        scroll = ListScroll.within(scroll, keys.size(), showing);
        int listed = Math.max(1, showing);
        int height = PADDING * 2 + this.font.lineHeight + GAP + ROW + GAP
                + ROW + GAP + listed * (ROW + GAP) + GAP + ROW;
        panel = new Rect((this.width - width) / 2, Math.max(4, (this.height - height) / 2), width,
                Math.min(height, this.height - 8));
        int inner = panel.width() - PADDING * 2;
        int left = panel.x() + PADDING;
        rightButton = Math.max(NARROWEST_RIGHT,
                Math.min(RIGHT_BUTTON, (inner - CROSS - NAME_ROOM - GAP * 4) / 3));

        // What anybody at all may do, as the same three switches a named player gets - so the row reads
        // the same way and "anyone may look, only I may take" is a thing you set rather than hope for.
        int lockTop = panel.y() + PADDING + this.font.lineHeight + GAP;
        int end = panel.right() - PADDING;
        addRenderableWidget(right(end - rightButton, lockTop,
                "screen.gathering.collection_keys.add", everyone.add(),
                () -> everyone(everyone.look(), everyone.take(), !everyone.add())));
        addRenderableWidget(right(end - (GAP + rightButton) * 2 + GAP, lockTop,
                "screen.gathering.collection_keys.take", everyone.take(),
                () -> everyone(everyone.look(), !everyone.take(), everyone.add())));
        addRenderableWidget(right(end - (GAP + rightButton) * 3 + GAP, lockTop,
                "screen.gathering.collection_keys.look", everyone.look(),
                () -> everyone(!everyone.look(), everyone.take(), everyone.add())));

        // A name, and the button that lets them look.
        int addTop = lockTop + ROW + GAP;
        int letInWidth = rightButton + 20;
        nameBox = new EditBox(this.font, left + 1, addTop + 1, inner - letInWidth - GAP - 2, ROW - 2,
                Component.translatable("screen.gathering.collection_keys.name"));
        nameBox.setMaxLength(CollectionKeyPayload.MOST_NAME_CHARACTERS);
        nameBox.setHint(Component.translatable("screen.gathering.collection_keys.name_hint"));
        addRenderableWidget(nameBox);
        addRenderableWidget(GatheringButtons.of(panel.right() - PADDING - letInWidth, addTop, letInWidth, ROW,
                Component.translatable("screen.gathering.collection_keys.let_in"), this::letIn));

        int listTop = addTop + ROW + GAP;
        boolean scrolls = ListScroll.scrolls(keys.size(), showing);
        int scrollbar = scrolls
                ? dev.gathering.core.ui.ListScreenLayout.SCROLLBAR + dev.gathering.core.ui.ListScreenLayout.SCROLLBAR_GAP
                : 0;
        for (int index = 0; index < Math.min(showing, keys.size() - scroll); index++) {
            CollectionKeysPayload.Key key = keys.get(scroll + index);
            int top = listTop + index * (ROW + GAP);
            int rowEnd = panel.right() - PADDING - scrollbar;
            addRenderableWidget(GatheringButtons.of(rowEnd - CROSS, top, CROSS, ROW,
                    Component.translatable("screen.gathering.collection_keys.shut_out"),
                    () -> send(key.name(), false, false, false)));
            addRenderableWidget(right(rowEnd - CROSS - GAP - rightButton, top,
                    "screen.gathering.collection_keys.add", key.add(),
                    () -> send(key.name(), key.look(), key.take(), !key.add())));
            addRenderableWidget(right(rowEnd - CROSS - (GAP + rightButton) * 2, top,
                    "screen.gathering.collection_keys.take", key.take(),
                    () -> send(key.name(), key.look(), !key.take(), key.add())));
            addRenderableWidget(right(rowEnd - CROSS - (GAP + rightButton) * 3, top,
                    "screen.gathering.collection_keys.look", key.look(),
                    () -> send(key.name(), !key.look(), key.take(), key.add())));
        }
        track = scrolls
                ? new Rect(panel.right() - PADDING - dev.gathering.core.ui.ListScreenLayout.SCROLLBAR, listTop,
                        dev.gathering.core.ui.ListScreenLayout.SCROLLBAR, showing * (ROW + GAP) - GAP)
                : Rect.NONE;

        // Handing it over, which is the only way a collection ever changes hands - and the only way one
        // whose owner has stopped playing is ever opened again.
        int decideTop = panel.bottom() - PADDING - ROW;
        int handOverWide = Math.min(inner / 2 - GAP, 96);
        addRenderableWidget(GatheringButtons.of(left, decideTop, handOverWide, ROW,
                Component.translatable("screen.gathering.collection_keys.hand_over"), this::handOver));
        addRenderableWidget(GatheringButtons.of(left + handOverWide + GAP, decideTop,
                inner - handOverWide - GAP, ROW, Component.translatable("gui.done"), this::onClose));
    }

    /** One of a row's three rights, lit when it is allowed. */
    private net.minecraft.client.gui.components.Button right(
            int x, int y, String label, boolean allowed, Runnable action) {
        return GatheringButtons.toggle(x, y, rightButton, ROW,
                Component.translatable(label), () -> allowed, action);
    }

    private void everyone(boolean look, boolean take, boolean add) {
        ClientNetworking.send(new CollectionLockPayload(where, look, take, add));
    }

    private void letIn() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        // Looking, which is the smallest thing being let in can mean. Taking and adding are the row's.
        send(name, true, false, false);
        nameBox.setValue("");
    }

    /** Hands it to whoever is named in the box. Nothing at all with the box empty. */
    private void handOver() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        ClientNetworking.send(new dev.gathering.network.CollectionOwnerPayload(where, name));
        nameBox.setValue("");
    }

    private void send(String name, boolean look, boolean take, boolean add) {
        ClientNetworking.send(new CollectionKeyPayload(where, name, look, take, add));
    }

    /** How many rows the window has room for. */
    private int rowsThatFit() {
        int room = this.height - 8 - PADDING * 2 - this.font.lineHeight - GAP * 4 - ROW * 3;
        return Math.max(0, Math.min(keys.size(), room / (ROW + GAP)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int showing = rowsThatFit();
        if (ListScroll.scrolls(keys.size(), showing)) {
            int wanted = ListScroll.after(scroll, keys.size(), showing, scrollY);
            if (wanted != scroll) {
                scroll = wanted;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Prompts.panel(graphics, panel);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int inner = panel.width() - PADDING * 2;
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + PADDING,
                inner, TEXT);
        int showing = rowsThatFit();
        int lockTop = panel.y() + PADDING + this.font.lineHeight + GAP;
        int listTop = lockTop + (ROW + GAP) * 2;
        // The row's own name, in the place a player's name sits on every row below it.
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.collection_keys.everyone"),
                panel.x() + PADDING, lockTop + (ROW - this.font.lineHeight) / 2,
                panel.width() - PADDING * 2 - (GAP + rightButton) * 3, TEXT);
        if (keys.isEmpty()) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.collection_keys.nobody"),
                    panel.x() + panel.width() / 2, listTop + (ROW - this.font.lineHeight) / 2, inner, DIM);
        }
        int scrollbar = track == Rect.NONE ? 0
                : dev.gathering.core.ui.ListScreenLayout.SCROLLBAR + dev.gathering.core.ui.ListScreenLayout.SCROLLBAR_GAP;
        int names = panel.width() - PADDING * 2 - scrollbar - CROSS - (GAP + rightButton) * 3 - GAP;
        for (int index = 0; index < Math.min(showing, keys.size() - scroll); index++) {
            CollectionKeysPayload.Key key = keys.get(scroll + index);
            GuiText.draw(graphics, this.font, Component.literal(key.name()), panel.x() + PADDING,
                    listTop + index * (ROW + GAP) + (ROW - this.font.lineHeight) / 2, names, TEXT);
        }
        if (track != Rect.NONE) {
            ListScrollbar.draw(graphics, track, scroll, showing, keys.size());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The rows as the owner sees them, for the scripted client. */
    List<CollectionKeysPayload.Key> rows() {
        return List.copyOf(keys);
    }

    boolean openToAll() {
        return everyone.look();
    }
}
