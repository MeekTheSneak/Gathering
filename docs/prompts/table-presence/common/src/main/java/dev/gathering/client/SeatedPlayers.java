package dev.gathering.client;

import dev.gathering.core.table.Side;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Which table a player is sitting at, which edge of it, and what is in their hand.
 * <p><b>Answered entirely from what this client already holds.</b> Every table in sight sends its
 * public board for the miniature on its surface, so {@link ClientTableState} already knows who is
 * in which seat and how many cards they have; the cluster's own shape comes off the blocks, and
 * {@code TableClusters.at(level, origin).seats()} is in the same order as the board's seats, which
 * is what turns a seat index into a {@link Side}. No payload was added for any of this, and none
 * should be: a second source for "who is sitting where" is a second thing to be wrong.
 * <p><b>Counts only.</b> The hand this reports is {@code ZoneView.count()} - the number
 * {@code VisibilityRules} sends to everybody, because the whole table watches you draw. It must
 * never grow a way to hand a caller a card: the thing that draws the fan takes an {@code int} and
 * a {@code Sleeve} precisely so that it cannot leak an identity it was never given.
 * <p>Client-only. Holds nothing: every answer is read fresh, because a cached seat is a player
 * whose cards are drawn at the chair they left.
 */
public final class SeatedPlayers {

    /**
     * What is worth knowing about a player who is sitting at a table.
     *
     * @param table  the cluster's origin block
     * @param side   which edge they are at, which is what says where their hands are
     * @param seat   their seat's index in the board, for the sleeve and the mat
     * @param cards  how many cards are in their hand - a public number, and the only one
     * @param sleeve what the backs of those cards look like
     */
    public record Seated(BlockPos table, Side side, int seat, int cards,
            dev.gathering.core.card.Sleeve sleeve) {
    }

    private SeatedPlayers() {
    }

    /**
     * Where this player is sitting, if they are.
     * <p>Empty for everybody who is walking around, which is almost everybody almost always.
     */
    public static Optional<Seated> of(UUID player) {
        throw new UnsupportedOperationException("""
                Not written yet. Walk the boards in ClientTableState, find the SeatView whose
                player() has this UUID, then:
                  - the seat index is SeatView.seat().index()
                  - the side is TableClusters.at(level, table).seats().get(index).side()
                  - the count is seat.zones().get(Zone.HAND).count()
                  - the sleeve is CardSleeves.of(board, seat.seat())
                Guard the index against a cluster whose shape changed under a board this client
                has not been re-sent: a seats() list shorter than the board's is a table somebody
                is mid-way through rebuilding, and the answer there is empty rather than a guess.""");
    }

    /** Whether this player is sitting at this particular table, which is the table view's question. */
    public static boolean isSeatedAt(UUID player, BlockPos table) {
        return of(player).map(seated -> seated.table().equals(table)).orElse(false);
    }
}
