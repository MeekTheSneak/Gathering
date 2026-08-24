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
import dev.gathering.core.game.CommandSlots;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Phase;
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
     *
     * <p>The only way to photograph a card in the air is to look while it is in the air.
     */
    private static final int A_MOMENT = 3;

    /**
     * How long one step may sit there before the run is declared stuck.
     *
     * <p>Per step rather than for the whole run. A budget for the whole run is a number that
     * has to be raised every time the scene grows a step, and the run that finally exceeds it
     * fails with "gave up waiting" rather than with whatever was actually wrong - which is
     * exactly what happened the first time this scene passed sixty steps. What is being
     * watched for is a scene that has stopped moving, and that is a step that has not
     * changed, however long the run before it took.
     *
     * <p>Forty seconds, against a longest legitimate wait of two.
     */
    private static final int STUCK_TICKS = 20 * 40;

    private static BlockPos table;
    private static boolean asked;
    private static boolean committed;
    private static int ticks;
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
    private static int taxPaid;

    /** Where in the turn the table was before the marker was stepped on. */
    private static Phase wasInPhase;

    /** Where the turn marker was before the undo was asked for. */
    private static Phase beforeTheUndo;

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
                if (client.screen != null) {
                    // Free play is the shorter answer to "what kind of game", and for a
                    // while this screen could not give it: every game it started was one
                    // somebody would be held to, and a game with no format meant closing
                    // the screen and finding the walk-up path nothing here mentions.
                    press(client, "Free play");
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
                // Draw a hand, so there is something in it to photograph.
                drawCards(client, 7);
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
                // Rest the cursor on a mat button, so the next step can read what it says.
                hoverAVerbButton(client, TableVerb.DRAW);
                advance(SETTLE);
            }
            case 10 -> {
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
            case 11 -> {
                aCardIsInTheAir(client, "drawing one");
                shoot(client, "06b-a-card-in-the-air");
                // A shuffle is the one move that changes nothing anybody may look at, so it
                // has nothing to show unless it is given something. The pile rattles.
                pressAVerbButton(client, TableVerb.SHUFFLE);
                advance(A_MOMENT);
            }
            case 12 -> {
                aPileIsBeingShaken(client);
                shoot(client, "06c-a-library-being-shuffled");
                advance(SETTLE);
            }
            case 13 -> {
                int now = countIn(Zone.HAND);
                if (now <= inTheHand) {
                    fail("the draw button on the mat drew nothing: "
                            + inTheHand + " to " + now);
                }
                advance(0);
            }
            case 14 -> {
                // The shared turn marker. Nothing enforces it, which is exactly why it has to
                // be movable: a marker stuck on "untap" all game is worse than none.
                wasInPhase = phaseNow();
                stepThePhase(client);
                advance(SETTLE);
            }
            case 15 -> {
                Phase now = phaseNow();
                if (now == null || now == wasInPhase) {
                    fail("the turn marker did not move on: still " + wasInPhase);
                }
                advance(0);
            }
            case 16 -> {
                // Taking a move back. Every misclick in a game played by dragging cards about
                // is permanent without this, and all of the machinery for it existed with
                // nothing able to ask for it.
                beforeTheUndo = phaseNow();
                undoTheLastThing(client);
                advance(SETTLE);
            }
            case 17 -> {
                Phase now = phaseNow();
                if (now == null || now == beforeTheUndo) {
                    fail("undoing the phase step changed nothing: still " + beforeTheUndo);
                }
                advance(0);
            }
            case 18 -> {
                // Play a card: take the first one in hand and drop it on the near mat. The one
                // gesture the whole table is built around, and the one nothing has yet checked
                // end to end.
                playACard(client);
                advance(SETTLE);
            }
            case 19 -> {
                shoot(client, "07-card-played");
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 20 -> {
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
            case 21 -> {
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
            case 22 -> {
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
            case 23 -> {
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
            case 24 -> {
                shoot(client, "12-key-list");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 25 -> {
                // Into the graveyard: the drop that has to land on a zone rather than on felt.
                dropIntoAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                advance(SETTLE);
            }
            case 26 -> {
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
            case 27 -> {
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
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_7, 0, 0);
                }
                advance(SETTLE);
            }
            case 28 -> {
                // The life total, which is the number a game of Magic is played to and until
                // now lived in a strip along the top of the window - the one part of the
                // screen that is not the table. It is on the table now, off the far edge of
                // each board, and it is a pair of buttons.
                lifeWas = myLife(client);
                pressMyLife(client, 1);
                advance(SETTLE);
            }
            case 29 -> {
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
            case 30 -> {
                int now = myLife(client);
                if (now != lifeWas) {
                    fail("the two ends of the life counter do not undo each other: "
                            + lifeWas + " to " + now);
                    return;
                }
                System.out.println("[devscene] the life counter goes up at one end and down"
                        + " at the other");
                advance(A_MOMENT);
            }
            case 31 -> {
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
            case 32 -> {
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
            case 33 -> {
                int backTo = commanderTax(client);
                if (backTo != taxPaid) {
                    fail("right-clicking the tax left it at " + backTo + ", not back at "
                            + taxPaid);
                    return;
                }
                System.out.println("[devscene] the tax under a commander goes up and back down");
                int afterTheKey = countIn(Zone.GRAVEYARD);
                if (afterTheKey <= beforeTheKey) {
                    fail("the graveyard key put nothing in the graveyard: "
                            + beforeTheKey + " to " + afterTheKey);
                }
                shoot(client, "15-crowded-hand");
                // A pile has to say what it is and what pressing it would do, for the same
                // reason a mat button does: its name is not printed on it once the board is
                // drawn small, and its count alone does not say it can be opened.
                hoverAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                advance(SETTLE / 2);
            }
            case 34 -> {
                aPileSaysWhatItDoes(client, Zone.GRAVEYARD);
                // The graveyard has a card in it by now, and left-clicking a pile that is not
                // a library opens it. Anything else here is a dead end the player would find.
                clickAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD), 0);
                advance(SETTLE);
            }
            case 35 -> {
                expectScreen(client, "left-clicking the graveyard", PileScreen.class);
                shoot(client, "16-graveyard-open");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                advance(SETTLE);
            }
            case 36 -> {
                expectScreen(client, "closing the graveyard", TableScreen.class);
                // Right-click on the library, which is where every verb a library has lives.
                clickAZone(client, Zone.PILES.indexOf(Zone.LIBRARY), 1);
                advance(SETTLE / 2);
            }
            case 37 -> {
                if (!menuIsOpen(client)) {
                    fail("right-clicking the library opened no menu");
                }
                shoot(client, "17-library-menu");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                // Look at the top three, which is the half of a scry that already worked.
                lookAtTheTopOfTheLibrary(client, 3, PileScreen.Decision.SCRY);
                advance(SETTLE);
            }
            case 38 -> {
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
                        pile.mouseClicked(first.centreX(), first.centreY(), 0);
                        pile.mouseReleased(first.centreX(), first.centreY(), 0);
                        if (pile.markedToSendAway() != 1) {
                            fail("clicking a scried card marked "
                                    + pile.markedToSendAway() + " cards to send away");
                        }
                    }
                }
                advance(SETTLE / 4);
            }
            case 39 -> {
                shoot(client, "19-one-going-to-the-bottom");
                press(client, "Done");
                advance(SETTLE);
            }
            case 40 -> {
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
            case 41 -> {
                expectScreen(client, "surveilling two", PileScreen.class);
                shoot(client, "20a-surveilling");
                if (client.screen instanceof PileScreen pile) {
                    theBinIsTheGraveyard(pile);
                    Rect first = pile.slotOfCard(0);
                    if (first.isEmpty()) {
                        fail("the surveil box showed no card to bin");
                    } else {
                        pile.mouseClicked(first.centreX(), first.centreY(), 0);
                        pile.mouseReleased(first.centreX(), first.centreY(), 0);
                    }
                }
                advance(SETTLE / 4);
            }
            case 42 -> {
                shoot(client, "20b-one-going-to-the-graveyard");
                press(client, "Done");
                advance(SETTLE);
            }
            case 43 -> {
                expectScreen(client, "deciding a surveil", TableScreen.class);
                int now = countIn(Zone.GRAVEYARD);
                if (now != inTheGraveyard + 1) {
                    fail("surveilling a card into the graveyard put " + (now - inTheGraveyard)
                            + " there, not one");
                }
                if (!theLogMentions("log.gathering.surveilled")) {
                    fail("the surveil never reached the game");
                }
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
            case 44 -> {
                if (client.screen instanceof TableScreen board && board.isHoveringSomething()) {
                    fail("a cursor off the board still had a card under it");
                }
                shoot(client, "21-on-the-table-in-play");
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 45 -> {
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
            case 46 -> {
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
            case 47 -> {
                aButtonSaysWhatItDoes(client, TableVerb.DRAW, "2");
                pressAVerbButton(client, TableVerb.DRAW);
                advance(A_MOMENT);
            }
            case 48 -> {
                // The same crossing, drawn by the world rather than by the window. Everyone
                // at the table sees a card move, and most of them are not sitting at it.
                aCardIsInTheAir(client, "drawing one on the block");
                shoot(client, "22b-a-card-in-the-air-on-the-table");
                advance(SETTLE);
            }
            case 49 -> {
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
            case 50 -> {
                int now = myLife(client);
                if (now != lifeWas + 1) {
                    fail("pressing the end marked plus on the block moved life by "
                            + (now - lifeWas) + ", not one");
                    return;
                }
                pressMyLife(client, -1);
                advance(A_MOMENT);
            }
            case 51 -> {
                int now = myLife(client);
                if (now != lifeWas) {
                    fail("the ends of the life counter on the block do not undo each other: "
                            + lifeWas + " to " + now);
                    return;
                }
                System.out.println("[devscene] the life counter presses on the block too");
                advance(A_MOMENT);
            }
            case 52 -> {
                // The same tax, pressed on the board drawn in the world. Worth pressing twice
                // over because the two views hand the press its rectangle in different spaces
                // - pixels on the window, units of felt on the block - and a button that only
                // works in the view which has had the attention is a shape of bug this run
                // has caught twice already.
                taxPaid = commanderTax(client);
                pressTheTaxUnderMyCommander(client, 0);
                advance(SETTLE);
            }
            case 53 -> {
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
            case 54 -> {
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
            case 55 -> {
                // The whole table, which is the one framing that shows the chair nobody is in.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 56 -> {
                // Somebody sits down opposite. Every picture so far has been of a table with
                // one player at it, which is not the game this is for.
                seatARival(client);
                advance(SETTLE);
            }
            case 57 -> {
                shoot(client, "23-two-players");
                // Both seats now, which is the only moment this run can check the far one.
                everyLifeCounterHasItsEnds(client);
                // A card somebody else moves across their own mat. The commonest movement in
                // the game and the last one still teleporting: a card changing zones was
                // followed, and a card sliding from one spot to another was not.
                theRivalSlidesACardAcrossTheirMat(client);
                advance(A_MOMENT);
            }
            case 58 -> {
                aCardIsInTheAir(client, "a rival sliding a card across their mat");
                shoot(client, "23a-a-rivals-card-on-the-move");
                advance(SETTLE);
            }
            case 59 -> {
                openMyCounters(client);
                advance(SETTLE / 2);
            }
            case 60 -> {
                expectScreen(client, "asking for my own counters", CountersScreen.class);
                shoot(client, "24-commander-damage");
                tookCommanderDamage = damageTaken(client);
                press(client, "+");
                advance(SETTLE);
            }
            case 61 -> {
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
            case 62 -> {
                aCommanderLeavesItsSlot(client);
                advance(SETTLE);
            }
            case 63 -> {
                theCommanderGoesHomeToItsOwnSlot(client);
                advance(SETTLE);
            }
            case 64 -> {
                expectScreen(client, "pressing Done on the counters", TableScreen.class);
                // The other number a game of Commander asks a player to keep for an hour.
                taxPaid = commanderTax(client);
                openCommanderCounters(client);
                advance(SETTLE / 2);
            }
            case 65 -> {
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
            case 66 -> {
                int now = commanderTax(client);
                if (now <= taxPaid) {
                    fail("commander tax did not go up: " + taxPaid + " to " + now);
                }
                press(client, "Done");
                advance(SETTLE / 2);
            }
            case 67 -> {
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
            case 68 -> {
                theWholeTableIsOnScreen(client);
                shoot(client, "26-the-whole-table");
                // A window somebody has resized, which is the one path that re-runs a screen's
                // init on an instance that is already holding a game. Two sizes: one where
                // everything gets bigger and the felt gets smaller, and one the other way.
                resizeTo(client, 1, "a smaller interface");
                advance(SETTLE / 2);
            }
            case 69 -> {
                theBoardIsStillFramed(client, "at the smallest interface");
                shoot(client, "27-a-smaller-interface");
                resizeTo(client, 0, "the automatic interface again");
                advance(SETTLE / 2);
            }
            case 70 -> {
                // The two questions a token asks. Nothing else in this run opens either of
                // them, and both are screens somebody can get stuck on: the first one is the
                // only place in the mod that takes a line of typing from a player.
                openTheTokenQuestion(client);
                advance(SETTLE / 2);
            }
            case 71 -> {
                expectScreen(client, "asking for a token", TextPromptScreen.class);
                shoot(client, "28a-what-token");
                typeInto(client, "Treasure");
                press(client, "OK");
                advance(SETTLE / 2);
            }
            case 72 -> {
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
            case 73 -> {
                expectScreen(client, "backing out of a token", TableScreen.class);
                theBoardIsStillFramed(client, "back at the automatic interface");
                shoot(client, "28-back-to-normal");
                // A game has to be finishable. Taken as far as the question and then backed
                // out of, because going through with it would end the game this run is still
                // using - but the wiring from menu entry to question is the part that was
                // missing entirely, and it is the part this proves.
                aGameCanBeConceded(client);
                // Last, because everything above needs a seat: stand up mid-game and look at
                // the same table as somebody who is only watching it.
                standUp(client);
                // Half a second, deliberately short: the table pushes the public board out on
                // its own every two seconds, so a longer wait here would cover for a seat
                // change that told nobody and this would pass either way.
                advance(SETTLE / 4);
            }
            case 74 -> {
                if (ClientTableState.seatAt(table).isPresent()) {
                    fail("standing up left the client still holding a seat");
                }
                shoot(client, "29-watching-from-outside");
                theFeltRunsToTheBottomForAWatcher(client);
                if (client.screen instanceof TableScreen board && !board.theLogHasRoom()) {
                    fail("a watcher has nowhere to open the game log");
                }
                aSpectatorReadsAGraveyard(client);
                advance(SETTLE / 2);
            }
            case 75 -> {
                expectScreen(client, "a spectator opening a graveyard", PileScreen.class);
                shoot(client, "30-a-spectator-reads-a-graveyard");
                if (client.screen != null) {
                    client.screen.onClose();
                }
                pokeEverything(client);
                advance(SETTLE);
            }
            case 76 -> {
                expectScreen(client, "a spectator using every gesture on the board",
                        TableScreen.class);
                shoot(client, "31-still-watching");
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
    /**
     * Changes the interface size, and says so only if it actually changed.
     *
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
        return wanted == null ? middleOfTheMat : screenPointFor(client, wanted, middleOfTheMat);
    }

    /**
     * The pixel that points at a given spot on the felt, found by sweeping the window.
     *
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
                return new double[] {where.centreX(), where.centreY()};
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
                // The scene plays Commander, so the column is the full four.
                .map(seat -> board.board().pileRect(seat, index, Zone.pilesFor(true)))
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

    /**
     * Drags the top card off a zone and onto the middle of the mat.
     *
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
        board.mouseClicked(zone.centreX(), zone.centreY(), 0);
        board.mouseDragged(onto[0], onto[1], 0,
                onto[0] - zone.centreX(), onto[1] - zone.centreY());
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
        client.screen.mouseClicked(zone.centreX(), zone.centreY(), button);
        client.screen.mouseReleased(zone.centreX(), zone.centreY(), button);
    }

    /**
     * A watcher's board runs to the bottom of the window, because they have no hand.
     *
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
     *
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
     *
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
        pile.mouseClicked(last.centreX(), last.centreY(), 0);
        pile.mouseDragged(first.centreX(), first.centreY(), 0, 0, 0);
        pile.mouseReleased(first.centreX(), first.centreY(), 0);

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
     *
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
     *
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
     *
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

    /** Rests the cursor on a zone, so the next step can read what it says about itself. */
    private static void hoverAZone(Minecraft client, int index) {
        Rect zone = zoneRect(client, index);
        if (zone.isEmpty()) {
            fail("no zone " + index + " to rest on");
            return;
        }
        hover(client, new int[] {(int) zone.centreX(), (int) zone.centreY()});
    }

    /**
     * Checks that resting on a pile names it and says what a press would do.
     *
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
     * Looks at the top of this player's own library and opens the screen that decides.
     *
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
     *
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
     *
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
            session.submit(new GameEvent.DeckLoaded(theirs, library, List.of()));
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
     *
     * <p>Through the session rather than through this client, which is the point: a card
     * somebody else moves is the case the travelling had to cover, and the only case where
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
     *
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
     *
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

    /** How much commander damage this player has taken from the seat opposite. */
    private static int damageTaken(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        if (me == null || board == null) {
            return -1;
        }
        return board.seat(me).commanderDamage().getOrDefault(new SeatId(1), 0);
    }

    /**
     * Gives up the seat, the way a player does: right-click your own edge of the table.
     *
     * <p>Through the block rather than through the seat register, because what is being
     * checked is what a player can do to themselves - and because the board they are left
     * holding afterwards is the whole point.
     */
    /**
     * Checks a player can reach the end of a game, and then does not end it.
     *
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
        if (!openTheTableMenu(client, board, "Concede")) {
            fail("no felt on the board offered a table menu to concede from");
            return;
        }
        if (!board.pressMenuEntry("Concede")) {
            fail("the table menu offers no way to concede");
            return;
        }
        if (!(client.screen instanceof ConfirmScreen)) {
            fail("conceding did not ask first: "
                    + (client.screen == null ? "none" : client.screen.getClass().getSimpleName()));
            return;
        }
        // No picture: a screen only opened this instant has not been drawn yet, and the
        // frame this would grab is the board that was up before it.
        System.out.println("[devscene] conceding asks before it ends the game");
        client.screen.onClose();
        if (client.screen != before) {
            fail("backing out of conceding did not come back to the board");
        }
    }

    /**
     * Right-clicks bare felt until the table's own menu is up.
     *
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
     *
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
        if (!openTheTableMenu(client, board, "Leave the table")) {
            fail("no felt on the board offered a table menu with a way to leave the table");
            return;
        }
        board.pressMenuEntry("Leave the table");
        System.out.println("[devscene] left the table from its own menu");
        // Leaving puts the board away, which is what standing up and walking off means. The
        // run wants to go on watching the same table without a seat, so it opens it again the
        // way anybody with no seat opens it.
        client.setScreen(new TableScreen(table));
    }

    /**
     * Every gesture the board has, aimed at somebody who has no seat.
     *
     * <p>Each of them is written for a player with a seat and several insist on one. Whether
     * the ones that insist can be reached without a seat is a question about control flow, and
     * reasoning about control flow is how an unreachable path that turns out to be reachable
     * stays in a codebase. So: click a card, right-click a card, click every zone, drag from
     * the hand, and press every key that does something.
     */
    /**
     * A graveyard is public, so somebody who is only watching the game may read one.
     *
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
        board.mouseClicked(zone.centreX(), zone.centreY(), 0);
        board.mouseReleased(zone.centreX(), zone.centreY(), 0);
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
     *
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
     * Presses one of the buttons printed on the player's own mat.
     *
     * <p>At the place the board says the button is, rather than by calling what the button
     * calls: a box drawn where nothing listens for a click is exactly the fault worth
     * catching, and calling the action directly would pass with no box drawn at all.
     */
    /**
     * The life total is inside the window at the framing a player spends the game in.
     *
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
     *
     * <p>Pressing only ever reaches the chair this run sits in, and only the near one: the
     * far seat's counter is drawn turned round to face its own player, and a table where that
     * one had no ends at all would look perfectly right from here.
     *
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

    /** This player's own life, as the board they are looking at reports it. */
    private static int myLife(Minecraft client) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        GameView view = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        return me == null || view == null ? 0 : view.seat(me).life();
    }

    /**
     * Presses one end of this player's own life counter.
     *
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
                ? screenPointFor(client, new double[] {end.centreX(), end.centreY()}, null)
                : new int[] {(int) end.centreX(), (int) end.centreY()};
        if (at == null) {
            fail("the life counter on the block was not under any pixel of the window");
            return;
        }
        client.screen.mouseClicked(at[0], at[1], 0);
        client.screen.mouseReleased(at[0], at[1], 0);
    }

    /**
     * Presses the tax written under this player's own commander.
     *
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
                ? screenPointFor(client, new double[] {band.centreX(), band.centreY()}, null)
                : new int[] {(int) band.centreX(), (int) band.centreY()};
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
            return screenPointFor(client, new double[] {where.centreX(), where.centreY()}, null);
        }
        return new int[] {(int) where.centreX(), (int) where.centreY()};
    }

    private static void hoverAVerbButton(Minecraft client, TableVerb verb) {
        int[] at = verbButtonAt(client, verb);
        if (at != null) {
            hover(client, at);
        }
    }

    /**
     * Checks that resting on a button produces its name and the key that does the same.
     *
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

    private static void pressAVerbButton(Minecraft client, TableVerb verb) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to press a mat button on");
            return;
        }
        // The same lookup the hover uses. It was two lookups once, and the copy that pressed
        // went on aiming at the seated board's pixels after the other had learnt that the
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
     *
     * <p>Everything else in this run that asks where the cursor is asks {@link TablePointer},
     * including the helper that finds a card to hover - so a picker that is off by a constant
     * would place the cursor by its own wrong answer and then agree with itself. Minecraft's
     * crosshair ray is worked out from the camera by the game, and at the exact centre of the
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
        // The camera's own forward axis, dropped onto the felt by hand. The exact centre of
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
     *
     * <p>The centre only proves there is no constant offset: a camera looks along its forward
     * axis whatever its lens does. An offset that grows towards the edges - the shape a wrong
     * field of view or a wrong aspect ratio makes - passes that check and ruins every click
     * that is not dead centre.
     *
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

    /** Where in the turn the table thinks everybody is. */
    private static Phase phaseNow() {
        GameView board = table == null ? null : ClientTableState.viewOf(table).orElse(null);
        return board == null ? null : board.turn().phase();
    }

    /**
     * Steps the shared turn marker on, off the table's own menu.
     *
     * <p>A marker nobody can move is a marker that says "untap" for the whole game, which is
     * worse than no marker: it is wrong for all but a moment of every turn and everybody
     * learns to stop reading it.
     */
    private static void stepThePhase(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to step the phase on");
            return;
        }
        if (!openTheTableMenu(client, board, "Next phase")) {
            fail("the table menu offers no way to move the turn marker on");
            return;
        }
        board.pressMenuEntry("Next phase");
    }

    /** Asks the table to take back the last thing this player did, off its own menu. */
    private static void undoTheLastThing(Minecraft client) {
        if (!(client.screen instanceof TableScreen board)) {
            fail("there was no board to undo anything from");
            return;
        }
        if (!openTheTableMenu(client, board, "Undo my last action")) {
            fail("the table menu offers no way to take back a misclick");
            return;
        }
        board.pressMenuEntry("Undo my last action");
    }

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
                        board.mouseClicked(zone.centreX(), zone.centreY(), button);
                        board.mouseReleased(zone.centreX(), zone.centreY(), button);
                    }
                }
            }
        }
        Rect hand = TableScreenLayout.of(width, height).hand();
        board.mouseClicked(hand.centreX(), hand.centreY(), 0);
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
    /**
     * Draws this many cards the way a player would: one press of the draw key each.
     *
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
