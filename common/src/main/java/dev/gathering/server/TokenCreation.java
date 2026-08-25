package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.network.CreateTokenPayload;
import dev.gathering.service.CardDataService;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Making tokens at a table.
 *
 * <p>Tokens are real printings from Scryfall, not invented cards. That is the whole reason
 * this goes through the card pipeline rather than making something up locally: a Thrull token
 * has art, a type line and a power and toughness that somebody printed, and a table that drew
 * a grey rectangle saying "1/1 Thrull" would be the one place in the mod that lies about what
 * a card is.
 *
 * <p>The client sends a name. The server does the lookup and builds the identity, so nothing
 * a player types decides what a token actually is - the same rule every other name crossing
 * this boundary follows.
 */
public final class TokenCreation {

    /** How far a player may be from a table and still be making tokens at it. */
    private static final double REACH = 12.0d;

    private TokenCreation() {
    }

    public static void handle(ServerPlayer player, CardDataService service, CreateTokenPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = originFor(player, payload.table(), level).orElse(null);
        if (origin == null) {
            return;
        }
        SeatId seat = TableSessions.seatIdOf(level, origin, player.getUUID()).orElse(null);
        if (seat == null || TableSessions.sessionAt(level, origin).isEmpty()) {
            return;
        }

        service.tokensNamed(payload.name())
                .whenComplete((found, failure) -> player.server.execute(() -> {
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.card_lookup_failed", payload.name()));
                        return;
                    }
                    place(player, level, origin, seat, found, payload);
                }));
    }

    /** Server thread only. */
    private static void place(
            ServerPlayer player, ServerLevel level, BlockPos origin, SeatId seat,
            List<CardMetadata> found, CreateTokenPayload payload) {
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.token_not_found", payload.name()));
            return;
        }
        // The most recent printing, which is what the search asked for and what somebody
        // picturing a token in their head is almost always picturing.
        CardMetadata token = found.get(0);

        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        if (session == null) {
            return;
        }
        // The client is about to be told to draw a card it has never heard of, so tell it what
        // the card is before the board arrives naming it.
        Sending.to(player,
                new CardMetadataPayload(List.of(CardSummary.of(token))));

        session.submit(new GameEvent.TokenCreated(
                seat, seat, CardIdentity.ofPrinting(token.scryfallId()), payload.count()));

        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(table -> table.setChanged());
        TableBroadcast.sendToTable(level, origin);
        player.sendSystemMessage(Component.translatable(
                "message.gathering.token_created", payload.count(), token.name()));
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
}
