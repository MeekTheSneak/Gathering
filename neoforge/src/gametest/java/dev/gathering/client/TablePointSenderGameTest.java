package dev.gathering.client;

import dev.gathering.Gathering;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A pointer is about a table in the world, and only that.
 * <p>From the 2026-09-25 sweep. The lesson's board and a replay's are filed at positions no table
 * can be, and the screen reported the cursor over their felt exactly as it does over a real one -
 * so the lesson sent pointers at a board with no server end, a replay sent them at a server with
 * no table there, and a player who opened the lesson from a real table's menu sent a pointer at the
 * lesson where the stop that lowered their arm at the real table should have gone.
 * <p>In this package because what is checked is the sender's own idea of where the cursor is,
 * which the tick reads; the tick itself needs a client, which a game-test server does not have.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TablePointSenderGameTest {

    /** A table somewhere in an ordinary world. */
    private static final BlockPos A_REAL_TABLE = new BlockPos(12, 64, 30);

    /** Where a replay files its board, as {@code TableScreen} does: the world does not go down that far. */
    private static final BlockPos A_REPLAY = new BlockPos(0, Integer.MIN_VALUE, 0);

    @GameTest(template = "empty")
    public static void onlyatableintheworldispointedat(GameTestHelper helper) {
        try {
            TablePointSender.clear();
            TablePointSender.hovering(TutorialDemo.table(), 100, 100);
            if (TablePointSender.hoveringOver() != null) {
                helper.fail("the cursor over the lesson's felt was taken for a table to tell");
                return;
            }
            TablePointSender.hovering(A_REPLAY, 100, 100);
            if (TablePointSender.hoveringOver() != null) {
                helper.fail("the cursor over a replay's felt was taken for a table to tell");
                return;
            }
            TablePointSender.hovering(A_REAL_TABLE, 100, 100);
            if (!A_REAL_TABLE.equals(TablePointSender.hoveringOver())) {
                helper.fail("the cursor over a real table's felt was not held for the next tick: "
                        + TablePointSender.hoveringOver());
                return;
            }
            helper.succeed();
        } finally {
            TablePointSender.clear();
        }
    }
}
