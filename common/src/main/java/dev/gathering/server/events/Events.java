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
import dev.gathering.core.tournament.HostActions;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Round;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.DraftedPool;
import dev.gathering.platform.WorldSpace;
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
import java.util.Set;
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

    /**
     * Everybody this server has actually seen online since it loaded.
     * <p>The grace period is a rule about a player walking away from a match, and it can only start
     * running once there is a player to walk away. Without this it started running against everybody
     * the moment an event loaded: a server restarted overnight would, five minutes later, concede
     * every unfinished match in a tournament, drop the whole field for being absent, and record the
     * event as finished - rated, with the prizes paid out on the standings of the last round anybody
     * played. Nothing was at fault and nobody was told. FINISHED is terminal.
     * <p>The cost of the other answer is a round that stands open until somebody comes back, which
     * the host can settle or drop by hand. A tournament that waits is recoverable; one that has
     * finished itself is not.
     */
    private static final Set<UUID> seenOnline = new java.util.LinkedHashSet<>();

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
        seenOnline.clear();
        lastDeskUse.clear();
        deskArrivals.clear();
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

    /** How far from a Scorekeeper's Desk, across, a table is one of the tournament hosted there. */
    public static final int DESK_TABLES_ACROSS = 16;
    /** And how far above or below it. */
    public static final int DESK_TABLES_UP = 4;

    /**
     * Hosts a tournament at a Scorekeeper's Desk, which is how a player hosts one: its tables are
     * the free tables near the desk, nearest long table first - the first is where a draft or sealed
     * event opens its packs - and the desk runs it, so signing up happens there from the start.
     * <p>A desk running a tournament that is not over is refused rather than taken: taking one over
     * is using it twice while hosting, and a sign-up screen is no place for a stray click to move an
     * event. That refusal is the whole of the limit on how many tournaments there are - one per desk,
     * however many one person runs. No free table nearby is not refused; the host adds tables later,
     * standing at them.
     *
     * @param prizes what the host put up while filling the screen in, taken from their hotbar once
     *               the event exists to hold it
     * @return the event, or empty with the host told why
     */
    public static Optional<EventState> hostAtDesk(ServerPlayer host, BlockPos deskPos, String name,
            EventSettings settings, List<dev.gathering.core.tournament.PrizeOffer> prizes) {
        ServerLevel level = host.serverLevel();
        if (!(level.getBlockEntity(deskPos) instanceof dev.gathering.block.ScorekeepersDeskBlockEntity desk)) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.at_a_desk"));
            return Optional.empty();
        }
        String dimension = level.dimension().location().toString();
        if (desk.event().flatMap(Events::get)
                .filter(state -> dimension.equals(state.dimension) && !state.tournament.isOver()).isPresent()) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.desk_busy"));
            return Optional.empty();
        }
        Optional<String> refused = whyNotCreate(host, settings);
        if (refused.isPresent()) {
            host.sendSystemMessage(Component.translatable(refused.get()));
            return Optional.empty();
        }
        List<BlockPos> tables = freeTablesNear(level, deskPos);
        EventState state = open(host, level, name, settings, tables);
        host.sendSystemMessage(tables.isEmpty()
                ? Component.translatable("message.gathering.event.created_no_tables", state.tournament.name())
                : Component.translatable("message.gathering.event.created", state.tournament.name(), tables.size()));
        // After the event exists and before anybody is told it is signing up: a prize promised on the
        // create screen is only a prize once the event holds the item, and the host should find out
        // which of them it took while they are still standing here.
        EventPrizes.putUpAtCreation(host, state, prizes);
        runFromDesk(host, desk, state, false);
        return Optional.of(state);
    }

    /**
     * Whether anything is standing on this table that a tournament would have to disturb.
     * <p>The same question {@code TablesApart} asks before changing a line's shape, asked here so the
     * answer is the same in both places. A draft waiting on a player who has logged off is the one
     * that is not the host's to clear: the pools are held until everybody can take theirs, which is
     * correct, and no amount of clearing the table will change it.
     */
    private static boolean somethingOnIt(ServerLevel level, BlockPos table) {
        return TableBlock.entityAt(level, table).map(entity -> TablesApart.inUse(entity)).orElse(Boolean.FALSE);
    }

    /** Every table near a desk that no tournament plays at and nothing is standing on, nearest first. */
    public static List<BlockPos> freeTablesNear(ServerLevel level, BlockPos deskPos) {
        List<List<BlockPos>> lines = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(deskPos.offset(-DESK_TABLES_ACROSS, -DESK_TABLES_UP, -DESK_TABLES_ACROSS),
                deskPos.offset(DESK_TABLES_ACROSS, DESK_TABLES_UP, DESK_TABLES_ACROSS))) {
            if (!(level.getBlockState(pos).getBlock() instanceof TableBlock)) {
                continue;
            }
            BlockPos origin = TableBlock.entityAt(level, pos).map(TableBlockEntity::getBlockPos).orElse(null);
            if (origin == null || seen.contains(origin)) {
                continue;
            }
            List<BlockPos> line = orderedTables(level, origin);
            seen.addAll(line);
            // Free means both things it has to mean: no other event has claimed it, and there is
            // nothing standing on it. Only the first was checked, so a desk would claim a table with
            // a game, a pot or an unfinished draft on it - and then refuse to start play on the
            // grounds that the table was in use, which is a tournament that can never begin.
            List<BlockPos> free = line.stream()
                    .filter(table -> atTable(level, table).isEmpty() && !somethingOnIt(level, table))
                    .toList();
            if (!free.isEmpty()) {
                lines.add(free);
            }
        }
        lines.sort(Comparator.comparingDouble(line -> line.stream().mapToDouble(table -> table.distSqr(deskPos)).min().orElse(0)));
        return lines.stream().flatMap(List::stream).toList();
    }

    /** Whatever refuses a tournament before where it is played is asked: its settings, and its host. */
    private static Optional<String> whyNotCreate(ServerPlayer host, EventSettings settings) {
        Optional<String> problem = settings.problem();
        if (problem.isPresent()) {
            return problem;
        }
        if (settings.kind() == EventSettings.Kind.CONSTRUCTED && FormatPresets.byId(settings.formatId()).isEmpty()) {
            return Optional.of("message.gathering.event.needs_a_format");
        }
        if (running(host.getUUID()) >= EventRecords.MOST_AT_ONCE) {
            return Optional.of("message.gathering.event.too_many");
        }
        return EventRecords.whyNotHost(host.getUUID());
    }

    /** How many tournaments this person has running that are not over. */
    private static int running(UUID host) {
        int open = 0;
        for (EventState state : events().values()) {
            if (!state.tournament.isOver() && state.tournament.host().equals(host)) {
                open++;
            }
        }
        return open;
    }

    private static EventState open(ServerPlayer host, ServerLevel level, String name, EventSettings settings, List<BlockPos> tables) {
        Tournament tournament = Tournament.create(UUID.randomUUID(), cleanName(name, host), host.getUUID(), settings);
        EventState state = new EventState(tournament, level.dimension().location().toString());
        state.tables.addAll(tables);
        events().put(tournament.id(), state);
        state.log(host.getUUID(), "create", tournament.name());
        EventRecords.hosted(host.getUUID());
        changed(level.getServer(), state);
        return state;
    }

    /**
     * Adds the long table the host is at to an event's tables. Host only.
     * <p>The rule is the simplest one that does not need a chair: <em>the nearest long table within
     * three blocks of where the host is standing</em> - the same search {@link #nearestTable} has
     * always done, now asked from where the host actually is. A host who
     * has just carried a table across the room adds it by walking up to it, and nobody has to sit
     * down first.
     * <p>Where they are comes from the player on the server and never from the request. The button
     * used to send a position and the server used to check the host was within reach of it, which
     * was two mistakes at once: the screen had nowhere to get a table from and sent the origin of
     * the world, so the check refused every press - and had it sent one, a position in a request is
     * whatever a client puts there.
     */
    public static void addTables(ServerPlayer host, UUID eventId) {
        addTablesNear(host, eventId, host.blockPosition());
    }

    /** The same, from a position the server worked out. Never from one a client sent. */
    public static void addTablesNear(ServerPlayer host, UUID eventId, BlockPos clicked) {
        EventState state = hosted(host, eventId).orElse(null);
        ServerLevel level = host.serverLevel();
        if (state == null || !level.dimension().location().toString().equals(state.dimension)) {
            return;
        }
        if (refused(host, state, HostActions.Action.ADD_TABLES)) {
            return;
        }
        BlockPos origin = nearestTable(level, clicked).orElse(null);
        if (origin == null) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.stand_at_a_table"));
            return;
        }
        int added = 0;
        for (BlockPos table : orderedTables(level, origin)) {
            // The same question the desk asks when it picks its own tables. Asked only there, this
            // added a table with a live game, a pot or an unfinished draft on it - and the next round
            // ran clearTables over it, which ends that game, hands its decks back and unwinds its
            // ante. Nobody at that table asked for any of it.
            if (!state.tables.contains(table) && atTable(level, table).isEmpty()
                    && !somethingOnIt(level, table)) {
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
            if (!host.serverLevel().dimension().location().toString().equals(state.dimension)
                    || refused(host, state, HostActions.Action.MARK_REGISTRATION)) {
                return;
            }
            // The desk it is leaving deliberately keeps its claim, which is what lets one use of it
            // take signing up back - see useDesk and ahostsOwnDeskTakesSigningUpBack. Moving to
            // another desk hands the claim over; marking a spot on the floor leaves the desk as the
            // way back to it.
            state.registrationPoint = host.blockPosition();
            changed(host.getServer(), state);
            host.sendSystemMessage(Component.translatable("message.gathering.event.registration_marked"));
        });
    }

    /** How long a second use of a desk counts as meaning it: long enough to read what the first said. */
    static final long DESK_SECOND_USE_MILLIS = 10_000;

    /** Who last used a desk that runs a tournament, where, and when: a second use soon after takes it over. */
    private record DeskUse(String dimension, BlockPos desk, long at) {
    }

    private static final Map<UUID, DeskUse> lastDeskUse = new HashMap<>();

    /**
     * Somebody uses a Scorekeeper's Desk.
     * <p>A free desk is taken on one use by a tournament of theirs that has nowhere to sign up - one
     * just made away from a desk, or one whose desk has been broken - because a tournament with
     * nowhere to sign up has nothing to lose by being given somewhere.
     * <p>A free desk beside a tournament of theirs that already has a desk shows what tournaments
     * there are and offers hosting one here ({@link #hostAtDesk}) instead, and says that using it
     * again moves the other one here. That is how a second tournament is hosted: somebody who puts a
     * desk down beside a second row of tables means a second tournament far more often than they mean
     * to pick the first one up and carry it over, and the two answers are one press apart rather than
     * one of them being what a stray click gives.
     * <p>A desk running somebody's unfinished tournament is taken over the same deliberate way, by a
     * second use soon after the first. A host's own desk that signing up has moved away from takes it
     * back on one use. Anybody else is shown the tournament the desk runs.
     */
    public static void useDesk(ServerPlayer player, BlockPos deskPos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(deskPos) instanceof dev.gathering.block.ScorekeepersDeskBlockEntity desk)) {
            return;
        }
        String dimension = level.dimension().location().toString();
        EventState running = desk.event().flatMap(Events::get).filter(state -> dimension.equals(state.dimension)).orElse(null);
        // A host running more than one tournament means the one with nowhere to sign up, and among
        // those the one played beside this desk.
        Vec3 deskInWorld = WorldSpace.get().centerInWorld(level, deskPos);
        EventState hosting = events().values().stream()
                .filter(state -> state != running && !state.tournament.isOver() && dimension.equals(state.dimension)
                        && state.tournament.host().equals(player.getUUID()))
                .min(java.util.Comparator.comparingInt((EventState state) -> state.registrationPoint == null ? 0 : 1)
                        .thenComparingDouble(state -> state.tables.stream()
                                .mapToDouble(table -> WorldSpace.get().centerInWorld(level, table).distanceToSqr(deskInWorld))
                                .min().orElse(Double.MAX_VALUE)))
                .orElse(null);
        boolean free = running == null || running.tournament.isOver();
        boolean homeless = hosting != null && hosting.registrationPoint == null;
        DeskUse before = lastDeskUse.remove(player.getUUID());
        long now = wallClock.getAsLong();
        boolean again = before != null && before.dimension().equals(dimension) && before.desk().equals(deskPos)
                && now - before.at() <= DESK_SECOND_USE_MILLIS;
        // Never a desk already running one of this host's own tournaments where it stands: using your
        // own desk twice moved your other tournament onto it and left this one with nowhere to sign up
        // at all - and a tournament with nowhere to sign up takes registrations from anywhere in the
        // world, so two clicks on your own desk quietly opened one of them to the whole server. Taking
        // somebody else's desk over is still what a second use does.
        boolean alreadyMine = running != null && !running.tournament.isOver()
                && running.tournament.host().equals(player.getUUID())
                && deskPos.equals(running.registrationPoint);
        if (hosting != null && !alreadyMine && (again || (free && homeless))) {
            if (running != null && deskPos.equals(running.registrationPoint)) {
                running.registrationPoint = null;
                changed(player.getServer(), running);
                // Somebody else's tournament has just lost the desk it signed up at, which leaves it
                // taking registrations from anywhere - so its host is told, by name. Two clicks used
                // to do that to another person's event and say nothing to them at all.
                tellTheHost(player.getServer(), running, "message.gathering.event.desk_taken_from_you",
                        running.tournament.name(), player.getGameProfile().getName());
            }
            runFromDesk(player, desk, hosting);
            return;
        }
        if (running != null && !running.tournament.isOver() && running.tournament.host().equals(player.getUUID())
                && !deskPos.equals(running.registrationPoint)) {
            // Their own desk, which signing up was moved away from - or which was carried somewhere new.
            runFromDesk(player, desk, running);
            return;
        }
        if (running != null && !running.tournament.isOver()) {
            if (hosting != null) {
                lastDeskUse.put(player.getUUID(), new DeskUse(dimension, deskPos.immutable(), now));
                player.sendSystemMessage(Component.translatable("message.gathering.desk.taken", running.tournament.name(),
                        hosting.tournament.name()));
            }
            EventViews.show(player, running, true);
            return;
        }
        // A free desk: the tournaments there are, finished ones included, and hosting a new one here.
        // A host already running one that has a desk of its own is told, once, that using this one
        // again moves it here - so both answers are one press away and neither is a stray click's.
        if (hosting != null && !homeless) {
            lastDeskUse.put(player.getUUID(), new DeskUse(dimension, deskPos.immutable(), now));
            player.sendSystemMessage(Component.translatable("message.gathering.desk.free", hosting.tournament.name()));
        }
        EventViews.list(player, true, deskPos);
    }

    private static void runFromDesk(ServerPlayer player, dev.gathering.block.ScorekeepersDeskBlockEntity desk, EventState state) {
        runFromDesk(player, desk, state, true);
    }

    /** The desk runs this tournament; said in the chat unless the host was just told it was created here. */
    private static void runFromDesk(ServerPlayer player, dev.gathering.block.ScorekeepersDeskBlockEntity desk, EventState state,
            boolean say) {
        // The desk it is leaving lets go of it first. Nothing ever called runs(null), so a desk a
        // tournament had moved away from went on claiming it: it refused to host anything else for as
        // long as that tournament ran, a display board wired to it showed a tournament signing up
        // somewhere else, and one ordinary use of it pulled the tournament straight back - which
        // defeated the two-press rule the move is supposed to take.
        letGoOfTheDesk(player.getServer(), state);
        state.registrationPoint = desk.getBlockPos().immutable();
        desk.runs(state.tournament.id());
        changed(player.getServer(), state);
        // The lectern's page turning: a desk taken on is heard, not only read about in the chat.
        player.serverLevel().playSound(null, desk.getBlockPos(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        if (say) {
            player.sendSystemMessage(Component.translatable("message.gathering.desk.runs", state.tournament.name()));
        }
        EventViews.show(player, state, true);
    }

    /** Says something to a tournament's host, if they are on the server to hear it. */
    private static void tellTheHost(net.minecraft.server.MinecraftServer server, EventState state,
            String message, Object... said) {
        if (server == null || state == null) {
            return;
        }
        ServerPlayer host = server.getPlayerList().getPlayer(state.tournament.host());
        if (host != null) {
            host.sendSystemMessage(Component.translatable(message, said));
        }
    }

    /** Whichever desk was running this tournament stops saying so. */
    private static void letGoOfTheDesk(net.minecraft.server.MinecraftServer server, EventState state) {
        if (server == null || state.registrationPoint == null) {
            return;
        }
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            if (!level.dimension().location().toString().equals(state.dimension)) {
                continue;
            }
            if (level.isLoaded(state.registrationPoint)
                    && level.getBlockEntity(state.registrationPoint)
                            instanceof dev.gathering.block.ScorekeepersDeskBlockEntity desk
                    && desk.event().filter(state.tournament.id()::equals).isPresent()) {
                desk.runs(null);
            }
        }
    }

    /** A table carried from one place to another: the events it is numbered in follow it. */
    public static void tableCarried(ServerLevel level, BlockPos from, BlockPos to) {
        String dimension = level.dimension().location().toString();
        for (EventState state : events().values()) {
            int index = dimension.equals(state.dimension) ? state.tables.indexOf(from) : -1;
            // Never onto a table the event already lists: two numbers for one table is worse than one
            // stale entry, which the event already copes with as a table gone.
            if (index >= 0 && !state.tables.contains(to)) {
                state.tables.set(index, to.immutable());
                changed(level.getServer(), state);
            }
        }
    }

    /** A desk that has just arrived somewhere, copied from one standing elsewhere this tick. */
    private record DeskArrival(String dimension, BlockPos from, BlockPos to, long tick) {
    }

    private static final Map<String, DeskArrival> deskArrivals = new HashMap<>();

    /**
     * A desk has been loaded at {@code to} from a save made this tick at {@code from}. If the desk at
     * {@code from} goes this same tick, it was carried - a Sable ship assembled around it - and
     * signing up goes with it rather than being set loose. Noted until then: a copy whose original
     * stays is a copy, and moves nothing.
     */
    public static void deskArrived(ServerLevel level, BlockPos from, BlockPos to) {
        String dimension = level.dimension().location().toString();
        deskArrivals.put(dimension + "@" + from.asLong(), new DeskArrival(dimension, from.immutable(), to.immutable(), level.getGameTime()));
    }

    /** A desk is gone: signing up goes with it if it was carried, and is no longer tied to where it stood if not. */
    public static void deskRemoved(ServerLevel level, BlockPos deskPos) {
        String dimension = level.dimension().location().toString();
        DeskArrival arrival = deskArrivals.remove(dimension + "@" + deskPos.asLong());
        BlockPos carriedTo = arrival != null && arrival.tick() == level.getGameTime()
                && level.getBlockEntity(arrival.to()) instanceof dev.gathering.block.ScorekeepersDeskBlockEntity ? arrival.to() : null;
        for (EventState state : events().values()) {
            if (dimension.equals(state.dimension) && deskPos.equals(state.registrationPoint)) {
                state.registrationPoint = carriedTo;
                changed(level.getServer(), state);
            }
        }
    }

    public static void openCheckIn(ServerPlayer host, UUID eventId) {
        hosted(host, eventId).filter(state -> !refused(host, state, HostActions.Action.OPEN_CHECK_IN))
                .ifPresent(state -> apply(host, state, Tournament::openCheckIn));
    }

    /**
     * Registration is over. A constructed event starts its first round at once; a limited one
     * seats everybody at the home table and opens its packs to a sign-up.
     */
    public static void begin(ServerPlayer host, UUID eventId) {
        EventState state = hosted(host, eventId).orElse(null);
        if (state == null || refused(host, state, HostActions.Action.BEGIN)) {
            return;
        }
        MinecraftServer server = host.getServer();
        ServerLevel level = levelOf(server, state).orElse(null);
        if (level == null || state.tables.isEmpty()) {
            host.sendSystemMessage(Component.translatable("message.gathering.event.no_tables"));
            return;
        }
        if (state.tournament.settings().kind().isLimited()) {
            // Asked before anything changes: a draft or sealed event is one pod at its home long
            // table, and a pod that cannot seat everybody registered is refused here rather
            // than begun with some of them left standing.
            //
            // The line is joined first, and refused if it will not join. Both were done after the
            // event had already been moved into preparing - the join's answer was thrown away and
            // the seat count was a message rather than a refusal - so a home table with anything on
            // it left the line split, reported half the seats, stood most of the pod up, and put the
            // event in a phase with no way back to sign-up. The only exit was to call it off.
            BlockPos home = state.tables.get(0);
            if (TablesApart.set(level, home, false) == TablesApart.Result.IN_USE) {
                host.sendSystemMessage(Component.translatable(
                        "message.gathering.event.tables_in_use", state.numberOf(home)));
                return;
            }
            int seats = TableClusters.at(level, home).seats().size();
            int players = state.tournament.playingIfBegunNow().size();
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
        // Everybody together at the home table, which is one surface for the draft. Already joined
        // and already counted, above, before this event was moved into preparing.
        BlockPos home = state.tables.get(0);
        List<Entrant> players = state.tournament.stillIn();
        int seats = TableClusters.at(level, home).seats().size();
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
            // Starting now skips the building, never the packs: while they are still in a sign-up or
            // a draft, play cannot begin at a table they are on. Refused with the reason the host's
            // screen was already showing.
            if (refused(host, state, HostActions.Action.START_NOW)) {
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
        // An event that has ended has nothing left to call off - and its tables may be a newer
        // event's by now, which a second clean-up would tear down.
        if (refused(host, state, HostActions.Action.CANCEL)) {
            return;
        }
        state.tournament = state.tournament.cancel();
        state.log(host.getUUID(), "cancel", "");
        finishUp(host.getServer(), state);
    }

    /**
     * Whether a host's action does not apply to the event as it stands - said to the host if so. The
     * same answer the host's screen is sent (HostActions), so a control grayed there is refused here,
     * and one that was not grayed when the screen was drawn but has gone stale is refused all the same.
     */
    static boolean refused(ServerPlayer host, EventState state, HostActions.Action action) {
        Optional<String> why = HostActions.refusal(action, state.tournament, packsStillOut(host.getServer(), state));
        why.ifPresent(key -> host.sendSystemMessage(Component.translatable(key)));
        return why.isPresent();
    }

    /** Whether an event's packs are still out, for the host's controls. False where the world cannot say. */
    static boolean packsStillOut(MinecraftServer server, EventState state) {
        return state.tournament.phase() == Tournament.Phase.PREPARING
                && levelOf(server, state).map(level -> podStillRunning(level, state)).orElse(false);
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
            // A table that cannot be pulled apart is one this event cannot play on, and it is not a
            // reason to refuse the whole tournament. It used to be: one table with an old draft on it
            // and an event of any size would not start, the host was told to clear a table that was
            // not theirs to clear, and there was nothing else to try. So the busy ones are given up
            // and the rest are played on, and the host is told which went and can add them back.
            List<BlockPos> busy = new ArrayList<>();
            for (BlockPos table : tablesStillOurs(level, state)) {
                if (TablesApart.set(level, table, true) == TablesApart.Result.IN_USE) {
                    busy.add(table);
                }
            }
            // Decided before anything moves. Giving the tables up first and refusing afterwards left
            // the event holding none of them and still in PREPARING - and the next press of Start
            // skipped this whole block for want of a table, paired a round, and seated nobody
            // anywhere. Then the first thing to save the event wrote the empty list to disk.
            if (busy.size() == state.tables.size()) {
                tell(server, state.tournament.host(),
                        Component.translatable("message.gathering.event.no_tables"));
                return;
            }
            // Numbered before any of them go, because the number is a position in the list: removing
            // one renumbers every table after it, and the host reads those numbers off the signs.
            List<Integer> numbers = new ArrayList<>();
            for (BlockPos table : busy) {
                numbers.add(state.numberOf(table));
            }
            for (int which = 0; which < busy.size(); which++) {
                tell(server, state.tournament.host(), Component.translatable(
                        "message.gathering.event.table_given_up", numbers.get(which)));
                state.tables.remove(busy.get(which));
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

    /**
     * The event whose tables are being cleared right now, if any.
     * <p>Said explicitly rather than worked out from the table. A game ended here is a match of
     * this event - but the round that finishes an event is cleared after the event already
     * reads as over, so {@link #atTable}, which only knows unfinished events, would not place
     * it; and a table from a long-finished event hosts casual games afterwards, so asking which
     * event ever used a table would place those wrongly, in the direction that shows somebody's
     * casual game to the whole server.
     */
    private static UUID clearing;

    /**
     * Which tournament a game ending at this table right now is a match of, if it is one.
     * Asked as the game is written down, to decide who may watch it back.
     */
    public static Optional<UUID> eventOfGameEndingAt(ServerLevel level, BlockPos origin) {
        if (clearing != null) {
            return Optional.of(clearing);
        }
        return atTable(level, origin).map(state -> state.tournament.id());
    }

    /** Ends anything left running at the event's tables, handing decks back. */
    private static void clearTables(ServerLevel level, EventState state) {
        UUID was = clearing;
        clearing = state.tournament.id();
        try {
            clearTablesOf(level, state);
        } finally {
            clearing = was;
        }
    }

    private static void clearTablesOf(ServerLevel level, EventState state) {
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
                if (server.getPlayerList().getPlayer(player) != null) {
                    seenOnline.add(player);
                } else if (seenOnline.contains(player)) {
                    // Counted as gone from when it was first noticed - but only for somebody this
                    // server has actually seen, or a restart starts the clock on the whole field at
                    // once. See seenOnline.
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
            // The pairing was confirmed between the turn passing and this lookup. The count has
            // still gone up, and leaving without saving it is the one thing this file's own comment
            // says cannot happen: every change goes through a method that applies it and saves it.
            changed(level.getServer(), state);
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
        // From here on their absence means something, because they have been here. See seenOnline.
        seenOnline.add(player.getUUID());
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
