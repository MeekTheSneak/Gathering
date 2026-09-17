package dev.gathering.client;

import dev.gathering.core.tournament.HostActions;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.ui.EventScreenLayout;
import dev.gathering.core.ui.InterfaceScale;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.EventActionPayload;
import dev.gathering.network.EventViewPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * One tournament: where it stands, your match, the standings and the pairings - and, for its
 * host, the controls that run it.
 * <p>Tabs rather than one long page, so it fits a small window: an overview with what you can do
 * next, the standings, the pairings, and the host's controls. The server sends every change, so
 * a result confirmed across the room shows here at once.
 * <p>Laid out by {@link EventScreenLayout}, at the text and control sizes the player chose; when a
 * window is too small for everything at those sizes, less is shown at once rather than smaller.
 * A refresh from the server keeps keyboard focus on the control it was on ({@link FocusKeeper}),
 * and the host's controls are offered only where the event would take them, with the reason on a
 * control that is not.
 * <p>Client-only.
 */
public final class EventScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int GOOD = 0xFF8FD18F;
    private static final int WARN = 0xFFE0B15A;
    private static final int PER_PAGE = 10;

    enum Tab {
        OVERVIEW, STANDINGS, PAIRINGS, HOST
    }

    private EventViewPayload view;
    private Tab tab = Tab.OVERVIEW;
    private int page;
    private int selectedTable = -1;
    private int prizePlace = 1;
    /** Whether the overview is showing a match's result buttons on their own, where they did not fit beside it. */
    private boolean reporting;
    /** Which page of result buttons shows, where there are more than fit at once. */
    private int resultPage;
    private EventScreenLayout layout = EventScreenLayout.of(854, 480, 100, 1f, 9);
    private final FocusKeeper focus = new FocusKeeper();

    private EventScreen(EventViewPayload view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    public static void accept(EventViewPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof EventScreen open && open.view.id().equals(payload.id())) {
            open.view = payload;
            open.refresh();
        } else if (payload.show()) {
            client.setScreen(new EventScreen(payload));
        }
    }

    EventViewPayload view() {
        return view;
    }

    /** Where this screen is laid out now. For the accessibility probe. */
    EventScreenLayout layout() {
        return layout;
    }

    Tab tab() {
        return tab;
    }

    void showTab(Tab wanted) {
        tab = wanted;
        page = 0;
        reporting = false;
        refresh();
    }

    /** Built again, focus kept on what it was on - or on this tab's own button if that has gone. */
    private void refresh() {
        focus.rebuild(this, this::rebuildWidgets, () -> "tab:" + tab.name());
    }

    private void send(EventActionPayload.Action action) {
        ClientNetworking.send(EventActionPayload.of(view.id(), action));
    }

    private void send(EventActionPayload.Action action, int table, int a, int b, int draws, java.util.UUID player) {
        ClientNetworking.send(new EventActionPayload(view.id(), action, table, a, b, draws, player,
                Minecraft.getInstance().player == null ? net.minecraft.core.BlockPos.ZERO : Minecraft.getInstance().player.blockPosition()));
    }

    private <T extends AbstractWidget> T add(String name, T widget) {
        return addRenderableWidget(focus.named(name, widget));
    }

    /** A button as wide as its label wants at the text size asked, and at least {@code least} scaled by the controls. */
    private int widthFor(Component label, int least) {
        int forTheLabel = Math.round(this.font.width(label) * GuiText.askedScale()) + 12;
        return Math.max(Math.round(least * InterfaceScale.asFraction(ClientSettings.controlScale())), forTheLabel);
    }

    @Override
    protected void init() {
        layout = EventScreenLayout.of(this.width, this.height, ClientSettings.controlScale(), GuiText.askedScale(),
                this.font.lineHeight);
        int tabs = view.youHost() ? 4 : 3;
        for (int index = 0; index < tabs; index++) {
            Tab each = Tab.values()[index];
            Rect at = layout.tab(index, tabs);
            add("tab:" + each.name(), GatheringButtons.toggle(at.x(), at.y(), at.width(), at.height(),
                    Component.translatable("screen.gathering.event.tab." + each.name().toLowerCase(java.util.Locale.ROOT)),
                    () -> tab == each, () -> showTab(each)));
        }
        Component done = Component.translatable("gui.done");
        int doneWidth = Math.min(widthFor(done, 70), layout.bodyWidth() / 3);
        int doneX = layout.panel().right() - EventScreenLayout.MARGIN - doneWidth;
        switch (tab) {
            case OVERVIEW -> overview(doneX);
            case STANDINGS, PAIRINGS -> paging(doneX);
            case HOST -> host(doneX);
        }
        if (tab != Tab.OVERVIEW && tab != Tab.PAIRINGS) {
            resultPage = 0;
        }
        add("done", GatheringButtons.of(doneX, layout.bottomRow(), doneWidth, layout.row(), done, this::onClose));
        // Buttons side by side draw their words at one size, not each at whatever its own
        // length allowed. A row is whatever shares a top edge.
        java.util.Map<Integer, List<Button>> rows = new java.util.TreeMap<>();
        for (var child : children()) {
            if (child instanceof Button button) {
                rows.computeIfAbsent(button.getY(), ignored -> new java.util.ArrayList<>()).add(button);
            }
        }
        rows.values().forEach(GatheringButtons::matchLabels);
    }

    // ------------------------------------------------------------------ overview

    private void overview(int doneX) {
        int x = layout.left();
        int y = layout.bottomRow();
        int room = doneX - layout.gap() - x;
        EventViewPayload.Mine reportingOn = view.mine();
        if (reporting && (reportingOn.table() <= 0 || !reportingOn.confirmed().isEmpty())) {
            // The match was settled while its buttons were up: back to the overview, which says so.
            reporting = false;
        }
        if (reporting) {
            Component back = Component.translatable("gui.back");
            int top = resultsTop(true);
            List<MatchResult> results = resultsForMe();
            int pageSize = resultsPerPage(results.size(), top);
            // Room for the page arrows beside it first, when there is more than one page.
            int arrows = pageSize < results.size() ? arrowWidth() * 2 + layout.gap() * 3 : 0;
            int backWidth = Math.max(1, Math.min(widthFor(back, 90), room - arrows));
            add("report:back", GatheringButtons.of(x, y, backWidth, layout.row(), back, () -> {
                reporting = false;
                resultPage = 0;
                refresh();
            }));
            resultButtons(results, top, pageSize);
            resultPaging(results.size(), pageSize, x + backWidth + layout.gap() * 2, y, "report");
            return;
        }
        boolean signingUp = "signup".equals(view.phase()) || "check_in".equals(view.phase());
        List<Button> row = new java.util.ArrayList<>();
        List<String> names = new java.util.ArrayList<>();
        if (!view.registered() && "signup".equals(view.phase())) {
            row.add(bottomButton("screen.gathering.event.register", () -> send(EventActionPayload.Action.REGISTER)));
            names.add("register");
        } else if (view.registered() && !view.dropped() && !"finished".equals(view.phase()) && !"cancelled".equals(view.phase())) {
            row.add(bottomButton(signingUp ? "screen.gathering.event.withdraw" : "screen.gathering.event.drop",
                    () -> send(EventActionPayload.Action.WITHDRAW)));
            names.add("withdraw");
            if ("check_in".equals(view.phase()) && !view.checkedIn()) {
                row.add(bottomButton("screen.gathering.event.check_in_now", () -> send(EventActionPayload.Action.CHECK_IN)));
                names.add("check_in");
            }
            if ("preparing".equals(view.phase()) && !view.ready() && !"constructed".equals(view.kind())) {
                row.add(bottomButton("screen.gathering.event.ready", () -> send(EventActionPayload.Action.READY)));
                names.add("ready");
            }
            // Try the deck in hand on a board of your own before committing to it.
            row.add(bottomButton("screen.gathering.event.practice", this::practice));
            names.add("practice");
        }
        EventViewPayload.Mine mine = view.mine();
        boolean reportable = mine.table() > 0 && mine.confirmed().isEmpty();
        boolean resultsFit = reportable && resultsFit(resultsTop(false));
        if (reportable && !resultsFit) {
            // Too many result buttons for the room beside the match at these sizes: a button that
            // shows them on their own, rather than buttons squeezed smaller than the player asked.
            row.add(0, bottomButton("screen.gathering.event.report_result", () -> {
                reporting = true;
                refresh();
            }));
            names.add(0, "report:open");
        }
        layoutRow(row, names, x, y, room);
        if (resultsFit) {
            List<MatchResult> results = resultsForMe();
            resultButtons(results, resultsTop(false), results.size());
        }
    }

    /** How many result buttons show at once from {@code top}: every one if they fit, else as many whole rows as do. */
    private int resultsPerPage(int count, int top) {
        int perRow = layout.perRow(count, narrowestResult());
        int rowsThatFit = Math.max(1, (layout.bodyBottom() - top + layout.gap()) / (layout.row() + layout.gap()));
        return Math.min(count, rowsThatFit * perRow);
    }

    /** A page arrow: square, a row tall, and never narrower than the control size asks. */
    private int arrowWidth() {
        return Math.max(layout.row(), Math.round(24 * InterfaceScale.asFraction(ClientSettings.controlScale())));
    }

    /** Arrows through the pages of result buttons, when there is more than one page. */
    private void resultPaging(int count, int pageSize, int x, int y, String name) {
        int pages = (count + pageSize - 1) / Math.max(1, pageSize);
        if (pages <= 1) {
            return;
        }
        resultPage = Math.min(resultPage, pages - 1);
        int arrow = arrowWidth();
        var back = add(name + ":earlier", GatheringButtons.of(x, y, arrow, layout.row(), Component.literal("<"), () -> {
            resultPage = Math.max(0, resultPage - 1);
            refresh();
        }));
        var forward = add(name + ":later", GatheringButtons.of(x + arrow + layout.gap(), y, arrow, layout.row(),
                Component.literal(">"), () -> {
                    resultPage = Math.min(pages - 1, resultPage + 1);
                    refresh();
                }));
        back.active = resultPage > 0;
        forward.active = resultPage < pages - 1;
    }

    private Button bottomButton(String key, Runnable action) {
        Component label = Component.translatable(key);
        return GatheringButtons.of(0, 0, widthFor(label, 90), layout.row(), label, action);
    }

    /** Lays buttons along the bottom row from the left, each as wide as it wants while they fit, shared out when not. */
    private void layoutRow(List<Button> buttons, List<String> names, int x, int y, int room) {
        if (buttons.isEmpty()) {
            return;
        }
        int wanted = buttons.stream().mapToInt(AbstractWidget::getWidth).sum() + layout.gap() * (buttons.size() - 1);
        int left = x;
        for (int index = 0; index < buttons.size(); index++) {
            Button button = buttons.get(index);
            int width = wanted <= room ? button.getWidth()
                    : Math.max(1, (room - layout.gap() * (buttons.size() - 1)) / buttons.size());
            button.setRectangle(width, layout.row(), left, y);
            add(names.get(index), button);
            left += width + layout.gap();
        }
    }

    /** Where a match's result buttons start: under the match, or at the top of the body when shown on their own. */
    private int resultsTop(boolean onTheirOwn) {
        // On their own, under two lines: the table, and what to do.
        return onTheirOwn ? layout.bodyTop() + layout.line() * 2 + layout.gap() : layout.bodyTop() + layout.line() * 4;
    }

    private boolean resultsFit(int top) {
        List<MatchResult> results = resultsForMe();
        return top + layout.heightOfRows(layout.rowsFor(results.size(), narrowestResult())) <= layout.bodyBottom();
    }

    /** The narrowest a result button may be and still show "2-1-1" at the text size asked. */
    private int narrowestResult() {
        return widthFor(Component.literal("2-1-1"), 40);
    }

    /** A page of this player's result buttons from {@code top}, {@code pageSize} to a page. */
    private void resultButtons(List<MatchResult> results, int top, int pageSize) {
        EventViewPayload.Mine mine = view.mine();
        if (mine.table() <= 0 || !mine.confirmed().isEmpty()) {
            return;
        }
        int perRow = layout.perRow(results.size(), narrowestResult());
        int width = (layout.bodyWidth() - layout.gap() * (perRow - 1)) / perRow;
        int from = Math.min(results.size(), resultPage * pageSize);
        for (int index = from; index < Math.min(results.size(), from + pageSize); index++) {
            MatchResult result = results.get(index);
            String label = result.label();
            int at = index - from;
            add("report:" + label, GatheringButtons.toggle(layout.left() + at % perRow * (width + layout.gap()),
                    top + at / perRow * (layout.row() + layout.gap()), width, layout.row(), Component.literal(label),
                    () -> label.equals(mine.myReport()), () -> send(EventActionPayload.Action.REPORT, 0,
                            result.winsA(), result.winsB(), result.draws(), EventActionPayload.NONE)));
        }
    }

    /**
     * The results this player is offered: every usual one, and what the table saw when that is not
     * among them - one to none with two drawn, say - so what the table suggests is one press.
     */
    private List<MatchResult> resultsForMe() {
        List<MatchResult> results = new java.util.ArrayList<>(offeredResults());
        MatchResult.parse(view.mine().suggested())
                .filter(seen -> seen.fits(view.bestOf()) && !(view.elimination() && seen.isDraw()))
                .filter(seen -> !results.contains(seen))
                .ifPresent(seen -> results.add(0, seen));
        return results;
    }

    /** The results this event's matches can be reported as, from the first chair. */
    private List<MatchResult> offeredResults() {
        return MatchResult.offered(view.bestOf(), view.elimination());
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
        Minecraft.getInstance().setScreen(TableScreen.practicing(player.blockPosition(), cards, commanders));
    }

    // ------------------------------------------------------------------ standings and pairings

    private void paging(int doneX) {
        int x = layout.left();
        int y = layout.bottomRow();
        int count = tab == Tab.STANDINGS ? view.standings().size() : view.pairings().size();
        int arrow = Math.max(layout.row(), Math.round(24 * InterfaceScale.asFraction(ClientSettings.controlScale())));
        // With a table picked and no room left for the list, the arrows turn the pages of its results.
        boolean pagingResults = tab == Tab.PAIRINGS && view.youHost() && chosenMatch() != null && perPage() == 0;
        var back = add("page:back", GatheringButtons.of(x, y, arrow, layout.row(), Component.literal("<"), () -> {
            if (pagingResults) {
                resultPage = Math.max(0, resultPage - 1);
            } else {
                page = Math.max(0, page - 1);
            }
            refresh();
        }));
        var forward = add("page:forward", GatheringButtons.of(x + arrow + layout.gap(), y, arrow, layout.row(),
                Component.literal(">"), () -> {
                    if (pagingResults) {
                        resultPage++;
                        refresh();
                    } else if ((page + 1) * Math.max(1, perPage()) < count) {
                        page++;
                        refresh();
                    }
                }));
        // Grayed where there is no page to go to, as the event list's are: a button that does
        // nothing when pressed reads as a button that is broken.
        back.active = page > 0;
        forward.active = (page + 1) * Math.max(1, perPage()) < count;
        if (tab != Tab.PAIRINGS || !view.youHost()) {
            return;
        }
        int from = page * Math.max(1, perPage());
        int rowY = listTop();
        for (int index = from; index < Math.min(view.pairings().size(), from + perPage()); index++) {
            EventViewPayload.Match match = view.pairings().get(index);
            if (match.table() > 0) {
                Component settle = Component.translatable("screen.gathering.event.select");
                int width = widthFor(settle, 40);
                add("settle:" + match.table(), GatheringButtons.toggle(layout.panel().right() - EventScreenLayout.MARGIN - width,
                        rowY, width, layout.row(), settle, () -> selectedTable == match.table(),
                        () -> {
                            selectedTable = selectedTable == match.table() ? -1 : match.table();
                            refresh();
                        }));
            }
            rowY += listLine();
        }
        EventViewPayload.Match chosen = chosenMatch();
        if (chosen == null) {
            return;
        }
        if (perPage() == 0) {
            // No room for the list beside the results at these sizes: just the chosen match, with its Settle.
            Component settle = Component.translatable("screen.gathering.event.select");
            int width = widthFor(settle, 40);
            add("settle:" + chosen.table(), GatheringButtons.toggle(layout.panel().right() - EventScreenLayout.MARGIN - width,
                    listTop(), width, layout.row(), settle, () -> true, () -> {
                        selectedTable = -1;
                        refresh();
                    }));
        }
        // The results a match of this length can end in, as the players' own buttons offer, on rows
        // of their own above the page buttons - a page of them at a time, where the window is too small
        // for them all at the sizes asked, turned by the same arrows the list uses.
        List<MatchResult> results = offeredResults();
        int perRow = layout.perRow(results.size(), narrowestResult());
        int rows = layout.rowsFor(results.size(), narrowestResult());
        int each = (layout.bodyWidth() - layout.gap() * (perRow - 1)) / perRow;
        boolean allFit = !pagingResults;
        int top = allFit ? settleTop(rows) : listTop() + listLine() + layout.gap();
        int pageSize = allFit ? results.size() : resultsPerPage(results.size(), top);
        int pages = (results.size() + pageSize - 1) / Math.max(1, pageSize);
        resultPage = Math.min(resultPage, pages - 1);
        if (!allFit) {
            back.active = resultPage > 0;
            forward.active = resultPage < pages - 1;
        }
        int firstResult = resultPage * pageSize;
        for (int index = firstResult; index < Math.min(results.size(), firstResult + pageSize); index++) {
            MatchResult result = results.get(index);
            int at = index - firstResult;
            add("settle:" + chosen.table() + ":" + result.label(), GatheringButtons.of(x + at % perRow * (each + layout.gap()),
                    top + at / perRow * (layout.row() + layout.gap()), each, layout.row(), Component.literal(result.label()),
                    () -> send(EventActionPayload.Action.SETTLE, chosen.table(), result.winsA(), result.winsB(),
                            result.draws(), EventActionPayload.NONE)));
        }
        Component dropA = Component.translatable("screen.gathering.event.drop_a");
        Component dropB = Component.translatable("screen.gathering.event.drop_b");
        int dropsX = forward.getX() + forward.getWidth() + layout.gap() * 2;
        int room = doneX - layout.gap() - dropsX;
        int dropAWidth = Math.min(widthFor(dropA, 44), Math.max(1, (room - layout.gap()) / 2));
        int dropBWidth = Math.min(widthFor(dropB, 44), Math.max(1, (room - layout.gap()) / 2));
        add("drop:a:" + chosen.table(), GatheringButtons.of(dropsX, y, dropAWidth, layout.row(), dropA,
                () -> send(EventActionPayload.Action.DROP_PLAYER, 0, 0, 0, 0, chosen.idA())));
        add("drop:b:" + chosen.table(), GatheringButtons.of(dropsX + dropAWidth + layout.gap(), y, dropBWidth, layout.row(), dropB,
                () -> send(EventActionPayload.Action.DROP_PLAYER, 0, 0, 0, 0, chosen.idB())));
    }

    private EventViewPayload.Match chosenMatch() {
        return view.pairings().stream().filter(match -> match.table() > 0 && match.table() == selectedTable)
                .findFirst().orElse(null);
    }

    /** Where the host's settle buttons start, so their last row sits just above the bottom row. */
    private int settleTop(int rows) {
        return layout.bodyBottom() - layout.heightOfRows(rows);
    }

    /**
     * How many rows a page of standings or pairings holds: ten, or as many as fit above the page
     * buttons - and above the host's settle buttons while a table is picked. None, while a table is
     * picked and its results leave no room for the list.
     */
    private int perPage() {
        int floor = layout.bodyBottom();
        if (tab == Tab.PAIRINGS && view.youHost() && chosenMatch() != null) {
            floor = settleTop(layout.rowsFor(offeredResults().size(), narrowestResult())) - layout.gap();
            return Math.max(0, Math.min(PER_PAGE, (floor - listTop()) / listLine()));
        }
        return Math.max(1, Math.min(PER_PAGE, (floor - listTop()) / listLine()));
    }

    /**
     * How tall a row of the list is. A line of text, except the host's pairings, whose rows each carry
     * a Settle button: a button squeezed into a line of text drew its art broken and its word spilling
     * off its face.
     */
    private int listLine() {
        return tab == Tab.PAIRINGS && view.youHost() ? layout.row() + 1 : layout.line();
    }

    private int listTop() {
        return layout.bodyTop() + layout.line();
    }

    // ------------------------------------------------------------------ the host's controls

    /**
     * The host's controls, in as many columns as their labels allow at the text size asked - one
     * control too wide for a column takes two rather than narrowing the rest - and a page at a time
     * where the rows do not all fit - a small window at large sizes has room for
     * two or three. Always in the same order, so a control is where it was on the page before.
     */
    private void host(int doneX) {
        int stepper = Math.max(layout.row(), Math.round(20 * InterfaceScale.asFraction(ClientSettings.controlScale())));
        Component prizeLabel = Component.translatable("screen.gathering.event.prize_for", Math.max(prizePlace, 10));
        List<HostCell> cells = List.of(
                cell(HostActions.Action.OPEN_CHECK_IN, "screen.gathering.event.open_check_in",
                        () -> send(EventActionPayload.Action.OPEN_CHECK_IN)),
                cell(HostActions.Action.BEGIN, "screen.gathering.event.begin", () -> send(EventActionPayload.Action.BEGIN)),
                cell(HostActions.Action.START_NOW, "screen.gathering.event.start_now",
                        () -> send(EventActionPayload.Action.START_NOW)),
                cell(HostActions.Action.ADD_TABLES, "screen.gathering.event.add_tables",
                        () -> send(EventActionPayload.Action.ADD_TABLES)),
                new HostCell(labelWidth(prizeLabel) + (stepper + layout.gap()) * 2, (x, y, width) -> prizeStepper(x, y, width, stepper)),
                // Calling an event off cannot be taken back, so it asks first - and says what it will do.
                cell(HostActions.Action.CANCEL, "screen.gathering.event.cancel",
                        () -> Minecraft.getInstance().setScreen(new ConfirmScreen(
                                Component.translatable("confirm.gathering.call_off", view.name()),
                                Component.translatable("confirm.gathering.call_off.detail"),
                                Component.translatable("confirm.gathering.call_off.yes"),
                                () -> send(EventActionPayload.Action.CANCEL), this))),
                cell(HostActions.Action.MARK_REGISTRATION, "screen.gathering.event.mark_registration",
                        () -> send(EventActionPayload.Action.MARK_REGISTRATION)));
        // Columns for the ordinary controls; one that needs more than a column - the prize, with its
        // steppers - takes as many as it needs, rather than squeezing every other control to its width.
        int columns = 3;
        while (columns > 1 && tooWide(cells, columnWidth(columns)) > 1) {
            columns--;
        }
        int width = columnWidth(columns);
        List<List<int[]>> rows = new java.util.ArrayList<>();
        List<int[]> row = new java.util.ArrayList<>();
        int used = 0;
        for (int index = 0; index < cells.size(); index++) {
            int span = Math.min(columns, Math.max(1, (cells.get(index).least() + layout.gap() + width - 1) / (width + layout.gap())));
            if (used + span > columns) {
                rows.add(row);
                row = new java.util.ArrayList<>();
                used = 0;
            }
            row.add(new int[] {index, used, span});
            used += span;
        }
        rows.add(row);
        int step = layout.row() + layout.gap() * 2;
        int perPage = Math.max(1, (layout.bodyBottom() - layout.bodyTop() + layout.gap() * 2) / step);
        int pages = (rows.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        int first = page * perPage;
        int shown = Math.min(rows.size() - first, perPage);
        for (int line = 0; line < shown; line++) {
            for (int[] placed : rows.get(first + line)) {
                cells.get(placed[0]).place().at(layout.left() + placed[1] * (width + layout.gap()),
                        layout.bodyTop() + line * step, width * placed[2] + layout.gap() * (placed[2] - 1));
            }
        }
        hostHelpTop = layout.bodyTop() + shown * step;
        if (pages > 1) {
            int arrow = arrowWidth();
            int x = doneX - layout.gap() * 2 - arrow * 2 - layout.gap();
            int y = layout.bottomRow();
            var back = add("host:earlier", GatheringButtons.arrow(x, y, arrow, layout.row(), GatheringSprites.Element.ARROW_LEFT,
                    Component.translatable("screen.gathering.event.host_earlier"), () -> {
                        page = Math.max(0, page - 1);
                        refresh();
                    }));
            var forward = add("host:later", GatheringButtons.arrow(x + arrow + layout.gap(), y, arrow, layout.row(),
                    GatheringSprites.Element.ARROW_RIGHT, Component.translatable("screen.gathering.event.host_later"), () -> {
                        page = Math.min(pages - 1, page + 1);
                        refresh();
                    }));
            back.active = page > 0;
            forward.active = page < pages - 1;
        }
    }

    /** How many of these controls need more than this width. */
    private static long tooWide(List<HostCell> cells, int width) {
        return cells.stream().filter(cell -> cell.least() > width).count();
    }

    /** How wide each of this many columns of host controls is. */
    private int columnWidth(int columns) {
        return (layout.bodyWidth() - layout.gap() * (columns - 1)) / columns;
    }

    /** Where the host's help starts: under the rows of controls on this page. */
    private int hostHelpTop;

    /** Somewhere a host control goes. */
    private interface Placing {
        void at(int x, int y, int width);
    }

    /** A host control, and the least width it can be given without its label shrinking. */
    private record HostCell(int least, Placing place) {
    }

    private HostCell cell(HostActions.Action action, String key, Runnable pressed) {
        return new HostCell(labelWidth(Component.translatable(key)),
                (x, y, width) -> hostControl(action, key, x, y, width, pressed));
    }

    /** The width a button needs for this label at the text size asked. */
    private int labelWidth(Component label) {
        return Math.round(this.font.width(label) * GuiText.askedScale()) + 12;
    }

    /** The prize: which place it is for, a step down and a step up either side. */
    private void prizeStepper(int x, int y, int width, int stepper) {
        Button less = add("prize:less", GatheringButtons.of(x, y, stepper, layout.row(), Component.literal("-"), () -> {
            prizePlace = Math.max(1, prizePlace - 1);
            refresh();
        }));
        Button prize = hostControl(HostActions.Action.ADD_PRIZE, null, x + stepper + layout.gap(), y,
                Math.max(1, width - (stepper + layout.gap()) * 2),
                () -> send(EventActionPayload.Action.ADD_PRIZE, prizePlace, 0, 0, 0, EventActionPayload.NONE));
        prize.setMessage(Component.translatable("screen.gathering.event.prize_for", prizePlace));
        Button more = add("prize:more", GatheringButtons.of(x + width - stepper, y, stepper, layout.row(),
                Component.literal("+"), () -> {
                    prizePlace = Math.min(16, prizePlace + 1);
                    refresh();
                }));
        less.active = prize.active;
        more.active = prize.active;
    }

    /** One of the host's controls: live where the event would take it, grayed with the reason where not. */
    private Button hostControl(HostActions.Action action, String key, int x, int y, int width, Runnable pressed) {
        Button button = add("host:" + action.name(), GatheringButtons.of(x, y, width, layout.row(),
                key == null ? Component.empty() : Component.translatable(key), pressed));
        view.refusalOf(action).ifPresent(why -> {
            button.active = false;
            button.setTooltip(Tooltip.create(Component.translatable(why)));
        });
        return button;
    }


    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Rect panel = layout.panel();
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Rect panel = layout.panel();
        int x = layout.left();
        int width = layout.bodyWidth();
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, layout.titleY(), width, LABEL);
        GuiText.drawCentered(graphics, this.font, header(), panel.x() + panel.width() / 2, layout.headerY(), width, DIM);
        int y = layout.bodyTop();
        switch (tab) {
            case OVERVIEW -> renderOverview(graphics, x, y, width);
            case STANDINGS -> renderStandings(graphics, x, y, width);
            case PAIRINGS -> renderPairings(graphics, x, y, width);
            case HOST -> hostHelp().ifPresent(help -> paragraph(graphics, help,
                    x, hostHelpTop, width, DIM));
        }
    }

    /**
     * What the host should do next, for the phase the event is in.
     * <p>Matched against the phases this screen knows rather than concatenated into a key. It was
     * built by joining a string that came off the wire, per frame - so a phase this build does not
     * recognize drew the raw translation key as the panel's text, and every frame of the host tab
     * allocated a key and a component to say the same sentence again.
     */
    private java.util.Optional<Component> hostHelp() {
        String phase = view.phase();
        for (String known : HOST_HELP_FOR) {
            if (known.equals(phase)) {
                return java.util.Optional.of(
                        Component.translatable("screen.gathering.event.host_help." + known));
            }
        }
        return java.util.Optional.empty();
    }

    /** The phases there is advice for, which is every phase the server can report. */
    private static final String[] HOST_HELP_FOR = {
        "signup", "check_in", "preparing", "swiss", "cut", "finished", "cancelled",
    };

    /** Writing over as many lines as it needs at the text size asked, stopping above the bottom row. */
    private void paragraph(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        int wrapAt = Math.max(1, Math.round(width / GuiText.askedScale()));
        for (FormattedCharSequence row : this.font.split(text, wrapAt)) {
            if (y + layout.line() > layout.bodyBottom()) {
                return;
            }
            StringBuilder words = new StringBuilder();
            row.accept((index, style, point) -> {
                words.appendCodePoint(point);
                return true;
            });
            GuiText.draw(graphics, this.font, Component.literal(words.toString()), x, y, width, color);
            y += layout.line();
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
        int line = layout.line();
        EventViewPayload.Mine mine = view.mine();
        if (reporting) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.you.table", mine.table(),
                    mine.opponent()), x, y, width, LABEL);
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.you.report"), x, y + line, width, WARN);
            return;
        }
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.settings_line", view.host(),
                view.bestOf(), view.roundMinutes(), view.topCut() == dev.gathering.core.tournament.EventSettings.AUTO_CUT
                        ? Component.translatable("screen.gathering.event.top_cut_by_players")
                        : view.topCut() == 0 ? Component.translatable("screen.gathering.event.no_top_cut")
                        : Component.translatable("screen.gathering.event.top_cut." + view.topCut()),
                Component.translatable("screen.gathering.event.decks." + view.decks())), x, y, width, DIM);
        y += line;
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
        y += line;
        if (mine.table() == 0) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.you.bye"), x, y, width, GOOD);
        } else if (mine.table() > 0) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.you.table", mine.table(),
                    mine.opponent()), x, y, width, LABEL);
            y += line;
            Component said = !mine.confirmed().isEmpty()
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
            GuiText.draw(graphics, this.font, said, x, y, width, mine.confirmed().isEmpty() ? WARN : GOOD);
        }
        int below = resultsTop(false);
        if (mine.table() > 0 && mine.confirmed().isEmpty() && resultsFit(below)) {
            below += layout.heightOfRows(layout.rowsFor(resultsForMe().size(), narrowestResult())) + layout.gap();
        }
        if (!view.places().isEmpty() && below + line <= layout.bodyBottom()) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.places",
                    String.join(", ", view.places().subList(0, Math.min(4, view.places().size())))), x, below, width, GOOD);
            below += line;
        }
        List<String> prizes = view.prizes();
        for (int index = 0; index < Math.min(4, prizes.size()) && below + line <= layout.bodyBottom(); index++) {
            GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.prize", prizes.get(index)),
                    x, below, width, DIM);
            below += line;
        }
    }

    /**
     * The standings as columns, each figure under its own heading.
     * <p>Laid out by position rather than by padding a string with spaces: this font is not
     * monospaced, so a name one letter wider pushed every number after it out of line.
     */
    private void renderStandings(GuiGraphics graphics, int x, int y, int width) {
        int[] rightEdges = standingsColumns(x, width);
        String[] heads = {"rank", "player", "points", "record", "omw", "gw", "ogw"};
        for (int column = 0; column < heads.length; column++) {
            cell(graphics, Component.translatable("screen.gathering.event.standings." + heads[column]), column, rightEdges, x, y, DIM);
        }
        y += layout.line();
        var me = Minecraft.getInstance().player;
        String myName = me == null ? "" : me.getGameProfile().getName();
        int from = page * perPage();
        for (int index = from; index < Math.min(view.standings().size(), from + perPage()); index++) {
            EventViewPayload.Row row = view.standings().get(index);
            int color = row.dropped() ? DIM : LABEL;
            if (row.name().equals(myName)) {
                // Your own line, found at a glance in a list of thirty-two. The theme's row highlight,
                // not a painted rectangle: nothing on screen is a color a theme cannot change.
                GatheringSprites.highlight(graphics, x - 2, y - 1, width + 4, layout.line() - 1);
            }
            String[] cells = {
                    Integer.toString(row.rank()), row.name(), Integer.toString(row.points()),
                    row.wins() + "-" + row.losses() + "-" + row.draws(),
                    percent(row.omw()), percent(row.gw()), percent(row.ogw())};
            for (int column = 0; column < cells.length; column++) {
                cell(graphics, Component.literal(cells[column]), column, rightEdges, x, y, color);
            }
            y += layout.line();
        }
    }

    /** Where each standings column ends: rank, then the name's wide column, then the figures. */
    private int[] standingsColumns(int x, int width) {
        float scale = GuiText.askedScale();
        int figure = Math.max(Math.round(28 * scale), width / 10);
        int rank = Math.round(this.font.width("88") * scale) + 4;
        int[] edges = new int[7];
        edges[6] = x + width;
        for (int column = 5; column >= 2; column--) {
            edges[column] = edges[column + 1] - figure - (column == 3 ? 6 : 0);
        }
        edges[0] = x + rank;
        edges[1] = edges[2] - figure;
        return edges;
    }

    /** One cell: the name column left-aligned and cut to fit, every other column right-aligned. */
    private void cell(GuiGraphics graphics, Component text, int column, int[] rightEdges, int x, int y, int color) {
        if (column == 1) {
            int left = rightEdges[0] + 6;
            GuiText.draw(graphics, this.font, text, left, y, Math.max(8, rightEdges[1] - left - 4), color);
            return;
        }
        int left = column == 0 ? x : rightEdges[column - 1];
        int room = Math.max(8, rightEdges[column] - left - 2);
        int drawn = GuiText.width(this.font, text, room);
        GuiText.draw(graphics, this.font, text, rightEdges[column] - drawn, y, room, color);
    }

    /** A tiebreaker percentage sent in tenths, as one decimal place. */
    private static String percent(int tenths) {
        return String.format(java.util.Locale.ROOT, "%.1f", tenths / 10.0);
    }

    private void renderPairings(GuiGraphics graphics, int x, int y, int width) {
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.event.pairings_head"), x, y, width, DIM);
        y += layout.line();
        int settleRoom = view.youHost()
                ? widthFor(Component.translatable("screen.gathering.event.select"), 40) + layout.gap() * 2 : 0;
        int shown = perPage();
        List<EventViewPayload.Match> matches;
        if (shown == 0) {
            EventViewPayload.Match chosen = chosenMatch();
            matches = chosen == null ? List.of() : List.of(chosen);
        } else {
            int from = page * shown;
            matches = view.pairings().subList(Math.min(from, view.pairings().size()),
                    Math.min(view.pairings().size(), from + shown));
        }
        for (EventViewPayload.Match match : matches) {
            Component line = match.table() == 0
                    ? Component.translatable("screen.gathering.event.pairing_bye", match.a())
                    : Component.translatable("screen.gathering.event.pairing", match.table(), match.a(), match.b(),
                            Component.translatable("screen.gathering.event.status." + match.status()), match.result());
            int color = switch (match.status()) {
                case "confirmed", "bye" -> GOOD;
                case "disputed" -> WARN;
                default -> LABEL;
            };
            // Clear of the host's Settle toggle, and in the middle of its taller row.
            int rise = Math.max(0, (listLine() - layout.line()) / 2);
            GuiText.draw(graphics, this.font, line, x, y + rise + 1, width - settleRoom, color);
            y += listLine();
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
