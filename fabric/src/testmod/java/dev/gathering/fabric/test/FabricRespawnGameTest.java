package dev.gathering.fabric.test;

import dev.gathering.item.PackItem;
import dev.gathering.server.Owed;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * What a player is owed is handed over when they respawn, on this loader.
 * <p>Nothing is put in the bag of a player on the death screen, because respawning makes a new player and
 * leaves that bag behind; what they are handed meanwhile is written down instead, and respawning is when
 * it comes. That hand-over is each loader's own respawn hook - Fabric's {@code AFTER_RESPAWN} here - so it
 * is checked on both: a hook left unregistered on one loader keeps a dead player's cards until they next
 * log in, and says nothing.
 */
public class FabricRespawnGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void whatIsOwedComesWithTheRespawn(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        ServerPlayer back = null;
        try {
            if (!Owed.aPack(player.getUUID(), "DMU", "draft", "")) {
                helper.fail("the fixture's owed pack could not be written down");
                return;
            }
            // On the death screen, then Respawn: the server's own respawn, which runs the hooks.
            player.setHealth(0.0F);
            back = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            int packs = 0;
            for (int slot = 0; slot < back.getInventory().getContainerSize(); slot++) {
                if (back.getInventory().getItem(slot).getItem() instanceof PackItem) {
                    packs += back.getInventory().getItem(slot).getCount();
                }
            }
            if (packs != 1) {
                helper.fail("respawning on this loader handed over " + packs + " of the 1 pack owed");
                return;
            }
            if (Owed.waitingFor(back.getUUID()) != 0) {
                helper.fail("the pack was handed over on respawning and is still owed as well");
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(player.getUUID());
            if (back != null) {
                helper.getLevel().getServer().getPlayerList().remove(back);
            }
        }
    }
}
