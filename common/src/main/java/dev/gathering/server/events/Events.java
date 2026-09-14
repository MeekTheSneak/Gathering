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
import dev.gathering.server.ServerTicks;
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

    /** Ticks in a minute. */
    static final long MINUTE = 20L * 60L;

    /** How long a player may be gone from a round before their match is conceded: five minutes. */
    static final long GRACE = 5L * MINUTE;

    /** How long after a round's last result the next round is paired: long enough to read it. */
    static final int PAUSE_BEFORE_NEXT_ROUND = 20 * 15;

    private static Map<UUID, EventState> events;
    private static final Map<UUID, Long> goneSince = new HashMap<>();

    private Events() {
    }

    /** Forgets everything, for a server that is stopping. */
    public static void clear() {
        events = null;
        goneSince.clear();
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
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            return host.getGameProfile().getName() + "'s tournament";
        }
        return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
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
                || !player.blockPosition().closerThan(state.registrationPoint, EventState.AT_REGISTRATION))) {
            BlockPos at = state.registrationPoint;
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
        if (deck != null) {
            state.decks.put(player.getUUID(), deck);
            save(state);
        }
        player.sendSystemMessage(Component.translatable(deck != null && settings.decks() == EventSettings.DeckRegistration.LOCKED
                ? "message.gathering.event.registered_locked" : "message.gathering.event.registered", state.tournament.name()));
    }

    public static void withdraw(ServerPlayer player, UUID eventId) {
        EventState state = get(eventId).orElse(null);
        if (state == null) {
            return;
        }
        if (apply(player, state, tournament -> tournament.drop(player.getUUID()))) {
            if (!state.tournament.isRegistered(player.getUUID())) {
                state.decks.remove(player.getUUID());
                state.pools.remove(player.getUUID());
                save(state);
            }
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
        if (state == null || !apply(host, state, Tournament::beginPreparing)) {
            return;
        }
        MinecraftServer server = host.getServer();
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level == null) {
            return;
        }
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
        // The pool names the table its draft was opened at, which is whichever of the long
        // table's tables the cluster answered for - so any of this event's tables counts.
        boolean fromThisEvent = pool != null && state.tables.stream()
                .anyMatch(table -> table.toShortString().equals(pool.fromPod()));
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
            if (state.tournament.phase() == Tournament.Phase.PREPARING) {
                startPlay(host.getServer(), state);
            }
        });
    }

    /** A player reports their match, as they see it. */
    public static void report(ServerPlayer player, UUID eventId, MatchResult asTheySeeIt) {
        get(eventId).ifPresent(state -> {
            if (apply(player, state, tournament -> tournament.report(player.getUUID(), asTheySeeIt))) {
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
                afterResult(host.getServer(), state);
            }
        });
    }

    public static void dropPlayer(ServerPlayer host, UUID eventId, UUID player) {
        hosted(host, eventId).ifPresent(state -> {
            if (apply(host, state, tournament -> tournament.drop(player))) {
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
        apply(host, state, Tournament::cancel);
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
        if (level != null && !state.tables.isEmpty()) {
            // Whatever the draft left at the home table is over now.
            clearTables(level, state);
            TablesApart.set(level, state.tables.get(0), true);
        }
        try {
            state.tournament = state.tournament.startSwiss();
        } catch (IllegalArgumentException refused) {
            tell(server, state.tournament.host(), Component.translatable(refused.getMessage()));
            return;
        }
        state.roundTicks = 0;
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
            TableSessions.Outcome outcome = TableSessions.start(level, table,
                    new MatchRules(format, state.tournament.settings().bestOf()));
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
        boolean near = online.serverLevel() == level && online.blockPosition().closerThan(table, 24);
        if (atThisLongTable || (bringFromNearby && near)) {
            online.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                    seat.side() == dev.gathering.core.table.Side.NORTH ? 180f : 0f, 0f);
        } else {
            EventPointers.pointTo(online, stand);
        }
    }

    /** Ends anything left running at the event's tables, handing decks back. */
    private static void clearTables(ServerLevel level, EventState state) {
        for (BlockPos table : state.tables) {
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

    private static void afterResult(MinecraftServer server, EventState state) {
        Round round = state.tournament.currentRound().orElse(null);
        if (round != null && round.isComplete() && !state.tournament.isOver()) {
            ServerTicks.on("event-next-" + state.tournament.id(), server.getTickCount() + PAUSE_BEFORE_NEXT_ROUND,
                    () -> nextRound(server, state.tournament.id()));
            for (Entrant entrant : state.tournament.stillIn()) {
                tell(server, entrant.id(), Component.translatable("message.gathering.event.round_complete", round.number()));
            }
        }
        changed(server, state);
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
        state.roundTicks = 0;
        if (state.tournament.isOver()) {
            finishUp(server, state);
            return;
        }
        seatRound(server, state);
        changed(server, state);
    }

    private static void finishUp(MinecraftServer server, EventState state) {
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level != null && !state.tables.isEmpty()) {
            clearTables(level, state);
            TablesApart.set(level, state.tables.get(0), false);
            TableBlock.entityAt(level, state.tables.get(0)).filter(TableBlockEntity::hasSignup)
                    .ifPresent(entity -> PodSignups.handBackEverything(level, state.tables.get(0), entity, "pod_signup_cancelled"));
        }
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
        if (events == null && server.getTickCount() % 100 != 0) {
            return;
        }
        for (EventState state : all()) {
            Tournament tournament = state.tournament;
            if (tournament.isOver()) {
                continue;
            }
            switch (tournament.phase()) {
                case PREPARING -> buildTick(server, state);
                case SWISS, CUT -> roundTick(server, state);
                default -> { }
            }
        }
    }

    private static void buildTick(MinecraftServer server, EventState state) {
        if (!state.tournament.settings().kind().isLimited() || !state.podOpened) {
            return;
        }
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level == null || state.tables.isEmpty()) {
            return;
        }
        boolean podStillRunning = TableBlock.entityAt(level, state.tables.get(0))
                .map(entity -> entity.hasSignup() || entity.hasPod()).orElse(false);
        if (podStillRunning) {
            return;
        }
        state.buildTicks++;
        if (state.buildTicks % (20 * 60) == 0) {
            save(state);
            changed(server, state);
        }
        if (state.buildTicks >= state.tournament.settings().buildMinutes() * MINUTE) {
            for (Entrant entrant : state.tournament.stillIn()) {
                tell(server, entrant.id(), Component.translatable("message.gathering.event.build_time_up"));
            }
            startPlay(server, state);
        }
    }

    private static void roundTick(MinecraftServer server, EventState state) {
        Round round = state.tournament.currentRound().orElse(null);
        if (round == null || round.isComplete()) {
            return;
        }
        state.roundTicks++;
        if (!round.timeCalled() && state.roundTicks >= state.tournament.settings().roundMinutes() * MINUTE) {
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
        long now = server.getTickCount();
        for (Pairing pairing : round.pairings()) {
            if (pairing.isConfirmed() || pairing.isBye()) {
                continue;
            }
            for (UUID player : new UUID[] {pairing.a(), pairing.b()}) {
                Long gone = goneSince.get(player);
                if (gone != null && now - gone >= GRACE && state.tournament.currentRound().flatMap(r -> r.pairingOf(player))
                        .map(p -> !p.isConfirmed()).orElse(false)) {
                    state.tournament = state.tournament.settle(pairing.table(), MatchResult.conceded(pairing.a().equals(player)));
                    tell(server, pairing.opponentOf(player), Component.translatable("message.gathering.event.opponent_gone"));
                    afterResult(server, state);
                }
            }
        }
        if (state.roundTicks % (20 * 30) == 0) {
            save(state);
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
            state.tournament = state.tournament.endAtTime(table, wins[0], wins[1], TableSessions.hasSession(level, origin));
            dev.gathering.server.TableBroadcast.tell(level, origin, Component.translatable("message.gathering.event.match_over_at_time"));
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
        state.seen.put(table, new int[] {match.winsFor(new SeatId(0)), match.winsFor(new SeatId(1))});
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
        return seen == null ? Optional.empty() : Optional.of(new MatchResult(seen[0], seen[1], 0));
    }

    private static int[] winsAt(ServerLevel level, BlockPos origin, Pairing pairing) {
        MatchState match = TableSessions.matchAt(level, origin).orElse(null);
        if (match == null) {
            return new int[] {0, 0};
        }
        return new int[] {match.winsFor(new SeatId(0)), match.winsFor(new SeatId(1))};
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

    public static void left(ServerPlayer player, int tick) {
        if (of(player.getUUID()).isPresent()) {
            goneSince.put(player.getUUID(), (long) tick);
        }
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
            if (after == state.tournament) {
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

    static void save(EventState state) {
        EventStore.write(state);
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
            roundTick(server, state);
        }
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
