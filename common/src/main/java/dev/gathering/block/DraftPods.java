package dev.gathering.block;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.CubePacks;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.DraftRules;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCluster;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Starting, finding and ending the draft on a cluster of tables.
 *
 * <p>The pod lives where the game would, on the cluster's anchor table, for the same reason:
 * one cluster runs one thing at a time, and every table in it agrees on where that is without
 * anything having to be written down. Drafting itself has no table geometry - the ring is the
 * order the seats are numbered in, and where anybody is standing does not matter once the
 * packs are out.
 */
public final class DraftPods {

    private DraftPods() {
    }

    public static Optional<DraftPod> podAt(BlockGetter level, BlockPos tableOrigin) {
        return TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .flatMap(TableBlockEntity::pod);
    }

    public static boolean hasPod(BlockGetter level, BlockPos tableOrigin) {
        return podAt(level, tableOrigin).isPresent();
    }

    /** Everybody registered at this cluster, in seat order, skipping the empty chairs. */
    public static List<PlayerRef> drafters(Level level, BlockPos tableOrigin) {
        TableCluster cluster = TableClusters.at(level, tableOrigin);
        List<SeatAnchor> anchors = cluster.seats();
        List<PlayerRef> drafters = new ArrayList<>(anchors.size());
        for (SeatAnchor seat : anchors) {
            Optional<UUID> occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, seat.cell()))
                    .flatMap(table -> table.occupantOf(seat.side()));
            if (occupant.isEmpty()) {
                continue;
            }
            Player player = level.getPlayerByUUID(occupant.get());
            String name = player == null ? "Player" : player.getGameProfile().getName();
            drafters.add(new PlayerRef(occupant.get(), name));
        }
        return List.copyOf(drafters);
    }

    /**
     * Opens a draft of this cube for everybody registered at this cluster.
     *
     * <p>Everybody who is here, rather than a list somebody assembles: a pod is the people at
     * the tables, and asking them to sign up twice - once by sitting down and once by being
     * added - is a step that only exists to be got wrong.
     *
     * @param cube every card in the cube, one entry per physical card
     */
    public static Outcome start(
            Level level, BlockPos tableOrigin, List<CardIdentity> cube, boolean poolsAreKept) {
        BlockPos anchor = TableSessions.anchorOf(level, tableOrigin).orElse(null);
        if (anchor == null) {
            return Outcome.NO_TABLE;
        }
        TableBlockEntity table = TableBlock.entityAt(level, anchor).orElse(null);
        if (table == null) {
            return Outcome.NO_TABLE;
        }
        if (table.hasPod()) {
            return Outcome.ALREADY_DRAFTING;
        }
        if (table.hasSession()) {
            // A cluster runs one thing. Drafting on top of a game in progress would put two
            // screens on the same tables with no way to say which one anybody meant.
            return Outcome.GAME_RUNNING;
        }

        List<PlayerRef> drafters = drafters(level, tableOrigin);
        if (!DraftRules.isAPodSize(drafters.size())) {
            return drafters.size() < DraftRules.SMALLEST_POD ? Outcome.TOO_FEW : Outcome.TOO_MANY;
        }
        if (cube == null || !CubePacks.isBigEnough(cube.size(), drafters.size())) {
            return Outcome.CUBE_TOO_SMALL;
        }

        table.setPod(DraftPod.opening(
                drafters,
                CubePacks.deal(cube, drafters.size(), SessionSeed.random().toBytes()),
                poolsAreKept));
        return Outcome.STARTED;
    }

    /** Replaces the running pod with what it became after somebody picked. */
    public static void record(Level level, BlockPos tableOrigin, DraftPod after) {
        TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(table -> table.setPod(after));
    }

    /** Forgets the pod, which is what handing the pools out means. */
    public static void end(Level level, BlockPos tableOrigin) {
        TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(TableBlockEntity::endPod);
    }

    /** How a request to start a draft turned out, and what to say about it. */
    public enum Outcome {
        STARTED,
        ALREADY_DRAFTING,
        GAME_RUNNING,
        TOO_FEW,
        TOO_MANY,
        CUBE_TOO_SMALL,
        NO_TABLE;

        public String messageKey() {
            return "message.gathering.draft_" + name().toLowerCase(Locale.ROOT);
        }
    }
}
