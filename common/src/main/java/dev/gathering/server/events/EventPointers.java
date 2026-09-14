package dev.gathering.server.events;

import dev.gathering.network.EventPointerPayload;
import dev.gathering.network.Sending;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pointing a player at their seat in a venue.
 * <p>At a long table they are moved straight into it; across a hall of tables they are told the
 * table's number and shown the way until they sit down, as the owner decided.
 */
public final class EventPointers {

    private EventPointers() {
    }

    static void pointTo(ServerPlayer player, BlockPos seat) {
        int table = Events.of(player.getUUID())
                .flatMap(state -> state.tournament.currentRound().flatMap(round -> round.pairingOf(player.getUUID())))
                .map(pairing -> pairing.table()).orElse(0);
        player.sendSystemMessage(Component.translatable("message.gathering.event.go_to_table", table,
                seat.getX(), seat.getY(), seat.getZ()));
        Sending.to(player, new EventPointerPayload(seat, Math.max(1, table)));
    }
}
