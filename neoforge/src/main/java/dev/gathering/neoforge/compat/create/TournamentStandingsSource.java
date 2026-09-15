package dev.gathering.neoforge.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.ValueListDisplaySource;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.server.events.EventBoard;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A tournament's standings, one player a line with their match points: "1. Alice (3-0-0)  9".
 * <p>Once the event has finished, its final places in order - the cut decides the top of those,
 * not the Swiss points - still with each player's points beside them.
 */
public class TournamentStandingsSource extends ValueListDisplaySource {

    @Override
    protected Stream<IntAttached<MutableComponent>> provideEntries(DisplayLinkContext context, int maxRows) {
        EventBoard.Board board = Boards.event(context).orElse(null);
        if (board == null) {
            return Stream.of(IntAttached.with(0, Boards.noEvent()));
        }
        List<IntAttached<MutableComponent>> lines = new ArrayList<>();
        if (board.phase() == Tournament.Phase.FINISHED && !board.places().isEmpty()) {
            for (int index = 0; index < board.places().size(); index++) {
                String name = board.places().get(index);
                int points = board.standings().stream().filter(row -> row.name().equals(name))
                        .mapToInt(EventBoard.Standing::points).findFirst().orElse(0);
                lines.add(IntAttached.with(points, Component.literal((index + 1) + ". " + name + " ")));
            }
        } else {
            for (EventBoard.Standing row : board.standings()) {
                lines.add(IntAttached.with(row.points(), Component.literal(row.rank() + ". " + row.name()
                        + " (" + row.wins() + "-" + row.losses() + "-" + row.draws() + ") ")));
            }
        }
        return lines.stream().limit(Math.max(1, maxRows));
    }

    @Override
    protected boolean valueFirst() {
        return false;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40;
    }
}
