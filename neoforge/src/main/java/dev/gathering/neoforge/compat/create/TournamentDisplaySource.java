package dev.gathering.neoforge.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.server.events.EventBoard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A tournament, read off the Scorekeeper's Desk that runs it, onto whatever the Display Link writes to.
 * <p>One source with a choice rather than a source for every view, chosen in the Display Link's own
 * settings the way Create's gauges offer their modes: the standings, this round's pairings, the round
 * and its clock, the final places, the prizes put up, or who has signed up. Public results only, as the
 * event screen shows them.
 */
public class TournamentDisplaySource extends DisplaySource {

    /** What a board shows, in the order the setting offers them. Its ordinal is what the link keeps. */
    public enum Show {
        STANDINGS, PAIRINGS, ROUND, PLACES, PRIZES, SIGNED_UP;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final String SHOW = "Show";

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        EventBoard.Board board = Boards.desk(context).orElse(null);
        if (board == null) {
            return List.of(Component.translatable("display.gathering.desk_runs_nothing"));
        }
        int rows = Math.max(1, stats.maxRows());
        // A display board's lines are as wide as the board: the name is the part worth the room.
        boolean narrow = context.getTargetBlockEntity() instanceof FlapDisplayBlockEntity;
        List<MutableComponent> lines = switch (showing(context)) {
            case STANDINGS -> standings(board, narrow);
            case PAIRINGS -> pairings(board, narrow);
            case ROUND -> List.of(round(board));
            case PLACES -> places(board);
            case PRIZES -> board.prizes().isEmpty()
                    ? List.of(Component.translatable("display.gathering.no_prizes"))
                    : board.prizes().stream().map(prize -> (MutableComponent) Component.literal(prize)).toList();
            case SIGNED_UP -> signedUp(board);
        };
        return lines.size() <= rows ? lines : lines.subList(0, rows);
    }

    static Show showing(DisplayLinkContext context) {
        int chosen = context.sourceConfig().getInt(SHOW);
        return Show.values()[Math.floorMod(chosen, Show.values().length)];
    }

    private static List<MutableComponent> standings(EventBoard.Board board, boolean narrow) {
        if (board.round() == 0) {
            return List.of(Component.translatable(board.players() == 0 ? "display.gathering.nobody_yet" : "display.gathering.no_rounds_yet"));
        }
        List<MutableComponent> lines = new ArrayList<>();
        for (EventBoard.Standing row : board.standings()) {
            lines.add(Component.literal(row.rank() + ". " + row.name()
                    + (narrow ? "" : " (" + row.wins() + "-" + row.losses() + "-" + row.draws() + ")") + "  " + row.points()));
        }
        return lines;
    }

    private static List<MutableComponent> pairings(EventBoard.Board board, boolean narrow) {
        if (board.pairings().isEmpty()) {
            return List.of(Component.translatable("display.gathering.no_pairings"));
        }
        List<MutableComponent> lines = new ArrayList<>();
        for (EventBoard.Match match : board.pairings()) {
            lines.add(match.isBye()
                    ? Component.translatable("display.gathering.bye", match.first())
                    : narrow
                            ? Component.literal(match.table() + ". " + match.first() + " - " + match.second()
                                    + (match.result().isEmpty() ? "" : " " + match.result()))
                            : Component.translatable("display.gathering.pairing", match.table(), match.first(), match.second(),
                                    match.result()));
        }
        return lines;
    }

    static MutableComponent round(EventBoard.Board board) {
        String clock = Boards.clock(board.secondsLeft());
        return switch (board.phase()) {
            case SIGNUP -> Component.translatable("display.gathering.round.signup", board.name());
            case CHECK_IN -> Component.translatable("display.gathering.round.check_in", board.name());
            case PREPARING -> Component.translatable("display.gathering.round.preparing", clock);
            case SWISS -> Component.translatable("display.gathering.round.swiss", board.round(), board.plannedRounds(), clock);
            case CUT -> Component.translatable("display.gathering.round.cut", clock);
            case FINISHED -> board.places().isEmpty()
                    ? Component.translatable("display.gathering.round.finished")
                    : Component.translatable("display.gathering.round.won_by", board.places().get(0));
            case CANCELLED -> Component.translatable("display.gathering.round.cancelled");
        };
    }

    private static List<MutableComponent> places(EventBoard.Board board) {
        if (board.phase() != Tournament.Phase.FINISHED || board.places().isEmpty()) {
            return List.of(Component.translatable("display.gathering.not_finished"));
        }
        List<MutableComponent> lines = new ArrayList<>();
        for (int index = 0; index < board.places().size(); index++) {
            lines.add(Component.literal((index + 1) + ". " + board.places().get(index)));
        }
        return lines;
    }

    private static List<MutableComponent> signedUp(EventBoard.Board board) {
        // Counted from the names listed, so the number over them is the number of them: nobody who dropped.
        List<MutableComponent> names = new ArrayList<>();
        for (EventBoard.Standing row : board.standings()) {
            if (!row.dropped()) {
                names.add(Component.literal(row.name()));
            }
        }
        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.translatable("display.gathering.signed_up", names.size()));
        lines.addAll(names);
        return lines;
    }

    @Override
    public int getPassiveRefreshTicks() {
        // Once a second, for the clock; the rest change no faster than players report.
        return 20;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        if (!isFirstLine) {
            return;
        }
        List<Component> options = new ArrayList<>();
        for (Show show : Show.values()) {
            options.add(Component.translatable("display.gathering.show." + show.key()));
        }
        builder.addSelectionScrollInput(0, 120, (input, label) -> input.forOptions(options)
                .titled(Component.translatable("display.gathering.show")), SHOW);
    }
}
