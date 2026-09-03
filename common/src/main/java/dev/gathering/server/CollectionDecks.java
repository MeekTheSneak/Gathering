package dev.gathering.server;

import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.collection.CardTally;
import dev.gathering.core.collection.DeckFromCollection;
import dev.gathering.core.deck.ResolvedCard;
import dev.gathering.core.deck.ResolvedDeck;
import dev.gathering.core.decklist.DeckSection;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.ImportResultPayload;
import dev.gathering.network.Sending;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Building a deck out of a collection, from a list.
 * <p>Sleeving one card at a time is how a collection works and it is not how a deck gets
 * built: a hundred-card Commander list is a hundred separate clicks, which is long enough
 * that nobody does it twice. So the list goes in whole and the deck comes out whole, with
 * the cards taken out of the box exactly the way taking them one at a time would.
 * <p>What to take is {@link DeckFromCollection}'s and is decided there, where it is checked:
 * whichever printing is in the box rather than the one the list named, the plain copy before
 * the foil, and nothing free taken at all. What happens here is going and getting the names
 * that rule needs, doing what it says, and saying honestly what the box was short of.
 * <p><strong>Short is not a refusal.</strong> A list the collection cannot fill builds the
 * part it can and names what is missing, because that is what somebody sitting in front of
 * their binder actually wants - the ninety cards they have, and a list of the ten to go and
 * find. A deck of ninety is still a deck.
 */
public final class CollectionDecks {

    /**
     * How many unnamed printings are worth going and asking about before building.
     * <p>A collection's cards nearly always went through this server on their way in, so the
     * cache already knows them. This is for the collection that arrived in a block somebody
     * carried from another world: worth one round of asking, not worth a hundred and forty
     * requests to somebody else's host while a player waits at a chest.
     */
    public static final int MOST_TO_LOOK_UP = 512;

    private CollectionDecks() {
    }

    /**
     * Builds what this collection can of this list, and hands it over.
     * <p>Called once the list has been resolved, on the server thread. The names of anything
     * in the box the cache has never heard of are fetched first, off the game thread, because
     * a card nobody can name is a card no line can ask for - and coming up short on a card
     * somebody definitely owns is the one failure here that would look like a bug.
     */
    public static void build(ServerPlayer player, BlockPos where, ResolvedDeck list,
            String deckName, String description) {
        CollectionBlockEntity collection = CollectionView.at(player, where);
        if (collection == null) {
            // Walked away, or a position nobody is standing at. Reading a collection is
            // public; being in front of one is not.
            send(player, "message.gathering.collection_gone");
            return;
        }
        if (!collection.rights().mayTake(player.getUUID())) {
            send(player, "message.gathering.collection_may_not_take");
            return;
        }

        CardDataService service = CardDataService.active().orElse(null);
        Map<UUID, String> named = namesIn(list);
        List<UUID> unnamed = unnamed(collection.cards(), named, service);
        if (unnamed.isEmpty() || service == null) {
            // Nothing to go and ask about, which is the ordinary case: a deck poured back
            // into the box and rebuilt from the same list needs no lookup at all.
            finish(player, where, list, named, deckName, description);
            return;
        }
        service.findAll(unnamed).whenComplete((found, failure) -> player.server.execute(() -> {
            if (player.hasDisconnected()) {
                return;
            }
            // A lookup that failed is not a reason to refuse: what the cache already knew is
            // still true, and the answer comes back short rather than not at all.
            finish(player, where, list, named, deckName, description);
        }));
    }

    // ------------------------------------------------------------------ bits

    /** Server thread. The collection is looked up again because the fetch took a moment. */
    private static void finish(ServerPlayer player, BlockPos where, ResolvedDeck list,
            Map<UUID, String> named, String deckName, String description) {
        CollectionBlockEntity collection = CollectionView.at(player, where);
        if (collection == null || !collection.rights().mayTake(player.getUUID())) {
            send(player, "message.gathering.collection_gone");
            return;
        }
        CardDataService service = CardDataService.active().orElse(null);

        List<DeckFromCollection.Wanted> wanted = new ArrayList<>();
        // A printing per name, for the lines that are given away rather than taken: the list
        // already resolved a real Forest, and that is the one to conjure.
        Map<String, CardIdentity> free = new LinkedHashMap<>();
        for (ResolvedCard card : list.cards()) {
            boolean given = card.metadata() != null && card.metadata().isBasicLand();
            wanted.add(new DeckFromCollection.Wanted(
                    card.name(), card.quantity(), card.section(), given));
            if (given) {
                free.putIfAbsent(key(card.name()), card.identity());
            }
        }

        CardTally holding = collection.cards();
        DeckFromCollection.Building built = DeckFromCollection.from(
                wanted, holding, card -> nameOf(named, service, card));
        if (built.size() == 0) {
            send(player, "message.gathering.collection_deck_nothing");
            return;
        }

        Assembled assembled = assemble(
                collection, built, free, list, player.getUUID(), deckName, description);
        DeckComponent deck = assembled.deck()
                .colored(dev.gathering.core.card.DeckColors.pick(player.level().getRandom().nextLong()));
        ItemStack stack = DeckItem.of(deck);
        dev.gathering.server.Handing.give(player, stack);

        for (CardMetadataPayload packet
                : CardMetadataPayload.inPackets(DecklistImport.summariesFor(list))) {
            Sending.to(player, packet);
        }
        int shortBy = built.shortBy() + assembled.leftBehind();
        Sending.to(player, new ImportResultPayload(
                deck.name(), deck.totalCards(), shortOf(built, assembled.leftBehind())));
        player.sendSystemMessage(shortBy == 0
                ? Component.translatable("message.gathering.collection_deck_built",
                        deck.name(), deck.totalCards())
                : Component.translatable("message.gathering.collection_deck_part",
                        deck.name(), deck.totalCards(), shortBy));
    }

    /** A built deck, and how much of the list would not fit in one. */
    private record Assembled(DeckComponent deck, int leftBehind) {
    }

    /**
     * The lines, turned into the three sections a deck item has, taking as it goes.
     * <p>Card by card, and a card only goes into the deck once it has actually come out of
     * the box. Allocating first and taking afterwards would be two answers to the same
     * question, and the moment they disagreed - a collection that changed under the fetch,
     * a bug in either half - the difference would be cards that exist twice. Sleeving is
     * moving, so this moves them.
     */
    private static Assembled assemble(CollectionBlockEntity collection,
            DeckFromCollection.Building built, Map<String, CardIdentity> free,
            ResolvedDeck list, UUID owner, String deckName, String description) {
        List<CardComponent> mainboard = new ArrayList<>();
        List<CardComponent> commanders = new ArrayList<>();
        List<CardComponent> sideboard = new ArrayList<>();
        int placed = 0;
        int leftBehind = 0;
        for (DeckFromCollection.Line line : built.lines()) {
            List<CardComponent> into = switch (line.section()) {
                case COMMANDER -> commanders;
                case SIDEBOARD -> sideboard;
                default -> mainboard;
            };
            for (CardIdentity card : line.cards()) {
                // A deck holds a thousand cards and no more, and past that it cannot be sent
                // to the client that asked for it. Stopping here leaves the rest in the box
                // and says so; taking them first and failing to hand them over would be
                // somebody's cards gone for a list that was too long.
                if (placed >= DeckComponent.MAX_CARDS) {
                    leftBehind++;
                    continue;
                }
                if (collection.take(card, 1) == 1) {
                    into.add(CardComponent.of(card));
                    placed++;
                }
            }
            CardIdentity given = free.get(key(line.name()));
            if (given != null) {
                CardComponent one = CardComponent.of(given);
                for (int copy = 0; copy < line.free(); copy++) {
                    if (placed >= DeckComponent.MAX_CARDS) {
                        leftBehind++;
                        continue;
                    }
                    into.add(one);
                    placed++;
                }
            }
        }
        String chosen = deckName == null || deckName.isBlank()
                ? list.deckName().orElse("Collection Deck")
                : deckName.strip();
        return new Assembled(
                new DeckComponent(chosen, description == null ? "" : description.strip(),
                        Optional.of(owner), mainboard, commanders, sideboard),
                leftBehind);
    }

    /** What the box was short of, in the words the import screen already shows. */
    private static List<String> shortOf(DeckFromCollection.Building built, int leftBehind) {
        List<String> said = new ArrayList<>();
        for (DeckFromCollection.Missing gap : built.missing()) {
            said.add(gap.howMany() + " " + gap.name()
                    + (gap.section() == DeckSection.SIDEBOARD ? " (sideboard)" : "")
                    + " - not in this collection");
        }
        if (leftBehind > 0) {
            said.add(leftBehind + " more would not fit: a deck holds "
                    + DeckComponent.MAX_CARDS + " cards, and they are still in the collection.");
        }
        return said;
    }

    /**
     * The names the list itself already knows.
     * <p>A deck that was imported and later poured back into the box is the ordinary way cards
     * get into one, so the printing a line names is very often the exact one sitting in the
     * collection. Asking a cache about a card whose metadata is already in hand would be a
     * lookup that can miss - and a miss here is a card somebody owns coming back short.
     */
    private static Map<UUID, String> namesIn(ResolvedDeck list) {
        Map<UUID, String> named = new LinkedHashMap<>();
        for (ResolvedCard card : list.cards()) {
            card.identity().printing().ifPresent(printing -> named.put(printing, card.name()));
        }
        return named;
    }

    /** The printings in the box that neither the list nor the cache can name, bounded. */
    private static List<UUID> unnamed(
            CardTally cards, Map<UUID, String> named, CardDataService service) {
        Set<UUID> asking = new LinkedHashSet<>();
        for (CardIdentity card : cards.cards()) {
            if (asking.size() >= MOST_TO_LOOK_UP) {
                break;
            }
            UUID printing = card.printing().orElse(null);
            if (printing != null && !named.containsKey(printing)
                    && CollectionView.known(service, card) == null) {
                asking.add(printing);
            }
        }
        return List.copyOf(asking);
    }

    /** What a card in the box is called: the list's own answer first, then the cache. */
    private static String nameOf(
            Map<UUID, String> named, CardDataService service, CardIdentity card) {
        String fromList = card.printing().map(named::get).orElse(null);
        if (fromList != null) {
            return fromList;
        }
        CardMetadata known = CollectionView.known(service, card);
        return known == null ? null : known.name();
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void send(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.translatable(message));
    }
}
