package dev.gathering.server;

import dev.gathering.block.DraftPods;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.PickClock;
import dev.gathering.core.draft.PodRecord;
import dev.gathering.core.game.PlayerRef;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The pick clock at a table: when a drafter's time is up, the first cards in their pack are
 * taken for them. See {@link PickClock} for why the first ones.
 * <p>The turn's start is kept on the table and not saved. A restart gives everybody the whole
 * clock again, which costs a pod at most one extra turn's wait and never takes a card from
 * somebody who had no chance to pick it.
 */
public final class PickClocks {

    private PickClocks() {
    }

    /** Once a tick, from the table holding the pod. */
    public static void tick(ServerLevel level, BlockPos anchor, TableBlockEntity table) {
        int clock = clockOf(table);
        DraftPod pod = table.pod().orElse(null);
        if (!PickClock.isOn(clock) || pod == null || pod.isFinished()) {
            return;
        }
        int seconds = PickClock.secondsFor(clock, PickClock.cardsInPacks(pod.state()));
        long now = level.getGameTime();
        long startedAt = startedAt(table, pod, now);
        if (!PickClock.isUp(seconds, startedAt, now)) {
            return;
        }
        List<PlayerRef> late = PickClock.late(pod);
        if (late.isEmpty()) {
            return;
        }
        DraftPod after = PickClock.pickForTheLate(pod);
        DraftPods.record(level, anchor, after);
        for (PlayerRef drafter : late) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(drafter.id());
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.gathering.draft.picked_for_you"));
            }
        }
        DraftBroadcast.sendToPod(level, anchor, false);
        if (after.isFinished()) {
            DraftActions.finish(level, anchor, after);
        }
    }

    /**
     * How long the drafters at this table have left to pick, or -1 when there is no clock.
     * <p>Starts the turn's clock when the packs have just moved, so what a drafter is shown
     * and what the table counts from are the same moment.
     */
    public static int secondsLeft(ServerLevel level, BlockPos anchor, TableBlockEntity table, DraftPod pod) {
        int clock = clockOf(table);
        if (!PickClock.isOn(clock) || pod.isFinished()) {
            return -1;
        }
        int seconds = PickClock.secondsFor(clock, PickClock.cardsInPacks(pod.state()));
        long now = level.getGameTime();
        return PickClock.secondsLeft(seconds, startedAt(table, pod, now), now);
    }

    private static int clockOf(TableBlockEntity table) {
        return table.podRecord().map(PodRecord::pickSeconds).orElse(0);
    }

    private static long startedAt(TableBlockEntity table, DraftPod pod, long now) {
        long turn = PickClock.turnOf(pod.state());
        if (!table.isClockOnTurn(turn)) {
            table.startClock(turn, now);
        }
        return table.clockStartedAt();
    }
}
