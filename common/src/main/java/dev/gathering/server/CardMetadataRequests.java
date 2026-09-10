package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.network.RequestCardMetadataPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Answers a client asking what the cards it is holding are called.
 * <p>Runs on the card pipeline's executor and answers from the cache, so a player opening a
 * deck they imported last week costs no Scryfall requests at all.
 */
public final class CardMetadataRequests {

    private CardMetadataRequests() {
    }

    /**
     * How many printings one player may have a lookup out for at a time.
     * <p>The card worker is one thread with a queue nobody was bounding, and this is the one
     * request any client may send about any printing at all. A client asking about a thousand
     * invented ids, over and over, put a thousand pieces of work in front of everybody else's
     * imports and pack openings. A deck is a hundred cards, so a hundred at a time is every
     * honest use of this and none of that one.
     */
    private static final int OUTSTANDING_PER_PLAYER = 128;

    /**
     * A ceiling across everybody, so a full table cannot make the budget meaningless.
     * <p>The per-player limit bounds one client. Eight of them each spending their whole
     * allowance is still eight hundred pieces of work in front of the imports and pack
     * openings sharing the one worker thread, which is the queue this was supposed to bound.
     */
    private static final int OUTSTANDING_ALTOGETHER = 512;

    /**
     * What one player has a lookup out for, and which connection asked.
     * <p>The count alone was not enough. It is keyed by player id, which survives a
     * disconnect - so a completion from before a relog subtracted from the allowance the
     * reconnected client had just been given, and a client could reconnect to get a second
     * allowance while the first one's work was still queued. The stamp is taken when a
     * player's accounting starts and thrown away when they leave, so a completion carrying an
     * old one belongs to a connection that has gone and changes nothing.
     */
    private record Outstanding(long connection, int count) {
    }

    /** Per player, what is out and for which connection. Server thread only. */
    private static final Map<UUID, Outstanding> BUSY = new HashMap<>();

    /** A number no two connections share, for the life of this process. */
    private static final java.util.concurrent.atomic.AtomicLong CONNECTIONS =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * What is actually queued on the shared worker, across everybody, whoever asked for it.
     * <p>Not per player, and not released by a disconnect. Releasing it on disconnect was the
     * bug: the work is on a shared executor and a logout cannot recall it, so freeing its
     * budget handed the next connection an allowance the worker had not actually finished with.
     * An audit ran six request-and-disconnect cycles for one player and admitted seven hundred
     * and sixty-eight lookups against a ceiling of five hundred and twelve.
     * <p>It comes down when a job completes, and only then - by whichever completion owned it,
     * whether or not the player who asked is still connected.
     */
    private static int queuedAltogether;

    public static void handle(ServerPlayer player, CardDataService service, RequestCardMetadataPayload request) {
        List<UUID> wanted = request.printings().stream()
                .distinct()
                .limit(RequestCardMetadataPayload.MAX_REQUESTED)
                .toList();
        if (wanted.isEmpty()) {
            return;
        }

        // Anything already in memory is answered from memory, on this thread, with no work
        // queued at all - which is most of what a screen asks for twice.
        List<CardSummary> known = new ArrayList<>();
        List<UUID> unknown = new ArrayList<>();
        for (UUID printing : wanted) {
            CardMetadata have = service.peek(printing).orElse(null);
            if (have != null) {
                known.add(CardSummary.of(have));
            } else {
                unknown.add(printing);
            }
        }
        if (!known.isEmpty()) {
            for (CardMetadataPayload packet : CardMetadataPayload.inPackets(known)) {
                Sending.to(player, packet);
            }
        }
        if (unknown.isEmpty()) {
            return;
        }

        // And the rest goes to the worker, up to what this player already has out. Over the
        // budget the request is simply not made: the client asks again when it draws the
        // cards it still cannot name, which is a screen refreshing rather than a queue
        // growing.
        Outstanding busy = BUSY.get(player.getUUID());
        long connection = busy == null ? CONNECTIONS.incrementAndGet() : busy.connection();
        int room = Math.min(
                OUTSTANDING_PER_PLAYER - (busy == null ? 0 : busy.count()),
                OUTSTANDING_ALTOGETHER - queuedAltogether);
        if (room <= 0) {
            return;
        }
        List<UUID> asking = unknown.size() > room ? unknown.subList(0, room) : unknown;
        BUSY.put(player.getUUID(),
                new Outstanding(connection, (busy == null ? 0 : busy.count()) + asking.size()));
        queuedAltogether += asking.size();
        send(player, service, connection, List.copyOf(asking));
    }

    /**
     * Forgets a player's own allowance, for a disconnect.
     * <p>Their allowance only. The work itself cannot be recalled - it is on a shared queue -
     * so the shared count stays exactly where it is until that work completes and its own
     * completion takes it off. Bringing it down here was a way to get a fresh allowance by
     * reconnecting: six cycles bought seven hundred and sixty-eight lookups against a ceiling
     * of five hundred and twelve, none of them finished.
     * <p>A completion belonging to the connection that has just gone still finds its stamp
     * missing and leaves the new connection's personal allowance alone; what it does do is
     * pay back the shared one, which is the part that was really spent.
     */
    public static void forget(UUID player) {
        BUSY.remove(player);
    }

    /**
     * Forgets everybody's, for a server that is stopping.
     * <p>The shared count goes here and only here, because a stopping server is the one moment
     * the queue itself is going away with it.
     */
    public static void clear() {
        BUSY.clear();
        queuedAltogether = 0;
    }

    /** What is queued on the shared worker right now, which is what a test asks. */
    public static int queued() {
        return queuedAltogether;
    }

    private static void send(ServerPlayer player, CardDataService service, long connection,
            List<UUID> wanted) {
        service.findAll(wanted).whenComplete(ServerRun.onServerThread(player, (cards, failure) -> {
            // The shared count comes down whatever else is true: this job is off the worker
            // now, and that is a fact about the queue rather than about whoever asked. It is
            // the only place it comes down outside a server stopping.
            queuedAltogether = Math.max(0, queuedAltogether - wanted.size());
            // The personal allowance, only against the connection that asked. A completion
            // carrying a stamp from before a relog belongs to a client that has gone, and
            // taking it off the new client's allowance would hand out a second one for the
            // same work.
            Outstanding busy = BUSY.get(player.getUUID());
            if (busy != null && busy.connection() == connection) {
                int left = busy.count() - wanted.size();
                if (left <= 0) {
                    BUSY.remove(player.getUUID());
                } else {
                    BUSY.put(player.getUUID(), new Outstanding(connection, left));
                }
            }
            if (player.hasDisconnected() || failure != null || cards == null || cards.isEmpty()) {
                return;
            }
            List<CardSummary> summaries = new ArrayList<>(cards.size());
            for (CardMetadata card : cards) {
                summaries.add(CardSummary.of(card));
            }
            // A request may name a whole deck, which is more summaries than the game will
            // write in one payload - and one it refuses to write disconnects whoever asked.
            for (CardMetadataPayload packet : CardMetadataPayload.inPackets(summaries)) {
                Sending.to(player, packet);
            }
        }));
    }
}
