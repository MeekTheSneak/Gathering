package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import dev.gathering.item.GatheringContent;
import java.util.List;
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
    public static void feltColorIsKeptAndIsToldToClients(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();

        if (!table.dye(DyeColor.PURPLE)) {
            helper.fail("Dyeing an undyed table changed nothing");
        }
        if (table.dye(DyeColor.PURPLE)) {
            helper.fail("Dyeing a table the color it already is counted as a change");
        }

        // Saved with the world...
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());
        TableBlockEntity reloaded = new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        if (!reloaded.felt().equals(java.util.Optional.of(DyeColor.PURPLE))) {
            helper.fail("The felt color did not survive a save and load: " + reloaded.felt());
        }

        // ...and told to a client that joins later, which is a separate tag and so a
        // separate way to lose it.
        CompoundTag update = table.getUpdateTag(helper.getLevel().registryAccess());
        if (!DyeColor.PURPLE.getSerializedName().equals(update.getString("felt"))) {
            helper.fail("A joining client is not told the felt color: " + update);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void whetherThereIsACommandZoneIsToldToClients(GameTestHelper helper) {
        // A client never receives the match, so it cannot work out whether this game has a
        // command zone - and it has to know, because it draws the box. An empty box labeled
        // with a zone the format does not have is a question every player asks once.
        BlockPos origin = place(helper, 1, 2, 1);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        if (table.hasCommandZone()) {
            helper.fail("A table with no game on it claimed to have a command zone");
        }

        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH,
                new UUID(0L, 91L));
        TableSessions.start(helper.getLevel(), origin,
                dev.gathering.core.match.MatchRules.single(
                        dev.gathering.core.format.FormatPresets.COMMANDER));
        if (!table.hasCommandZone()) {
            helper.fail("A game of Commander did not have a command zone");
        }
        if (!table.getUpdateTag(helper.getLevel().registryAccess()).getBoolean("command_zone")) {
            helper.fail("A joining client is not told the table has a command zone");
        }

        TableSessions.end(helper.getLevel(), origin, new SeatId(0), "test");
        TableSessions.start(helper.getLevel(), origin,
                dev.gathering.core.match.MatchRules.single(
                        dev.gathering.core.format.FormatPresets.MODERN));
        if (table.hasCommandZone()) {
            helper.fail("A game of Modern was given a command zone");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aGameNeedsSomebodySittingAtTheTable(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);

        if (TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules()) != TableSessions.Outcome.NOBODY_SEATED) {
            helper.fail("A game started at an empty table");
        }
        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("An empty table has a game on it");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aGameStartsWithASeatForEveryPlaceAtTheTable(GameTestHelper helper) {
        // Every seat the cluster has becomes a seat in the game, occupied or not: the shape
        // is frozen for the duration, so somebody arriving later should find a seat waiting
        // rather than a game with no room in it.
        BlockPos origin = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);
        UUID player = UUID.fromString("00000000-0000-4000-8000-00000000beef");
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player);

        if (TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules()) != TableSessions.Outcome.STARTED) {
            helper.fail("A game would not start with somebody seated");
        }

        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElse(null);
        if (session == null) {
            helper.fail("The game did not land on the table");
            return;
        }
        if (session.state().seats().size() != 4) {
            helper.fail("Two tables seat four, so the game should have four seats, got "
                    + session.state().seats().size());
        }
        if (TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules()) != TableSessions.Outcome.ALREADY_RUNNING) {
            helper.fail("A second game started on top of the first");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aGameSurvivesTheWorldBeingSavedAndLoaded(GameTestHelper helper) {
        // The whole reason a session lives on a block entity. State is the fold of the log,
        // so a log that does not come back is a board that never existed.
        BlockPos origin = place(helper, 1, 2, 1);
        UUID player = UUID.fromString("00000000-0000-4000-8000-00000000beef");
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player);
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());

        GameSession before = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        before.submit(new GameEvent.DeckLoaded(new SeatId(0), library(), List.of()));
        before.submit(new GameEvent.LibraryShuffled(new SeatId(0), new SeatId(0)));
        before.submit(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -7));

        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());

        TableBlockEntity reloaded = new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        GameSession after = reloaded.session().orElse(null);
        if (after == null) {
            helper.fail("The game did not come back after a save and load");
            return;
        }
        if (!after.state().equals(before.state())) {
            helper.fail("The game came back on a different board");
        }
        if (!after.records().equals(before.records())) {
            helper.fail("The log came back different");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSavedGameKeepsItsLibraryOutOfTheReadableHalf(GameTestHelper helper) {
        // The security property, checked where it actually lands: in the block entity's NBT,
        // which is what ends up in a world folder somebody could copy.
        BlockPos origin = place(helper, 1, 2, 1);
        UUID player = UUID.fromString("00000000-0000-4000-8000-00000000beef");
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player);
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());

        UUID printing = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        session.submit(new GameEvent.DeckLoaded(new SeatId(0),
                List.of(dev.gathering.core.card.CardIdentity.ofPrinting(printing, false)), List.of()));

        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());

        byte[] readable = saved.getByteArray("session_open");
        byte[] needle = new byte[16];
        java.nio.ByteBuffer.wrap(needle)
                .putLong(printing.getMostSignificantBits())
                .putLong(printing.getLeastSignificantBits());
        if (indexOf(readable, needle) >= 0) {
            helper.fail("A card from a library is sitting in the readable half of the save");
        }
        if (saved.getByteArray("session_sealed").length == 0) {
            helper.fail("Nothing was sealed, so nothing was protected");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTableWithAGameOnItCannotBeBroken(GameTestHelper helper) {
        // Not only because the seats would move. The game lives on one of these tables, so
        // breaking that one takes the game with it.
        BlockPos origin = place(helper, 1, 2, 1);
        UUID player = UUID.fromString("00000000-0000-4000-8000-00000000beef");
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player);
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());
        TableSeats.leave(helper.getLevel(), origin, player);

        if (TableSeats.occupiedSeats(helper.getLevel(), origin) != 0) {
            helper.fail("The seat was not given up");
        }
        if (TableSeats.mayBreak(helper.getLevel(), origin)) {
            helper.fail("A table with a game running on it could be broken");
        }

        TableSessions.end(helper.getLevel(), origin, new SeatId(0), "test");

        if (!TableSeats.mayBreak(helper.getLevel(), origin)) {
            helper.fail("The table was still locked after the game ended");
        }
        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("The game outlived being ended");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneplayerCanGoldfishAlone(GameTestHelper helper) {
        // Sandbox mode, which the design says ships in the first playable phase: all the same
        // verbs, no other humans required. It is not a separate mode - it is one person at a
        // table, and it has to work without anything special being asked for.
        BlockPos origin = place(helper, 1, 2, 1);
        UUID solo = new UUID(0L, 77L);
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, solo);

        if (TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules()) != TableSessions.Outcome.STARTED) {
            helper.fail("One player alone could not start a game");
        }
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        session.submit(new GameEvent.DeckLoaded(new SeatId(0), library(), List.of()));
        session.submit(new GameEvent.LibraryShuffled(new SeatId(0), new SeatId(0)));
        session.submit(new GameEvent.CardsDrawn(new SeatId(0), new SeatId(0), 7));

        if (session.state().contents(new SeatId(0), dev.gathering.core.game.Zone.HAND).size() != 7) {
            helper.fail("A solo player could not draw an opening hand");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoPlayersGetOppositeEdgesAndCannotReadEachOther(GameTestHelper helper) {
        // The game this mod is for. One person at a table is the easy case and the one every
        // other test here happens to exercise; two people facing each other is where the seats
        // have to end up on opposite edges, where the boards have to face each other, and
        // where hidden information has to actually be hidden.
        BlockPos origin = place(helper, 1, 2, 1);
        UUID north = new UUID(0L, 11L);
        UUID south = new UUID(0L, 22L);
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, north);
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.SOUTH, south);

        SeatAnchor mine = TableSeats.seatOf(helper.getLevel(), origin, north).orElse(null);
        SeatAnchor theirs = TableSeats.seatOf(helper.getLevel(), origin, south).orElse(null);
        if (mine == null || theirs == null) {
            helper.fail("Two players could not both sit at one table");
            return;
        }
        if (mine.side() == theirs.side()) {
            helper.fail("Both players ended up at the same edge: " + mine.side());
            return;
        }

        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        SeatId first = new SeatId(0);
        SeatId second = new SeatId(1);
        for (SeatId seat : List.of(first, second)) {
            session.submit(new GameEvent.DeckLoaded(seat, library(), List.of()));
            session.submit(new GameEvent.LibraryShuffled(seat, seat));
            session.submit(new GameEvent.CardsDrawn(seat, seat, 7));
        }

        // Each of them holds seven cards and can read their own; neither can read the other's.
        dev.gathering.core.game.visibility.GameView asFirst =
                dev.gathering.core.game.visibility.VisibilityRules.viewFor(
                        session.state(),
                        dev.gathering.core.game.visibility.Viewer.seat(first),
                        session.log());
        long readable = asFirst.seat(first).zone(dev.gathering.core.game.Zone.HAND).cards().stream()
                .filter(card -> card instanceof dev.gathering.core.game.visibility.CardView.Visible)
                .count();
        long theirsReadable = asFirst.seat(second).zone(dev.gathering.core.game.Zone.HAND).cards().stream()
                .filter(card -> card instanceof dev.gathering.core.game.visibility.CardView.Visible)
                .count();
        if (readable != 7) {
            helper.fail("A player could read " + readable + " of their own seven cards");
            return;
        }
        if (theirsReadable != 0) {
            helper.fail("A player could read " + theirsReadable + " cards in the other hand");
            return;
        }
        if (asFirst.seat(second).zone(dev.gathering.core.game.Zone.HAND).count() != 7) {
            helper.fail("A player could not see how many cards the other one was holding");
            return;
        }

        // And the two mats face each other rather than both facing the same way, which is what
        // makes the near edge of the board the near edge for both of them.
        dev.gathering.core.ui.TableSurface surface =
                dev.gathering.core.ui.TableSurface.forSeatCount(session.state().seats().size());
        if (surface.isTurned(0) == surface.isTurned(1)) {
            helper.fail("Both boards were laid out the same way up");
            return;
        }
        helper.succeed();
    }

    private static List<dev.gathering.core.card.CardIdentity> library() {
        List<dev.gathering.core.card.CardIdentity> cards = new java.util.ArrayList<>();
        for (int index = 0; index < 20; index++) {
            cards.add(dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(0, index), false));
        }
        return cards;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
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
