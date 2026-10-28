package dev.gathering.client;

import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.PodActionPayload;
import dev.gathering.network.PodLobbyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * A draft or sealed event being signed up for: who is in, whose packs are in, and what is still
 * missing - with the buttons for putting packs in, taking them back, and, for the host,
 * starting or calling it off.
 * <p>Kept current by the server rather than asked for: every change at the table sends every
 * player there a fresh view, so a pack going in across the table shows here without anybody
 * pressing anything. The status line is the same sentence the server would refuse a start
 * with, so a host is never shown Start and then told no.
 * <p>Client-only.
 */
public final class PodLobbyScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int GOOD = 0xFF8FD18F;
    private static final int WARN = 0xFFE0B15A;

    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 18;
    private static final int LINE = 11;
    private static final int GAP = 4;

    private PodLobbyPayload view;
    private Rect panel = Rect.NONE;
    private Button start;
    private String statusSaid = "";

    /** The screen this was opened over, which Done and Escape go back to; null means the world. */
    private final Screen openedFrom;

    private PodLobbyScreen(PodLobbyPayload view, Screen openedFrom) {
        super(Component.translatable("screen.gathering.pod.lobby." + view.settings().kind().key()));
        this.view = view;
        this.openedFrom = openedFrom;
    }

    /**
     * Back to whatever opened it. A tournament opens this over the host's own event screen when its
     * packs go out, and leaving it used to put the host in the room with their tournament behind them.
     */
    @Override
    public void onClose() {
        if (openedFrom != null) {
            this.minecraft.setScreen(openedFrom);
            return;
        }
        super.onClose();
    }

    /** What the server sent: open the screen, bring one already open up to date, or close it. */
    public static void accept(PodLobbyPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof PodLobbyScreen open && open.view.table().equals(payload.table())) {
            if (!payload.open()) {
                open.onClose();
                return;
            }
            open.view = payload;
            open.rebuildWidgets();
            return;
        }
        if (payload.open() && payload.show()) {
            Screen from = client.screen instanceof PodLobbyScreen was ? was.openedFrom : client.screen;
            client.setScreen(new PodLobbyScreen(payload, from));
        }
    }

    BlockPos table() {
        return view.table();
    }

    /** What the status line said last frame. For the scripted harness. */
    String statusSaid() {
        return statusSaid;
    }

    /** The view this screen is showing. For the scripted harness. */
    PodLobbyPayload view() {
        return view;
    }

    @Override
    protected void init() {
        int height = MARGIN * 2 + ROW_HEIGHT + LINE * 2 + GAP
                + Math.max(1, (view.players().size() + (view.players().size() > 4 ? 1 : 0)) / (view.players().size() > 4 ? 2 : 1)) * LINE + GAP * 2
                + LINE + GAP * 2 + (ROW_HEIGHT + GAP) * 3;
        panel = new Rect((this.width - PANEL_WIDTH) / 2, Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH, Math.min(height, this.height - MARGIN * 2));
        int half = (panel.width() - MARGIN * 2 - GAP) / 2;
        int bottom = panel.bottom() - MARGIN - ROW_HEIGHT;
        boolean bringsPacks = view.settings().source() != PodSettings.Source.GENERATED;

        int hostRow = bottom - ROW_HEIGHT - GAP;
        int packRow = hostRow - ROW_HEIGHT - GAP;
        if (bringsPacks) {
            addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, packRow, half, ROW_HEIGHT,
                    Component.translatable("screen.gathering.pod.put_in"), () -> send(PodActionPayload.Action.PUT_IN)));
            addRenderableWidget(GatheringButtons.of(panel.right() - MARGIN - half, packRow, half, ROW_HEIGHT,
                    Component.translatable("screen.gathering.pod.withdraw"), () -> send(PodActionPayload.Action.WITHDRAW)));
        }
        if (view.youHost()) {
            addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, hostRow, half, ROW_HEIGHT,
                    Component.translatable("screen.gathering.pod.cancel"), () -> send(PodActionPayload.Action.CANCEL)));
            start = GatheringButtons.of(panel.right() - MARGIN - half, hostRow, half, ROW_HEIGHT,
                    Component.translatable("screen.gathering.pod.start"), () -> send(PodActionPayload.Action.START));
            start.active = ready() && !view.opening();
            addRenderableWidget(start);
        }
        addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, bottom, panel.width() - MARGIN * 2, ROW_HEIGHT,
                Component.translatable("gui.done"), this::onClose));
    }

    private boolean ready() {
        return "message.gathering.pod.ready".equals(view.status());
    }

    private void send(PodActionPayload.Action action) {
        ClientNetworking.send(new PodActionPayload(view.table(), action));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int width = panel.width() - MARGIN * 2;
        int x = panel.x() + MARGIN;
        int y = panel.y() + 4;
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, y, width, LABEL);
        y += ROW_HEIGHT;

        PodSettings settings = view.settings();
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.pod.hosted_by", view.host()),
                x, y, width, DIM);
        y += LINE;
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.pod.summary",
                        settings.packsEach(), setsLine(settings),
                        Component.translatable("screen.gathering.pod.cards_line." + settings.cardsGo().key())),
                x, y, width, DIM);
        y += LINE + GAP;

        if (view.players().isEmpty()) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.pod.nobody_seated"), x, y, width, DIM);
            y += LINE;
        }
        // Two columns past four players, so a full table of eight still fits a small window.
        int columns = view.players().size() > 4 ? 2 : 1;
        int column = width / columns;
        int rowsTop = y;
        for (int index = 0; index < view.players().size(); index++) {
            PodLobbyPayload.Player player = view.players().get(index);
            int px = x + (index % columns) * column;
            int py = rowsTop + (index / columns) * LINE;
            GuiText.draw(graphics, this.font, Component.literal(player.name()), px, py, column * 3 / 5 - GAP, LABEL);
            Component packs = settings.source() == PodSettings.Source.EACH_BRINGS
                    ? Component.translatable("screen.gathering.pod.player_packs", player.in(), player.in() + player.owed())
                    : Component.translatable("screen.gathering.pod.player_in");
            GuiText.draw(graphics, this.font, packs, px + column * 3 / 5, py, column * 2 / 5 - GAP,
                    player.owed() == 0 ? GOOD : WARN);
        }
        y = rowsTop + ((view.players().size() + columns - 1) / columns) * LINE;
        y += GAP;

        Component status = view.opening()
                ? Component.translatable("message.gathering.pod.opening")
                : Component.translatable(view.status());
        GuiText.drawCentered(graphics, this.font, status, panel.x() + panel.width() / 2, y, width,
                ready() || view.opening() ? GOOD : WARN);
        statusSaid = status.getString();
    }

    private static Component setsLine(PodSettings settings) {
        return switch (settings.sets().mode()) {
            case ANY -> Component.translatable("screen.gathering.pod.sets.any");
            case ONE_SET -> Component.literal(settings.sets().sets().get(0).toUpperCase(java.util.Locale.ROOT));
            case PER_PACK -> Component.literal(String.join(" / ", settings.sets().sets()).toUpperCase(java.util.Locale.ROOT));
        };
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
