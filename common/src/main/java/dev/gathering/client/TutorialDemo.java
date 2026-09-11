package dev.gathering.client;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The guided first game, played against nothing, on this client alone.
 * <p>The teaching board used to be a real session at a real table: the server dealt it, the
 * player sat in it, and ending it had to hand back whatever the table had taken. That is where
 * the worst defect this project has had came from - draw the twenty practice cards, put a real
 * deck down, stop practice, and the deck was gone. A board that can hold property is a board
 * that can lose it, so this one cannot hold any.
 * <p><b>Nothing here is on a server.</b> The session is built in this client's memory, the
 * cards are blank stock invented on the spot, and no packet is sent by anything done to it.
 * There is no seat to allocate, no block to mark, no flag to save and nothing to migrate when
 * it ends: closing the screen drops the whole game on the floor, and the floor is a field.
 * <p>The isolation is not a promise made in this comment. It is {@link #NOWHERE}: the demo's
 * board is filed under a position no table can occupy, and {@link ClientTableActions#send}
 * routes by that position rather than by asking a screen what it thinks it is doing. Every
 * verb in the mod goes through that one method - the table screen, the counters screen, the
 * pile screen and the scripted harness alike - so a move made here reaches the local session
 * or it reaches nothing. A screen that forgot which mode it was in cannot leak one.
 * <p>Client thread only.
 */
public final class TutorialDemo {

    /**
     * Where the demonstration's board is filed, which is nowhere a table can be.
     * <p>The same trick a replay uses, and for the same reason: the flights, the chat lines
     * and the news are all kept by table, and this game has no table. The world does not go
     * down this far, so nothing a player builds can ever share a drawer with it - and, because
     * the send path routes on this position, nothing a player builds can ever receive one of
     * its moves either. One below the replay's own place, so the two never collide.
     */
    private static final BlockPos NOWHERE = new BlockPos(0, Integer.MIN_VALUE, 1);

    /**
     * How many cards the demonstration library holds.
     * <p>Enough to keep drawing for as long as anybody wants to press the key, and few enough
     * that the board never looks like a real game somebody should be reading.
     */
    private static final int LIBRARY = 20;

    /** The seat the player is taught in. */
    private static final SeatId LEARNER = SeatId.of(0);

    /** The seat nobody is in, holding the one card there is to read. */
    private static final SeatId DEMONSTRATION = SeatId.of(1);

    /** The game, or null when no demonstration is running. */
    private static GameSession session;

    private TutorialDemo() {
    }

    /** Whether a demonstration is running on this client. */
    public static boolean running() {
        return session != null;
    }

    /**
     * Whether this is the demonstration's place rather than a table in the world.
     * <p>The routing question, asked by {@link ClientTableActions#send} of every move any
     * screen makes. It answers for the position alone and not for whether a demonstration
     * happens to be running, so a move addressed here when nothing is running is dropped
     * rather than delivered to a table - which is the safe direction and the only one.
     */
    public static boolean at(BlockPos where) {
        return NOWHERE.equals(where);
    }

    /** Where the demonstration's board and its drawers live. */
    public static BlockPos table() {
        return NOWHERE;
    }

    /**
     * Starts a fresh demonstration and puts the first instruction up.
     * <p>Everything is built here rather than asked for: the cards, the seats, the one card
     * there is to read. Nothing is requested from a server, so this works on a fresh install
     * with an empty card cache, no network and no deck - which is the whole point, because the
     * moment it is wanted is the moment a player has none of those.
     */
    public static void begin() {
        session = freshGame();
        Tutorial.beginAt(NOWHERE, board().orElse(null));
    }

    /**
     * Starts over, on a board rebuilt from nothing.
     * <p>A new game rather than a rewound one, which is what makes Restart work after the
     * library has been drawn empty: the old session is dropped whole and the twenty cards are
     * invented again. The instruction baselines are taken from the new board at the moment the
     * step changes - see {@link Tutorial#restartOn} - because a baseline taken from the board
     * that is already gone would measure the next action against a game nobody is playing.
     */
    public static void restart() {
        if (session == null) {
            return;
        }
        session = freshGame();
        Tutorial.restartOn(board().orElse(null));
    }

    /**
     * Runs a move against the demonstration, and nothing else.
     * <p>The local half of {@link ClientTableActions#send}. A refusal is an answer: the pure
     * authorization rules are the same ones the server would apply, so a move this board will
     * not take is one a real table would not have taken either, and the instruction stays up.
     * <p>Nothing is sent. Nothing is saved. The one visible effect is the board this client
     * draws next frame, and whether the step on screen is now done.
     */
    public static void submit(GameEvent event) {
        if (session == null || event == null) {
            return;
        }
        session.submit(event);
        // Fed the board rather than the event, exactly as a real table's step is judged: what
        // advances a step is a fact about the board afterwards, so a move the session refused
        // advances nothing without anybody having to ask whether it was accepted.
        board().ifPresent(after -> Tutorial.sawBoard(NOWHERE, after));
    }

    /** The board as the learner is entitled to see it, which is the only view ever built. */
    public static Optional<GameView> board() {
        return session == null
                ? Optional.empty()
                : Optional.of(VisibilityRules.viewFor(session.state(), new Viewer.Seated(LEARNER)));
    }

    /**
     * Drops the demonstration, however it ended.
     * <p>Everything it had was in this one field, so there is nothing else to undo: no seat to
     * release, no session to end, no flag to clear and nothing saved anywhere. Whether the
     * player finished or left partway through is {@link Tutorial}'s to record, and it is
     * recorded there rather than here so that the two facts stay one fact.
     * <p>Named {@code clear} because that is the name {@code tools/statecheck.py} looks for:
     * a client holder called this has to be named in {@link ClientState}, which is what makes
     * "a disconnect abandons the demonstration" a checked rule rather than a remembered one.
     */
    public static void clear() {
        session = null;
    }

    // ------------------------------------------------------------------ bits

    /**
     * A whole teaching board, from nothing.
     * <p>Commander's life total, because Commander is what walking up to a table already
     * starts and a tutorial that taught a different table from the one waiting afterwards
     * would be teaching the wrong one.
     */
    private static GameSession freshGame() {
        GameSession fresh = GameSession.create(
                List.of(LEARNER, DEMONSTRATION),
                FormatPresets.COMMANDER.startingLife(),
                // Local, never sent, never written down and never shown: this seed orders
                // twenty blank cards that are all the same card. It is here because a session
                // needs one, not because anything about this game is secret.
                SessionSeed.random(),
                UndoMode.shippedDefault());
        deal(fresh, LEARNER);
        deal(fresh, DEMONSTRATION);
        // One card face up in front of the seat nobody is in, so that "read a card somebody
        // else has played" is a thing that can be done by somebody sitting alone. Written
        // rather than blank, because the step is about reading and a card with nothing on it
        // teaches nothing.
        fresh.submit(new GameEvent.PaperCardCreated(DEMONSTRATION, DEMONSTRATION, PaperStock.BLANK,
                Component.translatable("tutorial.gathering.demonstration_card").getString()));
        return fresh;
    }

    /**
     * Deals one seat a library of blank stock.
     * <p>Blank stock rather than real cards is what makes this work with an empty card cache
     * and no network: there is nothing to look up. A player's first minute should not depend
     * on Scryfall answering. It is also the reason none of this can become property - there is
     * no item anywhere in it, and no path from here to one.
     */
    private static void deal(GameSession game, SeatId seat) {
        List<CardIdentity> library = new ArrayList<>(LIBRARY);
        for (int card = 0; card < LIBRARY; card++) {
            library.add(PaperStock.BLANK.identity());
        }
        game.submit(new GameEvent.DeckLoaded(seat, library, List.of(), null));
        game.submit(new GameEvent.LibraryShuffled(seat, seat));
    }
}
