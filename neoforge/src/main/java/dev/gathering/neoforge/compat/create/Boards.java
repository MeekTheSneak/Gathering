package dev.gathering.neoforge.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import dev.gathering.server.TableBoard;
import dev.gathering.server.events.EventBoard;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/** What the display sources share: finding the table or desk a link reads, and saying a clock. */
final class Boards {

    private Boards() {
    }

    static Optional<EventBoard.Board> event(DisplayLinkContext context) {
        return context.level() instanceof ServerLevel level && context.getSourcePos() != null
                ? EventBoard.at(level, context.getSourcePos())
                : Optional.empty();
    }

    /** The tournament the Scorekeeper's Desk a link reads runs. */
    static Optional<EventBoard.Board> desk(DisplayLinkContext context) {
        return context.level() instanceof ServerLevel level && context.getSourcePos() != null
                ? EventBoard.atDesk(level, context.getSourcePos())
                : Optional.empty();
    }

    static List<TableBoard.Player> players(DisplayLinkContext context) {
        return context.level() instanceof ServerLevel level && context.getSourcePos() != null
                ? TableBoard.players(level, context.getSourcePos())
                : List.of();
    }

    /** "34:12", or blank when no clock is running. */
    static String clock(long secondsLeft) {
        if (secondsLeft < 0) {
            return "";
        }
        return (secondsLeft / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", secondsLeft % 60);
    }

    static MutableComponent noEvent() {
        return Component.translatable("display.gathering.no_event");
    }
}
