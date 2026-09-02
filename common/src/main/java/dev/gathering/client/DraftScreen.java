package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.DraftView;
import dev.gathering.core.draft.DraftViewCodec;
import dev.gathering.core.draft.DrafterId;
import dev.gathering.core.ui.PackLayout;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import dev.gathering.network.DraftPickPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The pack in front of you, and the cards you have taken out of it.
 * <p>A draft is one decision made forty-five times, so the screen is the decision and almost
 * nothing else: the pack laid out face up, click what you want, take it. What everybody else
 * is doing is a line at the bottom rather than a panel, because the answer is always either
 * "still picking" or "waiting for you" and neither deserves a box.
 * <p>Nothing here knows what is in anybody else's pack, because nothing here was sent it.
 * <p>Client-only.
 */
public final class DraftScreen extends ChildScreen implements CardPreviewHost {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int ACCENT = 0xFF6FD3E8;

    private static final int MARGIN = 14;
    private static final int GAP = 4;
    private static final int HEADER = 16;
    private static final int FOOTER = 26;

    /**
     * The largest a card is drawn, so a four-card pack is not blown up to fill the window.
     * <p>Only a ceiling. A full pack in a small window comes out smaller than this, because
     * every card being on screen matters more than any of them being large - and Alt reads
     * whichever one the cursor is on, at any size.
     */
    private static final int CARD_HEIGHT = 84;

    /** How wide the two buttons under the cards are. */
    private static final int TAKE_BUTTON = 70;
    private static final int POOL_BUTTON = 78;

    private final BlockPos pod;

    private DraftView view;

    /** Places in the pack this drafter has clicked, in the order they clicked them. */
    private final List<Integer> chosen = new ArrayList<>();

    /**
     * Whether the grid is showing what has been picked rather than what is on offer.
     * <p>The half of drafting the screen was missing. In paper you read your picks constantly
     * - the question a pack asks is "what am I building", and it cannot be answered by a
     * number. The cards were already on this client, sent with the pack; there was simply no
     * way to look at them.
     */
    private boolean showingPool;

    private Rect panel = Rect.NONE;
    private Rect grid = Rect.NONE;
    private Rect footerRow = Rect.NONE;

    /** How the pack was laid out to fit the window it is being drawn in. */
    private PackLayout laid = PackLayout.fit(1, 100, 100, GAP, CARD_HEIGHT);

    /** What the footer last actually wrote, for the scripted harness. */
    private String footerSaid = "";

    /**
     * Shows the pack that just arrived, opening the screen if it is not already up.
     * <p>Opening and refreshing are the same call, because from here they are the same event:
     * a pack arrived. Which of the two it is was decided by the server, which knows whether
     * this was somebody sitting down to draft or a neighbor picking.
     *
     * @param open false for an update, so a drafter who closed the screen to look something
     *             up is not dragged back to it every time anybody else picks
     */
    public static void show(BlockPos pod, byte[] written, boolean open) {
        DraftView view;
        try {
            view = DraftViewCodec.read(written);
        } catch (java.io.IOException unreadable) {
            // A pack this client cannot read is a pack it must not guess at. Saying so in the
            // log beats an empty screen that looks like a draft with nothing in it.
            org.slf4j.LoggerFactory.getLogger("Gathering")
                    .error("A draft pack will not open: {}", unreadable.getMessage());
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof DraftScreen already && already.pod().equals(pod)) {
            already.update(view);
            return;
        }
        if (!open) {
            return;
        }
        client.setScreen(new DraftScreen(pod, view, client.screen));
    }

    private DraftScreen(BlockPos pod, DraftView view, Screen back) {
        super(Component.translatable("screen.gathering.draft.title"), back);
        this.pod = pod;
        this.view = view;
    }

    /**
     * The pod moved on: a new pack, or a neighbor picking.
     * <p>The chosen cards are dropped whenever the pack itself changes, because a place in
     * one pack means a different card in the next - keeping them would turn "I had picked
     * that" into taking something nobody looked at.
     */
    public void update(DraftView updated) {
        boolean sameCards = view != null && view.myPack().equals(updated.myPack());
        this.view = updated;
        if (!sameCards) {
            chosen.clear();
            // And back to the pack, because a pack is the one with a decision attached.
            // Left on the pool, a drafter reading their picks when the packs moved would be
            // looking at a screen with no cards to take and nothing saying one had arrived.
            showingPool = false;
        }
        rebuild();
    }

    public BlockPos pod() {
        return pod;
    }

    @Override
    protected void init() {
        rebuild();
    }

    /** The cards the grid is laying out: the pack on offer, or the pool already taken. */
    private List<CardIdentity> onShow() {
        return showingPool ? view.myPool() : view.myPack().cards();
    }

    private void rebuild() {
        clearWidgets();
        int cards = Math.max(1, onShow().size());

        // Sized to fit rather than wrapped at a fixed card size. A pack is a comparison -
        // you are looking at all of it at once - so it must all be on screen, and a row of it
        // drawn under the edge of the panel is not a cramped pack but five cards nobody can
        // click. The cards shrink instead, down to whatever the window leaves.
        laid = PackLayout.fit(
                cards,
                this.width - MARGIN * 4,
                this.height - MARGIN * 4 - HEADER - FOOTER,
                GAP,
                CARD_HEIGHT);

        int gridWidth = laid.width(GAP);
        int gridHeight = laid.height(GAP);
        // As wide as the widest thing in it, not as wide as the cards. An empty pool lays out
        // as one card, and a panel built to that came out narrower than its own sentence and
        // its own buttons - which then drew over the edges of it and over whatever was behind.
        int wordsWidth = Math.max(
                this.font.width(headline()),
                this.font.width(footer()) + buttonsRoom());
        int width = Math.min(this.width - MARGIN * 2,
                Math.max(gridWidth, wordsWidth) + MARGIN * 2);
        int height = Math.min(this.height - MARGIN * 2,
                gridHeight + MARGIN * 2 + HEADER + FOOTER);
        panel = new Rect((this.width - width) / 2, (this.height - height) / 2, width, height);
        grid = new Rect(
                panel.x() + (panel.width() - gridWidth) / 2,
                panel.y() + MARGIN / 2 + HEADER,
                gridWidth,
                gridHeight);

        // Off the grid rather than off the panel, so the row under the cards cannot end up
        // drawn over the bottom row of them however the pack came out.
        footerRow = new Rect(panel.x() + MARGIN / 2, grid.bottom() + GAP * 2,
                panel.width() - MARGIN, 14);
        int right = footerRow.right();
        if (canPick() && !showingPool) {
            addRenderableWidget(GatheringButtons.of(
                    right - TAKE_BUTTON, footerRow.y(), TAKE_BUTTON, footerRow.height(),
                    Component.translatable("screen.gathering.draft.take"), this::take));
            right -= TAKE_BUTTON + 4;
        }
        // Always offered, even with nothing in the pool yet, because a button that appears
        // after the first pick is a button nobody knows was coming.
        addRenderableWidget(GatheringButtons.of(
                right - POOL_BUTTON, footerRow.y(), POOL_BUTTON, footerRow.height(),
                showingPool
                        ? Component.translatable("screen.gathering.draft.show_pack")
                        : Component.translatable("screen.gathering.draft.show_pool",
                                view.myPool().size()),
                this::togglePool));
    }

    /**
     * How much of the footer row the buttons take, leaving the rest for the sentence.
     * <p>Asked before they are made, because the panel has to be built wide enough to hold
     * them and they are placed from the panel. The buttons only - what the words need is
     * added by the one caller that cares, rather than folded in here and subtracted again by
     * the other, which is arithmetic that reads as a mistake even when it is not.
     */
    private int buttonsRoom() {
        return POOL_BUTTON + 4 + (canPick() && !showingPool ? TAKE_BUTTON + 4 : 0);
    }

    /**
     * Swaps the grid between the pack and the pool.
     * <p>The same grid rather than a second panel. A pack and a pool are the same thing to
     * look at - a spread of cards you are reading - and two boxes would mean deciding which
     * one is small, which is deciding which of them does not matter.
     */
    private void togglePool() {
        showingPool = !showingPool;
        GatheringButtons.clickSound();
        rebuild();
    }

    /** Whether this drafter still has a decision to make in front of them. */
    private boolean canPick() {
        return view != null && !view.finished() && !view.iHaveDeclared()
                && view.picksDueFromMe() > 0;
    }

    private void take() {
        if (!canPick() || chosen.size() != view.picksDueFromMe()) {
            return;
        }
        ClientNetworking.send(new DraftPickPayload(pod, List.copyOf(chosen)));
        GatheringButtons.clickSound();
        // Not cleared here. The server decides whether the pick stood, and the view that
        // comes back is what says so - clearing now would show an empty selection over a
        // pack that has not moved, which reads as the click having been lost.
    }

    /**
     * Behind the widgets, not over them.
     * <p>Drawn in {@code render} - which runs after {@code super.render} has already drawn
     * every button - the panel painted over its own buttons, so what was left was the label
     * with no frame under it and no sign it could be pressed. {@code CountersScreen} hit this
     * and fixed it the same way; these two kept the bug.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        GuiText.draw(graphics, this.font, headline(),
                panel.x() + MARGIN / 2, panel.y() + 5, panel.width() - MARGIN, ACCENT);

        List<CardIdentity> cards = onShow();
        int hovered = cardUnder(mouseX, mouseY);
        for (int index = 0; index < cards.size(); index++) {
            // Nothing is marked as chosen while the pool is up: a place in the pool is not a
            // place in the pack, and lighting one up there would mark whichever card of
            // theirs happened to sit at the same index.
            drawCard(graphics, cards.get(index), slotOf(index),
                    index == hovered, !showingPool && chosen.contains(index));
        }
        // Only while there is another pack coming. A finished pod has the headline saying so
        // and the footer saying where the pool went, and a third sentence in the middle of an
        // empty box saying it again is a box repeating itself.
        if (cards.isEmpty() && (showingPool || !view.finished())) {
            GuiText.drawCentered(graphics, this.font,
                    showingPool
                            ? Component.translatable("screen.gathering.draft.pool_empty")
                            : Component.translatable("screen.gathering.draft.pack_coming"),
                    panel.x() + panel.width() / 2, grid.y() + grid.height() / 2 - 4,
                    panel.width() - MARGIN, DIM);
        }

        Component footer = footer();
        footerSaid = footer.getString();
        GuiText.draw(graphics, this.font, footer,
                footerRow.x(), footerRow.y() + (footerRow.height() - this.font.lineHeight) / 2,
                Math.max(1, footerRow.width() - buttonsRoom()), DIM);

        // What Alt reads, the same way every other card box here does it: the card under
        // the cursor is handed to the zoom overlay rather than drawn large by this screen.
        if (hovered >= 0) {
            ClientHoverState.setHovered(CardItem.of(CardComponent.of(cards.get(hovered))));
        } else {
            ClientHoverState.clear();
        }
    }

    /** What round it is and how many this pod picks at a time. */
    private Component headline() {
        if (view.finished()) {
            return Component.translatable("screen.gathering.draft.done");
        }
        return Component.translatable("screen.gathering.draft.round",
                view.round() + 1, view.rounds(), view.myPool().size());
    }

    /**
     * Who the pod is waiting on, which is the only thing anybody wants to know between packs.
     * <p>By count rather than by name. Naming them would be a list that changes every few
     * seconds and reads as pressure on whoever is last; the count answers the real question,
     * which is whether the hold-up is somebody else or yourself.
     */
    private Component footer() {
        if (showingPool) {
            return Component.translatable("screen.gathering.draft.pool_of", view.myPool().size());
        }
        if (view.finished()) {
            return Component.translatable("screen.gathering.draft.finished");
        }
        if (canPick()) {
            return Component.translatable("screen.gathering.draft.pick_this_many",
                    view.picksDueFromMe(), chosen.size());
        }
        return Component.translatable("screen.gathering.draft.waiting", view.waitingOn().size());
    }

    private void drawCard(
            GuiGraphics graphics, CardIdentity card, Rect where, boolean hovered, boolean taken) {
        Optional<CardSummary> summary = summaryOf(card);
        if (summary.isPresent()) {
            CardInspectPanel.renderArt(
                    graphics, summary.get(), where.x(), where.y(), where.width(), where.height());
        } else {
            // A card whose details have not arrived was an empty recess and nothing else, so
            // a pack that had not caught up was fifteen identical black boxes and a player
            // being asked to pick one. Every other screen in the mod says it is waiting; this
            // one is the screen where being unable to read a card actually costs something.
            GatheringSprites.inset(
                    graphics, where.x(), where.y(), where.width(), where.height());
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.deck.loading_card"),
                    where.x() + where.width() / 2,
                    where.y() + where.height() / 2 - this.font.lineHeight / 2,
                    where.width() - 6, LABEL);
        }
        if (taken) {
            GatheringSprites.draw(graphics, Element.CHOSEN_FILL,
                    where.x(), where.y(), where.width(), where.height());
        }
        if (hovered || taken) {
            GatheringSprites.draw(graphics, taken ? Element.FOCUS_RING : Element.HOVER_RING,
                    where.x(), where.y(), where.width(), where.height());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && canPick() && !showingPool) {
            int index = cardUnder((int) mouseX, (int) mouseY);
            if (index >= 0) {
                choose(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Clicking a card takes it, and clicking it again puts it back.
     * <p>In a pod that picks two, the third click replaces the oldest rather than being
     * ignored: a drafter who has chosen two and sees a third they prefer is changing their
     * mind, and making them un-click something first is a step that exists only because the
     * screen could not be bothered to work out what they meant.
     */
    private void choose(int index) {
        if (chosen.remove(Integer.valueOf(index))) {
            GatheringButtons.clickSound();
            return;
        }
        chosen.add(index);
        while (chosen.size() > view.picksDueFromMe()) {
            chosen.remove(0);
        }
        GatheringButtons.clickSound();
    }

    private int cardUnder(int x, int y) {
        if (!grid.contains(x, y)) {
            return -1;
        }
        List<CardIdentity> cards = onShow();
        for (int index = 0; index < cards.size(); index++) {
            if (slotOf(index).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    private Rect slotOf(int index) {
        return new Rect(
                grid.x() + (index % laid.columns()) * (laid.cardWidth() + GAP),
                grid.y() + (index / laid.columns()) * (laid.cardHeight() + GAP),
                laid.cardWidth(),
                laid.cardHeight());
    }

    private Optional<CardSummary> summaryOf(CardIdentity card) {
        return ClientCardCache.get().summary(CardComponent.of(card));
    }

    // --- hooks for the scripted harness ---

    /** What the footer last actually wrote, rather than what it would write if asked again. */
    String footerSaid() {
        return footerSaid;
    }

    /** Where a card in this pack is, so the harness can click the card and not a pixel. */
    Rect slotOfCard(int index) {
        return index < 0 || index >= onShow().size() ? Rect.NONE : slotOf(index);
    }

    /** Whether the grid is showing the pool rather than the pack, for the scripted harness. */
    boolean isShowingPool() {
        return showingPool;
    }

    /** How many cards are marked to be taken, which is what pressing take would send. */
    int chosenCount() {
        return chosen.size();
    }

    DrafterId me() {
        return view.me();
    }

    DraftView showing() {
        return view;
    }
}
