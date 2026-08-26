package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.ante.AnteConsent;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.Antes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Nobody plays for keeps without saying so, in a running world.
 *
 * <p>The rule about who has agreed is pure and has its own tests. What cannot be checked
 * there is the part that decides whether a game actually begins - and that is the part where
 * a mistake costs somebody a card they never agreed to play for.
 *
 * <p>The test server runs with ante off, which is itself worth checking: a server that has
 * not turned it on must never see the question at all.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnteConsentGameTest {

    /**
     * With ante off, nothing asks and the game starts exactly as it always did.
     *
     * <p>The regression that would matter most: a gate added in front of every game that
     * silently stops games on the servers that never wanted it.
     */
    @GameTest(template = "empty")
    public static void aServerNotPlayingForKeepsIsNeverAsked(GameTestHelper helper) {
        if (Antes.isOffered()) {
            helper.fail("the test server has ante on, so this checks nothing");
            return;
        }
        BlockPos origin = seatedTable(helper, 2);
        if (Antes.askedFirst(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER))) {
            helper.fail("a server with ante off put the question to a table anyway");
            return;
        }
        if (Antes.isAsking(origin)) {
            helper.fail("a server with ante off left a question open at a table");
            return;
        }
        // And the ordinary path still works.
        if (TableSessions.start(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER))
                != TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start on a server with ante off");
            return;
        }
        Antes.clear();
        helper.succeed();
    }

    /**
     * An answer from somebody who is not at the table is not a vote.
     *
     * <p>Somebody watching does not get to decide whether the people playing lose a card, and
     * the seat is read off the server's own record rather than out of the payload - so this
     * is checking that a made-up answer reaches nothing.
     */
    @GameTest(template = "empty")
    public static void somebodyWhoIsNotSittingThereDoesNotGetAVote(GameTestHelper helper) {
        Antes.clear();
        BlockPos origin = seatedTable(helper, 2);

        ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
        watcher.setPos(origin.getCenter());
        // No question is open, and a watcher has no seat: either alone is enough, and both
        // together are what a client making things up would be trying to get past.
        Antes.answer(watcher, origin, AnteConsent.Answer.IN);

        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("an answer from somebody with no seat started a game");
            return;
        }
        Antes.clear();
        helper.succeed();
    }

    /** A question at a table nobody is sitting at is not asked at all. */
    @GameTest(template = "empty")
    public static void anEmptyTableIsNotAsked(GameTestHelper helper) {
        Antes.clear();
        BlockPos origin = seatedTable(helper, 0);
        if (Antes.askedFirst(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER))) {
            helper.fail("an empty table was asked whether it wanted to play for keeps");
            return;
        }
        Antes.clear();
        helper.succeed();
    }

    /** Between servers, a half-asked question does not survive into the next world. */
    @GameTest(template = "empty")
    public static void aHalfAskedQuestionDoesNotOutliveItsServer(GameTestHelper helper) {
        Antes.clear();
        if (Antes.isAsking(BlockPos.ZERO)) {
            helper.fail("a question survived being cleared");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ bits

    private static BlockPos seatedTable(GameTestHelper helper, int players) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        List<SeatAnchor> anchors = TableClusters.at(level, origin).seats();
        for (int index = 0; index < Math.min(players, anchors.size()); index++) {
            SeatAnchor anchor = anchors.get(index);
            TableSeats.take(level, origin, anchor.cell(), anchor.side(),
                    new java.util.UUID(11L, index));
        }
        return origin;
    }
}
