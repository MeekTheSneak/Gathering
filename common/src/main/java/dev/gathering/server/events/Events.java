package dev.gathering.server.events;

import dev.gathering.block.PodSignup;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.match.MatchState;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Round;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.DraftedPool;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.DeckCheck;
import dev.gathering.server.PodLobbies;
import dev.gathering.server.PodSignups;
import dev.gathering.server.TablesApart;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tournaments running in the world.
 * <p>The rules are {@link Tournament}'s; this is where they meet tables, players, decks and time.
 * Every change goes through one method that applies it, saves the event and tells everybody in
 * it, so no path changes an event without the others hearing.
 * <p>The mod runs the structure and nothing about play. It seats players, starts their match
 * in the event's format, suggests the result the table saw, and counts the round clock and the
 * extra turns - it never decides who won a game.
 * <p>Server thread only.
 */
public final class Events {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** Ticks in a minute, for what is still counted in ticks. */
    static final long MINUTE = 20L * 60L;

    /** Milliseconds in a minute. */
    static final long MINUTE_MILLIS = 60_000L;

    /** How long a player may be gone from a round before their match is conceded: five minutes. */
    static final long GRACE_MILLIS = 5L * MINUTE_MILLIS;

    /**
     * How long after a round's last result the next round is paired, in server ticks: long enough
     * to read it. A pause, not a clock anybody plays against, so it is counted in ticks.
     */
    static final int PAUSE_BEFORE_NEXT_ROUND = 20 * 15;

    /**
     * The most real time one server tick may count for. A server that stalls, or a single-player
     * game paused on its menu, resumes with a long gap since its last tick; that gap is the
     * server not running, and a round's clock does not run while the server does not.
     */
    static final long LONGEST_TICK_MILLIS = 1_000L;

    private static Map<UUID, EventState> events;

    /** When each absent player was first seen gone from an unfinished match, in wall-clock milliseconds. */
    private static final Map<UUID, Long> goneSince = new HashMap<>();

    /** Where real time comes from, in nanoseconds. Replaced only by the in-world tests. */
    static java.util.function.LongSupplier clock = System::nanoTime;

    /** Wall-clock time, in milliseconds, for grace periods. Replaced only by the in-world tests. */
    static java.util.function.LongSupplier wallClock = System::currentTimeMillis;

    private static long lastTickNanos;

    private Events() {
    }

    /** Forgets everything, for a server that is stopping. */
    public static void clear() {
        events = null;
        goneSince.clear();
        lastTickNanos = 0;
        EventViews.forgetBudgets();
    }

    private static Map<UUID, EventState> events() {
        if (events == null) {
            events = new LinkedHashMap<>(EventStore.readAll());
        }
        return events;
    }

    public static Collection<EventState> all() {
        return List.copyOf(events().values());
    }

    public static Optional<EventState> get(UUID id) {
        return Optional.ofNullable(events().get(id));
    }

    /** The unfinished event this player is in, if any. */
    public static Optional<EventState> of(UUID player) {
        return events().values().stream()
                .filter(state -> !state.tournament.isOver())
                .filter(state -> state.tournament.entrant(player).map(entrant -> !entrant.isDropped()).orElse(false))
                .findFirst();
    }

    /** The unfinished event using this table, if any. */
    public static Optional<EventState> atTable(ServerLevel level, BlockPos origin) {
        String dimension = level.dimension().location().toString();
        return events().values().stream()
                .filter(state -> !state.tournament.isOver() && dimension.equals(state.dimension))
                .filter(state -> state.tables.contains(origin))
                .findFirst();
    }

    // ------------------------------------------------------------------ hosting and joining

    /**
     * Creates an event at the long table this player is standing at, which becomes its tables.
     *
     * @return the event, or empty with the host told why
     */
    public static Optional<EventState> create(ServerPlayer host, BlockPos clicked, String name, EventSettings settings) {
        ServerLevel level = host.serverLevel();
        String problem = settings.problem().orElse(null);
        if (problem != null) {
            host.sendSystemMessage(Component.translatable(problem));
            return Optional.empty();
        }
        if (settings.kind() == EventSettings.Kind.CONSTRUCTED && FormatPresets.byId(settings.formatId()).isEmpty()) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.needs_a_format"));
            return Optional.empty();
        }
        Optional<String> hosting = EventRecords.whyNotHost(host.getUUID());
        if (hosting.isPresent()) {
            host.sendSystemMessage(Component.translatable(hosting.get()));
            return Optional.empty();
        }
        BlockPos origin = TableBlock.entityAt(level, clicked).map(TableBlockEntity::getBlockPos).orElse(null);
        if (origin == null) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.at_a_table"));
            return Optional.empty();
        }
        List<BlockPos> tables = orderedTables(level, origin);
        for (BlockPos table : tables) {
            if (atTable(level, table).isPresent()) {
                host.sendSystemMessage(Component.translatable("message.gathering.event.tables_taken"));
                return Optional.empty();
            }
        }
        Tournament tournament = Tournament.create(UUID.randomUUID(), cleanName(name, host), host.getUUID(), settings);
        EventState state = new EventState(tournament, level.dimension().location().toString());
        state.tables.addAll(tables);
        events().put(tournament.id(), state);
        state.log(host.getUUID(), "create", tournament.name());
        EventRecords.hosted(host.getUUID());
        changed(level.getServer(), state);
        host.sendSystemMessage(Component.translatable("message.gathering.event.created", tournament.name(), tables.size()));
        return Optional.of(state);
    }

    /** Adds the long table this table is part of to an event's tables. Host only. */
    public static void addTables(ServerPlayer host, UUID eventId, BlockPos clicked) {
        EventState state = hosted(host, eventId).orElse(null);
        ServerLevel level = host.serverLevel();
        if (state == null || !level.dimension().location().toString().equals(state.dimension)) {
            return;
        }
        if (state.tournament.isOver()) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.already_over"));
            return;
        }
        BlockPos origin = nearestTable(level, clicked).orElse(null);
        if (origin == null) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.at_a_table"));
            return;
        }
        int added = 0;
        for (BlockPos table : orderedTables(level, origin)) {
            if (!state.tables.contains(table) && atTable(level, table).isEmpty()) {
                state.tables.add(table);
                added++;
            }
        }
        changed(level.getServer(), state);
        host.sendSystemMessage(Component.translatable("message.gathering.event.tables_added", added, state.tables.size()));
    }

    /** The table at this position, or the nearest one within a few blocks of somebody standing here. */
    static Optional<BlockPos> nearestTable(ServerLevel level, BlockPos near) {
        Optional<BlockPos> direct = TableBlock.entityAt(level, near).map(TableBlockEntity::getBlockPos);
        if (direct.isPresent()) {
            return direct;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(near.offset(-3, -2, -3), near.offset(3, 1, 3))) {
            Optional<BlockPos> found = TableBlock.entityAt(level, pos).map(TableBlockEntity::getBlockPos);
            if (found.isPresent() && pos.distSqr(near) < bestDistance) {
                bestDistance = pos.distSqr(near);
                best = found.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<BlockPos> orderedTables(ServerLevel level, BlockPos origin) {
        return TablesApart.tablesTouching(level, origin).stream().map(TableBlockEntity::getBlockPos)
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getX()).thenComparingInt(pos -> pos.getZ())).toList();
    }

    private static String cleanName(String name, ServerPlayer host) {
        // Drawn in everybody's event list, so cleaned as any text one player shows another is.
        String cleaned = dev.gathering.core.game.PlayerText.oneLine(name, 40);
        return cleaned == null ? host.getGameProfile().getName() + "'s tournament" : cleaned;
    }

    /**
     * Registers a player. A constructed event with deck registration takes the deck in their hand,
     * checked against the format and, when locked, kept as the list they must play.
     */
    public static void register(ServerPlayer player, UUID eventId) {
        EventState state = get(eventId).orElse(null);
        if (state == null) {
            return;
        }
        if (of(player.getUUID()).filter(other -> other != state).isPresent()) {
            player.sendSystemMessage(Component.translatable("message.gathering.event.in_another"));
            return;
        }
        if (state.registrationPoint != null && (!player.serverLevel().dimension().location().toString().equals(state.dimension)
                // An entity's own distance, which Sable corrects for a point on a moving structure;
                // comparing block positions does not.
                || player.distanceToSqr(Vec3.atCenterOf(state.registrationPoint))
                        >= EventState.AT_REGISTRATION * EventState.AT_REGISTRATION)) {
            BlockPos at = BlockPos.containing(dev.gathering.platform.WorldSpace.get()
                    .centerInWorld(player.serverLevel(), state.registrationPoint));
            player.sendSystemMessage(Component.translatable("message.gathering.event.go_to_registration",
                    at.getX(), at.getY(), at.getZ()));
            EventPointers.pointTo(player, at);
            return;
        }
        EventSettings settings = state.tournament.settings();
        DeckComponent deck = null;
        if (settings.kind() == EventSettings.Kind.CONSTRUCTED && settings.decks() != EventSettings.DeckRegistration.OFF) {
            deck = DeckItem.deckOf(player.getMainHandItem()).orElse(null);
            if (deck == null) {
                player.sendSystemMessage(Component.translatable("message.gathering.event.hold_your_deck"));
                return;
            }
            FormatPreset format = FormatPresets.byId(settings.formatId()).orElse(null);
            DeckCheck.Answer answer = DeckCheck.nowOrSoon(deck, format, null);
            if (answer instanceof DeckCheck.Answer.NotYet) {
                player.sendSystemMessage(Component.translatable("message.gathering.event.deck_checking"));
                return;
            }
            var result = ((DeckCheck.Answer.Known) answer).result().orElse(null);
            if (result != null && !result.isLegal()) {
                player.sendSystemMessage(Component.translatable("message.gathering.event.deck_illegal",
                        format.displayName()));
                result.errors().stream().limit(4).forEach(issue ->
                        player.sendSystemMessage(Component.literal(" - " + issue.message())));
                return;
            }
        }
        if (!apply(player, state, tournament -> tournament.register(Entrant.registering(
                player.getUUID(), player.getGameProfile().getName(), EventRecords.seedOf(player.getUUID()))))) {
            return;
        }
        state.log(player.getUUID(), "register", player.getGameProfile().getName());
        if (deck != null) {
            state.decks.put(player.getUUID(), deck);
        }
        save(state);
        player.sendSystemMessage(Component.translatable(deck != null && settings.decks() == EventSettings.DeckRegistration.LOCKED
                ? "message.gathering.event.registered_locked" : "message.gathering.event.registered", state.tournament.name()));
    }

    public static void withdraw(ServerPlayer player, UUID eventId) {
        EventState state = get(eventId).orElse(null);
        if (state == null) {
            return;
        }
        if (apply(player, state, tournament -> tournament.drop(player.getUUID()))) {
            state.log(player.getUUID(), "withdraw", player.getGameProfile().getName());
            if (!state.tournament.isRegistered(player.getUUID())) {
                state.decks.remove(player.getUUID());
                state.pools.remove(player.getUUID());
                state.allocated.remove(player.getUUID());
            }
            save(state);
            player.sendSystemMessage(Component.translatable("message.gathering.event.left", state.tournament.name()));
        }
    }

    public static void checkIn(ServerPlayer player, UUID eventId) {
        get(eventId).ifPresent(state -> {
            if (apply(player, state, tournament -> tournament.checkIn(player.getUUID()))) {
                player.sendSystemMessage(Component.translatable("message.gathering.event.checked_in"));
            }
        });
    }

    // ------------------------------------------------------------------ the host's controls

    /** The host marks where they are standing as the place registrations are taken. */
    public static void markRegistration(ServerPlayer host, UUID eventId) {
        hosted(host, eventId).ifPresent(state -> {
            if (!host.serverLevel().dimension().location().toString().equals(state.dimension)) {
                return;
            }
            state.registrationPoint = host.blockPosition();
            changed(host.getServer(), state);
            host.sendSystemMessage(Component.translatable("message.gathering.event.registration_marked"));
        });
    }

    public static void openCheckIn(ServerPlayer host, UUID eventId) {
        hosted(host, eventId).ifPresent(state -> apply(host, state, Tournament::openCheckIn));
    }

    /**
     * Registration is over. A constructed event starts its first round at once; a limited one
     * seats everybody at the home table and opens its packs to a sign-up.
     */
    public static void begin(ServerPlayer host, UUID eventId) {
        EventState state = hosted(host, eventId).orElse(null);
        if (state == null) {
            return;
        }
        MinecraftServer server = host.getServer();
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level == null || state.tables.isEmpty()) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.at_a_table"));
            return;
        }
        if (state.tournament.settings().kind().isLimited()) {
            // Asked before anything changes: a draft or sealed event is one pod at its home long
            // table, and a pod that cannot seat everybody registered is refused here rather
            // than begun with some of them left standing.
            int seats = TableClusters.touching(level, state.tables.get(0)).seats().size();
            int players = (int) state.tournament.entrants().stream()
                    .filter(entrant -> !state.tournament.settings().largeEvent()
                            || state.tournament.checkedIn().contains(entrant.id()))
                    .count();
            var pod = state.tournament.settings().pod();
            int most = pod == null ? seats : Math.min(seats, pod.mostPlayers());
            if (players > most) {
                host.sendSystemMessage(Component.translatable("message.gathering.event.not_enough_seats", most, players));
                return;
            }
        }
        if (!apply(host, state, Tournament::beginPreparing)) {
            return;
        }
        state.log(host.getUUID(), "begin", state.tournament.entrants().size() + " players");
        if (!state.tournament.settings().kind().isLimited()) {
            Tournament ready = state.tournament;
            for (Entrant entrant : ready.stillIn()) {
                ready = ready.markReady(entrant.id());
            }
            state.tournament = ready;
            startPlay(server, state);
            return;
        }
        // Everybody together at the home table, which is one surface for the draft.
        BlockPos home = state.tables.get(0);
        TablesApart.set(level, home, false);
        List<Entrant> players = state.tournament.stillIn();
        int seats = TableClusters.at(level, home).seats().size();
        if (players.size() > seats) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.not_enough_seats", seats, players.size()));
        }
        for (int index = 0; index < Math.min(seats, players.size()); index++) {
            seatAt(level, home, index, players.get(index).id(), true);
        }
        PodSignups.Created created = PodSignups.create(level, home, state.tournament.host(), state.tournament.settings().pod());
        if (created != PodSignups.Created.OPEN) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.pod_not_opened"));
        } else {
            state.podOpened = true;
            for (Entrant entrant : players) {
                ServerPlayer player = server.getPlayerList().getPlayer(entrant.id());
                if (player != null) {
                    PodLobbies.show(player, home);
                }
            }
        }
        changed(server, state);
    }

    /**
     * A limited player's deck is built: the deck in their hand, from this event's pool, checked as
     * limited. Its pool is registered to them, and they are ready.
     */
    public static void ready(ServerPlayer player, UUID eventId) {
        EventState state = get(eventId).orElse(null);
        if (state == null || state.tournament.phase() != Tournament.Phase.PREPARING) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        DeckComponent deck = DeckItem.deckOf(held).orElse(null);
        DraftedPool pool = held.get(GatheringComponents.POOL.get());
        // The pool has to be the one this event handed this player: named for this event, and
        // the same cards the server recorded giving them. A pool item alone proves neither - one
        // from an earlier event at the same table, or somebody else's, looks just the same.
        List<CardComponent> given = state.allocated.get(player.getUUID());
        boolean fromThisEvent = pool != null && pool.fromPod().equals(state.podName())
                && given != null && sameCards(given, pool.cards());
        if (deck == null || !fromThisEvent) {
            player.sendSystemMessage(Component.translatable("message.gathering.event.hold_your_pool"));
            return;
        }
        DeckCheck.Answer answer = DeckCheck.nowOrSoon(deck, FormatPresets.LIMITED, pool);
        if (answer instanceof DeckCheck.Answer.NotYet) {
            player.sendSystemMessage(Component.translatable("message.gathering.event.deck_checking"));
            return;
        }
        var result = ((DeckCheck.Answer.Known) answer).result().orElse(null);
        if (result != null && !result.isLegal()) {
            player.sendSystemMessage(Component.translatable("message.gathering.event.deck_illegal",
                    FormatPresets.LIMITED.displayName()));
            result.errors().stream().limit(4).forEach(issue ->
                    player.sendSystemMessage(Component.literal(" - " + issue.message())));
            return;
        }
        if (apply(player, state, tournament -> tournament.markReady(player.getUUID()))) {
            state.pools.put(player.getUUID(), pool);
            state.log(player.getUUID(), "ready", deck.entries().size() + " cards");
            save(state);
            player.sendSystemMessage(Component.translatable("message.gathering.event.ready"));
            if (state.tournament.everyoneIsReady()) {
                startPlay(player.getServer(), state);
            }
        }
    }

    /** The host starts play now, whoever is not ready. Also what the build clock does at time. */
    public static void startNow(ServerPlayer host, UUID eventId) {
        hosted(host, eventId).ifPresent(state -> {
            if (state.tournament.phase() != Tournament.Phase.PREPARING) {
                return;
            }
            ServerLevel level = levelOf(host.getServer(), state).orElse(null);
            if (level != null && podStillRunning(level, state)) {
                // Starting now skips the building, never the packs: they are still in a sign-up
                // or a draft, and play cannot begin at a table they are on.
                host.sendSystemMessage(Component.translatable("message.gathering.event.pod_still_running"));
                return;
            }
            state.log(host.getUUID(), "start_now", "");
            startPlay(host.getServer(), state);
        });
    }

    /** Whether the event's packs are still being signed up for, opened or drafted at its home table. */
    static boolean podStillRunning(ServerLevel level, EventState state) {
        if (!state.tournament.settings().kind().isLimited() || state.tables.isEmpty()) {
            return false;
        }
        return TableBlock.entityAt(level, state.tables.get(0))
                .map(entity -> entity.hasSignup() || entity.hasPod() || entity.isOpening()).orElse(false);
    }

    /** A player reports their match, as they see it. */
    public static void report(ServerPlayer player, UUID eventId, MatchResult asTheySeeIt) {
        get(eventId).ifPresent(state -> {
            if (apply(player, state, tournament -> tournament.report(player.getUUID(), asTheySeeIt))) {
                state.log(player.getUUID(), "report", asTheySeeIt.winsA() + "-" + asTheySeeIt.winsB() + "-" + asTheySeeIt.draws());
                Pairing pairing = state.tournament.currentRound().flatMap(round -> round.pairingOf(player.getUUID()))
                        .orElse(null);
                if (pairing != null && pairing.isDisputed()) {
                    tell(player.getServer(), state.tournament.host(), Component.translatable(
                            "message.gathering.event.dispute", pairing.table()));
                }
                afterResult(player.getServer(), state);
            }
        });
    }

    /** The host settles a table's result, from the first player's chair. */
    public static void settle(ServerPlayer host, UUID eventId, int table, MatchResult fromFirst) {
        hosted(host, eventId).ifPresent(state -> {
            if (apply(host, state, tournament -> tournament.settle(table, fromFirst))) {
                state.log(host.getUUID(), "settle", "table " + table + ": " + fromFirst.winsA() + "-" + fromFirst.winsB()
                        + "-" + fromFirst.draws());
                afterResult(host.getServer(), state);
            }
        });
    }

    public static void dropPlayer(ServerPlayer host, UUID eventId, UUID player) {
        hosted(host, eventId).ifPresent(state -> {
            if (apply(host, state, tournament -> tournament.drop(player))) {
                state.log(host.getUUID(), "drop", nameOf(state, player));
                afterResult(host.getServer(), state);
            }
        });
    }

    public static void cancel(ServerPlayer host, UUID eventId) {
        EventState state = get(eventId).orElse(null);
        if (state == null) {
            return;
        }
        if (!state.tournament.host().equals(host.getUUID()) && !host.hasPermissions(2)) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.host_only"));
            return;
        }
        if (state.tournament.isOver()) {
            // An event that has ended has nothing left to call off - and its tables may be a
            // newer event's by now, which a second clean-up would tear down.
            host.sendSystemMessage(Component.translatable("message.gathering.event.already_over"));
            return;
        }
        state.tournament = state.tournament.cancel();
        state.log(host.getUUID(), "cancel", "");
        finishUp(host.getServer(), state);
    }

    private static Optional<EventState> hosted(ServerPlayer host, UUID eventId) {
        EventState state = get(eventId).orElse(null);
        if (state == null) {
            return Optional.empty();
        }
        if (!state.tournament.host().equals(host.getUUID()) && !host.hasPermissions(2)) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.host_only"));
            return Optional.empty();
        }
        return Optional.of(state);
    }

    // ------------------------------------------------------------------ play

    /** Pairs round one and seats everybody. */
    private static void startPlay(MinecraftServer server, EventState state) {
        if (state.tournament.phase() != Tournament.Phase.PREPARING) {
            return;
        }
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level != null && podStillRunning(level, state)) {
            tell(server, state.tournament.host(), Component.translatable("message.gathering.event.pod_still_running"));
            return;
        }
        if (level != null && !state.tables.isEmpty()) {
            // Whatever the draft left at the home table is over now.
            clearTables(level, state);
            // Every long table in the venue played apart, not only the first: a second row left
            // joined puts two pairings on one surface.
            for (BlockPos table : tablesStillOurs(level, state)) {
                if (TablesApart.set(level, table, true) == TablesApart.Result.IN_USE) {
                    tell(server, state.tournament.host(), Component.translatable("message.gathering.event.tables_in_use",
                            state.numberOf(table)));
                    return;
                }
            }
        }
        try {
            state.tournament = state.tournament.startSwiss();
        } catch (IllegalArgumentException refused) {
            tell(server, state.tournament.host(), Component.translatable(refused.getMessage()));
            return;
        }
        state.roundMillis = 0;
        state.completeForTicks = -1;
        state.log(null, "round", "1");
        seatRound(server, state);
        changed(server, state);
    }

    /** Seats every pairing of the current round at its table and starts the match there. */
    static void seatRound(MinecraftServer server, EventState state) {
        ServerLevel level = levelOf(server, state).orElse(null);
        Round round = state.tournament.currentRound().orElse(null);
        if (level == null || round == null) {
            return;
        }
        clearTables(level, state);
        state.seen.clear();
        FormatPreset format = state.tournament.settings().kind().isLimited()
                ? FormatPresets.LIMITED
                : FormatPresets.byId(state.tournament.settings().formatId()).orElse(FormatPresets.defaultPreset());
        for (Pairing pairing : round.pairings()) {
            if (pairing.isBye()) {
                tell(server, pairing.a(), Component.translatable("message.gathering.event.you_have_a_bye", round.number()));
                continue;
            }
            BlockPos table = state.table(pairing.table()).orElse(null);
            if (table == null) {
                tell(server, pairing.a(), Component.translatable("message.gathering.event.no_table_free", pairing.table()));
                tell(server, pairing.b(), Component.translatable("message.gathering.event.no_table_free", pairing.table()));
                continue;
            }
            seatAt(level, table, 0, pairing.a(), false);
            seatAt(level, table, 1, pairing.b(), false);
            // In the cut the higher Swiss seed chooses for the first game (MTR 2.2); in the Swiss
            // rounds it is left to chance.
            SeatId higherSeed = round.elimination()
                    ? new SeatId(state.tournament.firstSeededHigher(pairing) ? 0 : 1)
                    : null;
            TableSessions.Outcome outcome = TableSessions.start(level, table,
                    new MatchRules(format, state.tournament.settings().bestOf(), round.elimination()), null, higherSeed);
            if (outcome == TableSessions.Outcome.STARTED) {
                TableBlock.entityAt(level, table).ifPresent(entity -> entity.formatWasChosen(true));
                dev.gathering.server.TableBroadcast.sendToTable(level, table);
            }
            String a = nameOf(state, pairing.a());
            String b = nameOf(state, pairing.b());
            tell(server, pairing.a(), Component.translatable("message.gathering.event.paired", round.number(), pairing.table(), b));
            tell(server, pairing.b(), Component.translatable("message.gathering.event.paired", round.number(), pairing.table(), a));
        }
    }

    /**
     * Moves a player into a seat at this table, claiming it for them.
     * <p>Moved straight there when they are already sitting at the same long table, as after a
     * draft, and told the table's number otherwise, with a pointer showing the way - the owner's
     * rule for a venue. The one exception is gathering everybody for a draft or sealed event,
     * which also brings in whoever is standing nearby.
     *
     * @param bringFromNearby whether somebody within a few tables is moved too, not only
     *                        somebody already sitting at this long table
     */
    static void seatAt(ServerLevel level, BlockPos table, int seatIndex, UUID player, boolean bringFromNearby) {
        List<SeatAnchor> seats = TableClusters.at(level, table).seats();
        if (seatIndex >= seats.size()) {
            return;
        }
        SeatAnchor seat = seats.get(seatIndex);
        // Out of wherever they were sitting in the event's tables first: one chair each.
        boolean atThisLongTable = false;
        for (BlockPos anywhere : TablesApart.tablesTouching(level, table).stream().map(TableBlockEntity::getBlockPos).toList()) {
            atThisLongTable |= TableSeats.seatOf(level, anywhere, player).isPresent();
            TableSeats.leave(level, anywhere, player);
        }
        Optional<UUID> sitting = TableBlock.entityAt(level, TableClusters.blockPos(table, seat.cell()))
                .flatMap(entity -> entity.occupantOf(seat.side()));
        sitting.filter(other -> !other.equals(player)).ifPresent(other -> TableSeats.leave(level, table, other));
        TableSeats.take(level, table, seat.cell(), seat.side(), player);
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
        if (online == null) {
            return;
        }
        BlockPos stand = TableClusters.seatPos(table, seat);
        boolean near = online.serverLevel() == level && online.distanceToSqr(Vec3.atCenterOf(table)) < 24 * 24;
        if (atThisLongTable || (bringFromNearby && near)) {
            Chair chair = chairInWorld(level, table, seat);
            online.teleportTo(level, chair.where().x, chair.where().y, chair.where().z, chair.yaw(), LOOKING_AT_THE_TABLE);
        } else {
            EventPointers.pointTo(online, stand);
        }
    }

    /** Where a player moved into a seat stands, and which way they face. */
    record Chair(Vec3 where, float yaw) {
    }

    /**
     * Where a seat at this table is in the world, facing the table.
     * <p>In the world, not at the seat's block position: a table on a Create Aeronautics vehicle
     * keeps its blocks out in Sable's own region, and teleporting to those coordinates put the
     * player there instead of beside the table. See WorldSpace.
     */
    static Chair chairInWorld(ServerLevel level, BlockPos table, SeatAnchor seat) {
        BlockPos stand = TableClusters.seatPos(table, seat);
        var space = dev.gathering.platform.WorldSpace.get();
        return new Chair(space.toWorld(level, new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5)),
                facingTheTable(seat.side()) + space.yawOf(level, table));
    }

    /** How far down a player moved into a seat looks, in degrees: at the felt, not the horizon. */
    private static final float LOOKING_AT_THE_TABLE = 35f;

    /**
     * The yaw that looks across the table from a chair on this side. Minecraft's yaw is 0 for
     * south, 90 for west, 180 for north and -90 for east - so a chair on the north edge looks
     * south, over the table.
     */
    static float facingTheTable(dev.gathering.core.table.Side side) {
        return switch (side) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case EAST -> 90f;
            case WEST -> -90f;
        };
    }

    /** Ends anything left running at the event's tables, handing decks back. */
    private static void clearTables(ServerLevel level, EventState state) {
        for (BlockPos table : tablesStillOurs(level, state)) {
            TableBlock.entityAt(level, table).ifPresent(entity -> {
                if (entity.hasSession()) {
                    TableSessions.end(level, table, entity, new SeatId(0), "event_round_over");
                }
                if (entity.match().isPresent() || !entity.heldDecks().isEmpty()) {
                    TableSessions.returnDecks(level, table, entity);
                    entity.endSession();
                }
            });
        }
    }

    /**
     * After a result. The next round is not scheduled from here: the round clock notices a
     * complete round however it became complete - a report, the host, a drop, a player gone too
     * long, time - and pairs the next one after a pause, including after a restart.
     */
    private static void afterResult(MinecraftServer server, EventState state) {
        changed(server, state);
    }

    /**
     * This event's tables that no other unfinished event has claimed since.
     * <p>Coordinates are not ownership. An event that has ended gives its tables up, and a newer
     * event may be playing on them - so anything an event does to its tables asks this first.
     */
    static List<BlockPos> tablesStillOurs(ServerLevel level, EventState state) {
        return state.tables.stream()
                .filter(table -> atTable(level, table).map(owner -> owner == state).orElse(true))
                .toList();
    }

    private static void nextRound(MinecraftServer server, UUID eventId) {
        EventState state = get(eventId).orElse(null);
        if (state == null || state.tournament.isOver()) {
            return;
        }
        Round round = state.tournament.currentRound().orElse(null);
        if (round == null || !round.isComplete()) {
            return;
        }
        // Anybody gone since the last round is dropped before the next is paired.
        Tournament tournament = state.tournament;
        for (Entrant entrant : tournament.stillIn()) {
            if (server.getPlayerList().getPlayer(entrant.id()) == null) {
                tournament = tournament.drop(entrant.id());
            }
        }
        state.tournament = tournament.nextRound();
        state.roundMillis = 0;
        state.completeForTicks = -1;
        if (state.tournament.isOver()) {
            finishUp(server, state);
            return;
        }
        state.log(null, "round", String.valueOf(state.tournament.currentRound().map(Round::number).orElse(0)));
        seatRound(server, state);
        changed(server, state);
    }

    private static void finishUp(MinecraftServer server, EventState state) {
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level != null && !state.tables.isEmpty()) {
            List<BlockPos> ours = tablesStillOurs(level, state);
            clearTables(level, state);
            BlockPos home = state.tables.get(0);
            if (ours.contains(home)) {
                // A draft still running is ended through its own return path: every contributor
                // gets back what their packs held, as when its table is broken.
                TableBlock.entityAt(level, home).filter(TableBlockEntity::hasPod)
                        .ifPresent(entity -> dev.gathering.server.PodEvents.tableGoneMidDraft(level, home, entity));
                TableBlock.entityAt(level, home).filter(TableBlockEntity::hasSignup)
                        .ifPresent(entity -> PodSignups.handBackEverything(level, home, entity, "pod_signup_cancelled"));
            }
            for (BlockPos table : ours) {
                TablesApart.set(level, table, false);
            }
        }
        state.log(null, state.tournament.phase().key(), "");
        if (state.tournament.phase() == Tournament.Phase.FINISHED) {
            EventRecords.finished(state.tournament, state.playedAtTable, System.currentTimeMillis());
            EventPrizes.handOut(server, state);
            List<UUID> places = state.tournament.finalPlaces();
            if (!places.isEmpty()) {
                Component winner = Component.literal(nameOf(state, places.get(0)));
                for (Entrant entrant : state.tournament.entrants()) {
                    tell(server, entrant.id(), Component.translatable("message.gathering.event.won_by",
                            state.tournament.name(), winner, places.indexOf(entrant.id()) + 1));
                }
            }
        } else {
            EventPrizes.returnToHost(server, state);
            for (Entrant entrant : state.tournament.entrants()) {
                tell(server, entrant.id(), Component.translatable("message.gathering.event.cancelled", state.tournament.name()));
            }
        }
        changed(server, state);
    }

    // ------------------------------------------------------------------ hooks

    /** Every server tick: clocks, the build timer, and players gone too long. */
    public static void tick(MinecraftServer server) {
        long now = clock.getAsLong();
        long elapsed = lastTickNanos == 0 ? 50L : Math.min(LONGEST_TICK_MILLIS, Math.max(0L, (now - lastTickNanos) / 1_000_000L));
        lastTickNanos = now;
        if (events == null && server.getTickCount() % 100 != 0) {
            return;
        }
        for (EventState state : all()) {
            contained(server, state, () -> advance(server, state, elapsed));
        }
    }

    /**
     * Runs one event's share of a tick, keeping anything that goes wrong in it to that event.
     * <p>Uncaught, an exception here went out of the server's tick and took the whole server down -
     * every world and every table - over one tournament's clock. The same failure on every tick is
     * logged and told to the host once, not twenty times a second.
     */
    static void contained(MinecraftServer server, EventState state, Runnable work) {
        try {
            work.run();
            state.lastFailure = null;
        } catch (RuntimeException wentWrong) {
            String what = String.valueOf(wentWrong);
            if (!what.equals(state.lastFailure)) {
                state.lastFailure = what;
                LOGGER.error("The tournament {} ({}) could not run its clock this tick", state.tournament.name(),
                        state.tournament.id(), wentWrong);
                tell(server, state.tournament.host(), Component.translatable("message.gathering.event.clock_failed",
                        state.tournament.name()));
            }
        }
    }

    /** One tick's worth of an event's clocks, counting this much real time. */
    private static void advance(MinecraftServer server, EventState state, long elapsedMillis) {
        Tournament tournament = state.tournament;
        if (tournament.isOver()) {
            return;
        }
        switch (tournament.phase()) {
            case PREPARING -> buildTick(server, state, elapsedMillis);
            case SWISS, CUT -> roundTick(server, state, elapsedMillis);
            default -> { }
        }
    }

    private static void buildTick(MinecraftServer server, EventState state, long elapsedMillis) {
        if (!state.tournament.settings().kind().isLimited() || !state.podOpened) {
            return;
        }
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level == null || state.tables.isEmpty()) {
            return;
        }
        if (!level.isLoaded(state.tables.get(0)) || podStillRunning(level, state)) {
            // An unloaded home table says nothing about whether its draft has finished.
            return;
        }
        long before = state.buildMillis / MINUTE_MILLIS;
        state.buildMillis += elapsedMillis;
        if (state.buildMillis / MINUTE_MILLIS != before) {
            changed(server, state);
        }
        if (state.buildMillis >= state.tournament.settings().buildMinutes() * MINUTE_MILLIS) {
            for (Entrant entrant : state.tournament.stillIn()) {
                tell(server, entrant.id(), Component.translatable("message.gathering.event.build_time_up"));
            }
            startPlay(server, state);
        }
    }

    private static void roundTick(MinecraftServer server, EventState state, long elapsedMillis) {
        Round round = state.tournament.currentRound().orElse(null);
        if (round == null) {
            return;
        }
        if (round.isComplete()) {
            if (state.completeForTicks < 0) {
                state.completeForTicks = 0;
                for (Entrant entrant : state.tournament.stillIn()) {
                    tell(server, entrant.id(), Component.translatable("message.gathering.event.round_complete", round.number()));
                }
                return;
            }
            if (++state.completeForTicks >= PAUSE_BEFORE_NEXT_ROUND) {
                nextRound(server, state.tournament.id());
            }
            return;
        }
        state.completeForTicks = -1;
        long before = state.roundMillis / 30_000L;
        state.roundMillis += elapsedMillis;
        if (!round.timeCalled() && state.roundMillis >= state.tournament.settings().roundMinutes() * MINUTE_MILLIS) {
            state.tournament = state.tournament.callTime();
            for (Pairing pairing : state.tournament.currentRound().orElseThrow().pairings()) {
                if (!pairing.isConfirmed()) {
                    Component line = Component.translatable("message.gathering.event.time",
                            state.tournament.settings().extraTurns());
                    tell(server, pairing.a(), line);
                    tell(server, pairing.b(), line);
                }
            }
            changed(server, state);
        }
        long now = wallClock.getAsLong();
        for (Pairing pairing : round.pairings()) {
            if (pairing.isConfirmed() || pairing.isBye()) {
                continue;
            }
            for (UUID player : new UUID[] {pairing.a(), pairing.b()}) {
                if (server.getPlayerList().getPlayer(player) == null) {
                    // Somebody who is not here is counted as gone from when it is first noticed,
                    // not only from a disconnect this server saw: after a restart, a player who
                    // never comes back still runs out their grace.
                    goneSince.putIfAbsent(player, now);
                }
                Long gone = goneSince.get(player);
                if (gone != null && now - gone >= GRACE_MILLIS && state.tournament.currentRound().flatMap(r -> r.pairingOf(player))
                        .map(p -> !p.isConfirmed()).orElse(false)) {
                    state.tournament = state.tournament.settle(pairing.table(), MatchResult.conceded(pairing.a().equals(player), state.tournament.settings().bestOf()));
                    state.log(null, "gone", nameOf(state, player));
                    tell(server, pairing.opponentOf(player), Component.translatable("message.gathering.event.opponent_gone"));
                    afterResult(server, state);
                }
            }
        }
        if (state.roundMillis / 30_000L != before) {
            changed(server, state);
        }
    }

    /** A turn passed at a table: counts toward extra turns, and ends the match when they are over. */
    public static void turnPassed(ServerLevel level, BlockPos origin) {
        EventState state = atTable(level, origin).orElse(null);
        if (state == null || (state.tournament.phase() != Tournament.Phase.SWISS && state.tournament.phase() != Tournament.Phase.CUT)) {
            return;
        }
        int table = state.numberOf(origin);
        Round round = state.tournament.currentRound().orElse(null);
        if (table == 0 || round == null || !round.timeCalled()) {
            return;
        }
        state.tournament = state.tournament.turnPassed(table);
        state.playedAtTable.add(round.number() + ":" + table);
        Pairing pairing = state.tournament.currentRound().flatMap(r -> r.atTable(table)).orElse(null);
        if (pairing == null) {
            return;
        }
        int taken = pairing.turnsAfterTime();
        int allowed = state.tournament.settings().extraTurns();
        if (state.tournament.extraTurnsAreOver(table)) {
            int[] wins = winsAt(level, origin, pairing);
            var session = TableSessions.sessionAt(level, origin).orElse(null);
            int[] life = session == null ? new int[] {0, 0}
                    : new int[] {lifeOf(session.state(), chairsOf(level, origin, pairing)[0]),
                            lifeOf(session.state(), chairsOf(level, origin, pairing)[1])};
            state.tournament = state.tournament.endAtTime(table, wins[0], wins[1], wins[2], session != null, life[0], life[1]);
            boolean recorded = state.tournament.currentRound().flatMap(r -> r.atTable(table)).map(Pairing::isConfirmed).orElse(false);
            // A cut match still tied on games and life is left open for the host, and saying it
            // was recorded would send both players away from a match nobody has decided.
            dev.gathering.server.TableBroadcast.tell(level, origin, Component.translatable(recorded
                    ? "message.gathering.event.match_over_at_time" : "message.gathering.event.cut_tied_at_time"));
            afterResult(level.getServer(), state);
        } else {
            dev.gathering.server.TableBroadcast.tell(level, origin, taken == 0
                    ? Component.translatable("message.gathering.event.turn_zero")
                    : Component.translatable("message.gathering.event.extra_turn", taken, allowed));
            changed(level.getServer(), state);
        }
    }

    /** A game ended at a table: what the table saw, kept to suggest the result. */
    public static void gameEnded(ServerLevel level, BlockPos origin, MatchState match) {
        EventState state = atTable(level, origin).orElse(null);
        if (state == null) {
            return;
        }
        int table = state.numberOf(origin);
        Pairing pairing = state.tournament.currentRound().flatMap(round -> round.atTable(table)).orElse(null);
        if (pairing == null) {
            return;
        }
        SeatId[] chairs = chairsOf(level, origin, pairing);
        state.seen.put(table, new int[] {match.winsFor(chairs[0]), match.winsFor(chairs[1]), match.drawnGames()});
        state.tournament.currentRound().ifPresent(round -> state.playedAtTable.add(round.number() + ":" + table));
        if (match.isDecided() || !match.hasGameToPlay()) {
            for (UUID player : new UUID[] {pairing.a(), pairing.b()}) {
                tell(level.getServer(), player, Component.translatable("message.gathering.event.report_now"));
            }
        }
        changed(level.getServer(), state);
    }

    /** The result the table saw, from the first player's chair, if a game ended there this round. */
    public static Optional<MatchResult> suggested(EventState state, int table) {
        int[] seen = state.seen.get(table);
        return seen == null ? Optional.empty()
                // Drawn games counted too, now that a drawn game is one of the match's games.
                : Optional.of(new MatchResult(seen[0], seen[1], seen.length > 2 ? seen[2] : 0));
    }

    private static int lifeOf(dev.gathering.core.game.GameState game, SeatId seat) {
        return game.hasSeat(seat) ? game.seatState(seat).life() : 0;
    }

    private static int[] winsAt(ServerLevel level, BlockPos origin, Pairing pairing) {
        MatchState match = TableSessions.matchAt(level, origin).orElse(null);
        if (match == null) {
            return new int[] {0, 0, 0};
        }
        SeatId[] chairs = chairsOf(level, origin, pairing);
        return new int[] {match.winsFor(chairs[0]), match.winsFor(chairs[1]), match.drawnGames()};
    }

    /**
     * The chairs the pairing's two players are playing from at this table, first player first.
     * <p>A round seats the first player in chair 0 and the second in chair 1, and that is where
     * they usually stay - but nothing keeps them there, and results read by chair credited a
     * pair who had swapped with each other's games. The game in progress says who is sitting
     * where; one player found puts the other in the chair left over, and with neither found - or
     * both answering to the same chair, as when one stood up and the other moved across - the
     * chairs the round gave them.
     */
    private static SeatId[] chairsOf(ServerLevel level, BlockPos origin, Pairing pairing) {
        SeatId first = new SeatId(0);
        SeatId second = new SeatId(1);
        var session = TableSessions.sessionAt(level, origin).orElse(null);
        if (session == null) {
            return new SeatId[] {first, second};
        }
        SeatId a = seatedIn(session.state(), pairing.a());
        SeatId b = seatedIn(session.state(), pairing.b());
        if (a != null && b == null) {
            b = a.equals(first) ? second : first;
        } else if (b != null && a == null) {
            a = b.equals(first) ? second : first;
        }
        return a == null || a.equals(b) ? new SeatId[] {first, second} : new SeatId[] {a, b};
    }

    /** The chair this player is sitting in during this game, or null. */
    private static SeatId seatedIn(dev.gathering.core.game.GameState game, UUID player) {
        if (player == null) {
            return null;
        }
        for (SeatId seat : game.seats()) {
            if (game.seatState(seat).player().map(ref -> ref.id().equals(player)).orElse(false)) {
                return seat;
            }
        }
        return null;
    }

    /**
     * Whether a deck going down at this table is refused by a locked registration.
     * <p>A constructed player with a locked list plays that list: the deck's cards, commanders and
     * sideboard together must be the ones registered, however they are split between main and
     * side after sideboarding. A limited player plays from the pool registered when they were
     * ready.
     */
    public static Optional<Component> refusesDeck(ServerLevel level, BlockPos origin, UUID player, DeckComponent deck,
            DraftedPool pool) {
        EventState state = atTable(level, origin).orElse(null);
        if (state == null || !state.tournament.isRegistered(player)) {
            return Optional.empty();
        }
        EventSettings settings = state.tournament.settings();
        if (settings.kind().isLimited()) {
            DraftedPool registered = state.pools.get(player);
            if (registered != null && (pool == null || !registered.equals(pool))) {
                return Optional.of(Component.translatable("message.gathering.event.not_your_pool"));
            }
            return Optional.empty();
        }
        if (settings.decks() != EventSettings.DeckRegistration.LOCKED) {
            return Optional.empty();
        }
        DeckComponent registered = state.decks.get(player);
        if (registered == null) {
            return Optional.empty();
        }
        return sameCards(registered, deck) ? Optional.empty()
                : Optional.of(Component.translatable("message.gathering.event.not_your_deck"));
    }

    static boolean sameCards(DeckComponent registered, DeckComponent deck) {
        return everyCard(registered).equals(everyCard(deck));
    }

    /** Whether two lists hold the same cards, in any order. */
    static boolean sameCards(List<CardComponent> one, List<CardComponent> other) {
        Map<CardIdentity, Integer> counts = new HashMap<>();
        one.forEach(card -> counts.merge(card.toIdentity(), 1, Integer::sum));
        for (CardComponent card : other) {
            Integer left = counts.get(card.toIdentity());
            if (left == null) {
                return false;
            }
            if (left == 1) {
                counts.remove(card.toIdentity());
            } else {
                counts.put(card.toIdentity(), left - 1);
            }
        }
        return counts.isEmpty();
    }

    /**
     * A draft or sealed opening handed this player a pool at this table. Recorded against the
     * event that opened it, when there is one, as the pool that player's Ready is checked against.
     */
    public static void poolHandedOut(ServerLevel level, BlockPos tableOrigin, UUID player, String podName,
            List<CardComponent> cards) {
        if (podName == null || !podName.startsWith(EventState.PREFIX)) {
            return;
        }
        events().values().stream()
                .filter(state -> !state.tournament.isOver() && state.podName().equals(podName))
                .findFirst()
                .ifPresent(state -> {
                    state.allocated.put(player, List.copyOf(cards));
                    state.log(player, "pool", cards.size() + " cards");
                    save(state);
                });
    }

    /** The pod name an event's packs at this table carry, if an unfinished event plays there. */
    public static Optional<String> podNameAt(ServerLevel level, BlockPos tableOrigin) {
        return atTable(level, tableOrigin).map(EventState::podName);
    }

    private static Map<CardIdentity, Integer> everyCard(DeckComponent deck) {
        Map<CardIdentity, Integer> counts = new HashMap<>();
        List<CardComponent> all = new ArrayList<>();
        all.addAll(deck.entries());
        all.addAll(deck.commanders());
        all.addAll(deck.sideboard());
        for (CardComponent card : all) {
            counts.merge(card.toIdentity(), 1, Integer::sum);
        }
        return counts;
    }

    public static void left(ServerPlayer player) {
        EventViews.forget(player.getUUID());
        if (of(player.getUUID()).isPresent()) {
            goneSince.put(player.getUUID(), wallClock.getAsLong());
        }
        // Nobody who is no longer in an unfinished event needs their absence counted. Swept here,
        // on a disconnect, so the map holds only players some event is still waiting on.
        goneSince.keySet().removeIf(gone -> of(gone).isEmpty());
    }

    public static void arrived(ServerPlayer player) {
        goneSince.remove(player.getUUID());
        EventPrizes.arrived(player);
        of(player.getUUID()).ifPresent(state -> EventViews.show(player, state, false));
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Applies a change to an event, and if it is refused tells the player why.
     *
     * @return whether it was applied
     */
    static boolean apply(ServerPlayer player, EventState state, java.util.function.UnaryOperator<Tournament> change) {
        try {
            Tournament after = change.apply(state.tournament);
            if (after == state.tournament || after.equals(state.tournament)) {
                // Nothing changed - a second check-in, a repeated button - so nothing is saved
                // or sent: a repeated request costs a comparison, not a write and a broadcast.
                return true;
            }
            state.tournament = after;
        } catch (IllegalArgumentException refused) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable(refused.getMessage()));
            }
            return false;
        }
        if (player != null) {
            changed(player.getServer(), state);
        }
        return true;
    }

    /** Saves the event and tells everybody in it what it looks like now. */
    static void changed(MinecraftServer server, EventState state) {
        save(state);
        EventViews.broadcast(server, state);
    }

    /** Saves the event, and says whether it is on disk. */
    static boolean save(EventState state) {
        return EventStore.write(state);
    }

    static Optional<ServerLevel> levelOf(MinecraftServer server, EventState state) {
        ResourceLocation location = ResourceLocation.tryParse(state.dimension);
        if (location == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, location)));
    }

    static String nameOf(EventState state, UUID player) {
        return state.tournament.entrant(player).map(Entrant::name).orElse("?");
    }

    static void tell(MinecraftServer server, UUID who, Component message) {
        if (who == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    /** For the in-world tests: puts an event in as if it had been read from the save. */
    public static void putForTesting(EventState state) {
        events().put(state.tournament.id(), state);
    }

    /** For the in-world tests: the numbers over the event's tables brought up to date. */
    public static void labelTablesForTesting(MinecraftServer server, EventState state) {
        EventLabels.update(server, state);
    }

    /** For the in-world tests: an event taken out, as if it had never been. */
    public static void removeForTesting(EventState state) {
        events().remove(state.tournament.id());
    }

    /** For the in-world tests: a fresh event state around a tournament. */
    public static EventState stateForTesting(Tournament tournament, ServerLevel level, List<BlockPos> tables) {
        EventState state = new EventState(tournament, level.dimension().location().toString());
        state.tables.addAll(tables);
        return state;
    }

    /** For the in-world tests: replaces an event's tournament, as a change would. */
    public static void setForTesting(EventState state, Tournament tournament) {
        state.tournament = tournament;
    }

    /** For the in-world tests: seats the current round. */
    public static void seatRoundForTesting(MinecraftServer server, EventState state) {
        seatRound(server, state);
    }

    /** For the in-world tests: the round clock, run forward. */
    public static void runClockForTesting(MinecraftServer server, EventState state, long ticks) {
        for (long tick = 0; tick < ticks; tick++) {
            roundTick(server, state, 50L);
        }
    }

    /** For the in-world tests: the wall clock grace periods are measured by. */
    public static long wallClockForTesting() {
        return wallClock.getAsLong();
    }

    /** For the in-world tests: the pools this event recorded handing out, as a draft or sealed opening does. */
    public static void allocateForTesting(EventState state, UUID player, List<CardComponent> cards) {
        state.allocated.put(player, List.copyOf(cards));
    }

    /** For the in-world tests: an event written to its saved form and read back. */
    public static EventState roundTripForTesting(EventState state) throws java.io.IOException {
        return EventStore.read(EventStore.toTag(state));
    }

    /** For the in-world tests: a host putting up what is in their hand as a prize. */
    public static void putPrizeForTesting(ServerPlayer host, UUID eventId, int place) {
        EventPrizes.put(host, eventId, place);
    }

    /** For the in-world tests: the event finishing, with its records and prizes. */
    public static void finishForTesting(MinecraftServer server, EventState state) {
        finishUp(server, state);
    }

    /** For the in-world tests: a deck registered to a player, as signing up does. */
    public static void registerDeckForTesting(EventState state, UUID player, DeckComponent deck) {
        state.decks.put(player, deck);
    }

    /** For the in-world tests: a player gone since this tick. */
    public static void goneForTesting(UUID player, long since) {
        goneSince.put(player, since);
    }
}
