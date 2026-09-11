package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.server.Refusals;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Saying no once, however many times it was asked.
 * <p>A verb applied to a selection arrives at the server once per card, so a selection the
 * table refuses is refused once per card. Forty identical lines is not forty times as
 * informative as one.
 * <p>Everything here is one {@code @GameTest} on purpose. Minecraft runs game tests
 * concurrently in a grid, and these all read and write one static holder - as separate tests
 * they would race each other, which is a flake rather than a failure and is worse.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefusalsGameTest {

    @GameTest(template = "empty")
    public static void arefusedselectionissaidonce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Refusals.clear();
        try {
            // Forty cards' worth of the same refusal, which is what one gesture on a
            // selection produces.
            for (int card = 0; card < 40; card++) {
                Refusals.tell(player, "the game has finished");
            }
            // The first went out; the other thirty-nine are being held to be counted.
            if (Refusals.swallowedFor(player.getUUID()) != 39) {
                helper.fail("forty identical refusals left "
                        + Refusals.swallowedFor(player.getUUID())
                        + " held back rather than 39, so they were said one at a time");
                return;
            }

            // A different thing to say settles the run rather than letting the tally arrive
            // after the sentence it is about has scrolled away.
            Refusals.tell(player, "it is not your turn");
            if (Refusals.swallowedFor(player.getUUID()) != 0) {
                helper.fail("a different refusal left the old tally unsaid: "
                        + Refusals.swallowedFor(player.getUUID()) + " still held");
                return;
            }

            // And nothing is ever swallowed entirely. A table that silently ignores you is
            // the one thing worse than being told twice.
            Refusals.clear();
            Refusals.tell(player, "the game has finished");
            if (Refusals.swallowedFor(player.getUUID()) != 0) {
                helper.fail("the first refusal of a run was held back rather than said");
                return;
            }

            // Forgetting a player drops their run, because it is counted in this server's
            // ticks and means nothing in the next one.
            Refusals.tell(player, "the game has finished");
            Refusals.forget(player.getUUID());
            if (Refusals.swallowedFor(player.getUUID()) != 0) {
                helper.fail("a player who left still has refusals held for them");
                return;
            }

            // Nothing to say is not something to say.
            Refusals.tell(player, null);
            Refusals.tell(player, "   ");
            if (Refusals.swallowedFor(player.getUUID()) != 0) {
                helper.fail("a blank refusal was recorded as one");
                return;
            }
            helper.succeed();
        } finally {
            Refusals.clear();
        }
    }
}
