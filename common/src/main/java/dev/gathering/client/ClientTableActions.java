package dev.gathering.client;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.EventCodec;
import dev.gathering.network.TableActionPayload;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.core.BlockPos;

/**
 * Sends a move to the server.
 * <p>The client describes what it did and the server decides whether it happened. Nothing is
 * applied here first: a board that showed a move before the server agreed to it would be a
 * board that sometimes has to take one back, and in a game with hidden information "take that
 * back" is a sentence with information in it.
 * <p>Client-only.
 */
public final class ClientTableActions {

    private ClientTableActions() {
    }

    public static void send(BlockPos table, GameEvent event) {
        // The guided first game is played on this client and reaches no server at all. It is
        // routed here, at the one place every verb in the mod passes through, rather than in
        // the screens: the table screen, the counters screen and the pile screen all send
        // their own moves, so a check written in one of them would be a check the other two
        // do not make - and the counters screen is where a whole step of the tutorial
        // happens. Routing on the position rather than on a screen's idea of its own mode
        // means a move made during the demonstration reaches the local session or reaches
        // nothing. There is no third outcome and no way to add one by forgetting a guard.
        if (TutorialDemo.at(table)) {
            TutorialDemo.submit(event);
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                EventCodec.write(out, event);
            }
            ClientNetworking.send(new TableActionPayload(table, bytes.toByteArray()));
        } catch (IOException e) {
            // Nothing sent means nothing happens, which is the right outcome for a move this
            // client could not even describe.
            throw new IllegalStateException("Could not describe a move at the table", e);
        }
    }
}
