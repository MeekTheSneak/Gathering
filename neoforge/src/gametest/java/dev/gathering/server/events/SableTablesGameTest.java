package dev.gathering.server.events;

import dev.gathering.Gathering;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.core.table.Side;
import dev.gathering.item.GatheringContent;
import dev.gathering.platform.WorldSpace;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/**
 * Tables on Sable's moving structures, which Create Aeronautics builds its vehicles and physics
 * objects from.
 * <p>Registered only when Sable is installed - see {@link PackGameTests} - so the gate, which
 * runs without it, neither runs these nor counts them. {@code ./gradlew runPackGameTestServer}
 * with Sable in {@code neoforge/runs/pack-tests/mods} does.
 * <p>Each one builds an ordinary table, has Sable carry it off into a structure of its own, and
 * asks the questions that compared a table's block position with the world: where it is, which
 * side a player is standing at, and where a player is seated.
 */
@PrefixGameTestTemplate(false)
public final class SableTablesGameTest {

    private SableTablesGameTest() {
    }

    /**
     * A table holding a deck, carried into a structure, still holds it - once. The deck is either
     * on the table where it went or handed back where it was, and never both: a copy of the table's
     * keeping on the structure beside a deck spilled from the table it replaced would be a deck made
     * out of nothing.
     */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aDeckOnATableCarriedOffIsNeitherLostNorDoubled(GameTestHelper helper) {
        BlockPos placed = place(helper, 2, 2, 2);
        var deck = new dev.gathering.item.DeckComponent("Carried", "", java.util.Optional.empty(),
                List.of(dev.gathering.item.CardComponent.of(dev.gathering.core.card.CardIdentity.ofPrinting(new java.util.UUID(5L, 5L)))),
                List.of(), List.of());
        TableBlock.entityAt(helper.getLevel(), placed).orElseThrow()
                .holdDeck(new dev.gathering.core.game.SeatId(0), deck, null, null);
        Assembled assembled = assemble(helper, placed);
        if (assembled == null) {
            return;
        }
        int onTheTable = TableBlock.entityAt(helper.getLevel(), assembled.table())
                .map(entity -> entity.heldDecks().size()).orElse(0);
        long spilled = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(placed).inflate(8.0d)).stream()
                .filter(item -> dev.gathering.item.DeckItem.deckOf(item.getItem()).isPresent()).count();
        System.out.println("[sable] a carried table holds " + onTheTable + " deck(s); " + spilled + " spilled where it was");
        if (onTheTable + spilled != 1) {
            helper.fail("a table holding one deck, carried into a structure, left " + onTheTable
                    + " on the table and " + spilled + " on the ground");
            return;
        }
        // And set down again, the way Create Aeronautics takes a ship apart: its blocks moved back
        // into the world, a few blocks along from where the table first stood.
        BlockPos down = placed.offset(5, 0, 0);
        List<BlockPos> onTheShip = new ArrayList<>();
        for (TablePart part : TablePart.values()) {
            onTheShip.add(part.offsetFrom(assembled.table()).immutable());
        }
        SubLevelAssemblyHelper.moveBlocks(helper.getLevel(), new SubLevelAssemblyHelper.AssemblyTransform(assembled.table(), down, 0,
                net.minecraft.world.level.block.Rotation.NONE, helper.getLevel()), onTheShip);
        int setDown = TableBlock.entityAt(helper.getLevel(), down).map(entity -> entity.heldDecks().size()).orElse(-1);
        long spilledNow = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(placed).inflate(12.0d)).stream()
                .filter(item -> dev.gathering.item.DeckItem.deckOf(item.getItem()).isPresent()).count();
        System.out.println("[sable] set down again, the table holds " + setDown + " deck(s); " + spilledNow + " on the ground");
        if (setDown != 1 || spilledNow != 0) {
            helper.fail("a table carried off and set down again holds " + setDown + " decks, with " + spilledNow + " on the ground");
            return;
        }
        helper.succeed();
    }

    /**
     * A game being played at a table goes with the table when it is carried off: the table on the
     * structure has the game, with everything that had happened in it, and none is left where it stood.
     */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aGameInProgressGoesOnAboardTheStructure(GameTestHelper helper) {
        BlockPos placed = place(helper, 2, 2, 2);
        var seats = TableClusters.at(helper.getLevel(), placed).seats();
        dev.gathering.block.TableSeats.take(helper.getLevel(), placed, seats.get(0).cell(), seats.get(0).side(), new java.util.UUID(8L, 1L));
        dev.gathering.block.TableSeats.take(helper.getLevel(), placed, seats.get(1).cell(), seats.get(1).side(), new java.util.UUID(8L, 2L));
        var started = dev.gathering.block.TableSessions.start(helper.getLevel(), placed,
                dev.gathering.core.match.MatchRules.single(dev.gathering.core.format.FormatPresets.MODERN));
        var session = dev.gathering.block.TableSessions.sessionAt(helper.getLevel(), placed).orElse(null);
        if (session == null) {
            helper.fail("no game started to carry: " + started);
            return;
        }
        int logged = session.log().size();
        Assembled assembled = assemble(helper, placed);
        if (assembled == null) {
            return;
        }
        var aboard = dev.gathering.block.TableSessions.sessionAt(helper.getLevel(), assembled.table()).orElse(null);
        System.out.println("[sable] a game carried aboard: " + (aboard == null ? "gone" : aboard.log().size() + " log lines, was " + logged));
        if (aboard == null) {
            helper.fail("the game at a table carried into a structure did not go with it");
            return;
        }
        if (aboard.log().size() < logged) {
            helper.fail("the game carried aboard has " + aboard.log().size() + " log lines, not the " + logged + " it had");
            return;
        }
        if (dev.gathering.block.TableSessions.sessionAt(helper.getLevel(), placed).isPresent()) {
            helper.fail("a game is still being played where the table stood");
            return;
        }
        helper.succeed();
    }

    /** A tournament's table carried into a structure is still that tournament's table, with its number. */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aTournamentTableCarriedOffKeepsItsNumber(GameTestHelper helper) {
        BlockPos placed = place(helper, 2, 2, 2);
        EventState state = EventBoardGameTest.fourPlayerEvent(helper, placed);
        try {
            Assembled assembled = assemble(helper, placed);
            if (assembled == null) {
                return;
            }
            System.out.println("[sable] a carried tournament table is numbered at " + state.tables);
            if (!state.tables.equals(List.of(assembled.table()))) {
                helper.fail("the tournament lists its table at " + state.tables + ", not where it was carried to at "
                        + assembled.table());
                return;
            }
            if (EventBoard.at(helper.getLevel(), assembled.table()).map(EventBoard.Board::thisTable).orElse(0) != 1) {
                helper.fail("the carried table is no longer table 1 of its tournament");
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** A Scorekeeper's Desk carried into a structure takes signing up with it, to where it went. */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aDeskCarriedOffTakesSigningUpWithIt(GameTestHelper helper) {
        BlockPos desk = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(desk, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState(), 3);
        var tournament = dev.gathering.core.tournament.Tournament.create(java.util.UUID.randomUUID(), "Aloft",
                new java.util.UUID(3L, 3L), dev.gathering.core.tournament.EventSettings.usual(
                        dev.gathering.core.tournament.EventSettings.Kind.CONSTRUCTED, "modern"));
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of());
        Events.putForTesting(state);
        try {
            if (helper.getLevel().getBlockEntity(desk) instanceof dev.gathering.block.ScorekeepersDeskBlockEntity entity) {
                entity.runs(tournament.id());
            }
            state.registrationPoint = desk;
            ServerSubLevel structure = SubLevelAssemblyHelper.assembleBlocks(helper.getLevel(), desk, List.of(desk),
                    new BoundingBox3i(desk, desk));
            if (structure == null) {
                helper.fail("Sable did not assemble the desk into a structure");
                return;
            }
            BlockPos at = state.registrationPoint;
            System.out.println("[sable] a carried desk's tournament signs up at " + at);
            if (at == null || at.equals(desk)
                    || !(helper.getLevel().getBlockEntity(at) instanceof dev.gathering.block.ScorekeepersDeskBlockEntity)) {
                helper.fail("a desk carried into a structure left signing up at " + at);
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** A table carried into a structure is still where it was, in the world. */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aTableOnAStructureIsWhereItWasInTheWorld(GameTestHelper helper) {
        BlockPos placed = place(helper, 2, 2, 2);
        Vec3 before = Vec3.atCenterOf(placed);
        Assembled assembled = assemble(helper, placed);
        if (assembled == null) {
            return;
        }
        if (assembled.table().equals(placed)) {
            helper.fail("the table was not carried into the structure's own region, so nothing was tested");
            return;
        }
        Vec3 after = WorldSpace.get().centerInWorld(helper.getLevel(), assembled.table());
        if (after.distanceTo(before) > 0.25) {
            helper.fail("a table carried into a structure is at " + after + ", not where it was at " + before);
            return;
        }
        Vec3 back = WorldSpace.get().toLocalOf(helper.getLevel(), assembled.table(), before);
        if (back.distanceTo(Vec3.atCenterOf(assembled.table())) > 0.25) {
            helper.fail("where the table was in the world is " + back + " on the structure, not its own center");
            return;
        }
        helper.succeed();
    }

    /**
     * A player standing at a table's west edge is at its west side, however the structure has
     * turned - worked out in the table's coordinates, not by comparing a far-off block position
     * with where the player is.
     */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aPlayerAtATurnedTableIsAtTheSideTheyStandAt(GameTestHelper helper) {
        BlockPos placed = place(helper, 2, 2, 2);
        Assembled assembled = assemble(helper, placed);
        if (assembled == null) {
            return;
        }
        // A quarter turn, about the structure's own rotation point.
        assembled.structure().logicalPose().orientation().set(new Quaterniond().rotateY(Math.toRadians(90)));
        BlockPos table = assembled.table();
        var level = helper.getLevel();
        float yaw = WorldSpace.get().yawOf(level, table);
        if (Math.abs(Math.abs(yaw) - 90f) > 1f) {
            helper.fail("a structure turned a quarter reads as turned " + yaw + " degrees");
            return;
        }
        // Two blocks off the table's west edge, on the table's own terms, and where that is in the world.
        Vec3 westOfIt = new Vec3(table.getX() - 2.0, table.getY() + 1.0, table.getZ() + 1.0);
        Vec3 standing = WorldSpace.get().toWorld(level, westOfIt);
        Side side = TableClusters.sideFrom(Direction.UP, WorldSpace.get().toLocalOf(level, table, standing), table);
        if (side != Side.WEST) {
            helper.fail("a player standing west of a turned table was put at its " + side + " side");
            return;
        }
        helper.succeed();
    }

    /**
     * Where a player is moved to sit at a table on a structure is beside the table in the world,
     * facing it as the structure has turned.
     * <p>The place worked out rather than a player moved there: Sable sends every real player a
     * payload the test server's stand-in players cannot receive, so a test with one fails before
     * it gets to the question.
     */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aChairAtATableOnAStructureIsBesideItInTheWorld(GameTestHelper helper) {
        BlockPos placed = place(helper, 2, 2, 2);
        Assembled assembled = assemble(helper, placed);
        if (assembled == null) {
            return;
        }
        var seats = TableClusters.at(helper.getLevel(), assembled.table()).seats();
        if (seats.isEmpty()) {
            helper.fail("a table carried into a structure has no seats");
            return;
        }
        Events.Chair chair = Events.chairInWorld(helper.getLevel(), assembled.table(), seats.get(0));
        // Against where the table was put down, which assembling it does not move - not against the
        // same conversion the chair is worked out with, which would agree with itself however wrong.
        Vec3 table = Vec3.atCenterOf(placed);
        if (chair.where().distanceTo(table) > 3.0) {
            helper.fail("a chair at a table on a structure is at " + chair.where() + ", not beside the table at " + table);
            return;
        }
        helper.succeed();
    }

    private record Assembled(ServerSubLevel structure, BlockPos table) {
    }

    /** Has Sable carry the table's blocks into a structure, and finds the table there. */
    private static Assembled assemble(GameTestHelper helper, BlockPos origin) {
        if (!(WorldSpace.get() instanceof dev.gathering.neoforge.compat.SableWorldSpace)) {
            helper.fail("the world-space service in use is " + WorldSpace.get() + ", not Sable's");
            return null;
        }
        List<BlockPos> blocks = new ArrayList<>();
        for (TablePart part : TablePart.values()) {
            blocks.add(part.offsetFrom(origin).immutable());
        }
        BlockPos min = origin;
        BlockPos max = origin.offset(1, 0, 1);
        ServerSubLevel structure = SubLevelAssemblyHelper.assembleBlocks(helper.getLevel(), origin, blocks,
                new BoundingBox3i(min, max));
        if (structure == null) {
            helper.fail("Sable did not assemble the table into a structure");
            return null;
        }
        BoundingBox3ic box = structure.getPlot().getBoundingBox();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (helper.getLevel().getBlockEntity(at) instanceof TableBlockEntity entity) {
                        return new Assembled(structure, entity.getBlockPos());
                    }
                }
            }
        }
        helper.fail("no table was found in the assembled structure's region " + box);
        return null;
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        var table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
