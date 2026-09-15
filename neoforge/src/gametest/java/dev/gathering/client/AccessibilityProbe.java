package dev.gathering.client;

import dev.gathering.Gathering;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.HostActions;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.EventViewPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The event and settings screens at the text and control sizes a player can pick, checked rather
 * than only photographed: widgets grow with the control size, nothing overlaps or leaves the window,
 * keyboard focus survives a setting change, a refresh and an action disappearing, a host is offered
 * only the controls the event would take, and calling an event off asks first.
 * <p>From the polish audit of 15 September 2026, whose probe logged these defects; this one fails on
 * them. {@code ./gradlew :neoforge:runClient -Paccessibilityprobe}. Opens production screens with an
 * event view built from a real tournament; connects to no server. Pictures in
 * {@code neoforge/run/screenshots/access-*.png}.
 */
@EventBusSubscriber(modid = Gathering.MOD_ID, value = Dist.CLIENT)
public final class AccessibilityProbe {

    private static final boolean ARMED = System.getProperty("gathering.accessibilityprobe") != null;
    private static final List<String> FAILURES = new ArrayList<>();
    private static int stage;
    private static int waited;
    private static int ticks;
    private static String focusedBefore;
    private static int ordinaryResultHeight;
    private static final java.util.Set<String> SEEN = new java.util.LinkedHashSet<>();
    private static final java.util.Set<String> HOST_SEEN = new java.util.LinkedHashSet<>();
    private static int HOST_PAGES;
    private static int wasGuiScale = -1;
    private static int wasControls = 100;
    private static int wasText = 100;
    private static boolean wasReducedMotion;

    private AccessibilityProbe() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ARMED) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (++ticks > 20 * 120) {
            fail("stopped moving at stage " + stage);
            finish(client);
            return;
        }
        if (waited-- > 0) {
            return;
        }
        try {
            step(client);
        } catch (Throwable broken) {
            broken.printStackTrace();
            fail("stage " + stage + " threw " + broken);
            finish(client);
        }
    }

    private static void step(Minecraft client) {
        switch (stage) {
            case 0 -> {
                if (client.getOverlay() != null || client.screen == null) {
                    return;
                }
                client.options.pauseOnLostFocus = false;
                // Put back when the probe ends, however it ends: the scripted tour runs in this same
                // directory, and one run that stopped early left it at twice the sizes in the smallest
                // window, which the tour then inherited.
                wasGuiScale = client.options.guiScale().get();
                wasControls = ClientSettings.controlScale();
                wasText = ClientSettings.textScale();
                wasReducedMotion = ClientSettings.reducedMotion();
                client.options.guiScale().set(2);
                client.resizeDisplay();
                sizes(100, 100);
                client.setScreen(new SettingsScreen(null));
                next();
            }
            case 1 -> {
                // A setting pressed by keyboard keeps the keyboard on it through the rebuild.
                GuiEventListener row = client.screen.children().stream()
                        .filter(child -> message(child).startsWith("Reduced motion")).findFirst().orElseThrow();
                client.screen.setFocused(row);
                focusedBefore = message(row);
                client.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                next();
            }
            case 2 -> {
                String after = message(client.screen.getFocused());
                say("settings focus before=" + focusedBefore + " after=" + after);
                if (!after.startsWith("Reduced motion")) {
                    fail("pressing a setting by keyboard left focus on \"" + after + "\"");
                }
                // Put back as it was.
                client.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                sizes(100, 100);
                EventScreen.accept(view(swiss(), true, false));
                next();
            }
            case 3 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-event-100");
                laidOut(screen, "controls 100% text 100%");
                ordinaryResultHeight = heightOf(screen, "2-1");
                if (ordinaryResultHeight != 18) {
                    fail("a result button at the shipped sizes is " + ordinaryResultHeight + " tall, not 18");
                }
                sizes(200, 100);
                EventScreen.accept(view(swiss(), true, false));
                next();
            }
            case 4 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-event-controls-200");
                laidOut(screen, "controls 200%");
                int larger = heightOf(screen, "2-1");
                say("result button at controls 200%: " + larger + " (at 100%: " + ordinaryResultHeight + ")");
                if (larger < ordinaryResultHeight * 2) {
                    fail("at 200% control size a result button is " + larger + " tall, not twice " + ordinaryResultHeight);
                }
                if (tabHeight(screen) < 32) {
                    fail("at 200% control size a tab is " + tabHeight(screen) + " tall");
                }
                sizes(100, 200);
                EventScreen.accept(view(swiss(), true, false));
                next();
            }
            case 5 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-event-text-200");
                laidOut(screen, "text 200%");
                int line = screen.layout().line();
                if (line < 19) {
                    fail("at 200% text a line is " + line + " tall, less than its writing");
                }
                if (screen.layout().tab(0, 4).y() < screen.layout().headerY() + line) {
                    fail("at 200% text the tabs start inside the header line");
                }
                sizes(200, 200);
                EventScreen.accept(view(swiss(), true, false));
                next();
            }
            case 6 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-event-both-200");
                laidOut(screen, "both 200%");
                // The host's tab during Swiss: registration's controls grayed, each saying why.
                sizes(100, 100);
                screen.showTab(EventScreen.Tab.HOST);
                next();
            }
            case 7 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-host-during-swiss");
                laidOut(screen, "host tab during Swiss");
                offered(screen, "Open check-in", false);
                offered(screen, "Begin", false);
                offered(screen, "Start now", false);
                offered(screen, "Register here", false);
                offered(screen, "Add this table", true);
                offered(screen, "Call off", true);
                EventScreen.accept(view(signup(), true, false));
                next();
            }
            case 8 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-host-during-signup");
                offered(screen, "Open check-in", true);
                offered(screen, "Begin", true);
                offered(screen, "Start now", false);
                offered(screen, "Register here", true);
                // A refresh keeps the keyboard where it was.
                GuiEventListener begin = widget(screen, "Begin");
                screen.setFocused(begin);
                EventScreen.accept(view(signup(), true, false));
                if (!"Begin".equals(message(client.screen.getFocused()))) {
                    fail("a refresh of the same event moved focus from Begin to \"" + message(client.screen.getFocused()) + "\"");
                }
                // Calling off asks first.
                ((net.minecraft.client.gui.components.Button) widget(eventScreen(client), "Call off")).onPress();
                next();
            }
            case 9 -> {
                shoot(client, "access-call-off-asks");
                if (!(client.screen instanceof ConfirmScreen)) {
                    fail("Call off went straight through rather than asking: " + client.screen);
                }
                client.screen.onClose();
                next();
            }
            case 10 -> {
                // Focus on a result button, then the result is confirmed and the buttons go: focus
                // falls back to the tab, never onto whatever took the button's place.
                EventScreen screen = eventScreen(client);
                screen.showTab(EventScreen.Tab.OVERVIEW);
                EventScreen.accept(view(swiss(), true, false));
                screen = eventScreen(client);
                screen.setFocused(widget(screen, "2-1"));
                EventScreen.accept(view(swiss(), true, true));
                String after = message(client.screen.getFocused());
                say("focus after the result buttons went: " + after);
                if (!"Overview".equals(after)) {
                    fail("with its result button gone, focus went to \"" + after + "\" rather than the tab");
                }
                // A small window at the largest sizes: nothing leaves it, and the results are reached.
                client.options.guiScale().set(4);
                client.resizeDisplay();
                sizes(200, 200);
                EventScreen.accept(view(swiss(), true, false));
                next();
            }
            case 11 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-small-window-200");
                say("small window " + screen.width + "x" + screen.height);
                laidOut(screen, "small window at 200%");
                AbstractWidget report = find(screen, "Report result");
                AbstractWidget result = find(screen, "2-1");
                if (report == null && result == null) {
                    fail("in a small window at 200% there is no way to report a result");
                }
                if (report != null) {
                    ((net.minecraft.client.gui.components.Button) report).onPress();
                }
                next();
            }
            case 12 -> {
                EventScreen screen = eventScreen(client);
                shoot(client, "access-small-window-reporting");
                laidOut(screen, "reporting in a small window at 200%");
                if (find(screen, "2-1") == null) {
                    fail("reporting in a small window shows no result buttons");
                } else if (heightOf(screen, "2-1") < 32) {
                    fail("reporting in a small window squeezed a result button to " + heightOf(screen, "2-1"));
                }
                // Two lines are written above them - the table, then what to do - and the second
                // line's writing reaches half its growth below where it is placed.
                AbstractWidget first = find(screen, "2-0");
                int writingBottom = screen.layout().bodyTop() + screen.layout().line()
                        + Math.round(client.font.lineHeight * (1f + GuiText.askedScale()) / 2f);
                if (first != null && first.getY() < writingBottom) {
                    fail("reporting in a small window put the results at " + first.getY()
                            + ", over the line telling the player to report, which reaches " + writingBottom);
                }
                screen.children().stream().filter(child -> child instanceof AbstractWidget widget
                        && widget.getMessage().getString().matches("\\d-\\d(-\\d)?"))
                        .forEach(child -> SEEN.add(((AbstractWidget) child).getMessage().getString()));
                AbstractWidget later = find(screen, ">");
                if (later == null || !later.active) {
                    fail("reporting in a small window shows no way to the other results");
                } else {
                    ((net.minecraft.client.gui.components.Button) later).onPress();
                }
                next();
            }
            case 13 -> {
                // Every result reachable, a page at a time, each page laid out properly.
                EventScreen screen = eventScreen(client);
                laidOut(screen, "a later page of results in a small window at 200%");
                screen.children().stream().filter(child -> child instanceof AbstractWidget widget
                        && widget.getMessage().getString().matches("\\d-\\d(-\\d)?"))
                        .forEach(child -> SEEN.add(((AbstractWidget) child).getMessage().getString()));
                AbstractWidget later = find(screen, ">");
                if (later != null && later.active && SEEN.size() < 32) {
                    ((net.minecraft.client.gui.components.Button) later).onPress();
                    waited = 10;
                    return;
                }
                shoot(client, "access-small-window-last-results");
                say("results reached a page at a time: " + SEEN);
                if (SEEN.size() != 16 || !SEEN.contains("0-0") || !SEEN.contains("1-1-1")) {
                    fail("only " + SEEN.size() + " of the 16 results can be reached in a small window: " + SEEN);
                }
                EventScreen.accept(view(swiss(), true, false));
                eventScreen(client).showTab(EventScreen.Tab.HOST);
                next();
            }
            case 14 -> {
                // Every host control reachable in the small window too, a page at a time.
                EventScreen screen = eventScreen(client);
                laidOut(screen, "the host tab in a small window at 200%");
                screen.children().stream().filter(child -> child instanceof AbstractWidget)
                        .map(child -> ((AbstractWidget) child).getMessage().getString())
                        .forEach(HOST_SEEN::add);
                shoot(client, "access-small-window-host-" + HOST_PAGES++);
                AbstractWidget later = find(screen, "Show the host controls after these");
                if (later != null && later.active && HOST_SEEN.size() < 64) {
                    ((net.minecraft.client.gui.components.Button) later).onPress();
                    waited = 10;
                    return;
                }
                List<String> wanted = List.of("Open check-in", "Begin", "Start now", "Add this table", "Prize: place 1",
                        "Call off", "Register here");
                say("host controls reached a page at a time: " + HOST_SEEN);
                if (!HOST_SEEN.containsAll(wanted)) {
                    fail("in a small window the host can reach only " + HOST_SEEN + " of " + wanted);
                }
                finish(client);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ checks

    /** Every widget inside the window and the panel, none on top of another, none shorter than its writing. */
    private static void laidOut(EventScreen screen, String at) {
        Rect panel = screen.layout().panel();
        List<AbstractWidget> widgets = screen.children().stream()
                .filter(child -> child instanceof AbstractWidget).map(child -> (AbstractWidget) child).toList();
        if (widgets.stream().noneMatch(widget -> widget.getMessage().getString().equals("Done"))) {
            fail(at + ": no Done button");
        }
        for (AbstractWidget widget : widgets) {
            String name = widget.getMessage().getString();
            if (widget.getX() < panel.x() || widget.getY() < panel.y() || widget.getX() + widget.getWidth() > panel.right()
                    || widget.getY() + widget.getHeight() > panel.bottom()) {
                fail(at + ": \"" + name + "\" at " + widget.getX() + "," + widget.getY() + " " + widget.getWidth() + "x"
                        + widget.getHeight() + " is outside the panel " + panel);
            }
            if (widget.getX() + widget.getWidth() > screen.width || widget.getY() + widget.getHeight() > screen.height) {
                fail(at + ": \"" + name + "\" is off the window");
            }
            // Tabs are their own, shorter row; every other control is a row's height.
            int wanted = isTab(name) ? screen.layout().tab(0, 4).height() : screen.layout().row();
            if (widget.getHeight() < wanted && !name.isEmpty()) {
                fail(at + ": \"" + name + "\" is " + widget.getHeight() + " tall, less than its row of " + wanted);
            }
        }
        // Worded buttons side by side draw their words at one size.
        java.util.Map<Integer, List<AbstractWidget>> rows = new java.util.TreeMap<>();
        for (AbstractWidget widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.components.Button button && GatheringButtons.labelScale(button) > 0f) {
                rows.computeIfAbsent(widget.getY(), ignored -> new ArrayList<>()).add(widget);
            }
        }
        for (List<AbstractWidget> row : rows.values()) {
            float first = GatheringButtons.labelScale((net.minecraft.client.gui.components.Button) row.get(0));
            for (AbstractWidget widget : row) {
                float scale = GatheringButtons.labelScale((net.minecraft.client.gui.components.Button) widget);
                if (Math.abs(scale - first) > 0.001f) {
                    fail(at + ": \"" + widget.getMessage().getString() + "\" is drawn at " + scale + " beside \""
                            + row.get(0).getMessage().getString() + "\" at " + first + " in the same row");
                }
            }
        }
        for (int one = 0; one < widgets.size(); one++) {
            for (int two = one + 1; two < widgets.size(); two++) {
                AbstractWidget a = widgets.get(one);
                AbstractWidget b = widgets.get(two);
                if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                        && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight()) {
                    fail(at + ": \"" + a.getMessage().getString() + "\" and \"" + b.getMessage().getString() + "\" overlap");
                }
            }
        }
    }

    private static boolean isTab(String name) {
        return name.equals("Overview") || name.equals("Standings") || name.equals("Pairings") || name.equals("Host");
    }

    private static void offered(EventScreen screen, String name, boolean live) {
        AbstractWidget widget = find(screen, name);
        if (widget == null) {
            fail("the host's \"" + name + "\" is missing");
            return;
        }
        if (widget.active != live) {
            fail("the host's \"" + name + "\" is " + (widget.active ? "live" : "grayed") + " in phase " + screen.view().phase());
        }
        if (!live && widget.getTooltip() == null) {
            fail("the grayed \"" + name + "\" does not say why");
        }
    }

    private static int heightOf(EventScreen screen, String name) {
        AbstractWidget widget = find(screen, name);
        return widget == null ? -1 : widget.getHeight();
    }

    private static int tabHeight(EventScreen screen) {
        return heightOf(screen, "Overview");
    }

    private static AbstractWidget find(Screen screen, String name) {
        return screen.children().stream().filter(child -> child instanceof AbstractWidget widget
                && widget.getMessage().getString().equals(name)).map(child -> (AbstractWidget) child).findFirst().orElse(null);
    }

    private static GuiEventListener widget(Screen screen, String name) {
        AbstractWidget found = find(screen, name);
        if (found == null) {
            throw new IllegalStateException("no \"" + name + "\" on " + screen);
        }
        return found;
    }

    private static EventScreen eventScreen(Minecraft client) {
        if (client.screen instanceof EventScreen screen) {
            return screen;
        }
        throw new IllegalStateException("the event screen is not open: " + client.screen);
    }

    private static String message(GuiEventListener listener) {
        return listener instanceof AbstractWidget widget ? widget.getMessage().getString() : String.valueOf(listener);
    }

    // ------------------------------------------------------------------ fixtures

    private static void sizes(int controls, int text) {
        ClientSettings.controlScale(controls);
        ClientSettings.textScale(text);
    }

    private static final UUID EVENT = new UUID(9L, 9L);
    private static final UUID HOST = new UUID(9L, 1L);

    private static Tournament signup() {
        Tournament tournament = Tournament.create(EVENT, "Friday Night Gathering", HOST,
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        for (int index = 0; index < 8; index++) {
            tournament = tournament.register(Entrant.registering(new UUID(9L, 100 + index), "Player " + index, 1500 - index));
        }
        return tournament;
    }

    private static Tournament swiss() {
        Tournament tournament = signup().beginPreparing();
        for (int index = 0; index < 8; index++) {
            tournament = tournament.markReady(new UUID(9L, 100 + index));
        }
        return tournament.startSwiss();
    }

    /** An event view as its host sees it, with the host's controls worked out by the server's rules. */
    private static EventViewPayload view(Tournament tournament, boolean host, boolean confirmed) {
        List<String> refusals = new ArrayList<>();
        for (HostActions.Action action : HostActions.Action.values()) {
            refusals.add(host ? HostActions.refusal(action, tournament, false).orElse("") : "");
        }
        boolean playing = tournament.phase() == Tournament.Phase.SWISS;
        EventViewPayload.Mine mine = playing
                ? new EventViewPayload.Mine(1, "Opponent", "2-1", "", "", confirmed ? "2-1" : "", -1)
                : EventViewPayload.Mine.NONE;
        return new EventViewPayload(EVENT, tournament.name(), "Host", host, tournament.phase().key(), "constructed", "Modern",
                3, 50, 25, 0, "locked", false, playing ? 1 : 0, 3, playing ? 2930 : -1, false, false,
                true, true, true, false, tournament.entrants().size(), List.of(), List.of(), mine,
                List.of("1: 3 x Diamond"), List.of(), host ? refusals : List.of(), true);
    }

    // ------------------------------------------------------------------ plumbing

    private static void next() {
        stage++;
        waited = 20;
    }

    private static void shoot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> {
        });
    }

    private static void say(String line) {
        System.out.println("[accessibility] " + line);
    }

    private static void fail(String what) {
        FAILURES.add(what);
        say("FAIL " + what);
    }

    private static void finish(Minecraft client) {
        // Said for whoever runs this behind other windows: a scripted run's timing checks depend on it.
        say("frames per second at the end: " + client.getFps());
        // Anything of the mod's own cut short to an ellipsis on any screen drawn: a label that has
        // stopped saying what it was written to say. The tour fails on the same thing.
        if (!GuiText.trimmedCopy().isEmpty()) {
            fail("the mod's own lines were cut short: " + new java.util.TreeSet<>(GuiText.trimmedCopy()));
        }
        if (wasGuiScale >= 0) {
            client.options.guiScale().set(wasGuiScale);
            client.resizeDisplay();
            // Not flushed here: stopping the game writes what is waiting, which this relies on
            // and the shell checks afterwards (see TESTING.md).
            sizes(wasControls, wasText);
            // Pressed by keyboard in the first stages and pressed again to put it back - which does
            // nothing when that very focus is what broke. A run that lost it left reduced motion on,
            // and the scripted tour after it found every card arriving without flying.
            ClientSettings.reducedMotion(wasReducedMotion);
            say("sizes put back to controls " + wasControls + " text " + wasText
                    + "; the settings file should say so once the game has closed");
        }
        say("failures: " + FAILURES.size());
        stage = 99;
        client.stop();
    }
}
