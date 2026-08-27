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

    private RandomDiscards() {
    }

    public static void handle(ServerPlayer player, DiscardAtRandomPayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = at.origin();
        GameSession session = at.session();
        SeatId seat = at.seat();

        List<CardInstanceId> hand = session.state().contents(seat, Zone.HAND);
        List<CardInstanceId> going = RandomPick.some(
                hand, payload.howMany(), level.getRandom()::nextInt);
        if (going.isEmpty()) {
            // An empty hand is not a failure, but a button that does nothing and says nothing
            // is - the player is left wondering whether the press landed.
            player.sendSystemMessage(Component.translatable("message.gathering.discard_none"));
            return;
        }

        // One event per card, and a refusal partway through has to be told rather than
        // returned from. Whatever went before it has already gone: leaving without the
        // broadcast would put those cards in the graveyard on the server and leave every
        // client drawing them in a hand they are no longer in, until something unrelated
        // happened to send the board again.
        String refused = null;
        int moved = 0;
        for (CardInstanceId card : going) {
            GameSession.Result result = session.submit(new GameEvent.CardMoved(
                    seat, card, ZoneRef.of(seat, Zone.GRAVEYARD), Placement.TOP));
            if (result instanceof GameSession.Result.Rejected rejected) {
                refused = rejected.reason();
                break;
            }
            moved++;
        }
        if (moved > 0) {
            TableSessions.markDirty(level, origin);
            TableBroadcast.sendToTable(level, origin);
        }
        if (refused != null) {
            // How many really went, not just why the rest did not. A player told only "no"
            // after two of their three cards have gone has been told the wrong thing.
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.discard_partial", moved, going.size(),
                    Component.literal(refused)));
        }
    }
}
