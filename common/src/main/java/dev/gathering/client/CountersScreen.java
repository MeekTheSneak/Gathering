package dev.gathering.client;

import dev.gathering.core.ui.CounterText;
import dev.gathering.core.game.CardInstance;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.CommandSlots;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.item.CardComponent;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.ui.CountersLayout;
import dev.gathering.core.ui.Rect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Counters, on a card or beside a seat.
 * <p>One screen for both, because they are the same task with a different subject: a named
 * number that goes up and down. The verbs differ underneath - a card counter and a seat
 * counter are different events and the log has to say which.
 * <p>Named counters rather than a fixed set: a screen that only knows poison and energy needs
 * editing every time a set comes out. The common ones get a button, because typing "loyalty"
 * forty times a game is not a feature, and everything else gets the text field.
 * <p>Every change goes out as it is made. There is no confirm step, because a counter that
 * only exists once you press OK is a counter you can lose by closing a window.
 * <p>Client-only.
 */
public final class CountersScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int VALUE = 0xFFFFE9A8;

    /** Taken from the layout, so the drawing and the arithmetic cannot drift apart. */
    private static final int MARGIN = CountersLayout.margin();
    private static final int GAP = CountersLayout.gap();
    private static final int STEP_WIDTH = 18;

    /** Which counter is at the top of the list, when there are more than fit. */
    private int scroll;

    /**
     * How many counter rows this window shows.
     * <p>Usually {@link CountersLayout#MAX_ROWS}. Less on a short window or a crowded table,
     * because the rest of the panel - the commander damage grid, the tax grid, the way to add
     * one, the way out - is not optional and a panel that did not shrink for them drew its own
     * buttons off the bottom of the screen, where nothing could be pressed.
     */
    private int showing() {
        return layout == null ? 0 : layout.counterRows();
    }

    /**
     * Where everything on this panel goes, worked out once per rebuild.
     * <p>Held rather than recomputed while drawing, because the buttons are built from it and
     * a panel whose drawing and buttons disagreed put one row's minus beside another row's
     * name. Every input it reads - the counters, the named buttons, the opponents, the taxed
     * commanders - is watched by {@link #tick()}, which rebuilds when any of them moves.
     */
    private CountersLayout layout;

    /** A counter name long enough for anything real and short enough not to be a payload. */
    private static final int MAX_NAME = 24;

    /**
     * The number at which one commander's damage has killed somebody.
     * <p>Shown, never enforced. The mod does not end games and does not intend to; what it
     * does is what a life pad does, which is stop you counting to twenty-one in your head
     * three times a turn.
     */
    private static final int LETHAL_COMMANDER_DAMAGE = 21;

    private static final int LETHAL = 0xFFE06C6C;

    private final BlockPos table;
    private final Subject subject;

    private Rect panel = Rect.NONE;
    private EditBox customName;

    /**
     * What the panel was last laid out for, so the screen knows when it needs rebuilding.
     * <p>Set where the layout is built and nowhere else. Set while drawing they record what
     * the last frame drew rather than what the widgets were placed against, and a frame always
     * runs before the tick that compares them - so a counter arriving after the panel opened
     * is recorded as already known, never triggers a rebuild, and draws its row straight
     * through the button grid underneath.
     */
    private List<String> builtRows = List.of();

    /**
     * The counters as of the last board this screen saw, and which board that was.
     * <p>Worked out per board rather than per frame. {@link #current()} walks every card in
     * the subject, and for each one {@link #cardIn} streamed the whole board looking for it -
     * so a screen open over three selected cards on a Commander table streamed a few hundred
     * card views three times, sixty times a second, to draw a list of counters that changes
     * when somebody clicks. The view object is replaced whole every time the server sends
     * one, so comparing it by identity is exactly "has anything changed".
     */
    private GameView countedFrom;
    private Map<String, Integer> counted = Map.of();

    /** And which enemy commanders it was built to take damage from. */
    private List<CardInstanceId> builtOpponents = List.of();

    /** The commanders whose tax rows have widgets, to notice a new cast needing buttons. */
    private List<CardInstanceId> builtTaxed = List.of();

    /**
     * What the counters are on.
     * <p>A sealed pair rather than a nullable card and a nullable seat, so a screen with
     * neither - or somehow both - cannot be built.
     */
    public sealed interface Subject {

        /** Every card the change applies to. A selection on the table is one subject. */
        record Cards(List<CardInstanceId> cards, Component name) implements Subject {
        }

        record Seat(SeatId seat, Component name) implements Subject {
        }

        default Component name() {
            return switch (this) {
                case Cards cards -> cards.name();
                case Seat seat -> seat.name();
            };
        }
    }

    public CountersScreen(BlockPos table, Subject subject, Screen back) {
        super(Component.translatable("screen.gathering.counters"), back);
        this.table = table;
        this.subject = subject;
    }

    /**
     * The counters worth a button of their own: the usual suspects, plus whatever this table
     * has already named.
     * <p>A counter already on <em>this</em> card has its own row; the slow case is the second
     * card. Once anybody has put a "flying" counter on anything it is a button for everybody,
     * which also makes the table spell it one way - two players tracking "shield" and
     * "shields" is a bug nobody can see.
     * <p>Read off the board rather than remembered on this client, so it survives a relog and
     * arrives for the player who did not type it. Capped, because a table that has named
     * thirty things is no longer helped by all of them.
     */
    private List<String> common() {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == buttonsFrom && buttons != null) {
            return buttons;
        }
        buttonsFrom = board;
        buttons = buildCommon();
        return buttons;
    }

    private List<String> buildCommon() {
        List<String> shown = new ArrayList<>(switch (subject) {
            case Subject.Cards ignored -> List.of(
                    CardInstance.Counters.PLUS_ONE_PLUS_ONE,
                    CardInstance.Counters.MINUS_ONE_MINUS_ONE,
                    CardInstance.Counters.LOYALTY,
                    "charge",
                    "stun");
            case Subject.Seat ignored -> List.of(
                    SeatState.Counters.POISON,
                    SeatState.Counters.ENERGY,
                    SeatState.Counters.EXPERIENCE);
        });
        Map<String, Integer> already = current();
        for (String name : namedAtThisTable()) {
            if (shown.size() >= MOST_BUTTONS) {
                break;
            }
            // Not one already offered, and not one the subject has - that has its own row
            // above with the count on it, and a second button for it would be two ways to do
            // the same thing sitting one above the other.
            if (!shown.contains(name) && !already.containsKey(name)) {
                shown.add(name);
            }
        }
        return List.copyOf(shown);
    }

    /**
     * Every counter name anywhere at this table, cards and seats alike, in a settled order.
     * <p>Sorted rather than left in board order: these are buttons, and buttons that reorder
     * themselves as the game goes on are buttons nobody can learn the position of.
     */
    private List<String> namedAtThisTable() {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == namedFrom) {
            return named;
        }
        namedFrom = board;
        java.util.SortedSet<String> found = new java.util.TreeSet<>();
        if (board != null) {
            for (CardView card : board.allCardViews()) {
                found.addAll(card.counters().keySet());
            }
            board.seats().forEach(seat -> found.addAll(seat.counters().keySet()));
        }
        named = List.copyOf(found);
        return named;
    }

    /** How many counter buttons the panel will carry before it stops adding them. */
    private static final int MOST_BUTTONS = 9;

    /** The board the buttons were built from, so they are built once per board and not per frame. */
    private GameView buttonsFrom;

    private List<String> buttons;

    /** Which buttons were drawn last rebuild, so tick can notice the table naming a new counter. */
    private List<String> builtButtons = List.of();

    /** And how many counter rows it made room for, which is what the scripted run checks. */
    private int builtRowCount;

    /** The board the button list was built from, so it is built once per board and not once per frame. */
    private GameView namedFrom;

    private List<String> named = List.of();

    @Override
    protected void init() {
        List<String> present = new ArrayList<>(current().keySet());
        builtRows = List.copyOf(present);
        builtButtons = common();
        List<CardInstanceId> opponents = commanderDamageFrom();
        List<CardInstanceId> taxed = taxedCommanders();
        builtOpponents = opponents;
        builtTaxed = taxed;

        this.layout = CountersLayout.of(this.width, this.height,
                present.size(), builtButtons.size(), opponents.size(), taxed.size());
        // Clamped here rather than where the wheel turns, because the list also shortens
        // under it: taking the last counter off a scrolled list would otherwise leave the
        // panel looking at rows that are no longer there.
        this.scroll = Math.max(0, Math.min(this.scroll, present.size() - layout.counterRows()));
        builtRowCount = layout.counterRows();
        panel = layout.panel();

        // What is on it now, each with a minus and a plus.
        for (int index = 0; index < layout.counterRows(); index++) {
            String name = present.get(this.scroll + index);
            steppers(layout.counterRow(index), () -> change(name, -1), () -> change(name, 1));
        }

        // The ones worth a button. Adding one that is already there just adds another.
        List<String> common = builtButtons;
        for (int index = 0; index < layout.commonRows() * CountersLayout.BUTTON_COLUMNS
                && index < common.size(); index++) {
            String name = common.get(index);
            addRenderableWidget(GatheringButtons.of(layout.commonButton(index),
                    Component.literal(CounterText.name(name)), () -> change(name, 1)));
        }

        // Commander damage, one row per opponent, on a table that has commanders. A grid on
        // paper, which is what everybody uses because twenty-one from each of three people is
        // three numbers nobody can hold in their head for an hour.
        for (int index = 0; index < layout.damageRows(); index++) {
            CardInstanceId from = opponents.get(index);
            steppers(layout.damageRow(index), () -> hitBy(from, -1), () -> hitBy(from, 1));
        }

        // Commander tax, one row per commander here, on a table that has commanders. The same
        // shape as the damage grid above it and for the same reason: it is a number a player
        // has to keep for an hour and cannot hold in their head.
        for (int index = 0; index < layout.taxRows(); index++) {
            CardInstanceId commander = taxed.get(index);
            steppers(layout.taxRow(index),
                    () -> castCommander(commander, -1), () -> castCommander(commander, 1));
        }

        Rect field = layout.custom();
        customName = new EditBox(this.font,
                field.x(), field.y(), field.width() - 44 - GAP, field.height(),
                Component.translatable("screen.gathering.counters.custom"));
        customName.setMaxLength(MAX_NAME);
        customName.setHint(Component.translatable("screen.gathering.counters.custom"));
        addRenderableWidget(customName);
        addRenderableWidget(GatheringButtons.of(
                field.right() - 44, field.y(), 44, field.height(),
                Component.translatable("screen.gathering.counters.add"), this::addCustom));

        // Every change here has already been sent by the time it is drawn, so this closes
        // rather than confirms - but a panel whose only exit is a key nobody was told about
        // is a dead end, and the sibling screens all offer the same button.
        addRenderableWidget(GatheringButtons.of(layout.done(),
                Component.translatable("gui.done"), this::onClose));
    }

    /** The minus and plus at the right-hand end of a row, which every grid here has. */
    private void steppers(Rect row, Runnable down, Runnable up) {
        addRenderableWidget(GatheringButtons.of(
                row.right() - STEP_WIDTH * 2 - GAP, row.y(), STEP_WIDTH, row.height(),
                Component.literal("-"), down));
        addRenderableWidget(GatheringButtons.of(
                row.right() - STEP_WIDTH, row.y(), STEP_WIDTH, row.height(),
                Component.literal("+"), up));
    }

    /**
     * Records commander damage this seat has taken from one opponent.
     * <p>Signed by whoever is pressing it, like every other move: the table lets anybody
     * adjust anybody's numbers, because on a real table the person who notices says so and
     * whoever is nearest the pad writes it down.
     */
    private void hitBy(CardInstanceId commander, int delta) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || !(subject instanceof Subject.Seat mine)) {
            return;
        }
        ClientTableActions.send(table,
                new GameEvent.CommanderDamageChanged(me, mine.seat(), commander, delta));
    }

    private void addCustom() {
        String name = customName.getValue().trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return;
        }
        change(name, 1);
        customName.setValue("");
    }

    /**
     * Sends the change, then rebuilds so a counter that has just appeared gets its own row.
     * <p>Rebuilding on the next frame rather than now: the board it reads has to come back
     * from the server first, and reading it immediately would draw the screen as it was.
     */
    private void change(String name, int delta) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            return;
        }
        switch (subject) {
            case Subject.Cards cards -> {
                for (CardInstanceId card : cards.cards()) {
                    ClientTableActions.send(table, new GameEvent.CounterChanged(me, card, name, delta));
                }
            }
            case Subject.Seat seat ->
                    ClientTableActions.send(table,
                            new GameEvent.SeatCounterChanged(me, seat.seat(), name, delta));
        }
    }

    /**
     * Every other seat's commanders, in seat order, when this screen is about a seat at a
     * table with a command zone - and nothing otherwise.
     * <p>One row per commander and not per opponent, because the rule is twenty-one from the
     * <em>same</em> commander and a partner deck fields two: a single number per enemy seat
     * could not tell one partner's damage from the other's, which is the pair the rule
     * exists to separate. A seat that brought no commanders contributes no rows, so a table
     * playing Modern gets no grid rather than a grid of zeroes nobody can use.
     */
    private List<CardInstanceId> commanderDamageFrom() {
        if (!(subject instanceof Subject.Seat mine) || !tableHasACommandZone()) {
            return List.of();
        }
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null) {
            return List.of();
        }
        List<CardInstanceId> others = new ArrayList<>();
        for (SeatView seat : board.seats()) {
            if (!seat.seat().equals(mine.seat())) {
                others.addAll(seat.commanders());
            }
        }
        return others;
    }

    /** Asked of the block, which is where the table keeps what kind of game it is running. */
    private boolean tableHasACommandZone() {
        return net.minecraft.client.Minecraft.getInstance().level != null
                && net.minecraft.client.Minecraft.getInstance().level
                        .getBlockEntity(table) instanceof dev.gathering.block.TableBlockEntity entity
                && entity.hasCommandZone();
    }

    /** How much commander damage this seat has taken from that commander. */
    private int damageFrom(CardInstanceId commander) {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null || !(subject instanceof Subject.Seat mine)) {
            return 0;
        }
        return board.seat(mine.seat()).commanderDamage().getOrDefault(commander, 0);
    }

    /**
     * How many commander taxes this screen drew last frame. For the scripted harness.
     * <p>Counted while drawing rather than worked out again. Asked of the same list the
     * drawing walks it would answer for a panel that had decided not to draw at all - which
     * is the shape of check that stays green over a live fault, and this run has had three.
     */
    int taxRowsShowing() {
        return taxRowsDrawn;
    }

    /** Set by {@link #renderCommanderTax}, so the hook above reports rather than predicts. */
    private int taxRowsDrawn;

    /**
     * The cards here that a commander tax applies to.
     * <p>A card counts as a commander while it is in somebody's command zone, and goes on
     * counting once it has a tax recorded - because a commander that has been cast is on the
     * battlefield, which is exactly when its owner wants to see what the next one costs.
     * There is no permanent mark on a card saying it is a commander; where it started is all
     * the game knows, and this asks the two questions that follow from that.
     */
    private List<CardInstanceId> taxedCommanders() {
        if (!(subject instanceof Subject.Cards chosen) || !tableHasACommandZone()) {
            return List.of();
        }
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null) {
            return List.of();
        }
        List<CardInstanceId> found = new ArrayList<>();
        for (CardInstanceId card : chosen.cards()) {
            if (ownerOfCommander(board, card) != null) {
                found.add(card);
            }
        }
        return found;
    }

    /** Whose commander this is, or null if it is not one. */
    private static SeatId ownerOfCommander(GameView board, CardInstanceId card) {
        for (SeatView seat : board.seats()) {
            if (seat.commanderTax().containsKey(card)) {
                return seat.seat();
            }
            // Both slots. A deck with partners has a commander in each, and asking only the
            // first would leave the second one's tax with nowhere to be written down.
            for (Zone slot : Zone.COMMAND_SLOTS) {
                // The map rather than the accessor, so the guard below is a guard rather
                // than a line that can never run: asking a seat for a zone it has not got
                // throws by design.
                ZoneView command = seat.zones().get(slot);
                if (command == null) {
                    continue;
                }
                for (CardView held : command.cards()) {
                    if (held instanceof CardView.Visible visible && visible.id().equals(card)) {
                        return seat.seat();
                    }
                }
            }
        }
        return null;
    }

    /** How many times this commander has been cast out of the command zone. */
    private int castsOf(CardInstanceId card) {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        SeatId owner = board == null ? null : ownerOfCommander(board, card);
        return owner == null ? 0 : board.seat(owner).commanderTax().getOrDefault(card, 0);
    }

    /**
     * Records another cast of a commander, or takes one back.
     * <p>Counted in casts rather than in mana, because that is what the rule is about and the
     * mana falls out of it: two more for each cast that came before. Undoing a miscount has to
     * work as well as recording one, so the same row does both.
     */
    private void castCommander(CardInstanceId card, int delta) {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        SeatId owner = board == null ? null : ownerOfCommander(board, card);
        if (me == null || owner == null || castsOf(card) + delta < 0) {
            return;
        }
        ClientTableActions.send(table, new GameEvent.CommanderTaxChanged(me, owner, card, delta));
    }

    /** Whose damage it is, for a row that has to say who is killing you. */
    private Component nameOf(SeatId seat) {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        return board == null ? Component.empty() : titleForSeat(board, seat);
    }

    /**
     * The counters currently on the subject.
     * <p>For a selection of cards, the union of what they all have - so a row exists for every
     * counter anybody in the selection carries, and pressing minus on it takes one off each of
     * them that has one.
     */
    private Map<String, Integer> current() {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == countedFrom) {
            return counted;
        }
        countedFrom = board;
        counted = countersIn(board);
        return counted;
    }

    private Map<String, Integer> countersIn(GameView board) {
        if (board == null) {
            return Map.of();
        }
        Map<String, Integer> found = new LinkedHashMap<>();
        switch (subject) {
            case Subject.Cards cards -> {
                // The board walked once, not once per selected card. Ten cards selected used
                // to mean ten passes over every card view on the table.
                java.util.Set<CardInstanceId> wanted = java.util.Set.copyOf(cards.cards());
                for (CardView card : board.allCardViews()) {
                    if (card instanceof CardView.Visible visible && wanted.contains(visible.id())) {
                        card.counters().forEach((name, count) ->
                                found.merge(name, count, Integer::sum));
                    }
                }
            }
            case Subject.Seat seat -> found.putAll(board.seat(seat.seat()).counters());
        }
        return found;
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientTableState.viewOf(table).isEmpty()) {
            this.onClose();
            return;
        }
        // A counter that has just come into existence needs a row, and one that has just gone
        // needs to stop having one. So does an opponent: somebody sitting down opposite adds
        // a commander to take damage from, and this screen is open for the length of a turn.
        if (!List.copyOf(current().keySet()).equals(builtRows)
                || !commanderDamageFrom().equals(builtOpponents)
                // And a commander newly cast: its tax row is drawn regardless, but the
                // +/- buttons beside it only exist after a rebuild - a row with no way to
                // change it until some unrelated counter happened to change too.
                || !taxedCommanders().equals(builtTaxed)
                // And a counter name nobody at this table had used before, which becomes a
                // button - the panel is sized around how many there are, so one appearing
                // without a rebuild is a button drawn off the bottom of its own box.
                || !common().equals(builtButtons)) {
            rebuildWidgets();
        }
    }

    /**
     * The counters on screen right now, in order, at most as many as {@link #showing()}.
     * <p>One place, because the rows are drawn here and their plus and minus are built in
     * {@code init} - and a window those two disagreed about is a minus that takes a counter
     * off a different card than the one whose name is beside it.
     */
    private Map<String, Integer> shownRows(Map<String, Integer> counters) {
        Map<String, Integer> shown = new java.util.LinkedHashMap<>();
        int room = showing();
        int index = 0;
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            if (index >= scroll && shown.size() < room) {
                shown.put(entry.getKey(), entry.getValue());
            }
            index++;
        }
        return shown;
    }

    /** How many counters are below the window, which is what the hint at the foot says. */
    private int belowTheWindow() {
        return Math.max(0, current().size() - scroll - showing());
    }

    /** How many counter rows are on screen. For the scripted run. */
    int rowsOnScreen() {
        return shownRows(current()).size();
    }

    /**
     * How many counter rows the panel made room for. For the scripted run.
     * <p>Worth checking against {@link #rowsOnScreen()}: everything below the counter list is
     * positioned from this number, so a panel drawing more rows than it was built for is a
     * panel drawing them straight through its own button grid.
     */
    int rowsLaidOut() {
        return builtRowCount;
    }

    /** Whether this counter is one of the rows on screen. For the scripted run. */
    boolean isShowing(String name) {
        return shownRows(current()).containsKey(name);
    }

    /** How many counters are out of sight below. For the scripted run. */
    int moreBelow() {
        return belowTheWindow();
    }

    /**
     * The wheel moves the counter list, when there is more of it than fits.
     * <p>Anywhere on the panel rather than only over the six rows: this screen scrolls one
     * thing, and a wheel that only works inside a strip forty pixels tall is a wheel most
     * people conclude does nothing.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, current().size() - showing());
        if (most > 0) {
            int wanted = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY), most));
            if (wanted != scroll) {
                scroll = wanted;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Behind the widgets. Drawn in render(), the panel covers every button on the screen. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (layout == null) {
            return;
        }

        GuiText.drawCentered(graphics, this.font, subject.name(),
                panel.x() + panel.width() / 2, panel.y() + 4, panel.width() - MARGIN * 2, LABEL);

        Map<String, Integer> counters = current();
        int index = 0;
        for (Map.Entry<String, Integer> entry : shownRows(counters).entrySet()) {
            Rect row = layout.counterRow(index);
            GuiText.draw(graphics, this.font, Component.literal(entry.getKey()),
                    row.x(), row.y() + 5, row.width() - 60, LABEL);
            GuiText.draw(graphics, this.font, Component.literal(Integer.toString(entry.getValue())),
                    row.right() - STEP_WIDTH * 2 - GAP - 24, row.y() + 5, 22, VALUE);
            index++;
        }
        Rect under = layout.counterFooter();
        if (counters.isEmpty()) {
            GuiText.drawCentered(graphics, this.font,
                    Component.translatable("screen.gathering.counters.none"),
                    panel.x() + panel.width() / 2, layout.firstCounter().y() + 5,
                    panel.width() - MARGIN * 2, DIM);
        }
        // Said out loud, because a list that scrolls and gives no sign of it is a list
        // whose seventh row may as well not exist - which is what this used to be.
        int below = belowTheWindow();
        if (below > 0) {
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.counters.more", below),
                    under.x(), under.y() + 1, under.width(), DIM);
        }

        renderCommanderTax(graphics);
        if (layout.damage().isEmpty()) {
            return;
        }
        GuiText.draw(graphics, this.font,
                heading("screen.gathering.counters.commander_damage",
                        builtOpponents.size() - layout.damageRows()),
                layout.damage().x(), layout.damage().y() + 5, layout.damage().width(), DIM);
        for (int row = 0; row < layout.damageRows(); row++) {
            CardInstanceId from = builtOpponents.get(row);
            int taken = damageFrom(from);
            Rect at = layout.damageRow(row);
            GuiText.draw(graphics, this.font, nameOf(from),
                    at.x(), at.y() + 5, at.width() - 60, LABEL);
            // Twenty-one is a fact about the number, not a thing the mod does about it.
            GuiText.draw(graphics, this.font, Component.literal(Integer.toString(taken)),
                    at.right() - STEP_WIDTH * 2 - GAP - 24, at.y() + 5, 22,
                    taken >= LETHAL_COMMANDER_DAMAGE ? LETHAL : VALUE);
        }
    }

    /**
     * Commander tax, one row per commander in front of the player.
     * <p>Counted in casts and shown in mana, because casts are what the rule counts and mana
     * is what the player is about to pay. Two more for every cast that came before.
     */
    private void renderCommanderTax(GuiGraphics graphics) {
        taxRowsDrawn = 0;
        if (layout.tax().isEmpty()) {
            return;
        }
        GuiText.draw(graphics, this.font,
                heading("screen.gathering.counters.commander_tax",
                        builtTaxed.size() - layout.taxRows()),
                layout.tax().x(), layout.tax().y() + 5, layout.tax().width(), DIM);
        for (int row = 0; row < layout.taxRows(); row++) {
            CardInstanceId commander = builtTaxed.get(row);
            int casts = castsOf(commander);
            Rect at = layout.taxRow(row);
            GuiText.draw(graphics, this.font, nameOf(commander),
                    at.x(), at.y() + 5, at.width() - 60, LABEL);
            GuiText.draw(graphics, this.font,
                    Component.translatable("screen.gathering.counters.extra_mana",
                            CommandSlots.taxFor(casts)),
                    at.right() - STEP_WIDTH * 2 - GAP - 34, at.y() + 5, 32, VALUE);
            taxRowsDrawn++;
        }
    }

    /**
     * A grid's heading, which says so when the grid could not show every row.
     * <p>The commander grids have no wheel of their own and nowhere to put a line under them,
     * so what is missing is said in the heading rather than not said. It takes a window at the
     * smallest size Minecraft allows and a table fielding five or more enemy commanders before
     * this ever reads anything but the plain heading.
     */
    private Component heading(String key, int hidden) {
        return hidden <= 0
                ? Component.translatable(key)
                : Component.translatable("screen.gathering.counters.grid_more",
                        Component.translatable(key), hidden);
    }

    /**
     * What to call a card on a row, which is its name once this client knows it.
     * <p>Which card an id belongs to is asked once and remembered. It used to be asked every
     * frame by walking every card in every zone of every seat - libraries included, so four
     * hundred cards on a Commander table - to answer a question whose answer cannot change:
     * a card instance is one printing for its whole life. The name itself is still looked up
     * each frame, because that arrives from the cache whenever it arrives.
     */
    private Component nameOf(CardInstanceId card) {
        CardComponent known = printings.get(card);
        if (known == null) {
            GameView board = ClientTableState.viewOf(table).orElse(null);
            if (board == null) {
                return Component.empty();
            }
            for (CardView held : board.allCardViews()) {
                if (held instanceof CardView.Visible visible && visible.id().equals(card)) {
                    known = CardComponent.of(visible.identity());
                    printings.put(card, known);
                    break;
                }
            }
            if (known == null) {
                return Component.empty();
            }
        }
        return ClientCardCache.get().summary(known)
                .map(summary -> (Component) Component.literal(summary.name()))
                .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
    }

    /** Which printing each card on a row is, found once - it cannot change. */
    private final java.util.Map<CardInstanceId, CardComponent> printings =
            new java.util.HashMap<>();

    /** The label for a card menu opening this, which has to name what it will act on. */
    public static Component titleFor(List<CardInstanceId> cards, Component single) {
        return cards.size() == 1
                ? single
                : Component.translatable("screen.gathering.counters.several", cards.size());
    }

    /** The label for a seat, which is whoever is in it. */
    public static Component titleForSeat(GameView board, SeatId seat) {
        return dev.gathering.SeatNames.of(board.seat(seat));
    }

}
