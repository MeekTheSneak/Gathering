package dev.gathering.server;

import dev.gathering.core.card.ArtToSend;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends a viewer the pictures for the cards they have just been shown.
 *
 * <p>A client only ever asked what a card looks like on behalf of cards in its own inventory,
 * which is the right scope for a request - it grants no access the player did not have. But it
 * means a card belonging to somebody else has no picture on this client at all: the rules say
 * a graveyard is public and send its cards, the screen opens, and every one of them is an
 * empty recess under a count that says there is something there.
 *
 * <p>So this pushes rather than widening what a client may ask for. The printings sent are
 * read out of the view that was just sent to that same player, so a viewer is told about
 * exactly the cards the visibility rules decided they could see and never about one more.
 * Making the request channel general enough to cover a table would have been the other way
 * round: a client asking about any card it liked, aimed at somebody else's Scryfall quota.
 *
 * <p>Looked up through the card pipeline rather than on the server thread, because this runs
 * every time anybody moves a card and a printing missing from the index is a file read - a
 * board update that reads a hundred files is a board update that stutters. The lookup is
 * cache-first and will reach Scryfall for a printing this server has genuinely never seen,
 * which is why each one is asked for only once per client per session: a card nobody can
 * find must not become a request on every tap for the rest of the game.
 */
public final class TableCardArt {

    /**
     * What each player has already been sent, so a board update sends only what is new.
     *
     * <p>Every action at a table redraws every board, and a Commander game has a few dozen
     * cards in view - sending all of their pictures on every tap would be most of the traffic
     * at the table for no gain, since a printing's picture never changes.
     */
    private static final Map<UUID, Set<UUID>> ALREADY_SENT = new ConcurrentHashMap<>();

    /**
     * When a player's list is thrown away and started again.
     *
     * <p>Bounded by the distinct printings at one table, so in practice it never gets here.
     * The cap is for the session that goes on for a week: forgetting costs one repeat send.
     */
    private static final int MOST_WORTH_REMEMBERING = 2000;

    private TableCardArt() {
    }

    /**
     * Sends whatever of this view's cards this player has not been told about yet.
     *
     * @param view the view already sent to this player, which is what bounds what is sent
     */
    public static void sendFor(ServerPlayer player, GameView view) {
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null || view == null) {
            return;
        }
        // Anybody who is no longer online is forgotten, so a client that reconnects with an
        // empty cache is told about the table again. A disconnect has no hook here and adding
        // one per loader would be a new seam for a sweep this cheap: it is one lookup per
        // player on the server, on an update that has just encoded a whole board.
        ALREADY_SENT.keySet().removeIf(
                who -> player.server.getPlayerList().getPlayer(who) == null);
        Set<UUID> sent = ALREADY_SENT.computeIfAbsent(
                player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        if (sent.size() > MOST_WORTH_REMEMBERING) {
            sent.clear();
        }

        // Decided in the pure layer, where the property that matters is stated and checked:
        // every printing named is one this view already revealed.
        Set<UUID> wanted = ArtToSend.wanted(view, sent);
        if (wanted.isEmpty()) {
            return;
        }

        // Marked before the lookup rather than after it. A printing this server has never
        // heard of would otherwise be asked for again on every action anybody takes at the
        // table, for the rest of the session - a trickle of lookups aimed at somebody else's
        // Scryfall quota, one per tap. Asked once; if the answer was not there yet, the
        // picture arrives when that client next connects.
        sent.addAll(wanted);

        // Off the server thread. A printing that is not in the index yet is a file read, and
        // a board update that reads a hundred files is a board update that stutters the
        // server - so this goes through the card pipeline like every other lookup, and comes
        // back to the server thread only to send.
        service.findAll(List.copyOf(wanted)).whenComplete((cards, failure) ->
                player.server.execute(() -> {
                    if (player.hasDisconnected() || failure != null || cards == null
                            || cards.isEmpty()) {
                        return;
                    }
                    List<CardSummary> summaries = new ArrayList<>(cards.size());
                    for (CardMetadata card : cards) {
                        summaries.add(CardSummary.of(card));
                    }
                    // Split rather than sent whole. Every other sender of these is bounded by
                    // one deck; a table is several at once, and all of them can be public at
                    // the end of a long game. A payload the game refuses to write disconnects
                    // the player it was for.
                    for (CardMetadataPayload packet : CardMetadataPayload.inPackets(summaries)) {
                        player.connection.send(new ClientboundCustomPayloadPacket(packet));
                    }
                }));
    }
}
