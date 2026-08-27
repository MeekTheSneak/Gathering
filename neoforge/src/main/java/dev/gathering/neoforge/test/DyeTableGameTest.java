package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Dyeing the felt.
 *
 * <p>A table is four blocks and one block entity, so the half of this that can go wrong
 * without anybody noticing is which cell you clicked: dye applied to the corner and read from
 * the corner would pass every test and still do nothing for a player who right-clicked one of
 * the other three.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DyeTableGameTest {

    /** Dye goes on from whichever of the four blocks was clicked. */
    @GameTest(template = "empty")
    public static void anyCellOfTheTableTakesTheDye(GameTestHelper helper) {
        for (TablePart part : TablePart.values()) {
            BlockPos origin = table(helper);
            BlockPos clicked = part.offsetFrom(origin);

            TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), clicked).orElse(null);
            if (table == null) {
                helper.fail("clicking the " + part + " cell reached no table at all");
                return;
            }
            if (!table.dye(DyeColor.MAGENTA)) {
                helper.fail("the " + part + " cell would not take dye");
                return;
            }
            if (table.felt().orElse(null) != DyeColor.MAGENTA) {
                helper.fail("dye put on the " + part + " cell did not stick");
                return;
            }
            clear(helper, origin);
        }
        helper.succeed();
    }

    /** The same color, whichever cell it is read back from. */
    @GameTest(template = "empty")
    public static void everyCellReadsTheSameColor(GameTestHelper helper) {
        BlockPos origin = table(helper);
        TableBlock.entityAt(helper.getLevel(), origin).orElseThrow().dye(DyeColor.LIME);

        for (TablePart part : TablePart.values()) {
            DyeColor seen = TableBlock.entityAt(helper.getLevel(), part.offsetFrom(origin))
                    .flatMap(TableBlockEntity::felt)
                    .orElse(null);
            if (seen != DyeColor.LIME) {
                helper.fail("the " + part + " cell reads " + seen + " rather than lime, so it"
                        + " would draw undyed felt beside three dyed ones");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The color is in what the client is sent, not only in what is saved.
     *
     * <p>Color lives on the block entity rather than in the blockstate, so nothing tells a
     * client about it unless the update tag carries it - and a dyed table that only the
     * server knows about is a table that is not dyed as far as anybody can see.
     */
    @GameTest(template = "empty")
    public static void theColorIsInWhatTheClientIsSent(GameTestHelper helper) {
        BlockPos origin = table(helper);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.dye(DyeColor.ORANGE);

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag sent = table.getUpdateTag(registries);
        if (!sent.contains("felt") || !"orange".equals(sent.getString("felt"))) {
            helper.fail("the update sent to clients does not carry the felt color");
            return;
        }
        helper.succeed();
    }

    /** And it survives being written down. */
    @GameTest(template = "empty")
    public static void theColorSurvivesARestart(GameTestHelper helper) {
        BlockPos origin = table(helper);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.dye(DyeColor.PURPLE);

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag written = table.saveWithoutMetadata(registries);
        table.dye(DyeColor.WHITE);
        table.loadWithComponents(written, registries);

        if (table.felt().orElse(null) != DyeColor.PURPLE) {
            helper.fail("a dyed table came back from disk as " + table.felt().orElse(null));
            return;
        }
        helper.succeed();
    }

    /** Dyeing it the color it already is changes nothing, so the dye is not eaten. */
    @GameTest(template = "empty")
    public static void dyeingItTheSameColorTwiceIsNotAChange(GameTestHelper helper) {
        BlockPos origin = table(helper);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        if (!table.dye(DyeColor.RED)) {
            helper.fail("the first dye did not take");
            return;
        }
        if (table.dye(DyeColor.RED)) {
            helper.fail("dyeing it red twice counted as a change, so it would eat the dye");
            return;
        }
        helper.succeed();
    }

    private static void clear(GameTestHelper helper, BlockPos origin) {
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
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
