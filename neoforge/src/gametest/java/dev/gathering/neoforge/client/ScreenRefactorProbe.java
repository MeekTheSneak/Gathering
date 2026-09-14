package dev.gathering.neoforge.client;

import dev.gathering.client.*;
import dev.gathering.network.ReplayFramePayload;
import dev.gathering.network.WatchReplayPayload;
import dev.gathering.core.game.persistence.ViewCodec;
import dev.gathering.core.ui.ReplayStrip;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.card.Sleeve;
import dev.gathering.core.game.*;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.ui.Rect;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Deterministic screenshots through the real table card path, before and after extraction.
 * Run with :neoforge:runClient -Pdevscene -PscreenRefactorScene=LABEL. */
@EventBusSubscriber(modid = "gathering", value = Dist.CLIENT)
public final class ScreenRefactorProbe {
    private static int ticks;
    private static boolean done;
    private static Gallery gallery;
    private static Boolean autosaveIndicator;
    private static TextPromptScreen observedPrompt;

    /** Observes the existing tour without supplying input or changing its state. */
    private static void observe(Minecraft client) {
        if (observedPrompt != null && client.screen != observedPrompt) {
            for (var child : observedPrompt.children()) {
                if (child instanceof net.minecraft.client.gui.components.EditBox field) {
                    System.out.println("[screen-refactor] prompt closed: value=" + field.getValue()
                            + "; focused=" + field.isFocused() + "; next="
                            + (client.screen == null ? "none" : client.screen.getClass().getSimpleName()));
                }
            }
            observedPrompt = null;
        }
        if (client.screen instanceof TextPromptScreen prompt && observedPrompt != prompt) {
            observedPrompt = prompt;
            for (var child : prompt.children()) {
                if (child instanceof net.minecraft.client.gui.components.EditBox field) {
                    System.out.println("[screen-refactor] prompt opened: focused=" + field.isFocused());
                }
            }
        }
    }
    private static TableScreen watcher;
    private static java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> sender;
    private static final java.util.List<Integer> requests = new java.util.ArrayList<>();

    @SuppressWarnings("unchecked")
    private static void openReplay(Minecraft client) throws Exception {
        byte[] board = ViewCodec.write(TutorialDemo.board().orElseThrow());
        var field = ClientNetworking.class.getDeclaredField("sender");
        field.setAccessible(true);
        sender = (java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload>) field.get(null);
        ClientNetworking.bindSender(payload -> {
            if (payload instanceof WatchReplayPayload watch) {
                requests.add(watch.step());
                ClientReplay.accept(new ReplayFramePayload(watch.id(), watch.step(), 10, board));
            }
        });
        ClientReplay.watch("render-fixture");
        watcher = TableScreen.watching();
        client.setScreen(watcher);
        ClientReplay.scrubTo(5);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkReplay(Minecraft client) throws ReflectiveOperationException {
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
        require(ClientReplay.step() == 0, "Home did not seek to the beginning");
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
        require(ClientReplay.step() == 1, "Right did not advance");
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0);
        require(ClientReplay.step() == 10, "End did not seek to the end");
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0, 0);
        require(ClientReplay.step() == 9, "Left did not step back");
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, 0, 0);
        require(ClientReplay.playing(), "Space did not play");
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, 0, 0);
        require(!ClientReplay.playing(), "Space did not pause");
        Method layout = TableScreen.class.getDeclaredMethod("replayStrip");
        layout.setAccessible(true);
        ReplayStrip strip = (ReplayStrip) layout.invoke(watcher);
        watcher.mouseClicked(strip.button(0).centerX(), strip.button(0).centerY(), 0);
        require(ClientReplay.step() == 0, "Start button did not seek");
        watcher.mouseClicked(strip.bar().centerX(), strip.bar().centerY(), 0);
        require(ClientReplay.step() == strip.stepUnder((int) strip.bar().centerX(), 10), "Bar click did not match its drawing");
        watcher.mouseDragged(strip.bar().right() + 100, strip.bar().y(), 0, 0, 0);
        require(ClientReplay.step() == 10, "Dragging past the bar did not clamp");
        watcher.mouseReleased(strip.bar().right() + 100, strip.bar().y(), 0);
        watcher.mouseDragged(strip.bar().x() - 100, strip.bar().y(), 0, 0, 0);
        require(ClientReplay.step() == 10, "Released scrubber still handled drags");
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(client.screen == watcher, "Escape left replay instead of closing help");
        watcher.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(client.screen != watcher, "Escape did not close replay");
        System.out.println("[screen-refactor] PASS replay keys, click, drag, release and modal close; requests=" + requests);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        String label = System.getProperty("gathering.screenRefactorScene");
        if (label == null || done) return;
        label = label.replaceAll("[^a-zA-Z0-9_-]", "_");
        Minecraft client = Minecraft.getInstance();
        if (label.equals("observe")) { observe(client); return; }
        if (client.level == null || client.player == null || client.getOverlay() != null) return;
        try {
            ticks++;
            if (ticks == 5) {
                autosaveIndicator = client.options.showAutosaveIndicator().get();
                client.options.showAutosaveIndicator().set(false);
                gallery = new Gallery(client);
                client.setScreen(gallery);
            }
            if (ticks == 9 || ticks == 13) {
                String name = "card-render-" + label.replaceAll("[^a-zA-Z0-9_-]", "_")
                        + (ticks == 9 ? "-wide.png" : "-narrow.png");
                net.minecraft.client.Screenshot.grab(client.gameDirectory, name,
                        client.getMainRenderTarget(), message -> { });
                System.out.println("[screen-refactor] captured " + name + "; draws=" + gallery.draws);
                if (ticks == 9) gallery.narrow = true;
                else {
                    if (gallery.draws < 32) throw new AssertionError("Card path was not rendered");
                    System.out.println("[screen-refactor] PASS real screen card rendering");
                    openReplay(client);
                }
            }
            if (ticks == 17) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "replay-render-" + label + ".png",
                        client.getMainRenderTarget(), message -> { });
                checkReplay(client);
                if (label.equals("refactored")) CardLabelBenchmark.run();
                done = true;
                if (sender != null) ClientNetworking.bindSender(sender);
                TutorialDemo.clear();
                client.options.showAutosaveIndicator().set(autosaveIndicator);
                client.stop();
            }
        } catch (Throwable failure) {
            done = true;
            failure.printStackTrace();
            if (autosaveIndicator != null) client.options.showAutosaveIndicator().set(autosaveIndicator);
            if (sender != null) ClientNetworking.bindSender(sender);
            System.out.println("[screen-refactor] FAIL " + failure);
            client.stop();
        }
    }

    private static final class Gallery extends Screen {
        private final TableScreen table;
        private final Method draw;
        private final List<CardView> cards;
        private boolean narrow;
        private int draws;

        Gallery(Minecraft client) throws ReflectiveOperationException {
            super(Component.literal("Card rendering characterization"));
            table = TableScreen.learning(null);
            table.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
            draw = TableScreen.class.getDeclaredMethod("drawCard", GuiGraphics.class, CardView.class,
                    Sleeve.class, Rect.class, int.class, boolean.class, boolean.class);
            draw.setAccessible(true);
            Map<String, Integer> counters = new LinkedHashMap<>();
            counters.put("+1/+1", 12);
            counters.put("quest", 1);
            counters.put("a deliberately long counter", 37);
            counters.put("loyalty", 4);
            cards = List.of(visible(0, Map.of(), null, false, false),
                    visible(1, counters, null, false, false),
                    visible(2, counters, "7/9", true, true),
                    new CardView.Anonymous(new MarkerId("fixture"), false, counters, null,
                            null, "Public note", "3/3", true),
                    visible(4, Map.of("loyalty", 4), null, false, false),
                    visible(5, Map.of("loyalty", 4), "2/2", false, false),
                    new CardView.Visible(new CardInstanceId(6), PaperStock.BLANK.identity(),
                            SeatId.of(0), Facing.FACE_DOWN, true, counters, null, false, null,
                            "Known but face down", false, null, false),
                    visible(7, Map.of("-1/-1", 2), null, true, false));
        }

        private static CardView visible(int id, Map<String, Integer> counters, String strength,
                boolean tapped, boolean frozen) {
            return new CardView.Visible(new CardInstanceId(id), PaperStock.BLANK.identity(),
                    SeatId.of(0), Facing.FACE_UP, tapped, counters, null, false, null,
                    "Sample card", false, strength, frozen);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF243A31);
            graphics.drawString(font, "Card rendering: " + (narrow ? "crowded" : "wide"), 8, 8, 0xFFFFFFFF);
            try {
                for (int i = 0; i < cards.size(); i++) {
                    int w = narrow ? 30 : 58;
                    Rect rect = new Rect(25 + (i % 4) * (width / 4),
                            35 + (i / 4) * ((height - 35) / 2), w, narrow ? 42 : 81);
                    draw.invoke(table, graphics, cards.get(i), Sleeve.BLUE, rect,
                            i == 2 ? 90 : i == 7 ? 23 : 0, i == 1, i != 4);
                    draws++;
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
