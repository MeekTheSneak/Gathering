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
            // A commander, because the table's one-click path starts a game of Commander and a
            // run whose command zone is empty photographs a format nobody is playing - and
            // never touches anything that only exists once somebody has a commander.
            "Commander",
            "1 Sakura-Tribe Elder",
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

    /** How full the graveyard was before a card was dragged back out of it. */
    private static int inTheGraveyard;

    /** What the hand held before a mat button was pressed. */
    private static int inTheHand;

    /** Casts recorded on this player's commander before the button was pressed. */
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
                advance(SETTLE);
            }
            case 11 -> {
                int now = countIn(Zone.HAND);
                if (now <= inTheHand) {
                    fail("the draw button on the mat drew nothing: "
                            + inTheHand + " to " + now);
                }
                advance(0);
            }
            case 12 -> {
                // The shared turn marker. Nothing enforces it, which is exactly why it has to
                // be movable: a marker stuck on "untap" all game is worse than none.
                wasInPhase = phaseNow();
                stepThePhase(client);
                advance(SETTLE);
            }
            case 13 -> {
                Phase now = phaseNow();
                if (now == null || now == wasInPhase) {
                    fail("the turn marker did not move on: still " + wasInPhase);
                }
                advance(0);
            }
            case 14 -> {
                // Taking a move back. Every misclick in a game played by dragging cards about
                // is permanent without this, and all of the machinery for it existed with
                // nothing able to ask for it.
                beforeTheUndo = phaseNow();
                undoTheLastThing(client);
                advance(SETTLE);
            }
            case 15 -> {
                Phase now = phaseNow();
                if (now == null || now == beforeTheUndo) {
                    fail("undoing the phase step changed nothing: still " + beforeTheUndo);
                }
                advance(0);
            }
            case 16 -> {
                // Play a card: take the first one in hand and drop it on the near mat. The one
                // gesture the whole table is built around, and the one nothing has yet checked
                // end to end.
                playACard(client);
                advance(SETTLE);
            }
            case 17 -> {
                shoot(client, "07-card-played");
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 18 -> {
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
            case 19 -> {
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
            case 20 -> {
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
            case 21 -> {
                shoot(client, "11-log");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_L, 0, 0);
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 22 -> {
                shoot(client, "12-key-list");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 23 -> {
                // Into the graveyard: the drop that has to land on a zone rather than on felt.
                dropIntoAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD));
                advance(SETTLE);
            }
            case 24 -> {
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
            case 25 -> {
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
            case 26 -> {
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
            case 27 -> {
                aPileSaysWhatItDoes(client, Zone.GRAVEYARD);
                // The graveyard has a card in it by now, and left-clicking a pile that is not
                // a library opens it. Anything else here is a dead end the player would find.
                clickAZone(client, Zone.PILES.indexOf(Zone.GRAVEYARD), 0);
                advance(SETTLE);
            }
            case 28 -> {
                expectScreen(client, "left-clicking the graveyard", PileScreen.class);
                shoot(client, "16-graveyard-open");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                advance(SETTLE);
            }
            case 29 -> {
                expectScreen(client, "closing the graveyard", TableScreen.class);
                // Right-click on the library, which is where every verb a library has lives.
                clickAZone(client, Zone.PILES.indexOf(Zone.LIBRARY), 1);
                advance(SETTLE / 2);
            }
            case 30 -> {
                if (!menuIsOpen(client)) {
                    fail("right-clicking the library opened no menu");
                }
                shoot(client, "17-library-menu");
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                // Look at the top three, which is the half of a scry that already worked.
                lookAtTheTopOfTheLibrary(client, 3);
                advance(SETTLE);
            }
            case 31 -> {
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
            case 32 -> {
                shoot(client, "19-one-going-to-the-bottom");
                press(client, "Done");
                advance(SETTLE);
            }
            case 33 -> {
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
            case 34 -> {
                if (client.screen instanceof TableScreen board && board.isHoveringSomething()) {
                    fail("a cursor off the board still had a card under it");
                }
                shoot(client, "21-on-the-table-in-play");
                hover(client, cardPoint(client));
                advance(SETTLE / 2);
            }
            case 35 -> {
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
            case 36 -> {
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
            case 37 -> {
                aButtonSaysWhatItDoes(client, TableVerb.DRAW, "2");
                pressAVerbButton(client, TableVerb.DRAW);
                advance(SETTLE);
            }
            case 38 -> {
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
            case 39 -> {
                // The whole table, which is the one framing that shows the chair nobody is in.
                if (client.screen != null) {
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
                }
                advance(SETTLE / 2);
            }
            case 40 -> {
                // Somebody sits down opposite. Every picture so far has been of a table with
                // one player at it, which is not the game this is for.
                seatARival(client);
                advance(SETTLE);
            }
            case 41 -> {
                shoot(client, "23-two-players");
                openMyCounters(client);
                advance(SETTLE / 2);
            }
            case 42 -> {
                expectScreen(client, "asking for my own counters", CountersScreen.class);
                shoot(client, "24-commander-damage");
                tookCommanderDamage = damageTaken(client);
                press(client, "+");
                advance(SETTLE);
            }
            case 43 -> {
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
            case 44 -> {
                expectScreen(client, "pressing Done on the counters", TableScreen.class);
                // The other number a game of Commander asks a player to keep for an hour.
                taxPaid = commanderTax(client);
                openCommanderCounters(client);
                advance(SETTLE / 2);
            }
            case 45 -> {
                expectScreen(client, "asking for a commander's counters", CountersScreen.class);
                shoot(client, "25a-commander-tax");
                press(client, "+");
                advance(SETTLE);
            }
            case 46 -> {
                int now = commanderTax(client);
                if (now <= taxPaid) {
                    fail("commander tax did not go up: " + taxPaid + " to " + now);
                }
                press(client, "Done");
                advance(SETTLE / 2);
            }
            case 47 -> {
                expectScreen(client, "leaving a commander's counters", TableScreen.class);
                shoot(client, "26-the-whole-table");
                // A window somebody has resized, which is the one path that re-runs a screen's
                // init on an instance that is already holding a game. Two sizes: one where
                // everything gets bigger and the felt gets smaller, and one the other way.
                resizeTo(client, 1, "a smaller interface");
                advance(SETTLE / 2);
            }
            case 48 -> {
                theBoardIsStillFramed(client, "at the smallest interface");
                shoot(client, "27-a-smaller-interface");
                resizeTo(client, 0, "the automatic interface again");
                advance(SETTLE / 2);
            }
            case 49 -> {
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
            case 50 -> {
                if (ClientTableState.seatAt(table).isPresent()) {
                    fail("standing up left the client still holding a seat");
                }
                shoot(client, "29-watching-from-outside");
                theFeltRunsToTheBottomForAWatcher(client);
                aSpectatorReadsAGraveyard(client);
                pokeEverything(client);
                advance(SETTLE);
            }
            case 51 -> {
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
    private static void lookAtTheTopOfTheLibrary(Minecraft client, int howMany) {
        SeatId me = ClientTableState.seatAt(table).orElse(null);
        if (me == null) {
            fail("no seat to scry with");
            return;
        }
        ClientTableActions.send(table, new GameEvent.LibraryLooked(me, me, howMany));
        client.setScreen(new PileScreen(
                table, me, Zone.LIBRARY, true, PileScreen.Decision.SCRY, client.screen));
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
            TableBroadcast.sendToTable(server.overworld(), where);
            System.out.println("[devscene] a rival sat down opposite");
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
        ZoneView command = board.seat(me).zone(Zone.COMMAND);
        if (command == null) {
            return null;
        }
        for (CardView held : command.cards()) {
            if (held instanceof CardView.Visible visible) {
                return visible.id();
            }
        }
        return null;
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
        CardInstanceId commander = myCommander(client);
        if (commander == null) {
            fail("there was no commander in the command zone to tax");
            return;
        }
        client.setScreen(new CountersScreen(table,
                new CountersScreen.Subject.Cards(
                        java.util.List.of(commander),
                        net.minecraft.network.chat.Component.literal("Commander")),
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
        if (client.screen instanceof PileScreen) {
            System.out.println("[devscene] a spectator opened a seated player's graveyard");
            shoot(client, "30-a-spectator-reads-a-graveyard");
            client.screen.onClose();
        } else {
            fail("a spectator could not open a seated player's graveyard");
        }
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
