package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameState;
import dev.gathering.core.game.LossReminders;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The public numbers of the game at a table, for a board somebody has put up beside it.
 * <p>Life and poison only: the numbers anybody at a real table can see across it. Nothing about a
 * hand or a library, whose sizes are public too but are not what a scoreboard is for, and nothing
 * the visibility rules would withhold.
 */
public final class TableBoard {

    /** One player: their name, life, and poison counters. */
    public record Player(String name, int life, int poison, boolean conceded) {

        public boolean isAtALoss() {
            return conceded || LossReminders.lifeIsAtALoss(life) || LossReminders.poisonIsAtALoss(poison);
        }
    }

    private TableBoard() {
    }

    /** Everybody with a board at the game on the table this block is part of, in seat order. */
    public static List<Player> players(ServerLevel level, BlockPos anyTableBlock) {
        BlockPos origin = TableBlock.entityAt(level, anyTableBlock).map(TableBlockEntity::getBlockPos).orElse(null);
        if (origin == null) {
            return List.of();
        }
        GameState game = TableSessions.sessionAt(level, origin).map(session -> session.state()).orElse(null);
        if (game == null) {
            return List.of();
        }
        List<Player> players = new ArrayList<>();
        for (SeatId seat : game.seats()) {
            SeatState state = game.seatState(seat);
            state.whoseBoard().ifPresent(player -> players.add(new Player(player.name(), state.life(),
                    state.counter(SeatState.Counters.POISON), state.conceded())));
        }
        return List.copyOf(players);
    }
}
