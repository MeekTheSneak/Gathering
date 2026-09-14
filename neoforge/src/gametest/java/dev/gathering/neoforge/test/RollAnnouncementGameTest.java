package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientTableRolls;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A roll is announced across the table once, not every time somebody comes back to it.
 * <p>Found by running the game: one coin flip stayed across the middle of the felt through four
 * later screenshots, because every dialog opened over the board closed it, closing forgot the
 * announcement, and coming back found the same roll at the top of the log and announced it again.
 * <p>Plain data on a server: the announcer reads a board and a clock and names no client class.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RollAnnouncementGameTest {

    private static final SeatId ME = SeatId.of(0);

    private static GameSession aTable() {
        GameSession session = GameSession.create(List.of(ME, SeatId.of(1)),
                FormatPresets.COMMANDER.startingLife(), SessionSeed.random(), UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ME, new PlayerRef(UUID.randomUUID(), "Roller")));
        return session;
    }

    private static GameView boardOf(GameSession session) {
        return VisibilityRules.viewFor(session.state(), new Viewer.Seated(ME), session.recentLog(40));
    }

    @GameTest(template = "empty")
    public static void arollisannouncedonceacrossleavingandcomingback(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        ClientTableRolls.clear();
        try {
            GameSession session = aTable();
            session.submit(new GameEvent.CoinFlipped(ME, true));
            long now = 1_000_000L;

            ClientTableRolls.seen(table, boardOf(session), now);
            if (ClientTableRolls.showingAt(table, now).isEmpty()) {
                helper.fail("a fresh coin flip was not announced at all, so this proves nothing");
                return;
            }

            // A dialog opens over the board and closes again, long after the flip.
            ClientTableRolls.forget();
            long later = now + 60_000L;
            ClientTableRolls.seen(table, boardOf(session), later);
            if (ClientTableRolls.showingAt(table, later).isPresent()) {
                helper.fail("coming back to the table announced a coin flip from a minute ago again");
                return;
            }

            // And a new roll still is.
            session.submit(new GameEvent.CoinFlipped(ME, false));
            ClientTableRolls.seen(table, boardOf(session), later);
            if (ClientTableRolls.showingAt(table, later).isEmpty()) {
                helper.fail("a new roll after coming back was not announced");
                return;
            }
            helper.succeed();
        } finally {
            ClientTableRolls.clear();
        }
    }
}
