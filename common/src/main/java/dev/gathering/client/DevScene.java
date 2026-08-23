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
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.ui.HandFan;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.SurfaceBoard;
import dev.gathering.core.ui.TableScreenLayout;
import dev.gathering.core.ui.TableTop;
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

    /** Everything the run expected and did not get. Empty is the only passing answer. */
    private static final List<String> FAILURES = new ArrayList<>();

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
            fail("gave up waiting at step " + step);
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
        // Vanilla's "move with WASD" toast lands over the top-right corner of every picture
        // taken in the first two minutes of a fresh world, which is exactly where the zone
        // column is. Nothing to do with the mod, and it hides the thing being photographed.
        client.getToasts().clear();
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
                // Crouching at a bare table is the other way in: it asks what kind of game
                // this is going to be, which is the deliberate gesture for a table that wants
                // to be something other than the usual.
                askForAGame(client);
                advance(SETTLE * 2);
            }
            case 4 -> {
                expectScreen(client, "crouching at a bare table", TableSetupScreen.class);
                shoot(client, "02-what-kind-of-game");
                if (client.screen != null) {
                    press(client, "Modern");
                    press(client, "Best of 3");
                }
                advance(SETTLE / 2);
            }
            case 5 -> {
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
                boolean playing = table != null && ClientTableState.viewOf(table).isPresent();
                System.out.println("[devscene] one right-click later: board=" + playing);
                shoot(client, "03-one-click-in");
                if (playing) {
                    client.setScreen(new TableScreen(table));
                } else {
                    fail("one right-click with a deck did not start a game");
                }
                advance(SETTLE);
            }
            case 6 -> advance(0);
            case 7 -> {
                reportSeats(client);
                shoot(client, "04-seated-board");
                if (client.screen instanceof TableScreen) {
                    // Draw a hand, so there is something in it to photograph.
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_7, 0, 0);
                }
                advance(SETTLE);
            }
            case 8 -> {
                shoot(client, "05-with-a-hand");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 9 -> {
                shoot(client, "06-on-the-table");
                // Back to the seated screen for the rest, which is where the gestures are
                // easiest to aim without a camera in the way.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 10 -> {
                // Play a card: take the first one in hand and drop it on the near mat. The one
                // gesture the whole table is built around, and the one nothing has yet checked
                // end to end.
                playACard(client);
                advance(SETTLE);
            }
            case 11 -> {
                shoot(client, "07-card-played");
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 12 -> {
                if (client.screen instanceof TableScreen board && !board.isHoveringSomething()) {
                    fail("hovering the played card lit nothing; cursor at "
                            + client.mouseHandler.xpos() + "," + client.mouseHandler.ypos());
                }
                shoot(client, "08-hovering-a-card");
                // Holding the read key. Not by pressing it: the overlay asks the window for
                // the physical key, and a window under a headless X server has no focus and
                // so no key state - which is the whole reason the overlay takes its answer
                // from a supplier rather than reading the mapping itself.
                CardZoomOverlay.bindKeyState(() -> true);
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 13 -> {
                if (!CardZoomOverlay.isActive()) {
                    fail("the read-a-card overlay did not come up");
                }
                shoot(client, "09-reading-a-card");
                CardZoomOverlay.bindKeyState(() -> false);
                if (client.screen != null) {
                    int[] at = cardPoint(client);
                    client.screen.mouseClicked(at[0], at[1], 1);
                }
                advance(SETTLE / 2);
            }
            case 14 -> {
                if (!menuIsOpen(client)) {
                    fail("right-clicking a card on the table opened no menu");
                }
                shoot(client, "10-card-menu");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_L, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 15 -> {
                shoot(client, "11-log");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_L, 0, 0);
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 16 -> {
                shoot(client, "12-key-list");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 17 -> {
                // Into the graveyard: the drop that has to land on a zone rather than on felt.
                dropIntoAZone(client, 1);
                advance(SETTLE);
            }
            case 18 -> {
                shoot(client, "13-into-the-graveyard");
                // A crowded hand. Eighteen cards is a real Windfall turn and the size at which
                // a fan either overlaps sensibly or turns into a wall.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_9, 0, 0);
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_9, 0, 0);
                }
                advance(SETTLE);
            }
            case 19 -> {
                shoot(client, "14-crowded-hand");
                // The graveyard has a card in it by now, and left-clicking a pile that is not
                // a library opens it. Anything else here is a dead end the player would find.
                clickAZone(client, 1, 0);
                advance(SETTLE);
            }
            case 20 -> {
                expectScreen(client, "left-clicking the graveyard", PileScreen.class);
                shoot(client, "15-graveyard-open");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                advance(SETTLE);
            }
            case 21 -> {
                expectScreen(client, "closing the graveyard", TableScreen.class);
                // Right-click on the library, which is where every verb a library has lives.
                clickAZone(client, 0, 1);
                advance(SETTLE / 2);
            }
            case 22 -> {
                if (!menuIsOpen(client)) {
                    fail("right-clicking the library opened no menu");
                }
                shoot(client, "16-library-menu");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    // Back onto the block, now that there is a played card, a full graveyard
                    // and a crowded hand to look at rather than an empty table.
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                // Cursor off the board first, so the picture before the hover is a picture of
                // nothing being hovered. Two frames that both had the ring in them compared
                // equal, which read as the ring never being drawn at all.
                hover(client, new int[] {2, 2});
                advance(SETTLE);
            }
            case 23 -> {
                if (client.screen instanceof TableScreen board && board.isHoveringSomething()) {
                    fail("a cursor off the board still had a card under it");
                }
                shoot(client, "17-on-the-table-in-play");
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 24 -> {
                if (client.screen instanceof TableScreen board && !board.isHoveringSomething()) {
                    fail("hovering a card on the real table lit nothing");
                }
                if (!ClientTableHighlight.isLitAtAll()) {
                    fail("the table in the world was not told what the cursor was on");
                }
                shoot(client, "18-on-the-table-hovering");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 25 -> {
                // A window somebody has resized, which is the one path that re-runs a screen's
                // init on an instance that is already holding a game. Two sizes: one where
                // everything gets bigger and the felt gets smaller, and one the other way.
                setGuiScale(client, 3);
                advance(SETTLE / 2);
            }
            case 26 -> {
                theBoardIsStillFramed(client, "at gui scale three");
                shoot(client, "19-a-bigger-interface");
                setGuiScale(client, 1);
                advance(SETTLE / 2);
            }
            case 27 -> {
                theBoardIsStillFramed(client, "at gui scale one");
                shoot(client, "20-a-smaller-interface");
                setGuiScale(client, 0);
                advance(SETTLE / 2);
            }
            case 28 -> {
                theBoardIsStillFramed(client, "back at the automatic scale");
                shoot(client, "21-back-to-normal");
                advance(SETTLE / 2);
            }
            default -> finish(client, "done");
        }
    }

    /**
     * Changes the interface scale and lets the game deal with it.
     *
     * <p>Which is a resize as far as every screen is concerned - {@code init} runs again on the
     * instance that is already open, holding a game, a camera and whatever the player was
     * dragging. Zero is the automatic setting.
     */
    private static void setGuiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeDisplay();
    }

    /** After a resize the player's own board still has to be somewhere they can see it. */
    private static void theBoardIsStillFramed(Minecraft client, String when) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("the table screen did not survive being resized " + when);
            return;
        }
        SeatId seat = ClientTableState.seatAt(table).orElse(null);
        if (seat == null) {
            fail("the seat was lost by resizing " + when);
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        TableScreenLayout layout = TableScreenLayout.of(width, height);
        Rect mat = board.board().matRect(seat);
        if (mat.isEmpty()) {
            fail("the player's own mat vanished " + when);
            return;
        }
        if (mat.right() <= 0 || mat.x() >= width
                || mat.bottom() <= layout.status().bottom() || mat.y() >= layout.hand().y()) {
            fail("the player's own mat was off screen " + when + ": " + mat
                    + " in " + width + "x" + height);
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

    /**
     * Crouches at the table, which is the gesture that asks what kind of game this is.
     *
     * <p>Through the block rather than by opening the screen directly: the screen arrives as a
     * packet, and a harness that skipped the packet would go on passing after the day somebody
     * broke the way it is asked for.
     */
    private static void askForAGame(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (player != null) {
                TableBlock.startGameFor(server.overworld(), where, player);
            }
        });
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
     * Where on the screen the played card should have landed.
     *
     * <p>The near mat's middle. Aimed at the layout rather than at a remembered pixel, so it
     * keeps pointing at the card when the layout changes - which it has, twice.
     */
    private static int[] cardPoint(Minecraft client) {
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int[] middleOfTheMat = {width / 2, height / 4};
        if (!(client.screen instanceof TableScreen board)
                || !(board.board() instanceof SurfaceBoard)) {
            return middleOfTheMat;
        }
        // On the block there is no screen rectangle to aim at: the board is drawn by the world
        // and the only way back from a card to a pixel is the way the pointer goes forwards.
        // So sweep the window, ask the pointer what each point is over, and keep the one that
        // lands nearest the card. Two thousand rays once a run, which is nothing, and it
        // exercises the same pick the player's cursor uses rather than a copy of it.
        double[] wanted = playedCardSpot(client);
        if (wanted == null || table == null) {
            return middleOfTheMat;
        }
        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        int[] best = middleOfTheMat;
        double nearest = Double.MAX_VALUE;
        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                TableTop.Spot spot = TablePointer.at(top, x, y).orElse(null);
                if (spot == null) {
                    continue;
                }
                double away = Math.hypot(spot.x() - wanted[0], spot.y() - wanted[1]);
                if (away < nearest) {
                    nearest = away;
                    best = new int[] {x, y};
                }
            }
        }
        return best;
    }

    /** Where the first card on this player's own mat is, in surface units, or null. */
    private static double[] playedCardSpot(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            return null;
        }
        SeatId seat = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (seat == null || view == null) {
            return null;
        }
        for (CardView card : view.seat(seat).zone(Zone.BATTLEFIELD).cards()) {
            TablePosition at = card.placedAt().orElse(null);
            if (at != null) {
                Rect where = board.board().rectOf(seat, at);
                return new double[] {where.centreX(), where.centreY()};
            }
        }
        return null;
    }

    /** Drags the first card in hand onto the near mat, press and release, like a player. */
    private static void playACard(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            System.out.println("[devscene] no board to play onto");
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        TableScreenLayout layout = TableScreenLayout.of(width, height);
        HandFan.Slot first = HandFan.slot(layout.hand(), 7, 0, -1);
        int[] onto = cardPoint(client);

        board.mouseClicked(first.where().centreX(), first.where().centreY(), 0);
        board.mouseDragged(onto[0], onto[1], 0,
                onto[0] - first.where().centreX(), onto[1] - first.where().centreY());
        board.mouseReleased(onto[0], onto[1], 0);
        System.out.println("[devscene] dragged a card from the hand onto the table");
    }

    /** Where one of this player's zones is on screen, asked of the screen that drew it. */
    private static Rect zoneRect(Minecraft client, int index) {
        if (!(client.screen instanceof TableScreen board)) {
            return Rect.NONE;
        }
        return ClientTableState.seatAt(table)
                .map(seat -> board.board().pileRect(seat, index, Zone.PILES.size()))
                .orElse(Rect.NONE);
    }

    /** Drags the first card in hand onto one of the zones, which is how cards go there now. */
    private static void dropIntoAZone(Minecraft client, int index) {
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty() || !(client.screen instanceof TableScreen board)) {
            System.out.println("[devscene] no zone " + index + " to drop into");
            return;
        }
        Rect from = HandFan.slot(
                TableScreenLayout.of(client.getWindow().getGuiScaledWidth(),
                        client.getWindow().getGuiScaledHeight()).hand(),
                6, 0, -1).where();
        board.mouseClicked(from.centreX(), from.centreY(), 0);
        board.mouseDragged(zone.centreX(), zone.centreY(), 0,
                zone.centreX() - from.centreX(), zone.centreY() - from.centreY());
        board.mouseReleased(zone.centreX(), zone.centreY(), 0);
        System.out.println("[devscene] dropped a card into zone " + index);
    }

    /** Clicks a zone with the given button, which should open what is in it. */
    private static void clickAZone(Minecraft client, int index, int button) {
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty() || client.screen == null) {
            System.out.println("[devscene] no zone " + index + " to click");
            return;
        }
        client.screen.mouseClicked(zone.centreX(), zone.centreY(), button);
        client.screen.mouseReleased(zone.centreX(), zone.centreY(), button);
    }

    /**
     * What screen the last gesture left us on, and whether that is where it should have gone.
     *
     * <p>A scripted run that only narrates is a run somebody has to read. Saying what was
     * expected turns each step into something that can fail on its own, so a flow that stops
     * working stops the build rather than quietly producing one duller picture.
     */
    private static void expectScreen(Minecraft client, String what, Class<?> wanted) {
        String got = client.screen == null ? "none" : client.screen.getClass().getSimpleName();
        String want = wanted == null ? "none" : wanted.getSimpleName();
        if (got.equals(want)) {
            System.out.println("[devscene] after " + what + ": " + got);
        } else {
            fail("after " + what + ": expected " + want + " but got " + got);
        }
    }

    /** Something the run was supposed to prove and did not. Collected, not thrown. */
    private static void fail(String what) {
        FAILURES.add(what);
        System.out.println("[devscene] FAIL " + what);
    }

    /** Whether the table has a context menu up, which is how a right-click shows it worked. */
    private static boolean menuIsOpen(Minecraft client) {
        return client.screen instanceof TableScreen board && board.menuIsOpen();
    }

    /**
     * Puts the cursor somewhere without clicking, so a frame is drawn with it hovered.
     *
     * <p>The real cursor, not just a call to {@code mouseMoved}. Every frame a screen draws is
     * handed the pointer's actual position, and the board on the block works out what is
     * under it from that, so a harness that only tells the screen it moved photographs a
     * board with the cursor still parked in the middle of the window.
     *
     * <p>Moving it takes two goes. {@code glfwSetCursorPos} is documented to do nothing for a
     * window without input focus, and a window under a headless X server never has any - so
     * the ask goes in first, for the case where this is being watched on a real desktop, and
     * then the position is written where the game reads it from. Reflection into Minecraft,
     * in a class that only ever runs behind {@code -Dgathering.devscene=1}: nothing ships
     * that depends on it, and the alternative is production code carrying a way to lie about
     * where the mouse is.
     */
    private static void hover(Minecraft client, int[] at) {
        double x = at[0] * client.getWindow().getScreenWidth()
                / (double) Math.max(1, client.getWindow().getGuiScaledWidth());
        double y = at[1] * client.getWindow().getScreenHeight()
                / (double) Math.max(1, client.getWindow().getGuiScaledHeight());
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(), x, y);
        try {
            set(client.mouseHandler, "xpos", x);
            set(client.mouseHandler, "ypos", y);
        } catch (ReflectiveOperationException e) {
            fail("could not move the cursor: " + e);
        }
        if (client.screen != null) {
            client.screen.mouseMoved(at[0], at[1]);
        }
    }

    private static void set(Object target, String field, double value)
            throws ReflectiveOperationException {
        java.lang.reflect.Field found = target.getClass().getDeclaredField(field);
        found.setAccessible(true);
        found.setDouble(target, value);
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
        if (server == null || table == null) {
            return;
        }
        // The guard comes first: ClientTableState keys a ConcurrentHashMap by the table, and
        // that throws on a null key rather than answering empty - so a run where the table was
        // never placed used to fall out of the tick with a bare NPE instead of a report.
        System.out.println("[devscene] client seat: " + ClientTableState.seatAt(table));
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
        fail("no button labelled " + label + " on "
                + client.screen.getClass().getSimpleName());
    }

    private static void shoot(Minecraft client, String name) {
        Screenshot.grab(
                client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> { });
        TAKEN.add(name);
    }

    private static void finish(Minecraft client, String why) {
        System.out.println("[devscene] " + why + "; took " + TAKEN);
        for (String failure : FAILURES) {
            System.out.println("[devscene] FAIL " + failure);
        }
        System.out.println("[devscene] failures: " + FAILURES.size());
        new File(client.gameDirectory, "screenshots").mkdirs();
        client.stop();
    }
}
