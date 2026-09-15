package dev.gathering.neoforge.compat.create;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What Create's contraptions - bearings, pistons, gantries, trains - may carry off. A table's game and
 * a collection's cards live at a block position and would be lost to a contraption's copy of the world,
 * so those stay where they are, as they do for a piston; a Scorekeeper's Desk keeps nothing but which
 * tournament it runs, and goes. Registered only when Create is installed - see PackGameTests.
 */
@PrefixGameTestTemplate(false)
public final class CreateContraptionGameTest {

    private CreateContraptionGameTest() {
    }

    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void contraptionsLeaveTablesAndCollectionsWhereTheyAre(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState tableState = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(table), tableState.setValue(TableBlock.PART, part), 3);
        }
        for (TablePart part : TablePart.values()) {
            BlockPos at = part.offsetFrom(table);
            if (BlockMovementChecks.isMovementAllowed(helper.getLevel().getBlockState(at), helper.getLevel(), at)) {
                helper.fail("a Create contraption may carry off the table's " + part + " quarter");
                return;
            }
        }
        BlockPos collection = helper.absolutePos(new BlockPos(4, 2, 1));
        helper.getLevel().setBlock(collection, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
        if (BlockMovementChecks.isMovementAllowed(helper.getLevel().getBlockState(collection), helper.getLevel(), collection)) {
            helper.fail("a Create contraption may carry off a collection");
            return;
        }
        BlockPos desk = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlock(desk, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState(), 3);
        if (!BlockMovementChecks.isMovementAllowed(helper.getLevel().getBlockState(desk), helper.getLevel(), desk)) {
            helper.fail("a Create contraption may not carry a Scorekeeper's Desk, which has nothing to lose");
            return;
        }
        helper.succeed();
    }
}
