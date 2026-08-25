package dev.gathering.server;

import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.collection.CardTally;
import dev.gathering.core.collection.CollectionSearch;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CollectionPagePayload;
import dev.gathering.network.CollectionQuery;
import dev.gathering.network.OpenCollectionPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
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

    private CollectionView() {
    }

    /** Opens a collection for somebody, and sends them its first page. */
    public static void open(ServerPlayer player, BlockPos where, CollectionBlockEntity collection) {
        UUID who = player.getUUID();
        player.connection.send(new ClientboundCustomPayloadPacket(new OpenCollectionPayload(
                where,
                collection.label(),
                collection.cards().total(),
                collection.cards().distinct(),
                collection.rights().mayTake(who),
                collection.rights().mayAdd(who))));
        search(player, where, CollectionQuery.EVERYTHING, false, 0);
    }

    /** Answers one search with one page. */
    public static void search(ServerPlayer player, BlockPos where, CollectionQuery query,
            boolean descending, int page) {
        CollectionBlockEntity collection = at(player, where);
        if (collection == null) {
            return;
        }
        List<CollectionSearch.Row> rows = rowsOf(collection.cards());
        List<CollectionSearch.Row> found =
                CollectionSearch.run(rows, query.asSearch(descending));

        int perPage = CollectionPagePayload.ROWS_PER_PAGE;
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
        player.connection.send(new ClientboundCustomPayloadPacket(
                new CollectionPagePayload(where, showing, pages, found.size(), sending)));

        // Whatever this page could not name is looked up now, so a second look at the same
        // page has it. Only this page: a collection of ten thousand cards nobody has ever
        // fetched is not worth ten thousand requests, and the cards a player actually looks
        // at are the ones worth having.
        fetchLater(unnamed);
    }

    /** Takes cards out and puts them in a hand. */
    public static void take(ServerPlayer player, BlockPos where, CardComponent card, int howMany) {
        CollectionBlockEntity collection = at(player, where);
        if (collection == null || card == null) {
            return;
        }
        if (!collection.rights().mayTake(player.getUUID())) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.collection_may_not_take"));
            return;
        }
        int took = collection.take(card.faceUp().toIdentity(), howMany);
        if (took == 0) {
            return;
        }
        for (int one = 0; one < took; one++) {
            ItemStack stack = CardItem.of(card.faceUp());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        // The screen is showing a page that has just changed underneath it, so it gets a
        // fresh one rather than being left to guess.
        search(player, where, CollectionQuery.EVERYTHING, false, 0);
    }

    // ------------------------------------------------------------------ bits

    /**
     * The collection this player is standing at, or null.
     *
     * <p>Both halves matter. A block that is not a collection is somebody pointing a payload
     * at a wall; a collection across the world is somebody reading a stranger's binder from
     * their own base.
     */
    private static CollectionBlockEntity at(ServerPlayer player, BlockPos where) {
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
     */
    private static CardMetadata known(CardDataService service, CardIdentity card) {
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
