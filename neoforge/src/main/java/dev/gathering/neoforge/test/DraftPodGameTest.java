package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.DraftPods;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSeats;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.DraftRules;
import dev.gathering.core.draft.DrafterId;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.item.GatheringContent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import dev.gathering.block.TablePart;

/**
 * A draft on real tables in a real world.
 *
 * <p>The pod itself is checked to death in the pure module. What only exists here is the part
 * with blocks in it: that a pod forms from the people actually sitting at a cluster, that it
 * refuses the cases it should, and above all that it survives a save and load - a draft is
 * twenty minutes of decisions and a server restart in the middle of one must not eat it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DraftPodGameTest {

    @GameTest(template = "empty")
    public static void aPodFormsFromThePeopleSittingAtTheTables(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitFour(helper, origin);

        DraftPods.Outcome outcome = DraftPods.start(
                helper.getLevel(), origin, cubeOf(200), true);

        if (outcome != DraftPods.Outcome.STARTED) {
            helper.fail("A draft would not start with four seated: " + outcome);
            return;
        }
        DraftPod pod = DraftPods.podAt(helper.getLevel(), origin).orElse(null);
        if (pod == null) {
            helper.fail("A draft started and left no pod on the table");
            return;
        }
        if (pod.drafters().size() != 4) {
            helper.fail("The pod has " + pod.drafters().size() + " drafters, not the four seated");
            return;
        }
        if (pod.state().picksDueFrom(DrafterId.of(0)) != 2) {
            helper.fail("A pod of four is not picking two at a time");
        }
        helper.succeed();
    }

    /** Three people is not a pod, and being told so is better than a draft that plays badly. */
    @GameTest(template = "empty")
    public static void threePeopleAreNotAPod(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitAt(helper, origin, new TableCell(0, 0), Side.NORTH, 1);
        sitAt(helper, origin, new TableCell(0, 0), Side.SOUTH, 2);
        sitAt(helper, origin, new TableCell(1, 0), Side.NORTH, 3);
        if (DraftPods.drafters(helper.getLevel(), origin).size() != 3) {
            // Or the refusal below would be right for the wrong reason - a chair that is not
            // a seat sits nobody, and this would then be a test of two people, not three.
            helper.fail("Three people did not end up in three chairs");
            return;
        }

        if (DraftPods.start(helper.getLevel(), origin, cubeOf(200), true)
                != DraftPods.Outcome.TOO_FEW) {
            helper.fail("A draft started with three people at the tables");
            return;
        }
        if (DraftPods.hasPod(helper.getLevel(), origin)) {
            helper.fail("A refused draft left a pod behind anyway");
        }
        helper.succeed();
    }

    /** And a cube too thin to make packs out of is refused before anything is dealt. */
    @GameTest(template = "empty")
    public static void aCubeTooThinToMakePacksIsRefused(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitFour(helper, origin);

        if (DraftPods.start(helper.getLevel(), origin, cubeOf(20), true)
                != DraftPods.Outcome.CUBE_TOO_SMALL) {
            helper.fail("A twenty-card cube was dealt to four drafters");
            return;
        }
        if (DraftPods.hasPod(helper.getLevel(), origin)) {
            helper.fail("A refused draft left a pod behind anyway");
        }
        helper.succeed();
    }

    /** Two drafts on one cluster would be two screens with no way to say which was meant. */
    @GameTest(template = "empty")
    public static void oneClusterRunsOneDraft(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitFour(helper, origin);
        DraftPods.start(helper.getLevel(), origin, cubeOf(200), true);

        if (DraftPods.start(helper.getLevel(), origin, cubeOf(200), true)
                != DraftPods.Outcome.ALREADY_DRAFTING) {
            helper.fail("A second draft started on top of the first");
        }
        helper.succeed();
    }

    /**
     * A draft survives a save and load, mid-turn, with every pack where it was.
     *
     * <p>The one thing this test is really for. Everything else here would be noticed in the
     * first minute of a draft; a pod that comes back subtly wrong after a restart would be
     * noticed as somebody holding cards that are not theirs, an hour later, with no way to
     * work out what happened.
     */
    @GameTest(template = "empty")
    public static void aDraftSurvivesASaveAndLoad(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitFour(helper, origin);
        DraftPods.start(helper.getLevel(), origin, cubeOf(200), true);

        DraftPod pod = DraftPods.podAt(helper.getLevel(), origin).orElseThrow();
        // Halfway through a turn: one drafter has said what they are taking and the packs
        // have not moved, which is the state hardest to rebuild from anything less than all
        // of it.
        pod = pod.declare(pod.drafterAt(DrafterId.of(1)).orElseThrow().id(),
                DrafterId.of(1), List.of(0, 1));
        DraftPods.record(helper.getLevel(), origin, pod);

        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());
        TableBlockEntity reloaded =
                new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        DraftPod restored = reloaded.pod().orElse(null);
        if (restored == null) {
            helper.fail("A draft did not survive a save and load at all");
            return;
        }
        if (!restored.equals(pod)) {
            helper.fail("A draft came back different from how it went in");
            return;
        }
        if (!restored.state().hasDeclared(DrafterId.of(1))
                || restored.state().hasDeclared(DrafterId.of(0))) {
            helper.fail("A half-finished turn came back as a different half");
        }
        helper.succeed();
    }

    /**
     * No two drafters open the same card, on real tables with a real cube.
     *
     * <p>The pure module checks the dealing; this checks that what actually reaches a cluster
     * went through it, rather than something along the way handing the same list out four
     * times.
     */
    @GameTest(template = "empty")
    public static void noTwoDraftersOpenTheSameCard(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitFour(helper, origin);
        DraftPods.start(helper.getLevel(), origin, cubeOf(200), true);

        DraftPod pod = DraftPods.podAt(helper.getLevel(), origin).orElseThrow();
        Set<CardIdentity> seen = new LinkedHashSet<>();
        int dealt = 0;
        for (List<dev.gathering.core.draft.DraftPack> round : pod.state().opening()) {
            for (dev.gathering.core.draft.DraftPack pack : round) {
                seen.addAll(pack.cards());
                dealt += pack.size();
            }
        }
        if (dealt != DraftRules.ROUNDS * 4 * 15) {
            helper.fail("Two hundred cards between four came out as " + dealt + " dealt");
            return;
        }
        if (seen.size() != dealt) {
            helper.fail("A card was dealt into two packs: " + dealt + " dealt, " + seen.size()
                    + " distinct");
        }
        helper.succeed();
    }

    // --- helpers ---

    /**
     * Two tables side by side, which is four seats: the smallest pod there is.
     *
     * <p>Side by side along x, deliberately. Seats are only ever on the north and south edges,
     * so two tables stacked north to south would bury the two edges that seat anybody and
     * come out as a cluster nobody can sit at.
     */
    private static BlockPos twoTables(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);
        return origin;
    }

    private static void sitFour(GameTestHelper helper, BlockPos origin) {
        sitAt(helper, origin, new TableCell(0, 0), Side.NORTH, 1);
        sitAt(helper, origin, new TableCell(0, 0), Side.SOUTH, 2);
        sitAt(helper, origin, new TableCell(1, 0), Side.NORTH, 3);
        sitAt(helper, origin, new TableCell(1, 0), Side.SOUTH, 4);
    }

    private static void sitAt(
            GameTestHelper helper, BlockPos origin, TableCell cell, Side side, int who) {
        TableSeats.take(helper.getLevel(), origin, cell, side,
                UUID.fromString("00000000-0000-4000-8000-00000000000" + who));
    }

    private static List<CardIdentity> cubeOf(int size) {
        List<CardIdentity> cube = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            cube.add(CardIdentity.ofPrinting(UUID.nameUUIDFromBytes(
                    ("cube-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        return cube;
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        BlockState table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(
                    part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
