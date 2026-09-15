package dev.gathering.neoforge.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.gathering.server.events.EventBoard;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Where a tournament has got to, and its clock: "Round 2 of 4 34:12", "Top cut 5:03", "Won by Alice". */
public class TournamentRoundSource extends SingleLineDisplaySource {

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        EventBoard.Board board = Boards.event(context).orElse(null);
        if (board == null) {
            return Boards.noEvent();
        }
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

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        // A clock: once a second, rather than the five seconds a count of items gets.
        return 20;
    }
}
