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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A draft on real tables in a real world.
 * <p>The pod itself is checked to death in the pure module. What only exists here is the part
 * with blocks in it: that a pod forms from the people actually sitting at a cluster, that it
 * refuses the cases it should, and above all that it survives a save and load - a draft is
 * twenty minutes of decisions and a server restart in the middle of one must not eat it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DraftPodGameTest {

    @GameTest(template = "tables")
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

    /**
     * A cube draft's pools are for playing and not for keeping. The cube stays whole in its
     * owner's hands, so pools kept as real cards made every card in it twice, every time it was
     * drafted - which a security review reproduced.
     */
    @GameTest(template = "tables", timeoutTicks = 200)
    public static void acubeDraftsPoolsAreLoanersNotNewCards(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        List<net.minecraft.server.level.ServerPlayer> drafters = new ArrayList<>();
        TableCell[] cells = {new TableCell(0, 0), new TableCell(0, 0), new TableCell(1, 0), new TableCell(1, 0)};
        Side[] sides = {Side.NORTH, Side.SOUTH, Side.NORTH, Side.SOUTH};
        for (int index = 0; index < 4; index++) {
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(origin.getX() + 1.0, origin.getY(), origin.getZ() + 1.0);
            TableSeats.take(helper.getLevel(), origin, cells[index], sides[index], player.getUUID());
            drafters.add(player);
        }
        if (DraftPods.start(helper.getLevel(), origin, cubeOf(200), true) != DraftPods.Outcome.STARTED) {
            helper.fail("the cube draft did not start");
            return;
        }
        for (int turn = 0; turn < 200 && DraftPods.podAt(helper.getLevel(), origin).isPresent(); turn++) {
            DraftPod pod = DraftPods.podAt(helper.getLevel(), origin).orElseThrow();
            for (var player : drafters) {
                DrafterId place = pod.placeOf(player.getUUID()).orElseThrow();
                int due = pod.state().picksDueFrom(place);
                if (due > 0 && !pod.state().hasDeclared(place)) {
                    List<Integer> first = new ArrayList<>();
                    for (int position = 0; position < due; position++) {
                        first.add(position);
                    }
                    dev.gathering.server.DraftActions.handle(player, origin, TestTables.draftTurnAt(helper, origin), first);
                    pod = DraftPods.podAt(helper.getLevel(), origin).orElse(pod);
                }
            }
        }
        if (DraftPods.podAt(helper.getLevel(), origin).isPresent()) {
            helper.fail("the cube draft did not finish");
            return;
        }
        for (var player : drafters) {
            var pool = player.getInventory().items.stream()
                    .map(dev.gathering.item.DeckItem::deckOf).flatMap(java.util.Optional::stream).findFirst().orElse(null);
            if (pool == null) {
                helper.fail("a drafter was handed no pool");
                return;
            }
            if (!pool.loaner()) {
                helper.fail("a cube draft's pool was handed out as cards to keep");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A pick sent twice by the drafter the pod was waiting on takes nothing out of the next pack.
     * <p>From the 2026-09-25 sweep (#23). The last pick of a turn moves the packs at once, so the same
     * places sent again - Take pressed twice before the new pack arrived - used to be taken out of the
     * next pack, which this drafter had not seen. Through the route a client's packet takes.
     */
    @GameTest(template = "tables")
    public static void aPickSentTwiceByTheLastDrafterTakesNothingFromTheNextPack(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        List<net.minecraft.server.level.ServerPlayer> drafters = new ArrayList<>();
        TableCell[] cells = {new TableCell(0, 0), new TableCell(0, 0), new TableCell(1, 0), new TableCell(1, 0)};
        Side[] sides = {Side.NORTH, Side.SOUTH, Side.NORTH, Side.SOUTH};
        for (int index = 0; index < 4; index++) {
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(origin.getX() + 1.0, origin.getY(), origin.getZ() + 1.0);
            TableSeats.take(helper.getLevel(), origin, cells[index], sides[index], player.getUUID());
            drafters.add(player);
        }
        if (DraftPods.start(helper.getLevel(), origin, cubeOf(200), true) != DraftPods.Outcome.STARTED) {
            helper.fail("fixture: the draft did not start");
            return;
        }
        long turn = TestTables.draftTurnAt(helper, origin);
        for (int index = 1; index < 4; index++) {
            route(drafters.get(index), new dev.gathering.network.DraftPickPayload(origin, turn, List.of(0, 1)));
        }
        var last = drafters.get(0);
        var pick = new dev.gathering.network.DraftPickPayload(origin, turn, List.of(3, 5));
        route(last, pick);
        route(last, pick);

        DraftPod after = DraftPods.podAt(helper.getLevel(), origin).orElse(null);
        if (after == null || dev.gathering.core.draft.PickClock.turnOf(after.state()) == turn) {
            helper.fail("fixture: the last pick of the turn did not move the packs on");
            return;
        }
        DrafterId place = after.placeOf(last.getUUID()).orElseThrow();
        if (after.state().hasDeclared(place)) {
            helper.fail("a pick sent twice by the drafter the pod was waiting on took cards out of the next pack, "
                    + "which they had not seen");
            return;
        }
        if (after.state().poolOf(place).size() != 2) {
            helper.fail("the first of the two picks did not stand: the pool holds " + after.state().poolOf(place).size());
            return;
        }
        helper.succeed();
    }

    /**
     * A pick from somebody who is not in the pod is refused, and the refusal does not throw.
     * <p>Found reviewing the turn check (#23). Every refusal showed the picker the pack in front of them
     * again, and somebody not in the pod has no place in the ring: the view built for no place threw out of
     * the payload handler, on the server thread - which disconnects the client on one loader and reaches
     * the server's task queue on the other. A modified client standing at a drafting table could send one.
     */
    @GameTest(template = "tables")
    public static void aPickFromSomebodyNotInThePodIsRefusedWithoutThrowing(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitFour(helper, origin);
        if (DraftPods.start(helper.getLevel(), origin, cubeOf(200), true) != DraftPods.Outcome.STARTED) {
            helper.fail("fixture: the draft did not start");
            return;
        }
        DraftPod before = DraftPods.podAt(helper.getLevel(), origin).orElseThrow();
        var outsider = helper.makeMockServerPlayerInLevel();
        outsider.setPos(origin.getX() + 1.0, origin.getY(), origin.getZ() + 1.0);
        try {
            route(outsider, new dev.gathering.network.DraftPickPayload(
                    origin, TestTables.draftTurnAt(helper, origin), List.of(0, 1)));
        } catch (RuntimeException threw) {
            helper.fail("a pick from somebody not in the pod threw out of the handler: " + threw);
            return;
        }
        DraftPod after = DraftPods.podAt(helper.getLevel(), origin).orElse(null);
        if (after == null || !after.state().equals(before.state())) {
            helper.fail("a pick from somebody not in the pod changed the pod");
            return;
        }
        helper.succeed();
    }

    /**
     * A pick from a drafter moved out of reach of the tables is answered, not dropped without a word.
     * <p>Found reviewing #23. The screen offers Take again a few seconds after a pick nothing answered,
     * and the server answered nothing from out of reach - so a drafter pushed off the tables with the screen
     * up could press Take again and again, and nothing happened and nothing said why.
     */
    @GameTest(template = "tables")
    public static void aPickFromOutOfReachOfTheTablesSaysSo(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        sitFour(helper, origin);
        if (DraftPods.start(helper.getLevel(), origin, cubeOf(200), true) != DraftPods.Outcome.STARTED) {
            helper.fail("fixture: the draft did not start");
            return;
        }
        DraftPod before = DraftPods.podAt(helper.getLevel(), origin).orElseThrow();
        List<String> told = new ArrayList<>();
        // Drafter 1, forty blocks off the tables: TableReach is twelve.
        var drafter = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000001"), "far"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component line) {
                told.add(line.getString());
            }
        };
        drafter.setPos(origin.getX() + 40.5, origin.getY(), origin.getZ() + 0.5);
        route(drafter, new dev.gathering.network.DraftPickPayload(
                origin, TestTables.draftTurnAt(helper, origin), List.of(0, 1)));
        String tooFar = net.minecraft.network.chat.Component.translatable("message.gathering.draft_too_far").getString();
        if (!told.contains(tooFar)) {
            helper.fail("a pick from out of reach of the tables was dropped without a word: the drafter was told " + told);
            return;
        }
        DraftPod after = DraftPods.podAt(helper.getLevel(), origin).orElse(null);
        if (after == null || !after.state().equals(before.state())) {
            helper.fail("a pick from out of reach of the tables changed the pod");
            return;
        }
        helper.succeed();
    }

    /**
     * A pick for a table that is gone is answered too, the same way: broken while a drafter had the screen up,
     * the table took the pod with it, and Take went on being pressed into nothing.
     */
    @GameTest(template = "tables")
    public static void aPickForATableThatIsGoneSaysSo(GameTestHelper helper) {
        BlockPos nowhere = helper.absolutePos(new BlockPos(1, 2, 1));
        if (TableBlock.entityAt(helper.getLevel(), nowhere).isPresent()) {
            helper.fail("fixture: there is a table where there should be none");
            return;
        }
        List<String> told = new ArrayList<>();
        var drafter = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000001"), "near"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component line) {
                told.add(line.getString());
            }
        };
        drafter.setPos(nowhere.getX() + 0.5, nowhere.getY(), nowhere.getZ() + 2.5);
        route(drafter, new dev.gathering.network.DraftPickPayload(nowhere, 0L, List.of(0, 1)));
        String noTable = net.minecraft.network.chat.Component.translatable("message.gathering.draft_no_table").getString();
        if (!told.contains(noTable)) {
            helper.fail("a pick for a table that is gone was dropped without a word: the drafter was told " + told);
            return;
        }
        helper.succeed();
    }

    /** Sends a pick down the route a client's would take. */
    private static void route(net.minecraft.server.level.ServerPlayer player, dev.gathering.network.DraftPickPayload pick) {
        for (dev.gathering.network.GatheringProtocol.ToServer<?> route : dev.gathering.network.GatheringProtocol.TO_SERVER) {
            if (route.type().equals(pick.type())) {
                @SuppressWarnings("unchecked")
                var picking = (dev.gathering.network.GatheringProtocol.ToServer<dev.gathering.network.DraftPickPayload>) route;
                picking.handler().accept(player, pick);
                return;
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("nothing on the server takes a draft pick");
    }

    /** Three people is not a pod, and being told so is better than a draft that plays badly. */
    @GameTest(template = "tables")
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
    @GameTest(template = "tables")
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
    @GameTest(template = "tables")
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
     * <p>The one thing this test is really for. Everything else here would be noticed in the
     * first minute of a draft; a pod that comes back subtly wrong after a restart would be
     * noticed as somebody holding cards that are not theirs, an hour later, with no way to
     * work out what happened.
     */
    @GameTest(template = "tables")
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
     * <p>The pure module checks the dealing; this checks that what actually reaches a cluster
     * went through it, rather than something along the way handing the same list out four
     * times.
     */
    @GameTest(template = "tables")
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
     * <p>Side by side along x, deliberately. Seats are only ever on the north and south edges,
     * so two tables stacked north to south would bury the two edges that seat anybody and
     * come out as a cluster nobody can sit at.
     */
    private static BlockPos twoTables(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        place(helper, 4, 2, 1);
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
        return TestTables.place(helper, x, y, z);
    }
}
