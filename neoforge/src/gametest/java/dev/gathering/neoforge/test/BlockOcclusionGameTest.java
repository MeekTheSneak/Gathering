package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Nothing this mod puts down hides the block next to it.
 * <p>A block whose model is not a whole cube has to say so, or the game culls the faces of its neighbors
 * against a cube that is not there and a player sees straight through the world past it. The owner has
 * reported this twice - the collection cabinet, and then "some blocks still make adjacent blocks
 * invisible" (2026-09-16) - so it is asked here of every block the mod registers rather than of the one
 * that was noticed.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockOcclusionGameTest {

    @GameTest(template = "empty")
    public static void nothingThatIsNotACubeHidesItsNeighbors(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        List<String> lying = new ArrayList<>();
        int looked = 0;
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!Gathering.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            looked++;
            BlockState state = entry.getValue().defaultBlockState();
            // What a player sees it as, against what the game is told it fills.
            boolean wholeCube = Block.isShapeFullBlock(state.getShape(helper.getLevel(), at));
            if (!wholeCube && state.canOcclude()) {
                lying.add(id.getPath());
            }
        }
        if (looked == 0) {
            helper.fail("no blocks of this mod were found to look at");
            return;
        }
        if (!lying.isEmpty()) {
            helper.fail(lying.size() + " block(s) are drawn as less than a cube but still hide what is next "
                    + "to them: " + String.join(", ", lying));
            return;
        }
        helper.succeed();
    }
}
