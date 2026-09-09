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

    /** Per player, how many printings a lookup is out for. Server thread only. */
    private static final Map<UUID, Integer> BUSY = new HashMap<>();

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
        int busy = BUSY.getOrDefault(player.getUUID(), 0);
        int room = OUTSTANDING_PER_PLAYER - busy;
        if (room <= 0) {
            return;
        }
        List<UUID> asking = unknown.size() > room ? unknown.subList(0, room) : unknown;
        BUSY.merge(player.getUUID(), asking.size(), Integer::sum);
        send(player, service, List.copyOf(asking));
    }

    /** Forgets a player's outstanding work, for a disconnect or a server stop. */
    public static void forget(UUID player) {
        BUSY.remove(player);
    }

    /** Forgets everybody's, for a server that is stopping. */
    public static void clear() {
        BUSY.clear();
    }

    private static void send(ServerPlayer player, CardDataService service, List<UUID> wanted) {
        service.findAll(wanted).whenComplete((cards, failure) -> player.server.execute(() -> {
            BUSY.computeIfPresent(player.getUUID(), (who, out) -> {
                int left = out - wanted.size();
                return left <= 0 ? null : left;
            });
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
