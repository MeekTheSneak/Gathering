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
import dev.gathering.network.FetchBasicPayload;
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

    /**
     * A basic land, put on the table as a token.
     *
     * <p>The same act as making a token and it goes down the same path: something that came
     * from outside the game, is drawn as an ordinary card, and goes away with the game. What
     * differs is only which lookup answers - a basic land is a real printing, not a token, so
     * the token search would never find one.
     *
     * <p>The name is not the client's. It comes from an enum of the six basic lands, so this
     * cannot be used to ask the server to look up an arbitrary card - see
     * {@link FetchBasicPayload}.
     */
    public static void fetchBasic(
            ServerPlayer player, CardDataService service, FetchBasicPayload payload) {
        TableReach.Seated at = TableReach.seatedAt(player, payload.table()).orElse(null);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        String name = payload.land().printedName();
        service.findByName(name)
                .whenComplete((found, failure) -> player.server.execute(() -> {
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.card_lookup_failed", name));
                        return;
                    }
                    put(player, level, at.origin(), at.seat(),
                            found.map(List::of).orElse(List.of()), payload.count(), name);
                }));
    }

    /** Server thread only. */
    private static void place(
            ServerPlayer player, ServerLevel level, BlockPos origin, SeatId seat,
            List<CardMetadata> found, CreateTokenPayload payload) {
        put(player, level, origin, seat, found, payload.count(), payload.name());
    }

    /**
     * Puts whatever was found on the table, as that many tokens.
     *
     * <p>Shared by the token search and the basic-land button so the two cannot drift: both
     * have to tell the client what the card is before the board arrives naming it, both have
     * to mark the table changed, and both have to say something when nothing was found.
     *
     * <p>Server thread only.
     */
    private static void put(
            ServerPlayer player, ServerLevel level, BlockPos origin, SeatId seatWhenAsked,
            List<CardMetadata> found, int count, String asked) {
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.token_not_found", asked));
            return;
        }
        // The seat again, not the one from before the lookup. A card lookup is a network
        // round trip to somebody else's host, and a player can stand up during one - at
        // which point the seat they asked from may be empty, or may be somebody else's.
        // Putting tokens on a board that is no longer yours is a small hole and an easy one:
        // ask, stand up, and the cards land on whoever sat down.
        SeatId seat = seatWhenAsked;
        if (!TableReach.stillSeated(player, origin, seat)) {
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
                seat, seat, CardIdentity.ofPrinting(token.scryfallId()), count));

        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(table -> table.setChanged());
        TableBroadcast.sendToTable(level, origin);
        player.sendSystemMessage(Component.translatable(
                "message.gathering.token_created", count, token.name()));
    }

    /** The corner of the table, if this player is at one. One rule; see {@link TableReach}. */
    private static Optional<BlockPos> originFor(ServerPlayer player, BlockPos clicked, ServerLevel level) {
        return TableReach.originFor(player, clicked);
    }
}
