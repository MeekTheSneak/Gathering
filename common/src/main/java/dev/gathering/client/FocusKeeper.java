package dev.gathering.client;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps keyboard focus on the same control across a screen rebuilding its widgets.
 * <p>A screen that rebuilds - after a setting changes, or when the server sends a fresh copy of what
 * it shows - throws its widgets away and makes new ones, and focus went with them: somebody moving
 * through a screen by keyboard was put back to nothing every time an event they were watching
 * changed. An audit reproduced it on the settings and tournament screens.
 * <p>So each control is named for what it does, not where it is - "report 2-1", "settle table 3" -
 * and after a rebuild focus goes back to the control with that name. Not by position: after a phase
 * change the third button may be a different action, and pressing Enter on it would do something the
 * player never chose. If the action is gone, focus goes to a fallback the screen names, which is
 * never a control that changes anything.
 * <p>Client-only.
 */
final class FocusKeeper {

    private final Map<GuiEventListener, String> names = new IdentityHashMap<>();

    /** Names a control for what it does. Returns it, for use inline where it is added. */
    <T extends GuiEventListener> T named(String name, T control) {
        names.put(control, name);
        return control;
    }

    /**
     * Rebuilds the screen, then puts focus back on the control doing what the focused one did - or,
     * if that is gone, on the named fallback. Nothing is focused afterwards that was not before.
     */
    void rebuild(Screen screen, Runnable rebuild, Supplier<String> fallback) {
        GuiEventListener focused = screen.getFocused();
        String was = focused == null ? null : names.get(focused);
        names.clear();
        rebuild.run();
        if (focused == null) {
            return;
        }
        GuiEventListener again = find(was);
        if (again == null) {
            again = find(fallback.get());
        }
        if (again != null) {
            screen.setFocused(again);
        }
    }

    private GuiEventListener find(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<GuiEventListener, String> entry : names.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
