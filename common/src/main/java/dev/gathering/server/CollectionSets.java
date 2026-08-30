package dev.gathering.server;

import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.SetRelease;
import dev.gathering.core.collection.MissingCards;
import dev.gathering.core.collection.SetCompletion;
import dev.gathering.network.Sending;
import dev.gathering.network.SetMissingPayload;
import dev.gathering.network.SetProgressPayload;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * How much of each set is in a collection.
 *
 * <p>The question a binder cannot answer by being looked at, and the one that makes the next
 * pack worth opening. Worked out here rather than on the client for the reason every other
 * collection answer is: the cards are here, the card details are here, and what a set is comes
 * from Scryfall's own list of them - none of which is worth sending ten thousand rows to a
 * screen showing forty.
 *
 * <p><b>What it does about cards it cannot name.</b> A collection stores printings, not card
 * details, so working out which set a card belongs to means looking it up. Almost always it is
 * already known - a card got into a collection by being opened, bought or granted, and every
 * one of those had the details in hand at the time, and the cache survives a restart. But it
 * is not guaranteed, and a total quietly two hundred short is a screen lying about the one
 * number somebody opened it for. So the count of what could not be named is sent with the
 * answer and said out loud, and those cards are looked up in the background - so the same
 * screen opened again is right.
 *
 * <p>Server thread only, except the lookups it starts.
 */
public final class CollectionSets {

    /**
     * At most one of these per player per second.
     *
     * <p>Heavier than a search: a pass over every card in the collection, a cache read each,
     * and a sort. It is a button somebody presses, not something a screen polls, so a second
     * is generous - and the guard is here because a payload arrives whenever a client likes.
     */
    private static final int TICKS_BETWEEN = 20;

    /** Emptied rather than pruned when it outgrows a server's worth of players. */
    private static final int MOST_REMEMBERED = 512;

    private static final Map<UUID, Integer> LAST_ASKED = new java.util.HashMap<>();

    private CollectionSets() {
    }

    /** Answers one player's question about one collection. */
    public static void progress(ServerPlayer player, BlockPos where) {
        CollectionBlockEntity collection = CollectionView.at(player, where);
        if (collection == null || tooSoon(player)) {
            return;
        }
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            return;
        }
        // The set list is one fetch per server, kept: which set is current and which sets a
        // server draws packs from already want it. Asked for here rather than waited on, so a
        // server whose first question about sets is this one answers a moment later instead
        // of holding the tick.
        Map<String, SetRelease> sets = service.allSets().getNow(null);
        if (sets == null) {
            service.allSets().whenComplete((known, failure) -> player.server.execute(() -> {
                if (!player.hasDisconnected() && failure == null && known != null && !known.isEmpty()) {
                    answer(player, where, collection, service, known);
                }
            }));
            return;
        }
        answer(player, where, collection, service, sets);
    }

    /** Server thread only. */
    private static void answer(
            ServerPlayer player, BlockPos where, CollectionBlockEntity collection,
            CardDataService service, Map<String, SetRelease> sets) {
        List<CardMetadata> named = new ArrayList<>(collection.cards().distinct());
        List<UUID> unnamed = new ArrayList<>();
        for (CardIdentity card : collection.cards().cards()) {
            CardMetadata about = CollectionView.known(service, card);
            if (about != null) {
                named.add(about);
            } else {
                card.printing().ifPresent(unnamed::add);
            }
        }

        List<SetCompletion> progress = SetCompletion.of(named, sets);
        for (SetCompletion set : progress) {
            if (set.isComplete()) {
                Achievements.award(player, Achievements.SET_COMPLETE);
                break;
            }
        }
        List<SetProgressPayload.Row> rows = new ArrayList<>(
                Math.min(progress.size(), SetProgressPayload.MOST_SETS));
        for (SetCompletion set : progress) {
            if (rows.size() == SetProgressPayload.MOST_SETS) {
                break;
            }
            rows.add(SetProgressPayload.Row.of(set));
        }
        Sending.to(player, new SetProgressPayload(where, rows, unnamed.size()));

        // And whatever could not be named is looked up now, so asking again is right. Capped:
        // a collection nobody has ever fetched a card of is not worth ten thousand requests
        // in one press, and the next press picks up where this one left off.
        if (!unnamed.isEmpty()) {
            service.findAll(unnamed.subList(0, Math.min(unnamed.size(), MOST_LOOKED_UP)));
        }
    }

    /**
     * Which cards of one set this collection has not got.
     *
     * <p>The click behind the number. Everything about it is the same shape as the count
     * above: the collection is here, the card details are here, and what a set contains is a
     * fetch from Scryfall - so the subtraction happens here and a list goes back.
     *
     * <p><b>The set code came off a socket.</b> It is the one string in this file that sends
     * the server looking something up, so it is checked against the sets the server already
     * knows before anything is fetched. A code nothing recognizes is answered with silence
     * rather than with a request to Scryfall: otherwise a client could walk an alphabet
     * through somebody else's bandwidth.
     *
     * <p>Server thread only, except the lookup it starts.
     */
    public static void missing(ServerPlayer player, BlockPos where, String setCode) {
        CollectionBlockEntity collection = CollectionView.at(player, where);
        if (collection == null || tooSoon(player)) {
            return;
        }
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            return;
        }
        String wanted = setCode == null ? "" : setCode.trim().toLowerCase(java.util.Locale.ROOT);
        if (wanted.isEmpty()) {
            return;
        }
        Map<String, SetRelease> sets = service.allSets().getNow(null);
        if (sets == null) {
            // The set list has not arrived, so there is nothing to check the code against yet
            // and nothing worth fetching on the strength of it. The count above asks for the
            // same list, and this screen is only reachable through that one.
            return;
        }
        SetRelease set = sets.get(wanted);
        if (set == null) {
            return;
        }
        service.everyPrintingIn(wanted)
                .whenComplete((printings, failure) -> player.server.execute(() -> {
                    if (player.hasDisconnected() || failure != null || printings == null) {
                        return;
                    }
                    // The seat check again after a round trip to somebody else's host, the
                    // same as every other lookup here: a player can walk away from a
                    // collection while one is in flight.
                    if (CollectionView.at(player, where) == null) {
                        return;
                    }
                    Sending.to(player, SetMissingPayload.of(
                            MissingCards.of(set, printings, ownedFrom(collection, service, wanted))));
                }));
    }

    /**
     * The cards this collection holds that carry one set's code.
     *
     * <p>Only the ones the server can name: a card it has never looked up has no set code to
     * match on. That is the same softness the count above has, and it is said out loud there
     * - which is the screen this one is opened from, so somebody who sees a list that looks
     * long has already been told the count is still settling.
     */
    private static List<CardMetadata> ownedFrom(
            CollectionBlockEntity collection, CardDataService service, String setCode) {
        List<CardMetadata> found = new ArrayList<>();
        for (CardIdentity card : collection.cards().cards()) {
            CardMetadata about = CollectionView.known(service, card);
            if (about != null && about.setCode() != null
                    && about.setCode().trim().toLowerCase(java.util.Locale.ROOT).equals(setCode)) {
                found.add(about);
            }
        }
        return found;
    }

    /**
     * How many unknown cards one press will go and look up.
     *
     * <p>Scryfall takes seventy-five to a request and asks for ten a second, so this is about
     * four requests: enough that a collection with a handful of strangers in it is right the
     * second time it is asked, and few enough that one press cannot become a minute of
     * somebody else's bandwidth.
     */
    private static final int MOST_LOOKED_UP = 300;

    /** Whether this player asked too recently to ask again. Silently, like the search does. */
    private static boolean tooSoon(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = LAST_ASKED.get(player.getUUID());
        if (last != null && now - last < TICKS_BETWEEN) {
            return true;
        }
        if (LAST_ASKED.size() > MOST_REMEMBERED) {
            LAST_ASKED.clear();
        }
        LAST_ASKED.put(player.getUUID(), now);
        return false;
    }
}
