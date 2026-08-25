package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.platform.Platform;
import dev.gathering.service.ServerSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Right-clicking a pack, and the one thing that must never happen when it will not open.
 *
 * <p>A booster is taken out of the hand before the opening starts, because opening reaches a
 * network and comes back later and a pack still in the hand when it does is a pack that can
 * be opened twice. Which means every way an opening can fail has to hand one back - and the
 * first version of this did not, so a right-click on a server with collecting switched off
 * ate the pack.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackOpenGameTest {

    private static final String FILE_NAME = "gathering-server.toml";

    @GameTest(template = "empty")
    public static void aPackThatWillNotOpenComesBack(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = false\n", player -> {
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    PackItem.of(new PackComponent("blb", "play")));

            player.getItemInHand(InteractionHand.MAIN_HAND)
                    .use(player.level(), player, InteractionHand.MAIN_HAND);

            int packs = packsHeldBy(player);
            if (packs != 1) {
                return "A pack that could not be opened left " + packs + " in the inventory";
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void twoPacksLoseOnlyTheOneThatWouldNotOpen(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = false\n", player -> {
            ItemStack two = PackItem.of(new PackComponent("blb", "play"));
            two.setCount(2);
            player.setItemInHand(InteractionHand.MAIN_HAND, two);

            player.getItemInHand(InteractionHand.MAIN_HAND)
                    .use(player.level(), player, InteractionHand.MAIN_HAND);

            int packs = packsHeldBy(player);
            if (packs != 2) {
                return "Two packs, one refused, came to " + packs;
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void aPackOfNothingIsNotEatenEither(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = true\n", player -> {
            // A component somebody wrote by hand with a set code that is not one. It never
            // reaches an opening at all, so it must not be taken out of the hand either.
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    PackItem.of(new PackComponent("../../etc/passwd", "play")));

            player.getItemInHand(InteractionHand.MAIN_HAND)
                    .use(player.level(), player, InteractionHand.MAIN_HAND);

            int packs = packsHeldBy(player);
            return packs == 1 ? null : "A pack of nothing came to " + packs;
        });
    }

    // ------------------------------------------------------------------- bits

    /** What a check found wrong, or null if it found nothing. */
    @FunctionalInterface
    private interface Check {
        String run(ServerPlayer player);
    }

    private static int packsHeldBy(ServerPlayer player) {
        int packs = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (PackItem.packOf(stack).isPresent()) {
                packs += stack.getCount();
            }
        }
        return packs;
    }

    private static void withConfig(GameTestHelper helper, String text, Check check) {
        Path file = Platform.get().configDirectory().resolve(FILE_NAME);
        String before = null;
        try {
            if (Files.isRegularFile(file)) {
                before = Files.readString(file, StandardCharsets.UTF_8);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            ServerSettings.load(Platform.get());

            String wrong = check.run(helper.makeMockServerPlayerInLevel());
            if (wrong != null) {
                helper.fail(wrong);
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("The config file could not be written: " + couldNotWrite);
        } finally {
            try {
                if (before == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.writeString(file, before, StandardCharsets.UTF_8);
                }
            } catch (IOException couldNotRestore) {
                // Nothing useful to do here; the next start writes a fresh one.
            }
            ServerSettings.load(Platform.get());
        }
    }
}
