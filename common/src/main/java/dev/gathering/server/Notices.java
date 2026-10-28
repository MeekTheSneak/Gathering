package dev.gathering.server;

import dev.gathering.network.NoticePayload;
import dev.gathering.network.Sending;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The server's way of telling one player one thing, now.
 * <p>What this replaces is {@code player.displayClientMessage(line, true)}: the action bar,
 * which is the right place for an answer to a click and the wrong place for one given while a
 * screen is open, because the screen is drawn over it. Sent as a payload instead, the client
 * puts it where it can actually be read.
 * <p>Falls back to the action bar for a client that never agreed to take the mod's payloads -
 * a vanilla client on a server running this, which is a connection the mod is expected to
 * survive. That client has none of the mod's screens open either, so the action bar is exactly
 * right for it.
 * <p>Not instead of chat. Everything said with {@code sendSystemMessage} is a line in the log a
 * player can scroll back through, and moving those here would be replacing a record with a
 * notice that fades. What {@link #answer} is for is the line that is both: the log keeps it, and
 * the player who pressed something is shown it where they are looking - which, at a board or on
 * any screen, is not the chat window, because the board and the screens are drawn over it.
 */
public final class Notices {

    /**
     * Told of every notice as it is given: who it is for, and what it says. Does nothing unless a
     * test adds to it.
     * <p>A stand-in player cannot take a payload - {@code Sending.to} refuses it, deliberately - so
     * a test cannot read the wire and reads this instead, one step before it, as the board tests
     * read {@code TableBroadcast.watchForTesting}. A list rather than one slot, because game tests
     * in a batch run alongside each other and each filters on the player it cares about.
     */
    // statecheck: test watchers only, set by a game test and holding nothing about a world
    private static final java.util.List<java.util.function.BiConsumer<java.util.UUID, Component>>
            WATCHERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private Notices() {
    }

    /**
     * Says it to one player, if that player is a real one on this server.
     * <p>Takes a {@code Player} rather than a {@code ServerPlayer} because most of the callers
     * are block interactions, which vanilla calls on both sides. The client-side copy says
     * nothing: the server's own call is what reaches the screen.
     */
    public static void tell(Player player, Component line) {
        if (!(player instanceof ServerPlayer told) || line == null) {
            return;
        }
        for (var watcher : WATCHERS) {
            watcher.accept(told.getUUID(), line);
        }
        if (!Sending.to(told, new NoticePayload(line))) {
            told.displayClientMessage(line, true);
        }
    }

    /**
     * The answer to something this player pressed: in chat, where it is kept, and as a notice,
     * where it is seen.
     * <p>For an answer given while the player is looking at a board or a screen. Both boards cover
     * the chat window - the flat one is felt from edge to edge, and the one on the block hides the
     * game's own interface - and so does every panel the mod opens. A refusal said only in chat
     * was a press that, as far as anybody at the table could see, did nothing.
     */
    public static void answer(Player player, Component line) {
        if (!(player instanceof ServerPlayer told) || line == null) {
            return;
        }
        told.sendSystemMessage(line);
        tell(told, line);
    }

    /** Watches every notice as it is given. For tests; see {@link #WATCHERS}. */
    public static void watchForTesting(java.util.function.BiConsumer<java.util.UUID, Component> watcher) {
        WATCHERS.add(watcher);
    }
}
