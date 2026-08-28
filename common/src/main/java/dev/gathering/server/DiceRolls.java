package dev.gathering.server;

import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.event.PlanarFace;
import dev.gathering.network.FlipCoinPayload;
import dev.gathering.network.RollDicePayload;
import dev.gathering.network.RollPlanarPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rolling a die and flipping a coin, decided here because they cannot honestly be decided
 * anywhere else.
 *
 * <p>Magic asks for both often enough to deserve verbs - a d20 across half of Adventures in
 * the Forgotten Realms, a d6 on a Sarkhan, a coin as far back as Krark's Thumb - and the mod
 * trusts clients with almost everything else. This is the same exception a random discard is,
 * for the same reason: the outcome has no meaning unless somebody other than the player chose
 * it. A die the roller picked is not a die, it is a claim, and the whole value of rolling at
 * this table rather than on a desk is that everybody watched the same number come up.
 *
 * <p>Not from the session's shuffle seed. That seed is the most sensitive value the server
 * holds and never leaves the session that owns it; a roll anybody can ask for repeatedly
 * would be a window onto it. This uses the level's own randomness, the same as ante staking
 * and the random discard.
 *
 * <p>Nothing on the board changes. The result goes in the log under the name of whoever asked,
 * which is where the table reads it and what makes it evidence afterwards.
 *
 * <p>Server thread only.
 */
public final class DiceRolls {

    private DiceRolls() {
    }

    public static void roll(ServerPlayer player, RollDicePayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int sides = payload.sides();
        // nextInt(sides) is zero-based and a die is not: a d20 reads one to twenty.
        int result = level.getRandom().nextInt(sides) + 1;
        tell(level, at, new GameEvent.DiceRolled(at.seat(), sides, result));
    }

    public static void flip(ServerPlayer player, FlipCoinPayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        tell(level, at, new GameEvent.CoinFlipped(at.seat(), level.getRandom().nextBoolean()));
    }

    /**
     * The planar die, whose faces are symbols rather than numbers.
     *
     * <p>Rolled out of six and read as a face, so the odds are Planechase's printed ones - one
     * chaos, one planeswalk, four blanks - rather than a third each. See {@link PlanarFace}.
     */
    public static void planar(ServerPlayer player, RollPlanarPayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        tell(level, at, new GameEvent.PlanarRolled(
                at.seat(), PlanarFace.of(level.getRandom().nextInt(PlanarFace.FACES))));
    }

    /**
     * Writes it down and tells the table.
     *
     * <p>Broadcast even though the board is unchanged: the log travels with the view, so a
     * roll nobody was sent is a roll only the roller saw - which is the one thing this must
     * not be.
     */
    private static void tell(ServerLevel level, TableReach.Seated at, GameEvent what) {
        BlockPos origin = at.origin();
        GameSession session = at.session();
        if (session.submit(what) instanceof GameSession.Result.Rejected) {
            return;
        }
        TableSessions.markDirty(level, origin);
        TableBroadcast.sendToTable(level, origin);
    }
}
