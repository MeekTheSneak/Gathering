package dev.gathering.server.events;

import dev.gathering.core.tournament.Tournament;
import dev.gathering.server.Handing;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Prizes: any items a host puts up, held by the event and handed out by final place.
 * <p>Taken from the host's hand when they are put up, so a prize announced is a prize that
 * exists. A winner who is away when the event finishes is kept their prize until they next
 * join; an event called off gives every prize back to its host the same way.
 */
public final class EventPrizes {

    /** The lowest place a prize can be put up for. */
    public static final int LOWEST_PLACE = 16;

    private EventPrizes() {
    }

    /** One prize, for one place. */
    record Prize(int place, ItemStack stack) {
    }

    /** The host puts up what is in their hand as a prize for this place. */
    static void put(ServerPlayer host, UUID eventId, int place) {
        EventState state = Events.get(eventId).orElse(null);
        if (state == null) {
            return;
        }
        if (!state.tournament.host().equals(host.getUUID())) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.host_only"));
            return;
        }
        if (Events.refused(host, state, dev.gathering.core.tournament.HostActions.Action.ADD_PRIZE)) {
            return;
        }
        if (place < 1 || place > LOWEST_PLACE) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.prize_place", LOWEST_PLACE));
            return;
        }
        ItemStack held = host.getMainHandItem();
        if (held.isEmpty()) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.hold_a_prize"));
            return;
        }
        // On disk before it leaves the hand. A prize the event holds only in memory is lost with
        // a restart, so a save that fails refuses the prize and the host keeps it.
        Prize prize = new Prize(place, held.copy());
        state.prizes.add(prize);
        state.log(host.getUUID(), "prize", place + ": " + held.getCount() + " x " + held.getHoverName().getString());
        if (!Events.save(state)) {
            state.prizes.remove(prize);
            state.log.remove(state.log.size() - 1);
            host.sendSystemMessage(Component.translatable("message.gathering.event.prize_not_saved"));
            return;
        }
        host.getInventory().setItem(host.getInventory().selected, ItemStack.EMPTY);
        host.sendSystemMessage(Component.translatable("message.gathering.event.prize_added", held.getCount(),
                held.getHoverName(), place));
        Events.changed(host.getServer(), state);
    }

    /** Prizes, in place order, as lines. */
    static List<String> describe(EventState state) {
        List<String> lines = new ArrayList<>();
        state.prizes.stream().sorted(java.util.Comparator.comparingInt(Prize::place)).forEach(prize -> {
            if (lines.size() < 32) {
                lines.add(prize.place() + ": " + prize.stack().getCount() + " x " + prize.stack().getHoverName().getString());
            }
        });
        return lines;
    }

    /**
     * Hands every prize to the player in its place; places nobody finished in go back to the host.
     * <p>In two steps, each saved before anything moves. First every prize is assigned to who it
     * belongs to and kept for them; then whoever is online is handed theirs, one person at a
     * time, and only once the event on disk no longer holds it. A save that fails leaves the
     * prize kept for them, to be handed over when they next join - never handed over while the
     * saved event still holds it, which a restart would hand over again.
     */
    static void handOut(MinecraftServer server, EventState state) {
        List<UUID> places = state.tournament.finalPlaces();
        assign(state, prize -> prize.place() <= places.size() ? places.get(prize.place() - 1) : state.tournament.host());
        deliverToEverybodyOnline(server, state);
    }

    static void returnToHost(MinecraftServer server, EventState state) {
        assign(state, prize -> state.tournament.host());
        deliverToEverybodyOnline(server, state);
    }

    private static void assign(EventState state, java.util.function.Function<Prize, UUID> owner) {
        if (state.prizes.isEmpty()) {
            return;
        }
        for (Prize prize : state.prizes) {
            UUID who = owner.apply(prize);
            state.waitingPrizes.computeIfAbsent(who, ignored -> new ArrayList<>()).add(prize.stack().copy());
            state.log(who, "prize_won", prize.place() + ": " + prize.stack().getCount() + " x "
                    + prize.stack().getHoverName().getString());
        }
        state.prizes.clear();
        // If this save fails, the prizes are kept for their owners in memory and are still prizes
        // on disk. Either way nothing has been handed out yet, so nothing can be handed out twice:
        // each hand-over below saves first.
        Events.save(state);
    }

    private static void deliverToEverybodyOnline(MinecraftServer server, EventState state) {
        for (UUID who : List.copyOf(state.waitingPrizes.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player != null) {
                deliver(player, state);
            }
        }
    }

    /** Hands this player what the event is keeping for them, once that is saved as no longer kept. */
    private static void deliver(ServerPlayer player, EventState state) {
        List<ItemStack> waiting = state.waitingPrizes.remove(player.getUUID());
        if (waiting == null) {
            return;
        }
        if (!Events.save(state)) {
            state.waitingPrizes.put(player.getUUID(), waiting);
            return;
        }
        for (ItemStack stack : waiting) {
            Handing.give(player, stack.copy());
            player.sendSystemMessage(Component.translatable("message.gathering.event.prize_given", stack.getCount(),
                    stack.getHoverName(), state.tournament.name()));
        }
    }

    /** A player joined: anything an event is keeping for them is handed over. */
    static void arrived(ServerPlayer player) {
        for (EventState state : Events.all()) {
            if (state.waitingPrizes.containsKey(player.getUUID())) {
                deliver(player, state);
            }
        }
    }

    /** Whether anything is still waiting to be handed out by this event. */
    static boolean holdsAnything(EventState state) {
        return !state.prizes.isEmpty() || !state.waitingPrizes.isEmpty() || state.tournament.phase() != Tournament.Phase.FINISHED;
    }
}
