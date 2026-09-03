package dev.gathering.client;

import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Screenshot;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.block.TableSeats;
import dev.gathering.server.DecklistImport;
import dev.gathering.server.TableBroadcast;
import dev.gathering.service.CardDataService;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.CommandSlots;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.ui.HandFan;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.TableSurface;
import dev.gathering.core.ui.TableVerb;
import dev.gathering.core.ui.SurfaceBoard;
import dev.gathering.core.ui.TableScreenLayout;
import dev.gathering.core.ui.TableTop;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
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
 * <p>The reason this exists: everything about how the table <em>looks and feels</em> was being
 * checked by somebody opening the game and describing what was wrong. That works, and it is a
 * slow, lossy channel for the kind of problem where a zone is the wrong shape or a board is
 * upside down - the sort of thing that takes one glance and a paragraph to explain.
 * <p>So: boot the client with {@code -Dgathering.devscene=1}, and it makes a flat world, sets a
 * table up, starts a game, opens the board, photographs it in both views, and quits. The
 * pictures land in {@code run/screenshots} and can be looked at by anybody - or anything -
 * that can open a PNG.
 * <p>Off unless the property is set, and never referenced by anything that ships. It is a
 * workbench, not a feature.
 * <p>Client-only.
 */
public final class DevScene {

    /** Set {@code -Dgathering.devscene=1} to arm it. Absent everywhere else. */
    private static final String ENABLED = "gathering.devscene";

    /**
     * Set {@code -Dgathering.gallery=1} as well to photograph every look after the tour.
     * <p>Its own switch because it is a different job. The tour is a check - it presses every
     * gesture and fails when one is a dead end - and it has to stay quick enough to run on
     * every change. The gallery presses nothing: it wears each look in turn and takes the
     * same three pictures, which is a thing somebody asks for when they are choosing between
     * them and never something to run in a gate.
     */
    private static final boolean GALLERY = System.getProperty("gathering.gallery") != null;

    private static final String LEVEL = "GatheringDevScene";

    /**
     * A card whose printing names a token, and the token it names.
     * <p>Both sides of the pair are here rather than only the card, because the row under
     * test is labeled with the token's name: reading it back off the card's metadata would
     * check the menu against itself.
     */
    private static final String MAKES_A_TOKEN = "Krenko, Mob Boss";

    /** What {@link #MAKES_A_TOKEN} prints, and so what its row has to be called. */
    private static final String THE_TOKEN_IT_MAKES = "Goblin";

    /**
     * A deck small enough to import quickly and varied enough to look at.
     * <p>Real cards, fetched from Scryfall like any other deck, because the whole point of
     * photographing the client is to see what a player sees - and a board of gray rectangles
     * would prove only that gray rectangles are laid out correctly.
     */
    private static final String DECK = String.join("\n",
            // Two commanders, because the table's one-click path starts a game of Commander
            // and a run whose command zone is empty photographs a format nobody is playing -
            // and never touches anything that only exists once somebody has a commander.
            // Two rather than one so both command slots are exercised: a partner pair is the
            // whole reason there are two, and a run that only ever fills the first would
            // leave the second drawn, empty and untested in every picture.
            "Commander",
            "1 Sakura-Tribe Elder",
            "1 Llanowar Elves",
            "",
            "Deck",
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

    /**
     * Long enough for a board to arrive and not long enough for a card to finish crossing.
     * <p>The only way to photograph a card in the air is to look while it is in the air.
     */
    private static final int A_MOMENT = 3;

    /**
     * How long one step may sit there before the run is declared stuck.
     * <p>Per step rather than for the whole run. A budget for the whole run is a number that
     * has to be raised every time the scene grows a step, and the run that finally exceeds it
     * fails with "gave up waiting" rather than with whatever was actually wrong - which is
     * exactly what happened the first time this scene passed sixty steps. What is being
     * watched for is a scene that has stopped moving, and that is a step that has not
     * changed, however long the run before it took.
     * <p>Forty seconds, against a longest legitimate wait of two.
     */
    private static final int STUCK_TICKS = 20 * 40;

    private static BlockPos table;
    private static boolean asked;
    private static boolean committed;
    private static int ticks;
    /**
     * The last step the scene has a case for.
     * <p>Kept next to the dispatcher so a skipped case number fails loudly. Java reads a hole
     * in a switch as the default branch, and the default branch here is "the scene finished" -
     * so a scene that lost step 31 to a renumbering reported a clean run of a third of the mod.
     * Raise this when the last case number goes up.
     */
    private static final int LAST_STEP = 279;

    /** How many notches of wheel the gallery pulls the board out by, and puts it back by. */
    private static final int GALLERY_ZOOM_OUT = 6;

    /**
     * The gallery, if it was asked for: five pictures of every look installed.
     * <p>Two steps a screen, because a screen has to be opened in one step and photographed
     * in the next - a shot asked for in the same breath catches the frame that was already
     * drawn, which is the convention the whole scene follows. The board takes four of them:
     * walk to it, start a game on it if the tour finished the last one, open it, photograph
     * it - and then two more for the same board zoomed out.
     */
    private static final int GALLERY_FIRST = LAST_STEP + 1;
    private static final int STEPS_PER_LOOK = 12;

    /** The last step this run will reach, which is further when the gallery is on. */
    private static int lastStep() {
        return GALLERY
                ? GALLERY_FIRST + GuiThemes.all().size() * STEPS_PER_LOOK - 1
                : LAST_STEP;
    }

    private static int step;
    private static int waited;
    private static final List<String> TAKEN = new ArrayList<>();

    /** Everything the run expected and did not get. Empty is the only passing answer. */
    private static final List<String> FAILURES = new ArrayList<>();

    /** How full the graveyard was before a card was dragged back out of it. */
    private static int inTheGraveyard;

    /** What the hand held before a mat button was pressed. */
    private static int inTheHand;

    /** Casts recorded on this player's commander before the button was pressed. */
    private static int lifeWas;

    private static int drawnBack;

    /** The cards taken out of the hand, so exactly those can go back. */
    private static java.util.List<CardInstanceId> emptied;
    private static int taxPaid;

    /** Where in the turn the table was before the marker was stepped on. */
    private static int wasOnTurn;

    /** Where the turn marker was before the undo was asked for. */
    private static int beforeTheUndo;

    /** What the graveyard held before the verb key was pressed at a card. */
    private static int beforeTheKey;

    /** And how much commander damage had been taken before the button was pressed. */
    private static int tookCommanderDamage;

    /** How many cards the library held before a scry, which a scry must not change. */
    private static int onTopBefore;

    private DevScene() {
    }

    public static boolean isEnabled() {
        return System.getProperty(ENABLED) != null;
    }

    /**
     * One tick of the script.
     * <p>A state machine rather than a sequence of sleeps, because everything here is waiting
     * for the game to reach a state - the title screen to appear, a world to finish loading -
     * and a fixed delay for that is a test that passes on a fast machine and hangs on a slow
     * one.
     */
    public static void tick(Minecraft client) {
        if (!isEnabled() || client == null) {
            return;
        }
        if (++ticks > STUCK_TICKS) {
            fail("step " + step + " stopped moving");
            finish(client, "step " + step + " stopped moving");
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
                // Free play is the shorter answer to "what kind of game", and for a while
                // this screen could not give it: every game it started was one somebody
                // would be held to, and a game with no format meant closing the screen and
                // finding the walk-up path nothing here mentions.
                press(client, "Free play");
                advance(A_MOMENT);
            }
            case 5 -> {
                theSetupScreenSays(client, "Free play");
                press(client, "Modern");
                advance(A_MOMENT);
            }
            case 6 -> {
                // Choosing a format has to undo free play, or the screen would be showing one
                // thing and about to start another.
                theSetupScreenSays(client, "Modern");
                press(client, "Best of 3");
                advance(A_MOMENT);
            }
            case 7 -> {
                theSetupScreenSays(client, "best of 3");
                System.out.println("[devscene] the setup screen says what it will start");
                advance(SETTLE / 2);
            }
            case 8 -> {
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
            case 9 -> advance(0);
            case 10 -> {
                reportSeats(client);
                shoot(client, "04-seated-board");
                // Draw a hand, so there is something in it to photograph.
                drawCards(client, 7);
                advance(SETTLE);
            }
            case 11 -> {
                shoot(client, "05-with-a-hand");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 12 -> {
                shoot(client, "06-on-the-table");
                // Back to the seated screen for the rest, which is where the gestures are
                // easiest to aim without a camera in the way.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                // Rest the cursor on a mat button, so the next step can read what it says.
                hoverAVerbButton(client, TableVerb.DRAW);
                advance(SETTLE);
            }
            case 13 -> {
                // A button has to say what it is. The word printed on it goes when the board
                // is drawn too small to write on, and a box that says nothing at all in that
                // case is a box nobody can learn, so the tooltip is the thing that must
                // always be there.
                aButtonSaysWhatItDoes(client, TableVerb.DRAW, "2");
                shoot(client, "06a-a-button-says-what-it-does");
                // Drawn boxes that do nothing when pressed would be worse than no boxes at
                // all, so one of them is pressed for real.
                inTheHand = countIn(Zone.HAND);
                pressAVerbButton(client, TableVerb.DRAW);
                // A short wait, because what the next step is looking for is a card halfway
                // between the library and the hand - and it is only there for a moment.
                advance(A_MOMENT);
            }
            case 14 -> {
                aCardIsInTheAir(client, "drawing one");
                shoot(client, "06b-a-card-in-the-air");
                // A shuffle is the one move that changes nothing anybody may look at, so it
                // has nothing to show unless it is given something. The pile rattles.
                pressAVerbButton(client, TableVerb.SHUFFLE);
                advance(A_MOMENT);
            }
            case 15 -> {
                aPileIsBeingShaken(client);
                shoot(client, "06c-a-library-being-shuffled");
                advance(SETTLE);
            }
            case 16 -> {
                int now = countIn(Zone.HAND);
                if (now <= inTheHand) {
                    fail("the draw button on the mat drew nothing: "
                            + inTheHand + " to " + now);
                }
                advance(0);
            }
            case 17 -> {
                // The shared turn marker. Nothing enforces it, which is exactly why handing
                // it on has to work: it is the whole of the turn structure now that the phase
                // is gone.
                wasOnTurn = turnNow();
                passTheTurn(client);
                advance(SETTLE);
            }
            case 18 -> {
                if (turnNow() <= wasOnTurn) {
                    fail("the turn was passed and the marker is still on turn " + wasOnTurn);
                }
                advance(0);
            }
            case 19 -> {
                // Taking a move back. Every misclick in a game played by dragging cards about
                // is permanent without this, and all of the machinery for it existed with
                // nothing able to ask for it.
                beforeTheUndo = turnNow();
                undoTheLastThing(client);
                advance(SETTLE);
            }
            case 20 -> {
                if (turnNow() >= beforeTheUndo) {
                    fail("undoing the pass changed nothing: still on turn " + beforeTheUndo);
                }
                advance(0);
            }
            case 21 -> {
                // Play a card: take the first one in hand and drop it on the near mat. The one
                // gesture the whole table is built around, and the one nothing has yet checked
                // end to end.
                playACard(client);
                advance(SETTLE);
            }
            case 22 -> {
                shoot(client, "07-card-played");
                clickingDoesNotTap(client);
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 23 -> {
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
            case 24 -> {
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
            case 25 -> {
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
            case 26 -> {
                shoot(client, "11-log");
                // Escape shuts the log and leaves the player at the table. It used to shut
                // the table: the log was open on top of the felt and the key that closes
                // every other panel in the game walked straight past it.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                expectScreen(client, "pressing escape on the game log", TableScreen.class);
                if (client.screen instanceof TableScreen board && board.theLogIsShowing()) {
                    fail("escape left the game log open");
                    return;
                }
                System.out.println("[devscene] escape shuts the log without leaving the table");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 27 -> {
                shoot(client, "12-key-list");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 28 -> {
                // Held over the graveyard without letting go, because the question a player
                // is asking mid-drag is "will it land in this one or the one next to it" -
                // and the only honest way to find out whether the board answers is to
                // photograph the moment the question is being asked.
                startDraggingOntoAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                advance(SETTLE / 2);
            }
            case 29 -> {
                // Put the cursor back where the drag aimed it, a frame later. Moving it twice
                // inside one tick does not stick: glfwSetCursorPos is asynchronous, and the
                // callback from the first move arrives after the second has been written
                // straight into the mouse handler and puts the old position back. So the aim
                // reported was the spot the card was picked up from - a true answer to the
                // wrong question, and the reason this was read for years as the board
                // refusing to aim at anything.
                if (aimedAt != null) {
                    hover(client, aimedAt);
                }
                advance(SETTLE / 4);
            }
            case 30 -> {
                theBoardWorksOutWhatIsUnderTheCard(client);
                advance(SETTLE / 4);
            }
            case 31 -> {
                shoot(client, "12a-aiming-at-a-zone");
                dropWhereItIsAimed(client);
                advance(SETTLE);
            }
            case 32 -> {
                shoot(client, "13-into-the-graveyard");
                inTheGraveyard = countIn(Zone.GRAVEYARD);
                // And back out again. A zone that only swallows cards is half a zone: on a
                // real table, getting something back is picking it up.
                dragOutOfAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                // A crowded hand. Eighteen cards is a real Windfall turn and the size at which
                // a fan either overlaps sensibly or turns into a wall.
                drawCards(client, 18);
                advance(SETTLE);
            }
            case 33 -> {
                int now = countIn(Zone.GRAVEYARD);
                if (now >= inTheGraveyard) {
                    fail("dragging a card out of the graveyard left " + now
                            + " in it, from " + inTheGraveyard);
                }
                shoot(client, "14-out-of-the-graveyard");
                // The verb keys, on the card the cursor is over. One press of 7 is what the
                // reference table does and what the whole point of the number row is; a key
                // that draws a picture and reaches nothing would pass every other check here.
                beforeTheKey = countIn(Zone.GRAVEYARD);
                hover(client, cardPoint(client));
                advance(SETTLE);
            }
            case 34 -> {
                // The press is a step after the hover on purpose. The board only learns where
                // the cursor is while it is drawing, so a key pressed in the same step as the
                // move is aimed at wherever the cursor was last frame - which is empty felt,
                // and a verb key over empty felt does nothing quietly.
                //
                // 8 rather than 7: the reference table bins on 8 and exiles on 7, and this
                // mod had them the other way round until the bindings were checked against it.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_8, 0, 0);
                }
                // That was the only card on the battlefield, and it is in the graveyard now -
                // which is the point of the check, and would leave the rest of the scene with
                // nothing to write on, put counters on, or turn over. So another one goes
                // down. The count is read at step 42; the graveyard only grows in between.
                playACard(client);
                // The life total, which is the number a game of Magic is played to and until
                // now lived in a strip along the top of the window - the one part of the
                // screen that is not the table. It is on the table now, off the far edge of
                // each board, and it is a pair of buttons.
                lifeWas = myLife(client);
                pressMyLife(client, 1);
                advance(SETTLE);
            }
            case 35 -> {
                int now = myLife(client);
                if (now != lifeWas + 1) {
                    fail("pressing the right end of the life counter moved it by "
                            + (now - lifeWas) + ", not one");
                    return;
                }
                theLifeCounterIsOnScreen(client);
                shoot(client, "14a-life-on-the-table");
                pressMyLife(client, -1);
                advance(SETTLE);
            }
            case 36 -> {
                int now = myLife(client);
                if (now != lifeWas) {
                    fail("the two ends of the life counter do not undo each other: "
                            + lifeWas + " to " + now);
                    return;
                }
                System.out.println("[devscene] the life counter goes up at one end and down"
                        + " at the other");
                // And it says so, which is the only way anybody finds out that a number
                // printed on a table is a thing they may press. Rested on here and read a
                // step later: a tooltip is worked out while the frame is drawn, so asking
                // for it in the same breath as moving the cursor asks about the last frame.
                hoverMyLifeCounter(client);
                advance(SETTLE / 2);
            }
            case 37 -> {
                theLifeCounterSaysWhatItIs(client);
                // The other half: a swing for eleven, typed rather than ticked eleven times.
                lifeWas = myLife(client);
                typeAnAmountOfLife(client, 1);
                advance(SETTLE / 2);
            }
            case 38 -> {
                expectScreen(client, "right-clicking a life counter", AmountScreen.class);
                shoot(client, "14b-how-much-life");
                press(client, "5");
                advance(SETTLE);
            }
            case 39 -> {
                expectScreen(client, "answering how much life", TableScreen.class);
                int now = myLife(client);
                if (now != lifeWas + 5) {
                    fail("typing five on the end marked plus moved life by "
                            + (now - lifeWas) + ", not five");
                    return;
                }
                System.out.println("[devscene] a typed amount goes the way the end is marked");
                // Put it back, so the rest of the run starts where it always did.
                typeAnAmountOfLife(client, -1);
                advance(SETTLE / 2);
            }
            case 40 -> {
                expectScreen(client, "right-clicking the other end", AmountScreen.class);
                press(client, "5");
                advance(SETTLE);
            }
            case 41 -> {
                expectScreen(client, "answering the other end", TableScreen.class);
                int now = myLife(client);
                if (now != lifeWas) {
                    fail("five on each end did not cancel out: " + lifeWas + " to " + now);
                    return;
                }
                // The number under a commander is the tax, and it is a button. Pressed on the
                // felt rather than through a screen, because that is the whole point of it:
                // the one number a Commander game asks you to keep for an hour should not
                // need a panel opened to change.
                //
                // Here, while the view is still framed on this player's own mat. Later in the
                // run the board is stood back far enough to show every seat, and at that size
                // the number is not written at all - which is the right answer for a board
                // that small and the wrong place to test a button.
                taxPaid = commanderTax(client);
                pressTheTaxUnderMyCommander(client, 0);
                advance(SETTLE);
            }
            case 42 -> {
                int now = commanderTax(client);
                if (now != taxPaid + 1) {
                    fail("pressing the tax on the mat recorded " + (now - taxPaid)
                            + " casts, not one");
                    return;
                }
                shoot(client, "14a-tax-on-the-mat");
                // And back down again. A number that only goes up is a number a misclick
                // costs somebody the rest of the game.
                pressTheTaxUnderMyCommander(client, 1);
                advance(SETTLE);
            }
            case 43 -> {
                int backTo = commanderTax(client);
                if (backTo != taxPaid) {
                    fail("right-clicking the tax left it at " + backTo + ", not back at "
                            + taxPaid);
                    return;
                }
                System.out.println("[devscene] the tax under a commander goes up and back down");
                int afterTheKey = countIn(Zone.GRAVEYARD);
                if (afterTheKey <= beforeTheKey) {
                    fail("the graveyard key put nothing in the graveyard, so it was"
                            + " pointing at nothing - " + countIn(Zone.BATTLEFIELD)
                            + " cards on the battlefield to aim at: "
                            + beforeTheKey + " to " + afterTheKey);
                }
                shoot(client, "15-crowded-hand");
                advance(SETTLE / 2);
            }
            case 44 -> {
                // The other end of the same question. Eighteen cards is the fan at its
                // widest; none at all is the fan a run had never drawn, and the fan is laid
                // out from the number of cards in it.
                emptyMyHand(client);
                advance(SETTLE);
            }
            case 45 -> {
                anEmptyHandIsStillABoard(client);
                shoot(client, "15a-an-empty-hand");
                // Back to the hand it had, so the rest of the run plays the board it always
                // did. The same cards rather than fresh ones: drawing spends library, and
                // three steps later the scry has nothing to order.
                drawnBack = emptied == null ? 0 : emptied.size();
                fillMyHandBack(client);
                advance(SETTLE);
            }
            case 46 -> {
                int held = countIn(Zone.HAND);
                if (held != drawnBack) {
                    fail("drawing back up from an empty hand gave " + held + ", not "
                            + drawnBack);
                    return;
                }
                if (held == 0) {
                    fail("there was nothing left to draw back into an empty hand");
                    return;
                }
                System.out.println("[devscene] an emptied hand fills again");
                // A pile has to say what it is and what pressing it would do, for the same
                // reason a mat button does: its name is not printed on it once the board is
                // drawn small, and its count alone does not say it can be opened.
                hoverAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                advance(SETTLE / 2);
            }
            case 47 -> {
                aPileSaysWhatItDoes(client, Zone.GRAVEYARD);
                // The graveyard has a card in it by now, and left-clicking a pile that is not
                // a library opens it. Anything else here is a dead end the player would find.
                clickAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD), 0);
                advance(SETTLE);
            }
            case 48 -> {
                expectScreen(client, "left-clicking the graveyard", PileScreen.class);
                aPileBoxIsTheSizeOfWhatItHolds(client);
                shoot(client, "16-graveyard-open");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                advance(SETTLE);
            }
            case 49 -> {
                expectScreen(client, "closing the graveyard", TableScreen.class);
                // Right-click on the library, which is where every verb a library has lives.
                clickAZone(client, Zone.PILES.indexOf(Zone.LIBRARY), 1);
                advance(SETTLE / 2);
            }
            case 50 -> {
                if (!menuIsOpen(client)) {
                    fail("right-clicking the library opened no menu");
                }
                // Every verb a library has is on this one menu, so a verb that is not on it is
                // a verb nobody will find. Named rather than counted: a count passes when an
                // entry is renamed out of existence.
                theLibraryOffers(client, "cascade", "reveal_until_type", "search",
                        "shuffle", "scry", "surveil", "mill", "exile_top", "fetch_basic",
                        "draw_many", "reveal");
                shoot(client, "17-library-menu");
                // And then take the one that was missing longest. Mill is watched further
                // down; this is the other pile the top of a library goes to, and a row that
                // opens a box and then moves nothing would pass every other check here.
                inTheLibraryBefore = countIn(Zone.LIBRARY);
                inExileBefore = countIn(Zone.EXILE);
                pressLibraryRow(client, "exile_top");
                advance(SETTLE / 2);
            }
            case 51 -> {
                expectScreen(client, "asking how many to exile", AmountScreen.class);
                shoot(client, "17a-how-many-to-exile");
                press(client, "OK");
                advance(SETTLE);
            }
            case 52 -> {
                expectScreen(client, "back from exiling the top", TableScreen.class);
                int library = countIn(Zone.LIBRARY);
                int exile = countIn(Zone.EXILE);
                if (exile != inExileBefore + 1 || library != inTheLibraryBefore - 1) {
                    fail("exiling the top card moved the library " + inTheLibraryBefore + " to "
                            + library + " and exile " + inExileBefore + " to " + exile);
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] the top card of the library went to exile");
                // Look at the top three, which is the half of a scry that already worked.
                lookAtTheTopOfTheLibrary(client, 3, PileScreen.Decision.SCRY);
                advance(SETTLE);
            }
            case 53 -> {
                expectScreen(client, "scrying three", PileScreen.class);
                onTopBefore = countIn(Zone.LIBRARY);
                shoot(client, "18-scrying");
                if (client.screen instanceof PileScreen pile) {
                    // Every card the title claims. The box is built to the number it had when
                    // it opened and a scry's cards arrive after that, so a box that forgets
                    // to grow shows one card under the words "Scry 3".
                    if (!pile.everyCardIsOnScreen()) {
                        fail("the scry box does not hold every card it says it is showing");
                    }
                    // Send the first one to the bottom, then say so - the half that did not
                    // exist. Aimed at where the card actually is: this used to be a pair of
                    // numbers that were right for a box filling the whole window, and a box
                    // that no longer does would have gone on passing without clicking a card.
                    // Reordering first, while all three are still being kept: drag the last
                    // one to the front and check it really goes first. Putting the cards you
                    // keep in the order you want to draw them is half of what a scry is, and
                    // for a while they simply went back the way they came off.
                    theKeptCardsCanBeReordered(client, pile);
                    Rect first = pile.slotOfCard(0);
                    if (first.isEmpty()) {
                        fail("the scry box showed no first card to click");
                    } else {
                        pile.mouseClicked(first.centerX(), first.centerY(), 0);
                        pile.mouseReleased(first.centerX(), first.centerY(), 0);
                        if (pile.markedToSendAway() != 1) {
                            fail("clicking a scried card marked "
                                    + pile.markedToSendAway() + " cards to send away");
                        }
                    }
                }
                advance(SETTLE / 4);
            }
            case 54 -> {
                shoot(client, "19-one-going-to-the-bottom");
                // Held mid-drag rather than dropped, because what is being photographed is
                // what the player can see *while* they are still aiming - which used to be
                // nothing at all: the row does not move under the cursor, so a drag looked
                // like it had done nothing until it was let go and the numbers jumped.
                startDraggingAScriedCard(client);
                advance(SETTLE / 4);
            }
            case 55 -> {
                shoot(client, "19a-mid-drag");
                letTheScriedCardGo(client);
                advance(SETTLE / 4);
            }
            case 56 -> {
                press(client, "Done");
                advance(SETTLE);
            }
            case 57 -> {
                expectScreen(client, "deciding a scry", TableScreen.class);
                if (countIn(Zone.LIBRARY) != onTopBefore) {
                    fail("a scry changed how many cards were in the library: "
                            + onTopBefore + " to " + countIn(Zone.LIBRARY));
                }
                // The count alone proves nothing - it is unchanged whether the decision
                // arrived or was dropped on the floor. The log line only exists if it arrived.
                if (!theLogMentions("log.gathering.scried")) {
                    fail("the scry never reached the game");
                }
                shoot(client, "20-scry-decided");
                // The other half of the same screen. A surveil bins what a scry buries, and
                // nothing had ever opened one - so the word "graveyard" on it was a string
                // nobody had read and the move it names was never once made.
                inTheGraveyard = countIn(Zone.GRAVEYARD);
                lookAtTheTopOfTheLibrary(client, 2, PileScreen.Decision.SURVEIL);
                advance(SETTLE);
            }
            case 58 -> {
                expectScreen(client, "surveilling two", PileScreen.class);
                shoot(client, "20a-surveilling");
                if (client.screen instanceof PileScreen pile) {
                    theBinIsTheGraveyard(pile);
                    Rect first = pile.slotOfCard(0);
                    if (first.isEmpty()) {
                        fail("the surveil box showed no card to bin");
                    } else {
                        pile.mouseClicked(first.centerX(), first.centerY(), 0);
                        pile.mouseReleased(first.centerX(), first.centerY(), 0);
                    }
                }
                advance(SETTLE / 4);
            }
            case 59 -> {
                shoot(client, "20b-one-going-to-the-graveyard");
                press(client, "Done");
                advance(SETTLE);
            }
            case 60 -> {
                expectScreen(client, "deciding a surveil", TableScreen.class);
                int now = countIn(Zone.GRAVEYARD);
                if (now != inTheGraveyard + 1) {
                    fail("surveilling a card into the graveyard put " + (now - inTheGraveyard)
                            + " there, not one");
                }
                if (!theLogMentions("log.gathering.surveilled")) {
                    fail("the surveil never reached the game");
                }
                // Counters on before the board on the block is photographed, so the pictures
                // of it carry a card with numbers on it. The seated board draws counters and
                // the block drew none, which is a thing no picture of an empty board could
                // have shown - and the same two counters are read back later, on the felt.
                twoCountersOnACard(client);
                if (client.screen != null) {
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
            case 61 -> {
                if (client.screen instanceof TableScreen board && board.isHoveringSomething()) {
                    fail("a cursor off the board still had a card under it");
                }
                shoot(client, "21-on-the-table-in-play");
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 62 -> {
                if (client.screen instanceof TableScreen board && !board.isHoveringSomething()) {
                    fail("hovering a card on the real table lit nothing");
                }
                if (!ClientTableHighlight.isLitAtAll()) {
                    fail("the table in the world was not told what the cursor was on");
                }
                shoot(client, "22-on-the-table-hovering");
                theCursorPicksWhereItPoints(client);
                thePickerAgreesAcrossTheScreen(client);
                // A card lifted and still in the air over the board on the block: the size it
                // is drawn at and whether anything says where it is about to land are both
                // only visible mid-drag.
                liftACardOnTheBlock(client);
                advance(SETTLE / 2);
            }
            case 63 -> {
                shoot(client, "22a-carrying-a-card-on-the-table");
                if (client.screen instanceof TableScreen board) {
                    int[] to = cardPoint(client);
                    board.mouseReleased(to[0], to[1], 0);
                }
                // The mats on the block carry the same four buttons the seated board does,
                // and for a while pressing one there did nothing at all: the press was told
                // to look for the button in screen pixels while the board was measured in
                // units of felt. Four boxes painted on a table that ignore the mouse are a
                // dead end in the one view meant for playing in.
                inTheHand = countIn(Zone.HAND);
                hoverAVerbButton(client, TableVerb.DRAW);
                advance(SETTLE / 2);
            }
            case 64 -> {
                aButtonSaysWhatItDoes(client, TableVerb.DRAW, "2");
                pressAVerbButton(client, TableVerb.DRAW);
                advance(A_MOMENT);
            }
            case 65 -> {
                // The same crossing, drawn by the world rather than by the window. Everyone
                // at the table sees a card move, and most of them are not sitting at it.
                aCardIsInTheAir(client, "drawing one on the block");
                shoot(client, "22b-a-card-in-the-air-on-the-table");
                advance(SETTLE);
            }
            case 66 -> {
                // The counter has to be inside the window in this view as well. Framed on
                // the mat alone it came out under the status row here, the same way it came
                // out past the top of the window on the seated board.
                theLifeCounterIsOnScreen(client);
                everyLifeCounterHasItsEnds(client);
                // And pressed there. The signs on its ends are turned to face their own
                // seat on this board, and the press is worked out from the same rectangle -
                // so if those two ever stop agreeing, the end marked plus takes a life off.
                lifeWas = myLife(client);
                pressMyLife(client, 1);
                advance(A_MOMENT);
            }
            case 67 -> {
                int now = myLife(client);
                if (now != lifeWas + 1) {
                    fail("pressing the end marked plus on the block moved life by "
                            + (now - lifeWas) + ", not one");
                    return;
                }
                pressMyLife(client, -1);
                advance(A_MOMENT);
            }
            case 68 -> {
                int now = myLife(client);
                if (now != lifeWas) {
                    fail("the ends of the life counter on the block do not undo each other: "
                            + lifeWas + " to " + now);
                    return;
                }
                System.out.println("[devscene] the life counter presses on the block too");
                advance(A_MOMENT);
            }
            case 69 -> {
                // The same tax, pressed on the board drawn in the world. Worth pressing twice
                // over because the two views hand the press its rectangle in different spaces
                // - pixels on the window, units of felt on the block - and a button that only
                // works in the view which has had the attention is a shape of bug this run
                // has caught twice already.
                taxPaid = commanderTax(client);
                pressTheTaxUnderMyCommander(client, 0);
                advance(SETTLE);
            }
            case 70 -> {
                int paid = commanderTax(client);
                if (paid != taxPaid + 1) {
                    fail("pressing the tax on the block recorded " + (paid - taxPaid)
                            + " casts, not one");
                    return;
                }
                shoot(client, "22c-the-tax-on-the-block");
                pressTheTaxUnderMyCommander(client, 1);
                advance(SETTLE);
            }
            case 71 -> {
                int backTo = commanderTax(client);
                if (backTo != taxPaid) {
                    fail("right-clicking the tax on the block left it at " + backTo
                            + ", not back at " + taxPaid);
                    return;
                }
                System.out.println("[devscene] the tax presses on the block too");
                int now = countIn(Zone.HAND);
                if (now <= inTheHand) {
                    fail("the draw button on the block drew nothing: "
                            + inTheHand + " to " + now);
                }
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 72 -> {
                // The whole table, which is the one framing that shows the chair nobody is in.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 73 -> {
                // Somebody sits down opposite. Every picture so far has been of a table with
                // one player at it, which is not the game this is for.
                seatARival(client);
                advance(SETTLE);
            }
            case 74 -> {
                shoot(client, "23-two-players");
                // Both seats now, which is the only moment this run can check the far one.
                everyLifeCounterHasItsEnds(client);
                // A card somebody else moves across their own mat. The commonest movement in
                // the game and the last one still teleporting: a card changing zones was
                // followed, and a card sliding from one spot to another was not.
                theRivalSlidesACardAcrossTheirMat(client);
                advance(A_MOMENT);
            }
            case 75 -> {
                aCardIsInTheAir(client, "a rival sliding a card across their mat");
                shoot(client, "23a-a-rivals-card-on-the-move");
                advance(SETTLE);
            }
            case 76 -> {
                openMyCounters(client);
                advance(SETTLE / 2);
            }
            case 77 -> {
                expectScreen(client, "asking for my own counters", CountersScreen.class);
                shoot(client, "24-commander-damage");
                tookCommanderDamage = damageTaken(client);
                // One row per enemy commander, and the rival brought partners - so two rows,
                // or the grid has collapsed back to one number per seat, which is the bug
                // the keying exists to rule out: twenty-one is counted against the SAME
                // commander, and partners are two.
                if (damageRowsShowing(client) != 2) {
                    fail("a rival with two partners grew " + damageRowsShowing(client)
                            + " damage rows, not one per commander");
                }
                // And the rows say who. Each enemy commander's name is pushed with the view
                // that showed it, and a row stuck on "Loading..." cannot answer the one
                // question the grid exists for: which commander is at twenty-one.
                if (!enemyCommanderNamesKnown(client)) {
                    fail("the rival's commander names never arrived, "
                            + "so the damage rows cannot say who");
                }
                press(client, "+");
                advance(SETTLE);
            }
            case 78 -> {
                int now = damageTaken(client);
                if (now <= tookCommanderDamage) {
                    fail("commander damage did not go up: " + tookCommanderDamage + " to " + now);
                }
                shoot(client, "25-damage-recorded");
                // Out by the button rather than by the escape key. Every panel needs a way out
                // somebody can see, and a run that always leaves by a key nobody was told
                // about would pass just as happily with no way out at all.
                press(client, "Done");
                advance(SETTLE / 2);
            }
            case 79 -> {
                aCommanderLeavesItsSlot(client);
                advance(SETTLE);
            }
            case 80 -> {
                theCommanderGoesHomeToItsOwnSlot(client);
                advance(SETTLE);
            }
            case 81 -> {
                expectScreen(client, "pressing Done on the counters", TableScreen.class);
                // The other number a game of Commander asks a player to keep for an hour.
                taxPaid = commanderTax(client);
                openCommanderCounters(client);
                advance(SETTLE / 2);
            }
            case 82 -> {
                expectScreen(client, "asking for a commander's counters", CountersScreen.class);
                if (client.screen instanceof CountersScreen counters
                        && counters.taxRowsShowing() != Zone.COMMAND_SLOTS.size()) {
                    fail("the counters screen offered " + counters.taxRowsShowing()
                            + " commander taxes, not " + Zone.COMMAND_SLOTS.size());
                }
                shoot(client, "25a-commander-tax");
                press(client, "+");
                advance(SETTLE);
            }
            case 83 -> {
                int now = commanderTax(client);
                if (now <= taxPaid) {
                    fail("commander tax did not go up: " + taxPaid + " to " + now);
                }
                press(client, "Done");
                advance(SETTLE / 2);
            }
            case 84 -> {
                expectScreen(client, "leaving a commander's counters", TableScreen.class);
                // Framed on the whole table again. It was framed that way eleven steps ago
                // and then somebody sat down opposite, which re-frames the camera onto your
                // own board - so the picture named "the whole table" had been a picture of
                // one mat since the day a rival was added to the run.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 85 -> {
                theWholeTableIsOnScreen(client);
                if (client.screen instanceof TableScreen framed) {
                    seatedMat = ClientTableState.seatAt(table)
                            .map(seat -> framed.board().matRect(seat)).orElse(Rect.NONE);
                    System.out.println("[devscene] seated: " + framed.framingReport());
                }
                shoot(client, "26-the-whole-table");
                // A window somebody has resized, which is the one path that re-runs a screen's
                // init on an instance that is already holding a game. Two sizes: one where
                // everything gets bigger and the felt gets smaller, and one the other way.
                resizeTo(client, 1, "a smaller interface");
                advance(SETTLE / 2);
            }
            case 86 -> {
                theBoardIsStillFramed(client, "at the smallest interface");
                shoot(client, "27-a-smaller-interface");
                resizeTo(client, 0, "the automatic interface again");
                advance(SETTLE / 2);
            }
            case 87 -> {
                // The two questions a token asks. Nothing else in this run opens either of
                // them, and both are screens somebody can get stuck on: the first one is the
                // only place in the mod that takes a line of typing from a player.
                openTheTokenQuestion(client);
                advance(SETTLE / 2);
            }
            case 88 -> {
                expectScreen(client, "asking for a token", TextPromptScreen.class);
                shoot(client, "28a-what-token");
                typeInto(client, "Treasure");
                press(client, "OK");
                advance(SETTLE / 2);
            }
            case 89 -> {
                expectScreen(client, "naming a token", AmountScreen.class);
                shoot(client, "28b-how-many");
                // Backed out rather than answered: a token wants a real printing off
                // Scryfall, and this run has no network. What is being proved is that two
                // questions in a row both come back to the board.
                if (client.screen != null) {
                    client.screen.onClose();
                }
                advance(SETTLE / 2);
            }
            case 90 -> {
                expectScreen(client, "backing out of a token", TableScreen.class);
                theBoardIsStillFramed(client, "back at the automatic interface");
                shoot(client, "28-back-to-normal");
                // A game has to be finishable. Taken as far as the question and then backed
                // out of, because going through with it would end the game this run is still
                // using - but the wiring from menu entry to question is the part that was
                // missing entirely, and it is the part this proves.
                aGameCanBeConceded(client);
                advance(SETTLE / 2);
            }
            case 91 -> {
                // Photographed a step after it was opened, so the frame is the question
                // rather than the board it was asked over.
                expectScreen(client, "asking before a game is thrown away", ConfirmScreen.class);
                theConcedeQuestionOffersBothAnswers(client);
                shoot(client, "28c-are-you-sure");
                backOutOfConceding(client);

                // Last, because everything above needs a seat: stand up mid-game and look at
                // the same table as somebody who is only watching it. After backing out of
                // the question, not before - standing up works through the board's own menu,
                // and with a dialog over it the gesture goes nowhere at all.
                // Noted before the chair is given up, because the whole point of the check
                // that follows is that this name survives standing up.
                SeatId leaving = ClientTableState.seatAt(table).orElse(null);
                GameView leavingFrom = table == null
                        ? null : ClientTableState.viewOf(table).orElse(null);
                whoWasSitting = leaving == null || leavingFrom == null
                        ? null
                        : leavingFrom.seat(leaving).occupant()
                                .map(dev.gathering.core.game.PlayerRef::name).orElse(null);
                if (whoWasSitting == null) {
                    fail("the client stood up from a seat that had no name on it");
                    return;
                }
                standUp(client);
                // Half a second, deliberately short: the table pushes the public board out on
                // its own every two seconds, so a longer wait here would cover for a seat
                // change that told nobody and this would pass either way.
                advance(SETTLE / 4);
            }
            case 92 -> {
                if (ClientTableState.seatAt(table).isPresent()) {
                    fail("standing up left the client still holding a seat");
                }
                shoot(client, "29-watching-from-outside");
                theBoardTheyLeftIsStillDrawn(client);
                theFeltRunsToTheBottomForAWatcher(client);
                if (client.screen instanceof TableScreen board && !board.theLogHasRoom()) {
                    fail("a watcher has nowhere to open the game log");
                }
                aSpectatorReadsAGraveyard(client);
                advance(SETTLE / 2);
            }
            case 93 -> {
                expectScreen(client, "a spectator opening a graveyard", PileScreen.class);
                shoot(client, "30-a-spectator-reads-a-graveyard");
                if (client.screen != null) {
                    client.screen.onClose();
                }
                pokeEverything(client);
                advance(SETTLE);
            }
            case 94 -> {
                expectScreen(client, "a spectator using every gesture on the board",
                        TableScreen.class);
                shoot(client, "31-still-watching");
                hoverSomebodysLifeCounter(client);
                advance(SETTLE / 2);
            }
            case 95 -> {
                aWatcherIsToldWhoseLifeThatIs(client);
                shoot(client, "32-a-watcher-reads-a-life-total");
                aWatcherOpensTheLog(client);
                advance(SETTLE / 2);
            }
            case 96 -> {
                theLogStillNamesWhoLeft(client);
                shoot(client, "33-a-watcher-reads-the-log");
                advance(SETTLE / 2);
            }
            case 97 -> {
                aDraftPodFormsAtASecondCluster(client);
                advance(SETTLE);
            }
            case 98 -> {
                expectScreen(client, "a draft pod dealing its first pack", DraftScreen.class);
                theDraftScreenShowsAPack(client);
                shoot(client, "34-the-first-pack");
                advance(SETTLE / 2);
            }
            case 99 -> {
                // Clicked here and photographed next: a screenshot grabs the frame that has
                // already been drawn, so shooting in the same step as the click photographs
                // the screen as it was before it.
                pickTwoFromTheDraftPack(client);
                advance(SETTLE / 2);
            }
            case 100 -> {
                theChosenCardsAreMarkedOnScreen(client);
                shoot(client, "35-two-cards-chosen");
                takeTheDraftPick(client);
                advance(SETTLE);
            }
            case 101 -> {
                theDraftIsWaitingOnTheRest(client);
                shoot(client, "36-waiting-on-the-rest");
                lookAtMyPicks(client);
                advance(SETTLE / 2);
            }
            case 102 -> {
                theScreenIsShowingMyPicks(client);
                shoot(client, "36a-my-picks-so-far");
                advance(SETTLE / 2);
            }
            case 103 -> {
                openTheDeckScreen(client);
                advance(SETTLE / 2);
            }
            case 104 -> {
                expectScreen(client, "opening a deck to build it", DeckContentsScreen.class);
                everyBasicLandHasAButton(client);
                theDeckScreenListsItsCards(client);
                shoot(client, "37-building-a-deck");
                // A deck put together out of two cards has no name, and the title is where
                // one gets typed. Typed rather than assumed: a heading that turns out not to
                // take a cursor is a dead end nobody would find by reading the code.
                nameTheDeck(client, "Bear Tribal");
                advance(SETTLE / 2);
            }
            // Sleeves. What a deck looks like from behind is picked here, on the deck, rather
            // than anywhere near a player's own settings: somebody with three decks sleeves
            // them differently, which is the whole reason sleeves tell them apart.
            case 105 -> {
                press(client, Component.translatable(
                        "screen.gathering.deck.sleeves").getString());
                advance(SETTLE / 2);
            }
            case 106 -> {
                expectScreen(client, "picking sleeves", SleeveScreen.class);
                everySleeveHasItsPicture(client);
                shoot(client, "37b-picking-sleeves");
                advance(SETTLE / 2);
            }
            case 107 -> {
                pickTheSleeve(client, dev.gathering.core.card.Sleeve.GRASS);
                advance(SETTLE);
            }
            case 108 -> {
                if (deckInHand(client).map(deck -> deck.sleeve())
                        .orElse(dev.gathering.core.card.Sleeve.DEFAULT)
                        != dev.gathering.core.card.Sleeve.GRASS) {
                    fail("a sleeve was picked and the deck in hand is still in "
                            + deckInHand(client).map(deck -> deck.sleeve().name()).orElse("nothing"));
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] the deck is sleeved in grass");
                advance(SETTLE / 2);
            }
            case 109 -> {
                // Taken here rather than at the end of the step that typed it: a screenshot
                // grabs the last frame drawn, so a picture taken in the same step as the
                // typing is a picture of the screen before it.
                shoot(client, "37a-naming-a-deck");
                // Out of every screen, so the picture is the hotbar and the world.
                client.setScreen(null);
                sealedPacksInTheHotbar(client);
                advance(SETTLE);
            }
            case 110 -> {
                // A moment for the symbols to arrive: they are fetched the first time one is
                // asked for, so the first frame of a pack is always a plain wrapper.
                sealedPacksInTheHotbar(client);
                advance(SETTLE * 2);
            }
            case 111 -> {
                everyPackDrewItsSymbol(client);
                shoot(client, "38-sealed-packs");
                advance(SETTLE / 2);
            }
            case 112 -> {
                aSealedPackOnTheScreen(client);
                advance(SETTLE);
            }
            case 113 -> {
                expectScreen(client, "tearing a pack open", PackOpeningScreen.class);
                aSealedPackIsSealed(client);
                // Off to one side, so the picture is of a pack turned toward the cursor
                // rather than of one facing straight ahead. A pack square on is what this
                // looked like before it was a thing in space, so a photograph of that would
                // prove nothing about whether it still is.
                hover(client, new int[] {
                    client.getWindow().getGuiScaledWidth() / 2 - 130,
                    client.getWindow().getGuiScaledHeight() / 2 - 90});
                advance(SETTLE / 2);
            }
            case 114 -> {
                shoot(client, "39-a-sealed-pack");
                tearThePack(client, 0.45);
                advance(SETTLE / 2);
            }
            case 115 -> {
                aPackHalfTornIsHalfTorn(client);
                shoot(client, "40-tearing-it-open");
                tearThePack(client, 1.2);
                advance(SETTLE / 2);
            }
            case 116 -> {
                aTornPackIsOpen(client);
                everyCardIsShown(client);
                // The cursor onto one of them, so the next step can ask which way they are
                // leaning. These cards are made up and have no art, so they draw as the
                // placeholder and nothing about the turn is photographable - what can be
                // checked is the arithmetic behind it.
                putTheCursorOnAPulledCard(client);
                advance(SETTLE / 2);
            }
            case 117 -> {
                shoot(client, "41-what-was-in-it");
                thePulledCardsLeanTowardTheCursor(client);
                advance(SETTLE);
            }
            case 118 -> {
                theReadKeyAnswersOverAPulledCard(client);
                client.setScreen(null);
                aCollectionWithSomethingInIt(client);
                advance(SETTLE);
            }
            case 119 -> {
                openTheCollection(client);
                advance(SETTLE);
            }
            case 120 -> {
                expectScreen(client, "opening a collection", CollectionScreen.class);
                aCollectionShowsWhatIsInIt(client);
                everyButtonSaysSomething(client);
                everyCardStaysInItsBox(client);
                shoot(client, "42-a-collection");
                searchTheCollection(client, "forest");
                advance(SETTLE);
            }
            case 121 -> {
                aSearchNarrowsIt(client);
                shoot(client, "43-searching-a-collection");
                // The search language, which is most of what a collection is for once it is
                // bigger than a screen. Typed rather than set, so what the box parses and what
                // somebody would actually type are the same thing.
                searchTheCollection(client, "t:creature mv<=2");
                advance(SETTLE);
            }
            case 122 -> {
                if (!(client.screen instanceof CollectionScreen box)) {
                    fail("the collection closed while a search was being typed into it");
                    return;
                }
                if (box.shown().isEmpty()) {
                    fail("t:creature mv<=2 found nothing in a box that holds Grizzly Bears");
                }
                shoot(client, "43a-searching-by-what-a-card-is");
                // By what the button says rather than by what is drawn on it. The mark is a
                // question mark; the button is "What can I search for?".
                press(client, Component.translatable(
                        "screen.gathering.collection.search_help").getString());
                advance(SETTLE / 2);
            }
            case 123 -> {
                // What the box understands, which is the only thing that says the box
                // understands anything - a search language nobody is told about is one
                // nobody uses.
                shoot(client, "43b-what-the-box-understands");
                press(client, Component.translatable(
                        "screen.gathering.collection.search_help").getString());
                searchTheCollection(client, "");
                advance(SETTLE / 2);
            }
            case 124 -> {
                press(client, Component.translatable(
                        "screen.gathering.collection.build_deck").getString());
                advance(SETTLE);
            }
            case 125 -> {
                expectScreen(client, "building a deck from a collection",
                        DeckBuilderScreen.class);
                everyCardStaysInItsBox(client);
                shoot(client, "44-the-deck-builder");
                // A card into the deck, and one of them made the commander, so the picture
                // after this has a list on the right rather than an empty column - and so the
                // suggestions button has something to rank against.
                buildADeck(client);
                advance(SETTLE);
            }
            case 126 -> {
                // Photographed a step after the clicks, which is the convention: a shot asked
                // for in the same step catches the frame that was already drawn.
                shoot(client, "44a-a-deck-taking-shape");
                cancellingGoesBack(client, "the collection it was opened from",
                        CollectionScreen.class);
                client.setScreen(null);
                cardsInHandToTradeWith(client);
                advance(SETTLE);
            }
            case 127 -> {
                aTradeOnTheScreen(client, false, 2);
                advance(SETTLE);
            }
            case 128 -> {
                expectScreen(client, "opening a trade", TradeScreen.class);
                aTradeShowsBothSides(client);
                shoot(client, "45-a-trade");
                // The lights are the reason the screen exists, and they are the one thing a
                // picture of an open trade does not show. Sent as the server would send it.
                aTradeOnTheScreen(client, true, 2);
                advance(SETTLE);
            }
            case 129 -> {
                expectScreen(client, "agreeing to a trade", TradeScreen.class);
                shoot(client, "45a-both-agreed");
                // Taking a whole offer back down in one press. Reached for rather than
                // assumed: it is the third button on a row that used to hold two, and a
                // button drawn under another one is a button nobody can press.
                press(client, "Take it all back");
                aTradeOnTheScreen(client, false, 0);
                advance(SETTLE);
            }
            case 130 -> {
                expectScreen(client, "taking an offer back down", TradeScreen.class);
                takingItBackIsSpentWhenThereIsNothingUp(client);
                shoot(client, "45b-nothing-up");
                advance(SETTLE / 2);
            }
            case 131 -> {
                client.setScreen(null);
                aShelfOfLoanerDecks(client);
                advance(SETTLE);
            }
            case 132 -> {
                offerTheLoaners(client);
                advance(SETTLE);
            }
            case 133 -> {
                expectScreen(client, "being offered a loaner deck", LoanerScreen.class);
                theShelfIsOnTheScreen(client);
                shoot(client, "46-borrow-a-deck");
                advance(SETTLE / 2);
            }
            case 134 -> {
                client.setScreen(null);
                aBestOfThreeWaitingToSideboard(client);
                advance(SETTLE * 2);
            }
            case 135 -> {
                expectScreen(client, "finishing game one of a set", SideboardScreen.class);
                bothSidesOfTheSideboardAreShown(client);
                shoot(client, "47-between-games");
                advance(SETTLE / 2);
            }
            case 136 -> {
                client.setScreen(null);
                theQuestionBeforePlayingForKeeps(client, false);
                advance(SETTLE);
            }
            case 137 -> {
                expectScreen(client, "being asked to play for keeps",
                        AnteConsentScreen.class);
                theQuestionNamesTheStakesAndOffersBothAnswers(client);
                shoot(client, "48-playing-for-keeps");
                // Said yes, so the picture after this is the half of the screen nobody sees
                // until they have committed: waiting on everybody else.
                theQuestionBeforePlayingForKeeps(client, true);
                advance(SETTLE);
            }
            case 138 -> {
                expectScreen(client, "having agreed to play for keeps",
                        AnteConsentScreen.class);
                if (!(client.screen instanceof AnteConsentScreen said) || !said.saidYes()) {
                    fail("a player who agreed was still being asked");
                }
                shoot(client, "48a-waiting-on-the-rest");
                client.setScreen(null);
                advance(SETTLE / 2);
            }
            case 139 -> {
                client.setScreen(null);
                aPotOnTheTable(client);
                advance(SETTLE);
            }
            case 140 -> {
                // Back at the board this run has been playing on all along, which is where
                // the pot has to be visible if it is visible anywhere.
                MinecraftServer server = client.getSingleplayerServer();
                if (server == null || table == null || client.player == null) {
                    fail("there was no table to reopen with a pot on it");
                    return;
                }
                java.util.UUID who = client.player.getUUID();
                BlockPos at = table;
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(who);
                    if (player != null) {
                        dev.gathering.server.TableActions.openFor(player, at);
                    }
                });
                advance(SETTLE);
            }
            case 141 -> {
                expectScreen(client, "looking at a table with a pot on it",
                        TableScreen.class);
                thePotIsDrawnInTheMiddle(client);
                shoot(client, "49-the-pot");
                advance(SETTLE / 2);
            }
            case 142 -> {
                // Back in the chair. The run stood up forty steps ago to check what a
                // spectator sees, and a spectator cannot pick anything up at all.
                sitBackDown(client);
                advance(SETTLE);
            }
            case 143 -> {
                // The gesture a physical table has and this one did not: a hand held flat on
                // a pile picks the pile up. The library is the hard case - nobody may name a
                // card in it, so there is nothing in the air to name and the whole thing has
                // to move as a pile or not at all.
                expectScreen(client, "a table to pick a pile up from", TableScreen.class);
                int[] at = zoneCenter(client, Zone.PILES.indexOf(Zone.LIBRARY));
                if (at == null || !(client.screen instanceof TableScreen board)) {
                    fail("there was no library on screen to hold: seat "
                            + ClientTableState.seatAt(table) + ", table " + table);
                    advance(SETTLE / 2);
                    return;
                }
                if (countIn(Zone.LIBRARY) < 2) {
                    fail("the library had nothing in it to pick up: " + countIn(Zone.LIBRARY));
                    advance(SETTLE / 2);
                    return;
                }
                hover(client, at);
                board.mouseClicked(at[0], at[1], 0);
                // Held, not clicked: the frames that pass here are the hold.
                advance(SETTLE / 2);
            }
            case 144 -> {
                expectScreen(client, "a pile in the air", TableScreen.class);
                shoot(client, "50-a-pile-in-hand");
                int[] onto = zoneCenter(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                if (onto == null || !(client.screen instanceof TableScreen board)) {
                    fail("there was no graveyard on screen to drop a pile on");
                    advance(SETTLE / 2);
                    return;
                }
                hover(client, onto);
                board.mouseReleased(onto[0], onto[1], 0);
                advance(SETTLE);
            }
            case 145 -> {
                int left = countIn(Zone.LIBRARY);
                if (left != 0) {
                    fail("holding a library did not pick the whole thing up: " + left + " left");
                    advance(SETTLE / 2);
                    return;
                }
                if (!theLogMentions("log.gathering.zone_moved_own")) {
                    fail("a whole pile moved and the log said nothing about it");
                    advance(SETTLE / 2);
                    return;
                }
                shoot(client, "51-the-pile-landed");
                advance(SETTLE / 2);
            }
            case 146 -> {
                // Two +1/+1 counters on a creature, which is the thing the board has to say
                // out loud. It used to shrink them to a "+" and put the count after it, so
                // two of them read "+2" - a different card entirely, in Magic.
                twoCountersOnACard(client);
                advance(SETTLE);
            }
            case 147 -> {
                expectScreen(client, "a board with counters on it", TableScreen.class);
                int on = countersOnTheCardWithCounters(client);
                if (on != 2) {
                    fail("two +1/+1 counters went on and the board says " + on);
                    advance(SETTLE / 2);
                    return;
                }
                shoot(client, "52-counters-on-a-card");
                // And straighten it, so the next step can photograph the same counters on a
                // card that is not lying on its side. A tapped card turns its writing with
                // it, which is right, and it means one picture cannot say where the counters
                // sit on a card.
                straightenTheCounteredCard();
                advance(SETTLE);
            }
            case 148 -> {
                shoot(client, "52a-counters-straight");
                // Two cards on the same spot, which is what a stack on a real table is.
                aStackOfTwoOnTheFelt(client);
                advance(SETTLE);
            }
            case 149 -> {
                expectScreen(client, "a stack on the felt", TableScreen.class);
                inExileBefore = countIn(Zone.EXILE);
                holdTheStackAndDropItOnAZone(client);
                advance(SETTLE / 2);
            }
            case 150 -> {
                // Held rather than dragged, so the press is still down: the frames since the
                // last step are the hold, and this step lets go.
                letTheStackGo(client);
                advance(SETTLE);
            }
            case 151 -> {
                int now = countIn(Zone.EXILE);
                if (now != inExileBefore + 2) {
                    fail("a stack of two went to exile and it holds " + now
                            + ", not " + (inExileBefore + 2));
                    advance(SETTLE / 2);
                    return;
                }
                shoot(client, "53-a-stack-went-together");
                advance(SETTLE / 2);
            }
            case 152 -> {
                // The pen. A group with no rules engine remembers a rule by writing it on the
                // card, and every other player reading it is the whole point.
                writeOnACard(client);
                advance(SETTLE);
            }
            case 153 -> {
                expectScreen(client, "a card with writing on it", TableScreen.class);
                String written = whatIsWrittenOnTheCard();
                if (!"flying until end of turn".equals(written)) {
                    fail("a note was written and the board says \"" + written + "\"");
                    advance(SETTLE / 2);
                    return;
                }
                // A note is longer than a card is wide, so resting on the card is how the
                // rest of it gets read. Checked because the card itself can only ever show
                // the first word and a half of it.
                restOnTheWrittenCard(client);
                advance(SETTLE / 4);
            }
            case 154 -> {
                // The other pen: a power and toughness written over the printed ones. Typed,
                // never worked out - the mod does not know what the card was printed as and
                // deliberately never will.
                writeStrengthOnACard(client);
                advance(SETTLE);
            }
            case 155 -> {
                expectScreen(client, "a card with numbers written on it", TableScreen.class);
                String numbers = whatStrengthIsOnTheCard();
                if (!"12/12".equals(numbers)) {
                    fail("12/12 was written on a card and the board says \"" + numbers + "\"");
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] a card says " + numbers + " because somebody typed it");
                shoot(client, "54a-power-and-toughness");
                advance(SETTLE / 2);
            }
            case 156 -> {
                // Discard at random, which is the one verb here the server decides. What is
                // being proved is not that it is random - a run cannot prove that - but that
                // the press reaches the server at all and that cards actually move. A menu
                // entry that opens a box and then quietly does nothing would pass every other
                // check in this file.
                inTheHandBefore = countIn(Zone.HAND);
                inTheGraveyardBefore = countIn(Zone.GRAVEYARD);
                discardAtRandom(client);
                advance(SETTLE / 2);
            }
            case 157 -> {
                expectScreen(client, "asking how many to discard", AmountScreen.class);
                shoot(client, "54b-how-many-at-random");
                press(client, "OK");
                advance(SETTLE);
            }
            case 158 -> {
                expectScreen(client, "back from a random discard", TableScreen.class);
                int hand = countIn(Zone.HAND);
                int graveyard = countIn(Zone.GRAVEYARD);
                if (hand != inTheHandBefore - 1) {
                    fail("one card was discarded at random and the hand went from "
                            + inTheHandBefore + " to " + hand);
                    advance(SETTLE / 2);
                    return;
                }
                if (graveyard != inTheGraveyardBefore + 1) {
                    fail("one card was discarded at random and the graveyard went from "
                            + inTheGraveyardBefore + " to " + graveyard);
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println(
                        "[devscene] a card left the hand at random and landed in the graveyard");
                advance(SETTLE / 2);
            }
            case 159 -> {
                if (!(client.screen instanceof TableScreen board)) {
                    fail("the board went away before a note could be read off it");
                    advance(SETTLE / 2);
                    return;
                }
                String said = String.join(" ", board.tooltipShowing().stream()
                        .map(net.minecraft.network.chat.Component::getString).toList());
                if (!said.contains("flying until end of turn")) {
                    fail("resting on a written card said \"" + said + "\"");
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] resting on a written card reads: " + said);
                // Photographed here rather than a step earlier: a tooltip is worked out while
                // the frame is drawn, so a picture taken in the same step as the hover shows
                // whatever the cursor was resting on before it moved.
                shoot(client, "54-written-on-a-card");
                advance(SETTLE / 4);
            }
            case 160 -> {
                // Frozen: it stays tapped when everything else untaps. The whole feature
                // lands on a press made next turn without looking, so the only check worth
                // anything is the one that makes that press.
                freezeACard(client);
                advance(SETTLE);
            }
            case 161 -> {
                if (!isFrozen(frozen)) {
                    fail("a card was frozen and the board does not think so");
                    advance(SETTLE / 2);
                    return;
                }
                shoot(client, "54c-frozen");
                tapTheFrozenCard(client);
                advance(SETTLE);
            }
            case 162 -> {
                untapEverything(client);
                // Cursor off the card here rather than in the step that photographs it. What
                // is on screen is worked out while the frame is drawn, so a cursor moved and
                // a picture taken in the same step gives a picture of where the cursor was.
                lookAwayFromTheCards(client);
                advance(SETTLE);
            }
            case 163 -> {
                if (!isTapped(frozen)) {
                    fail("untapping everything untapped the frozen card, which is the one"
                            + " thing being frozen means");
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] a frozen card sat out the untap step");
                // The cursor was moved off a step ago, so this shows the frost on its own
                // rather than the frost plus the ring the cursor draws round anything it is
                // resting on - which is the whole thing being checked here.
                if (client.screen instanceof TableScreen board && !board.tooltipShowing().isEmpty()) {
                    fail("the cursor was still on a card when the frost was photographed");
                }
                shoot(client, "54d-still-tapped");
                advance(SETTLE / 2);
            }
            case 164 -> {
                // The other two destinations on the number row, checked because they were
                // wrong: 7 exiles and 9 puts cards back under the library. Everything else
                // about the row was right, which is exactly why nobody noticed these.
                // Before any of it, and not behind a check that can fail first: the menu
                // has to name the keys the keys actually are. That is the half that went
                // wrong last time - the row said "To graveyard 7" while 7 had started
                // exiling, and the interface was teaching the wrong key in the one place
                // somebody was looking straight at it.
                theMenuNamesTheRightKeys();
                inExileBefore = countIn(Zone.EXILE);
                inTheLibraryBefore = countIn(Zone.LIBRARY);
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 165 -> {
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_7, 0, 0);
                }
                advance(SETTLE);
            }
            case 166 -> {
                int now = countIn(Zone.EXILE);
                if (now != inExileBefore + 1) {
                    fail("7 is the exile key and exile went from " + inExileBefore
                            + " to " + now);
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] 7 exiles, the way the reference table has it");
                // And 9, which asks the server to put it back under the library in an order
                // nobody chose - so what is checked is that the card arrived, not where.
                playACard(client);
                advance(SETTLE);
            }
            case 167 -> {
                inTheLibraryBefore = countIn(Zone.LIBRARY);
                hover(client, cardPoint(client));
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_9, 0, 0);
                }
                advance(SETTLE);
            }
            case 168 -> {
                int now = countIn(Zone.LIBRARY);
                if (now != inTheLibraryBefore + 1) {
                    fail("9 puts a card under the library and it went from "
                            + inTheLibraryBefore + " to " + now);
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] 9 puts a card back under the library");
                // That was the last card on the battlefield, and the block view is next.
                // Every check over there needs something to aim at, so another one goes down.
                playACard(client);
                advance(SETTLE);
            }
            case 169 -> {
                // Sorting a hand by what things cost. The interesting half is the arithmetic,
                // which is checked next door in milliseconds; this checks the half that only
                // exists in a running game - that the entry is reachable, that the order gets
                // to the server, and that the hand comes back in it.
                sortTheHand(client);
                advance(SETTLE);
            }
            case 170 -> {
                String wrong = theHandIsInCostOrder();
                if (wrong != null) {
                    fail("the hand was sorted by cost and " + wrong);
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] the hand came back cheapest first");
                shoot(client, "54e-hand-sorted");
                advance(SETTLE / 2);
            }
            case 171 -> {
                // A Forest goes back into the deck first. By this point the library is down
                // to its last card or two - the run has drawn, milled and discarded its way
                // through it - and a fetch out of an empty deck proves only that the deck is
                // empty. Putting one back and taking it out again tests the property that
                // matters either way: it has to come from in there.
                putAForestBackInTheDeck(client);
                advance(SETTLE);
            }
            case 172 -> {
                // A basic land out of the deck. Two questions in a row, which is the
                // part worth photographing: the second one has to name the land, because by
                // then the first question has gone and "How many?" on its own is a question
                // about something the player can no longer see.
                askForABasicLand(client);
                advance(SETTLE / 2);
            }
            case 173 -> {
                expectScreen(client, "choosing a basic land", ChoiceScreen.class);
                shoot(client, "54f-which-basic");
                press(client, "Forest");
                advance(SETTLE / 2);
            }
            case 174 -> {
                expectScreen(client, "choosing how many lands", AmountScreen.class);
                shoot(client, "54g-how-many-basics");
                // Answered for real. It comes out of the deck rather than off Scryfall, so
                // this run can go through with it without a network - and going through with
                // it is the only way to prove that is where it came from.
                inTheLibraryBefore = countIn(Zone.LIBRARY);
                onTheBattlefieldBefore = countIn(Zone.BATTLEFIELD);
                press(client, "1");
                advance(SETTLE);
            }
            case 175 -> {
                expectScreen(client, "back from fetching a land", TableScreen.class);
                int library = countIn(Zone.LIBRARY);
                int battlefield = countIn(Zone.BATTLEFIELD);
                if (library != inTheLibraryBefore - 1) {
                    fail("a Forest was fetched and the library went from "
                            + inTheLibraryBefore + " to " + library
                            + " - it has to come out of the deck, not out of the ether");
                    advance(SETTLE / 2);
                    return;
                }
                if (battlefield != onTheBattlefieldBefore + 1) {
                    fail("a Forest was fetched and the battlefield went from "
                            + onTheBattlefieldBefore + " to " + battlefield);
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] a basic land came out of the deck, not out of nowhere");
                advance(SETTLE / 2);
            }
            case 176 -> {
                // Loyalty. A fresh card, because the one the earlier steps used has a written
                // power and toughness on it and the corner only holds one number - which is
                // the rule worth checking as much as the counter is.
                playACard(client);
                advance(SETTLE);
            }
            case 177 -> {
                putLoyaltyOn(client, LOYALTY_PUT_ON);
                advance(SETTLE);
            }
            case 178 -> {
                if (loyaltyNow() != LOYALTY_PUT_ON) {
                    fail(LOYALTY_PUT_ON + " loyalty went on and the board says " + loyaltyNow());
                    advance(SETTLE / 2);
                    return;
                }
                // The menu rows appear on anything already carrying loyalty, so a card that
                // has some can be nudged without opening the counters panel. Opened here and
                // pressed a step later, because a menu opened and photographed in the same
                // step is photographed from the frame before it existed.
                if (!theCardOffers(client, "Loyalty +1")) {
                    fail("a card with loyalty on it offers no way to add more");
                    advance(SETTLE / 2);
                    return;
                }
                advance(SETTLE / 2);
            }
            case 179 -> {
                shoot(client, "54h-loyalty");
                // The menu is still the one step 164 opened. Right-clicking again to reopen
                // it lands on the menu rather than on the card, which is what a player would
                // find too - so the run does what a player does and presses the row.
                if (!(client.screen instanceof TableScreen board)
                        || !board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.loyalty_up").getString())) {
                    fail("the card's menu lost its Loyalty +1 between opening and pressing");
                }
                advance(SETTLE);
            }
            case 180 -> {
                if (loyaltyNow() != LOYALTY_PUT_ON + 1) {
                    fail("Loyalty +1 was pressed and the board says " + loyaltyNow());
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] loyalty goes up off the card's own menu");
                // With the menu gone, so the number in the corner is the thing being looked
                // at rather than the thing behind a menu.
                lookAwayFromTheCards(client);
                advance(SETTLE);
            }
            case 181 -> {
                shoot(client, "54i-loyalty-on-the-card");
                advance(SETTLE / 2);
            }
            // The tokens a card prints, on the card's own menu. Scryfall knows what a card
            // makes; before this, making a Goblin meant opening a screen, spelling "Goblin"
            // and waiting on a lookup that a typo turned into nothing at all.
            case 182 -> {
                playTheCardThatMakesAToken(client);
                advance(SETTLE * 2);
            }
            case 183 -> {
                if (maker == null || findOnTheBattlefield(maker).isEmpty()) {
                    fail("the card that makes " + THE_TOKEN_IT_MAKES
                            + "s never reached the battlefield");
                    advance(SETTLE / 2);
                    return;
                }
                // Opened here and pressed a step later, so the picture is of a menu that
                // exists rather than of the frame before it did.
                if (openTheCardMenuOn(client, maker, "Make a " + THE_TOKEN_IT_MAKES) == null) {
                    fail(MAKES_A_TOKEN + " prints a " + THE_TOKEN_IT_MAKES
                            + " and its menu offers no way to make one");
                    advance(SETTLE / 2);
                    return;
                }
                advance(SETTLE / 2);
            }
            case 184 -> {
                shoot(client, "54j-the-token-this-card-makes");
                onTheBattlefieldBeforeTheToken = countIn(Zone.BATTLEFIELD);
                if (!(client.screen instanceof TableScreen board)
                        || !board.pressMenuEntry("Make a " + THE_TOKEN_IT_MAKES)) {
                    fail("the card's menu lost its token row between opening and pressing");
                }
                // Long enough for the row to go to the server, the server to look the token
                // up and the board to come back: this one really does leave the machine.
                advance(SETTLE * 4);
            }
            case 185 -> {
                int now = countIn(Zone.BATTLEFIELD);
                if (now != onTheBattlefieldBeforeTheToken + 1) {
                    fail("a " + THE_TOKEN_IT_MAKES + " was asked for and the battlefield went "
                            + "from " + onTheBattlefieldBeforeTheToken + " to " + now);
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] a card makes the token it prints, off its own menu");
                lookAwayFromTheCards(client);
                advance(SETTLE);
            }
            case 186 -> {
                // The pen itself. Everything written on a card so far in this run has been
                // written by sending the event, which proves the card carries it and proves
                // nothing at all about the screen a player actually types it into - and that
                // screen is now two pens sharing one body.
                ClientTableActions.send(table, new GameEvent.CardStrengthSet(
                        ClientTableState.seatAt(table).orElseThrow(), loyal, "5/5"));
                advance(SETTLE);
            }
            case 187 -> {
                if (!"5/5".equals(strengthOn(loyal))) {
                    fail("5/5 was written and the card says " + strengthOn(loyal));
                    advance(SETTLE / 2);
                    return;
                }
                openTheCardMenu(client, Component.translatable(
                        "menu.gathering.table.strength").getString());
                advance(SETTLE / 2);
            }
            case 188 -> {
                if (!(client.screen instanceof TableScreen board)
                        || !board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.strength").getString())) {
                    fail("the card's menu offers no way to set power and toughness");
                }
                advance(SETTLE);
            }
            case 189 -> {
                expectScreen(client, "the pen for power and toughness", NoteScreen.class);
                shoot(client, "54j-the-pen");
                // The other half of writing something is rubbing it out, and it has its own
                // button because selecting a line and deleting it is a pen that is easier to
                // pick up than to put down.
                press(client, "Use printed");
                advance(SETTLE);
            }
            case 190 -> {
                expectScreen(client, "back from the pen", TableScreen.class);
                if (strengthOn(loyal) != null) {
                    fail("the pen was put down and the card still says " + strengthOn(loyal));
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] the pen writes numbers on a card and rubs them out");
                advance(SETTLE / 2);
            }
            case 191 -> {
                // The user's report: "the actual table version is riddled with issues such as
                // flipping cards doesn't work". Right-clicking a card on the block had never
                // been in the run - the drag had, the buttons had, the menu had not.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 192 -> {
                if (!(client.screen instanceof TableScreen board)
                        || !(board.board() instanceof dev.gathering.core.ui.SurfaceBoard)) {
                    fail("pressing V did not put the board on the block");
                    advance(SETTLE / 2);
                    return;
                }
                // Aimed a step early so a frame is drawn with the cursor there: what the
                // board thinks is under the pointer is worked out while it draws, and a
                // right-click in the same step would be asking about the frame before.
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 193 -> {
                if (!(client.screen instanceof TableScreen board)) {
                    fail("the board went away before a card could be right-clicked on it");
                    advance(SETTLE / 2);
                    return;
                }
                if (!board.isHoveringSomething()) {
                    fail("the run could not aim at a card on the block, so this checks nothing");
                    advance(SETTLE / 2);
                    return;
                }
                faceDownWas = howManyAreFaceDown();
                int[] at = cardPoint(client);
                board.mouseClicked(at[0], at[1], 1);
                if (!board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.turn_face_down").getString())) {
                    // Both halves, because they fail differently: no menu at all means the
                    // pick missed the card, and a menu without the entry means the felt's
                    // menu opened instead of the card's.
                    fail("right-clicking a card on the block at " + at[0] + "," + at[1]
                            + " offered no way to turn it over: menu=" + board.hasAMenuOpen()
                            + ", viewer=" + (table == null ? "none"
                                    : ClientTableState.viewOf(table).map(GameView::viewer).orElse(null)));
                    advance(SETTLE / 2);
                    return;
                }
                System.out.println("[devscene] turned a card face down from its menu on the block");
                advance(SETTLE);
            }
            case 194 -> {
                int now = howManyAreFaceDown();
                if (now != faceDownWas + 1) {
                    fail("turning a card face down on the block left " + now
                            + " face down, not " + (faceDownWas + 1));
                    advance(SETTLE / 2);
                    return;
                }
                shoot(client, "55-flipped-on-the-block");
                advance(SETTLE / 2);
            }
            case 195 -> {
                // The written card, put in the graveyard and read back through the pile
                // screen. A card looked at through one screen and lying on the felt in
                // another has to be the same card.
                sendTheWrittenCardToTheGraveyard(client);
                // And back off the block. The zone slots on the real table are measured on
                // the felt rather than on the window, so a harness aiming at one with a
                // screen coordinate is aiming at nothing.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 196 -> {
                clickAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD), 0);
                advance(SETTLE);
            }
            case 197 -> {
                expectScreen(client, "a graveyard holding a written card", PileScreen.class);
                if (!theGraveyardHoldsTheWrittenCard()) {
                    fail("the card written on is not in the graveyard the screen opened");
                    advance(SETTLE / 2);
                    return;
                }
                shoot(client, "56-a-note-in-the-graveyard");
                if (client.screen != null) {
                    client.screen.onClose();
                }
                advance(SETTLE / 2);
            }
            case 198 -> {
                // "Many of the elements of the table gui phase in and out as you scroll in
                // and out." Photographed at four heights rather than reasoned about: whatever
                // comes and goes has to be visible in the pictures side by side.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                }
                advance(SETTLE);
            }
            case 199 -> {
                expectScreen(client, "the board on the block to zoom", TableScreen.class);
                // Aimed at the graveyard rather than at the middle of the window, because
                // the middle is where the camera already is: a wheel that ignored the cursor
                // would hold the middle still by accident and the check would pass for the
                // wrong reason.
                aimTheWheelAtAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                scrollTheBoard(client, 6);
                advance(SETTLE / 2);
            }
            case 200 -> {
                theWheelHeldItsPlace("after leaning all the way in");
                shoot(client, "57-zoom-1-closest");
                // Dragged here as well as at the whole-table framing, because how many blocks
                // a pixel is worth depends on the height and on nothing else: a conversion
                // that is right at one zoom is wrong at every other, which is the shape of
                // the fault. Leaned all the way in is the far end of it.
                dragTheBoard(client, 0, PAN_BY);
                advance(SETTLE / 2);
            }
            case 201 -> {
                theBoardFollowedTheHand("dragged while leaning all the way in");
                dragTheBoard(client, 0, -PAN_BY);
                advance(SETTLE / 2);
            }
            case 202 -> {
                theBoardFollowedTheHand("dragged back again");
                scrollTheBoard(client, -2);
                advance(SETTLE / 2);
            }
            case 203 -> {
                theWheelHeldItsPlace("two notches back out");
                shoot(client, "57-zoom-2");
                scrollTheBoard(client, -2);
                advance(SETTLE / 2);
            }
            case 204 -> {
                theWheelHeldItsPlace("four notches back out");
                shoot(client, "57-zoom-3");
                scrollTheBoard(client, -2);
                advance(SETTLE / 2);
            }
            case 205 -> {
                theWheelHeldItsPlace("all the way back out");
                shoot(client, "57-zoom-4-furthest");
                // And the same key the seated board has for it, on the block. Shot 26 is the
                // seated answer to "show me everything"; without this one there was no
                // picture of the in-world board's answer to compare it against, and the two
                // views are supposed to differ only in whether a point is a pixel or a place
                // on the felt.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
                }
                advance(SETTLE);
            }
            case 206 -> {
                expectScreen(client, "the whole table on the block", TableScreen.class);
                System.out.println("[devscene] camera: " + TableCameraView.report());
                theBlockFramesLikeTheScreen(client);
                shoot(client, "58-the-whole-table-on-the-block");
                // And the near end of the same range: the whole table framed, where a block
                // is worth a fraction of the pixels it is worth leaned in.
                aimTheWheelAtAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                dragTheBoard(client, 0, PAN_BY);
                advance(SETTLE / 2);
            }
            case 207 -> {
                theBoardFollowedTheHand("dragged down the whole-table view");
                dragTheBoard(client, PAN_BY, 0);
                advance(SETTLE / 2);
            }
            case 208 -> {
                theBoardFollowedTheHand("dragged across it");
                shoot(client, "59-the-board-panned");
                // Dyed with the board still open and nothing else touching the world, which
                // is the case that was broken: the color is a tint baked into the chunk's
                // mesh, and being told the block entity changed does not rebuild a mesh. The
                // two pictures either side of this are the whole check - a table that only
                // goes purple when something else happens nearby is a dye that looks broken.
                dyeTheTable(client);
                advance(SETTLE);
            }
            case 209 -> {
                shoot(client, "60-the-felt-dyed");
                // The rest of the family, stood in a row where they can be compared. A
                // cosmetic table that is the wooden one with a different texture is a recolor
                // rather than a table somebody would build with, and the only way to know
                // which of the two was built is to look at them side by side.
                standTheOtherTablesUp(client);
                advance(SETTLE);
            }
            case 210 -> {
                lookAtTheOtherTables(client);
                advance(SETTLE);
            }
            case 211 -> {
                shoot(client, "61-a-table-in-every-material");
                advance(SETTLE / 2);
            }
            case 212 -> {
                // A card thrown on the floor. Its own step because a dropped card is the one
                // state of the item nothing else photographs, and it was landing face down -
                // the model's ground transform turned it the wrong way about X, so the
                // printed side went into the dirt and the sleeve came up.
                throwACardOnTheFloor(client);
                advance(SETTLE * 2);
            }
            case 213 -> {
                // The look and the picture in two steps. A teleport is a packet: asked for and
                // photographed in the same tick, the camera in the picture is the one from
                // before it arrived, which is how the first run of this came back showing the
                // horizon and no card at all.
                lookDownAtTheFloor(client);
                advance(SETTLE * 2);
            }
            case 214 -> {
                aCardIsLyingFaceUp(client);
                shoot(client, "61a-a-card-on-the-floor");
                // Back to the board for the two verbs the server decides. A die nobody else
                // watched is a claim, so what matters is that the number reaches the log where
                // the whole table reads it - which is what the last picture is of.
                backToTheBoard(client);
                advance(SETTLE);
            }
            case 215 -> {
                openTheDiceQuestion(client);
                advance(SETTLE / 2);
            }
            case 216 -> {
                expectScreen(client, "asking which die", ChoiceScreen.class);
                shoot(client, "62-which-die");
                press(client, "d20");
                advance(SETTLE);
            }
            case 217 -> {
                flipACoin(client);
                advance(SETTLE);
            }
            case 218 -> {
                if (!logSays(client, "rolled a d20")) {
                    fail("a d20 was rolled and the log does not say so");
                }
                if (!logSays(client, "flipped a coin")) {
                    fail("a coin was flipped and the log does not say so");
                }
                // And on the felt, where the table can see it without opening anything. The
                // log line alone is what the players reported as not enough: a result nobody
                // saw is as good as a claim, which is the whole reason the server rolls.
                shoot(client, "62a-the-coin-announced");
                if (ClientTableRolls.showingAt(table, System.currentTimeMillis()).isEmpty()) {
                    fail("a coin was flipped and nothing was announced over the felt");
                }
                // Opened a step before it is photographed. A screenshot asked for in the same
                // step catches the frame that was already drawn, so the log would be open and
                // the picture would not show it - and a roll nobody can read is the thing
                // this whole verb exists to stop.
                openTheLog(client);
                advance(SETTLE);
            }
            case 219 -> {
                shoot(client, "63-a-roll-and-a-flip-in-the-log");
                // The log is over the board from here on, and the next thing to look at is
                // the board. Closed rather than left up, because a picture of an emblem with
                // the log across it is a picture of the log.
                openTheLog(client);
                advance(SETTLE / 2);
            }
            case 220 -> {
                // Blank stock: the mod's answer to every table state it has no feature for.
                // Worth photographing rather than only asserting, because the whole of what
                // an emblem is is how it looks - there is no art to fall back on.
                openThePaperQuestion(client, "make_emblem", "an emblem");
                advance(SETTLE / 2);
            }
            case 221 -> {
                expectScreen(client, "asking what the emblem says", TextPromptScreen.class);
                shoot(client, "64-what-does-the-emblem-say");
                typeInto(client, "Creatures you control get +1/+1");
                press(client, "OK");
                advance(SETTLE);
            }
            case 222 -> {
                if (!logSays(client, "Creatures you control get +1/+1")) {
                    fail("an emblem was made and the log does not say what it says");
                }
                openThePaperQuestion(client, "note_card", "a blank card");
                advance(SETTLE / 2);
            }
            case 223 -> {
                expectScreen(client, "asking what the blank card says", TextPromptScreen.class);
                typeInto(client, "Dev has the monarch");
                press(client, "OK");
                advance(SETTLE);
            }
            case 224 -> {
                if (!logSays(client, "Dev has the monarch")) {
                    fail("a blank card was written and the log does not say what it says");
                }
                shoot(client, "65-an-emblem-and-a-written-card");
                advance(SETTLE / 2);
            }
            case 225 -> {
                // Turning your hand round. The one feature that deliberately opens a hidden
                // zone, so the picture is worth as much as the assertion: what has to be true
                // is that the player doing it can see, on their own screen, that it is open.
                openTheHandQuestion(client);
                advance(SETTLE / 2);
            }
            case 226 -> {
                expectScreen(client, "asking who can see my hand", ChoiceScreen.class);
                shoot(client, "66-who-can-see-my-hand");
                press(client, net.minecraft.network.chat.Component
                        .translatable("screen.gathering.hand.everybody").getString());
                advance(SETTLE);
            }
            case 227 -> {
                if (!logSays(client, "face up to the table")) {
                    fail("a hand was turned face up and the log does not say so");
                }
                shoot(client, "67-my-hand-is-face-up");
                takeMyHandBack(client);
                advance(SETTLE);
            }
            case 228 -> {
                if (!logSays(client, "took their hand back")) {
                    fail("a hand was taken back and the log does not say so");
                }
                // And the other half of a table: talking at it. Typed with the player's own
                // chat key, into the board rather than into a screen that would take the
                // board away - see TableScreen.renderTalk.
                sayToTheTable(client, "attacking you with everything");
                advance(SETTLE);
            }
            case 229 -> {
                if (theTableHasNotHeard(client, "attacking you with everything")) {
                    fail("something was said to the table and the table did not hear it");
                }
                shoot(client, "68-said-at-the-table");
                advance(SETTLE / 2);
            }
            case 230 -> {
                // A dungeon starts outside the game, so something has to bring it in. Only
                // the question is photographed: the card itself is a Scryfall lookup and this
                // run has no network, exactly like the token search two hundred steps back.
                pressTableEntry(client, "bring_in_dungeon", "bring in a dungeon");
                advance(SETTLE / 2);
            }
            case 231 -> {
                expectScreen(client, "asking which dungeon", ChoiceScreen.class);
                shoot(client, "69-which-dungeon");
                if (client.screen != null) {
                    client.screen.onClose();
                }
                advance(SETTLE / 2);
            }
            case 232 -> {
                // Out of the board and into the world, holding a foil, to look at the one
                // thing in the mod that is drawn rather than fetched. There is no foil scan
                // on Scryfall and never will be: a foil is the same picture doing something
                // as it moves, so the whole feature is the movement.
                leaveTheBoard(client);
                holdAFoil(client);
                advance(SETTLE);
            }
            case 233 -> {
                if (theresNoCardInHand(client)) {
                    fail("nothing ended up in hand to read");
                }
                // The card as it is really held, before anything is opened over it. Reported
                // as a foil in the hand drawing flat: the sheen existed only in the reading
                // overlay, so out here it was a picture of a foil rather than a foil.
                shoot(client, "69a-a-foil-in-the-hand");
                CardZoomOverlay.bindKeyState(() -> true);
                advance(SETTLE);
            }
            case 234 -> {
                shoot(client, "70-reading-a-foil");
                // Turned both ways, so the three pictures are one card with the light in
                // three places. A sheen that looked identical in all of them would be a sheen
                // that is not moving, which is the only way this can fail quietly - and the
                // far ends of the turn are where the shine would leave the card if the
                // scissor were not holding it.
                turnTheHead(client, 20f);
                advance(SETTLE);
            }
            case 235 -> {
                if (CardTilt.yaw() < 1f) {
                    fail("the head turned one way and the card did not: yaw " + CardTilt.yaw());
                }
                shoot(client, "71-the-foil-turned");
                // Back the other way and tipped down as well, because a foil that only
                // answers a sideways turn is a foil that does nothing the first time somebody
                // tips it up and down - which is the first thing anybody tries.
                turnTheHead(client, -40f);
                tipTheHead(client, 18f);
                advance(SETTLE);
            }
            case 236 -> {
                if (CardTilt.yaw() > -1f) {
                    fail("the head turned back and the card did not: yaw " + CardTilt.yaw());
                }
                if (Math.abs(CardTilt.pitch()) < 0.5f) {
                    fail("the head tipped and the card did not: pitch " + CardTilt.pitch());
                }
                if (Math.abs(CardTilt.shineDown()) < 0.05f) {
                    fail("the card tipped and the shine did not move down it");
                }
                shoot(client, "72-the-foil-turned-back");
                CardZoomOverlay.bindKeyState(() -> false);
                backToTheBoard(client);
                advance(SETTLE);
            }
            case 237 -> {
                // The planar die, which is not a d6: four blanks, a chaos and a planeswalk.
                // Rolled through the same door every other die goes through, so what is being
                // checked is that the face reaches the log at all - a symbol only the roller
                // saw is the one thing none of these verbs may be.
                rollThePlanarDie(client);
                advance(SETTLE);
            }
            case 238 -> {
                if (!logSays(client, "planar die")) {
                    fail("the planar die was rolled and the log does not say so");
                }
                openTheCollection(client);
                advance(SETTLE);
            }
            case 239 -> {
                expectScreen(client, "opening a collection to count its sets", CollectionScreen.class);
                // Pressed rather than assumed, like every other button on this screen: one
                // that fits on a wide window and hides under its neighbor on a narrow one is
                // a button nobody can find.
                press(client, net.minecraft.network.chat.Component
                        .translatable("screen.gathering.collection.sets").getString());
                // And then answered here, because working out how much of a set is here is a
                // question for Scryfall and this run has no network - the same reason the
                // token search two hundred steps back backs out of its own question. What is
                // being checked is the screen, which is all of it that is ours.
                howMuchOfEachSet(client);
                advance(SETTLE);
            }
            case 240 -> {
                expectScreen(client, "how much of each set is here", SetProgressScreen.class);
                shoot(client, "73-how-much-of-each-set");
                hover(client, new int[] {client.getWindow().getGuiScaledWidth() / 2, SET_ROW_Y});
                advance(SETTLE / 2);
            }
            case 241 -> {
                // Pressing a set takes the collection down to it. Without that the screen
                // shows you where you are and then makes you go and find it again.
                //
                // Which set that is depends on what the server answered, and this run cannot
                // know: a machine with a network gets real sets and one without gets the four
                // fed above. So the row under the cursor is asked for and checked against
                // afterwards, which is the question that is actually being asked.
                if (client.screen instanceof SetProgressScreen sets) {
                    pressedSet = sets.hoveredCode();
                    if (pressedSet.isEmpty()) {
                        fail("no set row was under the cursor to press");
                    }
                    sets.mouseClicked(
                            client.getWindow().getGuiScaledWidth() / 2.0, SET_ROW_Y, 0);
                    // And answered here, for the reason the set list itself is answered here:
                    // what a set contains is a question for Scryfall, and what is being
                    // checked is the screen and the wire it arrives over.
                    whatIsStillMissing(client, pressedSet);
                }
                advance(SETTLE);
            }
            case 242 -> {
                expectScreen(client, "the cards a set is still missing", MissingCardsScreen.class);
                shoot(client, "74-what-is-still-missing");
                if (client.screen instanceof MissingCardsScreen missing && missing.total() == 0) {
                    fail("the missing list opened saying nothing is missing");
                }
                // One of them onto the wants list, which is the whole reason to be looking at
                // this list rather than reading it once and forgetting it.
                wantACardFromTheList(client);
                advance(SETTLE);
            }
            case 243 -> {
                theWantedCardCameBack(client);
                shoot(client, "74a-one-i-am-after");
                // Back to the set list, which is where a sub-screen of it goes.
                if (client.screen != null) {
                    client.screen.onClose();
                }
                advance(SETTLE);
            }
            case 244 -> {
                expectScreen(client, "back on the set list", SetProgressScreen.class);
                // The other half of the row: right for what you have rather than what you
                // do not. Both halves are pressed, because a row that answers two questions
                // is a row where one of them can quietly stop working.
                if (client.screen instanceof SetProgressScreen sets) {
                    // Read again rather than trusting what the left-click recorded. The set
                    // list fills in from the network while the scene is off in the missing
                    // cards screen, so the row at this height can be a different set by the
                    // time the right-click lands - and then the check below compares a set
                    // nobody pressed against one that was. What is being tested is that
                    // right-clicking a row filters to that row's set, so the row has to be
                    // asked at the moment it is pressed.
                    pressedSet = sets.hoveredCode();
                    if (pressedSet.isEmpty()) {
                        fail("no set row was under the cursor to right-click");
                    }
                    sets.mouseClicked(
                            client.getWindow().getGuiScaledWidth() / 2.0, SET_ROW_Y, 1);
                }
                advance(SETTLE);
            }
            case 245 -> {
                expectScreen(client, "back in the collection, filtered", CollectionScreen.class);
                if (client.screen instanceof CollectionScreen collection
                        && !pressedSet.equals(collection.query().setCode())) {
                    fail("right-clicking " + pressedSet + " narrowed the collection to '"
                            + collection.query().setCode() + "'");
                }
                shoot(client, "74b-only-that-set");
                advance(SETTLE / 2);
            }
            // A look is put on in one step and photographed in the next, the way every other
            // change here is: a picture is taken of the frame that has already been drawn, so
            // a step that changes something and photographs it in the same breath photographs
            // what was there before. Three looks came out labeled as each other.
            case 246 -> {
                wearTheLook(client, "gathering:retro");
                advance(SETTLE / 2);
            }
            case 247 -> {
                shoot(client, "75-the-retro-look");
                wearTheLook(client, "gathering:future");
                advance(SETTLE / 2);
            }
            case 248 -> {
                shoot(client, "76-the-future-sight-look");
                wearTheLook(client, "gathering:basic");
                advance(SETTLE / 2);
            }
            case 249 -> {
                shoot(client, "77-back-to-basic");
                client.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(
                        client.screen, client, client.options));
                advance(SETTLE);
            }
            case 250 -> {
                expectScreen(client, "opening the game's video settings",
                        net.minecraft.client.gui.screens.options.VideoSettingsScreen.class);
                // Scrolled to where the row actually is, which is the foot of the list: mod
                // settings go after the game's own. A picture of the top of the screen was a
                // picture that proved nothing about the row it was taken for.
                scrollToTheFoot(client);
                advance(SETTLE / 2);
            }
            case 251 -> {
                shoot(client, "78-the-look-in-video-settings");
                // Pressed rather than assumed. This row is put into a list vanilla built, and
                // a mod that adds a widget to somebody else's screen finds out it has stopped
                // working by nobody ever seeing it.
                pressTheLookRow(client);
                advance(SETTLE);
            }
            case 252 -> {
                if (GuiThemes.active().id().toString().equals("gathering:basic")) {
                    fail("the look row in video settings was pressed and the look did not change");
                }
                shoot(client, "79-a-look-picked-from-the-options");
                wearTheLook(client, "gathering:basic");
                if (client.screen != null) {
                    client.screen.onClose();
                }
                advance(SETTLE / 2);
            }
            case 253 -> {
                aCardWithAHistoryInHand(client);
                advance(SETTLE);
            }
            case 254 -> {
                if (!CardZoomOverlay.isActive()) {
                    fail("the read-a-card overlay did not come up over a card with a history");
                }
                shoot(client, "80-where-it-has-been");
                CardZoomOverlay.bindKeyState(() -> false);
                advance(SETTLE / 2);
            }
            // A game is played out and ended for real, rather than a list of replays being
            // faked: the whole feature is the round trip - a session written to disk, read
            // back, folded to a board and sent as a picture - and a fake would photograph
            // none of it.
            // More named counters on one card than the panel has room for. The seventh used
            // to be drawn nowhere and given no buttons, so a player who had put one on could
            // not see it, change it, or take it off.
            case 255 -> {
                aCardCoveredInCounters(client);
                advance(SETTLE * 2);
            }
            case 256 -> {
                expectScreen(client, "a card with more counters than fit", CountersScreen.class);
                everyCounterIsReachable(client);
                shoot(client, "85-more-counters-than-fit");
                advance(SETTLE / 2);
            }
            case 257 -> {
                shoot(client, "85a-scrolled-to-the-last-counter");
                if (client.screen != null) {
                    client.screen.onClose();
                }
                advance(SETTLE / 2);
            }
            case 258 -> {
                aGameWorthWatchingBack(client);
                advance(SETTLE * 2);
            }
            case 259 -> {
                expectScreen(client, "the list of finished games", ReplayListScreen.class);
                if (client.screen instanceof ReplayListScreen replays && replays.listed() < 1) {
                    fail("a game was played out and ended, and the shelf came back empty");
                }
                shoot(client, "81-games-that-finished");
                watchTheNewestGame(client);
                advance(SETTLE * 2);
            }
            case 260 -> {
                expectAReplay(client, "watching the game back");
                // Wound to the end, which is the board as the table was cleared - and the one
                // frame where a hand full of cards proves the disclosure works.
                ClientReplay.scrubTo(ClientReplay.steps());
                advance(SETTLE * 2);
            }
            case 261 -> {
                expectAReplay(client, "wound to the end of the game");
                aReplayShowsWhatWasHidden(client);
                shoot(client, "82-the-whole-game-back");
                // And wound back to the opening, so the scrubber is photographed at both ends
                // rather than only at the one it happened to stop at.
                ClientReplay.scrubTo(0);
                advance(SETTLE * 2);
            }
            case 262 -> {
                expectAReplay(client, "wound back to the start");
                if (ClientReplay.step() != 0) {
                    fail("the scrubber was dragged home and stopped at step " + ClientReplay.step());
                }
                shoot(client, "83-back-to-the-start");
                // The one thing a watcher must not be able to do. Pressed rather than
                // reasoned about: the guards are spread over four handlers, and a gesture
                // that got through one of them would be a finished game being played on.
                aWatcherCannotTouchTheBoard(client);
                advance(SETTLE);
            }
            case 263 -> {
                expectAReplay(client, "still watching after a watcher tried to play");
                if (ClientReplay.step() != 0) {
                    fail("a click on the felt of a replay moved the game to step "
                            + ClientReplay.step());
                }
                // The help a watcher gets, which is a different list from a player's: the
                // panel that teaches the table's verbs would be teaching a watcher things
                // they are not allowed to do.
                client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                advance(SETTLE / 2);
            }
            case 264 -> {
                shoot(client, "84-what-a-watcher-can-do");
                if (client.screen != null) {
                    client.screen.onClose();
                }
                advance(SETTLE / 2);
            }
            // A shelf of decks. The whole reason a box has a color is that this row is
            // otherwise eight identical objects with the name a hover away.
            case 265 -> {
                aShelfOfDecks(client);
                advance(SETTLE * 2);
            }
            case 266 -> {
                everyDeckIsItsOwnColor(client);
                shoot(client, "86-a-shelf-of-decks");
                advance(SETTLE / 2);
            }
            // Putting the cards you are carrying into the deck you are holding. The gesture
            // it replaces is a right-click per stack, which is a booster box's worth of
            // right-clicks - the one thing about deckbuilding that took all evening.
            case 267 -> {
                looseCardsToPickFrom(client);
                advance(SETTLE);
            }
            case 268 -> {
                openTheDeckScreen(client);
                advance(SETTLE);
            }
            case 269 -> {
                expectScreen(client, "opening a deck to add to it", DeckContentsScreen.class);
                press(client, Component.translatable("screen.gathering.deck.gather").getString());
                advance(SETTLE);
            }
            case 270 -> {
                expectScreen(client, "picking cards out of my own pockets",
                        DeckBuilderScreen.class);
                theBuilderIsOverMyPockets(client);
                everyCardStaysInItsBox(client);
                oneShiftClickTakesEveryCopy(client);
                advance(SETTLE);
            }
            case 271 -> {
                // A step after the click, which is the convention here: a shot asked for in
                // the same step catches the frame that was drawn before it, and the whole
                // point of this picture is the column on the right with something in it.
                shoot(client, "87-cards-you-are-carrying");
                // Closed rather than finished, so the cards picked here are still loose in
                // the inventory - which is what the next two steps are about. The deck goes
                // away with it, because a hand holding one cannot crouch at a collection at
                // all and the screen rightly offers that gesture instead.
                cancellingGoesBack(client, "the deck it was opened from",
                        DeckContentsScreen.class);
                client.setScreen(null);
                putTheDeckAway(client);
                openTheCollection(client);
                advance(SETTLE);
            }
            case 272 -> {
                expectScreen(client, "a collection opened while carrying loose cards",
                        CollectionScreen.class);
                theSweepIsOfferedToSomebodyCarryingCards(client);
                advance(SETTLE / 2);
            }
            case 273 -> {
                shoot(client, "88-put-every-loose-card-away");
                // The color filter, which is six mana orbs rather than six letters. Pressed
                // by the name behind the orb, which is the point of the message being a
                // sentence: a button whose whole label is a symbol cannot be found by one.
                press(client, Component.translatable(
                        "screen.gathering.collection.color_u").getString());
                advance(SETTLE);
            }
            case 274 -> {
                aColorFilterIsOn(client, "U");
                advance(SETTLE / 2);
            }
            case 275 -> {
                shoot(client, "89-filtered-to-one-color");
                client.setScreen(null);
                advance(SETTLE / 2);
            }
            // The shopkeeper, who is the one thing in the mod with a face. Everything above
            // this is a screen; a villager is a texture on a model in a world, and the only
            // way to know whether it reads as a card-shop keeper rather than a smudge is to
            // stand in front of one.
            case 276 -> {
                theShopkeepers(client);
                advance(SETTLE * 3);
            }
            case 277 -> {
                shoot(client, "90-the-shopkeeper-and-what-becomes-of-him");
                closerToTheShopkeeper(client, -1.0);
                advance(SETTLE * 2);
            }
            case 278 -> {
                shoot(client, "91-the-shopkeeper");
                closerToTheShopkeeper(client, 1.0);
                advance(SETTLE * 2);
            }
            case 279 -> {
                shoot(client, "92-the-zombie-shopkeeper");
                advance(SETTLE / 2);
            }
            default -> {
                // A step number nobody wrote is not the end of the scene, it is a hole in the
                // middle of it. Java's switch cannot tell the two apart, so falling off the
                // end silently reported a clean run that had photographed a third of the mod.
                // Anything short of the last step is the hole, and it fails here.
                if (step < LAST_STEP) {
                    fail("the scene has no step " + step + ", so it stopped there instead of"
                            + " running to " + LAST_STEP + "; a case number was skipped");
                }
                if (GALLERY && step <= lastStep()) {
                    gallery(client, step - GALLERY_FIRST);
                    return;
                }
                finish(client, "done");
            }
        }
    }

    /**
     * Presses the mod's row in the game's own video settings.
     * <p>Not through {@link #press}, which looks at a screen's own children: this row lives
     * inside the scrolling list of options, which is one child holding all of them. Walking
     * into it is the point - the row is only useful if it is really in that list, where a
     * player scrolling through video settings will come across it.
     */
    private static void pressTheLookRow(Minecraft client) {
        String wanted = net.minecraft.network.chat.Component
                .translatable("options.gathering.look").getString();
        if (client.screen == null) {
            fail("there was no screen to look for the look row on");
            return;
        }
        AbstractWidget row = widgetSaying(client.screen.children(), wanted, 0);
        if (row == null) {
            fail("the game's video settings offer no way to change the mod's look");
            return;
        }
        String was = row.getMessage().getString();
        row.onClick(row.getX() + row.getWidth() / 2.0, row.getY() + row.getHeight() / 2.0);
        System.out.println("[devscene] the look row said '" + was + "' and now says '"
                + row.getMessage().getString() + "'");
    }

    /** Winds a screen's scrolling list all the way down, so what is at its foot is on show. */
    private static void scrollToTheFoot(Minecraft client) {
        if (client.screen == null) {
            return;
        }
        for (GuiEventListener child : client.screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> list) {
                list.setClampedScrollAmount(Double.MAX_VALUE);
                return;
            }
        }
        fail("the video settings had no scrolling list of options in them");
    }

    /** How far into a screen's furniture to look for a widget. Deep enough for a list row. */
    private static final int NESTED = 4;

    /**
     * The first widget under here whose label starts with this, however deeply it is nested.
     * <p>Searched rather than reached for, because the row is put inside the scrolling list
     * vanilla built and that list is one child of the screen holding all of the options. What
     * is being checked is that a player scrolling through video settings comes across it,
     * which is a question about where it ended up rather than about what was added.
     */
    private static AbstractWidget widgetSaying(
            java.util.List<? extends GuiEventListener> among, String starting, int depth) {
        for (GuiEventListener child : among) {
            if (child instanceof AbstractWidget widget
                    && widget.getMessage().getString().startsWith(starting)) {
                return widget;
            }
            if (depth < NESTED
                    && child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler holder) {
                AbstractWidget found = widgetSaying(holder.children(), starting, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Puts one of the mod's looks on, and checks the art really came from it.
     * <p>Photographed on the collection screen, which is a panel, a backdrop, striped rows
     * and a scrollbar - five of the elements a theme owns, in one picture. The check is that
     * {@link GatheringSprites#of} resolves into that theme's folder: a theme that was chosen
     * and then silently drawn from the default one would look right in three screenshots and
     * be broken for every pack anybody made.
     */
    private static void wearTheLook(Minecraft client, String wanted) {
        GuiTheme found = GuiThemes.byId(wanted);
        if (!found.id().toString().equals(wanted)) {
            fail("no theme is installed called " + wanted + "; the mod has "
                    + GuiThemes.all());
            return;
        }
        GuiThemes.wear(found);
        String from = GatheringSprites.of(GatheringSprites.Element.PANEL).toString();
        if (!from.startsWith(found.sprites() + "/")) {
            fail("the " + wanted + " look draws its panel from " + from);
        }
    }

    /**
     * Presses a scried card and drags it, without letting go.
     * <p>Left held down on purpose: the next step photographs the screen mid-drag, and what
     * is being checked is whether the player can see where the card would land while they
     * are still deciding. Let go first and the picture is of the result, which was never the
     * part that was missing.
     */
    private static void startDraggingAScriedCard(Minecraft client) {
        if (!(client.screen instanceof PileScreen pile)) {
            fail("there was no scry to drag a card in");
            return;
        }
        Rect first = pile.slotOfCard(0);
        Rect last = pile.slotOfCard(2);
        if (first.isEmpty() || last.isEmpty()) {
            fail("the scry had no row of cards to drag along");
            return;
        }
        int fromX = (int) first.centerX();
        int fromY = (int) first.centerY();
        int toX = (int) last.centerX();
        pile.mouseClicked(fromX, fromY, 0);
        pile.mouseDragged(toX, fromY, 0, toX - fromX, 0);
        draggingTo = new int[] {toX, fromY};
        System.out.println("[devscene] dragging the first scried card along to " + toX);
    }

    /** Where the held drag is, so the step after can let go in the same place. */
    private static int[] draggingTo;

    /** Lets the dragged card go where it was being dragged to. */
    private static void letTheScriedCardGo(Minecraft client) {
        if (!(client.screen instanceof PileScreen pile) || draggingTo == null) {
            fail("there was no held drag to let go of");
            return;
        }
        pile.mouseReleased(draggingTo[0], draggingTo[1], 0);
        System.out.println("[devscene] let the scried card go");
    }

    /** Turns the wheel where it was aimed, or over the middle of the board, in either direction. */
    private static void scrollTheBoard(Minecraft client, int notches) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to zoom");
            return;
        }
        int x = wheelAt == null ? client.getWindow().getGuiScaledWidth() / 2 : wheelAt[0];
        int y = wheelAt == null ? client.getWindow().getGuiScaledHeight() / 2 : wheelAt[1];
        for (int turn = 0; turn < Math.abs(notches); turn++) {
            board.mouseScrolled(x, y, 0, Math.signum(notches));
        }
        System.out.println("[devscene] turned the wheel " + notches + " on the block");
    }

    /** The pixel the wheel is being turned over, and what the felt had under it to begin with. */
    private static int[] wheelAt;

    private static double[] wheelWasOver;

    /**
     * Points the wheel at one of this seat's zones and remembers what is under it.
     * <p>Off the middle of the window on purpose - see the caller.
     */
    private static void aimTheWheelAtAZone(Minecraft client, int index) {
        wheelAt = null;
        wheelWasOver = null;
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty()) {
            fail("no zone to aim the wheel at");
            return;
        }
        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        double[] pixel = TablePointer.onScreen(top, zone.centerX(), zone.centerY())
                .orElse(null);
        if (pixel == null) {
            fail("the zone the wheel was aimed at is not on the window");
            return;
        }
        wheelAt = new int[] {(int) Math.round(pixel[0]), (int) Math.round(pixel[1])};
        wheelWasOver = new double[] {zone.centerX(), zone.centerY()};
        System.out.println("[devscene] the wheel is aimed at " + wheelAt[0] + "," + wheelAt[1]);
    }

    /**
     * Checks the felt did not run out from under the cursor while the wheel turned.
     * <p>A card is a thousand surface units wide. Holding the anchor to a third of that
     * across a whole sweep is a board that stays where the player put it; the same sweep
     * before the wheel was anchored walked the graveyard clean off the window.
     */
    private static void theWheelHeldItsPlace(String when) {
        if (wheelAt == null || wheelWasOver == null) {
            fail("the wheel was never aimed, so nothing could be checked " + when);
            return;
        }
        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        TableTop.Spot now = TablePointer.at(top, wheelAt[0], wheelAt[1]).orElse(null);
        if (now == null) {
            fail("the wheel turned the felt out from under the cursor entirely " + when);
            return;
        }
        double drift = Math.hypot(now.x() - wheelWasOver[0], now.y() - wheelWasOver[1]);
        System.out.println("[devscene] the felt under the wheel moved "
                + Math.round(drift) + " units " + when);
        if (drift > 300) {
            fail("the wheel moved the felt " + Math.round(drift)
                    + " units out from under the cursor " + when);
        }
    }

    /** Turns the written card back up and bins it, so a pile screen can be opened over it. */
    private static void sendTheWrittenCardToTheGraveyard(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || written == null) {
            fail("there was no written card to bin");
            return;
        }
        ClientTableActions.send(table, new GameEvent.CardFacingSet(
                me, written, dev.gathering.core.game.Facing.FACE_UP));
        ClientTableActions.send(table, new GameEvent.CardMoved(
                me, written, dev.gathering.core.game.ZoneRef.of(me, Zone.GRAVEYARD),
                dev.gathering.core.game.Placement.TOP));
        System.out.println("[devscene] binned the written card");
    }

    /** Whether the note reached the client on the card now sitting in the graveyard. */
    private static boolean theGraveyardHoldsTheWrittenCard() {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null || written == null) {
            return false;
        }
        for (CardView card : view.seat(me).zone(Zone.GRAVEYARD).cards()) {
            if (card instanceof CardView.Visible visible && visible.id().equals(written)) {
                return card.writtenOn().isPresent();
            }
        }
        return false;
    }

    /** How many of this player's permanents were face down before the flip. */
    private static int faceDownWas;

    private static int howManyAreFaceDown() {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            return -1;
        }
        int down = 0;
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card.isFaceDown()) {
                down++;
            }
        }
        return down;
    }

    /** Writes on whatever is on this player's battlefield, the way the menu does. */
    private static void writeOnACard(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no board to write on");
            return;
        }
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible) {
                written = visible.id();
                ClientTableActions.send(table, new GameEvent.CardNoted(
                        me, visible.id(), "flying until end of turn"));
                System.out.println("[devscene] wrote on " + visible.id());
                return;
            }
        }
        fail("there was nothing on the battlefield to write on");
    }

    /** Puts the cursor on the card that was written on, so the note can be read off it. */
    private static void restOnTheWrittenCard(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null || written == null
                || !(client.screen instanceof TableScreen board)) {
            fail("there was no written card to rest on");
            return;
        }
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible && visible.id().equals(written)) {
                TablePosition where = card.placedAt().orElse(null);
                if (where == null) {
                    fail("the written card is not on the table");
                    return;
                }
                Rect at = board.board().rectOf(me, where);
                hover(client, new int[] {(int) at.centerX(), (int) at.centerY()});
                return;
            }
        }
        fail("the written card left the battlefield");
    }

    /**
     * Writes a power and toughness on the same card the pen wrote on.
     * <p>The same card on purpose. A note across the top and numbers in the corner are the
     * two things a player writes, and the one arrangement worth photographing is both at
     * once - which is also the one where they could be drawn over each other.
     */
    private static void writeStrengthOnACard(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || written == null) {
            fail("there was no written card to put numbers on");
            return;
        }
        // Two digits either side rather than one. A 6/6 fits any card at any zoom, so the
        // badge that has to hold it was never asked a hard question - and "the box does not
        // render large enough to contain the numbers" is a question about the wide case.
        ClientTableActions.send(table, new GameEvent.CardStrengthSet(me, written, "12/12"));
        System.out.println("[devscene] wrote 12/12 on " + written);
    }

    /** What the board says is written in the corner of that card, or null. */
    private static String whatStrengthIsOnTheCard() {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null || written == null) {
            return null;
        }
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible && visible.id().equals(written)) {
                return visible.writtenStrength().orElse(null);
            }
        }
        return null;
    }

    /**
     * Asks the table to throw away a card, without saying which.
     * <p>Off the player's own menu, the way a player reaches it - not by sending the payload
     * straight, which would prove the handler works and leave an entry nobody can find still
     * unreachable.
     */
    private static void discardAtRandom(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to discard from");
            return;
        }
        if (!openTheTableMenu(client, board, net.minecraft.network.chat.Component.translatable("menu.gathering.table.discard_at_random").getString())) {
            fail("the table menu offers no way to discard at random");
            return;
        }
        board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.discard_at_random").getString());
    }

    /**
     * Asks the table to put this hand in order of cost, off the player's own menu.
     */
    private static void sortTheHand(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to sort a hand on");
            return;
        }
        if (!openTheTableMenu(client, board, net.minecraft.network.chat.Component.translatable("menu.gathering.table.sort_hand").getString())) {
            fail("the table menu offers no way to sort a hand");
            return;
        }
        board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.sort_hand").getString());
    }

    /**
     * Whether the hand really is cheapest first, or what is wrong with it.
     * <p>Read off the board the way a player reads it, and priced the same way the sort
     * priced it - so this fails if the order never arrived, not if the two disagree about
     * what a hybrid symbol costs. That question is answered by ManaValueTest.
     */
    private static String theHandIsInCostOrder() {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            return "there was no board to read the hand off";
        }
        int last = Integer.MIN_VALUE;
        int seen = 0;
        for (CardView card : view.seat(me).zone(Zone.HAND).cards()) {
            if (!(card instanceof CardView.Visible visible)) {
                continue;
            }
            java.util.Optional<dev.gathering.network.CardSummary> summary = ClientCardCache.get()
                    .summary(dev.gathering.item.CardComponent.of(visible.identity()));
            if (summary.isEmpty()) {
                continue;
            }
            int cost = dev.gathering.core.card.ManaValue.of(summary.get().sideShown(false).manaCost());
            if (cost < last) {
                return "a card costing " + cost + " came after one costing " + last;
            }
            last = cost;
            seen++;
        }
        return seen < 2 ? "only " + seen + " cards in it could be priced at all" : null;
    }

    /**
     * Puts a Forest from the hand back on top of the deck, so there is one to fetch.
     * <p>The run has spent most of the library by this point. A fetch that found nothing
     * would pass a test about an empty deck rather than the one worth having, which is that
     * the card comes out of the library at all.
     */
    private static void putAForestBackInTheDeck(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no board to put a Forest back into");
            return;
        }
        // Wherever this seat has one. It used to look only in the hand, which made the step
        // hostage to how the run's cards happened to have been spent by the time it ran - and
        // a step earlier in the scene moving one more card is enough to empty it. What is
        // being set up is one property: there is a Forest in the library to be found.
        for (Zone from : List.of(Zone.HAND, Zone.BATTLEFIELD, Zone.GRAVEYARD, Zone.EXILE)) {
            for (CardView card : view.seat(me).zone(from).cards()) {
                if (!(card instanceof CardView.Visible visible)) {
                    continue;
                }
                java.util.Optional<dev.gathering.network.CardSummary> summary = ClientCardCache
                        .get().summary(dev.gathering.item.CardComponent.of(visible.identity()));
                if (summary.isEmpty() || !"Forest".equals(summary.get().name())) {
                    continue;
                }
                ClientTableActions.send(table, new GameEvent.CardMoved(
                        me, visible.id(),
                        dev.gathering.core.game.ZoneRef.of(me, Zone.LIBRARY),
                        dev.gathering.core.game.Placement.TOP));
                System.out.println("[devscene] put a Forest back into the deck from " + from);
                return;
            }
        }
        fail("this seat has no Forest anywhere to put back into the deck");
    }

    /** Opens the library's menu and asks for a basic land off it. */
    private static void askForABasicLand(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to fetch a land on");
            return;
        }
        clickAZone(client, Zone.PILES.indexOf(Zone.LIBRARY), 1);
        if (!board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.fetch_basic").getString())) {
            fail("the library's menu offers no way to fetch a basic land");
        }
    }

    /** Puts loyalty on the newest card on the battlefield, and remembers which. */
    private static void putLoyaltyOn(Minecraft client, int howMuch) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no board to put loyalty on");
            return;
        }
        java.util.List<CardView> onTheTable = view.seat(me).zone(Zone.BATTLEFIELD).cards();
        for (int index = onTheTable.size() - 1; index >= 0; index--) {
            if (onTheTable.get(index) instanceof CardView.Visible visible
                    && visible.writtenStrength().isEmpty()) {
                loyal = visible.id();
                ClientTableActions.send(table, new GameEvent.CounterChanged(
                        me, loyal, dev.gathering.core.game.CardInstance.Counters.LOYALTY, howMuch));
                return;
            }
        }
        fail("there was no plain card on the battlefield to put loyalty on");
    }

    /** How much loyalty the board says that card has. */
    private static int loyaltyNow() {
        return findOnTheBattlefield(loyal)
                .map(card -> card.counter(dev.gathering.core.game.CardInstance.Counters.LOYALTY))
                .orElse(-1);
    }

    /** Whether that card's own menu offers this row. Opens the menu and leaves it open. */
    private static boolean theCardOffers(Minecraft client, String label) {
        return openTheCardMenu(client, label) != null;
    }

    /** Right-clicks the loyalty card and answers with the board if the row is there. */
    private static TableScreen openTheCardMenu(Minecraft client, String label) {
        return openTheCardMenuOn(client, loyal, label);
    }

    /** The same, on whichever card is being worked on. */
    private static TableScreen openTheCardMenuOn(
            Minecraft client, CardInstanceId which, String label) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || which == null || !(client.screen instanceof TableScreen board)) {
            return null;
        }
        TablePosition where = findOnTheBattlefield(which).flatMap(CardView::placedAt).orElse(null);
        if (where == null) {
            return null;
        }
        Rect at = board.board().rectOf(me, where);
        hover(client, new int[] {(int) at.centerX(), (int) at.centerY()});
        board.mouseClicked(at.centerX(), at.centerY(), 1);
        return board.hasMenuEntry(label) ? board : null;
    }

    /**
     * Every menu row that names a number key names the one that really does it.
     * <p>Read out of the same table the menu prints from, and checked against what the run
     * has just watched those keys do - so a row whose label drifts from its key fails here
     * rather than teaching somebody the wrong key for a release.
     */
    private static void theMenuNamesTheRightKeys() {
        expect("to_exile", "7");
        expect("to_graveyard", "8");
        expect("to_library_bottom_random", "9");
        expect("scry", "3");
        expect("mill", "4");
        expect("pass_turn", "0");
        // And the row that is not the key: putting cards back in order is a different act
        // from putting them back in none, so it must not claim the key that randomizes.
        if (TableScreen.keyShownFor("to_library_bottom") != null) {
            fail("the ordered bottom-of-library row claims a key, and 9 is the random one");
        }
    }

    private static void expect(String verb, String key) {
        String shown = TableScreen.keyShownFor(verb);
        if (!key.equals(shown)) {
            fail("the menu says " + verb + " is key " + shown + ", not " + key);
        }
    }

    /** What the board says is written in that card's corner, or null. */
    private static String strengthOn(CardInstanceId card) {
        return findOnTheBattlefield(card)
                .flatMap(CardView::writtenStrength)
                .orElse(null);
    }

    /**
     * How much loyalty the loyalty steps put on.
     * <p>Three digits, because the corner it is written in has to hold them and a single
     * digit never asked it to - see {@link dev.gathering.core.ui.StrengthBadge}.
     */
    private static final int LOYALTY_PUT_ON = 137;

    /** The card the loyalty steps are working on. */
    private static CardInstanceId loyal;

    /** The card the token-row steps are working on, and what it says it makes. */
    private static CardInstanceId maker;

    /** How many cards were on the battlefield before a token row was pressed. */
    private static int onTheBattlefieldBeforeTheToken;

    /** What the library held before a step that moves cards into or out of it. */
    private static int inTheLibraryBefore;

    /** What the battlefield held before a land was fetched onto it. */
    private static int onTheBattlefieldBefore;

    /** What the hand and the graveyard held before the random discard, to compare after. */
    private static int inTheHandBefore;

    private static int inTheGraveyardBefore;

    /**
     * Freezes a card on the battlefield, off its own menu.
     * <p>Through the menu rather than by sending the event, because the entry is half the
     * feature: a freeze nobody can find is a freeze that does not exist.
     */
    private static void freezeACard(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null || !(client.screen instanceof TableScreen board)) {
            fail("there was no board to freeze a card on");
            return;
        }
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (!(card instanceof CardView.Visible visible)) {
                continue;
            }
            TablePosition where = card.placedAt().orElse(null);
            if (where == null) {
                continue;
            }
            frozen = visible.id();
            Rect at = board.board().rectOf(me, where);
            hover(client, new int[] {(int) at.centerX(), (int) at.centerY()});
            board.mouseClicked(at.centerX(), at.centerY(), 1);
            if (!board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.freeze").getString())) {
                fail("a card's menu offers no way to freeze it");
            }
            return;
        }
        fail("there was nothing on the battlefield to freeze");
    }

    /** Taps the frozen card, so the untap step has something to leave alone. */
    private static void tapTheFrozenCard(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || frozen == null) {
            fail("there was no frozen card to tap");
            return;
        }
        ClientTableActions.send(table, new GameEvent.CardTapSet(me, frozen, true));
    }

    /** The untap-everything press, which is the one thing being frozen changes. */
    private static void untapEverything(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("there was no seat to untap");
            return;
        }
        ClientTableActions.send(table, new GameEvent.SeatUntappedAll(me, me));
    }

    /** Whether the board says that card is frozen. */
    private static boolean isFrozen(CardInstanceId card) {
        return findOnTheBattlefield(card).map(CardView::frozen).orElse(false);
    }

    /**
     * A card picked up and put straight back down is not a card that was turned sideways.
     * <p>The plainest gesture on the table used to mean two things: every mis-click tapped
     * something, and a card could not be picked up and reconsidered without turning it. It was
     * fixed, and nothing was holding it fixed - which is the state a behavior is in right
     * before it comes back. Tapping is E, untapping is Q, and a click is a click.
     */
    private static void clickingDoesNotTap(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to click a card on");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        CardInstanceId card = me == null || view == null
                ? null
                : view.seat(me).zone(Zone.BATTLEFIELD).cards().stream()
                        .filter(CardView.Visible.class::isInstance)
                        .map(seen -> ((CardView.Visible) seen).id())
                        .findFirst().orElse(null);
        if (card == null) {
            fail("no card was on the battlefield to click");
            return;
        }
        boolean before = isTapped(card);
        int[] at = cardPoint(client);
        board.mouseClicked(at[0], at[1], 0);
        board.mouseReleased(at[0], at[1], 0);
        if (isTapped(card) != before) {
            fail("clicking a card turned it sideways - a click is one gesture and E is the other");
        }
    }

    /** Whether the board says that card is tapped. */
    private static boolean isTapped(CardInstanceId card) {
        return findOnTheBattlefield(card).map(CardView::tapped).orElse(false);
    }

    private static java.util.Optional<CardView> findOnTheBattlefield(CardInstanceId card) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null || card == null) {
            return java.util.Optional.empty();
        }
        for (CardView seen : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (seen instanceof CardView.Visible visible && visible.id().equals(card)) {
                return java.util.Optional.of(seen);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Moves the cursor onto bare felt.
     * <p>So a picture shows what a card looks like rather than what a card under the cursor
     * looks like. The two differ by a ring, and a mark that only reads next to that ring is a
     * mark that is not doing its job.
     */
    private static void lookAwayFromTheCards(Minecraft client) {
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        hover(client, new int[] {width / 4, height / 2});
    }

    /** The card the last step froze, so the steps after can watch it sit out an untap. */
    private static CardInstanceId frozen;

    /** The card the last step wrote on, so the step after can read it back. */
    private static CardInstanceId written;

    private static String whatIsWrittenOnTheCard() {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null || written == null) {
            return null;
        }
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible && visible.id().equals(written)) {
                return card.writtenOn().orElse(null);
            }
        }
        return null;
    }

    /** What exile held before a step that sends cards to it. */
    private static int inExileBefore;

    /** Where the stack was put, so the hold can find it again. */
    private static TablePosition stackedAt;

    /**
     * Puts two cards on one spot on the felt, which is what a stack is.
     * <p>There is no stack type in the game model - cards on a table are a stack when they
     * are lying on top of each other - so this is two moves to the same position, and the
     * gesture that picks the stack up has to work that out from where things are.
     */
    private static void aStackOfTwoOnTheFelt(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no board to build a stack on");
            return;
        }
        List<CardInstanceId> hand = new ArrayList<>();
        for (CardView card : view.seat(me).zone(Zone.HAND).cards()) {
            if (card instanceof CardView.Visible visible) {
                hand.add(visible.id());
            }
        }
        if (hand.size() < 2) {
            fail("there were not two cards in hand to stack: " + hand.size());
            return;
        }
        stackedAt = TablePosition.of(5000, 5000);
        for (int index = 0; index < 2; index++) {
            ClientTableActions.send(table, new GameEvent.CardMoved(me, hand.get(index),
                    dev.gathering.core.game.ZoneRef.of(me, Zone.BATTLEFIELD),
                    dev.gathering.core.game.Placement.at(stackedAt)));
        }
        System.out.println("[devscene] two cards stacked at " + stackedAt);
    }

    /** Presses on the stack and holds, without letting go. */
    private static void holdTheStackAndDropItOnAZone(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || stackedAt == null || !(client.screen instanceof TableScreen board)) {
            fail("there was no stack to hold");
            return;
        }
        Rect where = board.board().rectOf(me, stackedAt);
        if (where.isEmpty()) {
            fail("the stack is nowhere on screen");
            return;
        }
        int[] at = {(int) where.centerX(), (int) where.centerY()};
        hover(client, at);
        board.mouseClicked(at[0], at[1], 0);
        System.out.println("[devscene] holding a stack at " + at[0] + "," + at[1]);
    }

    /** Lets the held stack go over exile, which is where all of it should end up. */
    private static void letTheStackGo(Minecraft client) {
        int[] onto = zoneCenter(client, Zone.PILES.indexOf(Zone.EXILE));
        if (onto == null || !(client.screen instanceof TableScreen board)) {
            fail("there was no exile to drop a stack on");
            return;
        }
        hover(client, onto);
        board.mouseReleased(onto[0], onto[1], 0);
        System.out.println("[devscene] dropped a stack on exile");
    }

    /**
     * Takes the seat back, so the steps after this one are a player rather than a watcher.
     * <p>Through the session, the way {@link #seatARival} does: what is wanted is an occupied
     * seat, and walking a scripted client back to a block to right-click it would be testing
     * the world interaction all over again rather than the thing after this.
     */
    private static void sitBackDown(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null || client.player == null) {
            fail("there was no table to sit back down at");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        String name = client.player.getGameProfile().getName();
        BlockPos where = table;
        server.execute(() -> {
            GameSession session = TableSessions.sessionAt(server.overworld(), where).orElse(null);
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (session == null || player == null) {
                fail("the table went away before anybody could sit back down at it");
                return;
            }
            // A seat lives on the block entity, not in the session: the session's SeatTaken
            // records whose board it is, and the claim on the block is what makes the server
            // answer "that is your seat" when a payload turns up. Both, in that order, is
            // what right-clicking a table does.
            var cluster = dev.gathering.block.TableClusters.at(server.overworld(), where);
            var anchor = cluster.seats().stream().findFirst().orElse(null);
            if (anchor == null) {
                fail("the table has no seat to sit back down in");
                return;
            }
            var claim = dev.gathering.block.TableSeats.take(
                    server.overworld(), where, anchor.cell(), anchor.side(), who);
            System.out.println("[devscene] sitting back down: " + claim);
            session.submit(new GameEvent.SeatTaken(new SeatId(0), new PlayerRef(who, name)));
            dev.gathering.server.TableActions.openFor(player, where);
            System.out.println("[devscene] sat back down at " + where + ", seat "
                    + dev.gathering.block.TableSessions.seatIdOf(server.overworld(), where, who));
        });
    }

    /** Puts two +1/+1 counters on whatever is on this player's battlefield. */
    private static void twoCountersOnACard(Minecraft client) {
        if (countered != null) {
            // Already on. The counters go on once, early, so the board on the block is
            // photographed carrying them; the later step that used to put them on now only
            // reads them back. Counters are a delta, so asking twice would be four.
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no board to put counters on");
            return;
        }
        CardInstanceId target = null;
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible) {
                target = visible.id();
                break;
            }
        }
        if (target == null) {
            fail("there was nothing on the battlefield to put counters on");
            return;
        }
        countered = target;
        ClientTableActions.send(table, new GameEvent.CounterChanged(
                me, target, dev.gathering.core.game.CardInstance.Counters.PLUS_ONE_PLUS_ONE, 2));
        System.out.println("[devscene] two +1/+1 counters onto " + target);
    }

    /** The card the last step put counters on, so the step after can read them back. */
    private static CardInstanceId countered;

    /** How many differently named counters the crowded card gets. Two more than fit. */
    private static final int CROWDED_COUNTERS = 8;

    /** The last of them, which is the one that used to be unreachable. */
    private static final String LAST_COUNTER = "shield";

    /**
     * A card carrying more differently named counters than the panel has room for.
     * <p>Eight, because the panel shows six. Named after things that really go on a card, so
     * the picture reads as a game rather than as a test.
     */
    private static void aCardCoveredInCounters(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no board to crowd a card on");
            return;
        }
        CardInstanceId target = null;
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible) {
                target = visible.id();
                break;
            }
        }
        if (target == null) {
            fail("there was nothing on the battlefield to crowd with counters");
            return;
        }
        List<String> names = List.of(
                "loyalty", "charge", "quest", "lore", "stun", "flying", "poison", LAST_COUNTER);
        for (int index = 0; index < CROWDED_COUNTERS; index++) {
            ClientTableActions.send(table,
                    new GameEvent.CounterChanged(me, target, names.get(index), index + 1));
        }
        countered = target;
        client.setScreen(new CountersScreen(table,
                new CountersScreen.Subject.Cards(List.of(target),
                        Component.literal("A crowded card")),
                null));
        System.out.println("[devscene] " + CROWDED_COUNTERS + " named counters onto " + target);
    }

    /**
     * The panel shows a window of the counters, says how many are below it, and scrolls.
     * <p>What this is really checking is that the last one can be reached at all. A list that
     * stopped at six and gave no sign of it left every counter past the sixth invisible and
     * unchangeable - which is not a small bug on a card somebody has been stacking counters
     * on all game.
     */
    private static void everyCounterIsReachable(Minecraft client) {
        if (!(client.screen instanceof CountersScreen counters)) {
            fail("there was no counters panel to read");
            return;
        }
        // Everything under the counter list is positioned from the number of rows the panel
        // made room for, so drawing a different number is drawing them through the button
        // grid underneath. It did, for every counter that arrived after the panel opened.
        if (counters.rowsOnScreen() != counters.rowsLaidOut()) {
            fail("the counters panel draws " + counters.rowsOnScreen()
                    + " rows but was laid out for " + counters.rowsLaidOut()
                    + ", so they are on top of what comes after them");
            return;
        }
        // How many rows fit is the window's business - a short one shows fewer - so what is
        // checked is that it really is a window onto a longer list and that it says so.
        int shown = counters.rowsOnScreen();
        if (shown < 1 || shown >= CROWDED_COUNTERS) {
            fail("the counters panel shows " + shown + " rows of " + CROWDED_COUNTERS
                    + ", so it is not a window onto anything");
            return;
        }
        if (counters.moreBelow() != CROWDED_COUNTERS - shown) {
            fail("the panel shows " + shown + " counters and says " + counters.moreBelow()
                    + " are below, out of " + CROWDED_COUNTERS);
            return;
        }
        if (counters.isShowing(LAST_COUNTER)) {
            fail("the last counter is on screen before anything was scrolled, so the window "
                    + "is not where it says it is");
            return;
        }
        // Down to the end, which is where the counter that used to be unreachable lives.
        for (int turn = 0; turn < CROWDED_COUNTERS; turn++) {
            client.screen.mouseScrolled(
                    client.getWindow().getGuiScaledWidth() / 2.0,
                    client.getWindow().getGuiScaledHeight() / 2.0, 0, -1);
        }
        if (!(client.screen instanceof CountersScreen scrolled)
                || !scrolled.isShowing(LAST_COUNTER)) {
            fail("scrolling to the foot of the list still does not show '" + LAST_COUNTER + "'");
            return;
        }
        if (scrolled.moreBelow() != 0) {
            fail("the list says " + scrolled.moreBelow() + " are still below the foot of it");
        }
    }


    /** Untaps that card, so its counters can be photographed the right way up. */
    private static void straightenTheCounteredCard() {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || countered == null) {
            fail("there was no countered card to straighten");
            return;
        }
        ClientTableActions.send(table, new GameEvent.CardTapSet(me, countered, false));
        System.out.println("[devscene] straightened the card with counters on it");
    }

    private static int countersOnTheCardWithCounters(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null || countered == null) {
            return -1;
        }
        for (CardView card : view.seat(me).zone(Zone.BATTLEFIELD).cards()) {
            if (card instanceof CardView.Visible visible && visible.id().equals(countered)) {
                return card.counters().getOrDefault(
                        dev.gathering.core.game.CardInstance.Counters.PLUS_ONE_PLUS_ONE, 0);
            }
        }
        return -1;
    }

    /** The middle of one of the zone slots, in screen pixels, or null if there is none. */
    private static int[] zoneCenter(Minecraft client, int index) {
        Rect zone = zoneRect(client, index);
        return zone.isEmpty() ? null : new int[] {(int) zone.centerX(), (int) zone.centerY()};
    }


    /**
     * A best-of-three with game one over, so the sideboard screen has a reason to be open.
     * <p>Played rather than faked: a table goes down, a deck goes on it, the other seat scoops
     * and the server offers the screen the way it does for anybody. What is being photographed
     * is the one screen in the mod nothing had ever opened, and half of what could be wrong
     * with it is whether it opens at all.
     */
    private static void aBestOfThreeWaitingToSideboard(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to play a set on");
            return;
        }
        List<dev.gathering.item.CardComponent> deck = someCards(client, 20);
        List<dev.gathering.item.CardComponent> board = someCards(client, 5);
        if (deck.isEmpty()) {
            fail("there were no looked-up cards to build a match deck out of");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        BlockPos where = client.player.blockPosition().offset(-6, -1, -6);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                fail("the player went away before a set could be played");
                return;
            }
            BlockState state = GatheringContent.TABLE.get().defaultBlockState();
            for (TablePart part : TablePart.values()) {
                level.setBlock(part.offsetFrom(where), state.setValue(TableBlock.PART, part), 3);
            }
            var cluster = dev.gathering.block.TableClusters.at(level, where);
            var seats = cluster.seats();
            if (seats.size() < 2) {
                fail("a table put down for a match had " + seats.size() + " seats");
                return;
            }
            dev.gathering.block.TableSeats.take(level, where, seats.get(0).cell(),
                    seats.get(0).side(), who);
            dev.gathering.block.TableSeats.take(level, where, seats.get(1).cell(),
                    seats.get(1).side(), java.util.UUID.nameUUIDFromBytes(
                            "devscene-rival".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            // Modern, best of three: a format with a sideboard, which is what makes the step
            // between games exist at all.
            if (dev.gathering.block.TableSessions.start(level, where,
                    new dev.gathering.core.match.MatchRules(
                            dev.gathering.core.format.FormatPresets.MODERN, 3))
                    != dev.gathering.block.TableSessions.Outcome.STARTED) {
                fail("a best-of-three would not start");
                return;
            }
            // Sleeved, so every picture of a board taken from here on shows the sleeves a
            // player actually chose rather than the back a deck arrives in.
            TableBlock.putDown(level, where, player, dev.gathering.item.DeckItem.of(
                    new dev.gathering.item.DeckComponent("Match Deck", "", java.util.Optional.of(who),
                            deck, List.of(), board)
                            .sleeved(dev.gathering.core.card.Sleeve.SWORD)));

            var session = dev.gathering.block.TableSessions.sessionAt(level, where).orElse(null);
            if (session == null) {
                fail("the match went away before game one could be played");
                return;
            }
            session.submit(new GameEvent.Conceded(new SeatId(1)));
            dev.gathering.server.TableMatch.settleIfFinished(level, where, session.state());
            dev.gathering.server.Sideboarding.offerTo(player, where);
            System.out.println("[devscene] game one is over, sideboarding");
        });
    }

    /**
     * Plays a short game out on a real table and ends it, then asks for the shelf.
     * <p>Two seats, a deck down, a hand drawn, and the session ended the way the command ends
     * one - so what lands on the shelf came through the same code a real game does. The hand
     * matters: a replay of a board with nothing hidden on it would photograph nothing worth
     * checking, and the one thing a replay is allowed to do that a live board never may is
     * show it.
     */
    private static void aGameWorthWatchingBack(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to play a game worth keeping on");
            return;
        }
        List<dev.gathering.item.CardComponent> deck = someCards(client, 20);
        if (deck.isEmpty()) {
            fail("there were no looked-up cards to build a replay's deck out of");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        BlockPos where = client.player.blockPosition().offset(6, -1, -6);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                fail("the player went away before a game could be kept");
                return;
            }
            BlockState state = GatheringContent.TABLE.get().defaultBlockState();
            for (TablePart part : TablePart.values()) {
                level.setBlock(part.offsetFrom(where), state.setValue(TableBlock.PART, part), 3);
            }
            var seats = dev.gathering.block.TableClusters.at(level, where).seats();
            if (seats.size() < 2) {
                fail("a table put down for a replay had " + seats.size() + " seats");
                return;
            }
            dev.gathering.block.TableSeats.take(level, where, seats.get(0).cell(),
                    seats.get(0).side(), who);
            dev.gathering.block.TableSeats.take(level, where, seats.get(1).cell(),
                    seats.get(1).side(), java.util.UUID.nameUUIDFromBytes(
                            "devscene-historian".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if (dev.gathering.block.TableSessions.start(level, where,
                    new dev.gathering.core.match.MatchRules(
                            dev.gathering.core.format.FormatPresets.MODERN, 1))
                    != dev.gathering.block.TableSessions.Outcome.STARTED) {
                fail("a game to be kept would not start");
                return;
            }
            TableBlock.putDown(level, where, player, dev.gathering.item.DeckItem.of(
                    new dev.gathering.item.DeckComponent("Kept Deck", "",
                            java.util.Optional.of(who), deck, List.of(), List.of())));
            var session = dev.gathering.block.TableSessions.sessionAt(level, where).orElse(null);
            if (session == null) {
                fail("the game went away before it could be played");
                return;
            }
            session.submit(new GameEvent.CardsDrawn(new SeatId(0), new SeatId(0), REPLAY_HAND));
            if (dev.gathering.block.TableSessions.end(level, where, new SeatId(0), "devscene")
                    != dev.gathering.block.TableSessions.Outcome.ENDED) {
                fail("the game would not end, so there was nothing to keep");
                return;
            }
            dev.gathering.server.ReplayWatch.sendList(player);
            System.out.println("[devscene] a game was played out, ended and kept");
        });
    }

    /** How many cards the kept game drew, and therefore what a replay of it must show. */
    private static final int REPLAY_HAND = 4;

    /**
     * Clicks the top row of the list, which is the game that just finished.
     * <p>By position rather than by label, unlike everything else here: a row's label is the
     * names of whoever played and the minute they played at, so there is nothing stable to
     * name it by. The first widget of the panel is the newest game, which is the ordering the
     * list promises.
     */
    private static void watchTheNewestGame(Minecraft client) {
        if (!(client.screen instanceof ReplayListScreen)) {
            fail("there was no list of finished games to pick from");
            return;
        }
        // A game that does not exist, asked for first and on purpose. The client refuses to
        // re-ask for a step it is already waiting on, so a game picked while the one before
        // it had not answered yet used to send nothing at all - and the list screen sat there
        // for ever with nothing to say why. The server answers this one with a refusal and no
        // frame, which is exactly the state that used to wedge it; if it still does, the
        // replay steps below find a list screen where a board should be.
        ClientReplay.watch("no-such-replay");
        for (GuiEventListener child : client.screen.children()) {
            if (child instanceof AbstractWidget row) {
                row.onClick(row.getX() + row.getWidth() / 2.0,
                        row.getY() + row.getHeight() / 2.0);
                System.out.println("[devscene] watching " + row.getMessage().getString());
                return;
            }
        }
        fail("the list of finished games had no rows to press");
    }

    /** Whether the table screen currently up is showing a replay rather than a live game. */
    private static void expectAReplay(Minecraft client, String what) {
        if (!(client.screen instanceof TableScreen table) || !table.isReplay()) {
            fail(what + ": the screen is "
                    + (client.screen == null ? "none" : client.screen.getClass().getSimpleName())
                    + " rather than a replay");
            return;
        }
        if (ClientReplay.frame().isEmpty()) {
            fail(what + ": the replay screen is up with no frame in it");
        }
    }

    /**
     * The disclosure, checked on screen rather than only in a test.
     * <p>A hand is a count and nothing else to everybody at a live table, including the
     * spectator this scene has been watching as. If a replay of a finished game showed it the
     * same way, the whole feature would be a slideshow of empty boards.
     */
    private static void aReplayShowsWhatWasHidden(Minecraft client) {
        dev.gathering.core.game.visibility.GameView board = ClientReplay.frame().orElse(null);
        if (board == null) {
            fail("there was no replay frame to read a hand out of");
            return;
        }
        int shown = board.seat(new SeatId(0)).zone(Zone.HAND).cards().size();
        if (shown != REPLAY_HAND) {
            fail("a replay of a game where " + REPLAY_HAND + " cards were drawn shows "
                    + shown + " of them");
        }
    }

    /**
     * Every gesture a player would use on a live board, on a replay.
     * <p>None of them may do anything. The guards live in four separate handlers, so this
     * presses through all four: a click on the felt, a drag, the tap key, and the key that
     * passes the turn.
     */
    private static void aWatcherCannotTouchTheBoard(Minecraft client) {
        if (!(client.screen instanceof TableScreen table)) {
            fail("there was no replay to try to play on");
            return;
        }
        double middleX = client.getWindow().getGuiScaledWidth() / 2.0;
        double middleY = client.getWindow().getGuiScaledHeight() / 2.0;
        table.mouseClicked(middleX, middleY, 0);
        table.mouseClicked(middleX, middleY, 1);
        table.mouseDragged(middleX + 20, middleY + 20, 0, 20, 20);
        table.mouseReleased(middleX + 20, middleY + 20, 0);
        table.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E, 0, 0);
        table.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_P, 0, 0);
    }

    /** A sideboard screen shows the deck and the sideboard, not one of them. */
    private static void bothSidesOfTheSideboardAreShown(Minecraft client) {
        if (!(client.screen instanceof SideboardScreen board)) {
            fail("there was no sideboard screen to read");
            return;
        }
        if (board.listedDeck() <= 0) {
            fail("a sideboard screen listed none of the deck");
        }
        if (board.listedSideboard() <= 0) {
            fail("a sideboard screen listed none of the sideboard");
        }
    }



    /**
     * Cards in the pot at the table this run has been playing on.
     * <p>Put there on the server and sent out the way a real stake would be, so what is being
     * looked at is the drawing: whether a row of cards in the middle of a table reads as a
     * pot rather than as somebody having dropped their hand.
     */
    private static void aPotOnTheTable(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            fail("there was no table to put a pot on");
            return;
        }
        List<dev.gathering.item.CardComponent> cards = someCards(client, 1);
        if (cards.isEmpty()) {
            fail("there were no looked-up cards to make a pot out of");
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            var holding = dev.gathering.block.TableSessions.anchorOf(level, where)
                    .flatMap(anchor -> dev.gathering.block.TableBlock.entityAt(level, anchor))
                    .orElse(null);
            if (holding == null) {
                fail("the table went away before a pot could go on it");
                return;
            }
            for (int seat = 0; seat < 2; seat++) {
                holding.stake(new SeatId(seat), List.of(
                        cards.get(seat % cards.size()).toIdentity()));
            }
            dev.gathering.server.TableBroadcast.sendPot(level, where);
            System.out.println("[devscene] the pot holds " + holding.pot().size() + " card(s)");
        });
    }

    /** A pot that has been sent is a pot that is drawn, in the middle, on the table. */
    private static void thePotIsDrawnInTheMiddle(Minecraft client) {
        if (table == null) {
            fail("there was no table to look for a pot on");
            return;
        }
        List<dev.gathering.item.CardComponent> pot = ClientTableState.potOf(table);
        if (pot.isEmpty()) {
            fail("the client was never told what was in the pot");
            return;
        }
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to draw a pot on");
            return;
        }
        Rect where = board.potOnScreen();
        if (where.isEmpty()) {
            fail("a table with " + pot.size() + " card(s) in the pot drew none of them");
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        if (where.x() < 0 || where.y() < 0 || where.right() > width
                || where.bottom() > height) {
            fail("the pot was drawn off the screen: " + where + " in " + width + "x" + height);
        }
    }

    /**
     * The question a table playing for keeps is asked.
     * <p>Delivered the way the server delivers it. The scene's server has ante off - it has
     * collection off, and ante needs collection - so the path that puts this on screen cannot
     * run here. What is being looked at is the screen: the one place in the mod that stands
     * between somebody and losing a card, which had better read at a glance.
     */
    private static void theQuestionBeforePlayingForKeeps(Minecraft client, boolean agreed) {
        BlockPos where = client.player == null
                ? BlockPos.ZERO
                : client.player.blockPosition();
        AnteConsentScreen.accept(new dev.gathering.network.AnteConsentPayload(
                where, 1, agreed ? 2 : 3, agreed, false));
    }

    /** It says what it costs, and both answers are there to press. */
    private static void theQuestionNamesTheStakesAndOffersBothAnswers(Minecraft client) {
        if (!(client.screen instanceof AnteConsentScreen ask)) {
            fail("there was no ante question to read");
            return;
        }
        java.util.Set<String> answers = new java.util.LinkedHashSet<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : ask.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                answers.add(widget.getMessage().getString());
            }
        }
        if (answers.size() < 2) {
            fail("the ante question offered " + answers.size() + " answer(s): " + answers);
        }
        // Escape must not answer it either way: a card changing hands because somebody
        // reached for the wrong key is the exact mistake this screen exists to prevent.
        if (ask.shouldCloseOnEsc()) {
            fail("the ante question could be answered by pressing Escape");
        }
    }

    /**
     * Decks on the loaner shelf, put there rather than read out of a folder.
     * <p>What the folder becomes is a pure rule with its own tests. What is being looked at
     * here is the screen a new player is shown, which wants a shelf with enough on it to say
     * whether a list of names reads as a list of names.
     */
    private static void aShelfOfLoanerDecks(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            fail("there was no server to stock a loaner shelf on");
            return;
        }
        List<dev.gathering.item.CardComponent> cards = someCards(client, 20);
        if (cards.isEmpty()) {
            fail("there were no looked-up cards to build a loaner deck out of");
            return;
        }
        server.execute(() -> {
            dev.gathering.server.LoanerDecks.clear();
            for (String name : List.of("Green Stompy", "Mono Red Burn", "Azorius Control",
                    "Elves", "Goblins")) {
                dev.gathering.server.LoanerDecks.stock(name, new dev.gathering.item.DeckComponent(
                        name, "A deck the server lends out.", java.util.Optional.empty(),
                        cards, List.of(), List.of()));
            }
            System.out.println("[devscene] the loaner shelf has "
                    + dev.gathering.server.LoanerDecks.names().size() + " deck(s) on it");
        });
    }

    /** Offers the shelf the way sitting down at a table with nothing does. */
    private static void offerTheLoaners(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to offer a loaner from");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        BlockPos where = client.player.blockPosition();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                fail("the player went away before they could be offered a deck");
                return;
            }
            dev.gathering.server.Lending.offer(player, where);
        });
    }

    /** A shelf with decks on it shows decks. */
    private static void theShelfIsOnTheScreen(Minecraft client) {
        if (!(client.screen instanceof LoanerScreen shelf)) {
            fail("there was no loaner screen to read");
            return;
        }
        if (shelf.listed() <= 0) {
            fail("a loaner screen with decks on the shelf listed none of them");
        }
        // The way out that is not borrowing. A screen whose only answers all commit you to
        // something is one somebody feels trapped by.
        boolean wayOut = false;
        for (net.minecraft.client.gui.components.events.GuiEventListener child
                : shelf.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getMessage().getString().equals("No thanks")) {
                wayOut = true;
            }
        }
        if (!wayOut) {
            fail("the only way off the loaner screen was to take a deck");
        }
    }

    /**
     * Cards in the inventory, so the trade screen has something of mine to list.
     * <p>Given on the server, which is where an inventory lives: the client copy arrives on
     * its own, and a card put straight into the client's inventory would be one the server
     * would take back on the next sync.
     */
    private static void cardsInHandToTradeWith(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to hand trade cards out on");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        List<dev.gathering.item.CardComponent> cards = someCards(client, 1);
        if (cards.isEmpty()) {
            fail("there were no looked-up cards to trade with");
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                fail("the player went away before they could be given cards");
                return;
            }
            int each = 3;
            for (dev.gathering.item.CardComponent card : cards) {
                ItemStack stack = dev.gathering.item.CardItem.of(card);
                stack.setCount(each);
                if (!player.getInventory().add(stack)) {
                    fail("there was no room for a card to trade with");
                }
                each++;
            }
            // One in the off hand as well. Both sides used to read the main inventory only,
            // which made the card you are holding out to somebody the one card you could not
            // put up - so the scene holds one there on purpose.
            player.getInventory().offhand.set(0,
                    dev.gathering.item.CardItem.of(cards.get(0)));
        });
    }

    /**
     * A trade, delivered the way the server delivers one.
     * <p>Not opened by walking up to a second player, because there is only one here. What is
     * being looked at is the screen: the two columns, the counts, and the agreement lights -
     * all of which are drawn from the payload and from this client's own inventory, which are
     * exactly the two things a real trade gives it.
     */
    private static void aTradeOnTheScreen(Minecraft client, boolean agreed, int mineUp) {
        List<dev.gathering.item.CardComponent> cards = someCards(client, 1);
        if (cards.isEmpty()) {
            fail("there were no looked-up cards to build a trade out of");
            return;
        }
        List<dev.gathering.network.TradeViewPayload.Pile> mine = new java.util.ArrayList<>();
        if (mineUp > 0) {
            mine.add(new dev.gathering.network.TradeViewPayload.Pile(cards.get(0), mineUp));
        }
        List<dev.gathering.network.TradeViewPayload.Pile> theirs = new java.util.ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            theirs.add(new dev.gathering.network.TradeViewPayload.Pile(
                    cards.get(index), index + 1));
        }
        TradeScreen.accept(new dev.gathering.network.TradeViewPayload(
                "Steve", mine, theirs, agreed, agreed, false));
    }

    /** With nothing of mine up there is nothing to take back, and the button says so. */
    private static void takingItBackIsSpentWhenThereIsNothingUp(Minecraft client) {
        if (!(client.screen instanceof TradeScreen trade)) {
            fail("there was no trade screen to look for the take-back button on");
            return;
        }
        for (net.minecraft.client.gui.components.events.GuiEventListener child
                : trade.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getMessage().getString().equals("Take it all back")) {
                if (widget.active) {
                    fail("take it all back was still live with nothing on the table");
                }
                return;
            }
        }
        fail("there was no take-it-all-back button on the trade screen");
    }

    /** A trade with cards on both sides shows cards on both sides. */
    private static void aTradeShowsBothSides(Minecraft client) {
        if (!(client.screen instanceof TradeScreen trade)) {
            fail("there was no trade screen to read");
            return;
        }
        if (trade.listedMine() <= 0) {
            fail("a trade screen listed none of the cards I am carrying");
        }
        if (trade.listedTheirs() <= 0) {
            fail("a trade screen listed none of the cards I am being offered");
        }
    }

    /**
     * A sealed pack on the screen, with something worth finding in it.
     * <p>The cards are made up and told to this client the way the server would tell it, so
     * the light coming out of the tear is decided by the same rule a real pack's would be.
     * What is being looked at is the drawing.
     */
    private static void aSealedPackOnTheScreen(Minecraft client) {
        java.util.List<dev.gathering.network.CardSummary> summaries = new java.util.ArrayList<>();
        java.util.List<dev.gathering.item.CardComponent> cards = new java.util.ArrayList<>();
        dev.gathering.core.card.Rarity[] rarities = {
                dev.gathering.core.card.Rarity.COMMON, dev.gathering.core.card.Rarity.COMMON,
                dev.gathering.core.card.Rarity.UNCOMMON, dev.gathering.core.card.Rarity.MYTHIC};
        for (int index = 0; index < rarities.length; index++) {
            java.util.UUID id = java.util.UUID.nameUUIDFromBytes(
                    ("ceremony-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            summaries.add(new dev.gathering.network.CardSummary(
                    id,
                    id,
                    new dev.gathering.network.CardFaceSummary(
                            "Pulled Card " + index, "{1}", "Artifact", "", "", "", "", ""),
                    java.util.Optional.empty(),
                    rarities[index], 1.0, java.util.Set.of()));
            cards.add(dev.gathering.item.CardComponent.of(
                    dev.gathering.core.card.CardIdentity.ofPrinting(id, false)));
        }
        ClientCardCache.get().accept(summaries);
        client.setScreen(new PackOpeningScreen("blb", "collector", cards));
    }

    private static void aSealedPackIsSealed(Minecraft client) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("there was no pack on the screen to be sealed");
            return;
        }
        if (!pack.tear().isUntouched()) {
            fail("a pack nobody has touched was already torn");
        }
        if (pack.glow() != dev.gathering.core.ui.PackGlow.MYTHIC_LIGHT) {
            fail("a pack with a mythic in it is glowing " + Integer.toHexString(pack.glow()));
        }
    }

    private static void aPackHalfTornIsHalfTorn(Minecraft client) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("the pack screen went away mid-tear");
            return;
        }
        if (pack.tear().isUntouched()) {
            fail("dragging across the wrapper tore nothing at all");
            return;
        }
        if (pack.tear().isOpen()) {
            fail("a pack dragged half way across came apart");
        }
    }

    private static void aTornPackIsOpen(Minecraft client) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("the pack screen went away before it was open");
            return;
        }
        if (!pack.tear().isOpen()) {
            fail("a pack dragged the whole way across is still sealed at "
                    + Math.round(pack.tear().torn() * 100) + " per cent");
        }
    }

    /**
     * A torn pack shows what was in it rather than an empty wrapper.
     * <p>The dead end this exists to catch: a pack that tears open beautifully and then sits
     * there is worse than one that never opened, because the player has been made to work for
     * nothing.
     */
    private static void everyCardIsShown(Minecraft client) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("there was no pack screen showing what came out");
            return;
        }
        if (!pack.tear().isOpen()) {
            fail("the pack was not open, so nothing could be shown");
            return;
        }
        if (pack.shown().size() != 4) {
            fail("a pack of four showed " + pack.shown().size() + " cards");
            return;
        }
        // The best card last: the whole point of a reveal order.
        var last = ClientCardCache.get().summary(pack.shown().get(pack.shown().size() - 1));
        if (last.isEmpty()
                || last.get().rarity() != dev.gathering.core.card.Rarity.MYTHIC) {
            fail("the last card revealed was "
                    + last.map(s -> s.rarity().toString()).orElse("unknown")
                    + " rather than the mythic");
        }
    }

    /** Where the collection block is, once it has been put down. */
    private static BlockPos collectionBlock;

    /**
     * A collection with a few real cards in it.
     * <p>Stocked on the server, which is where a collection lives - the client is told what
     * is in one only when somebody opens it, which is the thing being checked.
     */
    private static void aCollectionWithSomethingInIt(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to put a collection on");
            return;
        }
        BlockPos where = client.player.blockPosition().offset(-2, -1, 2);
        collectionBlock = where;
        java.util.UUID player = client.player.getUUID();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setBlock(where, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
            if (!(level.getBlockEntity(where)
                    instanceof dev.gathering.block.CollectionBlockEntity collection)) {
                fail("a collection block was placed without its block entity");
                return;
            }
            collection.claimFor(player);
            collection.setLabel("The good one");
            // Cards the import has already resolved, so they are named the moment the
            // collection is opened. A collection of made-up ids would photograph as a list
            // of "not looked up yet", which says nothing about whether the screen reads.
            var service = dev.gathering.service.CardDataService.active().orElse(null);
            if (service == null) {
                fail("there was no card service to stock a collection from");
                return;
            }
            for (String name : java.util.List.of("Forest", "Mountain", "Island",
                    "Lightning Bolt", "Counterspell", "Grizzly Bears", "Giant Growth")) {
                service.findByName(name).thenAccept(found -> found.ifPresent(card ->
                        server.execute(() -> collection.put(
                                dev.gathering.core.card.CardIdentity.ofPrinting(
                                        card.scryfallId(), false),
                                name.equals("Forest") ? 20 : 4))));
            }
            System.out.println("[devscene] a collection is being stocked");
        });
    }

    /** Opens it the way a player would: standing at it, empty handed. */
    private static void openTheCollection(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || collectionBlock == null || client.player == null) {
            fail("there was no collection to open");
            return;
        }
        java.util.UUID player = client.player.getUUID();
        BlockPos where = collectionBlock;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            var opener = server.getPlayerList().getPlayer(player);
            if (opener == null
                    || !(level.getBlockEntity(where)
                            instanceof dev.gathering.block.CollectionBlockEntity collection)) {
                fail("the collection went away before it could be opened");
                return;
            }
            opener.teleportTo(where.getX() + 0.5, where.getY() + 1, where.getZ() + 1.5);
            dev.gathering.server.CollectionView.open(opener, where, collection);
        });
    }

    /**
     * A collection that has cards in it shows them.
     * <p>The dead end this exists to catch: a block you can put a collection into and then
     * open onto an empty list, because the page never arrived or never asked.
     */
    private static void aCollectionShowsWhatIsInIt(Minecraft client) {
        if (!(client.screen instanceof CollectionScreen collection)) {
            fail("there was no collection on the screen");
            return;
        }
        if (collection.shown().isEmpty()) {
            fail("a collection with a hundred cards in it opened onto an empty list");
            return;
        }
        if (!collection.mayTake()) {
            fail("the player who put the collection down may not take from it");
            return;
        }
        System.out.println("[devscene] a collection opened showing "
                + collection.shown().size() + " rows");
    }

    private static void searchTheCollection(Minecraft client, String text) {
        if (!(client.screen instanceof CollectionScreen collection)) {
            fail("there was no collection to search");
            return;
        }
        collection.searchFor(text);
    }

    /** Typing narrows it, and narrows it to something. */
    private static void aSearchNarrowsIt(Minecraft client) {
        if (!(client.screen instanceof CollectionScreen collection)) {
            fail("the collection went away mid-search");
            return;
        }
        if (collection.shown().isEmpty()) {
            fail("searching a collection for a card that is in it found nothing");
            return;
        }
        for (var row : collection.shown()) {
            String name = row.about().map(summary -> summary.front().name()).orElse("");
            if (!name.toLowerCase(java.util.Locale.ROOT).contains("forest")) {
                fail("a search for forest turned up " + name);
                return;
            }
        }
        System.out.println("[devscene] searching a collection narrowed it to "
                + collection.shown().size() + " rows");
    }

    /*
     * Sleeving a card out of a collection into a held deck is not scripted here.
     *
     * The gesture is "what is in the main hand", and this harness drives the client and the
     * integrated server separately: the pack pictures above set the client's own hotbar and
     * its selected slot without telling the server, so the two sides disagree about which
     * slot the main hand is and a deck put in one is not the deck the other reads. Fixing
     * that would mean the scene faking an inventory sync, which would be a picture of the
     * harness rather than of the mod.
     *
     * What it would have checked is checked in CollectionBlockGameTest, where there is one
     * player and one inventory: cards go into the deck in hand, they come out loose without
     * one, and a deck poured back in brings every section with it.
     */

    /** Puts the cursor on the card the pack was opened for. */
    private static void putTheCursorOnAPulledCard(Minecraft client) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("there was no pack screen to point at");
            return;
        }
        int[] middle = pack.middleOfCard(pack.shown().size() - 1);
        if (middle == null) {
            fail("the pack showed cards and could not say where any of them was");
            return;
        }
        hover(client, middle);
    }

    /**
     * The card being looked at leans toward the cursor, and the ones away from it do not.
     * <p>A grid where every card leaned the same way was the first thing this did: a card
     * three widths to the left of the cursor is still to its left, so without a falloff the
     * whole handful tipped one way like a stack about to go over. So both halves are checked
     * - that the one under the cursor turns, and that the far one has all but stopped.
     */
    private static void thePulledCardsLeanTowardTheCursor(Minecraft client) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("there was no pack screen to ask which way its cards were leaning");
            return;
        }
        int last = pack.shown().size() - 1;
        if (last < 1) {
            fail("a pack showed fewer than two cards, so nothing can lean differently");
            return;
        }
        float under = pack.leanOf(last);
        float across = pack.leanOf(0);
        System.out.println("[devscene] the pulled card under the cursor leans " + under
                + " and the far one " + across);
        if (under <= 0f) {
            fail("the pulled card under the cursor is not leaning toward it");
        }
        if (across >= under) {
            fail("a pulled card across the grid leans as far as the one being looked at");
        }
    }

    /**
     * A card you have just been given can be read where it is shown.
     * <p>The dead end this exists to catch: a reveal that hands over fourteen cards drawn at
     * thumbnail size and answers nothing when you try to look at one. Every other place the
     * mod draws a card answers the read-a-card key, and this was the one that did not.
     */
    private static void theReadKeyAnswersOverAPulledCard(Minecraft client) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("the pack screen went away before a card could be read");
            return;
        }
        net.minecraft.world.item.ItemStack under = ClientHoverState.hovered();
        if (under.isEmpty()) {
            fail("the cursor was on a card the pack had just given out and nothing was under it");
            return;
        }
        var card = dev.gathering.item.CardItem.cardOf(under).orElse(null);
        if (card == null) {
            fail("what was under the cursor on the reveal was not a card: " + under);
            return;
        }
        if (!card.equals(pack.shown().get(pack.shown().size() - 1))) {
            fail("the cursor was on the last card and the game thought it was on another");
            return;
        }
        System.out.println("[devscene] a card just pulled can be read where it is shown");
    }

    /**
     * Drags a cursor across the wrapper, corner first.
     * <p>Through the screen's own mouse handlers rather than past them: a tear that works and
     * a tear nothing can reach look identical from the inside.
     */
    private static void tearThePack(Minecraft client, double toFraction) {
        if (!(client.screen instanceof PackOpeningScreen pack)) {
            fail("there was no pack to tear");
            return;
        }
        int y = pack.packMiddleY();
        int from = pack.packLeft();
        pack.mouseClicked(from + 1, y, 0);
        int steps = 24;
        for (int step = 1; step <= steps; step++) {
            double along = toFraction * step / steps;
            pack.mouseDragged(from + along * pack.packWidth(), y, 0, 1, 0);
        }
    }

    /**
     * Three packs in the hotbar, one of each product a wrapper has a color for.
     * <p>Set client-side, which is all a picture of an item needs: what is drawn is what this
     * client's own inventory holds. The symbol on each is fetched by this client from
     * Scryfall exactly as it would be in a real game.
     */
    private static void sealedPacksInTheHotbar(Minecraft client) {
        if (client.player == null) {
            fail("there was no player to hand a pack to");
            return;
        }
        String[][] packs = {
                {"dmu", "draft"}, {"blb", "play"}, {"blb", "collector"}, {"lci", ""}};
        for (int slot = 0; slot < packs.length; slot++) {
            client.player.getInventory().setItem(slot, dev.gathering.item.PackItem.of(
                    new dev.gathering.item.PackComponent(packs[slot][0], packs[slot][1])));
        }
        client.player.getInventory().selected = 1;
    }

    /**
     * Every pack in the hotbar has its set's symbol by now, or says which has not.
     * <p>Not fatal on its own: this fetches from somebody else's server, and a run with no
     * network is a run that cannot check this rather than a broken mod. What it must never do
     * is pass quietly while drawing nothing.
     */
    private static void everyPackDrewItsSymbol(Minecraft client) {
        if (client.player == null) {
            fail("there was no player holding packs");
            return;
        }
        java.util.List<String> waiting = new java.util.ArrayList<>();
        for (int slot = 0; slot < 4; slot++) {
            var pack = dev.gathering.item.PackItem.packOf(client.player.getInventory().getItem(slot));
            if (pack.isEmpty()) {
                fail("slot " + slot + " was meant to hold a pack and did not");
                return;
            }
            String set = pack.get().setCode();
            int color = dev.gathering.core.ui.PackWrapper.symbolColor(pack.get().kind());
            if (ClientSetSymbols.get().symbol(set, color, 64).isEmpty()) {
                waiting.add(set + (ClientSetSymbols.get().hasFailed(set) ? " (gave up)" : ""));
            }
        }
        if (waiting.isEmpty()) {
            System.out.println("[devscene] every pack in the hotbar drew its own set's symbol");
            return;
        }
        System.out.println("[devscene] still without a symbol: " + waiting
                + "; the picture shows plain wrappers for those");
    }

    /**
     * Changes the interface size, and says so only if it actually changed.
     * <p>Minecraft clamps the scale to what the window can show - at the size this runs at,
     * anything above two is two - so asking for three did nothing at all, and the run went on
     * to photograph the same frame under the name "a bigger interface" and to check the board
     * was still framed after a resize that never happened. A step that cannot fail is worse
     * than no step: it reads, in the log and in the pictures, exactly like one that passed.
     */
    private static void resizeTo(Minecraft client, int scale, String what) {
        int wasWide = client.getWindow().getGuiScaledWidth();
        int wasHigh = client.getWindow().getGuiScaledHeight();
        client.options.guiScale().set(scale);
        client.resizeDisplay();
        int nowWide = client.getWindow().getGuiScaledWidth();
        int nowHigh = client.getWindow().getGuiScaledHeight();
        if (nowWide == wasWide && nowHigh == wasHigh) {
            fail("asking for " + what + " left the interface at " + wasWide + " by " + wasHigh
                    + ", so nothing about a resize was tested");
            return;
        }
        System.out.println("[devscene] " + what + ": " + wasWide + "x" + wasHigh
                + " became " + nowWide + "x" + nowHigh);
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
        // The clock is per step, so it starts again here. See STUCK_TICKS.
        ticks = 0;
    }

    /**
     * Puts a table down in front of the player and starts a game on it.
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

    /**
     * Walks back to the table and opens the board.
     * <p>The walking matters. The camera has just been eleven blocks away photographing
     * furniture, and every verb the server decides is checked against TableReach first - so
     * opening the screen alone would put a board in front of a player standing too far from
     * it to do anything, and the rolls would be refused with nothing on screen to say why.
     */
    private static void backToTheBoard(Minecraft client) {
        walkToTheTable(client);
        client.setScreen(new TableScreen(table));
    }

    /**
     * Asks the server for the board again, the way walking up to a table does.
     * <p>The board is broadcast to whoever is nearby when something happens on it. A player
     * who walks away and comes back is told nothing until it does - which is fine in the game,
     * because the way back in is a right-click and that asks - and is not fine here, where the
     * screen is constructed rather than opened. The gallery runs after the tour has wandered
     * off to a village, so its first board came out as a photograph of a field.
     */
    private static void askTheTableForTheBoard(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (table == null || server == null) {
            fail("there is no table to ask for a board");
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .findFirst().orElse(null);
            if (player != null) {
                dev.gathering.server.TableActions.openFor(player, where);
            }
        });
    }

    /**
     * Puts the player back within reach of the table without opening anything.
     * <p>Two halves because the teleport is done on the server's own thread and arrives a tick
     * later. A board opened in the same breath is a board opened while the player is still
     * wherever they were, and it closes itself for being out of reach - which in the gallery
     * came out as a photograph of a field.
     */
    private static void walkToTheTable(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (table == null || server == null) {
            fail("there is no table to go back to");
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .findFirst().orElse(null);
            if (player == null) {
                return;
            }
            double atX = where.getX() + 0.5;
            double atY = where.getY() + 1;
            double atZ = where.getZ() + 2.5;
            player.teleportTo(atX, atY, atZ);
            player.connection.teleport(atX, atY, atZ, 0f, 0f);
        });
    }

    /** Opens the table menu and asks for a die. */
    private static void openTheDiceQuestion(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to roll a die on");
            return;
        }
        String wanted = net.minecraft.network.chat.Component
                .translatable("menu.gathering.table.roll_die").getString();
        if (!openTheTableMenu(client, board, wanted)) {
            fail("no felt on the board offered a table menu to roll a die from");
            return;
        }
        if (!board.pressMenuEntry(wanted)) {
            fail("the table menu offers no way to roll a die");
        }
    }

    /** And the planar die, which lives on the same question the numbered dice do. */
    private static void rollThePlanarDie(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to roll the planar die on");
            return;
        }
        String wanted = net.minecraft.network.chat.Component
                .translatable("menu.gathering.table.roll_die").getString();
        if (!openTheTableMenu(client, board, wanted) || !board.pressMenuEntry(wanted)) {
            fail("the table menu offers no way to roll a die");
            return;
        }
        press(client, net.minecraft.network.chat.Component
                .translatable("screen.gathering.dice.planar").getString());
    }

    /** And a coin, which is one press rather than a question. */
    private static void flipACoin(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to flip a coin on");
            return;
        }
        String wanted = net.minecraft.network.chat.Component
                .translatable("menu.gathering.table.flip_coin").getString();
        if (!openTheTableMenu(client, board, wanted) || !board.pressMenuEntry(wanted)) {
            fail("the table menu offers no way to flip a coin");
        }
    }

    /**
     * Opens the one question a blank card asks: what should it say.
     * <p>Both stocks go through here because they are the same act with a different word on
     * the menu, and a scene that drove them separately would be two copies of one path - the
     * thing the mod itself refuses to have.
     */
    private static void openThePaperQuestion(Minecraft client, String entry, String what) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to put " + what + " on");
            return;
        }
        String wanted = net.minecraft.network.chat.Component
                .translatable("menu.gathering.table." + entry).getString();
        if (!openTheTableMenu(client, board, wanted) || !board.pressMenuEntry(wanted)) {
            fail("the table menu offers no way to put " + what + " on the table");
        }
    }

    /** Opens the question of who may read my hand. */
    private static void openTheHandQuestion(Minecraft client) {
        pressTableEntry(client, "show_hand", "ask who can see my hand");
    }

    /** And closes it again, which is the press the whole feature depends on existing. */
    private static void takeMyHandBack(Minecraft client) {
        pressTableEntry(client, "hide_hand", "take my hand back");
    }

    /** One row of the table menu, by its key, for the verbs that are a single press. */
    private static void pressTableEntry(Minecraft client, String entry, String what) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to " + what + " on");
            return;
        }
        String wanted = net.minecraft.network.chat.Component
                .translatable("menu.gathering.table." + entry).getString();
        if (!openTheTableMenu(client, board, wanted) || !board.pressMenuEntry(wanted)) {
            fail("the table menu offers no way to " + what);
        }
    }

    /**
     * Says something to the table, typed the way a player types it.
     * <p>Through the board's own key handling rather than by sending the payload, because the
     * thing most likely to break is the handling: a board where the chat key played a card,
     * or where the letters of the sentence did, is a board nobody can talk at.
     */
    private static void sayToTheTable(Minecraft client, String words) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to say anything at");
            return;
        }
        // T, and checked rather than assumed: the board opens the line on whatever the
        // player has bound, and this run has the default binding. Reading the bound key back
        // out is a loader extension and this layer has no loader.
        int chat = org.lwjgl.glfw.GLFW.GLFW_KEY_T;
        if (!client.options.keyChat.matches(chat, 0)) {
            fail("the chat key is not bound to T in this run, so the scene cannot type");
            return;
        }
        board.keyPressed(chat, 0, 0);
        for (char letter : words.toCharArray()) {
            board.charTyped(letter, 0);
        }
        board.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
    }

    /** And tips it up or down, which moves the shine the other way across the card. */
    private static void tipTheHead(Minecraft client, float degrees) {
        if (client.player == null) {
            fail("there was nobody to tip");
            return;
        }
        client.player.setXRot(client.player.getXRot() + degrees);
    }

    /** Whether the line got all the way back round to this client. */
    private static boolean theTableHasNotHeard(Minecraft client, String words) {
        if (table == null) {
            return true;
        }
        return ClientTableChat.recentAt(table, System.currentTimeMillis()).stream()
                .noneMatch(said -> said.text().contains(words));
    }

    /** Where the first row of the set list sits, which is the row the scene presses. */
    private static final int SET_ROW_Y = 44;

    /** Which set the run pressed, so the step after can check the collection followed. */
    private static String pressedSet = "";

    /**
     * Answers the set-completion question the way a server with a network would.
     * <p>Four sets covering what the screen has to draw differently: one finished, one most of
     * the way, one barely started, and one whose extras outnumber what it has of the set
     * itself. Sent as the payload rather than poked into the screen, so what is checked is the
     * whole path from the wire in.
     */
    private static void howMuchOfEachSet(Minecraft client) {
        if (table == null && collectionBlock == null) {
            fail("there was no collection to count");
            return;
        }
        java.util.List<dev.gathering.network.SetProgressPayload.Row> rows = java.util.List.of(
                new dev.gathering.network.SetProgressPayload.Row("tst", "The Test Set", 281, 281, 12),
                new dev.gathering.network.SetProgressPayload.Row("mid", "Innistrad: Midnight Hunt", 214, 277, 3),
                new dev.gathering.network.SetProgressPayload.Row("dom", "Dominaria", 61, 269, 0),
                new dev.gathering.network.SetProgressPayload.Row("leg", "Legends", 4, 310, 21));
        SetProgressScreen.accept(new dev.gathering.network.SetProgressPayload(
                collectionBlock, rows, 0));
    }

    /** Which card the run pressed on its wants list, and whether it was already on it. */
    private static java.util.UUID wanted;
    private static boolean wasWanted;

    /**
     * Puts the card under the cursor on the wants list.
     * <p>Through the screen's own click rather than by calling the network directly, because
     * what is being checked is that pressing a row of this list is what does it.
     */
    private static void wantACardFromTheList(Minecraft client) {
        if (!(client.screen instanceof MissingCardsScreen missing)) {
            fail("there was no missing list to want a card from");
            return;
        }
        int[] firstRow = missing.middleOfRow(0);
        if (firstRow == null) {
            fail("the missing list showed nothing to want");
            return;
        }
        hover(client, firstRow);
        // Rendered once so the row under the cursor is known, the same way every other
        // hovered thing in this scene is pressed.
        missing.render(new net.minecraft.client.gui.GuiGraphics(
                client, client.renderBuffers().bufferSource()), firstRow[0], firstRow[1], 0f);
        wanted = missing.printingOfRow(0);
        // What it was before, because this file survives a restart and the run before this
        // one may well have left the card on the list. What is being checked is that
        // pressing a row changes what the server holds, not what state it started in.
        wasWanted = dev.gathering.client.ClientWants.wants(wanted);
        missing.mouseClicked(firstRow[0], firstRow[1], 0);
    }

    /**
     * The server put it on the list and said so.
     * <p>Checked rather than assumed, because the client deliberately does not mark its own
     * card: a full list is refused, and a screen that marked what it hoped for would show a
     * card nobody is chasing with nothing to ever correct it.
     */
    private static void theWantedCardCameBack(Minecraft client) {
        if (wanted == null) {
            fail("nothing was ever put on the wants list to check");
            return;
        }
        if (dev.gathering.client.ClientWants.wants(wanted) == wasWanted) {
            fail("pressing a card on the missing list left the wants list "
                    + (wasWanted ? "still holding it" : "without it"));
        }
        System.out.println("[devscene] the wants list holds "
                + dev.gathering.client.ClientWants.all().size() + " card(s)");
    }

    /**
     * Answers the question a pressed set asks, the way a server with a network would.
     * <p>A short set rather than a real one: what is being checked is that the list draws,
     * scrolls and reads a card into the inspect panel, none of which needs three hundred
     * rows. Sent as the payload rather than poked into the screen, so the whole path from the
     * wire in is what runs.
     */
    private static void whatIsStillMissing(Minecraft client, String setCode) {
        java.util.List<dev.gathering.network.SetMissingPayload.Row> rows = java.util.List.of(
                new dev.gathering.network.SetMissingPayload.Row(
                        4, "Blistering Firecat", dev.gathering.core.card.Rarity.RARE,
                        java.util.UUID.nameUUIDFromBytes("missing-4".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))),
                new dev.gathering.network.SetMissingPayload.Row(
                        18, "Grizzly Bears", dev.gathering.core.card.Rarity.COMMON,
                        java.util.UUID.nameUUIDFromBytes("missing-18".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))),
                new dev.gathering.network.SetMissingPayload.Row(
                        99, "Sheoldred, the Apocalypse", dev.gathering.core.card.Rarity.MYTHIC,
                        java.util.UUID.nameUUIDFromBytes("missing-99".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))));
        MissingCardsScreen.accept(new dev.gathering.network.SetMissingPayload(
                setCode, "The Test Set", rows, rows.size()));
    }

    /**
     * A card with a history, held up and read.
     * <p>Put into the hand here rather than won in an ante game two hundred steps back,
     * because what is being looked at is the reading of it: the four things that write a
     * story are checked in world by CardStoryGameTest, where they belong, and the picture
     * this takes is of the panel that says where the card has been.
     */
    private static void aCardWithAHistoryInHand(Minecraft client) {
        if (client.player == null) {
            fail("there was no player to hand a card with a history to");
            return;
        }
        if (client.screen != null) {
            client.screen.onClose();
        }
        client.setScreen(null);

        java.util.UUID printing = java.util.UUID.nameUUIDFromBytes(
                "storied".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ClientCardCache.get().accept(java.util.List.of(new dev.gathering.network.CardSummary(
                printing,
                printing,
                new dev.gathering.network.CardFaceSummary(
                        "Sol Ring", "{1}", "Artifact", "{T}: Add {C}{C}.", "", "", "", ""),
                java.util.Optional.empty(),
                dev.gathering.core.card.Rarity.UNCOMMON, 1.0, java.util.Set.of())));

        dev.gathering.core.story.CardStory story = dev.gathering.core.story.CardStory
                .begunWith(new dev.gathering.core.story.CardStory.Chapter(
                        dev.gathering.core.story.HowItCame.PULLED, "Dev", "", "dsk", "2026-01-04"))
                .and(new dev.gathering.core.story.CardStory.Chapter(
                        dev.gathering.core.story.HowItCame.WON, "Rival", "Dev", "", "2026-02-11"))
                .and(new dev.gathering.core.story.CardStory.Chapter(
                        dev.gathering.core.story.HowItCame.TRADED, "Dev", "Rival", "", "2026-03-14"));

        net.minecraft.world.item.ItemStack card = dev.gathering.item.CardItem.of(
                dev.gathering.item.CardComponent.of(
                        dev.gathering.core.card.CardIdentity.ofPrinting(printing, false)));
        card.set(dev.gathering.registry.GatheringComponents.STORY.get(),
                dev.gathering.item.StoryComponent.of(story));
        client.player.getInventory().items.set(
                client.player.getInventory().selected, card);
        CardZoomOverlay.bindKeyState(() -> true);
    }

    /** Shuts the board, for the steps that happen out in the world. */
    private static void leaveTheBoard(Minecraft client) {
        if (client.screen != null) {
            client.screen.onClose();
        }
        client.setScreen(null);
    }

    /** How many decks go on the shelf, each in its own box. */
    private static final int SHELF_DECKS = 8;

    /**
     * Fills the hotbar with decks, each painted the way a newly built one is.
     * <p>Rolled rather than handed fixed colors, because what is being photographed is the
     * thing a player actually gets: eight decks made one after another, and whether they come
     * out telling apart.
     */
    private static void aShelfOfDecks(Minecraft client) {
        if (client.player == null) {
            fail("there was no player to put a shelf of decks on");
            return;
        }
        // Put on the client rather than handed over by the server, and that is not a shortcut
        // being taken. The scripted run plays in creative, where the inventory is the
        // client's own and a server-side write never arrives - eight decks went in and the
        // hotbar stayed empty. What this step is for is whether a painted deck draws in its
        // color, and the item renderer draws whatever the stack in front of it says. That the
        // color survives the wire is a different question, asked where it can be answered:
        // see DeckBoxColorGameTest.
        java.util.Random shelf = new java.util.Random(0x5EA7L);
        java.util.UUID who = client.player.getUUID();
        for (int slot = 0; slot < SHELF_DECKS; slot++) {
            dev.gathering.item.DeckComponent deck = new dev.gathering.item.DeckComponent(
                    "Deck " + (slot + 1), "", java.util.Optional.of(who),
                    List.of(), List.of(), List.of())
                    .colored(dev.gathering.core.card.DeckColors.pick(shelf.nextLong()));
            client.player.getInventory().items.set(
                    slot, dev.gathering.item.DeckItem.of(deck));
        }
        System.out.println("[devscene] " + SHELF_DECKS + " decks onto the shelf");
    }

    /**
     * Every deck on the shelf came out in a color, and they are not all the same one.
     * <p>Two failures this catches, both of which look fine in a screenshot taken from the
     * wrong angle: a box drawn white because the color never reached the item, and a wheel
     * that has collapsed so eight decks are three colors.
     */
    private static void everyDeckIsItsOwnColor(Minecraft client) {
        if (client.player == null) {
            fail("there was no player to read a shelf off");
            return;
        }
        java.util.Set<Integer> colors = new java.util.LinkedHashSet<>();
        int found = 0;
        for (int slot = 0; slot < SHELF_DECKS; slot++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(slot);
            dev.gathering.item.DeckComponent deck =
                    dev.gathering.item.DeckItem.deckOf(stack).orElse(null);
            if (deck == null || deck.color().isEmpty()) {
                continue;
            }
            found++;
            if (dev.gathering.item.DeckItem.tintOf(stack, 0) == 0xFFFFFFFF) {
                fail("a deck in slot " + slot + " is drawn white, so its color never "
                        + "reached the item");
                return;
            }
            colors.add(deck.color().orElseThrow());
        }
        if (found < SHELF_DECKS) {
            fail("the shelf was handed " + SHELF_DECKS + " painted decks and the client can "
                    + "see " + found);
            return;
        }
        // Eight rolls off a wheel of twenty-four repeat sometimes; three or fewer distinct
        // colors out of eight is the wheel being broken rather than luck.
        if (colors.size() <= 3) {
            fail("eight decks came out in " + colors.size() + " color(s)");
        }
        System.out.println("[devscene] eight decks in " + colors.size() + " colors");
    }

    /**
     * Puts a foil copy of a card this client can already draw into the player's hand.
     * <p>A foil rather than any card, because the shine is what is being looked at - and a
     * card the client has already been told about, because the read draws from what has
     * arrived rather than asking for anything. Set on the server player so it comes back down
     * the wire the way a real one would; setting it on the client would be undone by the next
     * inventory sync, which is exactly the kind of picture that lies.
     */
    private static void holdAFoil(Minecraft client) {
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        MinecraftServer server = client.getSingleplayerServer();
        if (board == null || server == null || client.player == null) {
            fail("there was no board or server to take a card from");
            return;
        }
        dev.gathering.core.card.CardIdentity identity = board.allCardViews().stream()
                .filter(CardView.Visible.class::isInstance)
                .map(CardView.Visible.class::cast)
                .map(CardView.Visible::identity)
                .filter(seen -> seen.printing().isPresent())
                .findFirst()
                .orElse(null);
        if (identity == null) {
            fail("no card on the board had a printing to make a foil of");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        dev.gathering.core.card.CardIdentity shiny = identity.withFoil(true);
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player != null) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        dev.gathering.item.CardItem.of(dev.gathering.item.CardComponent.of(shiny)));
            }
        });
    }

    /** Whether the read has anything to draw, which is the whole of what the next step needs. */
    private static boolean theresNoCardInHand(Minecraft client) {
        return client.player == null
                || dev.gathering.item.CardItem.cardOf(client.player.getMainHandItem()).isEmpty();
    }

    /** Turns the player's head, which out in the world is what turns the card being read. */
    private static void turnTheHead(Minecraft client, float degrees) {
        if (client.player == null) {
            fail("there was nobody to turn");
            return;
        }
        client.player.setYRot(client.player.getYRot() + degrees);
    }

    /** Shows the log on the board, so a picture of it has it in. */
    private static void openTheLog(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to open the log on");
            return;
        }
        if (!board.theLogIsShowing()) {
            board.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_L, 0, 0);
        }
        if (!board.theLogIsShowing()) {
            fail("the log key was pressed at the table and no log opened");
        }
    }

    /** Whether the public log carries a line with this in it. */
    private static boolean logSays(Minecraft client, String phrase) {
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (board == null) {
            return false;
        }
        return board.log().stream()
                .map(entry -> GameLogText.render(board, entry).getString())
                .anyMatch(line -> line.contains(phrase));
    }

    /** Where the row of stone tables was stood up, so the camera can be pointed at it. */
    private static BlockPos otherTables;

    /**
     * One of each other material, in a row.
     * <p>Placed rather than crafted: what is being checked is how they look, and the recipe
     * is checked by the recipe file existing. Three tables two apart, so each is whole and
     * none of them merge into a cluster - a row of separate tables reads as a catalog,
     * where four merged ones read as one large table.
     */
    private static void standTheOtherTablesUp(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        // On the ground rather than in it: the main table is placed a block down because the
        // scene stands on the block above it, and copying that here buried three tables up to
        // their felt - which photographed as three green squares lying in a field.
        BlockPos where = client.player.blockPosition().offset(-8, 0, 6);
        otherTables = where;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            int along = 0;
            for (dev.gathering.registry.Registered<net.minecraft.world.level.block.Block> which
                    : List.of(GatheringContent.COBBLESTONE_TABLE,
                            GatheringContent.BLACKSTONE_TABLE,
                            GatheringContent.CRYING_OBSIDIAN_TABLE)) {
                BlockState state = which.get().defaultBlockState();
                BlockPos corner = where.offset(along, 0, 0);
                for (TablePart part : TablePart.values()) {
                    level.setBlock(
                            part.offsetFrom(corner), state.setValue(TableBlock.PART, part), 3);
                }
                along += 4;
            }
            System.out.println("[devscene] a cobblestone, a blackstone and a crying obsidian table");
        });
    }

    /**
     * Stands a shopkeeper and a zombie shopkeeper on the flat, in the light.
     * <p>Both, because they are two different textures for one profession and the zombie one
     * is the easier of the two to get wrong: it lives in a folder of its own that vanilla
     * looks in with the same profession key, so a file in the right place under the wrong
     * folder renders a plain zombie villager and says nothing about why.
     * <p>Held still and facing the camera. A villager left to itself wanders, turns its back
     * and goes to sleep, and a photograph of the back of a villager's head says nothing about
     * a texture drawn on its front.
     */
    private static void theShopkeepers(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        client.setScreen(null);
        BlockPos where = client.player.blockPosition().offset(0, 0, 24);
        shopkeepers = where;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            // The scene's world is peaceful, and peaceful does not merely stop zombies
            // spawning - it deletes the ones that exist, on the tick they arrive. So the
            // zombie shopkeeper was spawned, removed, and photographed as an empty patch of
            // floor. These are the last three steps of the run, so nothing already
            // photographed can be affected by changing it here.
            server.setDifficulty(net.minecraft.world.Difficulty.EASY, true);
            // A floor to stand on and a lit roof over it. The roof is not decoration: a zombie
            // under an open sky catches fire within a second or two of arriving, and a
            // photograph of a burning villager says nothing about the cloth it is wearing.
            // Glowstone because blocking the sky also takes the light away, and a texture
            // photographed in the dark is a texture nobody can judge.
            for (int x = -3; x <= 3; x++) {
                for (int z = -4; z <= 4; z++) {
                    level.setBlock(where.offset(x, -1, z),
                            net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    level.setBlock(where.offset(x, 6, z),
                            net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState(), 3);
                }
            }
            stand(level, new net.minecraft.world.entity.npc.Villager(
                    net.minecraft.world.entity.EntityType.VILLAGER, level), where, -1.0);
            stand(level, new net.minecraft.world.entity.monster.ZombieVillager(
                    net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER, level), where, 1.0);

            // And the camera in front of both of them, far enough back to hold the pair.
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .findFirst().orElse(null);
            if (player != null) {
                double atX = where.getX() + 0.5;
                double atY = where.getY() + 1.1;
                double atZ = where.getZ() + 5.0;
                player.teleportTo(atX, atY, atZ);
                player.setYRot(180f);
                player.setXRot(6f);
                player.connection.teleport(atX, atY, atZ, 180f, 6f);
            }
            System.out.println("[devscene] a shopkeeper and a zombie shopkeeper at " + where);
        });
    }

    /**
     * Puts one on the floor, in the trade, facing the camera and staying there.
     * <p>The profession has to be set through {@code setVillagerData} on both, because the
     * two classes do not share a supertype that carries it - and the level matters as well as
     * the profession: the badge on the belt is drawn from it, and a level of zero draws
     * nothing at all, which reads as a texture that failed rather than a villager who has not
     * traded yet.
     */
    private static void stand(ServerLevel level, net.minecraft.world.entity.Mob who,
            BlockPos where, double across) {
        double x = where.getX() + 0.5 + across;
        double z = where.getZ() + 0.5;
        who.moveTo(x, where.getY(), z, 180f, 0f);
        who.setNoAi(true);
        who.setPersistenceRequired();
        net.minecraft.world.entity.npc.VillagerData data =
                new net.minecraft.world.entity.npc.VillagerData(
                        net.minecraft.world.entity.npc.VillagerType.PLAINS,
                        dev.gathering.village.GatheringVillagers.SHOPKEEPER.get(), 2);
        if (who instanceof net.minecraft.world.entity.npc.Villager villager) {
            villager.setVillagerData(data);
        } else if (who instanceof net.minecraft.world.entity.monster.ZombieVillager zombie) {
            zombie.setVillagerData(data);
            // Otherwise it burns down in the daylight halfway through the photograph.
            zombie.setPersistenceRequired();
        }
        level.addFreshEntity(who);
    }

    /**
     * Steps in close enough to read the cloth, in front of one of them.
     * <p>One each rather than one of the pair, because they are two textures and the whole
     * question is whether each of them is the right one in the right place. A photograph with
     * both in it answers that only for whichever is nearer the middle.
     */
    private static void closerToTheShopkeeper(Minecraft client, double across) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || shopkeepers == null) {
            return;
        }
        BlockPos where = shopkeepers;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .findFirst().orElse(null);
            if (player == null) {
                return;
            }
            double atX = where.getX() + 0.5 + across;
            double atY = where.getY() + 1.0;
            double atZ = where.getZ() + 2.2;
            player.teleportTo(atX, atY, atZ);
            player.setYRot(180f);
            player.setXRot(3f);
            player.connection.teleport(atX, atY, atZ, 180f, 3f);
        });
    }

    /** Where the two of them are standing, so the second shot can walk up to them. */
    private static BlockPos shopkeepers;

    /** Stands the camera back from the row and looks at it. */
    private static void lookAtTheOtherTables(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || otherTables == null || client.player == null) {
            return;
        }
        client.setScreen(null);
        BlockPos where = otherTables;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .findFirst().orElse(null);
            if (player == null) {
                return;
            }
            // Off to one side and above, which is how anybody looks at furniture they are
            // deciding between rather than the flat-on view the board is drawn at.
            // Far enough back that the whole row sits above the cards the scene is holding:
            // the first framing put a crying obsidian table behind a Lightning Bolt.
            double atX = where.getX() + 4.5;
            double atY = where.getY() + 3.2;
            double atZ = where.getZ() + 11.5;
            player.teleportTo(atX, atY, atZ);
            player.setYRot(180f);
            player.setXRot(12f);
            player.connection.teleport(atX, atY, atZ, 180f, 12f);
        });
    }

    /**
     * Throws a card on the floor in front of the player.
     * <p>A real dropped item rather than an item rendered by hand: what is being looked at is
     * the model's ground transform, which is the one thing no screen in this run exercises.
     * Spawned by the server, like everything else here, so what lands on the floor is what a
     * player who pressed Q would have dropped.
     */
    private static void throwACardOnTheFloor(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("no server to drop a card on the floor of");
            return;
        }
        client.setScreen(null);
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .findFirst().orElse(null);
            if (player == null) {
                return;
            }
            net.minecraft.world.item.ItemStack card = someCardFromTheDeck(server);
            if (card.isEmpty()) {
                System.out.println("[devscene] no card to throw on the floor");
                return;
            }
            // In front of the player along the way they are actually facing, not along a
            // guessed axis. A hardcoded +Z put it behind them - this scene turns the camera
            // to look north - and the picture came back as an empty patch of grass with the
            // card lying out of shot.
            net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3
                    .directionFromRotation(0f, player.getYRot()).scale(1.3);
            net.minecraft.world.entity.item.ItemEntity dropped =
                    new net.minecraft.world.entity.item.ItemEntity(player.level(),
                            player.getX() + ahead.x, player.getY() + 0.2,
                            player.getZ() + ahead.z, card);
            dropped.setDeltaMovement(0, 0, 0);
            // Left where it lands rather than picked straight back up. setNoPickUpDelay is
            // the opposite of what this wants: the player is standing over it, so the card
            // went into the inventory on the same tick it was dropped and the picture was of
            // grass. A long delay is what a thrown item has anyway.
            dropped.setPickUpDelay(400);
            player.level().addFreshEntity(dropped);
        });
    }

    /** One real card out of the loaner deck, so the floor has a printed face on it. */
    private static net.minecraft.world.item.ItemStack someCardFromTheDeck(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (net.minecraft.world.item.ItemStack held : player.getInventory().items) {
                if (held.getItem() instanceof dev.gathering.item.CardItem) {
                    return held.copyWithCount(1);
                }
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /**
     * There really is a card on the floor to be looking at.
     * <p>Which way up it is landing cannot be read from here - it is a model transform, and
     * the only honest check of that is the picture. What this can say is that the picture has
     * something in it, so a run where the drop silently did nothing does not quietly publish
     * a photograph of some grass.
     */
    private static void aCardIsLyingFaceUp(Minecraft client) {
        if (client.level == null) {
            fail("there was no world to find a dropped card in");
            return;
        }
        for (net.minecraft.world.entity.Entity thing : client.level.entitiesForRendering()) {
            if (thing instanceof net.minecraft.world.entity.item.ItemEntity item
                    && item.getItem().getItem() instanceof dev.gathering.item.CardItem) {
                System.out.println("[devscene] a card is lying on the floor");
                return;
            }
        }
        fail("nothing was thrown on the floor, so the picture of it is a picture of grass");
    }

    /** Stands over the dropped card and looks down at it. */
    private static void lookDownAtTheFloor(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .findFirst().orElse(null);
            if (player == null) {
                return;
            }
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), 78f);
        });
    }

    /** Where the draft pod's tables are, once they have been put down. */
    private static BlockPos draftTables;

    /**
     * Builds a second cluster away from the game, seats four, and deals a cube round it.
     * <p>A real pod on real blocks rather than a view assembled on the client: the whole
     * point of the harness is that a packet somebody breaks stops the run, and a screen fed
     * by hand would go on passing after the day the payload stopped arriving. This client is
     * one of the four, so what it is looking at is a pack the server decided it may see.
     * <p>Two tables side by side along x, because seats are only ever on the north and south
     * edges - stacked the other way, the cluster buries the two edges that seat anybody.
     */
    private static void aDraftPodFormsAtASecondCluster(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to run a draft on");
            return;
        }
        BlockPos where = client.player.blockPosition().offset(-6, -1, 2);
        draftTables = where;
        java.util.UUID me = client.player.getUUID();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            BlockState state = GatheringContent.TABLE.get().defaultBlockState();
            for (int cell = 0; cell < 2; cell++) {
                BlockPos corner = where.offset(cell * 2, 0, 0);
                for (TablePart part : TablePart.values()) {
                    level.setBlock(
                            part.offsetFrom(corner), state.setValue(TableBlock.PART, part), 3);
                }
            }
            dev.gathering.block.TableSeats.take(level, where,
                    new dev.gathering.core.table.TableCell(0, 0),
                    dev.gathering.core.table.Side.NORTH, me);
            for (int other = 1; other <= 3; other++) {
                dev.gathering.block.TableSeats.take(level, where,
                        new dev.gathering.core.table.TableCell(other == 1 ? 0 : 1, 0),
                        other == 1
                                ? dev.gathering.core.table.Side.SOUTH
                                : other == 2
                                        ? dev.gathering.core.table.Side.NORTH
                                        : dev.gathering.core.table.Side.SOUTH,
                        new java.util.UUID(0L, 7000 + other));
            }

            List<CardIdentity> cube = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                cube.add(CardIdentity.ofPrinting(new java.util.UUID(0L, 9000 + index), false));
            }
            dev.gathering.block.DraftPods.Outcome outcome =
                    dev.gathering.block.DraftPods.start(level, where, cube, true);
            if (outcome != dev.gathering.block.DraftPods.Outcome.STARTED) {
                System.out.println("[devscene] FAIL a draft would not start: " + outcome);
                return;
            }
            dev.gathering.server.DraftBroadcast.sendToPod(level, where, true);
            System.out.println("[devscene] a draft pod of four opened its first pack");
        });
    }

    /** The pack that arrived is a pack, with cards in it and a decision to make. */
    private static void theDraftScreenShowsAPack(Minecraft client) {
        if (!(client.screen instanceof DraftScreen draft)) {
            fail("there was no draft screen showing a pack");
            return;
        }
        if (draft.showing().myPack().size() != 15) {
            fail("the first pack has " + draft.showing().myPack().size() + " cards, not fifteen");
            return;
        }
        if (draft.showing().picksDueFromMe() != 2) {
            fail("a pod of four is not asking for two picks: "
                    + draft.showing().picksDueFromMe());
            return;
        }
        if (draft.slotOfCard(0).isEmpty() || draft.slotOfCard(14).isEmpty()) {
            fail("the pack was not laid out - a card has no place on screen");
            return;
        }
        if (!draft.footerSaid().contains("2")) {
            fail("the screen does not say how many to pick: " + draft.footerSaid());
            return;
        }
        System.out.println("[devscene] the first pack says: " + draft.footerSaid());
    }

    /** Clicks two cards, which is what a pod of four asks for. */
    private static void pickTwoFromTheDraftPack(Minecraft client) {
        if (!(client.screen instanceof DraftScreen draft)) {
            fail("there was no draft screen to pick from");
            return;
        }
        for (int index : new int[] {2, 5}) {
            Rect slot = draft.slotOfCard(index);
            if (slot.isEmpty()) {
                fail("there was no card at " + index + " to click");
                return;
            }
            draft.mouseClicked(slot.centerX(), slot.centerY(), 0);
        }
        if (draft.chosenCount() != 2) {
            fail("clicking two cards chose " + draft.chosenCount());
        }
    }

    /**
     * The two cards clicked are marked as taken, and the screen says so.
     * <p>Read off what the footer wrote rather than off the selection again: a check that
     * asks the screen what it has selected passes even when the screen draws no sign of it,
     * and a pick of two with nothing marked is a pick nobody can tell they have made.
     */
    private static void theChosenCardsAreMarkedOnScreen(Minecraft client) {
        if (!(client.screen instanceof DraftScreen draft)) {
            fail("there was no draft screen to read a selection on");
            return;
        }
        if (draft.chosenCount() != 2) {
            fail("two clicks left " + draft.chosenCount() + " cards chosen");
            return;
        }
        if (!draft.footerSaid().contains("2 chosen")) {
            fail("the screen does not say two are chosen: " + draft.footerSaid());
            return;
        }
        System.out.println("[devscene] with two clicked, the screen says: " + draft.footerSaid());
    }

    /** And presses take, which sends the pick and hands the pack on. */
    private static void takeTheDraftPick(Minecraft client) {
        if (!(client.screen instanceof DraftScreen draft)) {
            fail("there was no draft screen to take a pick on");
            return;
        }
        press(client, net.minecraft.network.chat.Component
                .translatable("screen.gathering.draft.take").getString());
    }

    /**
     * Having picked, this drafter is waiting on the other three.
     * <p>Which is the state a draft spends most of its time in, and the one place a pod can
     * silently stall: if the server never answered, the screen would still be offering a
     * take button over a pack the pod has moved on from.
     */
    private static void theDraftIsWaitingOnTheRest(Minecraft client) {
        if (!(client.screen instanceof DraftScreen draft)) {
            fail("the draft screen closed itself after a pick");
            return;
        }
        if (!draft.showing().iHaveDeclared()) {
            fail("the pick was sent and the pod does not think this drafter has picked");
            return;
        }
        if (draft.showing().myPool().size() != 0) {
            fail("a pick landed in a pool before the packs moved: "
                    + draft.showing().myPool().size());
            return;
        }
        if (draft.showing().waitingOn().size() != 3) {
            fail("the pod is waiting on " + draft.showing().waitingOn().size() + ", not three");
            return;
        }
        System.out.println("[devscene] after picking, the screen says: " + draft.footerSaid());
    }

    /**
     * Opens the deck screen on a deck in this client's hand.
     * <p>Through the hook the keybind uses rather than by constructing the screen, so this
     * goes on passing only while the way a player actually reaches it still works.
     * <p>The deck is put in the client's own hand, which is all this step needs: what is
     * checked here is what the screen draws, and the screen reads the client's copy. What
     * the server does when a land button is pressed is checked where it can be checked
     * properly - over the deck arithmetic itself, without a card service in the way.
     */
    private static void openTheDeckScreen(Minecraft client) {
        if (client.player == null) {
            fail("there was no player to open a deck screen for");
            return;
        }
        net.minecraft.world.InteractionHand holding = null;
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            if (dev.gathering.item.DeckItem.deckOf(client.player.getItemInHand(hand)).isPresent()) {
                holding = hand;
                break;
            }
        }
        if (holding == null) {
            // With cards in it. A deck of nothing photographs as an empty panel, and this
            // scene photographed exactly that for weeks under the name "building a deck":
            // the land buttons were there, the Done button was there, and there was no
            // evidence either way about the thing the screen is for.
            dev.gathering.item.DeckComponent made = new dev.gathering.item.DeckComponent(
                    "Pool", "", java.util.Optional.empty(),
                    someCards(client, 4), someCards(client, 1), List.of());
            client.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    dev.gathering.item.DeckItem.of(made));
            holding = net.minecraft.world.InteractionHand.MAIN_HAND;
        }
        dev.gathering.service.DeckScreenHook.Binding.open(holding);
    }

    /**
     * Every sleeve that carries a picture has one the game can actually load.
     * <p>The pictures are Minecraft's own item and block art, named by the sleeve itself, and
     * a path that is wrong by one letter draws the purple checkerboard and says nothing. This
     * is the only place in the run with a resource manager to ask, which is why it is asked
     * here rather than in a test.
     */
    private static void everySleeveHasItsPicture(Minecraft client) {
        for (dev.gathering.core.card.Sleeve sleeve : dev.gathering.core.card.Sleeve.values()) {
            if (!sleeve.hasEmblem()) {
                continue;
            }
            var where = CardSleeves.emblem(sleeve);
            if (client.getResourceManager().getResource(where).isEmpty()) {
                fail("the " + sleeve.name() + " sleeve points at " + where + ", which is not there");
                return;
            }
        }
        System.out.println("[devscene] every sleeve's picture loads");
    }

    /** Clicks that sleeve's swatch, the way a player picks one. */
    private static void pickTheSleeve(Minecraft client, dev.gathering.core.card.Sleeve wanted) {
        if (!(client.screen instanceof SleeveScreen picker)) {
            fail("there was no sleeve picker to choose from");
            return;
        }
        Rect swatch = picker.swatchOf(wanted);
        if (swatch.isEmpty()) {
            fail("the picker draws no swatch for " + wanted.name());
            return;
        }
        picker.mouseClicked(swatch.centerX(), swatch.centerY(), 0);
        System.out.println("[devscene] picked the " + wanted.name() + " sleeve");
    }

    /** The deck this client is holding, if it is holding one. */
    private static java.util.Optional<dev.gathering.item.DeckComponent> deckInHand(Minecraft client) {
        if (client.player == null) {
            return java.util.Optional.empty();
        }
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            var found = dev.gathering.item.DeckItem.deckOf(client.player.getItemInHand(hand));
            if (found.isPresent()) {
                return found;
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * A few loose cards in the inventory, so the pockets builder has something to show.
     * <p>Handed out on the server, like every other thing this scene gives the player: the
     * client's copy of the inventory is a copy, and a builder that read cards the server has
     * never heard of would pick cards that cannot be sleeved.
     */
    private static void looseCardsToPickFrom(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to hand loose cards out on");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        List<dev.gathering.item.CardComponent> cards = someCards(client, 1);
        if (cards.isEmpty()) {
            fail("there were no looked-up cards to fill the pockets with");
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                fail("the player went away before their pockets could be filled");
                return;
            }
            for (dev.gathering.item.CardComponent card : cards) {
                ItemStack stack = dev.gathering.item.CardItem.of(card);
                stack.setCount(4);
                if (!player.getInventory().add(stack)) {
                    fail("there was no room in the inventory for a loose card");
                }
            }
        });
    }

    /**
     * The gallery: every look wearing the same three screens.
     * <p>Three because they are the three that show most of what a look is - a grid of cards
     * in a recessed box, a panel with a list down it, and the full-screen read, which is the
     * one thing drawn over the world rather than over a backdrop. A five-colour card for the
     * read, so every mana orb is on screen at once.
     * <p>Driven by arithmetic rather than by a case each: there are as many looks as the
     * resource packs installed happen to declare, which is not a number this file can know.
     */
    private static void gallery(Minecraft client, int at) {
        List<GuiTheme> looks = GuiThemes.all();
        int which = at / STEPS_PER_LOOK;
        int phase = at % STEPS_PER_LOOK;
        if (which >= looks.size()) {
            finish(client, "done");
            return;
        }
        GuiTheme look = looks.get(which);
        String named = look.id().getPath();
        switch (phase) {
            case 0 -> {
                GuiThemes.wear(look);
                if (client.screen != null) {
                    client.screen.onClose();
                }
                readingACard(client, false);
                openTheCollection(client);
            }
            case 1 -> shoot(client, "look-" + named + "-1-collection");
            case 2 -> {
                if (client.screen != null) {
                    client.screen.onClose();
                }
                openTheDeckScreen(client);
            }
            case 3 -> shoot(client, "look-" + named + "-2-deck");
            case 4 -> {
                if (client.screen != null) {
                    client.screen.onClose();
                }
                client.setScreen(null);
                aFiveColorCardInHand(client);
                readingACard(client, true);
            }
            case 5 -> {
                shoot(client, "look-" + named + "-3-reading");
                readingACard(client, false);
            }
            // The board itself, which is the screen a game is actually played on and the one
            // this gallery did not show. Reported from a real session: "a few of the in table
            // views look really bad on some of the themes. for example the top bar on in the
            // table when using the future sight theme" - which nothing here would ever have
            // caught, because every look was only ever photographed wearing a menu.
            case 6 -> {
                if (client.screen != null) {
                    client.screen.onClose();
                }
                client.setScreen(null);
                walkToTheTable(client);
            }
            case 7 -> askTheTableForTheBoard(client);
            case 8 -> client.setScreen(new TableScreen(table));
            case 9 -> {
                expectScreen(client, "the board wearing the " + named + " look",
                        TableScreen.class);
                shoot(client, "look-" + named + "-4-the-board");
            }
            // And the same board zoomed out, where a themed border has the fewest pixels to
            // be itself in: "GUI borders also scale with zoom, that makes certain zones (like
            // graveyard or exile) look really bad when zoomed out on certain themes".
            case 10 -> scrollTheBoard(client, -GALLERY_ZOOM_OUT);
            default -> {
                shoot(client, "look-" + named + "-5-zoomed-out");
                // Back where it was, so the next look starts from the same board this one did.
                scrollTheBoard(client, GALLERY_ZOOM_OUT);
            }
        }
        advance(SETTLE);
    }

    /**
     * Holds the read key down, or lets it go.
     * <p>The overlay asks a supplier whether the key is held, so this answers that question
     * rather than pretending to press anything: there is no way to hold a real key across a
     * scripted frame, and a screenshot of a keyboard event is not what is wanted anyway.
     * <p>The loader's own binding is replaced for the rest of the run. That is fine and only
     * fine here: the gallery is the last thing this scene does before it quits.
     */
    private static void readingACard(Minecraft client, boolean held) {
        CardZoomOverlay.bindKeyState(() -> held);
    }

    /**
     * A card of all five colors in the player's hand, for the read.
     * <p>Five colors so the whole palette of orbs is on one screen, which is the thing worth
     * comparing between looks. Set client-side, because what the read draws is what this
     * client believes it is holding.
     */
    private static void aFiveColorCardInHand(Minecraft client) {
        if (client.player == null) {
            fail("there was no player to hand a card to");
            return;
        }
        var service = dev.gathering.service.CardDataService.active().orElse(null);
        if (service == null) {
            fail("there was no card service to look a five-color card up with");
            return;
        }
        // Named rather than searched: this is the picture's subject, and a run that quietly
        // photographed some other card would be a gallery of the wrong thing.
        for (String name : List.of("Progenitus", "Cromat", "Sliver Queen", "Child of Alara")) {
            var found = service.findByName(name).join().orElse(null);
            if (found == null) {
                continue;
            }
            var card = dev.gathering.item.CardComponent.of(
                    dev.gathering.core.card.CardIdentity.ofPrinting(found.scryfallId(), false));
            client.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    dev.gathering.item.CardItem.of(card));
            return;
        }
        fail("none of the five-color cards could be looked up");
    }

    /** Empties both hands, so what is left to say about the block is the sweep. */
    private static void putTheDeckAway(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("there was no server to put the deck away on");
            return;
        }
        java.util.UUID who = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                fail("the player went away before their hands could be emptied");
                return;
            }
            for (net.minecraft.world.InteractionHand hand
                    : net.minecraft.world.InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.isEmpty()) {
                    continue;
                }
                player.setItemInHand(hand, ItemStack.EMPTY);
                // Into the pack rather than the hotbar, and not through Inventory#add: that
                // fills the first free slot, which is the hotbar slot just emptied, so the
                // deck went straight back into the hand it was taken out of.
                if (!intoThePack(player, held)) {
                    fail("there was nowhere to put the deck down");
                }
            }
        });
    }

    /** Puts one stack in a slot above the hotbar, so it cannot land back in the hand. */
    private static boolean intoThePack(ServerPlayer player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int slot = net.minecraft.world.entity.player.Inventory.getSelectionSize();
                slot < inventory.items.size(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, stack);
                return true;
            }
        }
        return false;
    }

    /**
     * Pressing a color's orb actually turns that color on.
     * <p>The orb is a picture and the state is a line under it, so neither is something a
     * photograph can be trusted to settle. Asked of the query the screen is really holding.
     */
    private static void aColorFilterIsOn(Minecraft client, String color) {
        if (!(client.screen instanceof CollectionScreen box)) {
            fail("there was no collection screen to read the color filter of");
            return;
        }
        if (!box.colorsOn().contains(color)) {
            fail("pressing the " + color + " orb left the filter showing \""
                    + box.colorsOn() + "\"");
        }
    }

    /**
     * A collection opened by somebody carrying loose cards offers to take all of them.
     * <p>The gesture happens on the block, before this screen is open, so this line is the
     * only place it can be found - which makes a line that stopped appearing a feature that
     * silently stopped existing. Asked of the screen rather than read off the picture.
     */
    private static void theSweepIsOfferedToSomebodyCarryingCards(Minecraft client) {
        if (!(client.screen instanceof CollectionScreen box)) {
            fail("there was no collection screen to read the hint on");
            return;
        }
        net.minecraft.network.chat.Component hint = box.blockGestureHint();
        String wanted = net.minecraft.network.chat.Component
                .translatable("screen.gathering.collection.hint_sweep").getString();
        if (hint == null || !hint.getString().equals(wanted)) {
            fail("a collection opened by somebody carrying loose cards said "
                    + (hint == null ? "nothing" : "\"" + hint.getString() + "\"")
                    + " rather than how to put them all away");
        }
    }

    /** The builder opened from a deck screen is over the pockets, not over a box. */
    private static void theBuilderIsOverMyPockets(Minecraft client) {
        if (!(client.screen instanceof DeckBuilderScreen builder)) {
            fail("there was no builder to check the source of");
            return;
        }
        if (!builder.overThePockets()) {
            fail("the deck screen opened a builder over a collection rather than over the"
                    + " cards being carried");
            return;
        }
        if (builder.showing() <= 0) {
            fail("a builder over pockets holding twelve loose cards showed none of them");
        }
    }

    /**
     * Shift-click puts every copy in at once.
     * <p>The whole point of the row: one press per printing rather than one per card. A
     * shortcut that quietly added one would look identical in a photograph.
     */
    private static void oneShiftClickTakesEveryCopy(Minecraft client) {
        if (!(client.screen instanceof DeckBuilderScreen builder)) {
            fail("there was no builder to shift-click in");
            return;
        }
        int copies = builder.leftOf(0);
        if (copies < 2) {
            fail("the first card of the pockets builder had " + copies
                    + " copies, so shift-clicking it proves nothing");
            return;
        }
        int before = builder.deckSize();
        builder.clickCard(0, 0, true);
        if (builder.deckSize() != before + copies) {
            fail("shift-clicking a card with " + copies + " copies added "
                    + (builder.deckSize() - before) + " of them");
        }
    }

    /** A deck screen with a deck in it lists the deck. */
    private static void theDeckScreenListsItsCards(Minecraft client) {
        if (!(client.screen instanceof DeckContentsScreen deck)) {
            fail("there was no deck screen to read");
            return;
        }
        if (deck.listedRows() <= 0) {
            fail("a deck screen with cards in the deck listed none of them");
        }
    }

    /**
     * A few cards this client already knows the names of.
     * <p>Out of the cache the collection was stocked from, so the deck screen photographs as
     * a list of card names rather than a list of "not looked up yet".
     */
    private static List<dev.gathering.item.CardComponent> someCards(Minecraft client, int each) {
        List<dev.gathering.item.CardComponent> cards = new java.util.ArrayList<>();
        var service = dev.gathering.service.CardDataService.active().orElse(null);
        if (service == null) {
            return cards;
        }
        for (String name : List.of("Lightning Bolt", "Counterspell", "Grizzly Bears")) {
            service.findByName(name).join().ifPresent(card -> {
                var one = dev.gathering.item.CardComponent.of(
                        dev.gathering.core.card.CardIdentity.ofPrinting(card.scryfallId(), false));
                for (int copy = 0; copy < each; copy++) {
                    cards.add(one);
                }
            });
        }
        return cards;
    }

    /**
     * All five basics are on the deck screen, each with somewhere to be clicked.
     * <p>A drafted pool is forty-five spells: without these there is no legal deck to build
     * from one at all, so a button missing or drawn off the panel is a draft that ends in a
     * deck nobody can put down.
     */
    private static void everyBasicLandHasAButton(Minecraft client) {
        if (!(client.screen instanceof DeckContentsScreen deck)) {
            fail("there was no deck screen to look for lands on");
            return;
        }
        java.util.Set<String> labeled = new java.util.LinkedHashSet<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : deck.children()) {
            if (!(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)) {
                continue;
            }
            for (dev.gathering.core.card.BasicLand land
                    : dev.gathering.core.card.BasicLand.values()) {
                String name = net.minecraft.network.chat.Component
                        .translatable(land.translationKey()).getString();
                // By the glyph the button is actually marked with, so a button that lost its
                // symbol is a button this stops finding rather than one it quietly counts.
                String marked = dev.gathering.client.ManaText.of(land.symbol()).getString();
                if (marked.isBlank() || !widget.getMessage().getString().equals(marked)) {
                    continue;
                }
                if (widget.getWidth() < 4 || widget.getHeight() < 4) {
                    fail("the " + name + " button is " + widget.getWidth()
                            + " by " + widget.getHeight() + ", which nobody can click");
                    return;
                }
                if (widget.getX() < 0 || widget.getRight() > deck.width
                        || widget.getY() < 0 || widget.getBottom() > deck.height) {
                    fail("the " + name + " button is drawn off the screen");
                    return;
                }
                labeled.add(name);
            }
        }
        if (labeled.size() != dev.gathering.core.card.BasicLand.values().length) {
            fail("only " + labeled.size() + " basic lands have buttons: " + labeled);
            return;
        }
        System.out.println("[devscene] the deck screen offers " + labeled);
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
     * <p>The near mat's middle. Aimed at the layout rather than at a remembered pixel, so it
     * keeps pointing at the card when the layout changes - which it has, twice.
     */
    private static int[] cardPoint(Minecraft client) {
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int[] middleOfTheMat = {width / 2, height / 4};
        if (!(client.screen instanceof TableScreen board)) {
            return middleOfTheMat;
        }
        if (!(board.board() instanceof SurfaceBoard)) {
            // On the screen board the card's rectangle is already in pixels, so ask where a
            // card actually is rather than aiming at the middle of the mat and hoping. The
            // middle is only right until something moves: a card dragged back out of the
            // graveyard lands where it was dropped, and a verb key aimed at empty felt
            // silently does nothing, which is exactly the kind of pass that means nothing.
            double[] card = playedCardSpot(client);
            return card == null
                    ? middleOfTheMat
                    : new int[] {(int) card[0], (int) card[1]};
        }
        // On the block there is no screen rectangle to aim at: the board is drawn by the world
        // and the only way back from a card to a pixel is the way the pointer goes forwards.
        // So sweep the window, ask the pointer what each point is over, and keep the one that
        // lands nearest the card. Two thousand rays once a run, which is nothing, and it
        // exercises the same pick the player's cursor uses rather than a copy of it.
        double[] wanted = playedCardSpot(client);
        return wanted == null ? middleOfTheMat : screenPointFor(client, wanted, middleOfTheMat);
    }

    /**
     * The pixel that points at a given spot on the felt, found by sweeping the window.
     * <p>There is no screen rectangle to aim at once the board is the real table: the only
     * way back from a place on the felt to a pixel is the way the pointer goes forwards. So
     * every fourth pixel is asked what it is over and the nearest answer wins - two thousand
     * rays once a run, which is nothing, and it exercises the same pick the player's cursor
     * uses rather than a copy of it.
     */
    private static int[] screenPointFor(Minecraft client, double[] wanted, int[] fallback) {
        if (table == null) {
            return fallback;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        int[] best = fallback;
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
                return new double[] {where.centerX(), where.centerY()};
            }
        }
        return null;
    }

    /** Drags the first card in hand onto the near mat, press and release, like a player. */
    private static void playACard(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            System.out.println("[devscene] no board to play onto; screen is "
                    + (client.screen == null ? "none" : client.screen.getClass().getSimpleName()));
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        TableScreenLayout layout = TableScreenLayout.of(width, height);
        HandFan.Slot first = HandFan.slot(layout.hand(), 7, 0, -1);
        // The middle of the mat, not wherever a card already is: cardPoint aims at a card,
        // and dropping onto one would stack them where the scene wants two side by side.
        int[] onto = {width / 2, height / 4};

        board.mouseClicked(first.where().centerX(), first.where().centerY(), 0);
        board.mouseDragged(onto[0], onto[1], 0,
                onto[0] - first.where().centerX(), onto[1] - first.where().centerY());
        board.mouseReleased(onto[0], onto[1], 0);
        System.out.println("[devscene] dragged a card from the hand onto the table");
    }

    /** Where one of this player's zones is on screen, asked of the screen that drew it. */
    private static Rect zoneRect(Minecraft client, int index) {
        if (!(client.screen instanceof TableScreen board)) {
            return Rect.NONE;
        }
        return ClientTableState.seatAt(table)
                // The board's own count, not a guess at it. The column's rectangles are laid
                // out against how many slots it holds, so asking for four while the board
                // draws five aims at a rectangle nothing is drawn in.
                .map(seat -> board.board().pileRect(seat, index, board.pilesShowing()))
                .orElse(Rect.NONE);
    }

    /**
     * Presses a card in hand and drags it over a zone, without letting go.
     * <p>Split from {@link #dropWhereItIsAimed} so there is a frame to photograph while the
     * card is still in the air. What lands where is already checked; what was never looked at is
     * whether the player can tell, before they commit, which of four slots a card an inch
     * from the column is going into.
     */
    private static void startDraggingOntoAZone(Minecraft client, int index) {
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty() || !(client.screen instanceof TableScreen board)) {
            fail("no zone to aim at");
            return;
        }
        // A card already lying on the felt rather than one in the hand: the fan lifts and
        // re-lays its cards as the cursor crosses it, so a scripted press on a hand slot is
        // aiming at something that moves. What is being photographed is the aim, not the fan.
        //
        // The card's own rectangle, not cardPoint - that one sweeps rays at the board on the
        // block and falls back to a guess at the middle of the mat when it is handed the
        // seated one, and a press on a guess grabs nothing at all.
        double[] spot = playedCardSpot(client);
        if (spot == null) {
            fail("there was no card on the felt to drag at a zone");
            return;
        }
        int[] from = {(int) Math.round(spot[0]), (int) Math.round(spot[1])};
        aimedAt = new int[] {(int) zone.centerX(), (int) zone.centerY()};
        // The real cursor as well as the synthetic drag. The board draws the card in the air
        // at where the mouse actually is, so a drag that only reports a position leaves the
        // card hanging wherever the cursor was last really put.
        hover(client, from);
        board.mouseClicked(from[0], from[1], 0);
        board.mouseDragged(aimedAt[0], aimedAt[1], 0,
                aimedAt[0] - from[0], aimedAt[1] - from[1]);
        // The cursor is moved last and nothing is called after it, because the board works
        // out what a held card is over while it draws, from the cursor the game hands it -
        // not from the coordinates a drag was reported with. Moved before the drag, the
        // position was gone again by the time a frame asked, and the aim came back as the
        // battlefield spot the card was picked up from, which is a true answer to the wrong
        // question and reads exactly like the board refusing to aim.
        hover(client, aimedAt);
        System.out.println("[devscene] holding a card over zone " + index
                + " at " + aimedAt[0] + "," + aimedAt[1]);
    }

    /**
     * Checks the board worked out which slot the card in the air is over, and says which.
     * <p>Two halves, tested apart, because the cursor cannot be moved mid-drag from here:
     * {@code glfwSetCursorPos} does not reach a frame that already has a button held, so a
     * scripted drag reports one position and the board draws from another. That is the
     * harness's problem and not the mod's - the first time this was asked it read "slot -1",
     * which was the honest answer for the spot the card had been picked up from.
     * <p>So this checks the half that is reachable: that the board computes an answer at all
     * and names the slot it arrived at. Whether a lit slot is drawn lit cannot be scripted
     * from here - the aim is recomputed from the cursor on every frame, so a value set by
     * hand is gone before the next picture is taken.
     */
    private static void theBoardWorksOutWhatIsUnderTheCard(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to read an aim from");
            return;
        }
        String said = board.aimReport();
        System.out.println("[devscene] aim: " + said);
        if (!said.startsWith("cursor ")) {
            fail("the board made nothing of the card in the air: " + said);
            return;
        }
        // The card is being held over the graveyard, which is slot 0 of the column.
        int wanted = Zone.PILES.indexOf(Zone.GRAVEYARD);
        if (board.aimedSlot() != wanted) {
            fail("a card held over the graveyard is aimed at slot " + board.aimedSlot()
                    + ", not " + wanted + ": " + said);
            return;
        }
        // And the slot that lit up is the slot it is aimed at. These are worked out in two
        // places that cannot see each other - the aim while the held card is drawn, the light
        // while the column is - so a card can be aimed correctly at a zone that never lights,
        // which is the same to a player as not being aimed at all.
        if (board.litSlot() != wanted) {
            fail("slot " + wanted + " is aimed at but slot " + board.litSlot()
                    + " is the one drawn lit");
            return;
        }
        System.out.println("[devscene] the slot a card is aimed at is the slot that lights up");
        everySlotAnswersForItself(client, board);
    }

    /**
     * Every zone slot the board draws names itself when asked what is at its middle.
     * <p>This is the half of the aim that decides everything, and the half that can drift.
     * Two pieces of arithmetic have to agree - the one that lays a slot out and the one that
     * says which slot a point is on - and they are told the count separately. Give one of
     * them four while the other draws five and a card lands in a zone that never lit up,
     * which reads as a broken highlight rather than as two numbers disagreeing.
     * <p>Asked of the middle of each slot's own drawn rectangle, so it is the board checking
     * itself rather than the run checking a layout it worked out separately.
     */
    private static void everySlotAnswersForItself(Minecraft client, TableScreen board) {
        SeatId seat = ClientTableState.seatAt(table).orElse(null);
        if (seat == null) {
            fail("nobody is sitting at the table to check the zone column of");
            return;
        }
        int count = board.pilesShowing();
        for (int index = 0; index < count; index++) {
            Rect slot = board.board().pileRect(seat, index, count);
            if (slot.isEmpty()) {
                fail("slot " + index + " of " + count + " is drawn nowhere");
                return;
            }
            int said = board.slotUnder(seat, (int) slot.centerX(), (int) slot.centerY());
            if (said != index) {
                fail("the middle of slot " + index + " (" + slot + ") is where the board draws "
                        + Zone.PILES.get(index) + ", but asked what is there it says slot "
                        + said + " - the layout and the hit test disagree");
                return;
            }
        }
        System.out.println("[devscene] all " + count
                + " zone slots answer for their own rectangle");
    }

    /**
     * Checks the library's menu offers each of these by name.
     * <p>By the words on the row rather than by how many rows there are, because a count
     * passes while an entry is being renamed out from under it - and these are the rows a
     * player goes looking for when they want to do the thing.
     */
    private static void theLibraryOffers(Minecraft client, String... wanted) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to read a library menu on");
            return;
        }
        for (String key : wanted) {
            // By key, not by the English. An assertion written against the words drifts the
            // moment somebody rewords an entry, and then it is either a false failure or,
            // worse, quietly rewritten to whatever the menu now says.
            String label = Component.translatable("menu.gathering.table." + key).getString();
            if (!board.hasMenuEntry(label)) {
                fail("the library's menu does not offer " + key + " (\"" + label + "\")");
                return;
            }
        }
        System.out.println("[devscene] the library offers all " + wanted.length + " verbs");
    }

    /** Presses one row of the menu that is already open over the library. */
    private static void pressLibraryRow(Minecraft client, String key) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to press a library row on");
            return;
        }
        String label = Component.translatable("menu.gathering.table." + key).getString();
        if (!board.pressMenuEntry(label)) {
            fail("the library's menu would not press " + key + " (\"" + label + "\")");
        }
    }

    /** Where the held card is hovering, so the next step can drop it there. */
    private static int[] aimedAt;

    /** Lets the held card go where it is being aimed. */
    private static void dropWhereItIsAimed(Minecraft client) {
        if (!(client.screen instanceof TableScreen board) || aimedAt == null) {
            fail("there was no aimed card to drop");
            return;
        }
        board.mouseReleased(aimedAt[0], aimedAt[1], 0);
        System.out.println("[devscene] dropped the card where it was aimed");
    }

    /**
     * Drags the top card off a zone and onto the middle of the mat.
     * <p>The other half of the gesture: a zone that only ever swallowed cards was half a
     * zone, and a scripted run that only ever put cards in never noticed.
     */
    private static void dragOutOfAZone(Minecraft client, int index) {
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty() || !(client.screen instanceof TableScreen board)) {
            fail("no zone " + index + " to drag out of");
            return;
        }
        int[] onto = {client.getWindow().getGuiScaledWidth() / 2,
                client.getWindow().getGuiScaledHeight() / 3};
        board.mouseClicked(zone.centerX(), zone.centerY(), 0);
        board.mouseDragged(onto[0], onto[1], 0,
                onto[0] - zone.centerX(), onto[1] - zone.centerY());
        board.mouseReleased(onto[0], onto[1], 0);
        System.out.println("[devscene] dragged a card out of zone " + index);
    }

    /** Whether the public log carries a line with this key, which is how an event proves it landed. */
    private static boolean theLogMentions(String key) {
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (view == null) {
            return false;
        }
        return view.log().stream().anyMatch(entry -> entry.key().equals(key));
    }

    /** How many cards are sitting in one of this player's zones, as the client sees it. */
    private static int countIn(Zone zone) {
        SeatId seat = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (seat == null || view == null) {
            return -1;
        }
        ZoneView contents = view.seat(seat).zone(zone);
        return contents == null ? 0 : contents.count();
    }

    /** Clicks a zone with the given button, which should open what is in it. */
    private static void clickAZone(Minecraft client, int index, int button) {
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty() || client.screen == null) {
            System.out.println("[devscene] no zone " + index + " to click");
            return;
        }
        client.screen.mouseClicked(zone.centerX(), zone.centerY(), button);
        client.screen.mouseReleased(zone.centerX(), zone.centerY(), button);
    }

    /**
     * A watcher's board runs to the bottom of the window, because they have no hand.
     * <p>The strip along the bottom belongs to a hand, and reserving it for somebody who has
     * none costs them twice: the board is fitted into a shorter window and pushed up under
     * the status row, and the fifth of the felt beneath the strip stops answering the mouse -
     * so a graveyard that happens to lie there cannot be opened by the one person at the
     * table whose whole reason for being there is reading it.
     */
    private static void theFeltRunsToTheBottomForAWatcher(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to measure a watcher's felt on");
            return;
        }
        if (ClientTableState.seatAt(table).isPresent()) {
            fail("this check is about somebody with no seat, and this client has one");
            return;
        }
        int nearTheBottom = client.getWindow().getGuiScaledHeight() - 8;
        if (!board.feltReachesDownTo(nearTheBottom)) {
            fail("a watcher's board stops short of the bottom, leaving a strip for a hand "
                    + "they do not have");
            return;
        }
        System.out.println("[devscene] the watcher's felt reaches the bottom of the window");
    }

    /**
     * Opens the table menu and asks it for a token, which is the first of two questions.
     * <p>Worth walking because it is the only place in the mod that takes a line of typing
     * from a player, and because two child screens in a row is the shape most likely to
     * strand somebody: the second one's way back has to be the board and not the first.
     */
    private static void openTheTokenQuestion(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to ask for a token from");
            return;
        }
        String wanted = net.minecraft.network.chat.Component
                .translatable("menu.gathering.table.make_token").getString();
        if (!openTheTableMenu(client, board, wanted)) {
            fail("no felt on the board offered a table menu to make a token from");
            return;
        }
        if (!board.pressMenuEntry(wanted)) {
            fail("the table menu offers no way to make a token");
        }
    }

    /**
     * Types a name into the deck screen's title.
     * <p>The only box on that screen, and the point of it: a deck started by putting two
     * cards together has no name, and this is where it gets one.
     */
    private static void nameTheDeck(Minecraft client, String name) {
        if (client.screen == null) {
            fail("there was no deck screen to name a deck on");
            return;
        }
        for (GuiEventListener child : client.screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox box) {
                client.screen.setFocused(box);
                box.setFocused(true);
                // Cleared first, because the deck this scene opens is a drafted pool and
                // already has one. Typing appends at the cursor, which is right, and made
                // this read "PoolBear Tribal" the first time it ran.
                box.setValue("");
                for (char letter : name.toCharArray()) {
                    box.charTyped(letter, 0);
                }
                if (!box.getValue().equals(name)) {
                    fail("a deck's title would not take a name: " + box.getValue());
                }
                return;
            }
        }
        fail("the deck screen has nowhere to type a name");
    }

    /** Types into whatever field a screen put the cursor in. */
    private static void typeInto(Minecraft client, String text) {
        if (client.screen == null) {
            fail("there was no screen to type into");
            return;
        }
        for (char letter : text.toCharArray()) {
            client.screen.charTyped(letter, 0);
        }
    }

    /**
     * Drags the last card of a scry to the front and checks the order followed it.
     * <p>A press that has not moved is a click and toggles the card instead, so the drag is
     * made the way a hand makes one: press, move, release somewhere else. Checking the order
     * rather than the picture, because a row that looks rearranged and sends the old order to
     * the server is the failure worth catching.
     */
    private static void theKeptCardsCanBeReordered(Minecraft client, PileScreen pile) {
        java.util.List<CardInstanceId> before = pile.orderOnTop();
        if (before.size() < 2) {
            fail("a scry of three offered fewer than two cards to order: " + before.size());
            return;
        }
        Rect last = pile.slotOfCard(before.size() - 1);
        Rect first = pile.slotOfCard(0);
        if (last.isEmpty() || first.isEmpty()) {
            fail("the scry box had nowhere to drag a card from or to");
            return;
        }
        pile.mouseClicked(last.centerX(), last.centerY(), 0);
        pile.mouseDragged(first.centerX(), first.centerY(), 0, 0, 0);
        pile.mouseReleased(first.centerX(), first.centerY(), 0);

        java.util.List<CardInstanceId> after = pile.orderOnTop();
        if (after.size() != before.size()) {
            fail("dragging a scried card changed how many are being kept: "
                    + before.size() + " to " + after.size());
            return;
        }
        if (!after.get(0).equals(before.get(before.size() - 1))) {
            fail("dragging the last scried card to the front did not put it first");
            return;
        }
        System.out.println("[devscene] the kept cards can be put in an order");
    }

    /**
     * Checks something is actually crossing the felt, rather than having teleported.
     * <p>Cards used to change zones with nothing in between: in a library one frame and in a
     * hand the next, with a line of text as the only account of it. This is the check that
     * the journey happens at all - and it has to be made while the journey is going on, which
     * is why the step before it waits a moment rather than settling.
     */
    private static void aCardIsInTheAir(Minecraft client, String after) {
        if (table == null) {
            fail("no table to watch a card cross");
            return;
        }
        java.util.List<ClientCardFlights.Flight> flying =
                ClientCardFlights.at(table, ClientCardFlights.now());
        if (flying.isEmpty()) {
            fail("nothing was crossing the felt after " + after + ": the card teleported");
            return;
        }
        ClientCardFlights.Flight first = flying.get(0);
        System.out.println("[devscene] after " + after + ", a card is crossing from "
                + first.move().from().zone() + " to " + first.move().to().zone());
    }

    /**
     * Checks a shuffled library is visibly being shuffled.
     * <p>A shuffle moves no card between zones and changes no count, and the order it changes
     * is the order nobody is entitled to know - so it is the one move that leaves the board
     * looking exactly as it did. Without this it is a line in the log and nothing else.
     */
    private static void aPileIsBeingShaken(Minecraft client) {
        if (table == null) {
            fail("no table to watch a shuffle at");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("no seat to watch a shuffle from");
            return;
        }
        long shaking = ClientTableNews.shakingFor(
                table, me, Zone.LIBRARY, ClientCardFlights.now());
        if (shaking < 0) {
            fail("a shuffled library was not shaking: the shuffle showed nothing at all");
            return;
        }
        System.out.println("[devscene] a shuffled library has been shaking for "
                + shaking + "ms");
    }

    /**
     * Checks that framing the whole table shows the whole table.
     * <p>Every mat, whole, with its borders inside the view - which is the claim the framing
     * makes and the one thing that makes it worth having a key for. A mat fitted flush
     * against the edge of the view has a border drawn on the same row of pixels as whatever
     * bounds it, so it has no visible edge at all and reads as a crop.
     */
    private static void theWholeTableIsOnScreen(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to frame");
            return;
        }
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (view == null || view.seats().isEmpty()) {
            fail("there was no table to frame");
            return;
        }
        int wide = client.getWindow().getGuiScaledWidth();
        int high = client.getWindow().getGuiScaledHeight();
        for (SeatView seat : view.seats()) {
            Rect mat = board.board().matRect(seat.seat());
            if (mat.isEmpty()) {
                fail("seat " + seat.seat().index() + " had no mat to frame");
                return;
            }
            if (mat.x() <= 0 || mat.y() <= 0 || mat.right() >= wide || mat.bottom() >= high) {
                fail("framing the whole table left seat " + seat.seat().index()
                        + "'s mat against the edge of the view: " + mat
                        + " in " + wide + " by " + high);
                return;
            }
        }
        System.out.println("[devscene] the whole table is framed, every mat whole");
    }

    /**
     * Dyes the felt on the server, with nothing else touching the world.
     * <p>Through the block entity rather than by putting dye in a hand and clicking, because
     * what is being photographed is whether the client redraws - and a right-click on a table
     * is a block update in its own right, which would rebuild the chunk anyway and hide the
     * exact fault this is here for.
     */
    private static void dyeTheTable(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            fail("there was no server to dye a table on");
            return;
        }
        BlockPos origin = table;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            dev.gathering.block.TableBlock.entityAt(level, origin)
                    .ifPresent(felt -> felt.dye(net.minecraft.world.item.DyeColor.PURPLE));
            System.out.println("[devscene] dyed the felt purple");
        });
    }

    /** How far each pan drag goes, in window units: unambiguous, and well short of the edge. */
    private static final int PAN_BY = 40;

    /**
     * Drags the board with the middle button, the way a player slides a table about.
     * <p>Through the screen's own mouse handlers rather than by calling the camera, because
     * what is being checked is the whole path from a drag to a picture - and the step in the
     * middle, turning pixels into blocks, is exactly the one that was wrong.
     */
    private static void dragTheBoard(Minecraft client, int acrossPixels, int downPixels) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to drag");
            return;
        }
        int fromX = client.getWindow().getGuiScaledWidth() / 2;
        int fromY = client.getWindow().getGuiScaledHeight() / 2;
        board.mouseClicked(fromX, fromY, 2);
        board.mouseDragged(fromX + acrossPixels, fromY + downPixels, 2, acrossPixels, downPixels);
        board.mouseReleased(fromX + acrossPixels, fromY + downPixels, 2);
        pannedBy = new int[] {acrossPixels, downPixels};
        System.out.println("[devscene] dragged the board " + acrossPixels + "," + downPixels);
    }

    /** How far the last drag asked the board to move. */
    private static int[] pannedBy;

    /**
     * Checks the board moved as far as the hand did, and the same way.
     * <p>The felt under a known pixel is watched across the drag: slide the window forty
     * units and the place that was under that pixel has to end up forty units away, in the
     * direction the hand went. Both halves have failed. The board used to convert a drag with
     * a fixed two hundred and twenty pixels per block, which is about right at one height and
     * out by a factor of four at the ends of a zoom range that is now eleven-fold - so this is
     * asked at two very different heights, because at any single one a fixed number can look
     * perfectly correct. And it used to move the felt the opposite way from the hand.
     */
    private static void theBoardFollowedTheHand(String when) {
        if (wheelAt == null || wheelWasOver == null || pannedBy == null) {
            fail("nothing was aimed at, so a pan could not be measured " + when);
            return;
        }
        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        double[] now = TablePointer.onScreen(top, wheelWasOver[0], wheelWasOver[1]).orElse(null);
        if (now == null) {
            fail("the pan took the aimed-at felt off the window entirely " + when);
            return;
        }
        double movedX = now[0] - wheelAt[0];
        double movedY = now[1] - wheelAt[1];
        double asked = Math.hypot(pannedBy[0], pannedBy[1]);
        double moved = Math.hypot(movedX, movedY);
        System.out.println("[devscene] the hand moved " + Math.round(asked)
                + " and the board moved " + Math.round(moved) + " " + when);
        // A fifth. Room for an integer aim point and a drag measured in whole units, and
        // nowhere near enough for a factor of four.
        if (Math.abs(moved - asked) > asked * 0.2) {
            fail("the board moved " + Math.round(moved) + " units for a hand that moved "
                    + Math.round(asked) + " " + when);
            return;
        }
        // And the right way. A board that slides against the hand is worse than one that
        // slides too far, and the distance alone cannot tell them apart.
        if (pannedBy[0] * movedX + pannedBy[1] * movedY <= 0) {
            fail("the board moved the wrong way for a hand that went "
                    + pannedBy[0] + "," + pannedBy[1] + " " + when);
            return;
        }
        // The aim follows the felt, so the next drag or wheel measures from where this left off.
        wheelAt = new int[] {(int) Math.round(now[0]), (int) Math.round(now[1])};
    }

    /**
     * Checks that "show me everything" shows the same amount of table in both views.
     * <p>The claim the whole two-view design rests on is that they differ only in whether a
     * point is a pixel or a place on the felt. A player who presses the same key in both and
     * gets a board a fifth smaller on the real table has been told, without anybody saying
     * it, that the real table is the worse one - and that is exactly what "the card images
     * render so tiny" is a report of.
     * <p>Measured through the projection the game drew with, because the board on the block
     * has no screen rectangle of its own: everything it knows about itself is in surface
     * units, which say where a mat is and nothing about how big it comes out.
     */
    private static void theBlockFramesLikeTheScreen(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to measure");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("nobody was sitting down to measure a mat for");
            return;
        }
        Rect mat = board.board().matRect(me);
        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        double[] topLeft = TablePointer.onScreen(top, mat.x(), mat.y()).orElse(null);
        double[] bottomRight = TablePointer.onScreen(top, mat.right(), mat.bottom())
                .orElse(null);
        if (topLeft == null || bottomRight == null) {
            fail("the mat on the block did not land on the window at all");
            return;
        }
        double wide = Math.abs(bottomRight[0] - topLeft[0]);
        double deep = Math.abs(bottomRight[1] - topLeft[1]);
        System.out.println("[devscene] the mat on the block is drawn "
                + Math.round(wide) + " by " + Math.round(deep)
                + ", against " + seatedMat + " on the screen");
        if (seatedMat == null || seatedMat.isEmpty()) {
            fail("the seated framing was never measured to compare against");
            return;
        }
        // A tenth. Enough room for the two views rounding differently and for the block's
        // own edge of air, and nowhere near enough for one of them to look like the poor
        // relation of the other.
        double slack = 0.10;
        if (Math.abs(wide - seatedMat.width()) > seatedMat.width() * slack
                || Math.abs(deep - seatedMat.height()) > seatedMat.height() * slack) {
            fail("framing the whole table gives a mat " + Math.round(wide) + " by "
                    + Math.round(deep) + " on the block and " + seatedMat.width() + " by "
                    + seatedMat.height() + " on the screen: the two views disagree about how"
                    + " big the same table is");
        }
    }

    /** How big the seated view drew this player's mat when it was framing the whole table. */
    private static Rect seatedMat = Rect.NONE;

    /** Rests the cursor on a zone, so the next step can read what it says about itself. */
    private static void hoverAZone(Minecraft client, int index) {
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty()) {
            fail("no zone " + index + " to rest on");
            return;
        }
        hover(client, new int[] {(int) zone.centerX(), (int) zone.centerY()});
    }

    /**
     * Checks that resting on a pile names it and says what a press would do.
     * <p>A pile is a black box with a number on it. The word beside it goes when the board is
     * drawn small, and even when it is there it does not say that the box can be opened - so
     * without this a player's only route to a graveyard is trying things.
     */
    private static void aPileSaysWhatItDoes(Minecraft client, Zone zone) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to read a pile's tooltip on");
            return;
        }
        String all = board.tooltipShowing().stream()
                .map(net.minecraft.network.chat.Component::getString)
                .collect(java.util.stream.Collectors.joining(" / "));
        String name = ZoneText.name(zone).getString();
        if (!all.contains(name)) {
            fail("resting on the " + zone + " did not name it: \"" + all + "\"");
            return;
        }
        if (all.equals(name)) {
            fail("resting on the " + zone + " named it but did not say what a press would do");
            return;
        }
        System.out.println("[devscene] the " + zone + " says: " + all);
    }

    /**
     * What screen the last gesture left us on, and whether that is where it should have gone.
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
     * <p>The real cursor, not just a call to {@code mouseMoved}. Every frame a screen draws is
     * handed the pointer's actual position, and the board on the block works out what is
     * under it from that, so a harness that only tells the screen it moved photographs a
     * board with the cursor still parked in the middle of the window.
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
     * Looks at the top of this player's own library and opens the screen that decides.
     * <p>Straight to the screen rather than through the library menu: a context menu row has
     * no widget to press, and what is worth checking is that a decision reaches the game - the
     * menu entry that opens this is one line.
     */
    private static void lookAtTheTopOfTheLibrary(
            Minecraft client, int howMany, PileScreen.Decision decision) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("no seat to look at a library with");
            return;
        }
        ClientTableActions.send(table, new GameEvent.LibraryLooked(me, me, howMany));
        client.setScreen(new PileScreen(
                table, me, Zone.LIBRARY, true, decision, client.screen));
    }

    /**
     * Checks a surveil says where the cards it bins are going, and says the graveyard.
     * <p>The two decisions this screen makes differ in exactly one word, and that word is the
     * whole difference between the two verbs. Read off the screen rather than off the enum,
     * because a screen that carries the right decision and writes the other one's wording is
     * the failure worth catching.
     */
    private static void theBinIsTheGraveyard(PileScreen pile) {
        String said = pile.footerSays();
        String graveyard = net.minecraft.network.chat.Component
                .translatable("screen.gathering.pile.to_graveyard").getString();
        if (!said.contains(graveyard)) {
            fail("a surveil does not say its cards go " + graveyard + ": \"" + said + "\"");
            return;
        }
        System.out.println("[devscene] a surveil says: " + said);
    }

    /**
     * Sits somebody down opposite, with a deck, so the table is a game rather than a solo.
     * <p>Through the session rather than through the world, because what is wanted is a second
     * occupied seat and not a second Minecraft client - and every board rule worth checking
     * here is about what the seats say, not about who is holding the mouse.
     */
    private static void seatARival(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            GameSession session = TableSessions.sessionAt(server.overworld(), where).orElse(null);
            if (session == null) {
                return;
            }
            SeatId theirs = new SeatId(1);
            session.submit(new GameEvent.SeatTaken(theirs,
                    new PlayerRef(new java.util.UUID(0L, 4242L), "Rival")));
            List<CardIdentity> library = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                library.add(CardIdentity.ofPrinting(new java.util.UUID(0L, 500 + index), false));
            }
            // With partners, deliberately: two commanders is the case the damage grid is
            // keyed by the card for, and a rival with none would grow no damage rows at all
            // - which is correct for a Modern opponent and useless for this check. The
            // partners are real printings, borrowed from the deck already at the table:
            // the damage grid names each enemy commander, and a made-up UUID would leave
            // every row saying "Loading..." forever - indistinguishable, in a picture, from
            // the push of card names simply not working.
            java.util.LinkedHashSet<java.util.UUID> printings = new java.util.LinkedHashSet<>();
            List<CardIdentity> partners = new ArrayList<>();
            for (dev.gathering.core.game.CardInstance card : session.state().cards().values()) {
                if (partners.size() == 2) {
                    break;
                }
                if (card.owner().equals(new SeatId(0))
                        && card.identity().printing().isPresent()
                        && printings.add(card.identity().printing().get())) {
                    partners.add(card.identity());
                }
            }
            if (partners.size() < 2) {
                System.out.println(
                        "[devscene] FAIL no two real printings to lend the rival as partners");
                return;
            }
            // In sleeves of their own, so every picture of a two-player board shows the one
            // thing sleeves are for: whose face-down cards are whose, read across the table.
            session.submit(new GameEvent.DeckLoaded(theirs, library, partners,
                    dev.gathering.core.card.Sleeve.PURPLE));
            session.submit(new GameEvent.CardsDrawn(theirs, theirs, 3));
            // And plays one, so there is a card of somebody else's on the table - which is
            // the only way to check what a card moved by another player looks like.
            session.state().contents(theirs, Zone.HAND).stream().findFirst().ifPresent(card ->
                    session.submit(new GameEvent.CardMoved(theirs, card,
                            dev.gathering.core.game.ZoneRef.of(theirs, Zone.BATTLEFIELD),
                            dev.gathering.core.game.Placement.at(TablePosition.of(3000, 4000)))));
            // And bins one. A graveyard is public, so what a spectator reading it must see is
            // a card - an empty pile proves only that the screen opens.
            session.submit(new GameEvent.LibraryMilled(theirs, theirs, 1));
            TableBroadcast.sendToTable(server.overworld(), where);
            System.out.println("[devscene] a rival sat down opposite");
        });
    }

    /**
     * The rival plays a card and then slides it across their own mat.
     * <p>Through the session rather than through this client, which is the point: a card
     * somebody else moves is the case the traveling had to cover, and the only case where
     * this client is a spectator of the movement rather than the cause of it. It is also the
     * commonest movement in the game and was the last one still teleporting.
     */
    private static void theRivalSlidesACardAcrossTheirMat(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            fail("no server to move a rival's card on");
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            GameSession session = TableSessions.sessionAt(server.overworld(), where).orElse(null);
            if (session == null) {
                return;
            }
            SeatId theirs = new SeatId(1);
            CardInstanceId card = session.state()
                    .contents(theirs, Zone.BATTLEFIELD).stream().findFirst().orElse(null);
            if (card == null) {
                System.out.println("[devscene] the rival had nothing on the table to slide");
                return;
            }
            session.submit(new GameEvent.CardMoved(theirs, card,
                    dev.gathering.core.game.ZoneRef.of(theirs, Zone.BATTLEFIELD),
                    dev.gathering.core.game.Placement.at(TablePosition.of(7000, 6000))));
            TableBroadcast.sendToTable(server.overworld(), where);
        });
    }

    /** Opens this player's own counters, which is where commander damage is written down. */
    private static void openMyCounters(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            fail("there was no seat to open the counters of");
            return;
        }
        client.setScreen(new CountersScreen(table,
                new CountersScreen.Subject.Seat(me, CountersScreen.titleForSeat(board, me)),
                client.screen));
    }

    /** This player's own commander, which is the one card in their command zone. */
    private static CardInstanceId myCommander(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            return null;
        }
        return myCommanders(board, me).stream().findFirst().orElse(null);
    }

    /** Every commander this player has, one per command slot. */
    private static java.util.List<CardInstanceId> myCommanders(GameView board, SeatId me) {
        java.util.List<CardInstanceId> found = new java.util.ArrayList<>();
        for (Zone slot : Zone.COMMAND_SLOTS) {
            ZoneView command = board.seat(me).zones().get(slot);
            if (command == null) {
                continue;
            }
            for (CardView held : command.cards()) {
                if (held instanceof CardView.Visible visible) {
                    found.add(visible.id());
                }
            }
        }
        return found;
    }

    /**
     * Plays a commander out, so the next step can send it home again.
     * <p>The single most common thing anybody does with a commander, and the one the second
     * slot most easily gets wrong: with a partner still at home, "the command zone" as a
     * destination has two answers and only one of them is right.
     */
    private static void aCommanderLeavesItsSlot(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            fail("no seat to send a commander out from");
            return;
        }
        Zone leaving = Zone.COMMAND_SLOTS.get(Zone.COMMAND_SLOTS.size() - 1);
        ZoneView slot = board.seat(me).zones().get(leaving);
        CardInstanceId commander = slot == null ? null : slot.cards().stream()
                .filter(card -> card instanceof CardView.Visible)
                .map(card -> ((CardView.Visible) card).id())
                .findFirst().orElse(null);
        if (commander == null) {
            fail("the last command slot was empty, so nothing about going home was tested");
            return;
        }
        ClientTableActions.send(table, new GameEvent.CardMoved(me, commander,
                dev.gathering.core.game.ZoneRef.of(me, Zone.BATTLEFIELD),
                dev.gathering.core.game.Placement.at(TablePosition.of(5000, 5000))));
        wentOutFrom = leaving;
        sentOut = commander;
    }

    private static Zone wentOutFrom;
    private static CardInstanceId sentOut;

    /**
     * Sends it back the way the menu would, and checks the menu's answer was its own slot.
     * <p>Asking the same thing the menu entry asks, so what is checked is the rule the player
     * would actually get rather than a second copy of it written for the test.
     */
    private static void theCommanderGoesHomeToItsOwnSlot(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null || sentOut == null) {
            fail("no seat to bring a commander home to");
            return;
        }
        Zone home = dev.gathering.core.game.CommandSlots.homeFor(board.seat(me));
        if (home != wentOutFrom) {
            fail("a commander whose partner is still at home was sent to " + home
                    + " rather than back to " + wentOutFrom);
            return;
        }
        ClientTableActions.send(table, new GameEvent.CardMoved(me, sentOut,
                dev.gathering.core.game.ZoneRef.of(me, home),
                dev.gathering.core.game.Placement.TOP));
        System.out.println("[devscene] a commander goes home to " + home);
    }

    /** How many times this player has cast their commander out of the command zone. */
    private static int commanderTax(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        CardInstanceId commander = myCommander(client);
        if (me == null || board == null || commander == null) {
            return 0;
        }
        return board.seat(me).commanderTax().getOrDefault(commander, 0);
    }

    /** Opens the counters panel for this player's commander, which is where its tax lives. */
    private static void openCommanderCounters(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            fail("there was no seat to open a commander's counters from");
            return;
        }
        // Both of them. A deck with partners has a commander in each slot and each is cast on
        // its own tax, so the screen has to offer two numbers rather than one - and a run
        // that opened only the first would never notice if it did not.
        java.util.List<CardInstanceId> commanders = myCommanders(board, me);
        if (commanders.size() < Zone.COMMAND_SLOTS.size()) {
            fail("the command slots held " + commanders.size() + " commanders, not "
                    + Zone.COMMAND_SLOTS.size());
            return;
        }
        client.setScreen(new CountersScreen(table,
                new CountersScreen.Subject.Cards(commanders,
                        net.minecraft.network.chat.Component.literal("Commanders")),
                client.screen));
    }

    /** How many commander-damage rows the counters screen is offering. */
    private static int damageRowsShowing(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            return -1;
        }
        int rows = 0;
        for (dev.gathering.core.game.visibility.SeatView seat : board.seats()) {
            if (!seat.seat().equals(me)) {
                rows += seat.commanders().size();
            }
        }
        return rows;
    }

    /**
     * Whether every enemy commander's name has arrived, so a damage row can say who it is.
     * <p>Known through the same two hops the counters screen uses: the commander's id to its
     * visible view for the printing, the printing to the client cache for the name. The
     * server pushes both with the view that showed the card, so by the time the screen is
     * open they have had several steps to arrive - a miss here is the push not working, not
     * the run being impatient.
     */
    private static boolean enemyCommanderNamesKnown(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            return false;
        }
        for (SeatView seat : board.seats()) {
            if (seat.seat().equals(me)) {
                continue;
            }
            for (CardInstanceId commander : seat.commanders()) {
                boolean known = false;
                for (CardView held : board.allCardViews()) {
                    if (held instanceof CardView.Visible visible && visible.id().equals(commander)) {
                        known = visible.identity().printing()
                                .flatMap(printing -> ClientCardCache.get().summary(printing))
                                .isPresent();
                        break;
                    }
                }
                if (!known) {
                    return false;
                }
            }
        }
        return true;
    }

    /** How much commander damage this player has taken, over all enemy commanders. */
    private static int damageTaken(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            return -1;
        }
        // Summed over commanders rather than read per enemy seat: the map is keyed by the
        // commander card now, because twenty-one is a fact about the same commander and a
        // partner deck fields two. What this step checks is only that a press lands.
        return board.seat(me).commanderDamage().values().stream()
                .mapToInt(Integer::intValue).sum();
    }

    /**
     * Checks a player can reach the end of a game, and then does not end it.
     * <p>Conceding is the only verb at this table that finishes a game. The machinery behind
     * it was complete and tested on the server and reachable from nothing at all, so a game
     * could be started and never finished.
     */
    private static void aGameCanBeConceded(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to concede from");
            return;
        }
        Screen before = client.screen;
        if (!openTheTableMenu(client, board, net.minecraft.network.chat.Component.translatable("menu.gathering.table.concede").getString())) {
            fail("no felt on the board offered a table menu to concede from");
            return;
        }
        if (!board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.concede").getString())) {
            fail("the table menu offers no way to concede");
            return;
        }
        if (!(client.screen instanceof ConfirmScreen)) {
            fail("conceding did not ask first: "
                    + (client.screen == null ? "none" : client.screen.getClass().getSimpleName()));
            return;
        }
        // Left open on purpose. A screen opened this instant has not been drawn yet, so the
        // picture is taken in the step after this one - which is why this used to take none
        // at all, and why the one dialog that stops somebody throwing a game away by accident
        // went unlooked-at while every other screen was photographed.
        System.out.println("[devscene] conceding asks before it ends the game");
        cameFromTheBoard = before;
    }

    /** The board the concede question was asked over, so backing out can be checked. */
    private static Screen cameFromTheBoard;

    /** Backs out of the question, which must land back on the board it was asked over. */
    private static void backOutOfConceding(Minecraft client) {
        if (!(client.screen instanceof ConfirmScreen)) {
            fail("the concede question went away before it could be backed out of");
            return;
        }
        client.screen.onClose();
        if (client.screen != cameFromTheBoard) {
            fail("backing out of conceding did not come back to the board");
        }
    }

    /** A question with two answers has two answers you can see and press. */
    private static void theConcedeQuestionOffersBothAnswers(Minecraft client) {
        if (!(client.screen instanceof ConfirmScreen ask)) {
            fail("there was no concede question to read");
            return;
        }
        java.util.Set<String> answers = new java.util.LinkedHashSet<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : ask.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                answers.add(widget.getMessage().getString());
            }
        }
        if (answers.size() < 2) {
            fail("the concede question offered " + answers.size() + " answer(s): " + answers);
        }
    }

    /**
     * Right-clicks bare felt until the table's own menu is up.
     * <p>Not the middle of the board: by this point there are cards there, and a right-click
     * on a card opens that card's menu instead.
     */
    private static boolean openTheTableMenu(Minecraft client, TableScreen board, String wanted) {
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int[][] places = {
            {width / 2, height / 2}, {width / 6, height / 3},
            {width / 2, height / 6}, {width * 5 / 6, height / 3},
            {width / 6, height * 2 / 3},
        };
        for (int[] at : places) {
            board.mouseClicked(at[0], at[1], 1);
            board.mouseReleased(at[0], at[1], 1);
            if (board.menuIsOpen() && board.hasMenuEntry(wanted)) {
                // Every time it comes up, wherever it came up: the way to change how the mod
                // looks is a row of this menu, and a row that quietly stopped being drawn is
                // a feature nobody could find and nothing else would notice.
                String look = net.minecraft.network.chat.Component
                        .translatable("menu.gathering.table.theme", GuiThemes.active().name())
                        .getString();
                if (!board.hasMenuEntry(look)) {
                    fail("the table menu came up with no way to change the look");
                }
                return true;
            }
            // Only to shut a menu that opened. Escape with nothing open leaves the table, so
            // pressing it after a right-click that found bare felt closed the board and every
            // step after this one then had nothing to work on.
            if (board.menuIsOpen()) {
                board.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
        }
        return false;
    }

    /**
     * Leaves the table the way a player leaves it: off the board's own menu.
     * <p>It used to click the edge the player was sitting at, because that gave up the chair.
     * That click opens the board now - it is the one click a seated player standing at their
     * own side of the table most wants to make - so standing up moved to the table menu, and
     * this takes it from there. Pressing the entry rather than sending the event proves the
     * entry is on the menu at all.
     */
    private static void standUp(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to leave the table from");
            return;
        }
        if (!openTheTableMenu(client, board, net.minecraft.network.chat.Component.translatable("menu.gathering.table.leave_table").getString())) {
            fail("no felt on the board offered a table menu with a way to leave the table");
            return;
        }
        board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.leave_table").getString());
        System.out.println("[devscene] left the table from its own menu");
        // Leaving puts the board away, which is what standing up and walking off means. The
        // run wants to go on watching the same table without a seat, so it opens it again the
        // way anybody with no seat opens it.
        client.setScreen(new TableScreen(table));
    }

    /**
     * A graveyard is public, so somebody who is only watching the game may read one.
     * <p>Guards a click that used to be swallowed: the board asked for the viewer's own seat
     * before it would open anybody's pile, so a spectator clicking a graveyard got nothing at
     * all - no screen, no message, and a click sound saying it had worked.
     */
    private static void aSpectatorReadsAGraveyard(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board for a spectator to read a graveyard from");
            return;
        }
        if (ClientTableState.seatAt(table).isPresent()) {
            fail("the spectator check ran with a seat still held");
            return;
        }
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        SeatView seated = view == null ? null : view.seats().stream()
                .filter(seat -> seat.occupant().isPresent())
                .findFirst().orElse(null);
        if (seated == null) {
            fail("no seated player for a spectator to read a graveyard from");
            return;
        }
        // The board's own count, not an assumed four: a table without a command zone draws
        // three, and a guess that disagrees aims the click at a rectangle nothing is in.
        int index = Zone.PILES.indexOf(Zone.GRAVEYARD);
        Rect zone = board.board().pileRect(seated.seat(), index, board.pilesShowing());
        if (zone.isEmpty()) {
            fail("the seated player's graveyard had nowhere to be clicked");
            return;
        }
        board.mouseClicked(zone.centerX(), zone.centerY(), 0);
        board.mouseReleased(zone.centerX(), zone.centerY(), 0);
        if (!(client.screen instanceof PileScreen)) {
            fail("a spectator could not open a seated player's graveyard");
            return;
        }
        // Opening it is half of it. A graveyard is public, so a spectator has to be able to
        // read the cards in one - a screen that opens onto "Nothing here" over a pile that
        // has something in it is the visibility rules being too careful, which fails a
        // watcher just as surely as being too loose would fail everyone else.
        ZoneView bin = seated.zone(Zone.GRAVEYARD);
        if (bin == null || bin.count() == 0) {
            fail("the seated player's graveyard was empty, so nothing about reading one was tested");
            return;
        }
        if (bin.cards().stream().noneMatch(card -> card instanceof CardView.Visible)) {
            fail("a spectator opened a graveyard of " + bin.count()
                    + " and was shown none of them");
            return;
        }
        // And is not offered a gesture they have no seat to make. A screen that says "click
        // a card to move it" to somebody who cannot move anything teaches them that its own
        // writing is not worth reading.
        if (client.screen instanceof PileScreen pile) {
            String said = pile.footerSays();
            String offered = net.minecraft.network.chat.Component
                    .translatable("screen.gathering.pile.hint").getString();
            if (said.equals(offered)) {
                fail("a watcher was told to click a card to move it: \"" + said + "\"");
                return;
            }
            System.out.println("[devscene] a spectator read " + bin.count()
                    + " card(s) in a seated player's graveyard, and is told: " + said);
        }
        // The picture is the next step's. A screen opened this instant has not been drawn
        // yet, and the frame grabbed here is the board that was up before it - which is how
        // a picture named "a spectator reads a graveyard" came to be a picture of no
        // graveyard at all, under a check that was passing the whole time.
    }

    /**
     * Picks a card up on the board drawn on the block and leaves it in the air.
     * <p>Nothing about a card mid-drag can be photographed once it has been let go, and the
     * two things worth looking at there - how big it is, and whether the table says where it
     * would land - only exist while it is up.
     */
    private static void liftACardOnTheBlock(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to lift a card on");
            return;
        }
        int[] from = cardPoint(client);
        board.mouseClicked(from[0], from[1], 0);
        board.mouseDragged(from[0] + 24, from[1] + 12, 0, 24, 12);
        hover(client, new int[] {from[0] + 24, from[1] + 12});
    }

    /**
     * The life total is inside the window at the framing a player spends the game in.
     * <p>It sits off the far edge of the mat rather than on it, and the opening view was
     * fitted to the mat alone - so the one number the game is played to was just past the top
     * of the window, on a board framed the way it is framed for the whole game.
     */
    private static void theLifeCounterIsOnScreen(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to look for a life counter on");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("there was no seat whose life to look for");
            return;
        }
        Rect box = board.board().lifeRect(me);
        if (box.isEmpty()) {
            fail("this player's own board has nowhere to write a life total");
            return;
        }
        int wide = client.getWindow().getGuiScaledWidth();
        int high = client.getWindow().getGuiScaledHeight();
        // On the block the rectangle is in units of felt, so it has to be put through the
        // camera before it can be compared with a window. Asked in pixels either way, the
        // check passed on the seated board and compared a number in the thousands against a
        // window four hundred wide on the other - which is a check that cannot fail for the
        // right reason.
        boolean onTheBlock = board.board() instanceof SurfaceBoard;
        int[][] corners = new int[4][];
        double[][] wanted = {
                {box.x(), box.y()}, {box.right(), box.y()},
                {box.x(), box.bottom()}, {box.right(), box.bottom()}};
        for (int index = 0; index < wanted.length; index++) {
            corners[index] = onTheBlock
                    ? screenPointFor(client, wanted[index], null)
                    : new int[] {(int) wanted[index][0], (int) wanted[index][1]};
            int[] at = corners[index];
            if (at == null || at[0] < 0 || at[1] < 0 || at[0] > wide || at[1] > high) {
                fail("this player's own life counter is not on screen: corner "
                        + java.util.Arrays.toString(wanted[index]) + " came out at "
                        + java.util.Arrays.toString(at) + " in " + wide + " by " + high);
                return;
            }
        }
        System.out.println("[devscene] the life counter is on screen, "
                + (onTheBlock ? "on the block" : "on the seated board"));
    }

    /**
     * Every seat at the table has a counter with both ends placed on it.
     * <p>Pressing only ever reaches the chair this run sits in, and only the near one: the
     * far seat's counter is drawn turned round to face its own player, and a table where that
     * one had no ends at all would look perfectly right from here.
     * <p>What it does not check is that the sign on an end and the direction a press on it
     * goes are the same. They cannot differ: both come off one method on the surface, and a
     * core test states the arithmetic for a counter drawn either way round. Asked of the
     * screen here it would be the same call twice and green whatever happened.
     */
    private static void everyLifeCounterHasItsEnds(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to check life counters on");
            return;
        }
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (view == null) {
            fail("there was no board state to check life counters against");
            return;
        }
        int checked = 0;
        for (SeatView seat : view.seats()) {
            if (seat.occupant().isEmpty()) {
                continue;
            }
            for (int way : new int[] {-1, 1}) {
                Rect end = board.lifeEndFor(seat.seat(), way);
                Rect box = board.board().lifeRect(seat.seat());
                if (end.isEmpty() || box.isEmpty()
                        || !box.contains(end.x(), end.y())
                        || !box.contains(end.right() - 1, end.bottom() - 1)) {
                    fail("seat " + seat.seat().index() + " has no " + way
                            + " end on its counter: " + end + " in " + box);
                    return;
                }
                checked++;
            }
        }
        if (checked == 0) {
            fail("no seat had a life counter to check");
            return;
        }
        System.out.println("[devscene] " + checked + " life counter ends are placed");
    }

    /**
     * Resting on a life counter names whose it is, says what it is, and says it is a button.
     * <p>A number floating on the table between two boards belongs to one of them, and which
     * one is the whole question a four-seat table asks. The two hints matter as much: the
     * ends are buttons and nothing on the felt says so, so a counter that named itself and
     * stopped there would be a number somebody reads for an hour without touching.
     */
    private static void theLifeCounterSaysWhatItIs(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to read a life tooltip on");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no seat whose life to rest on");
            return;
        }
        String said = board.tooltipShowing().stream()
                .map(net.minecraft.network.chat.Component::getString)
                .collect(java.util.stream.Collectors.joining(" / "));
        if (said.isEmpty()) {
            fail("resting on a life counter said nothing at all");
            return;
        }
        String total = Integer.toString(view.seat(me).life());
        if (!said.contains(total)) {
            fail("the life tooltip does not give the total " + total + ": " + said);
            return;
        }
        if (!said.toLowerCase(java.util.Locale.ROOT).contains("click")) {
            fail("the life tooltip does not say the ends are buttons: " + said);
            return;
        }
        System.out.println("[devscene] the life counter says: " + said);
    }

    /**
     * Empties this player's hand onto the battlefield, a card at a time.
     * <p>An empty hand is a real state, and not a rare one: it is the last turn of every
     * aggressive game and the first turn of a mulligan to nothing. The board still has to lay
     * out a fan of no cards, decide that no card is under the cursor, and leave the strip
     * along the bottom looking like an empty hand rather than like a fault.
     */
    private static void emptyMyHand(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no hand to empty");
            return;
        }
        emptied = new java.util.ArrayList<>();
        for (CardView card : view.seat(me).zones().get(Zone.HAND).cards()) {
            if (card instanceof CardView.Visible visible) {
                emptied.add(visible.id());
                ClientTableActions.send(table, new GameEvent.CardMoved(me, visible.id(),
                        ZoneRef.of(me, Zone.GRAVEYARD), Placement.TOP));
            }
        }
    }

    /**
     * Puts back exactly the cards the hand was emptied of.
     * <p>Not a fresh draw. Drawing spends library, and by this point in the run the deck is
     * down to single figures - so refilling by drawing left the scry with nothing to order
     * and the draw button on the block with nothing to draw, three steps later and with no
     * hint that emptying a hand was what did it.
     */
    private static void fillMyHandBack(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null || emptied == null) {
            fail("there was no hand to put back");
            return;
        }
        for (CardInstanceId card : emptied) {
            ClientTableActions.send(table, new GameEvent.CardMoved(me, card,
                    ZoneRef.of(me, Zone.HAND), Placement.TOP));
        }
    }

    /**
     * A board with nothing in hand still draws, still answers the mouse, and still says so.
     * <p>The fan is laid out from the number of cards in it, and a run that never emptied a
     * hand would never have divided by that number.
     */
    private static void anEmptyHandIsStillABoard(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to check an empty hand on");
            return;
        }
        int held = countIn(Zone.HAND);
        if (held != 0) {
            fail("the hand did not empty: " + held + " left in it");
            return;
        }
        // The strip is still the player's own, not a watcher's: they have a hand, it is
        // simply empty, so the felt above it must not run down over where it sits.
        int nearTheBottom = client.getWindow().getGuiScaledHeight() - 8;
        if (board.feltReachesDownTo(nearTheBottom)) {
            fail("an empty hand gave its strip away to the felt");
            return;
        }
        // And nothing in it is under the cursor, wherever the cursor is.
        int middle = client.getWindow().getGuiScaledWidth() / 2;
        hover(client, new int[] {middle, nearTheBottom});
        // And it says so. An empty strip is indistinguishable from a broken one, and a
        // spectator has been told why theirs is empty since the day they had one.
        String said = board.handStripSaid();
        if (said.isEmpty()) {
            fail("an empty hand drew nothing at all in its strip");
            return;
        }
        System.out.println("[devscene] an empty hand says: " + said);
    }

    /**
     * The setup screen spells out the game it is about to start, and says this.
     * <p>Three buttons were pressed here and nothing was ever asked about the result, so a
     * button that quietly stopped choosing anything would have gone on passing. What the
     * screen writes at the foot of itself is the thing a player reads before committing, so
     * it is the thing worth reading here.
     */
    private static void theSetupScreenSays(Minecraft client, String wanted) {
        if (!(client.screen instanceof TableSetupScreen setup)) {
            fail("there was no setup screen to read a choice off");
            return;
        }
        String said = setup.chosenSaid();
        if (said.isEmpty()) {
            fail("the setup screen never said what it would start");
            return;
        }
        if (!said.toLowerCase(java.util.Locale.ROOT)
                .contains(wanted.toLowerCase(java.util.Locale.ROOT))) {
            fail("the setup screen says \"" + said + "\", which does not mention " + wanted);
            return;
        }
    }

    /**
     * The board this client just stood up from is still on the table.
     * <p>Standing up releases the chair and leaves the cards exactly where they were, and
     * everything that drew a mat asked whether somebody was sitting at it - so the board this
     * run had spent eighty steps building became a battlefield with no zones behind it the
     * moment its player walked away. The graveyard and the exile pile are public, and the one
     * person left looking at the table is there to read them.
     */
    private static void theBoardTheyLeftIsStillDrawn(Minecraft client) {
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (view == null) {
            fail("there was no board left behind to look at");
            return;
        }
        int boards = 0;
        for (SeatView seat : view.seats()) {
            if (!seat.hasABoard()) {
                continue;
            }
            boards++;
            if (seat.occupant().isEmpty() && countIn(seat, Zone.LIBRARY) == 0
                    && countIn(seat, Zone.GRAVEYARD) == 0) {
                fail("seat " + seat.seat().index()
                        + " counts as a board with nobody at it and nothing on it");
                return;
            }
        }
        if (boards == 0) {
            fail("standing up took every board off the table");
            return;
        }
        // What the screen drew, not what the board says it should have. Asked the second way
        // this check is the same question twice and stays green while the zones go missing.
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board on screen to count the mats it drew");
            return;
        }
        if (board.boardsDrawn() != boards) {
            fail("the table drew zones for " + board.boardsDrawn() + " of " + boards
                    + " boards still on it");
            return;
        }
        System.out.println("[devscene] " + boards + " board(s) still drawn after standing up");
    }

    /**
     * A box holding one card does not cover half the table.
     * <p>The point of shrinking these screens: what is behind them is the board, and the
     * board is what most of the decisions taken on them are about. The cards were never what
     * made this one wide - the sentence under them was, because every hint used to end by
     * saying Escape closes the box, next to a Done button and under a rule that Escape closes
     * every panel in the mod.
     */
    private static void aPileBoxIsTheSizeOfWhatItHolds(Minecraft client) {
        if (!(client.screen instanceof PileScreen pile)) {
            fail("there was no pile box to measure");
            return;
        }
        int window = client.getWindow().getGuiScaledWidth();
        if (pile.panelWidth() * 2 >= window) {
            fail("a graveyard holding one card took " + pile.panelWidth()
                    + " of " + window + " across");
            return;
        }
        System.out.println("[devscene] a one-card pile box is " + pile.panelWidth()
                + " of " + window + " across");
    }

    /** How many cards a particular seat holds in a zone, whoever is or is not sitting there. */
    private static int countIn(SeatView seat, Zone zone) {
        ZoneView held = seat.zones().get(zone);
        return held == null ? 0 : held.count();
    }

    /**
     * Rests on somebody else's life counter while holding no seat at all.
     * <p>The watcher's case, which is the one the naming is really for. Sitting down puts
     * your own board under your own number and the rest can be worked out from where they
     * are; standing behind the table gives you four numbers on bare felt and nothing saying
     * which board each belongs to.
     */
    private static void hoverSomebodysLifeCounter(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to rest on a life counter of");
            return;
        }
        if (ClientTableState.seatAt(table).isPresent()) {
            fail("the watcher check ran with a seat still held");
            return;
        }
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (view == null) {
            fail("a watcher was shown no board at all");
            return;
        }
        SeatId whose = null;
        for (SeatView seat : view.seats()) {
            if (seat.hasABoard() && !board.lifeEndFor(seat.seat(), 1).isEmpty()) {
                whose = seat.seat();
                break;
            }
        }
        if (whose == null) {
            fail("there was no life counter on the table for a watcher to rest on");
            return;
        }
        watched = whose;
        Rect end = board.lifeEndFor(whose, 1);
        int[] at = board.board() instanceof SurfaceBoard
                ? screenPointFor(client, new double[] {end.centerX(), end.centerY()}, null)
                : new int[] {(int) end.centerX(), (int) end.centerY()};
        if (at == null) {
            fail("the life counter was not under any pixel of the window");
            return;
        }
        hover(client, at);
    }

    /** Presses the button that swaps the grid over to what this drafter has picked. */
    private static void lookAtMyPicks(Minecraft client) {
        if (!(client.screen instanceof DraftScreen draft)) {
            fail("there was no draft screen to look at a pool on");
            return;
        }
        press(client, net.minecraft.network.chat.Component
                .translatable("screen.gathering.draft.show_pool",
                        draft.showing().myPool().size()).getString());
    }

    /**
     * And it shows what has been taken, which at this moment is nothing.
     * <p>Two things at once. The screen can show a pool at all, which is the half of drafting
     * it was missing - the question a pack asks is "what am I building" and a number cannot
     * answer it. And a pick that has been declared but not resolved is <em>not</em> in the
     * pool yet, which is the simultaneous-turn rule seen from the player's side: this drafter
     * has said what they are taking and the packs have not moved, so they have taken nothing.
     * A pool that filled up on declaration would be the rule broken where a player can see it.
     */
    private static void theScreenIsShowingMyPicks(Minecraft client) {
        if (!(client.screen instanceof DraftScreen draft)) {
            fail("the draft screen closed when asked for a pool");
            return;
        }
        if (!draft.isShowingPool()) {
            fail("pressing the pool button did not show the pool");
            return;
        }
        if (!draft.showing().iHaveDeclared()) {
            fail("this check wants a declared pick that has not resolved yet");
            return;
        }
        int picked = draft.showing().myPool().size();
        if (picked != 0) {
            fail("a pick that has not resolved is already in the pool: " + picked);
            return;
        }
        if (!draft.slotOfCard(0).isEmpty()) {
            fail("the empty pool has a place for a card it does not hold");
            return;
        }
        String empty = net.minecraft.network.chat.Component
                .translatable("screen.gathering.draft.pool_empty").getString();
        if (!draft.footerSaid().contains("0") && !draft.footerSaid().contains(empty)) {
            fail("the screen does not say the pool is empty: " + draft.footerSaid());
            return;
        }
        System.out.println("[devscene] looking at my picks, the screen says: "
                + draft.footerSaid());
    }

    /** Whose life counter the watcher is resting on, so the check knows whose name to want. */
    private static SeatId watched;

    /**
     * A watcher presses the key that opens the game log.
     * <p>Which they could not do at all: the key gave up at the seat check and the table
     * menu refused to open without one, while the layout carried on reserving room for a
     * panel nobody without a chair could reach. The log is the public record of a public
     * game and a watcher is exactly who wants to read it.
     */
    private static void aWatcherOpensTheLog(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board for a watcher to open the log on");
            return;
        }
        if (ClientTableState.seatAt(table).isPresent()) {
            fail("the watcher's log check ran with a seat still held");
            return;
        }
        if (!board.theLogIsShowing()) {
            board.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_L, 0, 0);
        }
        if (!board.theLogIsShowing()) {
            fail("a watcher pressed the log key and no log opened");
        }
    }

    /**
     * And what it says still names the player who stood up.
     * <p>Read off what the panel actually drew, not off the log again: a check that renders
     * the log a second time to see what the log says passes even when the panel draws
     * nothing. Every line in there was earned by this client while it was still sitting
     * down, so if standing up renamed them the panel is now a record of things "(empty)"
     * did - which is a log that answers "who did that" with nothing.
     */
    private static void theLogStillNamesWhoLeft(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to read a watcher's log on");
            return;
        }
        if (!board.theLogIsShowing()) {
            fail("the log a watcher opened is not showing");
            return;
        }
        String said = board.logSaid();
        if (said.isBlank()) {
            fail("the log a watcher opened drew no lines at all");
            return;
        }
        String nobody = net.minecraft.network.chat.Component
                .translatable("message.gathering.seat_empty").getString();
        if (said.contains(nobody)) {
            fail("the log calls somebody who left " + nobody + ": " + said);
            return;
        }
        if (whoWasSitting != null && !said.contains(whoWasSitting)) {
            fail("the log no longer names " + whoWasSitting + ": " + said);
            return;
        }
        System.out.println("[devscene] the log still names " + whoWasSitting);
    }

    /** The name this client played under, kept so a check can look for it after standing up. */
    private static String whoWasSitting;

    /**
     * A watcher resting on a life counter is told whose it is.
     * <p>It used to be told nothing: the tooltip started by asking whether this client held
     * a seat and gave up if it did not, so the one viewer with no way to work out whose
     * number that is was the one viewer it refused to tell. What a watcher must not get is
     * the two lines about clicking the ends, because there is no seat here to click from.
     */
    private static void aWatcherIsToldWhoseLifeThatIs(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to read a watcher's life tooltip on");
            return;
        }
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (view == null || watched == null) {
            fail("there was no counter a watcher had rested on");
            return;
        }
        String said = board.tooltipShowing().stream()
                .map(net.minecraft.network.chat.Component::getString)
                .collect(java.util.stream.Collectors.joining(" / "));
        if (said.isEmpty()) {
            fail("a watcher resting on a life counter was told nothing at all");
            return;
        }
        String total = Integer.toString(view.seat(watched).life());
        if (!said.contains(total)) {
            fail("a watcher's life tooltip does not give the total " + total + ": " + said);
            return;
        }
        String name = CountersScreen.titleForSeat(view, watched).getString();
        if (!name.isBlank() && !said.contains(name)) {
            fail("a watcher's life tooltip does not say whose it is (" + name + "): " + said);
            return;
        }
        if (said.toLowerCase(java.util.Locale.ROOT).contains("click")) {
            fail("a watcher was offered a button they have no seat to press: " + said);
            return;
        }
        System.out.println("[devscene] a watcher is told: " + said);
    }

    /** Puts the cursor on this player's own life counter, a frame before it is read. */
    private static void hoverMyLifeCounter(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to rest on a life counter of");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("there was no seat whose life to rest on");
            return;
        }
        Rect end = board.lifeEndFor(me, 1);
        int[] at = board.board() instanceof SurfaceBoard
                ? screenPointFor(client, new double[] {end.centerX(), end.centerY()}, null)
                : new int[] {(int) end.centerX(), (int) end.centerY()};
        if (at == null) {
            fail("the life counter was not under any pixel of the window");
            return;
        }
        hover(client, at);
    }

    /**
     * Right-clicking an end asks how much, and the answer goes that way.
     * <p>The half of this counter a swing for eleven needs. Pressed on the end marked plus,
     * so a run that ever lost track of which end is which would put the life the other way.
     */
    private static void typeAnAmountOfLife(Minecraft client, int side) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to ask for an amount of life on");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("there was no seat whose life to ask about");
            return;
        }
        Rect end = board.lifeEndFor(me, side);
        int[] at = board.board() instanceof SurfaceBoard
                ? screenPointFor(client, new double[] {end.centerX(), end.centerY()}, null)
                : new int[] {(int) end.centerX(), (int) end.centerY()};
        if (at == null) {
            fail("the life counter was not under any pixel of the window");
            return;
        }
        board.mouseClicked(at[0], at[1], 1);
        board.mouseReleased(at[0], at[1], 1);
    }

    /** This player's own life, as the board they are looking at reports it. */
    private static int myLife(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        return me == null || view == null ? 0 : view.seat(me).life();
    }

    /**
     * Presses one end of this player's own life counter.
     * <p>Aimed at the rectangle the board gives out, the same as every other press here: a
     * harness that owns a second copy of the layout goes on passing after the layout moves.
     *
     * @param side -1 for the end that takes one off, 1 for the end that puts one on
     */
    private static void pressMyLife(Minecraft client, int side) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to press a life total on");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("there was no seat whose life to press");
            return;
        }
        // The end the board actually marks with that sign, not whichever end of the
        // rectangle this run would have guessed. Guessing is how a run goes on passing while
        // the board draws its plus on the end that takes a life off.
        Rect end = board.lifeEndFor(me, side);
        if (end.isEmpty()) {
            fail("this table had nowhere to write a life total");
            return;
        }
        int[] at = board.board() instanceof SurfaceBoard
                ? screenPointFor(client, new double[] {end.centerX(), end.centerY()}, null)
                : new int[] {(int) end.centerX(), (int) end.centerY()};
        if (at == null) {
            fail("the life counter on the block was not under any pixel of the window");
            return;
        }
        client.screen.mouseClicked(at[0], at[1], 0);
        client.screen.mouseReleased(at[0], at[1], 0);
    }

    /**
     * Presses the tax written under this player's own commander.
     * <p>Aimed at the rectangle the board itself gives out rather than at a pixel worked out
     * here, because a harness that owns a second copy of the layout is a harness that goes on
     * passing after the layout moves - which this run has been caught doing twice.
     *
     * @param button 0 to record another cast, 1 to take one back
     */
    private static void pressTheTaxUnderMyCommander(Minecraft client, int button) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to press a commander's tax on");
            return;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || view == null) {
            fail("there was no seat to press a commander's tax for");
            return;
        }
        Zone slot = null;
        for (Zone candidate : Zone.COMMAND_SLOTS) {
            if (CommandSlots.commanderIn(view.seat(me), candidate) != null) {
                slot = candidate;
                break;
            }
        }
        if (slot == null) {
            fail("no commander was at home to be taxed");
            return;
        }
        Rect band = board.taxBandFor(me, slot);
        if (band.isEmpty()) {
            fail("the " + slot + " slot had no tax band to press; its slot is "
                    + board.board().pileRect(me, Zone.PILES.indexOf(slot), Zone.pilesFor(true)));
            return;
        }
        int[] at = board.board() instanceof SurfaceBoard
                ? screenPointFor(client, new double[] {band.centerX(), band.centerY()}, null)
                : new int[] {(int) band.centerX(), (int) band.centerY()};
        if (at == null) {
            fail("the tax band on the block was not under any pixel of the window");
            return;
        }
        client.screen.mouseClicked(at[0], at[1], button);
        client.screen.mouseReleased(at[0], at[1], button);
    }

    /** Where the button for this verb is on the seated player's own mat, or null. */
    private static int[] verbButtonAt(Minecraft client, TableVerb verb) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to find a mat button on");
            return null;
        }
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("no seat to find a mat button for");
            return null;
        }
        int index = java.util.Arrays.asList(TableVerb.values()).indexOf(verb);
        Rect where = board.board().verbRect(me, index, TableVerb.count());
        if (where.isEmpty()) {
            fail("the mat has nowhere for the " + verb + " button");
            return null;
        }
        if (board.board() instanceof SurfaceBoard) {
            // On the block the rectangle is in surface units, and the cursor is in pixels.
            return screenPointFor(client, new double[] {where.centerX(), where.centerY()}, null);
        }
        return new int[] {(int) where.centerX(), (int) where.centerY()};
    }

    private static void hoverAVerbButton(Minecraft client, TableVerb verb) {
        int[] at = verbButtonAt(client, verb);
        if (at != null) {
            hover(client, at);
        }
    }

    /**
     * Checks that resting on a button produces its name and the key that does the same.
     * <p>Both halves matter. The name is the only thing a player has when the board is drawn
     * too small for the word to be printed on the button itself, and the key is what turns
     * somebody who is clicking buttons into somebody who is playing at speed.
     */
    private static void aButtonSaysWhatItDoes(Minecraft client, TableVerb verb, String key) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to read a tooltip on");
            return;
        }
        List<net.minecraft.network.chat.Component> said = board.tooltipShowing();
        if (said.isEmpty()) {
            fail("resting on the " + verb + " button said nothing at all");
            return;
        }
        String name = net.minecraft.network.chat.Component.translatable(verb.key()).getString();
        String all = said.stream()
                .map(net.minecraft.network.chat.Component::getString)
                .collect(java.util.stream.Collectors.joining(" / "));
        if (!all.contains(name)) {
            fail("the " + verb + " button's tooltip does not name it: " + all);
            return;
        }
        if (!all.contains(key)) {
            fail("the " + verb + " button's tooltip does not give its key " + key + ": " + all);
            return;
        }
        System.out.println("[devscene] the " + verb + " button says: " + all);
    }

    /**
     * Presses one of the buttons printed on the player's own mat.
     * <p>At the place the board says the button is, rather than by calling what the button
     * calls: a box drawn where nothing listens for a click is exactly the fault worth
     * catching, and calling the action directly would pass with no box drawn at all.
     */
    private static void pressAVerbButton(Minecraft client, TableVerb verb) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to press a mat button on");
            return;
        }
        // The same lookup the hover uses. It was two lookups once, and the copy that pressed
        // went on aiming at the seated board's pixels after the other had learned that the
        // board on the block is measured in units of felt - so the press landed off the
        // window and reported success.
        int[] at = verbButtonAt(client, verb);
        if (at == null) {
            return;
        }
        board.mouseClicked(at[0], at[1], 0);
        board.mouseReleased(at[0], at[1], 0);
        System.out.println("[devscene] pressed the " + verb + " button at "
                + at[0] + "," + at[1]);
    }

    /**
     * Checks the board's own picker against the game's, at the one point they both answer for.
     * <p>Everything else in this run that asks where the cursor is asks {@link TablePointer},
     * including the helper that finds a card to hover - so a picker that is off by a constant
     * would place the cursor by its own wrong answer and then agree with itself. Minecraft's
     * crosshair ray is worked out from the camera by the game, and at the exact center of the
     * screen the two are answering the same question about the same pixel. They have to agree.
     */
    private static void theCursorPicksWhereItPoints(Minecraft client) {
        if (table == null || !(client.screen instanceof TableScreen board)) {
            fail("there was no board on the block to check the picker against");
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        hover(client, new int[] {width / 2, height / 2});

        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        TableTop.Spot ours = TablePointer.at(top, width / 2.0, height / 2.0).orElse(null);
        if (ours == null) {
            System.out.println("[devscene] the crosshair was not over the felt; picker unchecked");
            return;
        }
        // The camera's own forward axis, dropped onto the felt by hand. The exact center of
        // the screen is where a camera is looking, whatever its projection says, so this
        // answers the same question using only where the camera is and which way it faces -
        // no matrices, no viewport, none of the arithmetic being checked.
        var camera = client.gameRenderer.getMainCamera();
        var eye = camera.getPosition();
        var look = camera.getLookVector();
        if (look.y() >= -1.0e-4f) {
            System.out.println("[devscene] the camera was not looking down; picker unchecked");
            return;
        }
        double toTheFelt = (top.topY() - eye.y) / look.y();
        TableTop.Spot theirs = top.at(
                eye.x + look.x() * toTheFelt, eye.z + look.z() * toTheFelt).orElse(null);
        if (theirs == null) {
            System.out.println("[devscene] the camera's own ray missed the felt; unchecked");
            return;
        }
        // A tenth of a card. Anything smaller is rounding and the one frame between the
        // camera being set up and the cursor being read; anything larger is a cursor
        // pointing at one thing and picking another.
        double slack = TableSurface.CARD_WIDTH_UNITS / 10.0;
        double across = Math.abs(ours.x() - theirs.x());
        double down = Math.abs(ours.y() - theirs.y());
        if (across > slack || down > slack) {
            fail("the board picks " + Math.round(across) + " by " + Math.round(down)
                    + " surface units from where the camera is actually looking");
        } else {
            System.out.println("[devscene] the picker agrees with the game's own ray");
        }
    }

    /**
     * Checks the picker away from the middle of the screen, where the projection matters.
     * <p>The center only proves there is no constant offset: a camera looks along its forward
     * axis whatever its lens does. An offset that grows toward the edges - the shape a wrong
     * field of view or a wrong aspect ratio makes - passes that check and ruins every click
     * that is not dead center.
     * <p>The second answer is a ray built by hand out of where the camera is, the three axes
     * it faces along, and the half-angle the view frames with. No matrices, no viewport, no
     * unprojection: none of the arithmetic under test. If the two disagree then either the
     * picker is wrong or the board is framed with a lens it is not being drawn through, and
     * both are worth stopping for.
     */
    private static void thePickerAgreesAcrossTheScreen(Minecraft client) {
        if (table == null || !(client.screen instanceof TableScreen)) {
            fail("there was no board on the block to check the picker across");
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        TableTop top = TableTop.forCorner(table.getX(), table.getY(), table.getZ());
        var camera = client.gameRenderer.getMainCamera();
        var eye = camera.getPosition();
        var look = camera.getLookVector();
        var up = camera.getUpVector();
        var left = camera.getLeftVector();
        double tanUp = TableCameraView.spread() / 2.0;
        double aspect = client.getWindow().getWidth()
                / (double) Math.max(1, client.getWindow().getHeight());
        double slack = TableSurface.CARD_WIDTH_UNITS / 8.0;

        int[][] points = {
            {width / 2, height / 2},
            {width / 3, height / 3}, {width * 2 / 3, height / 3},
            {width / 3, height / 2}, {width * 2 / 3, height / 2},
        };
        int checked = 0;
        for (int[] at : points) {
            TableTop.Spot ours = TablePointer.at(top, at[0], at[1]).orElse(null);
            if (ours == null) {
                continue;
            }
            // Where this pixel sits on the film, from -1 at one edge to +1 at the other.
            double across = 2.0 * at[0] / width - 1.0;
            double down = 1.0 - 2.0 * at[1] / height;
            // Left is negative across, so the sideways term is subtracted.
            double dirX = look.x() - left.x() * across * tanUp * aspect + up.x() * down * tanUp;
            double dirY = look.y() - left.y() * across * tanUp * aspect + up.y() * down * tanUp;
            double dirZ = look.z() - left.z() * across * tanUp * aspect + up.z() * down * tanUp;
            if (dirY >= -1.0e-4) {
                continue;
            }
            double toTheFelt = (top.topY() - eye.y) / dirY;
            TableTop.Spot theirs = top.at(
                    eye.x + dirX * toTheFelt, eye.z + dirZ * toTheFelt).orElse(null);
            if (theirs == null) {
                continue;
            }
            checked++;
            double offX = Math.abs(ours.x() - theirs.x());
            double offY = Math.abs(ours.y() - theirs.y());
            if (offX > slack || offY > slack) {
                fail("at " + at[0] + "," + at[1] + " the board picks " + Math.round(offX)
                        + " by " + Math.round(offY) + " surface units from where a camera "
                        + "with that lens is pointing");
                return;
            }
        }
        if (checked < 2) {
            System.out.println("[devscene] too little felt on screen to check the picker across");
        } else {
            System.out.println("[devscene] the picker agrees at " + checked
                    + " places across the screen");
        }
    }

    /** Which turn of the game the table thinks it is on, or zero if there is no board. */
    private static int turnNow() {
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        return board == null ? 0 : board.turn().turnNumber();
    }

    /**
     * Hands the turn on, off the table's own menu.
     * <p>The whole of the turn structure: there is no phase marker any more, so a pass that
     * did not move the marker would leave a table with nothing at all saying whose turn it is.
     */
    private static void passTheTurn(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to pass the turn at");
            return;
        }
        String wanted = net.minecraft.network.chat.Component
                .translatable("menu.gathering.table.pass_turn").getString();
        if (!openTheTableMenu(client, board, wanted)) {
            fail("the table menu offers no way to pass the turn");
            return;
        }
        board.pressMenuEntry(wanted);
    }

    /**
     * Puts a couple of cards in the deck being built, and names one of them commander.
     * <p>Through the screen's own clicks rather than by reaching into its state, because what
     * is worth checking is that the cells are where the click test thinks they are - a grid
     * drawn to one rule and hit-tested against another is the bug this catches.
     */
    private static void buildADeck(Minecraft client) {
        if (!(client.screen instanceof DeckBuilderScreen builder)) {
            fail("there was no deck builder to build in");
            return;
        }
        if (builder.showing() <= 0) {
            fail("the deck builder was showing no cards to build from");
            return;
        }
        builder.clickCard(0, 1);
        builder.clickCard(1, 0);
        builder.clickCard(1, 0);
        if (builder.deckSize() < 3) {
            fail("three clicks in the deck builder put " + builder.deckSize() + " cards in the deck");
        }
        if (builder.commanderName().isEmpty()) {
            fail("right-clicking a card in the deck builder did not name a commander");
        }
        // And something the curve can draw. A curve leaves lands out - they are what a deck
        // spends mana with rather than on - so a deck of nothing but lands gives eight empty
        // columns, which is the picture that made "the bars communicate nothing" true.
        for (int card = 2; card < builder.showing() && builder.curveTotal() == 0; card++) {
            builder.clickCard(card, 0);
        }
        if (builder.curveTotal() == 0) {
            fail("nothing in the box was a spell, so the curve had no shape to draw");
        }
    }

    /** Asks the table to take back the last thing this player did, off its own menu. */
    private static void undoTheLastThing(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to undo anything from");
            return;
        }
        if (!openTheTableMenu(client, board, net.minecraft.network.chat.Component.translatable("menu.gathering.table.undo").getString())) {
            fail("the table menu offers no way to take back a misclick");
            return;
        }
        board.pressMenuEntry(net.minecraft.network.chat.Component.translatable("menu.gathering.table.undo").getString());
    }

    /**
     * Every gesture the board has, aimed at somebody who has no seat.
     * <p>Each of them is written for a player with a seat and several insist on one. Whether
     * the ones that insist can be reached without a seat is a question about control flow, and
     * reasoning about control flow is how an unreachable path that turns out to be reachable
     * stays in a codebase. So: click a card, right-click a card, click every zone, drag from
     * the hand, and press every key that does something.
     */
    private static void pokeEverything(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board left to poke at");
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int[] card = cardPoint(client);
        for (int button : new int[] {0, 1}) {
            board.mouseClicked(card[0], card[1], button);
            board.mouseReleased(card[0], card[1], button);
            board.mouseClicked(width / 2, height / 2, button);
            board.mouseReleased(width / 2, height / 2, button);
        }
        // Somebody else's zones, because a spectator has none of their own - and the point is
        // what happens when a person with no seat clicks a seated player's graveyard.
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (view != null) {
            for (SeatView seat : view.seats()) {
                for (int index = 0; index < board.pilesShowing(); index++) {
                    Rect zone = board.board().pileRect(seat.seat(), index, board.pilesShowing());
                    if (zone.isEmpty()) {
                        continue;
                    }
                    for (int button : new int[] {0, 1}) {
                        board.mouseClicked(zone.centerX(), zone.centerY(), button);
                        board.mouseReleased(zone.centerX(), zone.centerY(), button);
                    }
                }
            }
        }
        Rect hand = TableScreenLayout.of(width, height).hand();
        board.mouseClicked(hand.centerX(), hand.centerY(), 0);
        board.mouseDragged(card[0], card[1], 0, 0, 0);
        board.mouseReleased(card[0], card[1], 0);
        for (int key : new int[] {
                org.lwjgl.glfw.GLFW.GLFW_KEY_7, org.lwjgl.glfw.GLFW.GLFW_KEY_F,
                org.lwjgl.glfw.GLFW.GLFW_KEY_Q, org.lwjgl.glfw.GLFW.GLFW_KEY_E,
                org.lwjgl.glfw.GLFW.GLFW_KEY_R, org.lwjgl.glfw.GLFW.GLFW_KEY_U,
                org.lwjgl.glfw.GLFW.GLFW_KEY_G, org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,
                org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL,
                org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS, org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,
                org.lwjgl.glfw.GLFW.GLFW_KEY_L, org.lwjgl.glfw.GLFW.GLFW_KEY_L}) {
            if (client.screen != null) {
                client.screen.keyPressed(key, 0, 0);
            }
        }
        // Anything those gestures opened has to close again. A spectator may now read a
        // graveyard, so poking every zone legitimately leaves a pile screen up; what would be
        // a fault is one that will not go back to the board.
        for (int escape = 0; escape < 8 && !(client.screen instanceof TableScreen); escape++) {
            if (client.screen == null) {
                fail("a gesture with no seat closed the table out of the game");
                return;
            }
            client.screen.onClose();
        }
        if (!(client.screen instanceof TableScreen)) {
            fail("a gesture with no seat opened something that would not close back to the board");
        }
        System.out.println("[devscene] poked every gesture with no seat");
    }

    /**
     * Says what the server thinks about who is sitting where, and what the client thinks.
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
     * Puts a card that prints a token onto the battlefield.
     * <p>Looked up by name and put down directly rather than found in the library: the deck
     * on the table by this point in the run is three cards repeated twenty times, and which
     * card is on the felt matters here in a way it does not for the loyalty steps - the row
     * under test is named after what the card prints, so the run has to know what it played.
     * <p>It arrives marked as a token, which is what every card the mod brings in from
     * outside a deck is marked as. That changes nothing about the rows being checked: the
     * tokens a card offers come off its printing, not off how it got to the table.
     */
    private static void playTheCardThatMakesAToken(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || table == null) {
            fail("there was no server to put a token-making card on");
            return;
        }
        BlockPos where = table;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            GameSession session = TableSessions.sessionAt(level, where).orElse(null);
            CardDataService service = CardDataService.active().orElse(null);
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (session == null || service == null || player == null) {
                fail("no session, pipeline or player to put a token-making card down for");
                return;
            }
            SeatId seat = TableSessions.seatIdOf(level, where, player.getUUID()).orElse(null);
            if (seat == null) {
                fail("nobody was sitting where the token-making card had to go");
                return;
            }
            var known = service.findByName(MAKES_A_TOKEN).join().orElse(null);
            if (known == null) {
                fail("the card pipeline could not find " + MAKES_A_TOKEN);
                return;
            }
            // The client is about to be told to draw a card it has never heard of, and the
            // whole point of the step is what that card's metadata says - so the summary goes
            // first, exactly as TokenCreation sends it.
            dev.gathering.network.Sending.to(player, new dev.gathering.network.CardMetadataPayload(
                    List.of(dev.gathering.network.CardSummary.of(known))));
            java.util.List<CardInstanceId> before =
                    session.state().contents(seat, Zone.BATTLEFIELD);
            session.submit(new GameEvent.TokenCreated(seat, seat,
                    dev.gathering.core.card.CardIdentity.ofPrinting(known.scryfallId()), 1));
            maker = session.state().contents(seat, Zone.BATTLEFIELD).stream()
                    .filter(id -> !before.contains(id))
                    .findFirst()
                    .orElse(null);
            TableBroadcast.sendToTable(level, where);
            System.out.println("[devscene] put " + known.name() + " on the table, which prints "
                    + THE_TOKEN_IT_MAKES + "s");
        });
    }

    /**
     * Right-clicks the table with the deck, exactly as a player would.
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
     * Draws this many cards the way a player would: one press of the draw key each.
     * <p>The number row used to draw its own number, so seven cards was one press of 7. It
     * carries a verb per key now, the way the reference table binds them, and 7 sends the card
     * being pointed at to the graveyard - so a run that still pressed it would set up a
     * different game from the one it went on to photograph.
     */
    private static void drawCards(Minecraft client, int count) {
        for (int drawn = 0; drawn < count; drawn++) {
            if (client.screen == null) {
                fail("nothing to draw cards on");
                return;
            }
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_2, 0, 0);
        }
    }

    /**
     * Cancelling out of this screen goes back to whatever opened it.
     * <p>Reported from a real session: "when you hit cancel from the deck creation menu, it
     * doesn't take you back to the last menu, it just kicks you out entirely". The builder
     * was an ordinary screen rather than one of the detours, so closing it closed to the
     * world, and the collection you were standing at - with the search you had typed in it -
     * was gone.
     * <p>Pressed and checked in the one step, because a button's handler runs while it is
     * being pressed and going back waits on nobody.
     */
    private static void cancellingGoesBack(
            Minecraft client, String where, Class<?> expected) {
        String was = client.screen == null
                ? "nothing" : client.screen.getClass().getSimpleName();
        press(client, Component.translatable("gui.cancel").getString());
        if (!expected.isInstance(client.screen)) {
            fail("cancelling " + was + " went to "
                    + (client.screen == null ? "nothing at all"
                            : client.screen.getClass().getSimpleName())
                    + " rather than back to " + where);
        }
    }

    /**
     * Clicks the button with this label, wherever the layout has put it.
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
        fail("no button labeled " + label + " on "
                + client.screen.getClass().getSimpleName());
    }

    /**
     * Nothing the collection draws for a card lands outside the box it is drawn in.
     * <p>A cell is not the card. The stack of copies leans up and to the right of it by three
     * pixels a card, and the ring round a hovered one sits two pixels outside it, so a grid
     * laid out against cells alone puts every stack of two or more through the top of the
     * box - which reads as the box being the wrong size rather than as the cards overflowing
     * it. Asked of the box and the cells the screen itself reports, so the check cannot drift
     * from the layout by agreeing with a copy of it.
     */
    private static void everyCardStaysInItsBox(Minecraft client) {
        if (client.screen instanceof CollectionScreen box) {
            cardsStayInside(box.boxOnScreen(), box.cellsThatFit(), box::drawnAt, "collection");
        } else if (client.screen instanceof DeckBuilderScreen box) {
            cardsStayInside(box.boxOnScreen(), box.cellsThatFit(), box::drawnAt, "deck builder");
        } else {
            fail("there was no card grid to measure on "
                    + client.screen.getClass().getSimpleName());
        }
    }

    /**
     * How thick the wall of a recessed box is: its nine-slice border, which is eight.
     * <p>Content belongs inside the frame, not on it. Three was measured off the neutral
     * theme's own inset and was only ever true there - Future Sight's chrome band is the full
     * eight, Arcade's rail is seven, and Retro's pressed border reads wider still. Eight is
     * the structural answer rather than a measured one: the sprite is thirty-two with a
     * border of eight, so everything at that depth or deeper is the stretched field and
     * everything shallower is frame, in every theme there is or ever will be.
     */
    private static final int BOX_WALL = 8;

    /** The check itself, so the two grids cannot be checked to two different standards. */
    private static void cardsStayInside(
            Rect box, int cells, java.util.function.IntFunction<Rect> drawnAt, String what) {
        Rect inside = box.shrink(BOX_WALL);
        for (int index = 0; index < cells; index++) {
            Rect drawn = drawnAt.apply(index);
            if (drawn.x() < inside.x() || drawn.y() < inside.y()
                    || drawn.right() > inside.right() || drawn.bottom() > inside.bottom()) {
                fail("card " + index + " of the " + what + " is drawn at " + drawn
                        + ", which is not inside the " + inside + " that box leaves"
                        + " for it - a stack or a ring is spilling onto the frame");
                return;
            }
        }
        System.out.println("[devscene] all " + cells + " " + what
                + " slots, stacks and rings included, stay inside the box");
    }

    /**
     * No control on this screen is labelled with a piece of punctuation.
     * <p>The page turns were "&lt;" and "&gt;". A button labelled that way reads at a glance
     * as text somebody forgot to finish, and it is what a screen reader is handed too - so
     * they are arrows now, with a real sentence behind them for the tooltip and the narrator.
     * This is what stops one being written back: a message that is one or two characters of
     * punctuation is not a sentence, whatever is drawn on top of it.
     */
    private static void everyButtonSaysSomething(Minecraft client) {
        if (client.screen == null) {
            fail("there was no screen to read the buttons of");
            return;
        }
        for (GuiEventListener child : client.screen.children()) {
            if (!(child instanceof AbstractWidget widget)) {
                continue;
            }
            String said = widget.getMessage().getString();
            if (!said.isEmpty() && said.length() <= 2
                    && said.chars().noneMatch(Character::isLetterOrDigit)) {
                fail("a control on " + client.screen.getClass().getSimpleName()
                        + " is labelled \"" + said + "\", which is punctuation rather than a"
                        + " sentence - the narrator and the tooltip get that too");
                return;
            }
        }
        System.out.println("[devscene] every control on the collection says what it does");
    }

    private static void shoot(Minecraft client, String name) {
        nothingOverlapsAnythingElse(client, name);
        Screenshot.grab(
                client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> { });
        TAKEN.add(name);
    }

    /**
     * Every control on screen is on the screen, and no two are on top of each other.
     * <p>Run at every photograph rather than left to somebody looking at the picture, which
     * is how a pot ended up drawn across a life total: a thing checked on its own reads
     * perfectly and still lands on top of its neighbor, and the only reliable way to catch
     * that is to check it against everything else that can be on screen at the same time.
     * <p>Widgets only. What is drawn rather than added - felt, mats, the pot - has its
     * geometry checked where the geometry lives, in the pure layer, for the same reason.
     */
    private static void nothingOverlapsAnythingElse(Minecraft client, String name) {
        Screen screen = client.screen;
        if (screen == null) {
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        List<AbstractWidget> shown = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible
                    && widget.getWidth() > 0 && widget.getHeight() > 0) {
                shown.add(widget);
            }
        }
        for (AbstractWidget widget : shown) {
            if (widget.getX() < 0 || widget.getY() < 0
                    || widget.getX() + widget.getWidth() > width
                    || widget.getY() + widget.getHeight() > height) {
                fail(name + ": \"" + widget.getMessage().getString() + "\" is off screen at "
                        + widget.getX() + "," + widget.getY() + " "
                        + widget.getWidth() + "x" + widget.getHeight()
                        + " in " + width + "x" + height);
            }
        }
        for (int one = 0; one < shown.size(); one++) {
            for (int two = one + 1; two < shown.size(); two++) {
                AbstractWidget first = shown.get(one);
                AbstractWidget second = shown.get(two);
                if (overlap(first, second)) {
                    fail(name + ": \"" + first.getMessage().getString() + "\" and \""
                            + second.getMessage().getString() + "\" are drawn on top of"
                            + " each other");
                }
            }
        }
    }

    private static boolean overlap(AbstractWidget one, AbstractWidget two) {
        return one.getX() < two.getX() + two.getWidth()
                && two.getX() < one.getX() + one.getWidth()
                && one.getY() < two.getY() + two.getHeight()
                && two.getY() < one.getY() + one.getHeight();
    }

    private static void finish(Minecraft client, String why) {
        // Anything drawn at a scale nobody asked for, anywhere in the whole run. A power and
        // toughness meant for the corner of a card was once drawn eighteen times too big,
        // covering the board in letterforms too large to read as letters - and the build was
        // green, the game tests passed, and the only evidence was the color histogram of a
        // screenshot. This is the check that would have failed. See GuiText.wrongScales.
        if (GuiText.wrongScales() > 0) {
            fail("text was drawn at a scale nobody asked for " + GuiText.wrongScales()
                    + " times - an int width handed to a float scale, almost certainly");
        }
        // A card name losing its tail is the job - those are arbitrary and a panel cannot be
        // built around the longest one in Magic. A line the mod wrote losing its tail is a
        // label that has stopped saying what it was written to say, and no screenshot makes
        // that obvious: "Click to add - right-cli..." looks like a design until you read it.
        if (!GuiText.trimmedCopy().isEmpty()) {
            fail("the mod's own lines were cut short: "
                    + String.join(", ", new java.util.TreeSet<>(GuiText.trimmedCopy()))
                    + " - each needs more room or fewer words, not an ellipsis");
        }
        System.out.println("[devscene] " + why + "; took " + TAKEN);
        // How far it actually got, on its own line, because "failures: 0" is only worth
        // anything alongside it. A run that stopped a third of the way through and found
        // nothing wrong has found nothing; shots.sh reads this line and says so.
        System.out.println("[devscene] reached step "
                + Math.min(step, LAST_STEP + 1) + " of " + (LAST_STEP + 1));
        for (String failure : FAILURES) {
            System.out.println("[devscene] FAIL " + failure);
        }
        System.out.println("[devscene] failures: " + FAILURES.size());
        new File(client.gameDirectory, "screenshots").mkdirs();
        client.stop();
    }
}
