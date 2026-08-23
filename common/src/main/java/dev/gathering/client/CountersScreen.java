package dev.gathering.client;

import dev.gathering.core.game.CardInstance;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
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
 *
 * <p>One screen for both because they are the same task with a different subject: a named
 * number that goes up and down. The verbs differ underneath - a card counter and a player
 * counter are different events, and the log has to say which - but a player putting a third
 * loyalty on a planeswalker and a player taking their fourth poison are doing the same thing
 * with their hands.
 *
 * <p>Named counters, not a fixed set of three. Magic has added two new ones in the last three
 * years and will add more; a screen that only knows about poison and energy is a screen that
 * needs editing every time a set comes out. The common ones get a button because typing
 * "loyalty" forty times a game is not a feature, and everything else gets the text field.
 *
 * <p>Every change goes out as it is made. There is no confirm step, because a counter that
 * only exists once you press OK is a counter you can lose by closing a window.
 *
 * <p>Client-only.
 */
public final class CountersScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int VALUE = 0xFFFFE9A8;

    private static final int PANEL_WIDTH = 216;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;
    private static final int STEP_WIDTH = 18;

    /** How many named counters fit before the list scrolls. Nothing has ever needed more. */
    private static final int MAX_ROWS = 6;

    /** A counter name long enough for anything real and short enough not to be a payload. */
    private static final int MAX_NAME = 24;

    /**
     * The number at which one commander's damage has killed somebody.
     *
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

    /** Which rows were drawn last frame, so the screen knows when it needs rebuilding. */
    private List<String> rowsShown = List.of();

    /** And which opponents there were to take commander damage from, for the same reason. */
    private List<SeatId> opponentsShown = List.of();

    /**
     * What the counters are on.
     *
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

    /** The counters worth a button of their own, which differ for a card and for a player. */
    private List<String> common() {
        return switch (subject) {
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
        };
    }

    @Override
    protected void init() {
        List<String> present = new ArrayList<>(current().keySet());
        int rows = Math.min(MAX_ROWS, present.size());
        int commonRows = (common().size() + 2) / 3;
        List<SeatId> opponents = commanderDamageFrom();

        int height = MARGIN * 2 + ROW * 2
                + rows * (ROW + GAP)
                + commonRows * (ROW + GAP)
                + (opponents.isEmpty() ? 0 : (opponents.size() + 1) * (ROW + GAP))
                + ROW + GAP * 3;
        panel = new Rect(
                (this.width - PANEL_WIDTH) / 2,
                Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH,
                Math.min(height, this.height - MARGIN * 2));

        int y = panel.y() + MARGIN + ROW;

        // What is on it now, each with a minus and a plus.
        for (int index = 0; index < rows; index++) {
            String name = present.get(index);
            int rowY = y + index * (ROW + GAP);
            addRenderableWidget(GatheringButtons.of(
                    panel.right() - MARGIN - STEP_WIDTH * 2 - GAP, rowY, STEP_WIDTH, ROW,
                    Component.literal("-"), () -> change(name, -1)));
            addRenderableWidget(GatheringButtons.of(
                    panel.right() - MARGIN - STEP_WIDTH, rowY, STEP_WIDTH, ROW,
                    Component.literal("+"), () -> change(name, 1)));
        }

        // The ones worth a button. Adding one that is already there just adds another.
        int commonTop = y + rows * (ROW + GAP) + ROW;
        int width = (panel.width() - MARGIN * 2 - GAP * 2) / 3;
        List<String> common = common();
        for (int index = 0; index < common.size(); index++) {
            String name = common.get(index);
            addRenderableWidget(GatheringButtons.of(
                    panel.x() + MARGIN + (index % 3) * (width + GAP),
                    commonTop + (index / 3) * (ROW + GAP), width, ROW,
                    Component.literal(shortLabel(name)), () -> change(name, 1)));
        }

        // Commander damage, one row per opponent, on a table that has commanders. A grid on
        // paper, which is what everybody uses because twenty-one from each of three people is
        // three numbers nobody can hold in their head for an hour.
        int damageTop = commonTop + commonRows * (ROW + GAP) + GAP;
        for (int index = 0; index < opponents.size(); index++) {
            SeatId from = opponents.get(index);
            int rowY = damageTop + ROW + index * (ROW + GAP);
            addRenderableWidget(GatheringButtons.of(
                    panel.right() - MARGIN - STEP_WIDTH * 2 - GAP, rowY, STEP_WIDTH, ROW,
                    Component.literal("-"), () -> hitBy(from, -1)));
            addRenderableWidget(GatheringButtons.of(
                    panel.right() - MARGIN - STEP_WIDTH, rowY, STEP_WIDTH, ROW,
                    Component.literal("+"), () -> hitBy(from, 1)));
        }

        int customTop = damageTop
                + (opponents.isEmpty() ? 0 : (opponents.size() + 1) * (ROW + GAP));
        customName = new EditBox(this.font,
                panel.x() + MARGIN, customTop, panel.width() - MARGIN * 2 - 44 - GAP, ROW,
                Component.translatable("screen.gathering.counters.custom"));
        customName.setMaxLength(MAX_NAME);
        customName.setHint(Component.translatable("screen.gathering.counters.custom"));
        addRenderableWidget(customName);
        addRenderableWidget(GatheringButtons.of(
                panel.right() - MARGIN - 44, customTop, 44, ROW,
                Component.translatable("screen.gathering.counters.add"), this::addCustom));
    }

    /**
     * Records commander damage this seat has taken from one opponent.
     *
     * <p>Signed by whoever is pressing it, like every other move: the table lets anybody
     * adjust anybody's numbers, because on a real table the person who notices says so and
     * whoever is nearest the pad writes it down.
     */
    private void hitBy(SeatId from, int delta) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || !(subject instanceof Subject.Seat mine)) {
            return;
        }
        ClientTableActions.send(table,
                new GameEvent.CommanderDamageChanged(me, mine.seat(), from, delta));
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
     *
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
     * The other seats, in seat order, when this screen is about a seat at a table with a
     * command zone - and nothing otherwise.
     *
     * <p>Commander damage is per opponent and only exists where there are commanders, so a
     * table playing Modern gets no grid rather than a grid of zeroes nobody can use.
     */
    private List<SeatId> commanderDamageFrom() {
        if (!(subject instanceof Subject.Seat mine) || !tableHasACommandZone()) {
            return List.of();
        }
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null) {
            return List.of();
        }
        List<SeatId> others = new ArrayList<>();
        for (SeatView seat : board.seats()) {
            if (!seat.seat().equals(mine.seat()) && seat.occupant().isPresent()) {
                others.add(seat.seat());
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

    /** How much commander damage this seat has taken from that one. */
    private int damageFrom(SeatId from) {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null || !(subject instanceof Subject.Seat mine)) {
            return 0;
        }
        return board.seat(mine.seat()).commanderDamage().getOrDefault(from, 0);
    }

    /** Whose damage it is, for a row that has to say who is killing you. */
    private Component nameOf(SeatId seat) {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        return board == null ? Component.empty() : titleForSeat(board, seat);
    }

    /**
     * The counters currently on the subject.
     *
     * <p>For a selection of cards, the union of what they all have - so a row exists for every
     * counter anybody in the selection carries, and pressing minus on it takes one off each of
     * them that has one.
     */
    private Map<String, Integer> current() {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null) {
            return Map.of();
        }
        Map<String, Integer> found = new LinkedHashMap<>();
        switch (subject) {
            case Subject.Cards cards -> {
                for (CardInstanceId id : cards.cards()) {
                    cardIn(board, id).ifPresent(card ->
                            card.counters().forEach((name, count) -> found.merge(name, count, Integer::sum)));
                }
            }
            case Subject.Seat seat -> found.putAll(board.seat(seat.seat()).counters());
        }
        return found;
    }

    private static Optional<CardView> cardIn(GameView board, CardInstanceId id) {
        return board.allCardViews().stream()
                .filter(CardView.Visible.class::isInstance)
                .map(CardView.Visible.class::cast)
                .filter(visible -> visible.id().equals(id))
                .map(CardView.class::cast)
                .findFirst();
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
        if (!new ArrayList<>(current().keySet()).equals(rowsShown)
                || !commanderDamageFrom().equals(opponentsShown)) {
            rebuildWidgets();
        }
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

        GuiText.drawCentred(graphics, this.font, subject.name(),
                panel.x() + panel.width() / 2, panel.y() + 4, panel.width() - MARGIN * 2, LABEL);

        Map<String, Integer> counters = current();
        rowsShown = new ArrayList<>(counters.keySet());
        int y = panel.y() + MARGIN + ROW;
        int index = 0;
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            if (index >= MAX_ROWS) {
                break;
            }
            int rowY = y + index * (ROW + GAP);
            GuiText.draw(graphics, this.font, Component.literal(entry.getKey()),
                    panel.x() + MARGIN, rowY + 5, panel.width() - MARGIN * 2 - 60, LABEL);
            GuiText.draw(graphics, this.font, Component.literal(Integer.toString(entry.getValue())),
                    panel.right() - MARGIN - STEP_WIDTH * 2 - GAP - 24, rowY + 5, 22, VALUE);
            index++;
        }
        if (counters.isEmpty()) {
            GuiText.drawCentred(graphics, this.font,
                    Component.translatable("screen.gathering.counters.none"),
                    panel.x() + panel.width() / 2, y + 5, panel.width() - MARGIN * 2, DIM);
        }

        List<SeatId> opponents = commanderDamageFrom();
        opponentsShown = opponents;
        if (opponents.isEmpty()) {
            return;
        }
        int commonRows = (common().size() + 2) / 3;
        int damageTop = y + Math.min(MAX_ROWS, counters.size()) * (ROW + GAP) + ROW
                + commonRows * (ROW + GAP) + GAP;
        GuiText.draw(graphics, this.font,
                Component.translatable("screen.gathering.counters.commander_damage"),
                panel.x() + MARGIN, damageTop + 5, panel.width() - MARGIN * 2, DIM);
        for (int row = 0; row < opponents.size(); row++) {
            SeatId from = opponents.get(row);
            int taken = damageFrom(from);
            int rowY = damageTop + ROW + row * (ROW + GAP);
            GuiText.draw(graphics, this.font, nameOf(from),
                    panel.x() + MARGIN, rowY + 5, panel.width() - MARGIN * 2 - 60, LABEL);
            // Twenty-one is a fact about the number, not a thing the mod does about it.
            GuiText.draw(graphics, this.font, Component.literal(Integer.toString(taken)),
                    panel.right() - MARGIN - STEP_WIDTH * 2 - GAP - 24, rowY + 5, 22,
                    taken >= LETHAL_COMMANDER_DAMAGE ? LETHAL : VALUE);
        }
    }

    /** "+1/+1" is wider than the button; the button says what it does in the room it has. */
    private static String shortLabel(String name) {
        return switch (name) {
            case CardInstance.Counters.PLUS_ONE_PLUS_ONE -> "+1/+1";
            case CardInstance.Counters.MINUS_ONE_MINUS_ONE -> "-1/-1";
            default -> name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        };
    }

    /** The label for a card menu opening this, which has to name what it will act on. */
    public static Component titleFor(List<CardInstanceId> cards, Component single) {
        return cards.size() == 1
                ? single
                : Component.translatable("screen.gathering.counters.several", cards.size());
    }

    /** The label for a seat, which is whoever is in it. */
    public static Component titleForSeat(GameView board, SeatId seat) {
        return board.seat(seat).occupant()
                .map(player -> Component.literal(player.name()))
                .map(Component.class::cast)
                .orElseGet(() -> Component.translatable("message.gathering.seat_empty"));
    }

}
