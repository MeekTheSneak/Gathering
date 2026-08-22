package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import dev.gathering.item.GatheringContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tables in an actual world.
 *
 * <p>Which tables join up and where the seats go is worked out in the pure core and checked
 * there over every shape somebody can build. What these check is the part that only exists in
 * a world: that four blocks go down and come up together, that the corner owning the table is
 * the one carrying the block entity, and that the world coordinates handed to the pure
 * arithmetic are the ones it was expecting.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableGameTest {

    @GameTest(template = "empty")
    public static void aTableIsFourBlocksWithOneOwner(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);

        for (TablePart part : TablePart.values()) {
            BlockPos pos = part.offsetFrom(origin);
            BlockState state = helper.getLevel().getBlockState(pos);
            if (!(state.getBlock() instanceof TableBlock)) {
                helper.fail("The " + part + " quarter of the table is missing");
                return;
            }
            if (state.getValue(TableBlock.PART) != part) {
                helper.fail("The block at " + part + " thinks it is " + state.getValue(TableBlock.PART));
            }

            boolean hasEntity = helper.getLevel().getBlockEntity(pos) instanceof TableBlockEntity;
            if (hasEntity != part.isOrigin()) {
                helper.fail("Block entity in the wrong place: " + part + " has one? " + hasEntity);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingOneQuarterTakesTheWholeTable(GameTestHelper helper) {
        // A table missing a corner is not a smaller table.
        BlockPos origin = place(helper, 1, 2, 1);

        helper.getLevel().destroyBlock(TablePart.SOUTH_EAST.offsetFrom(origin), false);

        for (TablePart part : TablePart.values()) {
            BlockPos pos = part.offsetFrom(origin);
            if (helper.getLevel().getBlockState(pos).getBlock() instanceof TableBlock) {
                helper.fail("The " + part + " quarter survived the table being broken");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneTableIsOneClusterSeatingTwo(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);

        TableCluster cluster = TableClusters.at(helper.getLevel(), origin);

        if (cluster.tableCount() != 1 || cluster.capacity() != 2) {
            helper.fail("One table should be one cluster seating two, got " + cluster.tableCount()
                    + " tables and " + cluster.capacity() + " seats");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tablesPushedTogetherBecomeOneCluster(GameTestHelper helper) {
        // The gesture the whole design is built on.
        BlockPos first = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);

        TableCluster cluster = TableClusters.at(helper.getLevel(), first);

        if (cluster.tableCount() != 2 || cluster.capacity() != 4) {
            helper.fail("Two tables pushed together should seat four, got " + cluster.capacity()
                    + " across " + cluster.tableCount() + " tables");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tablesWithAGapBetweenThemStaySeparate(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        place(helper, 5, 2, 1);

        if (TableClusters.at(helper.getLevel(), first).tableCount() != 1) {
            helper.fail("Tables with a gap between them merged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFifthTableWillNotJoinAFullCluster(GameTestHelper helper) {
        // A cluster is capped, and the answer has to arrive when the table is placed rather
        // than as a table that sits next to a cluster without being part of it.
        BlockPos first = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);
        place(helper, 5, 2, 1);
        place(helper, 7, 2, 1);

        if (TableClusters.at(helper.getLevel(), first).tableCount() != 4) {
            helper.fail("Four tables in a row did not make one cluster of four");
        }
        if (TableClusters.wouldFit(helper.getLevel(), helper.absolutePos(new BlockPos(9, 2, 1)))) {
            helper.fail("A fifth table was allowed to join a full cluster");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everySeatIsOnTheOutsideOfTheCluster(GameTestHelper helper) {
        // The world coordinates, which the pure arithmetic cannot check on its own: a table
        // is two blocks across, so its far edges are one further out than its corner.
        BlockPos first = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);

        TableCluster cluster = TableClusters.at(helper.getLevel(), first);
        for (SeatAnchor seat : cluster.seats()) {
            BlockPos seatPos = TableClusters.seatPos(first, seat);
            if (helper.getLevel().getBlockState(seatPos).getBlock() instanceof TableBlock) {
                helper.fail("Seat " + seat + " is inside the table, at " + seatPos);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSeatIsTakenOnceAndSurvivesASaveAndLoad(GameTestHelper helper) {
        // A seat is a registration, and the design says leaving does not drop it - so it has
        // to be written with the table and come back with it.
        BlockPos origin = place(helper, 1, 2, 1);
        UUID player = UUID.fromString("00000000-0000-4000-8000-00000000beef");
        UUID other = UUID.fromString("00000000-0000-4000-8000-00000000face");
        TableCell here = new TableCell(0, 0);

        if (TableSeats.take(helper.getLevel(), origin, here, Side.NORTH, player) != TableSeats.Claim.TAKEN) {
            helper.fail("Could not take a free seat");
        }
        if (TableSeats.take(helper.getLevel(), origin, here, Side.NORTH, other)
                != TableSeats.Claim.OCCUPIED) {
            helper.fail("Two people took the same seat");
        }
        if (TableSeats.take(helper.getLevel(), origin, here, Side.SOUTH, player)
                != TableSeats.Claim.ALREADY_SEATED) {
            helper.fail("One player took two seats at the same cluster");
        }
        if (TableSeats.take(helper.getLevel(), origin, here, Side.EAST, other)
                != TableSeats.Claim.NOT_A_SEAT) {
            helper.fail("Somebody sat on an edge that is not a seat");
        }

        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());

        TableBlockEntity reloaded = new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        if (!reloaded.occupantOf(Side.NORTH).equals(java.util.Optional.of(player))) {
            helper.fail("A seat did not survive a save and load: " + reloaded.occupantOf(Side.NORTH));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTableInUseCannotBeBrokenOrExtended(GameTestHelper helper) {
        // Adding or removing a table reshapes the perimeter, which moves the seats. Somebody
        // registered at an edge should not find that edge is now the middle of the surface.
        BlockPos origin = place(helper, 1, 2, 1);
        UUID player = UUID.fromString("00000000-0000-4000-8000-00000000beef");
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player);

        if (TableSeats.mayBreak(helper.getLevel(), origin)) {
            helper.fail("A table with somebody seated at it could be broken");
        }
        if (!TableSeats.isShapeFrozen(helper.getLevel(), origin)) {
            helper.fail("The cluster's shape is not frozen while somebody is seated");
        }

        TableSeats.leave(helper.getLevel(), origin, player);

        if (!TableSeats.mayBreak(helper.getLevel(), origin)) {
            helper.fail("An empty table still could not be broken");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTableCannotBeBurntOrPushedApart(GameTestHelper helper) {
        // Both of these break a table in a way nothing else can: lava takes it out from under
        // a game nobody agreed to end, and a piston moves one quarter and leaves three.
        //
        // The lava half is here because the first version of this block called
        // ignitedByLava(), which switches lava ignition *on*, under a comment saying the
        // table was not flammable.
        BlockState state = GatheringContent.TABLE.get().defaultBlockState();

        if (state.ignitedByLava()) {
            helper.fail("A table burns");
        }
        if (state.getPistonPushReaction() != PushReaction.BLOCK) {
            helper.fail("A piston can move a table, which moves one quarter of it: "
                    + state.getPistonPushReaction());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void feltColourIsKeptAndIsToldToClients(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();

        if (!table.dye(DyeColor.PURPLE)) {
            helper.fail("Dyeing an undyed table changed nothing");
        }
        if (table.dye(DyeColor.PURPLE)) {
            helper.fail("Dyeing a table the colour it already is counted as a change");
        }

        // Saved with the world...
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());
        TableBlockEntity reloaded = new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        if (!reloaded.felt().equals(java.util.Optional.of(DyeColor.PURPLE))) {
            helper.fail("The felt colour did not survive a save and load: " + reloaded.felt());
        }

        // ...and told to a client that joins later, which is a separate tag and so a
        // separate way to lose it.
        CompoundTag update = table.getUpdateTag(helper.getLevel().registryAccess());
        if (!DyeColor.PURPLE.getSerializedName().equals(update.getString("felt"))) {
            helper.fail("A joining client is not told the felt colour: " + update);
        }
        helper.succeed();
    }

    /** Places a whole table with its corner at these relative coordinates. */
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
