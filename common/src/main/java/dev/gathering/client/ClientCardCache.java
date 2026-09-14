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

    /**
     * Printings asked about that came back without a name, and why.
     * <p>Guarded by this cache's own lock rather than made concurrent: it is only ever touched
     * from the client thread today, and the lock costs nothing at a size this small.
     */
    private final dev.gathering.core.card.UnresolvedCards unresolved =
            new dev.gathering.core.card.UnresolvedCards();

    private ClientCardCache() {
    }

    public static ClientCardCache get() {
        return INSTANCE;
    }

    public void accept(Collection<CardSummary> incoming) {
        for (CardSummary summary : incoming) {
            summaries.put(summary.scryfallId(), summary);
            synchronized (unresolved) {
                unresolved.found(summary.scryfallId());
            }
        }
    }

    /** The server's answer for printings it has no name for. */
    public void acceptUnresolved(dev.gathering.network.CardsUnresolvedPayload payload) {
        acceptUnresolved(payload, System.currentTimeMillis());
    }

    /** The same, at a given moment, for a test that must not wait half an hour. */
    public void acceptUnresolved(dev.gathering.network.CardsUnresolvedPayload payload, long now) {
        synchronized (unresolved) {
            unresolved.missing(payload.missing(), now);
            unresolved.unavailable(payload.unavailable(), now);
        }
    }

    /**
     * Whether asking about this printing again would only get the same "no such card".
     * <p>What the inventory sweep checks before asking, so a card that does not exist costs one
     * lookup every half hour rather than one a minute.
     */
    public boolean alreadyAnsweredMissing(UUID printing, long now) {
        synchronized (unresolved) {
            return unresolved.alreadyAnswered(printing, now);
        }
    }

    /**
     * What a screen writes where a card's name would go when there is no name to write.
     * <p>One answer for every screen, because there are three different things it can mean
     * and each screen used to say the first one whatever was true: still being looked up; no
     * such card; or the lookup could not be made just now.
     */
    public net.minecraft.network.chat.Component unnamed(UUID printing) {
        return unnamed(printing, System.currentTimeMillis());
    }

    public net.minecraft.network.chat.Component unnamed(UUID printing, long now) {
        dev.gathering.core.card.UnresolvedCards.Reason reason;
        synchronized (unresolved) {
            reason = unresolved.reasonFor(printing, now).orElse(null);
        }
        if (reason == dev.gathering.core.card.UnresolvedCards.Reason.MISSING) {
            return net.minecraft.network.chat.Component.translatable("screen.gathering.deck.missing_card");
        }
        if (reason == dev.gathering.core.card.UnresolvedCards.Reason.UNAVAILABLE) {
            return net.minecraft.network.chat.Component.translatable("screen.gathering.deck.unavailable_card");
        }
        return net.minecraft.network.chat.Component.translatable("screen.gathering.deck.loading_card");
    }

    /** The same for a card, which may carry no printing at all - and then it is still loading. */
    public net.minecraft.network.chat.Component unnamed(CardComponent card) {
        return unnamed(card == null ? null : card.scryfallId().orElse(null));
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
        synchronized (unresolved) {
            unresolved.clear();
        }
    }

    public int size() {
        return summaries.size();
    }
}
