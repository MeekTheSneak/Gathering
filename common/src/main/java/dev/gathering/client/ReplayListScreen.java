package dev.gathering.client;

import dev.gathering.network.ReplayListPayload;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The games this server still has, newest first.
 * <p>A row is who played and when, because that is how anybody looks for a game they were in:
 * nobody remembers a game by its length. Clicking one starts watching it - there is no second
 * confirming step, since the way out of a replay is the same Escape as everywhere else.
 * <p>Client-only.
 */
public final class ReplayListScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 10;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_WIDTH = 300;

    private static final int DIM = 0xFF9A9690;

    /** Local time, to the minute. A replay is looked for by "before dinner", not by seconds. */
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM HH:mm");

    private final List<ReplayListPayload.Game> games;

    private int scroll;

    private ReplayListScreen(List<ReplayListPayload.Game> games) {
        super(Component.translatable("screen.gathering.replays"));
        this.games = List.copyOf(games);
    }

    public static void accept(ReplayListPayload listed) {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ReplayListScreen(listed.games()));
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - MARGIN * 2);
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    private int listTop() {
        return panelTop() + PADDING + this.font.lineHeight * 2 + GAP + 2;
    }

    private int panelHeight() {
        int wanted = PADDING * 2 + this.font.lineHeight * 2 + GAP + 2
                + Math.max(1, rowsThatFit()) * (ROW_HEIGHT + GAP) + GAP * 3 + ROW_HEIGHT;
        return Math.min(wanted, this.height - MARGIN * 2);
    }

    private int rowsThatFit() {
        int room = this.height - MARGIN * 2 - PADDING * 2 - this.font.lineHeight * 2 - GAP - 2
                - ROW_HEIGHT - GAP * 3;
        return Math.max(1, Math.min(Math.max(1, games.size()), room / (ROW_HEIGHT + GAP)));
    }

    @Override
    protected void init() {
        int width = panelWidth() - PADDING * 2;
        int left = panelLeft() + PADDING;
        int showing = rowsThatFit();
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, games.size() - showing)));

        for (int row = 0; row < showing && this.scroll + row < games.size(); row++) {
            ReplayListPayload.Game game = games.get(this.scroll + row);
            addRenderableWidget(GatheringButtons.of(
                    left, listTop() + row * (ROW_HEIGHT + GAP), width, ROW_HEIGHT,
                    Component.literal(label(game)), () -> watch(game)));
        }

        addRenderableWidget(GatheringButtons.of(
                left, listTop() + showing * (ROW_HEIGHT + GAP) + GAP * 2, width, ROW_HEIGHT,
                Component.translatable("screen.gathering.replays.close"), this::onClose));
    }

    /** "Chris, Sam - 12 Mar 21:40". The names first, because that is what is scanned for. */
    private String label(ReplayListPayload.Game game) {
        String when = WHEN.format(Instant.ofEpochMilli(game.when()).atZone(ZoneId.systemDefault()));
        return game.players() + "  -  " + when;
    }

    private void watch(ReplayListPayload.Game game) {
        ClientReplay.watch(game.id());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int showing = rowsThatFit();
        if (games.size() > showing) {
            int wanted = Math.max(0,
                    Math.min(this.scroll - (int) Math.signum(scrollY), games.size() - showing));
            if (wanted != this.scroll) {
                this.scroll = wanted;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        GuiText.drawCentered(graphics, this.font, getTitle(),
                panelLeft() + panelWidth() / 2, panelTop() + PADDING, inner, 0xFFFFFF);
        GuiText.drawCentered(graphics, this.font,
                Component.translatable(games.isEmpty()
                        ? "screen.gathering.replays.none"
                        : "screen.gathering.replays.note"),
                panelLeft() + panelWidth() / 2,
                panelTop() + PADDING + this.font.lineHeight + 1, inner, DIM);

        // A shelf holds sixty-four games and a window holds five of them. Without this the
        // rest are reachable only by somebody who thought to try the wheel on a list that
        // gave no sign of having anything below it.
        int below = games.size() - rowsThatFit() - this.scroll;
        if (below > 0) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.replays.more", below),
                    panelLeft() + panelWidth() / 2,
                    listTop() + rowsThatFit() * (ROW_HEIGHT + GAP) + 1, inner, DIM);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** How many games this screen is showing. For the scene that photographs it. */
    int listed() {
        return Math.min(games.size(), rowsThatFit());
    }
}
