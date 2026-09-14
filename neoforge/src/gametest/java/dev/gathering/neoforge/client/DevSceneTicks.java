package dev.gathering.neoforge.client;

import dev.gathering.Gathering;
import dev.gathering.client.DevScene;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Ticks the scripted client run, in development runs only.
 * <p>Here, in the game-test source set, because this is the one place that is part of every
 * development run and no part of the jar. The shipped client tick used to call the scene
 * directly, which is what kept it in every release. {@code DevScene.tick} does nothing unless
 * {@code -Pdevscene} set its property, so a development client without it is unchanged.
 * <p>Client only, and never loaded on a dedicated server.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID, value = Dist.CLIENT)
public final class DevSceneTicks {

    private DevSceneTicks() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        DevScene.tick(Minecraft.getInstance());
    }
}
