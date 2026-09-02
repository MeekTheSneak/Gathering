package dev.gathering.client;

import dev.gathering.item.CardComponent;
import dev.gathering.network.CardSummary;
import dev.gathering.service.CardNameLookup;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything this client has been told about cards.
 * <p>Emphasis on "been told". The client never resolves a card itself and never asks
 * Scryfall what a card is; it knows exactly the printings the server has sent it summaries
 * for, which is exactly the set the visibility rules entitle it to. A card it has not been
 * told about has no name here, and that is correct rather than a gap to paper over.
 * <p>Cleared on disconnect so nothing survives into the next server.
 */
public final class ClientCardCache implements CardNameLookup {

    private static final ClientCardCache INSTANCE = new ClientCardCache();

    /**
     * Concurrent as insurance, not because anything crosses a thread today.
     * <p>It used to say the opposite - written from the network thread, read from the render
     * one - and that was never true on either loader. NeoForge wraps every payload handler in
     * {@code MainThreadPayloadHandler} unless a registrar asks otherwise, and Fabric's
     * {@code ClientPlayNetworking} says in as many words that a handler "is called on the
     * render thread". Summaries arrive on the same thread that draws them.
     * <p>It stays a {@link java.util.concurrent.ConcurrentHashMap} anyway. The cost is
     * nothing at this size, and the failure it would guard against - a handler moved onto the
     * network thread by somebody chasing a frame of latency - is an intermittent,
     * unreproducible corruption on a client that happened to be drawing when a packet landed.
     * That is the worst kind of bug to trade for the fastest kind of map.
     */
    private final Map<UUID, CardSummary> summaries = new ConcurrentHashMap<>();

    private ClientCardCache() {
    }

    public static ClientCardCache get() {
        return INSTANCE;
    }

    public void accept(Collection<CardSummary> incoming) {
        for (CardSummary summary : incoming) {
            summaries.put(summary.scryfallId(), summary);
        }
    }

    public Optional<CardSummary> summary(UUID scryfallId) {
        return Optional.ofNullable(summaries.get(scryfallId));
    }

    public Optional<CardSummary> summary(CardComponent card) {
        return card.scryfallId().flatMap(this::summary);
    }

    @Override
    public Optional<String> nameOf(CardComponent card) {
        return summary(card).map(CardSummary::name);
    }

    /** Called on disconnect: what one server told us is not true of the next one. */
    public void clear() {
        summaries.clear();
    }

    public int size() {
        return summaries.size();
    }
}
