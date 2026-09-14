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
        if (state.tournament.isOver()) {
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
        state.prizes.add(new Prize(place, held.copy()));
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

    /** Hands every prize to the player in its place; places nobody finished in go back to the host. */
    static void handOut(MinecraftServer server, EventState state) {
        List<UUID> places = state.tournament.finalPlaces();
        for (Prize prize : state.prizes) {
            UUID winner = prize.place() <= places.size() ? places.get(prize.place() - 1) : state.tournament.host();
            give(server, state, winner, prize.stack());
        }
        state.prizes.clear();
        Events.save(state);
    }

    static void returnToHost(MinecraftServer server, EventState state) {
        for (Prize prize : state.prizes) {
            give(server, state, state.tournament.host(), prize.stack());
        }
        state.prizes.clear();
        Events.save(state);
    }

    private static void give(MinecraftServer server, EventState state, UUID who, ItemStack stack) {
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        if (player != null) {
            Handing.give(player, stack.copy());
            player.sendSystemMessage(Component.translatable("message.gathering.event.prize_given", stack.getCount(),
                    stack.getHoverName(), state.tournament.name()));
        } else {
            state.waitingPrizes.computeIfAbsent(who, ignored -> new ArrayList<>()).add(stack.copy());
        }
    }

    /** A player joined: anything an event is keeping for them is handed over. */
    static void arrived(ServerPlayer player) {
        for (EventState state : Events.all()) {
            List<ItemStack> waiting = state.waitingPrizes.remove(player.getUUID());
            if (waiting == null) {
                continue;
            }
            for (ItemStack stack : waiting) {
                Handing.give(player, stack);
            }
            player.sendSystemMessage(Component.translatable("message.gathering.event.prizes_kept", waiting.size(),
                    state.tournament.name()));
            Events.save(state);
        }
    }

    /** Whether anything is still waiting to be handed out by this event. */
    static boolean holdsAnything(EventState state) {
        return !state.prizes.isEmpty() || !state.waitingPrizes.isEmpty() || state.tournament.phase() != Tournament.Phase.FINISHED;
    }
}
