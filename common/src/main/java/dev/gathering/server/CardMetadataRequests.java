package dev.gathering.server;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.network.Sending;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.network.RequestCardMetadataPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Answers a client asking what the cards it is holding are called.
 *
 * <p>Runs on the card pipeline's executor and answers from the cache, so a player opening a
 * deck they imported last week costs no Scryfall requests at all.
 */
public final class CardMetadataRequests {

    private CardMetadataRequests() {
    }

    /**
     * Tells somebody what these cards are, without them having asked.
     *
     * <p>For the one case where a client is shown cards it does not hold: the other half of a
     * trade table. Somebody putting a card up is showing it to the person across from them on
     * purpose, so the name and the picture go with it - otherwise the offer is a row of
     * sleeves under a count, which is not something anybody can agree to.
     *
     * <p>Only ever the cards the rules just put in front of them, never a list a client chose.
     */
    public static void pushKnown(ServerPlayer player, java.util.Collection<CardIdentity> cards) {
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null || cards == null || cards.isEmpty()) {
            return;
        }
        List<UUID> printings = cards.stream()
                .map(CardIdentity::printing)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .limit(RequestCardMetadataPayload.MAX_REQUESTED)
                .toList();
        if (printings.isEmpty()) {
            return;
        }
        send(player, service, printings);
    }

    public static void handle(ServerPlayer player, CardDataService service, RequestCardMetadataPayload request) {
        List<UUID> wanted = request.printings().stream()
                .distinct()
                .limit(RequestCardMetadataPayload.MAX_REQUESTED)
                .toList();
        if (wanted.isEmpty()) {
            return;
        }
        send(player, service, wanted);
    }

    private static void send(ServerPlayer player, CardDataService service, List<UUID> wanted) {
        service.findAll(wanted).whenComplete((cards, failure) -> player.server.execute(() -> {
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
