package dev.gathering.client;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.EventCodec;
import dev.gathering.core.ui.BulkLimit;
import dev.gathering.network.TableActionPayload;
import dev.gathering.network.TableActionsPayload;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
        ClientNetworking.send(new TableActionPayload(table, describe(event)));
    }

    /**
     * Sends several moves that belong to one gesture, in order.
     * <p>A verb on a selection. Each move is still judged by the server on its own, gate for
     * gate, exactly as {@link #send} would have it judged; the table is shown the result once
     * rather than once per card. See {@link TableActionsPayload}.
     * <p>Split into as many batches as the payload's bounds need, in order, so a selection is
     * never quietly cut short here: {@code BulkLimit} decides how many a gesture may touch, and
     * says so, before anything reaches this. A single move goes as a single move.
     */
    public static void sendAll(BlockPos table, List<GameEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        if (TutorialDemo.at(table)) {
            // The same routing as a single move, for the same reason.
            events.forEach(TutorialDemo::submit);
            return;
        }
        if (events.size() == 1) {
            send(table, events.getFirst());
            return;
        }
        List<byte[]> batch = new ArrayList<>();
        int bytesInBatch = 0;
        for (GameEvent event : events) {
            byte[] described = describe(event);
            if (described.length > TableActionsPayload.MOST_BYTES_EACH) {
                // Too big to travel in a batch. Sent on its own, in its place in the order.
                flush(table, batch);
                bytesInBatch = 0;
                ClientNetworking.send(new TableActionPayload(table, described));
                continue;
            }
            if (batch.size() == BulkLimit.MOST_AT_ONCE
                    || bytesInBatch + described.length > MOST_BYTES_PER_BATCH) {
                flush(table, batch);
                bytesInBatch = 0;
            }
            batch.add(described);
            bytesInBatch += described.length;
        }
        flush(table, batch);
    }

    /**
     * How much event data one batch carries.
     * <p>Well inside what a client may put in one packet, with room for the position and the
     * list's own framing. A selection bigger than this goes as more than one batch.
     */
    private static final int MOST_BYTES_PER_BATCH = 16_000;

    private static void flush(BlockPos table, List<byte[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        ClientNetworking.send(batch.size() == 1
                ? new TableActionPayload(table, batch.getFirst())
                : new TableActionsPayload(table, batch));
        batch.clear();
    }

    private static byte[] describe(GameEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                EventCodec.write(out, event);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            // Nothing sent means nothing happens, which is the right outcome for a move this
            // client could not even describe.
            throw new IllegalStateException("Could not describe a move at the table", e);
        }
    }
}
