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
        whoIs(player, collection, payload.name().trim(), who ->
                set(player, payload.where(), who, payload.look(), payload.take(), payload.add()));
    }

    /**
     * The same, for somebody already known by id.
     * <p>Split from the name so the three rights can be checked without a server that remembers names:
     * a game test's players are in no profile cache and no player list. The lookup by name is checked
     * on its own, with a cache stood in for the one a game-test server does not have.
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
        BlockPos where = payload.where().immutable();
        int pressed = player.getServer() == null ? 0 : player.getServer().getTickCount();
        whoIs(player, collection, payload.name().trim(), who -> askThenHandOver(player, where, who, pressed));
    }

    /**
     * The hand-over, once the name has an answer.
     * <p>Asked twice, because it cannot be undone and the collection is a player's cards. Every
     * other irreversible thing in this mod is confirmed; this was one press on a typed name, and a
     * transposition that happens to be somebody real gives them everything in the cabinet. The
     * second press is the confirmation - it names who they are about to hand it to, which the first
     * press could not, because until the name resolves there is nobody to name.
     * <p>The collection is asked about again here: a name looked up off the server thread comes back
     * a moment later, and the player may have walked away or handed it to somebody else since.
     * <p>And a press only confirms if it arrived after the question was put. The question is put
     * when the name comes back, which can be seconds after the press for a name the server had to
     * ask about - so a second press made while nothing seemed to be happening confirmed a question
     * nobody had read, and the collection went to whoever the name turned out to be.
     *
     * @param pressed the server tick the press arrived on
     */
    private static void askThenHandOver(ServerPlayer player, BlockPos where, UUID who, int pressed) {
        if (owned(player, where) == null) {
            return;
        }
        Integer asked = askedWhen.get(where);
        if (!player.getUUID().equals(askedBefore.get(where)) || !who.equals(askedAbout.get(where))
                || asked == null || asked >= pressed) {
            askedBefore.put(where, player.getUUID());
            askedAbout.put(where, who);
            askedWhen.put(where, player.getServer() == null ? 0 : player.getServer().getTickCount());
            Notices.tell(player, Component.translatable(
                    "message.gathering.collection_hand_over_sure", nameOf(player, who)));
            return;
        }
        askedBefore.remove(where);
        askedAbout.remove(where);
        askedWhen.remove(where);
        handOverTo(player, where, who);
    }

    /** Who last asked to hand each collection over, to whom, and on which tick, so the next press is the answer. */
    private static final java.util.Map<BlockPos, UUID> askedBefore = new java.util.HashMap<>();
    private static final java.util.Map<BlockPos, UUID> askedAbout = new java.util.HashMap<>();
    private static final java.util.Map<BlockPos, Integer> askedWhen = new java.util.HashMap<>();

    /**
     * Forgets a half-asked hand-over, for a player who has gone or a server that is stopping.
     * <p>Not their {@link #NAME_LOOKUPS}: that is what limits how often a name goes to Mojang, and
     * one forgotten on disconnect is a full allowance again for every reconnect.
     */
    public static void forget(UUID player) {
        askedBefore.entrySet().removeIf(asked -> asked.getValue().equals(player));
        askedAbout.keySet().retainAll(askedBefore.keySet());
        askedWhen.keySet().retainAll(askedBefore.keySet());
    }

    /** For a server that is stopping. */
    public static void clear() {
        askedBefore.clear();
        askedAbout.clear();
        askedWhen.clear();
        NAME_LOOKUPS.clear();
    }

    /** Whether this collection is waiting on its owner to confirm handing it to this player. For tests. */
    static boolean waitingToHandOver(BlockPos where, UUID who) {
        return who.equals(askedAbout.get(where));
    }

    /**
     * How often one player may have a name looked up that the server does not already know.
     * <p>Even off the server thread, every one of those is a request against this server's allowance
     * at Mojang, and one that is refused or never answered holds the lookup thread for seconds. A
     * person letting friends in types a name every so often; the rows already on the list, and
     * anybody online, cost nothing from this.
     */
    static final ActionBudget NAME_LOOKUPS = new ActionBudget(0.5, 4);

    /** How many lookups may wait behind the one being made, from everybody together. */
    private static final int MOST_WAITING = 16;

    /**
     * The one thread names are looked up on.
     * <p>Its own, and only one. Vanilla's own background pool is the one its world generation and
     * lighting run on, and a name lookup that waits on the network was a thread of that taken away
     * for as long as it waited. One thread with a short queue is the most a flood of names can hold
     * up, whoever sends it: past the queue, a lookup is refused and said so.
     */
    private static final java.util.concurrent.ExecutorService NAMES = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(MOST_WAITING),
            dev.gathering.service.ServiceThreads.named("gathering-names"));

    /**
     * Who a name belongs to, answered on the server thread: at once for anybody online or already on
     * this collection's lists, and otherwise once it has been looked up off it.
     * <p>From the 2026-09-25 sweep. Vanilla's profile cache answers a name it does not hold by asking
     * Mojang there and then - an HTTPS request, a pause between pages, three tries a second apart when
     * it is refused, and a file written when it is answered - on whatever thread asked, and this asked
     * on the server thread, for every press, as often as a client cared to send one. So one owner could
     * stall every tick by typing names. Now the network is only ever asked from {@link #NAMES}, no
     * faster than {@link #NAME_LOOKUPS} allows, and what comes back is checked against the collection
     * as it is by then, by whatever {@code then} does.
     * <p>The rows on the list are answered from the list. They send the name the list showed, which is
     * a remembered name or, for somebody the server has forgotten, the start of their id - which no
     * lookup could ever find, so those rows could not be changed at all.
     */
    private static void whoIs(ServerPlayer asking, CollectionBlockEntity collection, String name,
            java.util.function.Consumer<UUID> then) {
        net.minecraft.server.MinecraftServer server = asking.getServer();
        if (name.isEmpty() || server == null) {
            noSuchPlayer(asking, name);
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            then.accept(online.getUUID());
            return;
        }
        for (UUID letIn : collection.rights().everybodyLetIn()) {
            if (nameOf(asking, letIn).equalsIgnoreCase(name)) {
                then.accept(letIn);
                return;
            }
        }
        net.minecraft.server.players.GameProfileCache cache = profiles(server);
        // Nobody is called anything a player cannot be called, so that is not worth a question.
        if (cache == null || !net.minecraft.util.StringUtil.isValidPlayerName(name)) {
            noSuchPlayer(asking, name);
            return;
        }
        if (!NAME_LOOKUPS.spend(asking.getUUID(), 1)) {
            Notices.tell(asking, Component.translatable("message.gathering.collection_names_busy"));
            return;
        }
        // The answer is waited for before the question is asked, so it always comes back through the
        // server's queue in the order it was asked. A lookup that finished before anything was
        // waiting for it ran its answer there and then instead, ahead of answers to earlier presses
        // still in the queue - and two presses on one row could land the wrong way round.
        java.util.concurrent.CompletableFuture<Optional<com.mojang.authlib.GameProfile>> lookup =
                new java.util.concurrent.CompletableFuture<>();
        lookup.whenComplete(ServerRun.onServerThread(asking, (found, failure) -> {
            if (asking.hasDisconnected()) {
                return;
            }
            UUID who = failure != null || found == null
                    ? null
                    : found.map(com.mojang.authlib.GameProfile::getId).orElse(null);
            if (who == null) {
                noSuchPlayer(asking, name);
                return;
            }
            then.accept(who);
        }));
        try {
            NAMES.execute(() -> {
                try {
                    lookup.complete(cache.get(name));
                } catch (RuntimeException failed) {
                    lookup.completeExceptionally(failed);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            Notices.tell(asking, Component.translatable("message.gathering.collection_names_busy"));
        }
    }

    /**
     * Refused and said so, rather than written down against an id made up from the name: a key cut
     * for a player who does not exist is a key the owner thinks they gave somebody.
     */
    private static void noSuchPlayer(ServerPlayer asking, String name) {
        Notices.tell(asking, Component.translatable("message.gathering.collection_no_such_player", name));
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

    /**
     * A profile cache to ask instead of the server's, for a test. A game-test server has none at all,
     * which is why the lookup by name never ran in one.
     */
    private static volatile net.minecraft.server.players.GameProfileCache standIn;

    /** Asks this cache rather than the server's, until it is given null. */
    static void profilesForTesting(net.minecraft.server.players.GameProfileCache cache) {
        standIn = cache;
    }

    private static net.minecraft.server.players.GameProfileCache profiles(net.minecraft.server.MinecraftServer server) {
        net.minecraft.server.players.GameProfileCache cache = standIn;
        return cache != null ? cache : server.getProfileCache();
    }

    /** What to call a player the owner has let in, or their id where the server has forgotten them. */
    private static String nameOf(ServerPlayer asking, UUID who) {
        if (asking.getServer() != null) {
            ServerPlayer online = asking.getServer().getPlayerList().getPlayer(who);
            if (online != null) {
                return online.getGameProfile().getName();
            }
            net.minecraft.server.players.GameProfileCache cache = profiles(asking.getServer());
            Optional<String> remembered = cache == null ? Optional.empty()
                    : cache.get(who).map(com.mojang.authlib.GameProfile::getName);
            if (remembered.isPresent()) {
                return remembered.get();
            }
        }
        return who.toString().substring(0, 8);
    }
}
