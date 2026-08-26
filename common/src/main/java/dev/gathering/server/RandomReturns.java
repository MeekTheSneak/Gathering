package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.CardInstance;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.RandomPick;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.network.ToBottomAtRandomPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Putting cards back under a library in an order nobody chose.
 *
 * <p>Which cards is the client's to say - they are the ones the player is pointing at and
 * everybody can see them. The <em>order</em> is not, and that is the whole reason this is
 * here: the bottom of a library is hidden from everyone, so a player who arranged their own
 * four cards on the way down would be the only person at the table who knew what was there.
 *
 * <p>From the level's randomness, never the session's shuffle seed - the same rule ante
 * staking and the random discard follow, and for the same reason.
 *
 * <p>Each card goes under <em>its owner's</em> library, which is the rule the card menu
 * already uses for the same move. Nothing here can put somebody else's card into your deck.
 *
 * <p>Server thread only.
 */
public final class RandomReturns {

    private RandomReturns() {
    }

    public static void handle(ServerPlayer player, ToBottomAtRandomPayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null || payload.cards().isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = at.origin();
        GameSession session = at.session();

        // Only cards this game actually holds, and each one only once: a list that named the
        // same card twice would move it, then move it again from wherever it had landed.
        List<CardInstanceId> real = new ArrayList<>();
        for (CardInstanceId card : payload.cards()) {
            if (!real.contains(card) && session.state().card(card).isPresent()) {
                real.add(card);
            }
        }
        if (real.isEmpty()) {
            return;
        }

        List<CardInstanceId> order = RandomPick.some(real, real.size(), level.getRandom()::nextInt);
        int moved = 0;
        for (CardInstanceId card : order) {
            CardInstance instance = session.state().card(card).orElse(null);
            if (instance == null) {
                continue;
            }
            GameSession.Result result = session.submit(new GameEvent.CardMoved(
                    at.seat(), card, ZoneRef.of(instance.owner(), Zone.LIBRARY), Placement.BOTTOM));
            if (result instanceof GameSession.Result.Rejected) {
                break;
            }
            moved++;
        }
        if (moved == 0) {
            return;
        }
        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(TableBlockEntity::setChanged);
        TableBroadcast.sendToTable(level, origin);
    }
}
