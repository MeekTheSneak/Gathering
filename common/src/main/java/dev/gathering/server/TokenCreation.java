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
 * <p>Tokens are real printings from Scryfall, not invented cards. That is the whole reason
 * this goes through the card pipeline rather than making something up locally: a Thrull token
 * has art, a type line and a power and toughness that somebody printed, and a table that drew
 * a gray rectangle saying "1/1 Thrull" would be the one place in the mod that lies about what
 * a card is.
 * <p>The client sends a name, or a printing a player picked. Either way the server does the
 * lookup and builds the identity, so nothing a client sends decides what a token actually is -
 * the same rule every other name crossing this boundary follows.
 * <p>A name can match several different tokens - a 1/1 white Cat and a 2/2 green one - and when
 * it does the player is asked which rather than handed the newest. See {@link #offer}.
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
                .whenComplete(ServerRun.onServerThread(player, (found, failure) -> {
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.card_lookup_failed", payload.name()));
                        return;
                    }
                    offer(player, level, origin, seat, found, payload.count(), payload.name());
                }));
    }

    /**
     * Makes the token a name found, or asks which one when it found several.
     * <p>Several means several that differ. The search already gives one printing per token,
     * and the same token reprinted with new art is still one choice, so what is left is
     * grouped by what matters on the table - type, size, colors and rules text - and one row is
     * offered per group. One group is made straight away, with no question: most names are
     * one token, and a choice with one answer is a click for nothing.
     * <p>Server thread only.
     *
     * @return how many distinct tokens the player was offered; one or zero means none was asked
     */
    public static int offer(ServerPlayer player, ServerLevel level, BlockPos origin, SeatId seat,
            List<CardMetadata> found, int count, String asked) {
        List<CardMetadata> distinct = distinctTokens(found);
        if (distinct.size() <= 1) {
            put(player, level, origin, seat, distinct, count, asked,
                    "message.gathering.token_created", "message.gathering.token_not_found");
            return distinct.size();
        }
        // Still their seat, as put() checks: a choice offered to somebody who has stood up is
        // a question about a board that is not theirs any more.
        if (!TableReach.stillSeated(player, origin, seat)) {
            return 0;
        }
        List<CardSummary> choices = new java.util.ArrayList<>();
        for (CardMetadata token : distinct) {
            choices.add(CardSummary.of(token));
        }
        Sending.to(player, new dev.gathering.network.TokenChoicesPayload(
                origin, asked, count, choices));
        return distinct.size();
    }

    /**
     * One printing per token that plays differently, newest first.
     * <p>Package-visible so the grouping can be checked without a table.
     */
    static List<CardMetadata> distinctTokens(List<CardMetadata> found) {
        java.util.LinkedHashMap<String, CardMetadata> byWhatItIs = new java.util.LinkedHashMap<>();
        for (CardMetadata token : found) {
            byWhatItIs.putIfAbsent(whatItIs(token), token);
        }
        return List.copyOf(byWhatItIs.values());
    }

    /** What makes two tokens the same token for a player: name, type, size, colors, text. */
    private static String whatItIs(CardMetadata token) {
        var face = token.frontFace().orElse(null);
        String power = face == null ? "" : String.valueOf(face.power());
        String toughness = face == null ? "" : String.valueOf(face.toughness());
        String text = face == null ? token.oracleText() : face.oracleText();
        return String.join("|", token.name(), token.typeLine(), power, toughness,
                new java.util.TreeSet<>(token.colors()).toString(), String.valueOf(text));
    }

    /**
     * Makes this many of one exact token, picked by printing.
     * <p>From a card's own "make its token" row, a choice between tokens of one name, or a
     * remembered token. The printing is looked up here and must be a token or an emblem: a
     * client that sent the id of a real card gets nothing, exactly as it would have typing the
     * card's name into the token search.
     */
    public static void handleChosen(
            ServerPlayer player, CardDataService service, dev.gathering.network.MakeTokenPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = originFor(player, payload.table(), level).orElse(null);
        if (origin == null) {
            return;
        }
        SeatId seat = TableSessions.seatIdOf(level, origin, player.getUUID()).orElse(null);
        if (seat == null || TableSessions.sessionAt(level, origin).isEmpty()) {
            return;
        }
        service.card(payload.printing())
                .whenComplete(ServerRun.onServerThread(player, (found, failure) -> {
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.card_lookup_failed", payload.printing().toString()));
                        return;
                    }
                    makeChosen(player, level, origin, seat,
                            found == null ? Optional.empty() : found, payload.count());
                }));
    }

    /**
     * The half of {@link #handleChosen} after the lookup, so it can be checked without one.
     * <p>Server thread only.
     *
     * @return whether a token was made
     */
    public static boolean makeChosen(ServerPlayer player, ServerLevel level, BlockPos origin, SeatId seat,
            Optional<CardMetadata> found, int count) {
        CardMetadata token = found.filter(TokenCreation::isATokenOrEmblem).orElse(null);
        if (token == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.token_not_found",
                    found.map(CardMetadata::name).orElse("?")));
            return false;
        }
        put(player, level, origin, seat, List.of(token), count, token.name(),
                "message.gathering.token_created", "message.gathering.token_not_found");
        return true;
    }

    /**
     * Whether Scryfall files this printing as a token or an emblem.
     * <p>The layout first, which is what Scryfall itself uses to mean "not a real card", and the
     * type line after, for the printings whose layout says something more specific.
     */
    static boolean isATokenOrEmblem(CardMetadata card) {
        String layout = card.layout() == null ? "" : card.layout().toLowerCase(java.util.Locale.ROOT);
        if (layout.equals("token") || layout.equals("double_faced_token") || layout.equals("emblem")) {
            return true;
        }
        for (String word : String.valueOf(card.typeLine()).split("[^A-Za-z]+")) {
            String lower = word.toLowerCase(java.util.Locale.ROOT);
            if (lower.equals("token") || lower.equals("emblem")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts whatever was found on the table, that many times.
     * <p>Shared by everything that brings a card in from outside the game - the token search,
     * the basic-land button, the dungeons - so that none of them can drift from the others.
     * Every one of them has to tell the client what the card is before the board arrives
     * naming it, has to re-check the seat after the lookup came back, has to mark the table
     * changed, and has to say something when nothing was found. Four rules, written once.
     * <p>What it says afterwards is the caller's, because a dungeon is not a token and a
     * message calling it one would be the mod being wrong about Magic in the one sentence a
     * player reads.
     * <p>Server thread only.
     */
    public static void put(
            ServerPlayer player, ServerLevel level, BlockPos origin, SeatId seatWhenAsked,
            List<CardMetadata> found, int count, String asked, String madeKey, String missingKey) {
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.translatable(missingKey, asked));
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
        player.sendSystemMessage(Component.translatable(madeKey, count, token.name()));
    }

    /** The corner of the table, if this player is at one. One rule; see {@link TableReach}. */
    private static Optional<BlockPos> originFor(ServerPlayer player, BlockPos clicked, ServerLevel level) {
        return TableReach.originFor(player, clicked);
    }
}
