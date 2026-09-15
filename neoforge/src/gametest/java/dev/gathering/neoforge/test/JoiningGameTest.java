package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.ChairBlock;
import dev.gathering.block.ChairSeat;
import dev.gathering.block.Chairs;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Legality;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.match.MatchRules;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.ChooseDeckPayload;
import dev.gathering.network.JoinTableAnswerPayload;
import dev.gathering.server.TableJoining;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Coming to a game that is on, the owner's way: sitting down at a free seat asks whether you are joining or
 * watching; a chair at an edge nobody plays at watches; a deck goes down chosen from the decks you carry,
 * not by clicking the table with one; and a deck not legal in the chosen format is asked about, then played
 * anyway and remembered for the table.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JoiningGameTest {

    private JoiningGameTest() {
    }

    /** Sitting down at a free seat of a game on takes no seat until the player says they are joining. */
    @GameTest(template = "tables")
    public static void sittingDownAtAGameAsksBeforeTakingTheSeat(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer host = hostAGame(helper, table, MatchRules.single(FormatPresets.defaultPreset()));
        ServerPlayer arriving = player(helper);
        BlockPos south = chairAt(helper, table.offset(1, 0, 3), Direction.NORTH);
        Chairs.sit(arriving, south, helper.getLevel().getBlockState(south));
        if (!(arriving.getVehicle() instanceof ChairSeat seat)) {
            helper.fail("a player sitting down at a free seat of a game on is riding " + arriving.getVehicle());
            return;
        }
        if (TableSeats.seatOf(helper.getLevel(), table, arriving.getUUID()).isPresent() || !table.equals(seat.watchingAt())) {
            helper.fail("sitting down at a game on took the seat before asking: watching " + seat.watchingAt());
            return;
        }
        TableJoining.answer(arriving, new JoinTableAnswerPayload(table, true));
        if (TableSeats.seatOf(helper.getLevel(), table, arriving.getUUID()).isEmpty()
                || TableSessions.seatIdOf(helper.getLevel(), table, arriving.getUUID()).isEmpty()) {
            helper.fail("joining from the chair did not seat the player in the game");
            return;
        }
        if (!table.equals(seat.tableOrigin()) || seat.watchingAt() != null) {
            helper.fail("the chair of a player who joined holds " + seat.tableOrigin() + " and watches " + seat.watchingAt());
            return;
        }
        if (TableSeats.seatOf(helper.getLevel(), table, host.getUUID()).isEmpty()) {
            helper.fail("somebody joining took the host's seat away");
            return;
        }
        helper.succeed();
    }

    /**
     * Watching takes no seat; once the game is over the watcher at a seat's chair can take it from there; and
     * getting up gives up only what they took.
     */
    @GameTest(template = "tables")
    public static void watchingAGameTakesNoSeat(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer host = hostAGame(helper, table, MatchRules.single(FormatPresets.defaultPreset()));
        ServerPlayer watcher = player(helper);
        BlockPos south = chairAt(helper, table.offset(1, 0, 3), Direction.NORTH);
        Chairs.sit(watcher, south, helper.getLevel().getBlockState(south));
        TableJoining.answer(watcher, new JoinTableAnswerPayload(table, false));
        if (!watcher.isPassenger() || TableSeats.seatOf(helper.getLevel(), table, watcher.getUUID()).isPresent()) {
            helper.fail("watching took a seat, or got the watcher out of the chair");
            return;
        }
        // The game over, the watcher in the seat's chair takes the seat by right-clicking the table.
        SeatId hostSeat = TableSessions.seatIdOf(helper.getLevel(), table, host.getUUID()).orElseThrow();
        TableSessions.end(helper.getLevel(), table, hostSeat, "test");
        if (TableSessions.hasSession(helper.getLevel(), table)) {
            helper.fail("fixture: the game did not end");
            return;
        }
        BlockPos middle = table.offset(1, 0, 1);
        helper.getLevel().getBlockState(middle).useItemOn(ItemStack.EMPTY, helper.getLevel(), watcher, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(middle),
                        Direction.UP, middle, false));
        if (TableSeats.seatOf(helper.getLevel(), table, watcher.getUUID()).isEmpty()) {
            helper.fail("a watcher in the chair at a free seat could not take it once the game was over");
            return;
        }
        watcher.stopRiding();
        if (TableSeats.seatOf(helper.getLevel(), table, host.getUUID()).isEmpty()) {
            helper.fail("a watcher getting up disturbed the host's seat");
            return;
        }
        helper.succeed();
    }

    /** A chair at an edge nobody plays at is sat in, and watches: no seat, and the table stays as it was. */
    @GameTest(template = "tables")
    public static void aChairAtAnEdgeNobodyPlaysAtWatches(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        hostAGame(helper, table, MatchRules.single(FormatPresets.defaultPreset()));
        ServerPlayer watcher = player(helper);
        BlockPos east = chairAt(helper, table.offset(3, 0, 1), Direction.WEST);
        Chairs.sit(watcher, east, helper.getLevel().getBlockState(east));
        if (!(watcher.getVehicle() instanceof ChairSeat seat) || !table.equals(seat.watchingAt())) {
            helper.fail("a chair at the east edge of a table played north to south did not seat a watcher");
            return;
        }
        if (TableSeats.seatOf(helper.getLevel(), table, watcher.getUUID()).isPresent()) {
            helper.fail("a chair at an edge nobody plays at took a seat");
            return;
        }
        if (!TableJoining.watchers(helper.getLevel(), table).contains(watcher)) {
            helper.fail("the table does not count the player in its chair among its watchers");
            return;
        }
        helper.succeed();
    }

    /**
     * A deck goes down chosen from the inventory. Clicking the table with a deck in hand, which used to put
     * it down, now leaves it in hand; choosing its slot puts it down.
     */
    @GameTest(template = "tables")
    public static void aDeckIsChosenFromTheInventory(GameTestHelper helper) {
        CardMetadata forest = forest(helper);
        if (forest == null) {
            return;
        }
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer host = hostAGame(helper, table, MatchRules.single(FormatPresets.defaultPreset()));
        ItemStack inHand = DeckItem.of(deckOf("In hand", forest, 60));
        host.setItemInHand(InteractionHand.MAIN_HAND, inHand);
        BlockPos middle = table.offset(1, 0, 1);
        helper.getLevel().getBlockState(middle).useItemOn(inHand, helper.getLevel(), host, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(middle),
                        Direction.UP, middle, false));
        if (library(helper, table, host) != 0 || host.getItemInHand(InteractionHand.MAIN_HAND) != inHand || inHand.isEmpty()) {
            helper.fail("clicking the table with a deck at a game on put it down");
            return;
        }
        int slot = 20;
        ItemStack chosen = DeckItem.of(deckOf("Chosen", forest, 60));
        host.getInventory().setItem(slot, chosen);
        TableJoining.choose(host, new ChooseDeckPayload(table, slot, false));
        if (library(helper, table, host) != 60 || !host.getInventory().getItem(slot).isEmpty()) {
            helper.fail("choosing the deck in slot " + slot + " left a library of " + library(helper, table, host)
                    + " and " + host.getInventory().getItem(slot));
            return;
        }
        helper.succeed();
    }

    /**
     * A deck not legal in the format somebody chose is asked about, not played and not refused outright;
     * chosen again anyway it goes down, and the table keeps what is not legal about it - through a save - to
     * tell everybody who comes to it.
     */
    @GameTest(template = "tables")
    public static void aDeckNotLegalIsAskedAboutThenPlayedAnyway(GameTestHelper helper) {
        CardMetadata forest = forest(helper);
        if (forest == null) {
            return;
        }
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer host = hostAGame(helper, table, MatchRules.single(FormatPresets.MODERN));
        TableBlockEntity entity = TableBlock.entityAt(helper.getLevel(), table).orElseThrow();
        entity.formatWasChosen(true);
        int slot = 21;
        ItemStack tooSmall = DeckItem.of(deckOf("Too small", forest, 30));
        host.getInventory().setItem(slot, tooSmall);
        TableJoining.choose(host, new ChooseDeckPayload(table, slot, false));
        if (library(helper, table, host) != 0 || host.getInventory().getItem(slot) != tooSmall) {
            helper.fail("a thirty-card deck at a Modern table went down without being asked about");
            return;
        }
        // Another deck put in that slot after the question is not the deck the player said yes to: asked about
        // in its turn, not played on the strength of an answer about a different one.
        ItemStack swapped = DeckItem.of(deckOf("Swapped in", forest, 30));
        host.getInventory().setItem(slot, swapped);
        TableJoining.choose(host, new ChooseDeckPayload(table, slot, true));
        if (library(helper, table, host) != 0 || host.getInventory().getItem(slot) != swapped) {
            helper.fail("a deck swapped into the slot after the question went down on the answer about another");
            return;
        }
        TableJoining.choose(host, new ChooseDeckPayload(table, slot, true));
        if (library(helper, table, host) != 30 || !host.getInventory().getItem(slot).isEmpty()) {
            helper.fail("a deck chosen anyway did not go down: library " + library(helper, table, host));
            return;
        }
        SeatId seat = TableSessions.seatIdOf(helper.getLevel(), table, host.getUUID()).orElseThrow();
        TableBlockEntity.NotLegal why = entity.notLegal().get(seat);
        if (why == null || !why.format().equals(FormatPresets.MODERN.displayName()) || why.problems().isEmpty()) {
            helper.fail("the table does not remember what is not legal about the deck played anyway: " + why);
            return;
        }
        net.minecraft.nbt.CompoundTag saved = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
        net.minecraft.nbt.ListTag decks = saved.getList("decks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (decks.isEmpty() || !decks.getCompound(0).getString("not_legal_in").equals(why.format())) {
            helper.fail("what is not legal about a held deck is not saved with it: " + decks);
            return;
        }
        // Somebody sitting down to watch is told; nothing to assert of a stand-in's chat but that it runs.
        ServerPlayer watcher = player(helper);
        BlockPos east = chairAt(helper, table.offset(3, 0, 1), Direction.WEST);
        Chairs.sit(watcher, east, helper.getLevel().getBlockState(east));
        if (!watcher.isPassenger()) {
            helper.fail("a watcher could not sit down at a table with a deck played anyway");
            return;
        }
        helper.succeed();
    }

    /** A host sat in the north chair of this table, with a game of these rules started. */
    private static ServerPlayer hostAGame(GameTestHelper helper, BlockPos table, MatchRules rules) {
        ServerPlayer host = player(helper);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        Chairs.sit(host, north, helper.getLevel().getBlockState(north));
        if (TableSeats.seatOf(helper.getLevel(), table, host.getUUID()).isEmpty()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("fixture: the host did not sit down");
        }
        if (TableSessions.start(helper.getLevel(), table, rules) != TableSessions.Outcome.STARTED) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("fixture: the game did not start");
        }
        return host;
    }

    private static int library(GameTestHelper helper, BlockPos table, ServerPlayer player) {
        var session = TableSessions.sessionAt(helper.getLevel(), table).orElse(null);
        SeatId seat = TableSessions.seatIdOf(helper.getLevel(), table, player.getUUID()).orElse(null);
        if (session == null || seat == null) {
            return -1;
        }
        return VisibilityRules.viewFor(session.state(), new Viewer.Seated(seat)).seat(seat).zone(Zone.LIBRARY).count();
    }

    private static DeckComponent deckOf(String name, CardMetadata card, int count) {
        List<CardComponent> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(new CardComponent(Optional.of(card.scryfallId()), false, Optional.empty(), false));
        }
        return new DeckComponent(name, "", Optional.empty(), cards, List.of(), List.of());
    }

    /** A basic Forest in the running server's card cache, legal everywhere, so the check answers at once. */
    private static CardMetadata forest(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("No card service running, so a deck cannot be checked");
            return null;
        }
        Map<String, Legality> legalities = new java.util.LinkedHashMap<>();
        for (var preset : FormatPresets.all()) {
            legalities.put(preset.legalitiesKey(), Legality.LEGAL);
        }
        CardMetadata forest = new CardMetadata(
                UUID.nameUUIDFromBytes("gathering-test:Joining Forest".getBytes()),
                UUID.nameUUIDFromBytes("gathering-test-oracle:Forest".getBytes()),
                "Forest", "", 0, "Basic Land - Forest", "", Set.of(), Set.of("G"), List.of(),
                "normal", "tst", "Test", "1", Rarity.COMMON,
                false, false, true, false, false, List.of("paper"),
                legalities, Map.of(), "");
        cards.store().store(forest, null);
        return forest;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static BlockPos chairAt(GameTestHelper helper, BlockPos where, Direction facing) {
        BlockState chair = GatheringContent.CHAIR.get().defaultBlockState().setValue(ChairBlock.FACING, facing);
        helper.getLevel().setBlock(where, chair, 3);
        return where;
    }
}
