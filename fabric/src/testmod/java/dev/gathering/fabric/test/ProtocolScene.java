package dev.gathering.fabric.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Two real clients and the protocol check between them, in development runs only.
 * <p>{@code ./gradlew :fabric:runProtocolHost} opens a world to LAN on port 25599;
 * {@code ./gradlew :fabric:runProtocolJoin -PpretendProtocol=11} joins it answering with another
 * protocol number. The host's log says whether the joiner was let in or turned away, and why. A LAN
 * world rather than a dedicated server so nobody's EULA is accepted for them.
 */
public final class ProtocolScene implements ClientModInitializer {

    static final int PORT = 25599;
    private static final String ROLE = System.getProperty("gathering.protocolscene", "");
    private static int ticks;
    private static boolean started;

    @Override
    public void onInitializeClient() {
        if (!ROLE.isEmpty()) {
            ClientTickEvents.END_CLIENT_TICK.register(ProtocolScene::tick);
        }
    }

    private static void tick(Minecraft client) {
        ticks++;
        // A fresh run directory opens on the accessibility onboarding, not the title screen.
        if (!started && client.getOverlay() == null && client.screen != null && !(client.screen instanceof TitleScreen)) {
            client.options.onboardAccessibility = false;
            client.setScreen(new TitleScreen());
            return;
        }
        if (ROLE.equals("host")) {
            host(client);
        } else if (ROLE.equals("join")) {
            join(client);
        }
        if (ticks > 20 * 240) {
            System.out.println("[protocolscene] " + ROLE + " done");
            client.stop();
        }
    }

    private static void host(Minecraft client) {
        if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            started = true;
            LevelSettings settings = new LevelSettings("GatheringProtocolHost", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                    true, new GameRules(), WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("GatheringProtocolHost", settings, new WorldOptions(1L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), null);
            return;
        }
        var server = client.getSingleplayerServer();
        if (started && server != null && client.player != null && !server.isPublished()) {
            // Offline development accounts on both ends: nothing to authenticate against.
            server.setUsesAuthentication(false);
            boolean published = server.publishServer(GameType.CREATIVE, false, PORT);
            System.out.println("[protocolscene] host published on " + PORT + ": " + published);
        }
        if (server != null && server.isPublished() && ticks % 100 == 0) {
            System.out.println("[protocolscene] host sees players " + server.getPlayerList().getPlayerNamesArray().length);
        }
    }

    private static void join(Minecraft client) {
        // Long enough for the host to have made its world and opened it.
        if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null && ticks > 20 * 60) {
            started = true;
            System.out.println("[protocolscene] joining localhost:" + PORT);
            ConnectScreen.startConnecting(client.screen, client, ServerAddress.parseString("localhost:" + PORT),
                    new ServerData("host", "localhost:" + PORT, ServerData.Type.LAN), false, null);
        }
        if (started && ticks % 40 == 0) {
            String where = client.screen instanceof DisconnectedScreen disconnected
                    ? "turned away: " + disconnected.getTitle().getString()
                    : client.player != null ? "playing" : String.valueOf(client.screen);
            System.out.println("[protocolscene] joiner is " + where);
        }
    }
}
