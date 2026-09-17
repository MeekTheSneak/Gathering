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
 * <p>Which is the case worth testing in a running game, because it is the case every server
 * starts in and the one where the failure is silent: the archive is empty until the coverage
 * audit has run, and an empty archive that dropped packs anyway would hand out nothing and
 * eat the pack doing it.
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
     * <p>A test server is not collecting, so the archive is empty here for the same reason it
     * is empty on a server that has just come up - and a pack out of it either way would be a
     * pack holding nothing.
     */
    @GameTest(template = "empty")
    public static void anEmptyArchiveDropsNothing(GameTestHelper helper) {
        // Set the state rather than assume it. The sheet is filled by an asynchronous warm at
        // boot, so whether it has landed by the time this test runs is a matter of how long the
        // tests before it took - which is not something this test is about.
        Archive.clear();
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
        if (!Archive.candidates("", helper.getLevel().getRandom()).isEmpty()
                || Archive.firstWithCards(Archive.candidates("sos", helper.getLevel().getRandom())).join().isPresent()) {
            helper.fail("an empty archive opened into cards");
            return;
        }
        helper.succeed();
    }

    /**
     * An archive pack found in the world is for one set, and opens as that set's archive.
     * <p>The owner's rule: one set to a pack, so opening one looks up that set's family alone rather
     * than every set there has ever been.
     */
    @GameTest(template = "empty")
    public static void aFoundArchivePackIsForOneSet(GameTestHelper helper) {
        java.util.UUID card = java.util.UUID.randomUUID();
        Archive.holdForTesting(java.util.Set.of(card));
        String wrong = null;
        try {
            ItemStack found = null;
            for (int roll = 0; roll < 500 && found == null; roll++) {
                found = Archive.rollFor("minecraft:entities/wither", helper.getLevel().getRandom(), true).orElse(null);
            }
            PackComponent pack = found == null ? null : PackItem.packOf(found).orElse(null);
            if (pack == null) {
                wrong = "fixture: a full archive dropped nothing from five hundred withers";
            } else if (!pack.isArchive() || !"tst".equals(pack.kind())) {
                wrong = "a found archive pack is for '" + pack.kind() + "' rather than the one set there is";
            } else {
                var opened = Archive.firstWithCards(Archive.candidates(pack.kind(), helper.getLevel().getRandom())).join();
                if (opened.isEmpty() || !opened.get().family().equals("tst")
                        || !opened.get().printings().equals(java.util.List.of(card))) {
                    wrong = "an archive pack for one set opened as " + opened;
                } else if (Archive.draw(opened.get().printings(), helper.getLevel().getRandom()).size()
                        != dev.gathering.core.sealed.ArchiveDrops.CARDS) {
                    wrong = "an archive pack drew the wrong number of cards";
                }
            }
        } finally {
            Archive.clear();
        }
        if (wrong != null) {
            helper.fail(wrong);
            return;
        }
        helper.succeed();
    }

    /**
     * A full archive drops nothing once collecting is switched off.
     * <p>The sheet is emptied when the setting changes, but a walk over history begun before the
     * change could publish after it, and the roll never asked the switch itself. Checked at the
     * roll now, where no race can reach it.
     */
    /**
     * A boss nobody fought drops no archive pack.
     * <p>A wither farm is a boss dying over and over with no player in the fight, and one pack in two
     * is the most generous roll in the mod - so without this the one thing a player cannot buy would
     * be the one thing a machine hands out fastest.
     */
    @GameTest(template = "empty")
    public static void abossNobodyFoughtDropsNoArchivePack(GameTestHelper helper) {
        Archive.holdForTesting(java.util.Set.of(java.util.UUID.randomUUID()));
        try {
            for (int roll = 0; roll < 1_000; roll++) {
                if (Archive.rollFor("minecraft:entities/wither", helper.getLevel().getRandom(), false).isPresent()) {
                    helper.fail("an archive pack dropped off a wither no player had a hand in killing");
                    return;
                }
            }
            // And the chests it also comes out of never had a kill behind them to begin with.
            boolean fromAChest = false;
            for (int roll = 0; roll < 2_000 && !fromAChest; roll++) {
                fromAChest = Archive.rollFor("minecraft:chests/ancient_city",
                        helper.getLevel().getRandom(), false).isPresent();
            }
            if (!fromAChest) {
                helper.fail("fixture: a full archive dropped nothing from two thousand ancient city chests");
                return;
            }
        } finally {
            Archive.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFullArchiveDropsNothingWithCollectingOff(GameTestHelper helper) {
        Archive.holdForTesting(java.util.Set.of(java.util.UUID.randomUUID()));
        String[] wrong = {null};
        try {
            TestConfig.run("[modes]\ncollection_enabled = true\n", () -> {
                boolean any = false;
                for (int roll = 0; roll < 200 && !any; roll++) {
                    any = Archive.rollFor("minecraft:entities/wither", helper.getLevel().getRandom(), true).isPresent();
                }
                if (!any) {
                    wrong[0] = "fixture: a full archive dropped nothing from two hundred withers with collecting on";
                }
            });
            if (wrong[0] == null) {
                TestConfig.run("[modes]\ncollection_enabled = false\n", () -> {
                    for (int roll = 0; roll < 1_000; roll++) {
                        if (Archive.rollFor("minecraft:entities/wither", helper.getLevel().getRandom(), true).isPresent()) {
                            wrong[0] = "an archive pack dropped on a server with collecting switched off";
                            return;
                        }
                    }
                });
            }
        } finally {
            Archive.clear();
        }
        if (wrong[0] != null) {
            helper.fail(wrong[0]);
            return;
        }
        helper.succeed();
    }
}
