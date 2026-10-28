package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.network.RequestCardMetadataPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import dev.gathering.network.CardsUnresolvedPayload;
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

    /**
     * Every printing each connection has been answered about, so it is answered once.
     * <p>From the 2026-09-25 sweep. This is the one card request with no budget in front of it, and
     * it answered every printing it found in memory every time it was asked: a sixteen-kilobyte
     * request naming a thousand warm printings came back as four packets of names, rules text and
     * picture addresses, about forty times its own size to encode and compress, as often as a client
     * could send it. A client keeps what it is told until it disconnects, so nothing honest needs
     * telling twice - and the deck screen, which asks for the whole deck again whenever it opens or
     * the window changes size, was being told the whole deck again each time.
     * <p>Per player, emptied when they leave, which is when their client's cache empties too.
     * Server thread only.
     */
    private static final Map<UUID, java.util.Set<UUID>> ANSWERED = new HashMap<>();

    /** The most printings remembered as answered for one connection: what the server holds in memory. */
    private static final int MOST_REMEMBERED_ANSWERED = 16_384;

    /**
     * Past that, how many more a connection may be answered from memory, a printing at a time.
     * <p>So a client that really has been shown sixteen thousand printings in one sitting is still
     * answered, and one sending the same requests round and round is answered at a walk. Not
     * forgotten to make room: a list emptied when it filled up is one a flood cycles through.
     */
    static final ActionBudget PAST_WHAT_IS_REMEMBERED = new ActionBudget(256, 2048);

    /**
     * Whether this printing should be answered to this player now, writing it down if so.
     * <p>Never for one already answered on this connection.
     */
    private static boolean answering(UUID player, java.util.Set<UUID> answered, UUID printing) {
        if (answered.contains(printing)) {
            return false;
        }
        if (answered.size() < MOST_REMEMBERED_ANSWERED) {
            answered.add(printing);
            return true;
        }
        return PAST_WHAT_IS_REMEMBERED.spend(player, 1);
    }

    /**
     * Answers one request: from memory at once where it can, and from the worker for the rest.
     *
     * @return how many printings were answered from memory, which is what a test counts - a
     *     stand-in player cannot be sent the answers themselves
     */
    public static int handle(ServerPlayer player, CardDataService service, RequestCardMetadataPayload request) {
        List<UUID> wanted = request.printings().stream()
                .distinct()
                .limit(RequestCardMetadataPayload.MAX_REQUESTED)
                .toList();
        if (wanted.isEmpty()) {
            return 0;
        }

        // Anything already in memory is answered from memory, on this thread, with no work
        // queued at all - which is most of what a screen asks for twice. And anything this
        // connection has been told already is not answered at all: its client still has it.
        java.util.Set<UUID> answered = ANSWERED.computeIfAbsent(player.getUUID(), ignored -> new java.util.HashSet<>());
        List<CardSummary> known = new ArrayList<>();
        List<UUID> unknown = new ArrayList<>();
        for (UUID printing : wanted) {
            if (answered.contains(printing)) {
                continue;
            }
            CardMetadata have = service.peek(printing).orElse(null);
            if (have == null) {
                unknown.add(printing);
            } else if (answering(player.getUUID(), answered, printing)) {
                known.add(CardSummary.of(have));
            }
        }
        if (!known.isEmpty()) {
            for (CardMetadataPayload packet : CardMetadataPayload.inPackets(known)) {
                Sending.to(player, packet);
            }
        }
        if (unknown.isEmpty()) {
            return known.size();
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
            return known.size();
        }
        List<UUID> asking = unknown.size() > room ? unknown.subList(0, room) : unknown;
        BUSY.put(player.getUUID(),
                new Outstanding(connection, (busy == null ? 0 : busy.count()) + asking.size()));
        queuedAltogether += asking.size();
        send(player, service, connection, List.copyOf(asking));
        return known.size();
    }

    /**
     * Forgets a player's own allowance, and what they have been told, for a disconnect.
     * <p>Theirs only. The work itself cannot be recalled - it is on a shared queue -
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
        // And what they have been told, because a client that comes back has an empty cache.
        ANSWERED.remove(player);
        PAST_WHAT_IS_REMEMBERED.forget(player);
    }

    /**
     * Forgets everybody's, for a server that is stopping.
     * <p>The shared count goes here and only here, because a stopping server is the one moment
     * the queue itself is going away with it.
     */
    public static void clear() {
        BUSY.clear();
        queuedAltogether = 0;
        ANSWERED.clear();
        PAST_WHAT_IS_REMEMBERED.clear();
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
            if (player.hasDisconnected()) {
                return;
            }
            Answer answer = answerFor(wanted, cards, failure);
            // Written down as answered like the ones that came from memory, and only the ones
            // this connection has not been told since it asked: two requests can be out for one
            // printing at once.
            java.util.Set<UUID> answered = ANSWERED.computeIfAbsent(
                    player.getUUID(), ignored -> new java.util.HashSet<>());
            List<CardSummary> found = new ArrayList<>(answer.found().size());
            for (CardSummary summary : answer.found()) {
                if (answering(player.getUUID(), answered, summary.scryfallId())) {
                    found.add(summary);
                }
            }
            // A request may name a whole deck, which is more summaries than the game will
            // write in one payload - and one it refuses to write disconnects whoever asked.
            for (CardMetadataPayload packet : CardMetadataPayload.inPackets(found)) {
                Sending.to(player, packet);
            }
            if (!answer.unresolved().isEmpty()) {
                Sending.to(player, answer.unresolved());
            }
        }));
    }

    /**
     * What a finished lookup tells the client: the names it found, and for every other
     * printing asked about, whether it does not exist or could not be looked up.
     *
     * @param found      a summary for each printing that has one
     * @param unresolved the rest, with a reason; empty when everything was found
     */
    public record Answer(List<CardSummary> found, CardsUnresolvedPayload unresolved) {
    }

    /**
     * Sorts a completed lookup into found, missing and unavailable.
     * <p>A lookup that failed - Scryfall unreachable, rate limited, a body that was not JSON -
     * says every printing it was for is unavailable, and nothing is missing: an outage is not
     * evidence a card does not exist, and a client told it was would stop asking. A lookup
     * that succeeded says a printing it did not come back with is missing.
     * <p>Public and free of the player, so the sorting can be checked without a network.
     */
    public static Answer answerFor(List<UUID> wanted, List<CardMetadata> cards, Throwable failure) {
        if (failure != null) {
            return new Answer(List.of(), new CardsUnresolvedPayload(List.of(), wanted));
        }
        List<CardSummary> summaries = new ArrayList<>();
        java.util.Set<UUID> named = new java.util.HashSet<>();
        for (CardMetadata card : cards == null ? List.<CardMetadata>of() : cards) {
            summaries.add(CardSummary.of(card));
            named.add(card.scryfallId());
        }
        List<UUID> missing = new ArrayList<>();
        for (UUID printing : wanted) {
            if (!named.contains(printing)) {
                missing.add(printing);
            }
        }
        return new Answer(List.copyOf(summaries), new CardsUnresolvedPayload(missing, List.of()));
    }
}
