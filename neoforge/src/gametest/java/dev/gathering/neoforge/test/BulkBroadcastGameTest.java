package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.match.MatchRules;
import dev.gathering.server.TableActions;
import dev.gathering.server.TableBroadcast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a gesture on a selection actually costs the server.
 * <p>The working record carried "a hundred-card gesture is a hundred broadcasts, unmeasured"
 * for long enough that nobody knew whether the hundred was the problem or the number of people
 * at the table was. This counts it.
 * <p>Building one board means walking every zone of every seat through the visibility rules
 * and serializing the result, once for each person who can see the table - so the real cost is
 * this count multiplied by the watchers. What is asserted here is the shape of the growth
 * rather than a time: a number that grows with the size of a selection is a number worth
 * knowing about, and one that does not is not.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BulkBroadcastGameTest {

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    /** A seated game with a handful of cards on the battlefield to act on. */
    private static SeatId aGameWithCardsOut(
            GameTestHelper helper, ServerPlayer player, BlockPos origin, int howMany) {
        // Standing at it. Every action goes through a reach check, so a player left wherever
        // the harness put them has every gesture refused before it reaches the board - and the
        // broadcast count would then be zero for a reason that has nothing to do with
        // broadcasting.
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        var seat = TableClusters.at(helper.getLevel(), origin).seats().getFirst();
        if (TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), player.getUUID())
                != TableSeats.Claim.TAKEN) {
            throw new AssertionError("could not seat the player");
        }
        if (TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, 1)) != TableSessions.Outcome.STARTED) {
            throw new AssertionError("could not start the game");
        }
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        SeatId me = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID())
                .orElseThrow();
        for (int card = 0; card < howMany; card++) {
            session.submit(new GameEvent.PaperCardCreated(me, me, PaperStock.BLANK, "card"));
        }
        return me;
    }

    private static List<GameEvent> tapEverything(
            GameTestHelper helper, BlockPos origin, SeatId me) {
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        GameView board = VisibilityRules.viewFor(session.state(), new Viewer.Seated(me));
        List<GameEvent> gestures = new ArrayList<>();
        for (CardView card : board.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible) {
                gestures.add(new GameEvent.CardTapSet(me, visible.id(), true));
            }
        }
        return gestures;
    }

    /**
     * A gesture on a selection costs one broadcast per card, not one per gesture.
     * <p>This is a measurement rather than a complaint. It is written to pass at whatever the
     * cost currently is and to <b>say the number in its failure text</b>, so that anybody who
     * changes the broadcast path sees immediately what it did - in either direction.
     * <p>What it does assert is that the count grows with the selection. If it ever stops
     * doing so, the coalescing somebody added is working and this test should be rewritten to
     * say so rather than quietly continuing to pass.
     */
    @GameTest(template = "empty")
    public static void abulkgesturecostsaboardpercard(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId me = aGameWithCardsOut(helper, player, origin, 8);
        List<GameEvent> gestures = tapEverything(helper, origin, me);
        if (gestures.size() != 8) {
            helper.fail("the fixture put " + gestures.size() + " cards out rather than 8");
            return;
        }

        // Two measurements on one table, because a second table would be built outside
        // this test's plot - the server packs every game test's template into one world, so a
        // block written at another test's x is a block written into another test's fixture.
        // tools/plotcheck.py caught exactly that in an earlier draft of this file.
        TableBroadcast.forgetTheCount();
        for (GameEvent gesture : gestures.subList(0, 2)) {
            TableActions.handle(player,
                    new dev.gathering.network.TableActionPayload(origin, encode(gesture)));
        }
        int forTwo = TableBroadcast.boardsSent();

        TableBroadcast.forgetTheCount();
        for (GameEvent gesture : gestures.subList(2, 8)) {
            TableActions.handle(player,
                    new dev.gathering.network.TableActionPayload(origin, encode(gesture)));
        }
        int forSix = TableBroadcast.boardsSent();

        if (forTwo == 0 || forSix == 0) {
            helper.fail("no boards went out at all, so this is measuring nothing: two cards "
                    + "cost " + forTwo + " and six cost " + forSix);
            return;
        }
        if (forSix <= forTwo) {
            // Either the path coalesces now, or the fixture is not exercising it. Both are
            // worth stopping for: a measurement that has quietly stopped measuring is worse
            // than none, because it goes on reporting.
            helper.fail("tapping six cards cost " + forSix + " boards and tapping two cost "
                    + forTwo + "; the cost has stopped growing with the selection, so either "
                    + "broadcasts are coalesced now (rewrite this test to say so) or this "
                    + "fixture no longer exercises the path");
            return;
        }
        helper.succeed();
    }

    private static byte[] encode(GameEvent event) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
                dev.gathering.core.game.persistence.EventCodec.write(out, event);
            }
            return bytes.toByteArray();
        } catch (java.io.IOException cannotDescribe) {
            throw new IllegalStateException(cannotDescribe);
        }
    }
}
