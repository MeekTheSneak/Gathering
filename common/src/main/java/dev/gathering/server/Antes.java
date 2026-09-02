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

    /**
     * Tables with a question open, and what the table has said so far.
     *
     * <p>Keyed by the world as well as the block. A position on its own is not a table: the
     * overworld and the nether both have a block at every coordinate, and two tables at the
     * same numbers in different worlds would have shared one question - with either one able
     * to answer for the other.
     */
    private static final Map<Where, Asking> ASKING = new ConcurrentHashMap<>();

    /** One table, anywhere in the server. */
    private record Where(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> world,
            BlockPos at) {

        static Where of(ServerLevel level, BlockPos at) {
            return new Where(level.dimension(), at.immutable());
        }
    }

    /**
     * A question in flight, and everything the game it starts will need.
     *
     * <p>{@code formatChosen} travels with it because the game starts later, out of the last
     * answer, and by then whoever picked the format is long gone from the call stack. Without
     * it the table came up never having been told a format was named - so the deck check,
     * which refuses only on a table that was, could not refuse anything on any server with
     * ante turned on.
     */
    private record Asking(AnteConsent consent, MatchRules rules, boolean formatChosen) {
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
    public static boolean isAsking(ServerLevel level, BlockPos tableOrigin) {
        return level != null && tableOrigin != null
                && ASKING.containsKey(Where.of(level, tableOrigin));
    }

    /**
     * Puts the question to every seat, if this table needs asking.
     *
     * <p>Returns false when there is nothing to ask - ante is off, or the table has already
     * agreed - and the caller starts the game as it always did. Returns true when the
     * question has gone out, and the caller does nothing: the game starts when the last seat
     * answers, not now.
     */
    public static boolean askedFirst(ServerLevel level, BlockPos tableOrigin,
            MatchRules rules, boolean formatChosen) {
        if (!isOffered()) {
            return false;
        }
        Set<SeatId> seats = seatedAt(level, tableOrigin).keySet();
        if (seats.isEmpty()) {
            return false;
        }
        Where where = Where.of(level, tableOrigin);
        Asking already = ASKING.get(where);
        if (already != null && already.consent().settled()) {
            // Agreed a moment ago and the game is starting now. The answer is spent here
            // rather than kept: consent is per game, so the next one asks again.
            ASKING.remove(where);
            return false;
        }

        ASKING.put(where, new Asking(AnteConsent.asking(seats), rules, formatChosen));
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
        Where where = Where.of(level, tableOrigin);
        Asking asking = ASKING.get(where);
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
        ASKING.put(where, new Asking(said, asking.rules(), asking.formatChosen()));

        if (said.refused()) {
            ASKING.remove(where);
            close(level, tableOrigin);
            // And then the game starts anyway, without a stake. Declining ante at a real
            // table means "deal me in, but I am not playing for my cards" - it does not mean
            // nobody plays. This used to stop here, so one person saying no left a table that
            // could not start a game at all: the next attempt asked the same question and got
            // the same answer, for ever. The message even said what should have happened -
            // "Ante declined. Nothing is at stake." - while nothing at all did.
            //
            // Unless the server says a table may not opt out, which is what
            // allow_per_table_opt_out has always meant and what nothing has ever read. A
            // server that turns it off wants ante or no game, and now says so out loud
            // rather than leaving the table clicking at nothing.
            if (!ServerSettings.get().ante().allowPerTableOptOut()) {
                tell(level, tableOrigin, "message.gathering.ante_required");
                return;
            }
            tell(level, tableOrigin, "message.gathering.ante_declined");
            begin(level, tableOrigin, asking.rules(), asking.formatChosen(), false);
            return;
        }
        if (!said.settled()) {
            show(level, tableOrigin);
            return;
        }

        tell(level, tableOrigin, "message.gathering.ante_agreed");
        close(level, tableOrigin);
        ASKING.remove(where);
        begin(level, tableOrigin, asking.rules(), asking.formatChosen(), true);
    }

    /**
     * Somebody sat down or stood up while the question was open.
     *
     * <p>Both directions, and the first is the one that matters. The seats were fixed when
     * the question was asked, so a player who sat down afterwards was never in the set being
     * waited on - and the game could reach unanimity and start with somebody at the table who
     * had never been asked. That is a card taken off a person who did not agree, which is the
     * single failure this whole feature exists to prevent.
     *
     * <p>The other direction is the ordinary one: a seat given up takes its answer with it,
     * and if it was the last seat anybody was waiting on, the game the rest agreed to starts
     * rather than hanging on a chair nobody is in.
     */
    public static void seatsChanged(ServerLevel level, BlockPos tableOrigin) {
        if (level == null || tableOrigin == null) {
            return;
        }
        Where where = Where.of(level, tableOrigin);
        Asking asking = ASKING.get(where);
        if (asking == null) {
            return;
        }
        Set<SeatId> seats = seatedAt(level, tableOrigin).keySet();
        if (seats.isEmpty()) {
            ASKING.remove(where);
            close(level, tableOrigin);
            return;
        }
        // Rebuilt against who is actually sitting there. A yes left behind by somebody who
        // has stood up is not a vote, and the rule in :core drops it for us.
        AnteConsent said = new AnteConsent(seats, asking.consent().answers());
        ASKING.put(where, new Asking(said, asking.rules(), asking.formatChosen()));
        if (!said.settled()) {
            show(level, tableOrigin);
            return;
        }
        // The seat that left was the one everybody was waiting for. The remaining players
        // have all said yes, so the game they agreed to starts rather than hanging.
        tell(level, tableOrigin, "message.gathering.ante_agreed");
        close(level, tableOrigin);
        ASKING.remove(where);
        begin(level, tableOrigin, asking.rules(), asking.formatChosen(), true);
    }

    /**
     * Starts the game the table settled on, for keeps or not.
     *
     * <p>Marked on the table rather than remembered here, because the decks arrive after the
     * game starts and a stake is drawn as each one goes down. It also has to survive a
     * restart: a deck put down tomorrow morning is staked from on the terms everybody agreed
     * to last night, or on none at all.
     *
     * <p>Both answers come through here. A table that declined is still a table that asked
     * for a game, and every step after the question - the format it was told, the board its
     * seats are sent - is the same whichever way the vote went.
     */
    private static void begin(ServerLevel level, BlockPos tableOrigin, MatchRules rules,
            boolean formatChosen, boolean forKeeps) {
        TableSessions.Outcome outcome = TableSessions.start(level, tableOrigin, rules);
        if (outcome != TableSessions.Outcome.STARTED) {
            tell(level, tableOrigin, outcome.messageKey());
            return;
        }
        TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(table -> {
                    table.playForKeeps(forKeeps);
                    // Exactly what the ordinary start does. A game that went through the ante
                    // question is still a game somebody picked a format for, and a table that
                    // does not know it was named cannot be held to it - which is as true of
                    // the table that declined as of the one that agreed.
                    table.formatWasChosen(formatChosen);
                });
        TableBroadcast.sendToTable(level, tableOrigin);
    }

    // ------------------------------------------------------------------ bits

    private static void show(ServerLevel level, BlockPos tableOrigin) {
        Asking asking = ASKING.get(Where.of(level, tableOrigin));
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
