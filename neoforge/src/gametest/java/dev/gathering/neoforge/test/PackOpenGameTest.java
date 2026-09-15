package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Right-clicking a pack, and the one thing that must never happen when it will not open.
 * <p>A booster is taken out of the hand before the opening starts, because opening reaches a
 * network and comes back later and a pack still in the hand when it does is a pack that can
 * be opened twice. Which means every way an opening can fail has to hand one back - and the
 * first version of this did not, so a right-click on a server with collecting switched off
 * ate the pack.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackOpenGameTest {

    /**
     * A pack whose opener is removed before its cards arrive - died and respawned, or a Deployer's
     * stand-in broken with the Deployer - counts as gone, so its cards are written down for them rather
     * than handed to an entity that is nowhere. The question, asked directly: waiting on a real pack
     * reaches Scryfall, and a run that is rate limited never gets as far as the answer.
     */
    @GameTest(template = "empty")
    public static void aRemovedOpenerCountsAsGone(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        if (dev.gathering.server.PackOpening.openerIsGone(player)) {
            helper.fail("a player standing in the world counts as gone");
            return;
        }
        player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        if (!dev.gathering.server.PackOpening.openerIsGone(player)) {
            helper.fail("a removed player does not count as gone, so their cards would go nowhere");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPackThatWillNotOpenComesBack(GameTestHelper helper) {
        TestConfig.withPlayer(helper, "[modes]\ncollection_enabled = false\n", player -> {
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
        TestConfig.withPlayer(helper, "[modes]\ncollection_enabled = false\n", player -> {
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
        TestConfig.withPlayer(helper, "[modes]\ncollection_enabled = true\n", player -> {
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

}
