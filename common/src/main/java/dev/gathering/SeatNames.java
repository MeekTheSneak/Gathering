package dev.gathering;

import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * What to call a seat in a sentence.
 * <p>One rule, in one place, because there are five sentences that need it - the game log,
 * the tooltip on a life total, the title over a pile of somebody's cards, the status a table
 * block reports, and the messages a match sends - and each of them used to work it out for
 * itself. They agreed on the day they were written. Then a board learned to outlast its
 * player, and all five kept asking who was <em>sitting</em> there: the moment somebody stood
 * up, their life total, their graveyard and every line they had already put in the log turned
 * into "(empty)". A game log that rewrites its own history the instant somebody leaves is
 * worse than no log, because the whole reason it exists is to answer "who did that".
 * <p>So the question is whose board it is, not who holds the chair. "(empty)" is kept for
 * what it is actually true of: a chair nobody has ever sat in.
 */
public final class SeatNames {

    private SeatNames() {
    }

    /** The seat with this id on this board, named. */
    public static Component of(GameView board, SeatId seat) {
        for (SeatView view : board.seats()) {
            if (view.seat().equals(seat)) {
                return of(view);
            }
        }
        return nobody();
    }

    public static Component of(SeatView seat) {
        return seat == null ? nobody() : of(seat.whoseBoard());
    }

    public static Component of(SeatState seat) {
        return seat == null ? nobody() : of(seat.whoseBoard());
    }

    public static Component of(Optional<PlayerRef> whoseBoard) {
        return whoseBoard
                .<Component>map(player -> Component.literal(player.name()))
                .orElseGet(SeatNames::nobody);
    }

    /** A chair nobody has ever sat in - the one case there really is no name for. */
    private static Component nobody() {
        return Component.translatable("message.gathering.seat_empty");
    }
}
