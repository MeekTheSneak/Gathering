package dev.gathering.neoforge.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.gathering.server.events.EventBoard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The tournament match at this very table: "Table 3: Alice vs Bob", and its result once confirmed. */
public class TableMatchSource extends SingleLineDisplaySource {

    /** Fewer columns than this is too narrow for "Table 12: Alexandra vs Bartholomew" on one line. */
    static final int NARROW = 30;

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        EventBoard.Board board = Boards.event(context).orElse(null);
        if (board == null) {
            return Boards.noEvent();
        }
        return board.match()
                .map(match -> match.isBye()
                        ? Component.translatable("display.gathering.bye", match.first())
                        : Component.translatable("display.gathering.pairing", match.table(), match.first(), match.second(),
                                match.result()))
                .orElseGet(() -> Component.translatable("display.gathering.no_match_here"));
    }

    /**
     * On something narrow and a few lines tall - a sign - the match goes one part a line, since on one
     * line a sign shows "Table 1: Alice vs" and nothing of who Alice is playing. Unless a label was
     * given, which asks for the one line it heads.
     */
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        EventBoard.Board board = Boards.event(context).orElse(null);
        EventBoard.Match match = board == null ? null : board.match().orElse(null);
        boolean narrow = stats.maxRows() >= 3 && stats.maxColumns() < NARROW;
        if (match == null || !narrow || !context.sourceConfig().getString("Label").isEmpty()) {
            return super.provideText(context, stats);
        }
        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.translatable("display.gathering.table_number", match.table()));
        lines.add(Component.literal(match.first()));
        lines.add(match.isBye()
                ? Component.translatable("display.gathering.has_a_bye")
                : Component.translatable("display.gathering.versus", match.second()));
        if (!match.result().isEmpty()) {
            lines.add(Component.literal(match.result()));
        }
        return lines.size() <= stats.maxRows() ? lines : lines.subList(0, stats.maxRows());
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40;
    }
}
