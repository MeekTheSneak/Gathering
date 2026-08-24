package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCluster;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A game in progress survives the server going away and coming back.
 *
 * <p>The deliverable this is for is stated plainly in the brief: persistence across restart.
 * Everything else can be retried; a Commander game an hour in cannot, and a table that comes
 * back empty is the single worst thing this mod could do to a group of friends.
 *
 * <p>Tested through the block entity's own save and load rather than through the codec alone,
 * because the codec is only the middle of it: the hidden half of the log is sealed on the way
 * out and has to be opened again on the way back, and a session that writes but does not open
 * is indistinguishable from one that was never written.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SessionSurvivalGameTest {

    private SessionSurvivalGameTest() {
    }

    @GameTest(template = "empty")
    public static void aGameInProgressSurvivesARestart(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, 1));

        SeatId me = new SeatId(0);
        GameSession before = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        before.submit(new GameEvent.DeckLoaded(me, library(6), List.of(commander())));
        before.submit(new GameEvent.CardsDrawn(me, me, 3));
        before.submit(new GameEvent.LifeChanged(me, me, -7));

        int handBefore = before.state().contents(me, Zone.HAND).size();
        int libraryBefore = before.state().contents(me, Zone.LIBRARY).size();
        int lifeBefore = before.state().seatState(me).life();
        if (handBefore != 3 || libraryBefore != 3) {
            helper.fail("The game under test did not set itself up: hand " + handBefore
                    + ", library " + libraryBefore);
            return;
        }

        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());

        TableBlockEntity reopened =
                new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reopened.setLevel(helper.getLevel());
        reopened.loadWithComponents(saved, helper.getLevel().registryAccess());

        GameSession after = reopened.session().orElse(null);
        if (after == null) {
            helper.fail("A table with a game on it came back with no game");
            return;
        }
        if (after.state().contents(me, Zone.HAND).size() != handBefore) {
            helper.fail("A hand did not survive a restart: "
                    + after.state().contents(me, Zone.HAND).size() + ", was " + handBefore);
        }
        if (after.state().contents(me, Zone.LIBRARY).size() != libraryBefore) {
            helper.fail("A library did not survive a restart: "
                    + after.state().contents(me, Zone.LIBRARY).size()
                    + ", was " + libraryBefore);
        }
        if (after.state().seatState(me).life() != lifeBefore) {
            helper.fail("A life total did not survive a restart: "
                    + after.state().seatState(me).life() + ", was " + lifeBefore);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aGameThatHasBeenRewoundSurvivesARestart(GameTestHelper helper) {
        // The format grew a record kind when rewinds became storable, and the first rewind in
        // a game used to make the table unwritable. A game nobody rewound would never have
        // shown that, so this one does.
        BlockPos origin = seatedTable(helper);
        TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, 1));

        SeatId me = new SeatId(0);
        GameSession before = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        before.submit(new GameEvent.DeckLoaded(me, library(6), List.of(commander())));
        before.submit(new GameEvent.LifeChanged(me, me, -5));
        before.submit(new GameEvent.LifeChanged(me, me, -5));

        GameSession.Result rewound = before.undo(me, 1, List.of());
        if (rewound instanceof GameSession.Result.Rejected refused) {
            helper.fail("A player could not take back their own life change: " + refused.reason());
            return;
        }
        int lifeBefore = before.state().seatState(me).life();

        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        CompoundTag saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());

        TableBlockEntity reopened =
                new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reopened.setLevel(helper.getLevel());
        reopened.loadWithComponents(saved, helper.getLevel().registryAccess());

        GameSession after = reopened.session().orElse(null);
        if (after == null) {
            helper.fail("A table somebody had rewound came back with no game at all");
            return;
        }
        if (after.state().seatState(me).life() != lifeBefore) {
            helper.fail("A rewind did not survive a restart: life is "
                    + after.state().seatState(me).life() + ", was " + lifeBefore);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ setup

    private static BlockPos seatedTable(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState table = dev.gathering.item.GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(
                    part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        TableCluster cluster = dev.gathering.block.TableClusters.at(helper.getLevel(), origin);
        List<SeatAnchor> anchors = cluster.seats();
        for (int index = 0; index < Math.min(2, anchors.size()); index++) {
            SeatAnchor anchor = anchors.get(index);
            TableSeats.take(
                    helper.getLevel(), origin, anchor.cell(), anchor.side(), new UUID(11L, index));
        }
        return origin;
    }

    private static List<CardIdentity> library(int count) {
        List<CardIdentity> cards = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cards.add(CardIdentity.ofPrinting(new UUID(3L, index), true));
        }
        return cards;
    }

    private static CardIdentity commander() {
        return CardIdentity.ofPrinting(new UUID(5L, 5L), true);
    }
}
