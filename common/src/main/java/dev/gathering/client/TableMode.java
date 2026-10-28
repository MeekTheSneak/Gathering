package dev.gathering.client;

import dev.gathering.core.game.visibility.GameView;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Which kind of board a table screen is showing, and what that decides.
 * <p>One screen draws three boards on purpose - a game at a table, the lesson played on this
 * client alone, and a finished game played back - so every layout rule is the one people
 * play on. What differs between them used to be two booleans read in two dozen places, with
 * the combinations that made sense known only to whoever wrote each branch. The differences
 * are here instead, each asked by name: where the board comes from, where its flights and
 * news are filed, whether a move may be sent anywhere, and whether there is a block in the
 * world to ask about the game.
 * <p>Fixed for the life of a screen. A mode that could change halfway through is a mode a
 * gesture can be in the middle of when it does.
 * <p>Not the lesson's network fence. That is {@link ClientTableActions#send}, which routes on
 * the position and so cannot be fooled by a screen, a sub-screen or a menu callback that
 * forgot to ask; {@link #sendsMoves()} is the replay's belt in front of it.
 */
enum TableMode {

    /** A game at a table in the world. */
    PLAYING,

    /** The guided first game, played against nothing on this client alone. */
    LEARNING,

    /** A finished game, watched back. Nothing can be done to it. */
    WATCHING;

    /**
     * Where the board comes from: the replay's current frame, or the table's latest view. The
     * lesson's table is {@link TutorialDemo#table()}, whose views are filed like any other.
     */
    Optional<GameView> view(BlockPos table) {
        return this == WATCHING ? ClientReplay.frame() : ClientTableState.viewOf(table);
    }

    /** Where this board's flights, pointing and news are filed. See {@link TableScreen#replayTable()}. */
    BlockPos filedUnder(BlockPos table) {
        return this == WATCHING ? TableScreen.replayTable() : table;
    }

    /** Whether a move made on this board may be sent at all. A game that is over cannot be played. */
    boolean sendsMoves() {
        return this != WATCHING;
    }

    /**
     * Whether there is a block in the world to ask about this game - which format it was set
     * up in, say. A replay's game has left its block, and the lesson never had one.
     */
    boolean hasABlock() {
        return this == PLAYING;
    }

    /** Only a replay: the board is looked at and nothing on it answers a gesture. */
    boolean isWatching() {
        return this == WATCHING;
    }

    /** Only the lesson. */
    boolean isLearning() {
        return this == LEARNING;
    }
}
