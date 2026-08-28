package dev.gathering.client;

import dev.gathering.network.TableSaidPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * What has just been said at a table, held long enough to be read.
 *
 * <p>The board covers the window, and the window is where Minecraft draws its chat - so a
 * player reading their hand cannot see the person across the table talking to them. This is
 * the same lines, kept here so the board can draw them over the felt.
 *
 * <p>Held rather than logged. Chat is not a move: it is not in the session's event log, it is
 * not folded into any board, and undo cannot reach it. A rewind that struck out what somebody
 * said would be a mod editing a conversation. So it lives on the client that heard it, for as
 * long as it is worth reading, and then it is gone.
 *
 * <p>Client-only, and touched from the network thread as well as the render thread.
 */
public final class ClientTableChat {

    /** One thing somebody said, and when this client heard it. */
    public record Said(BlockPos table, String who, String text, long at) {
    }

    /**
     * How long a line stays on the felt.
     *
     * <p>Long enough to catch up after looking down at your hand, short enough that the board
     * is not permanently wearing a conversation. Whatever has scrolled off is still in the
     * chat window, where it always was.
     */
    public static final long SHOWN_MILLIS = 20_000L;

    /** How many lines the felt shows at once, newest at the bottom. */
    public static final int SHOWN_AT_ONCE = 5;

    /** How many are kept at all. Small: the chat window is the archive, this is the echo. */
    private static final int KEPT = 32;

    private static final List<Said> HEARD = new ArrayList<>();

    private ClientTableChat() {
    }

    /**
     * Heard something: keeps it for the board and puts it in the chat window.
     *
     * <p>Both, because there are two places a player might be. Somebody walking round the
     * world reads the chat window like any other message; somebody sitting at the board has
     * that window covered by the board and reads it off the felt. One line, two places, and
     * neither of them showing it twice - a screen is drawn over the chat window, not beside it.
     *
     * <p>Main thread, so it may touch the client's own chat.
     */
    public static void accept(TableSaidPayload said) {
        heard(said.table(), said.who(), said.text());
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.getChat().addMessage(
                    Component.translatable("chat.gathering.table", said.who(), said.text()));
        }
    }

    /** Heard something. Called from the network thread. */
    public static void heard(BlockPos table, String who, String text) {
        if (table == null || who == null || text == null || text.isBlank()) {
            return;
        }
        synchronized (ClientTableChat.class) {
            HEARD.add(new Said(table.immutable(), who, text, System.currentTimeMillis()));
            while (HEARD.size() > KEPT) {
                HEARD.remove(0);
            }
        }
    }

    /** The lines this table has heard lately, oldest first, at most {@link #SHOWN_AT_ONCE}. */
    public static List<Said> recentAt(BlockPos table, long now) {
        List<Said> recent = new ArrayList<>(SHOWN_AT_ONCE);
        synchronized (ClientTableChat.class) {
            for (Said said : HEARD) {
                if (said.table().equals(table) && now - said.at() < SHOWN_MILLIS) {
                    recent.add(said);
                }
            }
        }
        return recent.size() <= SHOWN_AT_ONCE
                ? recent
                : new ArrayList<>(recent.subList(recent.size() - SHOWN_AT_ONCE, recent.size()));
    }

    /** What one server told us is not true of the next one. */
    public static void clear() {
        synchronized (ClientTableChat.class) {
            HEARD.clear();
        }
    }
}
