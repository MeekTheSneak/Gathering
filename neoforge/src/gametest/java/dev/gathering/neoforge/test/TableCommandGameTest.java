package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the table commands let somebody do to a game that is not theirs.
 * <p>Ending a game cannot be undone, which is why it is a command rather than a click on the
 * felt. That reasoning only holds if the person typing it is at the table: a command anybody
 * can point at anybody's match is a click on the felt with extra steps and no owner.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableCommandGameTest {

    /**
     * Somebody who is not sitting at a table cannot end the game on it.
     * <p>The seat was looked up and, when the caller had none, quietly replaced with seat
     * zero - so the command ran as whoever was sitting in the first chair. Anybody who walked
     * past a table could end a match nobody at it had agreed to stop, signed with somebody
     * else's seat, and on a server playing for keeps that also settles the pot.
     */
    @GameTest(template = "empty")
    public static void aStrangerCannotEndSomebodyElsesGame(GameTestHelper helper) {
        BlockPos origin = tableAt(helper);
        ServerPlayer seated = helper.makeMockServerPlayerInLevel();
        List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
        TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(), anchors.get(0).side(),
                seated.getUUID());
        if (TableSessions.start(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER)) != TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start at the table");
            return;
        }

        ServerPlayer stranger = lookingDownAt(helper, origin);
        // The command ray-casts, so prove the aim before trusting what the command says: a
        // player who is not looking at the table is refused for that reason instead, which
        // leaves the game running exactly as a seat check does and would let this pass with
        // the seat check deleted.
        if (!(stranger.pick(6.0d, 0.0f, false) instanceof net.minecraft.world.phys.BlockHitResult at)
                || !(helper.getLevel().getBlockState(at.getBlockPos()).getBlock()
                        instanceof TableBlock)) {
            helper.fail("the player is not looking at the table, so this checks nothing");
            return;
        }
        if (run(helper, stranger, "gathering table end") != 0
                || !TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("somebody who is not sitting at the table ended the game on it");
            return;
        }

        // The same command, from the person whose seat it is, through the same path. Without
        // this the test above passes for the wrong reason: a command that cannot find the
        // table at all also leaves the session running, and would go on passing with the seat
        // check deleted. This is what proves the path works and the seat is what refused.
        stand(seated, origin);
        if (run(helper, seated, "gathering table end") != 1) {
            helper.fail("the person sitting at the table could not end their own game, so the"
                    + " refusal above proves nothing");
            return;
        }
        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("the game was reported ended and is still running");
            return;
        }
        helper.succeed();
    }

    /**
     * A card out of nothing is an admin grant where cards are property.
     * <p>/gathering pack requires an operator, and says why: product out of nothing is a
     * grant, not a way to collect. /gathering card had nothing in front of it at all - and a
     * single card by name is more of a grant than a sealed pack, not less, being the thing
     * packs exist to produce with the odds taken out. On a server running the whole
     * collecting economy, anybody could type past all of it.
     * <p>Only where collecting is on. With it off the mod is a table and a box of proxies,
     * and conjuring a card is the point rather than a way round anything - so that case is
     * checked here too, since a gate that is always shut would have broken it.
     * <p>Asked for by a name nothing can resolve, on purpose. The gate decides the return
     * value before any lookup happens, so the refusal is what is being read either way - and
     * a card that really arrived would go into the server's card cache, which stocks the
     * archive sheet, which another test asserts is empty. A test that quietly changes what
     * the next one is looking at is worse than the bug it was written for.
     */
    @GameTest(template = "empty")
    public static void conjuringACardIsAnAdminGrantWhereCardsAreProperty(GameTestHelper helper) {
        boolean before = dev.gathering.service.ServerSettings.get().modes().collectionEnabled();
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();

            dev.gathering.server.Settings.set("modes.collection_enabled", "on");
            if (run(helper, player, "gathering card Zzz Not A Real Card") != 0) {
                helper.fail("a player conjured a card by name on a server that collects them");
                return;
            }
            dev.gathering.server.Settings.set("modes.collection_enabled", "off");
            if (run(helper, player, "gathering card Zzz Not A Real Card") == 0) {
                helper.fail("a card could not be conjured on a server with collecting off,"
                        + " where that is the ordinary way to hold one");
                return;
            }
            helper.succeed();
        } finally {
            dev.gathering.server.Settings.set(
                    "modes.collection_enabled", before ? "on" : "off");
        }
    }

    /**
     * Runs a command as this player and hands back what it returned.
     * <p>Through the dispatcher rather than {@code performPrefixedCommand}, which returns
     * nothing - and the return value is the whole point here. A command that refuses and one
     * that cannot find the table both leave the game running, so only the number tells them
     * apart.
     */
    private static int run(GameTestHelper helper, ServerPlayer who, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher()
                    .execute(command, who.createCommandSourceStack());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException refused) {
            return 0;
        }
    }

    /** A player standing over the table, looking straight down at it. */
    private static ServerPlayer lookingDownAt(GameTestHelper helper, BlockPos origin) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        stand(player, origin);
        return player;
    }

    /**
     * Puts this player above the table, looking straight down at it.
     * <p>{@code moveTo} rather than {@code setPos} and {@code setXRot}, because the command
     * ray-casts and {@link net.minecraft.world.entity.Entity#pick} builds both its origin and
     * its direction by interpolating from the <em>previous</em> tick's position and rotation.
     * A player only half-placed still stood and looked wherever the mock spawned, the ray
     * missed the table entirely, and the command refused with "no table here" - which leaves
     * the game running exactly as a seat check does. That version of this test passed with
     * the seat check deleted.
     */
    private static void stand(ServerPlayer player, BlockPos origin) {
        // Standing on the table, not above the plot. A game test structure is boxed in
        // barrier blocks, so a player placed clear of the table by a couple of blocks is
        // outside that box looking down at its roof, and the ray stops at the barrier.
        player.moveTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0.0f, 90.0f);
    }

    private static BlockPos tableAt(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
