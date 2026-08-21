package dev.gathering.server;

import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.decklist.ParseProblem;
import dev.gathering.core.deck.ResolvedCard;
import dev.gathering.core.deck.ResolvedDeck;
import dev.gathering.core.deck.UnresolvedEntry;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.network.ImportResultPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a pasted decklist into a deck item in a player's hands.
 *
 * <p>All of the interesting work is in the pure core; this is the part that knows about
 * players, threads, and packets. Two rules shape it:
 *
 * <ul>
 *   <li>Resolution runs on the card pipeline's executor, never on the server thread. Giving
 *       the item runs on the server thread, never anywhere else.</li>
 *   <li>The client is told the card metadata for the deck it just imported - it holds these
 *       cards, so it is entitled to know what they are. Nothing else is sent.</li>
 * </ul>
 */
public final class DecklistImport {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /**
     * One import at a time per player, and a short breath between them.
     *
     * <p>Every import is potentially two Scryfall requests. The rate limiter keeps the
     * server polite whatever happens, but without this a client could queue unbounded work
     * behind it and starve everyone else's imports. A player who has to wait a couple of
     * seconds between decklists has lost nothing.
     */
    private static final long COOLDOWN_NANOS = java.time.Duration.ofSeconds(3).toNanos();

    private static final java.util.Map<UUID, Long> lastImportNanos = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<UUID> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private DecklistImport() {
    }

    /**
     * @param player   the importer; the deck is bound to them and lands in their inventory
     * @param service  the card pipeline, which owns the off-thread executor
     * @param decklist the pasted text, exactly as typed
     */
    public static void importFor(ServerPlayer player, CardDataService service, String decklist) {
        UUID id = player.getUUID();

        if (!inFlight.add(id)) {
            send(player, new ImportResultPayload("", 0,
                    List.of("An import is already running; wait for it to finish.")));
            return;
        }

        long now = System.nanoTime();
        Long previous = lastImportNanos.get(id);
        if (previous != null && now - previous < COOLDOWN_NANOS) {
            inFlight.remove(id);
            send(player, new ImportResultPayload("", 0,
                    List.of("Importing again so soon; give it a few seconds.")));
            return;
        }
        lastImportNanos.put(id, now);

        service.importDecklist(decklist)
                .whenComplete((deck, failure) -> {
                    // Back to the server thread before touching a player or an inventory.
                    inFlight.remove(id);
                    player.server.execute(() -> {
                        if (player.hasDisconnected()) {
                            // Nobody to hand a deck to. The cache kept everything it fetched,
                            // so re-importing after logging back in costs no requests.
                            return;
                        }
                        if (failure != null) {
                            LOGGER.warn("Decklist import failed for {}", player.getGameProfile().getName(), failure);
                            send(player, new ImportResultPayload("", 0,
                                    List.of("The import could not reach Scryfall: " + rootMessage(failure))));
                            return;
                        }
                        deliver(player, deck);
                    });
                });
    }

    private static void deliver(ServerPlayer player, ResolvedDeck deck) {
        DeckComponent component = toComponent(deck, player.getUUID());
        ItemStack stack = DeckItem.of(component);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        send(player, new CardMetadataPayload(summariesFor(deck)));
        send(player, new ImportResultPayload(component.name(), component.totalCards(), problemsOf(deck)));

        player.sendSystemMessage(Component.translatable(
                "message.gathering.import_complete", component.name(), component.totalCards()));
    }

    /**
     * Flattens quantities into individual cards: a decklist line saying "4 Lightning Bolt"
     * becomes four cards, because four copies is four objects on a table.
     */
    public static DeckComponent toComponent(ResolvedDeck deck, UUID owner) {
        return new DeckComponent(
                deck.deckName().orElse("Imported Deck"),
                java.util.Optional.of(owner),
                flatten(deck, DeckSection.MAINBOARD),
                flatten(deck, DeckSection.COMMANDER),
                flatten(deck, DeckSection.SIDEBOARD));
    }

    private static List<CardComponent> flatten(ResolvedDeck deck, DeckSection section) {
        List<CardComponent> cards = new ArrayList<>();
        for (ResolvedCard card : deck.in(section)) {
            CardComponent component = CardComponent.of(card.identity());
            for (int copy = 0; copy < card.quantity(); copy++) {
                cards.add(component);
            }
        }
        return cards;
    }

    /** One summary per distinct printing, not one per card: a deck of forty Forests is one entry. */
    public static List<CardSummary> summariesFor(ResolvedDeck deck) {
        Map<UUID, CardSummary> distinct = new LinkedHashMap<>();
        for (ResolvedCard card : deck.cards()) {
            distinct.computeIfAbsent(card.metadata().scryfallId(), id -> CardSummary.of(card.metadata()));
        }
        return List.copyOf(distinct.values());
    }

    public static List<String> problemsOf(ResolvedDeck deck) {
        List<String> problems = new ArrayList<>();
        for (ParseProblem problem : deck.problems()) {
            problems.add(problem.toString());
        }
        for (UnresolvedEntry unresolved : deck.unresolved()) {
            problems.add(unresolved.toString());
        }
        int limit = ImportResultPayload.MAX_PROBLEMS;
        if (problems.size() > limit) {
            List<String> trimmed = new ArrayList<>(problems.subList(0, limit - 1));
            trimmed.add("...and " + (problems.size() - limit + 1) + " more");
            return trimmed;
        }
        return problems;
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}
