package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.network.StartTablePayload;
import dev.gathering.server.TableSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Starting a game nobody will be held to.
 *
 * <p>The setup screen could only ever start a formatted game, so the one mode most tables
 * actually want - sit down, put cards down, no questions asked - was reachable only by
 * closing the screen and finding the walk-up path, which nothing on the screen mentions.
 *
 * <p>What has to stay true of it: free play is the absence of a format rather than a twelfth
 * entry in the list of them, it borrows a table's own numbers so a game has somewhere to
 * start, and it must not become the thing an unknown format id quietly turns into.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FreePlayGameTest {

    @GameTest(template = "empty")
    public static void freePlayBorrowsTheTablesOwnNumbers(GameTestHelper helper) {
        StartTablePayload asked = new StartTablePayload(
                BlockPos.ZERO, StartTablePayload.FREE_PLAY, 1);

        if (!TableSetup.isFreePlay(asked)) {
            helper.fail("An empty format id was not read as free play");
            return;
        }
        MatchRules rules = TableSetup.rulesOf(asked).orElse(null);
        if (rules == null) {
            helper.fail("Free play produced no rules to play by");
            return;
        }
        if (rules.format().startingLife() != FormatPresets.COMMANDER.startingLife()) {
            helper.fail("Free play did not borrow the table's own starting life: "
                    + rules.format().startingLife());
            return;
        }
        if (!rules.format().hasCommandZone()) {
            helper.fail("Free play left the table without a command zone to start from");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anUnknownFormatIsStillRefusedRatherThanBecomingFreePlay(
            GameTestHelper helper) {
        StartTablePayload madeUp = new StartTablePayload(BlockPos.ZERO, "two-card-vintage", 1);

        if (TableSetup.isFreePlay(madeUp)) {
            helper.fail("A format this server does not have was read as free play");
            return;
        }
        if (TableSetup.rulesOf(madeUp).isPresent()) {
            helper.fail("A made-up format produced rules instead of being refused");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aNamedFormatIsNotFreePlay(GameTestHelper helper) {
        StartTablePayload modern = new StartTablePayload(
                BlockPos.ZERO, FormatPresets.MODERN.id(), 3);

        if (TableSetup.isFreePlay(modern)) {
            helper.fail("Choosing Modern was read as choosing no format");
            return;
        }
        MatchRules rules = TableSetup.rulesOf(modern).orElse(null);
        if (rules == null || !rules.format().equals(FormatPresets.MODERN)) {
            helper.fail("Choosing Modern did not produce Modern's rules");
            return;
        }
        helper.succeed();
    }
}
