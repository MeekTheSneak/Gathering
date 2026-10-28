package dev.gathering.fabric.test;

import dev.gathering.fabric.GatheringFabric;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Which loot tables this loader will add the mod's pool to.
 * <p>Checked here rather than in the world, because the case that went wrong cannot be built in
 * a game test: making a table {@code DATA_PACK} needs an external data pack in the world folder,
 * and a test mod's own resources come through as {@code MOD}. So the rule is asked directly, for
 * every value the enum has - which is what would have caught a hook that asked
 * {@code isBuiltin()} and quietly stopped giving packs from any chest a server had edited.
 */
public class FabricLootSourceGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void everySourceButAReplacedTableIsFairGame(GameTestHelper helper) {
        for (LootTableSource source : LootTableSource.values()) {
            boolean wanted = source != LootTableSource.REPLACED;
            if (GatheringFabric.mayModify(source) != wanted) {
                helper.fail("A " + source + " table was " + (wanted ? "refused" : "accepted")
                        + " by the loot hook");
                return;
            }
        }
        // Named as well as looped, so the two that matter are readable in the failure.
        if (!GatheringFabric.mayModify(LootTableSource.DATA_PACK)) {
            helper.fail("A chest a data pack had edited got no card packs - the two loaders "
                    + "disagree about the same world again");
            return;
        }
        if (!GatheringFabric.mayModify(LootTableSource.VANILLA)) {
            helper.fail("A vanilla chest got no card packs");
            return;
        }
        helper.succeed();
    }
}
