package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.match.MatchRules;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.PracticeTable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The guided first game's one hard rule: nothing in it can become property.
 * <p>A practice game is a real session with real events at a real table, which is what makes
 * it worth having and also what makes it worth checking. Everything here is about the ways a
 * card could get out: handed back at the end, dropped on the floor, staked in a pot, or left
 * behind for whoever sits down next.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PracticeTableGameTest {

    /** Builds a two-by-two table at the helper's origin and hands back the cluster origin. */
    private static BlockPos aTable(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var state = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    state.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    /** How many deck items this player is carrying, anywhere they could reach. */
    private static int decksCarriedBy(ServerPlayer player) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (DeckItem.deckOf(stack).isPresent()) {
                found += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (DeckItem.deckOf(stack).isPresent()) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** How many items of any kind are lying on the floor near the table. */
    private static int itemsOnTheFloorNear(GameTestHelper helper, BlockPos origin) {
        return helper.getLevel()
                .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(origin).inflate(6))
                .size();
    }

    /** A practice game deals a real board that the learner can actually play on. */
    @GameTest(template = "empty")
    public static void practicedealsarealboard(GameTestHelper helper) {
        ServerPlayer learner = helper.makeMockServerPlayerInLevel();
        BlockPos origin = aTable(helper);
        try {
            PracticeTable.Outcome how = PracticeTable.start(learner, origin);
            if (how != PracticeTable.Outcome.STARTED) {
                helper.fail("a practice game would not start on an empty table: " + how);
                return;
            }
            if (!PracticeTable.isPracticeAt(helper.getLevel(), origin)) {
                helper.fail("the table does not know its game is practice");
                return;
            }
            SeatId mine = TableSessions.seatIdOf(helper.getLevel(), origin, learner.getUUID())
                    .orElse(null);
            if (mine == null) {
                helper.fail("the learner was not seated");
                return;
            }
            var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
            GameView board = VisibilityRules.viewFor(session.state(), new Viewer.Seated(mine));
            if (board.seat(mine).zone(Zone.LIBRARY).count() < 10) {
                helper.fail("the learner has nothing to draw from: "
                        + board.seat(mine).zone(Zone.LIBRARY).count() + " cards");
                return;
            }
            // And something to read that somebody else has played.
            SeatId theirs = TableSessions
                    .seatIdOf(helper.getLevel(), origin, PracticeTable.demonstrationSeat())
                    .orElse(null);
            if (theirs == null) {
                helper.fail("there is no demonstration seat");
                return;
            }
            if (board.seat(theirs).zone(Zone.BATTLEFIELD).count() != 1) {
                helper.fail("the demonstration seat has "
                        + board.seat(theirs).zone(Zone.BATTLEFIELD).count()
                        + " cards face up rather than one");
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    /**
     * Ending a practice game hands nothing to anybody.
     * <p>The rule the whole feature rests on. An ordinary game gives the deck back; a practice
     * game has nothing to give back, and must not invent something.
     */
    @GameTest(template = "empty")
    public static void endingpracticehandsnothingover(GameTestHelper helper) {
        ServerPlayer learner = helper.makeMockServerPlayerInLevel();
        learner.getInventory().clearContent();
        BlockPos origin = aTable(helper);
        int litterBefore = itemsOnTheFloorNear(helper, origin);
        try {
            if (PracticeTable.start(learner, origin) != PracticeTable.Outcome.STARTED) {
                helper.fail("the fixture would not start");
                return;
            }
            PracticeTable.stop(helper.getLevel(), origin);

            if (decksCarriedBy(learner) != 0) {
                helper.fail("a practice game handed the learner "
                        + decksCarriedBy(learner) + " deck(s)");
                return;
            }
            if (itemsOnTheFloorNear(helper, origin) != litterBefore) {
                helper.fail("a practice game dropped items on the floor when it ended");
                return;
            }
            if (TableSessions.hasSession(helper.getLevel(), origin)) {
                helper.fail("the practice session outlived the practice game");
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    /**
     * Leaving the table mid-practice hands nothing over either.
     * <p>The other way out, and a different code path: standing up returns one seat's deck
     * rather than ending the game.
     */
    @GameTest(template = "empty")
    public static void leavingmidpracticehandsnothingover(GameTestHelper helper) {
        ServerPlayer learner = helper.makeMockServerPlayerInLevel();
        learner.getInventory().clearContent();
        BlockPos origin = aTable(helper);
        try {
            if (PracticeTable.start(learner, origin) != PracticeTable.Outcome.STARTED) {
                helper.fail("the fixture would not start");
                return;
            }
            SeatId mine = TableSessions.seatIdOf(helper.getLevel(), origin, learner.getUUID())
                    .orElseThrow();
            TableSessions.returnDeckTo(helper.getLevel(), origin, mine);
            if (decksCarriedBy(learner) != 0) {
                helper.fail("standing up mid-practice handed over a deck");
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    /** A practice table refuses to be played for keeps, so nothing can be staked. */
    @GameTest(template = "empty")
    public static void practicecannotbeplayedforkeeps(GameTestHelper helper) {
        ServerPlayer learner = helper.makeMockServerPlayerInLevel();
        BlockPos origin = aTable(helper);
        try {
            if (PracticeTable.start(learner, origin) != PracticeTable.Outcome.STARTED) {
                helper.fail("the fixture would not start");
                return;
            }
            var table = TableSessions.anchorOf(helper.getLevel(), origin)
                    .flatMap(anchor -> TableBlock.entityAt(helper.getLevel(), anchor))
                    .orElseThrow();
            table.playForKeeps(true);
            if (table.playingForKeeps()) {
                helper.fail("a practice table agreed to play for keeps");
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    /** Practice never takes over a table somebody is really playing at. */
    @GameTest(template = "empty")
    public static void practicewillnottakeoveraliveGame(GameTestHelper helper) {
        ServerPlayer learner = helper.makeMockServerPlayerInLevel();
        BlockPos origin = aTable(helper);
        try {
            var seats = dev.gathering.block.TableClusters.at(helper.getLevel(), origin).seats();
            TableSeats.take(helper.getLevel(), origin, seats.get(0).cell(), seats.get(0).side(),
                    learner.getUUID());
            TableSessions.start(helper.getLevel(), origin,
                    new MatchRules(FormatPresets.COMMANDER, 1));

            PracticeTable.Outcome how = PracticeTable.start(learner, origin);
            if (how != PracticeTable.Outcome.GAME_RUNNING) {
                helper.fail("practice started on a table with a game already on it: " + how);
                return;
            }
            if (PracticeTable.isPracticeAt(helper.getLevel(), origin)) {
                helper.fail("a real game was turned into a practice game");
                return;
            }
            helper.succeed();
        } finally {
            TableSessions.end(helper.getLevel(), origin, null, "test over");
        }
    }

    /** Nor a table somebody else is sitting at, even with no game on it. */
    @GameTest(template = "empty")
    public static void practicewillnottakesomebodyelsestable(GameTestHelper helper) {
        ServerPlayer learner = helper.makeMockServerPlayerInLevel();
        BlockPos origin = aTable(helper);
        try {
            var seats = dev.gathering.block.TableClusters.at(helper.getLevel(), origin).seats();
            TableSeats.take(helper.getLevel(), origin, seats.get(0).cell(), seats.get(0).side(),
                    UUID.randomUUID());

            PracticeTable.Outcome how = PracticeTable.start(learner, origin);
            if (how != PracticeTable.Outcome.SOMEBODY_ELSE_HERE) {
                helper.fail("practice took a table somebody else was sitting at: " + how);
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    /**
     * A practice deck the table somehow ends up holding is still never handed over.
     * <p>The belt to the braces. Practice does not ask the table to hold a deck, so this
     * cannot happen today - which is exactly why it is worth pinning: a later change that made
     * practice go through the ordinary deck-placement path would otherwise silently start
     * minting decks, and every other test here would still pass.
     */
    @GameTest(template = "empty")
    public static void adeckthetableholdsinpracticeisstillneverhandedover(GameTestHelper helper) {
        ServerPlayer learner = helper.makeMockServerPlayerInLevel();
        learner.getInventory().clearContent();
        BlockPos origin = aTable(helper);
        try {
            if (PracticeTable.start(learner, origin) != PracticeTable.Outcome.STARTED) {
                helper.fail("the fixture would not start");
                return;
            }
            SeatId mine = TableSessions.seatIdOf(helper.getLevel(), origin, learner.getUUID())
                    .orElseThrow();
            var table = TableSessions.anchorOf(helper.getLevel(), origin)
                    .flatMap(anchor -> TableBlock.entityAt(helper.getLevel(), anchor))
                    .orElseThrow();
            // Force the table to hold a real deck, the way an ordinary game would.
            var card = dev.gathering.item.CardComponent.of(
                    CardIdentity.ofPrinting(UUID.randomUUID()));
            table.holdDeck(mine, new DeckComponent("Smuggled", "", Optional.empty(),
                    List.of(card), List.of(), List.of()), null, learner.getUUID());

            PracticeTable.stop(helper.getLevel(), origin);

            if (decksCarriedBy(learner) != 0) {
                helper.fail("a deck held by a practice table was handed to the learner");
                return;
            }
            if (itemsOnTheFloorNear(helper, origin) != 0) {
                helper.fail("a deck held by a practice table was dropped on the floor");
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    /** Stopping something that is not a practice game does nothing at all. */
    @GameTest(template = "empty")
    public static void stoppingsomethingthatisnotpracticedoesnothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = aTable(helper);
        try {
            var seats = dev.gathering.block.TableClusters.at(helper.getLevel(), origin).seats();
            TableSeats.take(helper.getLevel(), origin, seats.get(0).cell(), seats.get(0).side(),
                    player.getUUID());
            TableSessions.start(helper.getLevel(), origin,
                    new MatchRules(FormatPresets.COMMANDER, 1));

            PracticeTable.stop(helper.getLevel(), origin);

            if (!TableSessions.hasSession(helper.getLevel(), origin)) {
                helper.fail("stopping practice ended a real game");
                return;
            }
            helper.succeed();
        } finally {
            TableSessions.end(helper.getLevel(), origin, null, "test over");
        }
    }
}
