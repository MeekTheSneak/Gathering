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

    /** What everybody together has out, kept as it goes rather than summed. */
    private static int outstandingAltogether;

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
                OUTSTANDING_ALTOGETHER - outstandingAltogether);
        if (room <= 0) {
            return;
        }
        List<UUID> asking = unknown.size() > room ? unknown.subList(0, room) : unknown;
        BUSY.put(player.getUUID(),
                new Outstanding(connection, (busy == null ? 0 : busy.count()) + asking.size()));
        outstandingAltogether += asking.size();
        send(player, service, connection, List.copyOf(asking));
    }

    /**
     * Forgets a player's outstanding work, for a disconnect or a server stop.
     * <p>The work itself cannot be recalled - it is on a shared queue - so what this does is
     * make sure it changes nothing when it lands. The global count comes down here, because
     * that work is no longer anybody's allowance to spend.
     */
    public static void forget(UUID player) {
        Outstanding gone = BUSY.remove(player);
        if (gone != null) {
            outstandingAltogether = Math.max(0, outstandingAltogether - gone.count());
        }
    }

    /** Forgets everybody's, for a server that is stopping. */
    public static void clear() {
        BUSY.clear();
        outstandingAltogether = 0;
    }

    private static void send(ServerPlayer player, CardDataService service, long connection,
            List<UUID> wanted) {
        service.findAll(wanted).whenComplete((cards, failure) -> player.server.execute(() -> {
            // Only against the connection that asked. A completion carrying a stamp from
            // before a relog belongs to a client that has gone, and taking it off the new
            // client's allowance would hand out a second one for the same work.
            Outstanding busy = BUSY.get(player.getUUID());
            if (busy != null && busy.connection() == connection) {
                int left = busy.count() - wanted.size();
                outstandingAltogether = Math.max(0, outstandingAltogether - wanted.size());
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
