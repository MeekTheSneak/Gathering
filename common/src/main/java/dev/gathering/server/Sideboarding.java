package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.SeatId;
import dev.gathering.item.DeckComponent;
import dev.gathering.network.OpenSideboardPayload;
import dev.gathering.network.SideboardEditPayload;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Changing your deck between games of a match.
 *
 * <p>Edits the deck the table is holding for the asking player's own seat. Which deck that is
 * comes from the table's record of who is sitting where, never from the payload - so there is
 * no way to phrase a request to reach into somebody else's sideboard, which is stronger than
 * checking that nobody did.
 *
 * <p>Only between games, and only in a format that has a sideboard. Neither is a rules
 * judgement the mod is making up: a deck arrives at the table as its owner built it, and a
 * singleton format has no fifteen cards to swap.
 */
public final class Sideboarding {

    /** How far a player may be from a table and still be sideboarding at it. */
    private static final double REACH = 12.0d;

    private Sideboarding() {
    }

    /**
     * Sends a player their deck to change, if this is a moment when they may.
     *
     * <p>Silent when it is not. Being told "you cannot sideboard right now" every time you
     * right-click a table mid-game is worse than nothing happening.
     */
    public static void offerTo(ServerPlayer player, BlockPos tableOrigin) {
        ServerLevel level = player.serverLevel();
        if (!TableMatch.isSideboarding(level, tableOrigin)) {
            return;
        }
        deckFor(level, tableOrigin, player).ifPresent(held -> TableSessions.matchAt(level, tableOrigin)
                .ifPresent(match -> player.connection.send(new ClientboundCustomPayloadPacket(
                        new OpenSideboardPayload(
                                tableOrigin, held.deck(), match.gameNumber(), match.rules().bestOf())))));
    }

    public static void handle(ServerPlayer player, SideboardEditPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = originFor(player, payload.table(), level).orElse(null);
        if (origin == null || !TableMatch.isSideboarding(level, origin)) {
            return;
        }
        Held held = deckFor(level, origin, player).orElse(null);
        if (held == null) {
            return;
        }
        // Only ever between the deck proper and the sideboard. The command zone is not
        // somewhere a card gets moved to between games of a match, and a client asking for
        // that is asking for something that has no meaning rather than something forbidden.
        if (!isSwap(payload.from(), payload.to())) {
            return;
        }

        Optional<DeckComponent> edited = held.deck().moved(payload.from(), payload.to(), payload.card());
        if (edited.isEmpty()) {
            // A stale click: the card is not where the client thought. Doing nothing is right.
            return;
        }
        // Kept, not dropped. Boarding between games edits the deck and never the pool, and a
        // pool that vanished at the first sideboard step would stop a limited match being
        // limited halfway through.
        held.table().holdDeck(held.seat(), edited.get(),
                held.table().poolOf(held.seat()).orElse(null));
        player.connection.send(new ClientboundCustomPayloadPacket(new OpenSideboardPayload(
                origin, edited.get(),
                TableSessions.matchAt(level, origin).map(match -> match.gameNumber()).orElse(1),
                TableSessions.matchAt(level, origin).map(match -> match.rules().bestOf()).orElse(1))));
    }

    private static boolean isSwap(DeckComponent.Section from, DeckComponent.Section to) {
        return from != to
                && (from == DeckComponent.Section.MAINBOARD || from == DeckComponent.Section.SIDEBOARD)
                && (to == DeckComponent.Section.MAINBOARD || to == DeckComponent.Section.SIDEBOARD);
    }

    /** The deck this table is holding for this player, and the seat it belongs to. */
    private static Optional<Held> deckFor(ServerLevel level, BlockPos tableOrigin, ServerPlayer player) {
        SeatId seat = TableSessions.seatIdOf(level, tableOrigin, player.getUUID()).orElse(null);
        if (seat == null) {
            return Optional.empty();
        }
        TableBlockEntity table = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .orElse(null);
        if (table == null) {
            return Optional.empty();
        }
        return table.deckOf(seat).map(deck -> new Held(table, seat, deck));
    }

    private static Optional<BlockPos> originFor(ServerPlayer player, BlockPos clicked, ServerLevel level) {
        if (player.distanceToSqr(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5)
                > REACH * REACH) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TableBlock)) {
            return Optional.empty();
        }
        return Optional.of(TableBlock.originOf(state, clicked));
    }

    private record Held(TableBlockEntity table, SeatId seat, DeckComponent deck) {
    }
}
