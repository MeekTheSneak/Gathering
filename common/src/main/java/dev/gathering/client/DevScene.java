package dev.gathering.client;

import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.item.GatheringContent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Drives the client through a scripted session and takes pictures of it.
 *
 * <p>The reason this exists: everything about how the table <em>looks and feels</em> was being
 * checked by somebody opening the game and describing what was wrong. That works, and it is a
 * slow, lossy channel for the kind of problem where a zone is the wrong shape or a board is
 * upside down - the sort of thing that takes one glance and a paragraph to explain.
 *
 * <p>So: boot the client with {@code -Dgathering.devscene=1}, and it makes a flat world, sets a
 * table up, starts a game, opens the board, photographs it in both views, and quits. The
 * pictures land in {@code run/screenshots} and can be looked at by anybody - or anything -
 * that can open a PNG.
 *
 * <p>Off unless the property is set, and never referenced by anything that ships. It is a
 * workbench, not a feature.
 *
 * <p>Client-only.
 */
public final class DevScene {

    /** Set {@code -Dgathering.devscene=1} to arm it. Absent everywhere else. */
    private static final String ENABLED = "gathering.devscene";

    private static final String LEVEL = "GatheringDevScene";

    /** Ticks to wait between steps, so the game has settled before it is photographed. */
    private static final int SETTLE = 40;

    /** A hard stop, so a scene that never gets going does not sit there until the timer kills it. */
    private static final int GIVE_UP_TICKS = 20 * 60 * 2;

    private static BlockPos table;
    private static int ticks;
    private static int step;
    private static int waited;
    private static final List<String> TAKEN = new ArrayList<>();

    private DevScene() {
    }

    public static boolean isEnabled() {
        return System.getProperty(ENABLED) != null;
    }

    /**
     * One tick of the script.
     *
     * <p>A state machine rather than a sequence of sleeps, because everything here is waiting
     * for the game to reach a state - the title screen to appear, a world to finish loading -
     * and a fixed delay for that is a test that passes on a fast machine and hangs on a slow
     * one.
     */
    public static void tick(Minecraft client) {
        if (!isEnabled() || client == null) {
            return;
        }
        if (++ticks > GIVE_UP_TICKS) {
            finish(client, "gave up waiting at step " + step);
            return;
        }
        if (waited > 0) {
            waited--;
            return;
        }
        switch (step) {
            case 0 -> {
                // Not "wait for the title screen": a client that has never been run before
                // opens on the accessibility onboarding instead, and waiting for a screen that
                // never comes is a script that sits there until its timer kills it. Wait for
                // the loading overlay to clear, say what turned up, and put the title screen
                // there ourselves.
                if (client.getOverlay() == null && client.screen != null) {
                    System.out.println("[devscene] first screen: " + client.screen.getClass().getName());
                    client.setScreen(new TitleScreen());
                    advance(SETTLE);
                }
            }
            case 1 -> {
                shoot(client, "00-title");
                makeAWorld(client);
                advance(SETTLE * 4);
            }
            case 2 -> {
                if (client.level != null && client.player != null) {
                    shoot(client, "01-in-world");
                    setATableUp(client);
                    advance(SETTLE * 2);
                }
            }
            case 3 -> {
                // Photograph whatever the table actually did, rather than waiting for what it
                // was assumed to do. A script that hangs until its timer runs out tells you
                // nothing; a picture of the screen that came up tells you everything.
                System.out.println("[devscene] after sitting down: screen="
                        + (client.screen == null ? "none" : client.screen.getClass().getSimpleName())
                        + " board=" + (table != null && ClientTableState.viewOf(table).isPresent()));
                shoot(client, "02-sat-down");
                if (table != null && ClientTableState.viewOf(table).isPresent()) {
                    client.setScreen(new TableScreen(table));
                } else {
                    press(client, "Start");
                }
                advance(SETTLE * 3);
            }
            case 4 -> {
                if (table != null && ClientTableState.viewOf(table).isPresent()
                        && !(client.screen instanceof TableScreen)) {
                    client.setScreen(new TableScreen(table));
                    advance(SETTLE);
                    return;
                }
                shoot(client, "02-seated-screen");
                // The key that swaps to playing on the block itself.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 5 -> {
                shoot(client, "03-on-the-table");
                advance(SETTLE / 2);
            }
            default -> finish(client, "done");
        }
    }

    private static void advance(int settle) {
        step++;
        waited = settle;
    }

    /**
     * Puts a table down in front of the player and starts a game on it.
     *
     * <p>On the server's own thread. The client and the integrated server share a process and
     * not a thread, and blocks placed from the wrong one are a race that shows up as a table
     * with a corner missing about one run in five.
     */
    private static void setATableUp(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        BlockPos where = client.player.blockPosition().offset(2, -1, 2);
        table = where;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            BlockState state = GatheringContent.TABLE.get().defaultBlockState();
            for (TablePart part : TablePart.values()) {
                level.setBlock(part.offsetFrom(where), state.setValue(TableBlock.PART, part), 3);
            }
            server.getPlayerList().getPlayers().stream().findFirst()
                    .ifPresent(player -> TableBlock.startGameFor(level, where, player));
        });
    }

    /** A flat, bright, empty world in creative: nothing to look at but the table. */
    private static void makeAWorld(Minecraft client) {
        LevelSettings settings = new LevelSettings(
                LEVEL, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(
                LEVEL,
                settings,
                new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                null);
    }

    /**
     * Clicks the button with this label, wherever the layout has put it.
     *
     * <p>By label rather than by coordinates: a harness that clicks a fixed spot on the screen
     * stops working the first time somebody moves a button, and does it silently - it goes on
     * taking pictures of a screen nothing was pressed on.
     */
    private static void press(Minecraft client, String label) {
        if (client.screen == null) {
            return;
        }
        for (GuiEventListener child : client.screen.children()) {
            if (child instanceof AbstractWidget widget
                    && widget.getMessage().getString().equalsIgnoreCase(label)) {
                widget.onClick(widget.getX() + widget.getWidth() / 2.0,
                        widget.getY() + widget.getHeight() / 2.0);
                System.out.println("[devscene] pressed " + label);
                return;
            }
        }
        System.out.println("[devscene] no button labelled " + label + " on "
                + client.screen.getClass().getSimpleName());
    }

    private static void shoot(Minecraft client, String name) {
        Screenshot.grab(
                client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> { });
        TAKEN.add(name);
    }

    private static void finish(Minecraft client, String why) {
        System.out.println("[devscene] " + why + "; took " + TAKEN);
        new File(client.gameDirectory, "screenshots").mkdirs();
        client.stop();
    }
}
