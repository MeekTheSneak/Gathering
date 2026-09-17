package dev.gathering.client;

import dev.gathering.core.tournament.EventDraft;
import dev.gathering.core.tournament.PrizeOffer;
import dev.gathering.core.ui.Rect;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The prizes for a tournament that does not exist yet: a place, and what the host is holding for it.
 * <p>The same gesture the host's own tab uses once the event is running - hold the prize, choose the
 * place, put it up - so there is one way to put a prize up rather than two. What is written down is
 * the hotbar slot rather than the item, because until Create is pressed there is nothing to hand the
 * item to; each row is drawn from the host's own inventory every frame, so a slot they have since
 * emptied reads as empty here rather than quietly promising something else.
 * <p>Client-only.
 */
public final class EventPrizeScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int WARN = 0xFFE0B15A;
    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;
    private static final int LINE = 11;

    private final Consumer<EventDraft> chosen;
    private EventDraft draft;
    private int place = 1;
    private Rect panel = Rect.NONE;
    private String said = "";

    public EventPrizeScreen(Screen back, EventDraft draft, Consumer<EventDraft> chosen) {
        super(Component.translatable("screen.gathering.event.prizes"), back);
        this.draft = draft;
        this.chosen = chosen;
    }

    /** What the line under the rows said last frame. For the scripted harness. */
    String said() {
        return said;
    }

    EventDraft draft() {
        return draft;
    }

    private void choose(EventDraft wanted) {
        draft = wanted;
        chosen.accept(wanted);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int rows = Math.max(1, draft.prizes().size());
        int height = MARGIN + ROW + GAP + rows * (ROW + GAP) + GAP + ROW + GAP + LINE + GAP + ROW + MARGIN;
        int width = Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
        panel = new Rect((this.width - width) / 2, Math.max(4, (this.height - height) / 2), width,
                Math.min(height, this.height - 8));
        int x = panel.x() + MARGIN;
        int inner = panel.width() - MARGIN * 2;
        int y = panel.y() + MARGIN + ROW;

        int takeDown = Math.min(inner / 3, this.font.width(Component.translatable("screen.gathering.event.take_down")) + 12);
        for (int index = 0; index < draft.prizes().size(); index++) {
            int which = index;
            addRenderableWidget(GatheringButtons.of(panel.right() - MARGIN - takeDown, y, takeDown, ROW,
                    Component.translatable("screen.gathering.event.take_down"),
                    () -> choose(draft.withoutPrize(which))));
            y += ROW + GAP;
        }
        if (draft.prizes().isEmpty()) {
            y += ROW + GAP;
        }
        y += GAP;

        // The place, with a step either side, and Put up beside it: the whole row is one gesture.
        int step = 20;
        addRenderableWidget(GatheringButtons.of(x, y, step, ROW, Component.literal("-"), () -> {
            place = Math.max(1, place - 1);
            rebuildWidgets();
        }));
        addRenderableWidget(GatheringButtons.of(x + step + GAP + 56 + GAP, y, step, ROW, Component.literal("+"), () -> {
            place = Math.min(PrizeOffer.LOWEST_PLACE, place + 1);
            rebuildWidgets();
        }));
        placeAt = new int[] {x + step + GAP + 28, y + (ROW - this.font.lineHeight) / 2 + 1};
        int putUp = panel.right() - MARGIN - (x + step * 2 + GAP * 3 + 56 + GAP);
        var press = GatheringButtons.of(x + step * 2 + GAP * 3 + 56, y, putUp, ROW,
                Component.translatable("screen.gathering.event.put_up"), this::putUp);
        press.active = !held().isEmpty() && draft.prizes().size() < PrizeOffer.HOTBAR_SLOTS;
        addRenderableWidget(press);
        y += ROW + GAP + LINE + GAP;

        addRenderableWidget(GatheringButtons.of(x, y, inner, ROW, Component.translatable("gui.done"), this::onClose));
    }

    private int[] placeAt = {0, 0};

    /** What the host is holding: the prize this would put up. */
    private ItemStack held() {
        var player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getInventory().getSelected();
    }

    private void putUp() {
        var player = Minecraft.getInstance().player;
        if (player == null || held().isEmpty()) {
            return;
        }
        choose(draft.withPrize(new PrizeOffer(place, player.getInventory().selected)));
    }

    /** What is in the slot a prize was promised from, right now. */
    private ItemStack inSlot(PrizeOffer offer) {
        var player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getInventory().getItem(offer.slot());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = panel.x() + MARGIN;
        int inner = panel.width() - MARGIN * 2;
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + 4, inner, LABEL);
        int y = panel.y() + MARGIN + ROW + (ROW - this.font.lineHeight) / 2 + 1;
        int names = inner - inner / 3 - GAP;
        for (PrizeOffer offer : draft.prizes()) {
            ItemStack stack = inSlot(offer);
            Component row = stack.isEmpty()
                    ? Component.translatable("screen.gathering.event.prize_empty", offer.place())
                    : Component.translatable("screen.gathering.event.prize_row", offer.place(), stack.getCount(),
                            stack.getHoverName());
            GuiText.draw(graphics, this.font, row, x, y, names, stack.isEmpty() ? WARN : LABEL);
            y += ROW + GAP;
        }
        if (draft.prizes().isEmpty()) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.prizes_none"), x, y, names, DIM);
        }
        GuiText.drawCentered(graphics, this.font, Component.translatable("screen.gathering.event.prize_for", place),
                placeAt[0], placeAt[1], 56, LABEL);
        ItemStack holding = held();
        Component line = holding.isEmpty()
                ? Component.translatable("message.gathering.event.hold_a_prize")
                : Component.translatable("screen.gathering.event.prize_held", holding.getCount(), holding.getHoverName());
        GuiText.drawCentered(graphics, this.font, line, panel.x() + panel.width() / 2,
                panel.bottom() - MARGIN - ROW - GAP - LINE, inner, holding.isEmpty() ? WARN : DIM);
        said = line.getString();
    }
}
