package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.RandomPick;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.network.DiscardAtRandomPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Discarding at random, decided here because it cannot honestly be decided anywhere else.
 *
 * <p>The mod trusts clients with almost everything: no rules enforcement, section 16, and a
 * player who wants to draw eight cards can, exactly as they could across a real table where
 * everyone would see them do it. This is the exception, and the reason is not security in the
 * usual sense - it is that the outcome has no meaning unless somebody other than the player
 * chose it. A random discard the discarder picked is not a random discard.
 *
 * <p>Not from the session's shuffle seed. The seed is the most sensitive value the server
 * holds and never leaves the session that owns it; this uses the level's own randomness, the
 * same as ante staking does and for the same reason.
 *
 * <p>Server thread only.
 */
public final class RandomDiscards {

    /** How far a player may be from a table and still be playing at it. Same as every verb. */
    private static final double REACH = 12.0d;

    private RandomDiscards() {
    }

    public static void handle(ServerPlayer player, DiscardAtRandomPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos clicked = payload.table();
        if (player.distanceToSqr(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5)
                > REACH * REACH) {
            return;
        }
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TableBlock)) {
            return;
        }
        BlockPos origin = TableBlock.originOf(state, clicked);
        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        SeatId seat = TableSessions.seatIdOf(level, origin, player.getUUID()).orElse(null);
        if (session == null || seat == null) {
            return;
        }

        List<CardInstanceId> hand = session.state().contents(seat, Zone.HAND);
        List<CardInstanceId> going = RandomPick.some(
                hand, payload.howMany(), level.getRandom()::nextInt);
        if (going.isEmpty()) {
            // An empty hand is not a failure, but a button that does nothing and says nothing
            // is - the player is left wondering whether the press landed.
            player.sendSystemMessage(Component.translatable("message.gathering.discard_none"));
            return;
        }

        for (CardInstanceId card : going) {
            GameSession.Result result = session.submit(new GameEvent.CardMoved(
                    seat, card, ZoneRef.of(seat, Zone.GRAVEYARD), Placement.TOP));
            if (result instanceof GameSession.Result.Rejected rejected) {
                player.sendSystemMessage(Component.literal(rejected.reason()));
                return;
            }
        }
        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(TableBlockEntity::setChanged);
        TableBroadcast.sendToTable(level, origin);
    }
}
