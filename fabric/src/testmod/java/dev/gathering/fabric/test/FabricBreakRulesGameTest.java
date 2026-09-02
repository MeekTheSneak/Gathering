package dev.gathering.fabric.test;

import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.item.GatheringContent;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A break this mod refuses is really refused on this loader.
 * <p>The block cannot decline in vanilla - by the time it hears, the decision is made - so each
 * loader catches it somewhere of its own. NeoForge has a break event it can cancel; Fabric has
 * {@code PlayerBlockBreakEvents.BEFORE}, where returning false is the refusal. The rule itself
 * is shared, in {@code BreakRules}, so what is worth checking here is only the wiring: that the
 * answer reaches the event, and that a false really leaves the block standing.
 * <p>Checked by firing the event rather than by swinging, because a mock player in a game test
 * does not go through the loader's mining path - so a test that broke the block and found it
 * gone would be testing vanilla, and one that found it there would pass with the hook
 * unregistered.
 */
public class FabricBreakRulesGameTest implements FabricGameTest {

    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @GameTest(template = EMPTY_STRUCTURE)
    public void somebodyElsesCollectionMayNotBeBroken(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        collection.setRights(CollectionRights.ownedBy(STRANGER));
        var player = helper.makeMockServerPlayerInLevel();

        boolean allowed = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), player, helper.absolutePos(at),
                helper.getLevel().getBlockState(helper.absolutePos(at)), collection);

        if (allowed) {
            helper.fail("A stranger was allowed to break somebody else's collection - the "
                    + "refusal never reached this loader's break event");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void yourOwnCollectionMayBeBroken(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        CollectionBlockEntity collection = place(helper, at);
        var player = helper.makeMockServerPlayerInLevel();
        collection.claimFor(player.getUUID());

        // The other half, and the half a hook wired up backwards would fail: a rule that
        // refused everything would pass the test above and make the block unbreakable.
        boolean allowed = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), player, helper.absolutePos(at),
                helper.getLevel().getBlockState(helper.absolutePos(at)), collection);

        if (!allowed) {
            helper.fail("The owner of a collection was refused a break of their own block");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anOrdinaryBlockIsNoneOfTheModsBusiness(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, net.minecraft.world.level.block.Blocks.STONE);
        var player = helper.makeMockServerPlayerInLevel();

        boolean allowed = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                helper.getLevel(), player, helper.absolutePos(at),
                helper.getLevel().getBlockState(helper.absolutePos(at)), null);

        if (!allowed) {
            helper.fail("The mod refused a break of a plain stone block");
            return;
        }
        helper.succeed();
    }

    private static CollectionBlockEntity place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, GatheringContent.COLLECTION.get());
        return (CollectionBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(at));
    }
}
