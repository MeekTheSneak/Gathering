package dev.gathering.server.events;

import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Round;
import dev.gathering.core.tournament.Standings;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.network.CreateEventPayload;
import dev.gathering.network.EventActionPayload;
import dev.gathering.network.EventListPayload;
import dev.gathering.network.EventViewPayload;
import dev.gathering.network.Sending;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The tournament screens' server half: what each player is shown, and what their buttons do.
 * <p>Nothing here decides anything. Every button goes to {@link Events}, which applies it or
 * refuses it with a reason; this only turns an event into what one player may see of it - which
 * is everything public about the event and never a rating or a seed.
 */
public final class EventViews {

    private EventViews() {
    }

    public static void create(ServerPlayer player, CreateEventPayload payload) {
        if (!dev.gathering.server.TableReach.within(player, payload.desk())) {
            return;
        }
        // Shown by the desk taking it on.
        Events.hostAtDesk(player, payload.desk(), payload.name(), payload.settings());
    }

    public static void act(ServerPlayer player, EventActionPayload payload) {
        if (!withinBudget(player)) {
            // Each action can save and broadcast a whole event; a client sending them faster
            // than a person can press is dropped rather than answered.
            return;
        }
        UUID id = payload.event();
        switch (payload.action()) {
            case LIST -> list(player, true);
            case VIEW -> Events.get(id).ifPresent(state -> show(player, state, true));
            case REGISTER -> Events.register(player, id);
            case WITHDRAW -> Events.withdraw(player, id);
            case CHECK_IN -> Events.checkIn(player, id);
            case READY -> Events.ready(player, id);
            case REPORT -> result(player, payload).ifPresent(result -> Events.report(player, id, result));
            case OPEN_CHECK_IN -> Events.openCheckIn(player, id);
            case BEGIN -> Events.begin(player, id);
            case START_NOW -> Events.startNow(player, id);
            case SETTLE -> result(player, payload).ifPresent(result -> Events.settle(player, id, payload.table(), result));
            case DROP_PLAYER -> Events.dropPlayer(player, id, payload.player());
            case CANCEL -> Events.cancel(player, id);
            case ADD_TABLES -> {
                if (dev.gathering.server.TableReach.within(player, payload.at())) {
                    Events.addTables(player, id, payload.at());
                }
            }
            case ADD_PRIZE -> EventPrizes.put(player, id, payload.table());
            case RECORD -> record(player, payload.player());
            case MARK_REGISTRATION -> Events.markRegistration(player, id);
        }
        if (payload.action() != EventActionPayload.Action.LIST && payload.action() != EventActionPayload.Action.VIEW
                && payload.action() != EventActionPayload.Action.RECORD) {
            Events.get(id).ifPresent(state -> show(player, state, false));
        }
    }

    /**
     * The result a button sent, or empty with the player told it is not one. Never turned into
     * some other result: a report nobody made is worse than a refusal.
     */
    private static java.util.Optional<MatchResult> result(ServerPlayer player, EventActionPayload payload) {
        if (!MatchResult.isAMatch(payload.winsA(), payload.winsB(), payload.draws())) {
            player.sendSystemMessage(Component.translatable("message.gathering.event.not_a_result"));
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new MatchResult(payload.winsA(), payload.winsB(), payload.draws()));
    }

    /** Actions each player has left this second, and which second that is. */
    private static final java.util.Map<UUID, long[]> BUDGETS = new java.util.HashMap<>();

    /** The most event actions one player is answered for in a second; a person pressing buttons makes a few. */
    static final int ACTIONS_PER_SECOND = 8;

    /** Whether this player may be answered for another action now. */
    private static boolean withinBudget(ServerPlayer player) {
        long second = System.currentTimeMillis() / 1000L;
        long[] budget = BUDGETS.computeIfAbsent(player.getUUID(), ignored -> new long[] {second, 0});
        if (budget[0] != second) {
            budget[0] = second;
            budget[1] = 0;
        }
        return ++budget[1] <= ACTIONS_PER_SECOND;
    }

    /** Forgets one player's budget, who has gone. */
    public static void forget(UUID player) {
        BUDGETS.remove(player);
    }

    /** Forgets every budget, for a server that is stopping. */
    static void forgetBudgets() {
        BUDGETS.clear();
    }

    /** A player's public record, said in chat. */
    private static void record(ServerPlayer asking, UUID player) {
        UUID who = EventActionPayload.NONE.equals(player) ? asking.getUUID() : player;
        EventRecords.recordOf(who).ifPresentOrElse(record -> asking.sendSystemMessage(Component.translatable(
                        "message.gathering.event.record", record.name(), record.matchWins(), record.matchLosses(),
                        record.matchDraws(), Math.round(record.matchWinRate() * 100), record.eventsPlayed(), record.eventsWon())),
                () -> asking.sendSystemMessage(Component.translatable("message.gathering.event.no_record")));
    }

    /** The list of events, running first, then the most recently finished. */
    public static void list(ServerPlayer player, boolean show) {
        list(player, show, null);
    }

    /** The list, offering to host a tournament at this desk; anywhere else, with no desk, it offers nothing. */
    public static void list(ServerPlayer player, boolean show, BlockPos desk) {
        List<EventListPayload.Summary> summaries = new ArrayList<>();
        List<EventState> all = new ArrayList<>(Events.all());
        all.sort(java.util.Comparator.comparing((EventState state) -> state.tournament.isOver()));
        for (EventState state : all) {
            if (summaries.size() >= EventListPayload.MOST) {
                break;
            }
            Tournament tournament = state.tournament;
            summaries.add(new EventListPayload.Summary(tournament.id(), tournament.name(), hostName(player.getServer(), state),
                    tournament.phase().key(), tournament.settings().kind().key(), formatName(tournament.settings()),
                    tournament.entrants().size(), tournament.isRegistered(player.getUUID()),
                    tournament.host().equals(player.getUUID())));
        }
        Sending.to(player, new EventListPayload(summaries, show, java.util.Optional.ofNullable(desk).map(BlockPos::immutable)));
    }

    /** Sends a player the event as they see it. */
    public static void show(ServerPlayer player, EventState state, boolean open) {
        Sending.to(player, viewFor(player.getServer(), player.getUUID(), state, open));
    }

    /** Everybody an event concerns is sent it, updating a screen they have open. */
    static void broadcast(MinecraftServer server, EventState state) {
        if (server == null) {
            return;
        }
        Set<UUID> concerned = new LinkedHashSet<>();
        concerned.add(state.tournament.host());
        state.tournament.entrants().forEach(entrant -> concerned.add(entrant.id()));
        for (UUID who : concerned) {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player != null) {
                Sending.to(player, viewFor(server, who, state, false));
            }
        }
        EventLabels.update(server, state);
    }

    static EventViewPayload viewFor(MinecraftServer server, UUID viewer, EventState state, boolean show) {
        Tournament tournament = state.tournament;
        EventSettings settings = tournament.settings();
        List<EventViewPayload.Row> rows = new ArrayList<>();
        for (Standings.Row row : tournament.standings()) {
            if (rows.size() >= EventViewPayload.MOST_ROWS) {
                break;
            }
            rows.add(new EventViewPayload.Row(row.rank(), row.player().name(), row.matchPoints(), row.wins(), row.losses(),
                    row.draws(), tenths(row.opponentsMatchWin()), tenths(row.gameWin()), tenths(row.opponentsGameWin()),
                    row.player().isDropped()));
        }
        Round round = tournament.currentRound().orElse(null);
        List<EventViewPayload.Match> matches = new ArrayList<>();
        EventViewPayload.Mine mine = EventViewPayload.Mine.NONE;
        if (round != null && (tournament.phase() == Tournament.Phase.SWISS || tournament.phase() == Tournament.Phase.CUT)) {
            for (Pairing pairing : round.pairings()) {
                String status = pairing.isBye() ? "bye" : pairing.isConfirmed() ? "confirmed" : pairing.isDisputed()
                        ? "disputed" : pairing.reportA() != null || pairing.reportB() != null ? "reported" : "pending";
                matches.add(new EventViewPayload.Match(pairing.table(), Events.nameOf(state, pairing.a()),
                        pairing.isBye() ? "" : Events.nameOf(state, pairing.b()), status, said(pairing.result()),
                        pairing.a(), pairing.isBye() ? EventActionPayload.NONE : pairing.b()));
                if (pairing.has(viewer)) {
                    boolean first = pairing.a().equals(viewer);
                    // Both reports are kept from the first player's chair; each is turned to the
                    // viewer's chair exactly once.
                    MatchResult mineReported = fromChair(first ? pairing.reportA() : pairing.reportB(), first);
                    MatchResult theirs = fromChair(first ? pairing.reportB() : pairing.reportA(), first);
                    String suggested = Events.suggested(state, pairing.table())
                            .map(result -> said(first ? result : result.flipped())).orElse("");
                    mine = new EventViewPayload.Mine(pairing.table(), pairing.isBye() ? "" : Events.nameOf(state, pairing.opponentOf(viewer)),
                            suggested, said(mineReported), said(theirs), said(pairing.resultFor(viewer)), pairing.turnsAfterTime());
                }
            }
        }
        long ticksLeft = switch (tournament.phase()) {
            case SWISS, CUT -> (settings.roundMinutes() * Events.MINUTE_MILLIS - state.roundMillis) / 50L;
            case PREPARING -> settings.kind().isLimited() ? (settings.buildMinutes() * Events.MINUTE_MILLIS - state.buildMillis) / 50L : -1;
            default -> -1;
        };
        Entrant me = tournament.entrant(viewer).orElse(null);
        List<String> places = new ArrayList<>();
        if (tournament.phase() == Tournament.Phase.FINISHED) {
            for (UUID place : tournament.finalPlaces()) {
                if (places.size() >= 16) {
                    break;
                }
                places.add(Events.nameOf(state, place));
            }
        }
        return new EventViewPayload(tournament.id(), tournament.name(), hostName(server, state),
                tournament.host().equals(viewer), tournament.phase().key(), settings.kind().key(), formatName(settings),
                settings.bestOf(), settings.roundMinutes(), settings.buildMinutes(), settings.topCut(), settings.decks().key(),
                settings.largeEvent(), round == null ? 0 : round.number(), tournament.plannedRounds(),
                ticksLeft < 0 ? -1 : (int) Math.max(0, ticksLeft / 20), round != null && round.timeCalled(),
                round != null && round.elimination(), me != null, tournament.checkedIn().contains(viewer),
                tournament.ready().contains(viewer), me != null && me.isDropped(), tournament.entrants().size(), rows, matches,
                mine, EventPrizes.describe(state), places, hostRefusals(server, viewer, state), show);
    }

    /** Why each host control does not apply now, for the host alone. See EventViewPayload.hostRefusals. */
    private static List<String> hostRefusals(MinecraftServer server, UUID viewer, EventState state) {
        if (!state.tournament.host().equals(viewer)) {
            return List.of();
        }
        boolean packsOut = Events.packsStillOut(server, state);
        List<String> refusals = new ArrayList<>();
        for (dev.gathering.core.tournament.HostActions.Action action : dev.gathering.core.tournament.HostActions.Action.values()) {
            refusals.add(dev.gathering.core.tournament.HostActions.refusal(action, state.tournament, packsOut).orElse(""));
        }
        return refusals;
    }

    /** A result kept from the first player's chair, as the viewer sees it. */
    private static MatchResult fromChair(MatchResult fromFirst, boolean viewerIsFirst) {
        return fromFirst == null || viewerIsFirst ? fromFirst : fromFirst.flipped();
    }

    private static String said(MatchResult result) {
        return result == null ? "" : result.label();
    }

    private static int tenths(double share) {
        return (int) Math.round(share * 1000);
    }

    private static String hostName(MinecraftServer server, EventState state) {
        ServerPlayer host = server == null ? null : server.getPlayerList().getPlayer(state.tournament.host());
        if (host != null) {
            return host.getGameProfile().getName();
        }
        return EventRecords.recordOf(state.tournament.host()).map(EventRecords.Record::name)
                .filter(name -> !name.isBlank()).orElse(Events.nameOf(state, state.tournament.host()));
    }

    static String formatName(EventSettings settings) {
        if (settings.kind().isLimited()) {
            return "";
        }
        return FormatPresets.byId(settings.formatId()).map(format -> format.displayName()).orElse(settings.formatId());
    }

}
