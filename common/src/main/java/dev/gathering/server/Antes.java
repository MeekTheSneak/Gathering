package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.ante.AnteConsent;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.network.AnteConsentPayload;
import dev.gathering.network.Sending;
import dev.gathering.service.ServerSettings;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Asking a table whether it is playing for keeps.
 *
 * <p>Ante is the only thing in this mod that takes a card off somebody for good, so it is the
 * only thing that asks first - everybody at the table, every game, by name. Not a setting an
 * admin turned on months ago and a player who has never seen it.
 *
 * <p>The rule about who has agreed is {@link AnteConsent}'s, and it is the part with the
 * failure in it: silence is not agreement, one refusal is enough, and an answer left behind
 * by somebody who has stood up is not a vote. This is the part that knows which table, which
 * player is in which seat, and when the game may actually begin.
 *
 * <p>Nothing here stakes a card. A question that has been answered yes starts an ordinary
 * game; what a table that agreed does differently arrives with the pot, and until then a
 * server with ante on is a server that asks and then plays for nothing. That order is
 * deliberate - the consent gate is what makes everything after it safe to build.
 */
public final class Antes {

    /** Tables with a question open, and what the table has said so far. */
    private static final Map<BlockPos, Asking> ASKING = new ConcurrentHashMap<>();

    /** A question in progress: the answers, and what to start if they all say yes. */
    private record Asking(AnteConsent consent, MatchRules rules) {
    }

    private Antes() {
    }

    /**
     * Whether this server plays for keeps at all.
     *
     * <p>Config's answer, and the config has already refused to turn ante on without
     * collection mode - a card is only property where a collection makes it one.
     */
    public static boolean isOffered() {
        return ServerSettings.get().ante().enabled();
    }

    /** Between servers, so one world's half-asked question is not the next one's. */
    public static void clear() {
        ASKING.clear();
    }

    /** Whether this table is waiting on an answer from anybody. */
    public static boolean isAsking(BlockPos tableOrigin) {
        return tableOrigin != null && ASKING.containsKey(tableOrigin);
    }

    /**
     * Puts the question to every seat, if this table needs asking.
     *
     * <p>Returns false when there is nothing to ask - ante is off, or the table has already
     * agreed - and the caller starts the game as it always did. Returns true when the
     * question has gone out, and the caller does nothing: the game starts when the last seat
     * answers, not now.
     */
    public static boolean askedFirst(ServerLevel level, BlockPos tableOrigin, MatchRules rules) {
        if (!isOffered()) {
            return false;
        }
        Set<SeatId> seats = seatedAt(level, tableOrigin).keySet();
        if (seats.isEmpty()) {
            return false;
        }
        Asking already = ASKING.get(tableOrigin);
        if (already != null && already.consent().settled()) {
            // Agreed a moment ago and the game is starting now. The answer is spent here
            // rather than kept: consent is per game, so the next one asks again.
            ASKING.remove(tableOrigin);
            return false;
        }

        ASKING.put(tableOrigin, new Asking(AnteConsent.asking(seats), rules));
        show(level, tableOrigin);
        return true;
    }

    /** One seat's answer. Starts the game when it was the last one needed. */
    public static void answer(ServerPlayer player, BlockPos tableOrigin,
            AnteConsent.Answer answer) {
        if (player == null || tableOrigin == null || answer == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Asking asking = ASKING.get(tableOrigin);
        if (asking == null) {
            return;
        }
        SeatId seat = seatIdOf(level, tableOrigin, player.getUUID()).orElse(null);
        if (seat == null) {
            // Not at this table. Somebody watching does not get a vote on whether the people
            // playing lose a card.
            return;
        }

        AnteConsent said = asking.consent().from(seat, answer);
        ASKING.put(tableOrigin, new Asking(said, asking.rules()));

        if (said.refused()) {
            ASKING.remove(tableOrigin);
            tell(level, tableOrigin, "message.gathering.ante_declined");
            close(level, tableOrigin);
            return;
        }
        if (!said.settled()) {
            show(level, tableOrigin);
            return;
        }

        tell(level, tableOrigin, "message.gathering.ante_agreed");
        close(level, tableOrigin);
        TableSessions.Outcome outcome =
                TableSessions.start(level, tableOrigin, asking.rules());
        ASKING.remove(tableOrigin);
        if (outcome == TableSessions.Outcome.STARTED) {
            TableBroadcast.sendToTable(level, tableOrigin);
        } else {
            tell(level, tableOrigin, outcome.messageKey());
        }
    }

    /** Somebody gave up their chair, or logged out, mid-question. */
    public static void left(ServerLevel level, BlockPos tableOrigin) {
        Asking asking = tableOrigin == null ? null : ASKING.get(tableOrigin);
        if (asking == null) {
            return;
        }
        Set<SeatId> seats = seatedAt(level, tableOrigin).keySet();
        if (seats.isEmpty()) {
            ASKING.remove(tableOrigin);
            close(level, tableOrigin);
            return;
        }
        // Rebuilt against who is actually sitting there. A yes left behind by somebody who
        // has stood up is not a vote, and the rule in :core drops it for us.
        ASKING.put(tableOrigin,
                new Asking(new AnteConsent(seats, asking.consent().answers()), asking.rules()));
        show(level, tableOrigin);
    }

    // ------------------------------------------------------------------ bits

    private static void show(ServerLevel level, BlockPos tableOrigin) {
        Asking asking = ASKING.get(tableOrigin);
        if (asking == null) {
            return;
        }
        var settings = ServerSettings.get().ante();
        seatedAt(level, tableOrigin).forEach((seat, player) -> Sending.to(player,
                new AnteConsentPayload(
                        tableOrigin,
                        settings.cardsPerPlayer(),
                        asking.consent().waitingOn().size(),
                        asking.consent().answerFrom(seat) == AnteConsent.Answer.IN,
                        false)));
    }

    /** Takes the question off everybody's screen, however it ended. */
    private static void close(ServerLevel level, BlockPos tableOrigin) {
        seatedAt(level, tableOrigin).forEach((seat, player) ->
                Sending.to(player, AnteConsentPayload.over(tableOrigin)));
    }

    private static void tell(ServerLevel level, BlockPos tableOrigin, String key) {
        TableBroadcast.tell(level, tableOrigin, Component.translatable(key));
    }

    /** Which seat each online player at this table is in. */
    private static Map<SeatId, ServerPlayer> seatedAt(ServerLevel level, BlockPos tableOrigin) {
        Map<SeatId, ServerPlayer> found = new LinkedHashMap<>();
        var anchors = TableClusters.at(level, tableOrigin).seats();
        for (int index = 0; index < anchors.size(); index++) {
            SeatAnchor anchor = anchors.get(index);
            UUID occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, anchor.cell()))
                    .flatMap(table -> table.occupantOf(anchor.side()))
                    .orElse(null);
            if (occupant == null) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(occupant);
            if (player != null) {
                found.put(new SeatId(index), player);
            }
        }
        return found;
    }

    private static Optional<SeatId> seatIdOf(ServerLevel level, BlockPos tableOrigin, UUID who) {
        var anchors = TableClusters.at(level, tableOrigin).seats();
        for (int index = 0; index < anchors.size(); index++) {
            SeatAnchor anchor = anchors.get(index);
            Optional<UUID> occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, anchor.cell()))
                    .flatMap(table -> table.occupantOf(anchor.side()));
            if (occupant.isPresent() && occupant.get().equals(who)) {
                return Optional.of(new SeatId(index));
            }
        }
        return Optional.empty();
    }
}
