package dev.gathering.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Whether the game's own keys still work with Gathering installed on Fabric, in a development run only.
 * <p>{@code ./gradlew :fabric:runKeyScene}. Fabric keeps vanilla's key lookup, which holds one mapping per key:
 * a mod mapping on a vanilla key's default takes that key from the game - pressing it clicks the mod's mapping
 * and not, say, Drop. So each of the game's own mappings is pressed, the way a key press reaches them, and the
 * log says which still receive their press. NeoForge's lookup holds several mappings per key and is not at risk.
 */
public final class KeyScene implements ClientModInitializer {

    private static final boolean ARMED = System.getProperty("gathering.keyscene") != null;
    private static int ticks;

    @Override
    public void onInitializeClient() {
        if (ARMED) {
            ClientTickEvents.END_CLIENT_TICK.register(KeyScene::tick);
        }
    }

    private static void tick(Minecraft client) {
        if (++ticks < 40 || client.getOverlay() != null) {
            return;
        }
        List<KeyMapping> vanilla = new ArrayList<>(List.of(client.options.keyDrop, client.options.keyInventory,
                client.options.keySwapOffhand, client.options.keyCommand, client.options.keyChat,
                client.options.keyAdvancements, client.options.keyTogglePerspective));
        vanilla.addAll(List.of(client.options.keyHotbarSlots));
        int lost = 0;
        for (KeyMapping mapping : vanilla) {
            while (mapping.consumeClick()) {
                // Nothing left over from before.
            }
            InputConstants.Key key = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(mapping);
            KeyMapping.click(key);
            boolean heard = mapping.consumeClick();
            if (!heard) {
                lost++;
            }
            System.out.println("[keyscene] " + mapping.getName() + " on " + key.getName() + ": " + (heard ? "works" : "TAKEN"));
        }
        System.out.println("[keyscene] vanilla keys taken: " + lost);
        // And the table's own verbs still answer to their keys inside the table's screen, which is the only place
        // they are read - after the lookup has been rebuilt, as moving a key in the Controls screen rebuilds it.
        KeyMapping.resetMapping();
        String onQ = dev.gathering.client.TableShortcuts.actionFor(org.lwjgl.glfw.GLFW.GLFW_KEY_Q, 0);
        String onTwo = dev.gathering.client.TableShortcuts.actionFor(org.lwjgl.glfw.GLFW.GLFW_KEY_2, 0);
        System.out.println("[keyscene] in the table's screen Q is " + onQ + " and 2 is " + onTwo);
        client.stop();
        ticks = Integer.MIN_VALUE;
    }
}
