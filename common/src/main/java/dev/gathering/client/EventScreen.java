package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.network.EventActionPayload;
import dev.gathering.network.EventViewPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * One tournament: where it stands, your match, the standings and the pairings - and, for its
 * host, the controls that run it.
 * <p>Tabs rather than one long page, so it fits a small window: an overview with what you can do
 * next, the standings, the pairings, and the host's controls. The server sends every change, so
 * a result confirmed across the room shows here at once.
 * <p>Client-only.
 */
public final class EventScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int GOOD = 0xFF8FD18F;
    private static final int WARN = 0xFFE0B15A;
    private static final int PANEL_WIDTH = 420;
    private static final int MARGIN = 8;
    private static final int ROW = 18;
    private static final int LINE = 11;
    private static final int PER_PAGE = 10;

    enum Tab {
        OVERVIEW, STANDINGS, PAIRINGS, HOST
    }

    private EventViewPayload view;
    private Tab tab = Tab.OVERVIEW;
    private int page;
    private int selectedTable = -1;
    private int prizePlace = 1;
    private Rect panel = Rect.NONE;

    private EventScreen(EventViewPayload view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    public static void accept(EventViewPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof EventScreen open && open.view.id().equals(payload.id())) {
            open.view = payload;
            open.rebuildWidgets();
        } else if (payload.show()) {
            client.setScreen(new EventScreen(payload));
        }
    }

    EventViewPayload view() {
        return view;
    }

    void showTab(Tab wanted) {
        tab = wanted;
        page = 0;
        rebuildWidgets();
    }

    private void send(EventActionPayload.Action action) {
        ClientNetworking.send(EventActionPayload.of(view.id(), action));
    }

    private void send(EventActionPayload.Action action, int table, int a, int b, int draws, java.util.UUID player) {
        ClientNetworking.send(new EventActionPayload(view.id(), action, table, a, b, draws, player,
                Minecraft.getInstance().player == null ? net.minecraft.core.BlockPos.ZERO : Minecraft.getInstance().player.blockPosition()));
    }

    @Override
    protected void init() {
        int height = Math.min(236, this.height - 4);
        int width = Math.min(PANEL_WIDTH, this.width - 8);
        panel = new Rect((this.width - width) / 2, Math.max(2, (this.height - height) / 2), width, height);
        int tabs = view.youHost() ? 4 : 3;
        int tabWidth = (panel.width() - MARGIN * 2 - 3 * (tabs - 1)) / tabs;
        for (int index = 0; index < tabs; index++) {
            Tab each = Tab.values()[index];
            addRenderableWidget(GatheringButtons.toggle(panel.x() + MARGIN + index * (tabWidth + 3), panel.y() + 26, tabWidth, 16,
                    Component.translatable("screen.gathering.event.tab." + each.name().toLowerCase(java.util.Locale.ROOT)),
                    () -> tab == each, () -> showTab(each)));
        }
        int bottom = panel.bottom() - MARGIN - ROW;
        switch (tab) {
            case OVERVIEW -> overview(bottom);
            case STANDINGS, PAIRINGS -> paging(bottom);
            case HOST -> host(bottom);
        }
        addRenderableWidget(GatheringButtons.of(panel.right() - MARGIN - 70, bottom, 70, ROW, Component.translatable("gui.done"),
                this::onClose));
    }

    private void overview(int bottom) {
        int x = panel.x() + MARGIN;
        int buttonWidth = 90;
        boolean signingUp = "signup".equals(view.phase()) || "check_in".equals(view.phase());
        if (!view.registered() && "signup".equals(view.phase())) {
            addRenderableWidget(GatheringButtons.of(x, bottom, buttonWidth, ROW, Component.translatable("screen.gathering.event.register"),
                    () -> send(EventActionPayload.Action.REGISTER)));
        } else if (view.registered() && !view.dropped() && !"finished".equals(view.phase()) && !"cancelled".equals(view.phase())) {
            addRenderableWidget(GatheringButtons.of(x, bottom, buttonWidth, ROW,
                    Component.translatable(signingUp ? "screen.gathering.event.withdraw" : "screen.gathering.event.drop"),
                    () -> send(EventActionPayload.Action.WITHDRAW)));
            if ("check_in".equals(view.phase()) && !view.checkedIn()) {
                addRenderableWidget(GatheringButtons.of(x + buttonWidth + 4, bottom, buttonWidth, ROW,
                        Component.translatable("screen.gathering.event.check_in_now"), () -> send(EventActionPayload.Action.CHECK_IN)));
            }
            // Try the deck in hand on a board of your own before committing to it.
            addRenderableWidget(GatheringButtons.of(x + (buttonWidth + 4) * 2, bottom, buttonWidth, ROW,
                    Component.translatable("screen.gathering.event.practice"), this::practice));
            if ("preparing".equals(view.phase()) && !view.ready() && !"constructed".equals(view.kind())) {
                addRenderableWidget(GatheringButtons.of(x + buttonWidth + 4, bottom, buttonWidth, ROW,
                        Component.translatable("screen.gathering.event.ready"), () -> send(EventActionPayload.Action.READY)));
            }
        }
        EventViewPayload.Mine mine = view.mine();
        if (mine.table() > 0 && mine.confirmed().isEmpty()) {
            // The results a match of this length can end in, from this player's chair.
            int[][] results = resultsFor(view.bestOf());
            int width = (panel.width() - MARGIN * 2 - 4 * (results.length - 1)) / results.length;
            int y = panel.y() + 26 + 16 + 6 + LINE * 4;
            for (int index = 0; index < results.length; index++) {
                int[] result = results[index];
                String label = result[0] + "-" + result[1] + (result[2] > 0 ? "-" + result[2] : "");
                addRenderableWidget(GatheringButtons.toggle(x + index * (width + 4), y, width, ROW, Component.literal(label),
                        () -> label.equals(mine.myReport()), () -> send(EventActionPayload.Action.REPORT, 0,
                                result[0], result[1], result[2], EventActionPayload.NONE)));
            }
        }
    }

    /** The results a match of this length can end in, from the first chair. */
    static int[][] resultsFor(int bestOf) {
        return bestOf == 1
                ? new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}
                : bestOf == 3
                        ? new int[][] {{2, 0, 0}, {2, 1, 0}, {1, 2, 0}, {0, 2, 0}, {1, 1, 1}, {1, 0, 1}, {0, 1, 1}}
                        : new int[][] {{3, 0, 0}, {3, 1, 0}, {3, 2, 0}, {2, 3, 0}, {1, 3, 0}, {0, 3, 0}, {2, 2, 1}};
    }

    private void practice() {
        var player = Minecraft.getInstance().player;
        var deck = player == null ? null : dev.gathering.item.DeckItem.deckOf(player.getMainHandItem()).orElse(null);
        if (deck == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.gathering.event.hold_a_deck_to_practice"), true);
            }
            return;
        }
        // The main deck, or the sideboard when there is none: a pool fresh from a draft or a
        // sealed opening is all sideboard until the player builds from it.
        java.util.List<dev.gathering.item.CardComponent> source = deck.entries().isEmpty() ? deck.sideboard() : deck.entries();
        if (source.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.gathering.event.hold_a_deck_to_practice"), true);
            return;
        }
        java.util.List<dev.gathering.core.card.CardIdentity> cards = source.stream()
                .map(dev.gathering.item.CardComponent::toIdentity).toList();
        java.util.List<dev.gathering.core.card.CardIdentity> commanders = deck.commanders().stream()
                .map(dev.gathering.item.CardComponent::toIdentity).toList();
        Minecraft.getInstance().setScreen(TableScreen.practising(player.blockPosition(), cards, commanders));
    }

    private void paging(int bottom) {
        int x = panel.x() + MARGIN;
        int count = tab == Tab.STANDINGS ? view.standings().size() : view.pairings().size();
        addRenderableWidget(GatheringButtons.of(x, bottom, 24, ROW, Component.literal("<"), () -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }));
        addRenderableWidget(GatheringButtons.of(x + 28, bottom, 24, ROW, Component.literal(">"), () -> {
            if ((page + 1) * PER_PAGE < count) {
                page++;
                rebuildWidgets();
            }
        }));
        if (tab == Tab.PAIRINGS && view.youHost()) {
            int from = page * PER_PAGE;
            int y = listTop();
            for (int index = from; index < Math.min(view.pairings().size(), from + PER_PAGE); index++) {
                EventViewPayload.Match match = view.pairings().get(index);
                if (match.table() > 0) {
                    Component settle = Component.translatable("screen.gathering.event.select");
                    int width = this.font.width(settle) + 12;
                    addRenderableWidget(GatheringButtons.toggle(panel.right() - MARGIN - width, y - 1, width, LINE,
                            settle, () -> selectedTable == match.table(),
                            () -> {
                                selectedTable = selectedTable == match.table() ? -1 : match.table();
                                rebuildWidgets();
                            }));
                }
                y += LINE;
            }
            EventViewPayload.Match chosen = view.pairings().stream().filter(match -> match.table() == selectedTable)
                    .findFirst().orElse(null);
            if (chosen != null) {
                // The results a match of this length can end in, as the players' own buttons offer.
                int[][] results = resultsFor(view.bestOf());
                int sx = x + 56;
                int room = panel.right() - MARGIN - 74 - sx - 48;
                int each = Math.max(22, Math.min(34, room / results.length - 3));
                for (int[] result : results) {
                    String label = result[0] + "-" + result[1] + (result[2] > 0 ? "-" + result[2] : "");
                    addRenderableWidget(GatheringButtons.of(sx, bottom, each, ROW, Component.literal(label),
                            () -> send(EventActionPayload.Action.SETTLE, chosen.table(), result[0], result[1], result[2],
                                    EventActionPayload.NONE)));
                    sx += each + 3;
                }
                addRenderableWidget(GatheringButtons.of(sx, bottom, 44, ROW, Component.translatable("screen.gathering.event.drop_a"),
                        () -> send(EventActionPayload.Action.DROP_PLAYER, 0, 0, 0, 0, chosen.idA())));
                addRenderableWidget(GatheringButtons.of(sx + 47, bottom, 44, ROW, Component.translatable("screen.gathering.event.drop_b"),
                        () -> send(EventActionPayload.Action.DROP_PLAYER, 0, 0, 0, 0, chosen.idB())));
            }
        }
    }

    private void host(int bottom) {
        int x = panel.x() + MARGIN;
        int y = panel.y() + 26 + 16 + 8;
        int width = (panel.width() - MARGIN * 2 - 8) / 3;
        addRenderableWidget(GatheringButtons.of(x, y, width, ROW, Component.translatable("screen.gathering.event.open_check_in"),
                () -> send(EventActionPayload.Action.OPEN_CHECK_IN)));
        addRenderableWidget(GatheringButtons.of(x + width + 4, y, width, ROW, Component.translatable("screen.gathering.event.begin"),
                () -> send(EventActionPayload.Action.BEGIN)));
        addRenderableWidget(GatheringButtons.of(x + (width + 4) * 2, y, width, ROW, Component.translatable("screen.gathering.event.start_now"),
                () -> send(EventActionPayload.Action.START_NOW)));
        y += ROW + 6;
        addRenderableWidget(GatheringButtons.of(x, y, width, ROW, Component.translatable("screen.gathering.event.add_tables"),
                () -> send(EventActionPayload.Action.ADD_TABLES)));
        addRenderableWidget(GatheringButtons.of(x + width + 4, y, 20, ROW, Component.literal("-"), () -> {
            prizePlace = Math.max(1, prizePlace - 1);
            rebuildWidgets();
        }));
        addRenderableWidget(GatheringButtons.of(x + width + 4 + 24, y, width - 48, ROW,
                Component.translatable("screen.gathering.event.prize_for", prizePlace),
                () -> send(EventActionPayload.Action.ADD_PRIZE, prizePlace, 0, 0, 0, EventActionPayload.NONE)));
        addRenderableWidget(GatheringButtons.of(x + width * 2 - 16, y, 20, ROW, Component.literal("+"), () -> {
            prizePlace = Math.min(16, prizePlace + 1);
            rebuildWidgets();
        }));
        addRenderableWidget(GatheringButtons.of(x + (width + 4) * 2, y, width, ROW, Component.translatable("screen.gathering.event.cancel"),
                () -> send(EventActionPayload.Action.CANCEL)));
        y += ROW + 6;
        addRenderableWidget(GatheringButtons.of(x, y, width, ROW, Component.translatable("screen.gathering.event.mark_registration"),
                () -> send(EventActionPayload.Action.MARK_REGISTRATION)));
    }

    private int listTop() {
        return panel.y() + 26 + 16 + 6 + LINE;
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
        int width = panel.width() - MARGIN * 2;
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + 3, width, LABEL);
        GuiText.drawCentered(graphics, this.font, header(), panel.x() + panel.width() / 2, panel.y() + 14, width, DIM);
        int y = panel.y() + 26 + 16 + 6;
        switch (tab) {
            case OVERVIEW -> renderOverview(graphics, x, y, width);
            case STANDINGS -> renderStandings(graphics, x, y, width);
            case PAIRINGS -> renderPairings(graphics, x, y, width);
            case HOST -> GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.host_help"),
                    x, panel.y() + 26 + 16 + 8 + (ROW + 6) * 3, width, DIM);
        }
    }

    private Component header() {
        Component phase = Component.translatable("screen.gathering.event.phase." + view.phase());
        Component kind = Component.translatable("screen.gathering.event.kind." + view.kind());
        String clock = view.secondsLeft() < 0 ? "" : String.format(java.util.Locale.ROOT, " · %d:%02d",
                view.secondsLeft() / 60, view.secondsLeft() % 60);
        if (view.round() > 0) {
            return Component.translatable("screen.gathering.event.header_round", kind, view.format(), phase, view.round(),
                    Math.max(view.round(), view.plannedRounds()), view.players(), clock);
        }
        return Component.translatable("screen.gathering.event.header", kind, view.format(), phase, view.players(), clock);
    }

    private void renderOverview(GuiGraphics graphics, int x, int y, int width) {
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.settings_line", view.host(),
                view.bestOf(), view.roundMinutes(), view.topCut() == 0 ? Component.translatable("screen.gathering.event.no_top_cut")
                        : Component.translatable("screen.gathering.event.top_cut." + view.topCut()),
                Component.translatable("screen.gathering.event.decks." + view.decks())), x, y, width, DIM);
        y += LINE;
        Component status;
        int color = DIM;
        if (!view.registered()) {
            status = Component.translatable("screen.gathering.event.you.not_registered");
        } else if (view.dropped()) {
            status = Component.translatable("screen.gathering.event.you.dropped");
        } else if (view.ready()) {
            status = Component.translatable("screen.gathering.event.you.ready");
            color = GOOD;
        } else {
            status = Component.translatable("screen.gathering.event.you.registered");
            color = GOOD;
        }
        GuiText.draw(graphics, this.font, status, x, y, width, color);
        y += LINE;
        EventViewPayload.Mine mine = view.mine();
        if (mine.table() == 0) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.you.bye"), x, y, width, GOOD);
        } else if (mine.table() > 0) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.you.table", mine.table(),
                    mine.opponent()), x, y, width, LABEL);
            y += LINE;
            Component line = !mine.confirmed().isEmpty()
                    ? Component.translatable("screen.gathering.event.you.confirmed", mine.confirmed())
                    : !mine.theirReport().isEmpty() && !mine.myReport().isEmpty()
                            ? Component.translatable("screen.gathering.event.you.disputed", mine.myReport(), mine.theirReport())
                            : !mine.theirReport().isEmpty()
                                    ? Component.translatable("screen.gathering.event.you.they_reported", mine.theirReport())
                                    : !mine.suggested().isEmpty()
                                            ? Component.translatable("screen.gathering.event.you.suggested", mine.suggested())
                                            : mine.extraTurns() >= 0
                                                    ? Component.translatable("screen.gathering.event.you.extra_turns", mine.extraTurns())
                                                    : Component.translatable("screen.gathering.event.you.report");
            GuiText.draw(graphics, this.font, line, x, y, width, mine.confirmed().isEmpty() ? WARN : GOOD);
        }
        int prizesTop = panel.y() + 26 + 16 + 6 + LINE * 4 + ROW + 6;
        if (!view.places().isEmpty()) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.places",
                    String.join(", ", view.places().subList(0, Math.min(4, view.places().size())))), x, prizesTop, width, GOOD);
            prizesTop += LINE;
        }
        List<String> prizes = view.prizes();
        for (int index = 0; index < Math.min(4, prizes.size()); index++) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.prize", prizes.get(index)),
                    x, prizesTop + index * LINE, width, DIM);
        }
    }

    private void renderStandings(GuiGraphics graphics, int x, int y, int width) {
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.standings_head"), x, y, width, DIM);
        y += LINE;
        int from = page * PER_PAGE;
        for (int index = from; index < Math.min(view.standings().size(), from + PER_PAGE); index++) {
            EventViewPayload.Row row = view.standings().get(index);
            String line = String.format(java.util.Locale.ROOT, "%2d  %-16s %2d  %d-%d-%d  %4.1f  %4.1f  %4.1f",
                    row.rank(), row.name(), row.points(), row.wins(), row.losses(), row.draws(),
                    row.omw() / 10.0, row.gw() / 10.0, row.ogw() / 10.0);
            GuiText.draw(graphics, this.font, Component.literal(line), x, y, width, row.dropped() ? DIM : LABEL);
            y += LINE;
        }
    }

    private void renderPairings(GuiGraphics graphics, int x, int y, int width) {
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.pairings_head"), x, y, width, DIM);
        y += LINE;
        int from = page * PER_PAGE;
        for (int index = from; index < Math.min(view.pairings().size(), from + PER_PAGE); index++) {
            EventViewPayload.Match match = view.pairings().get(index);
            Component line = match.table() == 0
                    ? Component.translatable("screen.gathering.event.pairing_bye", match.a())
                    : Component.translatable("screen.gathering.event.pairing", match.table(), match.a(), match.b(),
                            Component.translatable("screen.gathering.event.status." + match.status()), match.result());
            int color = switch (match.status()) {
                case "confirmed", "bye" -> GOOD;
                case "disputed" -> WARN;
                default -> LABEL;
            };
            GuiText.draw(graphics, this.font, line, x, y, width - (view.youHost() ? 44 : 0), color);
            y += LINE;
        }
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
