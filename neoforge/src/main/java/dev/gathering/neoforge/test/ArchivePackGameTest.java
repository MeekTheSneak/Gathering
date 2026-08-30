package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.Archive;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Archive Pack, on a server that has none to give.
 *
 * <p>Which is the case worth testing in a running game, because it is the case every server
 * starts in and the one where the failure is silent: the archive is empty until the coverage
 * audit has run, and an empty archive that dropped packs anyway would hand out nothing and
 * eat the pack doing it.
 *
 * <p>What is in one, and which chests it comes out of, is decided in the pure layer where it
 * can be checked properly - see {@code ArchiveDropsTest} and {@code CoverageAuditTest}.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchivePackGameTest {

    private ArchivePackGameTest() {
    }

    /** An archive pack is a pack, and reads as the archive rather than as a set. */
    @GameTest(template = "empty")
    public static void anArchivePackIsAPack(GameTestHelper helper) {
        ItemStack stack = Archive.pack();
        if (!stack.is(GatheringContent.PACK.get())) {
            helper.fail("an archive pack is not a pack item");
            return;
        }
        PackComponent pack = PackItem.packOf(stack).orElse(null);
        if (pack == null || !pack.isReal()) {
            helper.fail("an archive pack has nothing on it");
            return;
        }
        if (!pack.isArchive()) {
            helper.fail("an archive pack does not know it is one, so it would be looked up "
                    + "as a set called '" + pack.setCode() + "'");
            return;
        }
        helper.succeed();
    }

    /**
     * Nothing drops while the archive is empty, whatever the chest.
     *
     * <p>A test server is not collecting, so the archive is empty here for the same reason it
     * is empty on a server that has just come up - and a pack out of it either way would be a
     * pack holding nothing.
     */
    @GameTest(template = "empty")
    public static void anEmptyArchiveDropsNothing(GameTestHelper helper) {
        if (Archive.size() != 0) {
            helper.fail("a test server has an archive of " + Archive.size() + " card(s)");
            return;
        }
        for (String table : java.util.List.of(
                "minecraft:entities/ender_dragon",
                "minecraft:chests/ancient_city",
                "minecraft:gameplay/fishing/treasure",
                "minecraft:chests/simple_dungeon",
                "minecraft:entities/zombie")) {
            // A thousand rolls of the most generous table there is. One in two would show up
            // in the first handful; nothing in a thousand is nothing.
            for (int roll = 0; roll < 1_000; roll++) {
                Optional<ItemStack> found = Archive.rollFor(table, helper.getLevel().getRandom());
                if (found.isPresent()) {
                    helper.fail("an empty archive dropped a pack out of " + table);
                    return;
                }
            }
        }
        if (!Archive.open(helper.getLevel().getRandom()).isEmpty()) {
            helper.fail("an empty archive opened into cards");
            return;
        }
        helper.succeed();
    }
}
