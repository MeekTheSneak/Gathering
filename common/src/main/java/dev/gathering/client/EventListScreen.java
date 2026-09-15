package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.network.EventActionPayload;
import dev.gathering.network.EventListPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The tournaments on this server: running ones first, then the most recently finished, each a row
 * to open. Hosting a new one is offered when the list was opened from a table, which is where an
 * event has to be created.
 * <p>Client-only.
 */
public final class EventListScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int GOOD = 0xFF8FD18F;
    private static final int PANEL_WIDTH = 380;
    private static final int MARGIN = 10;
    private static final int ROW = 22;
    private static final int PER_PAGE = 6;

    /** The table the list was opened from, for hosting; null when opened by command. */
    private static BlockPos lastTable;

    private List<EventListPayload.Summary> events;
    private Rect panel = Rect.NONE;
    private int page;

    private EventListScreen(List<EventListPayload.Summary> events) {
        super(Component.translatable("screen.gathering.events"));
        this.events = events;
    }

    /** Asks the server for the list, remembering this table for hosting. */
    public static void openFrom(BlockPos table) {
        lastTable = table;
        ClientNetworking.send(EventActionPayload.of(EventActionPayload.NONE, EventActionPayload.Action.LIST));
    }

    public static void accept(EventListPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof EventListScreen open) {
            open.events = payload.events();
            open.rebuildWidgets();
        } else if (payload.show()) {
            client.setScreen(new EventListScreen(payload.events()));
        }
    }

    /** Forgets the table, for a disconnect. */
    public static void clear() {
        lastTable = null;
    }

    List<EventListPayload.Summary> events() {
        return events;
    }

    @Override
    protected void init() {
        int height = MARGIN * 2 + 16 + PER_PAGE * ROW + 24 + 22;
        int width = Math.min(PANEL_WIDTH, this.width - 20);
        panel = new Rect((this.width - width) / 2, Math.max(4, (this.height - height) / 2), width, Math.min(height, this.height - 8));
        int y = panel.y() + MARGIN + 16;
        int from = page * PER_PAGE;
        for (int index = from; index < Math.min(events.size(), from + PER_PAGE); index++) {
            EventListPayload.Summary event = events.get(index);
            addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, y, panel.width() - MARGIN * 2, ROW - 3,
                    Component.translatable("screen.gathering.events.row", event.name(),
                            Component.translatable("screen.gathering.event.kind." + event.kind()), event.format(),
                            Component.translatable("screen.gathering.event.phase." + event.phase()), event.players()),
                    () -> ClientNetworking.send(EventActionPayload.of(event.id(), EventActionPayload.Action.VIEW))));
            y += ROW;
        }
        int bottom = panel.bottom() - MARGIN - 18;
        int quarter = (panel.width() - MARGIN * 2 - 12) / 4;
        var back = GatheringButtons.of(panel.x() + MARGIN, bottom, quarter, 18, Component.literal("<"), () -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        });
        var forward = GatheringButtons.of(panel.x() + MARGIN + quarter + 4, bottom, quarter, 18, Component.literal(">"), () -> {
            if ((page + 1) * PER_PAGE < events.size()) {
                page++;
                rebuildWidgets();
            }
        });
        // Grayed where there is no page to go to, rather than a press that does nothing.
        back.active = page > 0;
        forward.active = (page + 1) * PER_PAGE < events.size();
        addRenderableWidget(back);
        addRenderableWidget(forward);
        var host = GatheringButtons.of(panel.x() + MARGIN + (quarter + 4) * 2, bottom, quarter, 18,
                Component.translatable("screen.gathering.events.host"),
                () -> this.minecraft.setScreen(new EventCreateScreen(lastTable)));
        host.active = lastTable != null;
        if (lastTable == null) {
            host.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("screen.gathering.events.host_at_a_table")));
        }
        addRenderableWidget(host);
        addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN + (quarter + 4) * 3, bottom, quarter, 18,
                Component.translatable("gui.done"), this::onClose));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + 4,
                panel.width() - MARGIN * 2, LABEL);
        if (events.isEmpty()) {
            GuiText.drawCentered(graphics, this.font, Component.translatable("screen.gathering.events.none"),
                    panel.x() + panel.width() / 2, panel.y() + MARGIN + 30, panel.width() - MARGIN * 2, DIM);
        }
        GuiText.drawCentered(graphics, this.font, Component.translatable("screen.gathering.events.page", page + 1,
                        Math.max(1, (events.size() + PER_PAGE - 1) / PER_PAGE)),
                panel.x() + panel.width() / 2, panel.bottom() - MARGIN - 30, 100, GOOD);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
