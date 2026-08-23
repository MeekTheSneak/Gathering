package dev.gathering.client;

import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.block.TableSeats;
import dev.gathering.server.DecklistImport;
import dev.gathering.server.TableBroadcast;
import dev.gathering.service.CardDataService;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

    /**
     * A deck small enough to import quickly and varied enough to look at.
     *
     * <p>Real cards, fetched from Scryfall like any other deck, because the whole point of
     * photographing the client is to see what a player sees - and a board of grey rectangles
     * would prove only that grey rectangles are laid out correctly.
     */
    private static final String DECK = String.join("\n",
            "4 Llanowar Elves",
            "4 Grizzly Bears",
            "4 Giant Growth",
            "4 Lightning Bolt",
            "4 Counterspell",
            "4 Forest",
            "4 Mountain",
            "4 Island");

    /** Ticks to wait between steps, so the game has settled before it is photographed. */
    private static final int SETTLE = 40;

    /** A hard stop, so a scene that never gets going does not sit there until the timer kills it. */
    private static final int GIVE_UP_TICKS = 20 * 60 * 2;

    private static BlockPos table;
    private static boolean asked;
    private static boolean committed;
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
        // Where a seat goes missing, rather than only that it has. The claim is taken when the
        // player sits - the chat line says so - and gone by the time the board is drawn, so
        // what matters is which step in between drops it.
        watchTheSeat(client);
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
                // What a player does now: get a deck, walk up to the table, right-click it.
                // No sitting, no crouching, no format screen. If that stops being enough the
                // pictures will show a table with nothing on it.
                if (!asked) {
                    asked = true;
                    if (client.screen != null) {
                        client.setScreen(null);
                    }
                    importADeck(client);
                    waited = SETTLE * 8;
                    return;
                }
                if (!committed) {
                    committed = true;
                    putTheDeckDown(client);
                    waited = SETTLE * 4;
                    return;
                }
                System.out.println("[devscene] one right-click later: board="
                        + (table != null && ClientTableState.viewOf(table).isPresent()));
                shoot(client, "02-one-click-in");
                if (table != null && ClientTableState.viewOf(table).isPresent()) {
                    client.setScreen(new TableScreen(table));
                }
                advance(SETTLE);
            }
            case 4 -> advance(0);
            case 5 -> {
                reportSeats(client);
                shoot(client, "03-seated-board");
                if (client.screen instanceof TableScreen) {
                    // Draw a hand, so there is something in it to photograph.
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_7, 0, 0);
                }
                advance(SETTLE);
            }
            case 6 -> {
                shoot(client, "04-with-a-hand");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 7 -> {
                shoot(client, "05-on-the-table");
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
            // Deliberately not seated or started here. The whole point is that walking up
            // holding a deck is enough, so the scene has to actually walk up holding a deck.
            System.out.println("[devscene] table placed, nobody seated");
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

    private static String lastSeat = "?";

    private static void watchTheSeat(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            return;
        }
        BlockPos where = table;
        int at = step;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (player == null) {
                return;
            }
            String now = TableSeats.seatOf(server.overworld(), where, player.getUUID()).toString();
            if (!now.equals(lastSeat)) {
                System.out.println("[devscene] seat changed at step " + at + ": " + lastSeat + " -> " + now);
                lastSeat = now;
            }
        });
    }

    /**
     * Says what the server thinks about who is sitting where, and what the client thinks.
     *
     * <p>Because they disagree, and a picture of the disagreement does not say which side is
     * wrong. The board draws the right name against seat nought while the client believes it
     * is watching, so one of the two stores that answer "is this my seat" is not being read.
     */
    private static void reportSeats(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        System.out.println("[devscene] client seat: " + ClientTableState.seatAt(table));
        if (server == null || table == null) {
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            System.out.println("[devscene] server claim: "
                    + (player == null ? "no player" : TableSeats.seatOf(level, where, player.getUUID())));
            System.out.println("[devscene] server sees seated: "
                    + TableBroadcast.seatedAt(level, where).size());
        });
    }

    /** Imports a real deck the way the import command does, on the server's thread. */
    private static void importADeck(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            CardDataService service = CardDataService.active().orElse(null);
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (service == null || player == null) {
                System.out.println("[devscene] no card pipeline; the board will be empty");
                return;
            }
            DecklistImport.importFor(player, service, DECK);
            System.out.println("[devscene] importing a deck");
        });
    }

    /**
     * Right-clicks the table with the deck, exactly as a player would.
     *
     * <p>Through the real interaction rather than by calling whatever the table does with a
     * deck: a harness that reaches past the path players take stops testing that path, and the
     * path players take is the one that breaks.
     */
    private static void putTheDeckDown(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (player == null) {
                return;
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (DeckItem.deckOf(stack).isEmpty()) {
                    continue;
                }
                player.getInventory().selected = Math.min(slot, 8);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(where.above()), Direction.UP, where, false);
                player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
                System.out.println("[devscene] put a deck down");
                return;
            }
            System.out.println("[devscene] no deck arrived to put down");
        });
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
