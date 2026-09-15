package dev.gathering.neoforge.client;

import dev.gathering.Gathering;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Ticks the modpack scene, in development runs with {@code -Ppackscene} only. See {@link PackScene}.
 * <p>Asks the property before naming the scene, whose steps reach into Create's and Sable's classes.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID, value = Dist.CLIENT)
public final class PackSceneTicks {

    private PackSceneTicks() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty("gathering.packscene") != null) {
            PackScene.tick(Minecraft.getInstance());
        }
    }
}
