package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.collection.CardTally;
import dev.gathering.core.collection.CollectionSearch;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.CollectionPagePayload;
import dev.gathering.network.BuildDeckPayload;
import dev.gathering.network.CollectionQuery;
import dev.gathering.network.OpenCollectionPayload;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Reading and taking from a collection, on the server.
 *
 * <p>The searching happens here rather than on the client, because here is where the card
 * details are. A client that had to search a collection itself would need every name in it,
 * and a collection is meant to run to ten thousand cards; what crosses the wire instead is a
 * question and one page of answer.
 *
 * <p>Every one of these is a discrete thing somebody did - opened a block, pressed a sort
 * button, took a card - so the work happens on the server thread where the collection lives.
 * Searching one is a pass over what is in it and a sort, off a cache that is already in
 * memory; it is not per-tick and it is not per-keystroke, because the screen asks when a
 * search is finished rather than while it is being typed.
 *
 * <p>Every payload names a position, and a position is any position. So each of these starts
 * by finding out whether there is a collection there and whether this player is standing at
 * it: reading a collection is public, but being in front of it is not, and without that check
 * this would be a way to read every collection on the server from anywhere on it.
 */
public final class CollectionView {

    /** How far from a collection somebody may be and still be at it. */
    private static final double WITHIN = 8.0;

    /**
     * At most one search per player per tick.
     *
     * <p>A search is a pass over everything in the collection and a sort of what matched, on
     * the server thread, asked for by a packet a client sends whenever it likes. Ten thousand
     * cards is a few milliseconds; ten thousand cards a thousand times a second is a server
     * nobody can play on.
     *
     * <p>One tick rather than a comfortable-looking number on purpose. Anything slower would
     * drop the refresh after a card is taken when somebody is clicking quickly, which is a
     * screen going stale to defend against something nobody doing that is doing. Two searches
     * inside one tick cannot come from a person at all.
     */
    private static final int TICKS_BETWEEN_SEARCHES = 1;

    /**
     * When each player last searched. Server thread only.
     *
     * <p>Emptied rather than pruned when it grows past a server's worth of players: the
     * entries are a UUID and an int, and forgetting them all costs one player one extra
     * search.
     */
    private static final int MOST_REMEMBERED = 512;
    private static final Map<UUID, Integer> LAST_SEARCH = new java.util.HashMap<>();

    private CollectionView() {
    }

    /**
     * Opens a collection for somebody.
     *
     * <p>No cards with it. The screen asks for its first page itself, once it knows how tall
     * it is - a page sent before then would be sized for a window nobody has measured, and
     * would be thrown away by the one the screen asks for a moment later.
     */
    public static void open(ServerPlayer player, BlockPos where, CollectionBlockEntity collection) {
        UUID who = player.getUUID();
        Sending.to(player, new OpenCollectionPayload(
                where,
                collection.label(),
                collection.cards().total(),
                collection.cards().distinct(),
                collection.rights().mayTake(who),
                collection.rights().mayAdd(who)));
    }

    /** Answers one search with one page. */
    public static void search(ServerPlayer player, BlockPos where, CollectionQuery query,
            boolean descending, int page, int rowsThatFit) {
        CollectionBlockEntity collection = at(player, where);
        if (collection == null || tooSoon(player)) {
            return;
        }
        List<CollectionSearch.Row> rows = rowsOf(collection.cards());
        List<CollectionSearch.Row> found = CollectionSearch.run(rows, query.asSearch(descending));

        // As many as the window asking has room for, and never more than a page holds. A
        // page larger than the box is rows nobody can see and rows somebody can click on
        // without seeing.
        int perPage = Math.clamp(rowsThatFit, 1, CollectionPagePayload.ROWS_PER_PAGE);
        int pages = Math.max(1, (found.size() + perPage - 1) / perPage);
        int showing = Math.clamp(page, 0, pages - 1);
        int from = showing * perPage;
        int to = Math.min(found.size(), from + perPage);

        List<CollectionPagePayload.Row> sending = new ArrayList<>();
        List<UUID> unnamed = new ArrayList<>();
        for (CollectionSearch.Row row : found.subList(from, to)) {
            sending.add(new CollectionPagePayload.Row(
                    CardComponent.of(row.card()),
                    row.count(),
                    Optional.ofNullable(row.about())
                            .map(dev.gathering.network.CardSummary::of)));
            if (row.about() == null) {
                row.card().printing().ifPresent(unnamed::add);
            }
        }
        Sending.to(player, new CollectionPagePayload(
                where, showing, pages,
                new CollectionPagePayload.Counts(
                        collection.cards().total(), collection.cards().distinct(), found.size()),
                sending));

        // Whatever this page could not name is looked up now, so a second look at the same
        // page has it. Only this page: a collection of ten thousand cards nobody has ever
        // fetched is not worth ten thousand requests, and the cards a player actually looks
        // at are the ones worth having.
        fetchLater(unnamed);
    }

    /**
     * Takes cards out.
     *
     * <p>Into the deck in hand where there is one, and into the inventory otherwise. That is
     * what sleeving is: you do not carry forty loose cards from the binder to the table, you
     * put them in the deck as you pick them. Holding a deck is the whole of the gesture -
     * there is no mode to switch into and nothing to press first.
     *
     * <p>Takes and says nothing back. The screen asks for a fresh page itself, because the
     * screen is where the search somebody is looking at actually lives: a page pushed from
     * here would have to guess at it, and guessing wrong means every card taken throws the
     * player back to the top of an unfiltered list.
     */
    public static int take(ServerPlayer player, BlockPos where, CardComponent card, int howMany) {
        CollectionBlockEntity collection = at(player, where);
        if (collection == null || card == null) {
            return 0;
        }
        if (!collection.rights().mayTake(player.getUUID())) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.collection_may_not_take"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        DeckComponent deck = DeckItem.deckOf(held).orElse(null);
        int took = collection.take(card.faceUp().toIdentity(), howMany);
        if (took == 0) {
            return 0;
        }
        int sleeved = deck == null ? 0 : sleeve(held, deck, card, took);
        // Whatever the deck had no room for goes in the hand rather than back in the
        // collection: it came out because somebody asked for it, and a card that silently
        // un-took itself is a click that did nothing for a reason nobody can see.
        // Ordinary copies first, and only then the ones with a history. The card somebody
        // won in an ante game stays at the bottom of the box until it is the only one left,
        // which is what a person does with a card like that - and it means a trophy cannot be
        // sleeved into a deck by a click that meant any copy.
        // The count before the take, less the copies that have a history: that many ordinary
        // ones come out first. Anything the box cannot back with a copy is dropped by take
        // itself, so this never hands out a story for a card that has already gone.
        CardIdentity identity = card.faceUp().toIdentity();
        int plain = Math.max(0,
                collection.cards().of(identity) + took - collection.storiedCount(identity));
        for (int one = sleeved; one < took; one++) {
            ItemStack stack = CardItem.of(card.faceUp());
            if (one >= plain) {
                dev.gathering.core.story.CardStory story = collection.takeStory(identity);
                if (!story.isEmpty()) {
                    stack.set(dev.gathering.registry.GatheringComponents.STORY.get(),
                            dev.gathering.item.StoryComponent.of(story));
                }
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        return took;
    }

    /**
     * Takes a whole built deck out of the box at once, and hands it over as a deck.
     *
     * <p>The commit end of the deck builder. Every card the client named is checked against
     * what the collection actually holds and taken out one at a time by the same call a single
     * click uses, so a client asking for cards that are not there gets a deck without them
     * rather than a deck the box never had - and the arithmetic that decides which physical
     * copy leaves, plain before storied, is the one that was already there.
     *
     * <p>Basics are conjured rather than taken, exactly as building from a list does: they are
     * given away everywhere else in this mod and charging for them here would be a charge for
     * something that is not for sale.
     *
     * <p>Told what it could not find, by name and count. A deck that quietly came out four
     * cards short is a deck somebody takes to a table and discovers is illegal.
     */
    public static void build(ServerPlayer player, BuildDeckPayload asked) {
        CollectionBlockEntity collection = at(player, asked.where());
        if (collection == null) {
            return;
        }
        if (!collection.rights().mayTake(player.getUUID())) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.collection_may_not_take"));
            return;
        }

        CardDataService service = CardDataService.active().orElse(null);
        List<CardComponent> got = new ArrayList<>();
        List<CardComponent> commanders = new ArrayList<>();
        int missed = 0;
        for (CardComponent wanted : asked.commander().map(List::of).orElse(List.of())) {
            if (claim(service, collection, wanted)) {
                commanders.add(wanted.faceUp());
            } else {
                missed++;
            }
        }
        for (CardComponent wanted : asked.cards()) {
            if (claim(service, collection, wanted)) {
                got.add(wanted.faceUp());
            } else {
                missed++;
            }
        }

        DeckComponent deck = new DeckComponent(
                asked.name(), asked.description(), Optional.of(player.getUUID()),
                List.copyOf(got), List.copyOf(commanders), List.of());
        ItemStack stack = DeckItem.of(deck);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.translatable(
                "message.gathering.deck_built", deck.totalCards()));
        Achievements.award(player, Achievements.FIRST_DECK);
        if (missed > 0) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.deck_built_short", missed));
        }
    }

    /**
     * Takes one copy out of the box for a deck being built, or says the box did not have it.
     *
     * <p>A basic land is never taken and always granted, which is what building from a list
     * already does. Everything else has to actually be in there: this is the check that makes
     * the payload's card list a request rather than an instruction.
     */
    private static boolean claim(
            CardDataService service, CollectionBlockEntity collection, CardComponent wanted) {
        if (wanted == null) {
            return false;
        }
        CardIdentity identity = wanted.faceUp().toIdentity();
        if (isBasic(service, identity)) {
            return true;
        }
        return collection.take(identity, 1) > 0;
    }

    /**
     * Whether this printing is a basic land, and so free.
     *
     * <p>Asked of the card cache rather than believed from the client, because "this one is
     * free" is exactly the claim a client should not be allowed to make about a card it wants
     * out of somebody's box. A printing the server has never looked up is not free: the
     * conservative answer costs a card that was probably a Forest, and the other way round
     * costs whatever a client cares to name.
     */
    private static boolean isBasic(CardDataService service, CardIdentity identity) {
        CardMetadata card = known(service, identity);
        if (card == null) {
            return false;
        }
        for (dev.gathering.core.card.BasicLand basic : dev.gathering.core.card.BasicLand.values()) {
            if (basic.printedName().equalsIgnoreCase(card.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts cards into a held deck, and says how many fitted.
     *
     * <p>Into the mainboard. Which section a card belongs in is the deck screen's question
     * and it is a better place to ask it: sleeving is gathering the cards, and sorting them
     * is what you do once they are all in front of you.
     */
    private static int sleeve(ItemStack held, DeckComponent deck, CardComponent card, int howMany) {
        DeckComponent building = deck;
        int sleeved = 0;
        for (int one = 0; one < howMany; one++) {
            DeckComponent grown =
                    building.withAdded(DeckComponent.Section.MAINBOARD, card.faceUp()).orElse(null);
            if (grown == null) {
                break;
            }
            building = grown;
            sleeved++;
        }
        if (sleeved > 0) {
            held.set(GatheringComponents.DECK.get(), building);
        }
        return sleeved;
    }

    /**
     * Pours a deck back into a collection.
     *
     * <p>Every card of it, from every section, and the deck item goes with them. Paper-true,
     * and the reason a shared collection cannot quietly back two decks at once: the cards are
     * in one place or the other, never counted in both.
     *
     * @return whether the deck was dissolved, so the caller knows whether to keep the item
     */
    public static boolean dissolve(ServerPlayer player, BlockPos where, ItemStack held) {
        CollectionBlockEntity collection = at(player, where);
        DeckComponent deck = DeckItem.deckOf(held).orElse(null);
        if (collection == null || deck == null) {
            return false;
        }
        if (!collection.rights().mayAdd(player.getUUID())) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.collection_may_not_add"));
            return true;
        }
        if (deck.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.collection_deck_empty"));
            return true;
        }
        CardTally.Builder pouring = CardTally.builder();
        for (DeckComponent.Section section : DeckComponent.Section.values()) {
            for (CardComponent card : deck.section(section)) {
                pouring.add(card.faceUp().toIdentity(), 1);
            }
        }
        CardTally poured = pouring.build();
        collection.putAll(poured);
        held.shrink(1);
        player.sendSystemMessage(Component.translatable(
                "message.gathering.collection_dissolved",
                deck.name().isBlank()
                        ? Component.translatable("item.gathering.deck")
                        : Component.literal(deck.name()),
                poured.total()));
        return true;
    }

    // ------------------------------------------------------------------ bits

    /**
     * Whether this player has searched too recently to search again.
     *
     * <p>Silently, because the only thing that can hit it is a client asking faster than a
     * person can press a button, and a screen that said "slow down" to somebody who did
     * nothing would be answering a question they never asked. A dropped search is followed
     * by another one the moment they touch anything.
     */
    private static boolean tooSoon(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = LAST_SEARCH.get(player.getUUID());
        if (last != null && now - last < TICKS_BETWEEN_SEARCHES && now >= last) {
            return true;
        }
        if (LAST_SEARCH.size() >= MOST_REMEMBERED) {
            LAST_SEARCH.clear();
        }
        LAST_SEARCH.put(player.getUUID(), now);
        return false;
    }

    /**
     * The collection this player is actually standing at, or null.
     *
     * <p>Three halves, and all of them matter. A block that is not a collection is somebody
     * pointing a payload at a wall; a position in an unloaded chunk is somebody making the
     * server go and fetch one; and a collection across the world is somebody reading a
     * stranger's binder from their own base.
     *
     * <p>Package-visible so {@link CollectionDecks} asks the same question rather than a
     * second one that looks like it. Reading a collection is public; being in front of one is
     * not, and a payload naming a position is a payload naming <em>any</em> position - so
     * every path in has to check, and there is one check.
     */
    static CollectionBlockEntity at(ServerPlayer player, BlockPos where) {
        if (where == null || !player.level().isLoaded(where)) {
            return null;
        }
        if (player.distanceToSqr(where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5)
                > WITHIN * WITHIN) {
            return null;
        }
        return player.level().getBlockEntity(where) instanceof CollectionBlockEntity collection
                ? collection
                : null;
    }

    /** The collection as rows, with whatever the cache already knows about each card. */
    private static List<CollectionSearch.Row> rowsOf(CardTally cards) {
        CardDataService service = CardDataService.active().orElse(null);
        List<CollectionSearch.Row> rows = new ArrayList<>(cards.distinct());
        for (Map.Entry<CardIdentity, Integer> entry : cards.counts().entrySet()) {
            rows.add(new CollectionSearch.Row(
                    entry.getKey(), entry.getValue(), known(service, entry.getKey())));
        }
        return rows;
    }

    /**
     * What is already known about a card, without going anywhere for it.
     *
     * <p>The cache only. A search that fetched what it did not know would be one search
     * making ten thousand requests, and the honest answer for a card nobody has looked up is
     * that nobody has looked it up.
     *
     * <p>Package-visible for the same reason {@link #at} is: one answer, asked from two
     * places.
     */
    static CardMetadata known(CardDataService service, CardIdentity card) {
        if (service == null) {
            return null;
        }
        return card.printing()
                .flatMap(printing -> service.store().find(CardQuery.byId(printing)))
                .orElse(null);
    }

    private static void fetchLater(List<UUID> printings) {
        if (printings.isEmpty()) {
            return;
        }
        CardDataService.active().ifPresent(service -> service.findAll(printings));
    }
}
