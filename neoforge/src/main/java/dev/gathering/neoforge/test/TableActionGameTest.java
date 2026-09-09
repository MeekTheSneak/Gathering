package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.EventCodec;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TableActions;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a client is allowed to say happened.
 * <p>A client sends a move it says it made. The server's job is to refuse the ones it did not
 * - and the check that matters is not "was that legal", because the mod has no opinion about
 * legality, but "is that your name on it". Attribution is what makes the permissiveness safe:
 * any seated player may move any public card, and the log says who did.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableActionGameTest {

    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID STRANGER = new UUID(0L, 2L);

    @GameTest(template = "tables")
    public static void yourOwnMoveIsAccepted(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 1, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -1))).isPresent();

        if (!accepted) {
            helper.fail("A player's own move was refused");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aMoveSignedWithSomebodyElsesSeatIsRefused(GameTestHelper helper) {
        // The whole reason the actor is checked rather than trusted. Without this a client
        // could take a card off an opponent's board and have the log say the opponent did it.
        BlockPos origin = seatedGame(helper, 5, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.LifeChanged(new SeatId(1), new SeatId(0), -40))).isPresent();

        if (accepted) {
            helper.fail("A move signed with another seat was accepted");
        }
        helper.succeed();
    }

    /**
     * The events the table writes about itself are not a client's to send.
     * <p>Signed correctly - the forged event names the player's own seat, so the attribution
     * check above is satisfied - and still refused, because a seat being taken, a deck being
     * loaded and the session ending are things the server does when a player sits, crouches
     * or concedes. Authorization allows anything it does not name, which is right for a mod
     * with no rules enforcement and left these three open: a client could end the game for
     * everybody with nothing put away, or swap its own library for any cards mid-game.
     */
    @GameTest(template = "tables")
    public static void theTablesOwnEventsAreNotAClientsToSend(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 1, 2, 1);
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        boolean endedBefore = session.state().ended();

        boolean ended = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.SessionEnded(new SeatId(0), "forged"))).isPresent();
        boolean loaded = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.DeckLoaded(new SeatId(0), java.util.List.of(),
                        java.util.List.of(), dev.gathering.core.card.Sleeve.DEFAULT))).isPresent();
        boolean sat = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.SeatTaken(new SeatId(1),
                        new dev.gathering.core.game.PlayerRef(ALICE, "Alice")))).isPresent();

        if (ended || loaded || sat) {
            helper.fail("A client's forged lifecycle event was accepted: ended=" + ended
                    + " loaded=" + loaded + " sat=" + sat);
        }
        if (session.state().ended() != endedBefore) {
            helper.fail("A refused SessionEnded still ended the game");
        }
        // And the honest one beside them still goes through, so this refused the right thing.
        if (TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.Conceded(new SeatId(0)))).isEmpty()) {
            helper.fail("A player's own concession was refused along with the forgeries");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void somebodyWithNoSeatCannotMoveAtAll(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 9, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, STRANGER,
                encoded(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -1))).isPresent();

        if (accepted) {
            helper.fail("A player with no seat made a move");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void nonsenseIsRefusedRatherThanGuessedAt(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 13, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                new byte[] {9, 9, 9, 9}).isPresent();

        if (accepted) {
            helper.fail("A malformed move was accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void thereIsNothingToDoAtATableWithNoGame(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -1))).isPresent();

        if (accepted) {
            helper.fail("A move was accepted at a table with no game on it");
        }
        helper.succeed();
    }

    /** A table with a game on it and Alice in seat zero. */
    private static BlockPos seatedGame(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = place(helper, x, y, z);
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, ALICE);
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());
        return origin;
    }

    private static byte[] encoded(GameEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                EventCodec.write(out, event);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new GameTestAssertException("Could not encode a test move: " + e);
        }
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
