package dev.gathering.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Whether the read-a-card key is being held, including while a screen is open.
 * <p>{@link KeyMapping#isDown()} cannot answer that. {@code KeyboardHandler} only calls
 * {@code KeyMapping.set(key, true)} when {@code minecraft.screen == null}, while release
 * always clears it - so a key mapping reads as permanently up inside <em>any</em> screen.
 * Binding the overlay to {@code isDown()} therefore made it work in the world and silently
 * never work over an inventory slot or a deck list, which is exactly where reading a card
 * matters most.
 * <p>So this asks the window for the physical key instead. Mouse bindings and unbound keys
 * fall back to the mapping, which is correct for them: neither is affected by the above.
 * <p>Which key a mapping is currently bound to is the one thing this cannot answer on its
 * own: {@code KeyMapping#getKey} is a NeoForge addition and Fabric has its own helper, so the
 * caller supplies it. That keeps the reasoning here and the loader-specific lookup where it
 * belongs.
 * <p>Client-only.
 */
public final class ZoomKeyState {

    private ZoomKeyState() {
    }

    /**
     * @param boundKey how to ask this loader which key the mapping currently sits on
     */
    public static BooleanSupplier of(KeyMapping mapping, Supplier<InputConstants.Key> boundKey) {
        return () -> isHeld(mapping, boundKey.get());
    }

    public static boolean isHeld(KeyMapping mapping, InputConstants.Key key) {
        if (mapping.isUnbound() || key == null) {
            return false;
        }
        if (key.getType() != InputConstants.Type.KEYSYM) {
            return mapping.isDown();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
    }
}
