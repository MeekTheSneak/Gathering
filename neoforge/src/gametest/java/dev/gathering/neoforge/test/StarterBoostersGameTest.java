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

    /** Runs a check and then takes this player back off the starter list. */
    private static void forgetting(UUID who, Runnable check) {
        try {
            check.run();
        } finally {
            StarterBoosters.forget(who);
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
        }
    }
}
