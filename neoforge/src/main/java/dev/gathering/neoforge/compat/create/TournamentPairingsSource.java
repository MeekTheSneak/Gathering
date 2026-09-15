package dev.gathering.neoforge.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.gathering.server.events.EventBoard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** A tournament's pairings this round, one table a line: "Table 1: Alice vs Bob 2-1". */
public class TournamentPairingsSource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        EventBoard.Board board = Boards.event(context).orElse(null);
        if (board == null) {
            return List.of(Boards.noEvent());
        }
        if (board.pairings().isEmpty()) {
            return List.of(Component.translatable("display.gathering.no_pairings"));
        }
        List<MutableComponent> lines = new ArrayList<>();
        for (EventBoard.Match match : board.pairings()) {
            if (lines.size() >= Math.max(1, stats.maxRows())) {
                break;
            }
            lines.add(match.isBye()
                    ? Component.translatable("display.gathering.bye", match.first())
                    : Component.translatable("display.gathering.pairing", match.table(), match.first(), match.second(),
                            match.result()));
        }
        return lines;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40;
    }
}
