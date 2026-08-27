package dev.gathering.server;

import dev.gathering.network.Sending;
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

    /** Vanilla's own "runs the server" level, the same one /op grants. */
    private static final int OPERATOR_LEVEL = 2;

    private static final java.util.Map<UUID, Long> lastImportNanos = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<UUID> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private DecklistImport() {
    }

    /**
     * Why this player may not import, or null if they may.
     *
     * <p>Public so the command that opens the import screen can ask before opening it: being
     * told no after typing a decklist out is a worse answer than being told no instead of
     * being handed the box to type it into.
     */
    public static String whyNot(ServerPlayer player) {
        return whyNot(player.hasPermissions(OPERATOR_LEVEL));
    }

    /**
     * Why this player may not build a deck out of a collection, or null.
     *
     * <p>Not the import rule. Sleeving a deck out of cards you already own is what collection
     * mode is for, and a server running collection without import - which the brief calls a
     * collecting game that still uses the table - would otherwise be a server where the whole
     * feature is switched off. What the list is checked against is the box, and that check is
     * the collection's own: own the cards, and be standing in front of them.
     */
    public static String whyNotFromCollection() {
        return dev.gathering.service.ServerSettings.get().modes().collectionEnabled()
                ? null
                : "Collecting is turned off on this server.";
    }

    /** The same answer for somebody described only by whether they run the server. */
    public static String whyNot(boolean isOperator) {
        var settings = dev.gathering.service.ServerSettings.get();
        if (!settings.modes().importEnabled()) {
            return "Importing decklists is turned off on this server.";
        }
        if (!settings.mayImport(isOperator)) {
            return "Only server operators can import decklists here.";
        }
        return null;
    }

    /**
     * @param player   the importer; the deck is bound to them and lands in their inventory
     * @param service  the card pipeline, which owns the off-thread executor
     * @param decklist the pasted text, exactly as typed
     */
    public static void importFor(ServerPlayer player, CardDataService service, String decklist) {
        importFor(player, service, decklist, "", "");
    }

    /**
     * @param deckName    what the player called it, or blank to take the list's own name
     * @param description the player's note, shown under the name on the item
     */
    public static void importFor(
            ServerPlayer player, CardDataService service, String decklist, String deckName,
            String description) {
        importFor(player, service, decklist, deckName, description, null);
    }

    /**
     * @param from the collection to build the deck out of, or null to conjure it out of
     *             nothing. Naming one is the same request with the cards having to come from
     *             somewhere: everything up to the resolved list is identical, and only the
     *             last step differs.
     */
    public static void importFor(
            ServerPlayer player, CardDataService service, String decklist, String deckName,
            String description, net.minecraft.core.BlockPos from) {
        UUID id = player.getUUID();

        // The server's own answer, not the screen's. A client that never saw the screen - or
        // one written to skip it - arrives here, and this is the only place that decides.
        String refusal = from == null ? whyNot(player) : whyNotFromCollection();
        if (refusal != null) {
            send(player, new ImportResultPayload("", 0, List.of(refusal)));
            return;
        }

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
        // Forgotten wholesale past a bound, the way CollectionView remembers its takes: one
        // entry per player who ever imported, kept for the life of the JVM, is a map that
        // only grows. Forgetting costs one free re-import, which the cooldown can afford.
        if (lastImportNanos.size() > 512) {
            lastImportNanos.clear();
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
                        if (from == null) {
                            deliver(player, deck, deckName, description);
                        } else {
                            CollectionDecks.build(player, from, deck, deckName, description);
                        }
                    });
                });
    }

    private static void deliver(ServerPlayer player, ResolvedDeck deck, String deckName, String description) {
        DeckComponent component = toComponent(deck, player.getUUID(), deckName, description);
        ItemStack stack = DeckItem.of(component);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        // Split, because a deck may be a cube of a thousand cards and a summary carries
        // oracle text and two image links per face. One packet of that is a packet the game
        // refuses to write, which disconnects whoever was importing.
        for (CardMetadataPayload packet : CardMetadataPayload.inPackets(summariesFor(deck))) {
            send(player, packet);
        }
        send(player, new ImportResultPayload(component.name(), component.totalCards(), problemsOf(deck)));

        player.sendSystemMessage(Component.translatable(
                "message.gathering.import_complete", component.name(), component.totalCards()));
    }

    /**
     * Flattens quantities into individual cards: a decklist line saying "4 Lightning Bolt"
     * becomes four cards, because four copies is four objects on a table.
     */
    public static DeckComponent toComponent(ResolvedDeck deck, UUID owner) {
        return toComponent(deck, owner, "", "");
    }

    public static DeckComponent toComponent(
            ResolvedDeck deck, UUID owner, String deckName, String description) {
        // What the player typed wins; a list that named itself is the fallback.
        String chosen = deckName == null || deckName.isBlank()
                ? deck.deckName().orElse("Imported Deck")
                : deckName.strip();
        return new DeckComponent(
                chosen,
                description == null ? "" : description.strip(),
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
        Sending.to(player, payload);
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
