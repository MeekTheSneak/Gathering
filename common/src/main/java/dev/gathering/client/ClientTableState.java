package dev.gathering.client;

import dev.gathering.core.game.visibility.GameView;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * The board this client has been told about.
 *
 * <p>One table at a time, because a player sits at one table at a time. Replaced wholesale
 * every time the server sends a new view rather than patched: the server decides what this
 * client may know, and a client that merged updates into a board it was keeping would
 * eventually be holding something it was told once and is no longer entitled to.
 *
 * <p>Client-only.
 */
public final class ClientTableState {

    private static volatile BlockPos table;
    private static volatile GameView view;

    private ClientTableState() {
    }

    public static void accept(BlockPos at, GameView board) {
        table = at;
        view = board;
    }

    public static Optional<GameView> view() {
        return Optional.ofNullable(view);
    }

    public static Optional<BlockPos> table() {
        return Optional.ofNullable(table);
    }

    /** On disconnect, and when a game ends: what one table showed is not true of the next. */
    public static void clear() {
        table = null;
        view = null;
    }
}
