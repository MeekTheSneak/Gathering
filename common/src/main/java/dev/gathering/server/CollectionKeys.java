package dev.gathering.server;

import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.network.CollectionKeyPayload;
import dev.gathering.network.CollectionKeysPayload;
import dev.gathering.network.CollectionLockPayload;
import dev.gathering.network.CollectionOwnerPayload;
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
            // Whether they are on the looking list, not whether they may look: while the collection is
            // open everybody may, and a row that showed that would light up for people who have not
            // been let in and would not change when the owner pressed it.
            keys.add(new CollectionKeysPayload.Key(nameOf(player, letIn),
                    rights.mayLook().contains(letIn), rights.mayTake(letIn), rights.mayAdd(letIn)));
        }
        CollectionRights.Everyone everyone = rights.everyone();
        Sending.to(player, new CollectionKeysPayload(where,
                new CollectionKeysPayload.Key("", everyone.looks(), everyone.takes(), everyone.adds()),
                keys));
    }

    /** Says what anybody at all may do with a collection: look in it, take from it, add to it. */
    public static void lock(ServerPlayer player, CollectionLockPayload payload) {
        CollectionBlockEntity collection = owned(player, payload.where());
        if (collection == null) {
            return;
        }
        collection.setRights(collection.rights().allowingEveryone(
                new CollectionRights.Everyone(payload.look(), payload.take(), payload.add())));
        Notices.tell(player, Component.translatable(collection.rights().open()
                ? "message.gathering.collection_opened_to_all"
                : "message.gathering.collection_closed_to_all"));
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
            Notices.tell(player,
                    Component.translatable("message.gathering.collection_no_such_player", name));
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
            Notices.tell(player,
                    Component.translatable("message.gathering.collection_owner_already"));
            return;
        }
        CollectionRights rights = collection.rights();
        rights = look ? rights.allowingLook(who) : rights.refusingLook(who);
        rights = take ? rights.allowingTake(who) : rights.refusingTake(who);
        rights = add ? rights.allowingAdd(who) : rights.refusingAdd(who);
        collection.setRights(rights);
        Notices.tell(player, Component.translatable(look || take || add
                ? "message.gathering.collection_let_in"
                : "message.gathering.collection_shut_out", nameOf(player, who)));
        show(player, where);
    }

    /**
     * Hands a collection to somebody else, by name.
     * <p>The one thing a collection could not do: whoever put it down owned it for ever, and since the
     * owner travels in the item, a cabinet whose owner had stopped playing was locked to everybody with
     * no way back. The lock on looking made that worse rather than better, so this is the way out.
     * <p>The new owner comes off every list on the way in - their rights are no longer a list entry -
     * and the old owner keeps nothing. Handing over is handing over.
     */
    public static void handOver(ServerPlayer player, CollectionOwnerPayload payload) {
        CollectionBlockEntity collection = owned(player, payload.where());
        if (collection == null) {
            return;
        }
        String named = payload.name().trim();
        UUID who = idOf(player, named).orElse(null);
        if (who == null) {
            Notices.tell(player, Component.translatable(
                    "message.gathering.collection_no_such_player", named));
            return;
        }
        // Asked twice, because it cannot be undone and the collection is a player's cards. Every
        // other irreversible thing in this mod is confirmed; this was one press on a typed name, and
        // a transposition that happens to be somebody real gives them everything in the cabinet.
        // The second press is the confirmation - it names who they are about to hand it to, which
        // the first press could not, because until the name resolves there is nobody to name.
        if (!player.getUUID().equals(askedBefore.get(payload.where()))
                || !who.equals(askedAbout.get(payload.where()))) {
            askedBefore.put(payload.where().immutable(), player.getUUID());
            askedAbout.put(payload.where().immutable(), who);
            Notices.tell(player, Component.translatable(
                    "message.gathering.collection_hand_over_sure", nameOf(player, who)));
            return;
        }
        askedBefore.remove(payload.where());
        askedAbout.remove(payload.where());
        handOverTo(player, payload.where(), who);
    }

    /** Who last asked to hand each collection over, and to whom, so the second ask is the answer. */
    private static final java.util.Map<BlockPos, UUID> askedBefore = new java.util.HashMap<>();
    private static final java.util.Map<BlockPos, UUID> askedAbout = new java.util.HashMap<>();

    /** Forgets a half-asked hand-over, for a player who has gone or a server that is stopping. */
    public static void forget(UUID player) {
        askedBefore.entrySet().removeIf(asked -> asked.getValue().equals(player));
        askedAbout.keySet().retainAll(askedBefore.keySet());
    }

    /** For a server that is stopping. */
    public static void clear() {
        askedBefore.clear();
        askedAbout.clear();
    }

    /** The same, for somebody already known by id. */
    public static void handOverTo(ServerPlayer player, BlockPos where, UUID who) {
        CollectionBlockEntity collection = owned(player, where);
        if (collection == null || who == null) {
            return;
        }
        if (collection.rights().isOwner(who)) {
            Notices.tell(player,
                    Component.translatable("message.gathering.collection_owner_already"));
            return;
        }
        collection.setRights(collection.rights().ownedNowBy(who));
        Notices.tell(player, Component.translatable(
                "message.gathering.collection_handed_over", nameOf(player, who)));
        // And they are not its owner any more, so there is nothing left here to show them.
        Sending.to(player, new CollectionKeysPayload(where,
                new CollectionKeysPayload.Key("", false, false, false), java.util.List.of()));
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
            Notices.tell(player,
                    Component.translatable("message.gathering.collection_not_yours"));
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
