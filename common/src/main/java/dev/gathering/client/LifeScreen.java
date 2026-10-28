package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.ui.LifeLayout;
import dev.gathering.core.ui.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * One seat's life, and the commander damage that comes off it.
 * <p>Commander damage used to sit under counters, which is where the owner found it and is not
 * what it is: a poison counter is a number beside a player and commander damage is <em>life</em>,
 * taken from a particular commander. Recording it here puts the two numbers on one panel, and
 * the event that writes one writes the other - see {@code SeatState.withCommanderDamage}.
 * <p>One row per commander and not per opponent, because the rule is twenty-one from the
 * <em>same</em> commander and a partner deck fields two: a single number per enemy seat could
 * not tell one partner's damage from the other's, which is the pair the rule exists to
 * separate. A seat that brought no commanders contributes no rows, so a table playing Modern
 * gets a life total and nothing else rather than a grid of zeroes nobody can use.
 * <p>Every change goes out as it is made. There is no confirm step, because a total that only
 * exists once you press OK is a total you can lose by closing a window.
 * <p><b>Nothing here decides anything.</b> Twenty-one is colored, because that is a fact about
 * the number; it is not refused, and neither is the twenty-second point. What has ended is for
 * the table to say.
 * <p>Client-only.
 */
public final class LifeScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int VALUE = 0xFFFFE9A8;
    private static final int LETHAL = 0xFFE06C6C;

    /** Taken from the layout, so the drawing and the arithmetic cannot drift apart. */
    private static final int MARGIN = LifeLayout.margin();
    private static final int GAP = LifeLayout.gap();
    private static final int STEP_WIDTH = 18;

    private final BlockPos table;
    private final SeatId seat;
    private final BoardCardNames names = new BoardCardNames();

    private Rect panel = Rect.NONE;

    /**
     * Where everything on this panel goes, worked out once per rebuild.
     * <p>Held rather than recomputed while drawing, because the buttons are built from it and a
     * panel whose drawing and buttons disagreed put one row's minus beside another row's name.
     */
    private LifeLayout layout;

    /**
     * Which enemy commanders the panel was built for, so {@link #tick()} can notice one
     * arriving.
     * <p>Somebody sitting down opposite adds a commander to take damage from, and this screen
     * is open for the length of a turn.
     */
    private List<CardInstanceId> builtOpponents = List.of();

    public LifeScreen(BlockPos table, SeatId seat, Screen back) {
        super(Component.translatable("screen.gathering.life"), back);
        this.table = table;
        this.seat = seat;
    }

    @Override
    protected void init() {
        List<CardInstanceId> opponents = commanderDamageFrom();
        builtOpponents = opponents;
        this.layout = LifeLayout.of(this.width, this.height, opponents.size());
        panel = layout.panel();

        steppers(layout.life(), () -> changeLife(-1), () -> changeLife(1));

        for (int index = 0; index < layout.damageRows(); index++) {
            CardInstanceId from = opponents.get(index);
            steppers(layout.damageRow(index), () -> hitBy(from, -1), () -> hitBy(from, 1));
        }

        // Every change here has already been sent by the time it is drawn, so this closes
        // rather than confirms - but a panel whose only exit is a key nobody was told about is
        // a dead end, and the sibling screens all offer the same button.
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
     * Changes this seat's life.
     * <p>Signed by whoever is pressing it, like every other move: the table lets anybody adjust
     * anybody's numbers, because on a real table the person who notices says so and whoever is
     * nearest the pad writes it down. Who did it is in the log.
     */
    private void changeLife(int delta) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            return;
        }
        ClientTableActions.send(table, new GameEvent.LifeChanged(me, seat, delta));
    }

    /**
     * Records commander damage this seat has taken from one commander.
     * <p>The life above it moves with this, in the same event, because commander damage is
     * damage. Taking it back gives the life back, so a misclick is undone with the gesture that
     * made it.
     */
    private void hitBy(CardInstanceId commander, int delta) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            return;
        }
        ClientTableActions.send(table,
                new GameEvent.CommanderDamageChanged(me, seat, commander, delta));
    }

    /**
     * Every other seat's commanders, in seat order, at a table that counts commander damage -
     * and nothing otherwise.
     * <p>A seat that brought no commanders contributes no rows.
     */
    private List<CardInstanceId> commanderDamageFrom() {
        if (!tableCountsCommanderDamage()) {
            return List.of();
        }
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null) {
            return List.of();
        }
        List<CardInstanceId> others = new ArrayList<>();
        for (SeatView view : board.seats()) {
            if (!view.seat().equals(seat)) {
                others.addAll(view.commanders());
            }
        }
        return others;
    }

    /** Whether the game on the table counts commander damage - Commander does, Oathbreaker does not. */
    private boolean tableCountsCommanderDamage() {
        return net.minecraft.client.Minecraft.getInstance().level != null
                && net.minecraft.client.Minecraft.getInstance().level
                        .getBlockEntity(table) instanceof dev.gathering.block.TableBlockEntity entity
                && entity.countsCommanderDamage();
    }

    /** This seat as the board has it, or null if the board no longer has such a seat. */
    private SeatView mine() {
        GameView board = ClientTableState.viewOf(table).orElse(null);
        if (board == null) {
            return null;
        }
        for (SeatView view : board.seats()) {
            if (view.seat().equals(seat)) {
                return view;
            }
        }
        return null;
    }

    /** How much commander damage this seat has taken from that commander. */
    private int damageFrom(CardInstanceId commander) {
        SeatView view = mine();
        return view == null ? 0 : view.commanderDamage().getOrDefault(commander, 0);
    }

    @Override
    public void tick() {
        super.tick();
        // The table closing, or this seat leaving the board entirely, leaves nothing to show.
        if (mine() == null) {
            this.onClose();
            return;
        }
        if (!commanderDamageFrom().equals(builtOpponents)) {
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
        SeatView view = mine();
        if (layout == null || view == null) {
            return;
        }
        GameView board = ClientTableState.viewOf(table).orElse(null);

        GuiText.drawCentered(graphics, this.font, dev.gathering.SeatNames.of(view),
                panel.x() + panel.width() / 2, panel.y() + 4, panel.width() - MARGIN * 2, LABEL);

        Rect life = layout.life();
        GuiText.draw(graphics, this.font, Component.translatable("screen.gathering.life.total"),
                life.x(), life.y() + 5, life.width() - 60, LABEL);
        GuiText.draw(graphics, this.font, Component.literal(Integer.toString(view.life())),
                life.right() - STEP_WIDTH * 2 - GAP - 24, life.y() + 5, 22, VALUE);

        if (layout.damage().isEmpty()) {
            return;
        }
        GuiText.draw(graphics, this.font,
                heading(builtOpponents.size() - layout.damageRows()),
                layout.damage().x(), layout.damage().y() + 5, layout.damage().width(), DIM);
        for (int row = 0; row < layout.damageRows(); row++) {
            CardInstanceId from = builtOpponents.get(row);
            int taken = damageFrom(from);
            Rect at = layout.damageRow(row);
            GuiText.draw(graphics, this.font, names.of(board, from),
                    at.x(), at.y() + 5, at.width() - 60, LABEL);
            // Twenty-one is a fact about the number, not a thing the mod does about it.
            GuiText.draw(graphics, this.font, Component.literal(Integer.toString(taken)),
                    at.right() - STEP_WIDTH * 2 - GAP - 24, at.y() + 5, 22,
                    dev.gathering.core.game.LossReminders.commanderDamageIsAtALoss(taken)
                            ? LETHAL : VALUE);
        }
    }

    /**
     * The damage grid's heading, which says so when the grid could not show every row.
     * <p>The grid has no wheel of its own and nowhere to put a line under it, so what is missing
     * is said in the heading rather than not said. It takes a window at the smallest size
     * Minecraft allows and a table fielding several enemy commanders before this ever reads
     * anything but the plain heading.
     */
    private static Component heading(int hidden) {
        Component plain = Component.translatable("screen.gathering.life.commander_damage");
        return hidden <= 0
                ? plain
                : Component.translatable("screen.gathering.counters.grid_more", plain, hidden);
    }

    /** How many commander rows the panel made room for. For the scripted run. */
    int damageRowsLaidOut() {
        return layout == null ? 0 : layout.damageRows();
    }

    /**
     * Where one commander's row is, for the scripted run to press its plus.
     * <p>Asked of the layout the buttons were built from, because the life row's plus comes
     * first on this panel and a run that pressed the first plus it found moved the life total
     * and never touched the damage it was there to record.
     */
    Rect damageRow(int row) {
        return layout == null || row < 0 || row >= layout.damageRows() ? Rect.NONE : layout.damageRow(row);
    }
}
