package dev.gathering.block;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The board, in words, for a player standing at the table.
 *
 * <p>A stand-in for the seated view, and a useful one: it is built from the same
 * visibility-filtered {@code GameView} a real client will be sent, so what it can say is
 * exactly what that client would be entitled to know. Reading the game state directly here
 * would be easier and would quietly make this the one place in the mod that can see
 * everybody's hand.
 */
public final class TableStatus {

    private TableStatus() {
    }

    public static List<Component> describe(GameView view) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("message.gathering.session_header")
                .withStyle(ChatFormatting.GOLD));

        for (SeatView seat : view.seats()) {
            lines.add(describeSeat(view, seat));
        }
        if (view.ended()) {
            lines.add(Component.translatable("message.gathering.session_over")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static Component describeSeat(GameView view, SeatView seat) {
        boolean mine = view.viewer() instanceof dev.gathering.core.game.visibility.Viewer.Seated seated
                && seated.seat().equals(seat.seat());

        String who = dev.gathering.SeatNames.of(seat).getString();

        Component line = Component.translatable(
                "message.gathering.session_seat",
                seat.seat().index() + 1,
                who,
                seat.life(),
                count(seat, Zone.LIBRARY),
                count(seat, Zone.HAND),
                count(seat, Zone.BATTLEFIELD));

        return mine ? line.copy().withStyle(ChatFormatting.AQUA) : line;
    }

    /**
     * How many cards are in a zone, as this viewer is entitled to know it.
     *
     * <p>A count is a count whether or not the identities came with it - an opponent's hand
     * arrives as a number and nothing else, which is the point.
     */
    private static int count(SeatView seat, Zone zone) {
        ZoneView view = seat.zone(zone);
        return view == null ? 0 : view.count();
    }

    /** The seat a player is sitting in at this cluster, for building their own view. */
    public static dev.gathering.core.game.visibility.Viewer viewerFor(java.util.Optional<SeatId> seat) {
        return seat.<dev.gathering.core.game.visibility.Viewer>
                        map(dev.gathering.core.game.visibility.Viewer.Seated::new)
                .orElseGet(dev.gathering.core.game.visibility.Viewer.Spectator::new);
    }
}
