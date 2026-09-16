package dev.gathering.server;

import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.network.CollectionKeyPayload;
import dev.gathering.network.CollectionKeysPayload;
import dev.gathering.network.CollectionLockPayload;
import dev.gathering.network.Sending;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who is let into a collection, and the only way that changes.
 * <p>The rights themselves have been there since collections were - a list of who may take and a
 * list of who may add - and nothing could change either of them, so in practice every collection
 * was its owner's forever and nobody else's ever. The owner asked for the lock and the list
 * (2026-09-16): view-only, locked outright, and people let in by name.
 * <p>Every one of these is the owner's alone, checked here, on the server, against the collection
 * in front of them. A payload naming a position is a payload naming any position.
 */
public final class CollectionKeys {

    private CollectionKeys() {
    }

    /** Sends the owner the list of who is let in. Nobody else is answered at all. */
    public static void show(ServerPlayer player, BlockPos where) {
        CollectionBlockEntity collection = owned(player, where);
        if (collection == null) {
            return;
        }
        CollectionRights rights = collection.rights();
        List<CollectionKeysPayload.Key> keys = new ArrayList<>();
        for (UUID letIn : rights.everybodyLetIn()) {
            if (keys.size() >= CollectionKeysPayload.MOST_KEYS) {
                break;
            }
            keys.add(new CollectionKeysPayload.Key(nameOf(player, letIn),
                    rights.mayLook(letIn), rights.mayTake(letIn), rights.mayAdd(letIn)));
        }
        Sending.to(player, new CollectionKeysPayload(where, rights.open(), keys));
    }

    /** Opens a collection to everybody, or shuts it to everybody not let in. */
    public static void lock(ServerPlayer player, CollectionLockPayload payload) {
        CollectionBlockEntity collection = owned(player, payload.where());
        if (collection == null) {
            return;
        }
        collection.setRights(collection.rights().openedToLook(payload.open()));
        player.displayClientMessage(Component.translatable(payload.open()
                ? "message.gathering.collection_opened_to_all"
                : "message.gathering.collection_closed_to_all"), false);
        show(player, payload.where());
    }

    /**
     * Lets somebody in, or shuts them out. Every right false is off every list.
     * <p>A name the server has never heard of is refused and said so, rather than written down
     * against an id made up from it: a key cut for a player who does not exist is a key the owner
     * thinks they gave somebody.
     */
    public static void set(ServerPlayer player, CollectionKeyPayload payload) {
        CollectionBlockEntity collection = owned(player, payload.where());
        if (collection == null) {
            return;
        }
        String name = payload.name().trim();
        UUID who = idOf(player, name).orElse(null);
        if (who == null) {
            player.displayClientMessage(
                    Component.translatable("message.gathering.collection_no_such_player", name), false);
            return;
        }
        set(player, payload.where(), who, payload.look(), payload.take(), payload.add());
    }

    /**
     * The same, for somebody already known by id.
     * <p>Split from the name so the three rights can be checked without a server that remembers names:
     * a game test's players are in no profile cache and no player list, and the part worth guarding is
     * what happens once the owner has been recognized, not the lookup.
     */
    public static void set(ServerPlayer player, BlockPos where, UUID who,
            boolean look, boolean take, boolean add) {
        CollectionBlockEntity collection = owned(player, where);
        if (collection == null || who == null) {
            return;
        }
        if (collection.rights().isOwner(who)) {
            // The owner's rights are not a list entry. Saying so beats a row that will not change.
            player.displayClientMessage(
                    Component.translatable("message.gathering.collection_owner_already"), false);
            return;
        }
        CollectionRights rights = collection.rights();
        rights = look ? rights.allowingLook(who) : rights.refusingLook(who);
        rights = take ? rights.allowingTake(who) : rights.refusingTake(who);
        rights = add ? rights.allowingAdd(who) : rights.refusingAdd(who);
        collection.setRights(rights);
        player.displayClientMessage(Component.translatable(look || take || add
                ? "message.gathering.collection_let_in"
                : "message.gathering.collection_shut_out", nameOf(player, who)), false);
        show(player, where);
    }

    /**
     * The collection in front of this player, if it is theirs.
     * <p>Through {@link CollectionView#at}, which is the one place that asks whether somebody is
     * actually standing at the block a payload names - and which refuses a locked collection to
     * anybody not let in, the owner included in every case but this one, where they are the owner
     * and so are let in anyway.
     */
    private static CollectionBlockEntity owned(ServerPlayer player, BlockPos where) {
        CollectionBlockEntity collection = CollectionView.at(player, where);
        if (collection == null) {
            return null;
        }
        if (!collection.rights().isOwner(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("message.gathering.collection_not_yours"), true);
            return null;
        }
        return collection;
    }

    /** Whoever this name belongs to on this server, online or remembered. */
    private static Optional<UUID> idOf(ServerPlayer asking, String name) {
        if (name.isEmpty() || asking.getServer() == null) {
            return Optional.empty();
        }
        ServerPlayer online = asking.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getUUID());
        }
        net.minecraft.server.players.GameProfileCache cache = asking.getServer().getProfileCache();
        return cache == null ? Optional.empty() : cache.get(name).map(com.mojang.authlib.GameProfile::getId);
    }

    /** What to call a player the owner has let in, or their id where the server has forgotten them. */
    private static String nameOf(ServerPlayer asking, UUID who) {
        if (asking.getServer() != null) {
            ServerPlayer online = asking.getServer().getPlayerList().getPlayer(who);
            if (online != null) {
                return online.getGameProfile().getName();
            }
            net.minecraft.server.players.GameProfileCache cache = asking.getServer().getProfileCache();
            Optional<String> remembered = cache == null ? Optional.empty()
                    : cache.get(who).map(com.mojang.authlib.GameProfile::getName);
            if (remembered.isPresent()) {
                return remembered.get();
            }
        }
        return who.toString().substring(0, 8);
    }
}
