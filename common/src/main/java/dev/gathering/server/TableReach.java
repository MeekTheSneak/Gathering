package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether the player asking is actually standing at the table they named.
 *
 * <p>Every payload the table accepts carries a position, and a position is any position: a
 * client can put whatever coordinates it likes in one. So each handler has to check that the
 * player is really there before it answers - and each handler was checking it separately, with
 * its own copy of the number, in eight places. Two of them measured a different distance. That
 * is the shape of a rule that drifts: the day the reach changes, seven files quietly disagree
 * about who may reach a table, and nothing fails.
 *
 * <p>Measured from where the player is to the middle of the block they named, which is the
 * question being asked. Block-to-block was one of the two versions and it is the wrong one: it
 * ignores where in the block the player is standing and is short by up to most of a metre.
 *
 * <p>Server thread only.
 */
public final class TableReach {

    /**
     * How far a player may be from a table and still be playing at it.
     *
     * <p>Generous on purpose, and not vanilla's reach. A player at a table steps back to read
     * their own board on the block, walks round to see somebody else's, and leans in again -
     * and a verb that stopped working halfway through that is a table that feels like it is
     * about to throw you out. What this is really for is the client that names a table on the
     * other side of the world.
     */
    public static final double REACH = 12.0d;

    private TableReach() {
    }

    /** Whether this player is close enough to the middle of that block to be playing at it. */
    public static boolean within(ServerPlayer player, BlockPos block) {
        return player != null && block != null
                && player.distanceToSqr(
                        block.getX() + 0.5d, block.getY() + 0.5d, block.getZ() + 0.5d)
                <= REACH * REACH;
    }

    /**
     * The corner of the table this player is asking about, if it is a table and they are at it.
     *
     * <p>A table is four blocks and every verb is written against the corner one, so the
     * conversion belongs here rather than in each caller - a handler that worked from the
     * block that was clicked would find a different session depending on which quarter of the
     * table somebody pressed.
     */
    public static Optional<BlockPos> originFor(ServerPlayer player, BlockPos clicked) {
        if (player == null || clicked == null || !within(player, clicked)) {
            return Optional.empty();
        }
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TableBlock)) {
            return Optional.empty();
        }
        return Optional.of(TableBlock.originOf(state, clicked));
    }

    /** A player at a table, in a seat, with a game going on. */
    public record Seated(BlockPos origin, GameSession session, SeatId seat) {
    }

    /**
     * All of that plus the seat, for the verbs that act on your own board.
     *
     * <p>The seat comes from the player the packet arrived from and never from the packet.
     * That is the whole of the authorisation for these verbs: a client cannot ask to shuffle
     * somebody else's library because there is nowhere in the request to say whose.
     */
    public static Optional<Seated> seatedAt(ServerPlayer player, BlockPos clicked) {
        BlockPos origin = originFor(player, clicked).orElse(null);
        if (origin == null) {
            return Optional.empty();
        }
        ServerLevel level = player.serverLevel();
        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        SeatId seat = TableSessions.seatIdOf(level, origin, player.getUUID()).orElse(null);
        return session == null || seat == null
                ? Optional.empty()
                : Optional.of(new Seated(origin, session, seat));
    }
}
