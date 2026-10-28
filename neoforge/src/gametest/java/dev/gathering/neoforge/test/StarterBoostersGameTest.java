package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.MagicColor;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.ServerRun;
import dev.gathering.server.StarterBoosters;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Two boosters for finishing the guided first game, and only two, ever.
 * <p>This is the one place the tutorial puts a real card into an economy, so the rule that
 * matters is not that it works - it is that it cannot be made to work twice. Redoing the
 * tutorial is free and always will be; being paid for redoing it is not.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarterBoostersGameTest {

    /** How many sealed packs this player is carrying. */
    private static int packsCarriedBy(ServerPlayer player) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof PackItem) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * Runs a check for a player who has finished the lesson, which is what the packs are for, and then takes them
     * back off both lists.
     */
    private static void forgetting(UUID who, Runnable check) {
        dev.gathering.server.LessonRecords.finishedForTesting(who);
        try {
            check.run();
        } finally {
            StarterBoosters.forget(who);
            dev.gathering.server.LessonRecords.unfinishForTesting(who);
        }
    }

    /**
     * No lesson finished on this world, no packs: the owner's rule. Asked straight for them, the way a request
     * sent without playing the lesson asks, the answer is to go and play it.
     */
    @GameTest(template = "empty")
    public static void nopacksbeforethelessonisfinished(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        StarterBoosters.forget(player.getUUID());
        dev.gathering.server.LessonRecords.unfinishForTesting(player.getUUID());
        try {
            StarterBoosters.Outcome how = StarterBoosters.give(player, List.of(MagicColor.RED, MagicColor.GREEN));
            if (how != StarterBoosters.Outcome.LESSON_NOT_FINISHED || packsCarriedBy(player) != 0) {
                helper.fail("asking for the starter packs without finishing the lesson answered " + how
                        + " and handed over " + packsCarriedBy(player));
                return;
            }
            helper.succeed();
        } finally {
            StarterBoosters.forget(player.getUUID());
        }
    }

    /**
     * A lesson is written down as finished only when it began, took at least the time the steps take, and had
     * every step done - not on a finish nobody began, one sent straight after beginning, or one with a step
     * missing.
     */
    @GameTest(template = "empty")
    public static void onlyALessonReallyPlayedIsWrittenDown(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID id = player.getUUID();
        dev.gathering.server.LessonRecords.unfinishForTesting(id);
        List<String> every = java.util.Arrays.stream(dev.gathering.core.tutorial.TutorialStep.values()).map(Enum::name).toList();
        long[] now = {1_000L};
        var was = dev.gathering.server.LessonRecords.clock;
        dev.gathering.server.LessonRecords.clock = () -> now[0];
        try {
            dev.gathering.server.LessonRecords.handle(player, new dev.gathering.network.LessonPayload(true, every));
            if (dev.gathering.server.LessonRecords.finished(id)) {
                helper.fail("a finish to a lesson that never began was written down");
                return;
            }
            dev.gathering.server.LessonRecords.handle(player, new dev.gathering.network.LessonPayload(false, List.of()));
            now[0] += 5 * 20;
            dev.gathering.server.LessonRecords.handle(player, new dev.gathering.network.LessonPayload(true, every));
            if (dev.gathering.server.LessonRecords.finished(id)) {
                helper.fail("a lesson finished five seconds after it began was written down");
                return;
            }
            dev.gathering.server.LessonRecords.handle(player, new dev.gathering.network.LessonPayload(false, List.of()));
            now[0] += 60 * 20;
            dev.gathering.server.LessonRecords.handle(player,
                    new dev.gathering.network.LessonPayload(true, every.subList(0, every.size() - 1)));
            if (dev.gathering.server.LessonRecords.finished(id)) {
                helper.fail("a lesson finished with a step missing was written down");
                return;
            }
            dev.gathering.server.LessonRecords.handle(player, new dev.gathering.network.LessonPayload(false, List.of()));
            now[0] += 60 * 20;
            dev.gathering.server.LessonRecords.handle(player, new dev.gathering.network.LessonPayload(true, every));
            if (!dev.gathering.server.LessonRecords.finished(id)) {
                helper.fail("a lesson begun, played for a minute and finished with every step was not written down");
                return;
            }
            helper.succeed();
        } finally {
            dev.gathering.server.LessonRecords.clock = was;
            dev.gathering.server.LessonRecords.unfinishForTesting(id);
        }
    }

    /** Two different colors buys two packs. */
    @GameTest(template = "empty")
    public static void twocolorsbuytwopacks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        forgetting(player.getUUID(), () -> {
            StarterBoosters.forget(player.getUUID());
            StarterBoosters.Outcome how =
                    StarterBoosters.give(player, List.of(MagicColor.RED, MagicColor.GREEN));
            if (how != StarterBoosters.Outcome.GIVEN) {
                helper.fail("finishing the guided first game gave nothing: " + how);
                return;
            }
            if (packsCarriedBy(player) != 2) {
                helper.fail("two colors bought " + packsCarriedBy(player) + " packs");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The colors are on the packs, so the seed picks from that color's arrangements.
     * <p>What is <em>inside</em> is still decided when the pack is torn. What this checks is
     * that the choice reached the pack at all - without it, picking red and green would hand
     * over two packs that could open as anything.
     */
    @GameTest(template = "empty")
    public static void thepackscarrythecolorsthatwerepicked(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        forgetting(player.getUUID(), () -> {
            StarterBoosters.forget(player.getUUID());
            StarterBoosters.give(player, List.of(MagicColor.WHITE, MagicColor.BLUE));
            java.util.Set<String> colors = new java.util.LinkedHashSet<>();
            for (ItemStack stack : player.getInventory().items) {
                PackComponent pack = PackItem.packOf(stack).orElse(null);
                if (pack != null) {
                    colors.add(pack.color());
                }
            }
            if (!colors.equals(java.util.Set.of("W", "U"))) {
                helper.fail("the packs carry " + colors + " rather than the W and U picked");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Asking twice gives nothing the second time.
     * <p>The rule the whole thing rests on. Without it, a player who worked out that finishing
     * the tutorial pays would finish it every four minutes.
     */
    @GameTest(template = "empty")
    public static void askingtwicegivesnothingthesecondtime(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        forgetting(player.getUUID(), () -> {
            StarterBoosters.forget(player.getUUID());
            StarterBoosters.give(player, List.of(MagicColor.RED, MagicColor.GREEN));
            int after = packsCarriedBy(player);

            StarterBoosters.Outcome again =
                    StarterBoosters.give(player, List.of(MagicColor.BLACK, MagicColor.BLUE));
            if (again != StarterBoosters.Outcome.ALREADY) {
                helper.fail("asking a second time answered " + again);
                return;
            }
            if (packsCarriedBy(player) != after) {
                helper.fail("asking a second time handed over "
                        + (packsCarriedBy(player) - after) + " more pack(s)");
                return;
            }
            helper.succeed();
        });
    }

    /** Two of the same color is not two colors, and buys nothing. */
    @GameTest(template = "empty")
    public static void thesamecolortwiceisrefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        forgetting(player.getUUID(), () -> {
            StarterBoosters.forget(player.getUUID());
            StarterBoosters.Outcome how =
                    StarterBoosters.give(player, List.of(MagicColor.RED, MagicColor.RED));
            if (how != StarterBoosters.Outcome.NOT_TWO_COLORS) {
                helper.fail("the same color twice answered " + how);
                return;
            }
            if (packsCarriedBy(player) != 0) {
                helper.fail("the same color twice still handed over a pack");
                return;
            }
            // And it did not use up their one go.
            if (StarterBoosters.alreadyHad(player.getUUID())) {
                helper.fail("a refused ask used up their one starter");
                return;
            }
            helper.succeed();
        });
    }

    /** One color, three colors, or none at all are all refused the same way. */
    @GameTest(template = "empty")
    public static void thewrongnumberofcolorsisrefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        forgetting(player.getUUID(), () -> {
            StarterBoosters.forget(player.getUUID());
            for (List<MagicColor> asked : List.of(
                    List.<MagicColor>of(),
                    List.of(MagicColor.RED),
                    List.of(MagicColor.RED, MagicColor.GREEN, MagicColor.BLUE))) {
                if (StarterBoosters.give(player, asked)
                        != StarterBoosters.Outcome.NOT_TWO_COLORS) {
                    helper.fail(asked.size() + " colors was not refused");
                    return;
                }
            }
            if (packsCarriedBy(player) != 0) {
                helper.fail("a refused ask still handed over a pack");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Nothing is handed over when the record cannot be written.
     * <p>Written down first and handed over second, the same order the owed ledger settled on.
     * The other way round turns one failed write into an unlimited supply of boosters.
     */
    @GameTest(template = "empty")
    public static void arecordthatcannotbewrittenhandsnothingover(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        Path folder = ServerRun.inSave("starter").orElseThrow();
        Path blocked = folder.resolve("given.txt.writing");
        try {
            StarterBoosters.forget(player.getUUID());
            // Finished the lesson, so what is refused is the record and not the player.
            dev.gathering.server.LessonRecords.finishedForTesting(player.getUUID());
            Files.createDirectories(folder);
            // A directory where the temporary file wants to be, so the write cannot land.
            Files.createDirectory(blocked);
            Files.writeString(blocked.resolve("in-the-way"), "fault injection");

            StarterBoosters.Outcome how =
                    StarterBoosters.give(player, List.of(MagicColor.RED, MagicColor.GREEN));
            if (how != StarterBoosters.Outcome.COULD_NOT_RECORD) {
                helper.fail("a blocked record answered " + how);
                return;
            }
            if (packsCarriedBy(player) != 0) {
                helper.fail("a starter that could not be written down was handed over anyway");
                return;
            }
            helper.succeed();
        } finally {
            Files.deleteIfExists(blocked.resolve("in-the-way"));
            Files.deleteIfExists(blocked);
            StarterBoosters.forget(player.getUUID());
            dev.gathering.server.LessonRecords.unfinishForTesting(player.getUUID());
        }
    }
}
