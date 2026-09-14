package dev.gathering.fabric.test;

import dev.gathering.client.DevScene;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Ticks the scripted client run, in development runs only.
 * <p>The test mod's client entrypoint, because the test mod is the one thing on the development
 * client run that is not in the jar. The shipped client tick used to call the scene directly,
 * which is what kept it in every release. {@code DevScene.tick} does nothing unless
 * {@code -Pdevscene} set its property.
 */
public final class DevSceneTicks implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(DevScene::tick);
    }
}
