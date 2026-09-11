package dev.gathering.server;

import net.minecraft.server.level.ServerPlayer;

/**
 * Everything the server forgets when somebody leaves, in one place.
 * <p>Both loaders have a disconnect hook, and each was a list of calls somebody had to
 * remember to add to twice. An audit found what that costs: the trade cleanup existed, was
 * correct, and was wired into neither loader - so a player who logged out mid-trade left the
 * person across the table holding a trade that would not close, against somebody who was not
 * coming back.
 * <p>One method both loaders call, so a cleanup added here is a cleanup that runs. Anything
 * this server remembers about a particular player belongs in it.
 */
public final class PlayerGone {

    private PlayerGone() {
    }

    /**
     * Called from both loaders' join hooks and nowhere else.
     * <p>The other half of the same list. What a player is owed is handed over here because
     * this is the first moment there is somebody to hand it to.
     */
    public static void arrived(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Wants.joined(player);
        Owed.deliver(player);
    }

    /** Called from both loaders' disconnect hooks and nowhere else. */
    public static void left(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Wants.left(player);
        ReplayWatch.forget(player.getUUID());
        // The trade goes with them, and whoever is across the table is told rather than left
        // agreeing with an empty chair.
        TradeSessions.leave(player);
        // What this client has been told about is what this client knows. A reconnecting
        // player has an empty cache, and a server that still believes it sent the pictures
        // sends nothing for the rest of the session.
        CardArtPush.forget(player.getUUID());
        // And the throttle that decides how often they may ask about a set, which is counted
        // in this server's ticks and means nothing in the next one.
        CollectionSets.forget(player.getUUID());
        // And any search of theirs still waiting on that throttle, along with the same
        // tick count for the collection screen.
        CollectionView.forget(player.getUUID());
        // And what they had a card lookup out for, which is what bounds the shared worker.
        CardMetadataRequests.forget(player.getUUID());
        // And which deck they were last told about, so the one in their hand is sent again
        // the moment they are back holding it.
        dev.gathering.item.DeckItem.forget(player.getUUID());
        // And whatever refusal was still folding, which is counted in this server's ticks and
        // means nothing in the next one.
        Refusals.forget(player.getUUID());
        // And the lesson they were in the middle of. A practice game exists to teach one
        // person; when that person goes there is nobody it is for, and leaving it behind
        // leaves the table holding a game nobody can play and nobody can replace. Ordinary
        // games are untouched - a real match outlasts a logout on purpose.
        PracticeTable.forget(player.getServer(), player.getUUID());

        // StarterBoosters is deliberately NOT forgotten here, and this comment is what says
        // so - the check next door looks for the name rather than the call. Everything else
        // on this list is a cache about a player who has gone; that one is a record that they
        // have already been given something, and forgetting it on disconnect would make
        // logging out and back in a way to be given it again.
    }
}
