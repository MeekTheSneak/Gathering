package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.BreakRules;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What is in use is not blown up.
 * <p>The owner lost a table mid-game to TNT (2026-09-22) and asked for the rest of it as well -
 * so what is checked here is the rule rather than the one block: a real explosion at a real table
 * with a real game on it, and the same question asked of everything else the mod lets somebody
 * put their property into.
 * <p>Vanilla asks a block how much explosion it can take and never says where that block is, so
 * "this table, which has a game on it" cannot be answered by the block. Both loaders take entries
 * out of the explosion's own list instead, and both ask the same method.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExplosionGameTest {

    /** A whole table with a game on it, and TNT going off in the middle of it. */
    @GameTest(template = "tables")
    public static void atableInPlaySurvivesTnt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SeatAnchor anchor = TableClusters.at(level, origin).seats().get(0);
        TableSeats.take(level, origin, anchor.cell(), anchor.side(), player.getUUID());
        if (TableSessions.start(level, origin, dev.gathering.core.match.MatchRules.single(
                dev.gathering.core.format.FormatPresets.COMMANDER))
                != TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start at the table");
            return;
        }

        level.explode(null, origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ() + 0.5,
                6.0f, Level.ExplosionInteraction.TNT);

        for (TablePart part : TablePart.values()) {
            BlockPos at = part.offsetFrom(origin);
            if (!(level.getBlockState(at).getBlock() instanceof TableBlock)) {
                helper.fail("TNT took the " + part + " corner of a table with a game on it");
                return;
            }
        }
        if (!(level.getBlockState(origin).getBlock() instanceof TableBlock)) {
            helper.fail("TNT took the anchor of a table with a game on it");
            return;
        }
        helper.succeed();
    }

    /**
     * And a table nobody is using is furniture, which TNT is allowed to have.
     * <p>The other half, and the one that keeps this from being "the mod's blocks are
     * blast-proof": a world with TNT in it is a world where an empty table can be blown up, and
     * deciding otherwise would be the mod deciding how somebody's world works.
     */
    @GameTest(template = "tables")
    public static void anemptyTableIsOrdinaryFurniture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);

        if (BreakRules.survivesExplosions(level, origin)) {
            helper.fail("a table with nothing on it was treated as in use");
            return;
        }
        helper.succeed();
    }

    /** A cabinet with somebody's cards in it is somebody's cards, however the lid came off. */
    @GameTest(template = "tables")
    public static void acollectionWithCardsInItSurvives(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(at) instanceof dev.gathering.block.CollectionBlockEntity box)) {
            helper.fail("a collection was placed without its block entity");
            return;
        }
        if (BreakRules.survivesExplosions(level, at)) {
            helper.fail("an empty collection was treated as in use");
            return;
        }

        box.put(dev.gathering.core.card.CardIdentity.ofPrinting(
                java.util.UUID.fromString("11111111-1111-4111-8111-111111111111"), false), 1);
        if (!BreakRules.survivesExplosions(level, at)) {
            helper.fail("a collection with cards in it was left to an explosion");
            return;
        }
        helper.succeed();
    }

    /** One whole table, built out of its four parts. */
    private static BlockPos table(GameTestHelper helper) {
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
