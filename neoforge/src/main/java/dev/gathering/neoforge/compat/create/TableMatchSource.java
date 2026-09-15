package dev.gathering.neoforge.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.gathering.server.events.EventBoard;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The tournament match at this very table: "Table 3: Alice vs Bob", and its result once confirmed. */
public class TableMatchSource extends SingleLineDisplaySource {

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

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40;
    }
}
