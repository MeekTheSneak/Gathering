package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.core.card.ArtToSend;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends a viewer the pictures for the cards they have just been shown.
 * <p>Used by the table and by a draft pod, which is why it is not called after either: both
 * have the same problem - a client can only ask about cards in its own inventory - and both
 * solve it the same way, by sending what the rules have just decided this viewer may see.
 * <p>A client only ever asked what a card looks like on behalf of cards in its own inventory,
 * which is the right scope for a request - it grants no access the player did not have. But it
 * means a card belonging to somebody else has no picture on this client at all: the rules say
 * a graveyard is public and send its cards, the screen opens, and every one of them is an
 * empty recess under a count that says there is something there.
 * <p>So this pushes rather than widening what a client may ask for. The printings sent are
 * read out of the view that was just sent to that same player, so a viewer is told about
 * exactly the cards the visibility rules decided they could see and never about one more.
 * Making the request channel general enough to cover a table would have been the other way
 * round: a client asking about any card it liked, aimed at somebody else's Scryfall quota.
 * <p>Looked up through the card pipeline rather than on the server thread, because this runs
 * every time anybody moves a card and a printing missing from the index is a file read - a
 * board update that reads a hundred files is a board update that stutters. The lookup is
 * cache-first and will reach Scryfall for a printing this server has genuinely never seen,
 * which is why each one is asked for only once per client per session: a card nobody can
 * find must not become a request on every tap for the rest of the game.
 */
public final class CardArtPush {

    /**
     * What each player has already been sent, so a board update sends only what is new.
     * <p>Every action at a table redraws every board, and a Commander game has a few dozen
     * cards in view - sending all of their pictures on every tap would be most of the traffic
     * at the table for no gain, since a printing's picture never changes.
     */
    private static final Map<UUID, Set<UUID>> ALREADY_SENT = new ConcurrentHashMap<>();

    /**
     * Per player, the printings a lookup is out for right now.
     * <p>Kept apart from what has been sent, because the two answer different questions.
     * Marking a printing sent before the lookup came back meant a lookup that failed - the
     * service still starting, a read that threw, a network that was not there yet - was never
     * tried again for the rest of that session, and the cards it was for stayed nameless.
     */
    private static final Map<UUID, Set<UUID>> BEING_LOOKED_UP = new ConcurrentHashMap<>();

    /**
     * When a player's list is thrown away and started again.
     * <p>Bounded by the distinct printings at one table, so in practice it never gets here.
     * The cap is for the session that goes on for a week: forgetting costs one repeat send.
     */
    private static final int MOST_WORTH_REMEMBERING = 2000;

    /**
     * How long a printing the service could not name stays written off.
     * <p>Long enough that a board nobody can name does not become a lookup per tap, short
     * enough that a set finishing its download while somebody is sitting at a table fixes
     * itself without a relog. A miss is a fact about this moment, not about the printing.
     */
    private static final java.time.Duration MISS_GOES_STALE = java.time.Duration.ofMinutes(5);

    /**
     * Per player, the printings the service could not answer for, and when.
     * <p>Separate from what was sent, because they are different facts. "You have been told
     * about this card" is permanent for the life of a connection; "this card could not be
     * looked up just now" is not, and treating the second as the first is how a card that
     * arrived in the cache a minute later stayed a grey rectangle until the player relogged.
     */
    private static final Map<UUID, Map<UUID, Long>> MISSED = new ConcurrentHashMap<>();

    private CardArtPush() {
    }

    /** Forgets a player, so a client that reconnects is told about the table again. */
    public static void forget(UUID player) {
        ALREADY_SENT.remove(player);
        BEING_LOOKED_UP.remove(player);
        MISSED.remove(player);
    }

    /** Forgets everybody, for a server that is stopping. */
    public static void clear() {
        ALREADY_SENT.clear();
        BEING_LOOKED_UP.clear();
        MISSED.clear();
    }

    /** This player's written-off printings, made if this is the first. */
    private static Map<UUID, Long> missesFor(ServerPlayer player) {
        return MISSED.computeIfAbsent(player.getUUID(), who -> new ConcurrentHashMap<>());
    }

    /** Whether this printing was written off recently enough not to ask again yet. */
    private static boolean recentlyMissed(Map<UUID, Long> misses, UUID printing, long now) {
        Long when = misses.get(printing);
        if (when == null) {
            return false;
        }
        if (now - when > MISS_GOES_STALE.toMillis()) {
            misses.remove(printing);
            return false;
        }
        return true;
    }

    /** Keeps a written-off list from growing without end on a session that runs for a week. */
    private static void forgetTheOldest(Map<UUID, Long> misses, int keep) {
        if (misses.size() <= keep) {
            return;
        }
        misses.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(misses.size() - keep)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(misses::remove);
    }

    /**
     * What this player has been told about, forgetting anybody who has gone.
     * <p>A client that reconnects with an empty cache is told about the table again. A
     * disconnect has no hook here and adding one per loader would be a new seam for a sweep
     * this cheap: it is one lookup per player on the server, on an update that has just
     * encoded a whole board.
     */
    private static Set<UUID> alreadySentTo(ServerPlayer player) {
        ALREADY_SENT.keySet().removeIf(
                who -> player.server.getPlayerList().getPlayer(who) == null);
        Set<UUID> sent = ALREADY_SENT.computeIfAbsent(
                player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        if (sent.size() > MOST_WORTH_REMEMBERING) {
            sent.clear();
        }
        return sent;
    }

    /**
     * Sends whatever of this view's cards this player has not been told about yet.
     *
     * @param view the view already sent to this player, which is what bounds what is sent
     */
    public static void sendFor(ServerPlayer player, GameView view) {
        if (view == null) {
            return;
        }
        // Decided in the pure layer, where the property that matters is stated and checked:
        // every printing named is one this view already revealed.
        send(player, ArtToSend.wanted(view, alreadySentTo(player)));
    }

    /**
     * Sends the pictures for these printings, if this client has not been told about them.
     *
     * @param wanted printings this viewer has already been shown by name. The caller has
     *               made the safety argument; this only avoids saying it twice.
     */
    public static void send(ServerPlayer player, Set<UUID> wanted) {
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null || wanted == null || wanted.isEmpty()) {
            return;
        }
        Set<UUID> sent = alreadySentTo(player);
        Set<UUID> asking = BEING_LOOKED_UP.computeIfAbsent(
                player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        Map<UUID, Long> misses = missesFor(player);
        long now = System.currentTimeMillis();
        wanted = new LinkedHashSet<>(wanted);
        wanted.removeAll(sent);
        wanted.removeAll(asking);
        // And whatever could not be looked up a moment ago, which is asked again once that
        // has gone stale rather than never or on every tap.
        wanted.removeIf(printing -> recentlyMissed(misses, printing, now));
        if (wanted.isEmpty()) {
            return;
        }
        // Marked as being asked about, not as sent. A printing this server has never heard of
        // must not be asked for again on every action anybody takes at the table - a trickle
        // of lookups aimed at somebody else's Scryfall quota, one per tap - so the answer
        // decides: what comes back is remembered as sent for good, what does not is written
        // off for a few minutes, and a lookup that failed outright is forgotten at once so
        // the next board can try it again.
        asking.addAll(wanted);
        Set<UUID> outstanding = Set.copyOf(wanted);

        // Off the server thread. A printing that is not in the index yet is a file read, and
        // a board update that reads a hundred files is a board update that stutters the
        // server - so this goes through the card pipeline like every other lookup, and comes
        // back to the server thread only to send.
        service.findAll(List.copyOf(wanted)).whenComplete(
                ServerRun.onServerThread(player, (cards, failure) -> {
                    asking.removeAll(outstanding);
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null || cards == null) {
                        // Nothing was learned, so nothing is remembered: the next board this
                        // player is sent asks again.
                        return;
                    }
                    // Only what actually came back is remembered as sent. It used to be
                    // everything asked about, which reads as the same thing and is not: an
                    // empty or partial answer - the service still warming, a set half read,
                    // one printing missing out of a hundred - marked cards as delivered that
                    // this client had never been told a thing about, and they stayed nameless
                    // for the rest of the connection.
                    List<CardSummary> summaries = new ArrayList<>(cards.size());
                    for (CardMetadata card : cards) {
                        summaries.add(CardSummary.of(card));
                        sent.add(card.scryfallId());
                    }
                    // What did not come back is not forgotten either, or every board update
                    // would ask for it again - a trickle of lookups aimed at somebody else's
                    // Scryfall quota, one per tap. It is written down as missing, with the
                    // time, and asked again once that has gone stale. See MISSED.
                    long answeredAt = System.currentTimeMillis();
                    for (UUID printing : outstanding) {
                        if (!sent.contains(printing)) {
                            misses.put(printing, answeredAt);
                        }
                    }
                    forgetTheOldest(misses, MOST_WORTH_REMEMBERING);
                    if (summaries.isEmpty()) {
                        return;
                    }
                    // Split rather than sent whole. Every other sender of these is bounded by
                    // one deck; a table is several at once, and all of them can be public at
                    // the end of a long game. A payload the game refuses to write disconnects
                    // the player it was for.
                    for (CardMetadataPayload packet : CardMetadataPayload.inPackets(summaries)) {
                        Sending.to(player, packet);
                    }
                }));
    }
}
