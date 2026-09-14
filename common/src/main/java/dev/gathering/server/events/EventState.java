package dev.gathering.server.events;

import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * A tournament and everything the world needs beside it: which tables it plays at, how long the
 * clock has run, and the decks players registered.
 * <p>The tournament itself is pure and says nothing about blocks; this is the part with a world
 * in it. Server thread only.
 */
public final class EventState {

    Tournament tournament;

    /** The dimension the event's tables are in. */
    String dimension;

    /** The event's tables, numbered from one in this order. */
    final List<BlockPos> tables = new ArrayList<>();

    /**
     * Real milliseconds the current round's clock has run. Counted from the time between server
     * ticks, so a server running slowly still gives a round its fifty minutes, and nothing is
     * counted while the server is stopped.
     */
    long roundMillis;

    /** Real milliseconds the build clock has run, the same way. */
    long buildMillis;

    /**
     * How many ticks the last round has stood complete, or -1 while it is not. Not saved: an
     * event loaded with a complete round starts the pause again, and pairs the next round after it.
     */
    long completeForTicks = -1;

    /**
     * Each limited player's pool as the server handed it to them, by player: what their Ready is
     * checked against. A pool item says what it is; this says who was given it, by this event.
     */
    final Map<UUID, List<dev.gathering.item.CardComponent>> allocated = new LinkedHashMap<>();

    /** What happened in the event, oldest first, for the host and admins. Bounded. */
    final List<LogLine> log = new ArrayList<>();

    /** The most lines an event's log keeps; the oldest go first. */
    static final int MOST_LOG_LINES = 500;

    /**
     * One thing that happened.
     *
     * @param at     wall-clock milliseconds
     * @param actor  who did it, or null for the event itself
     * @param action a short key, e.g. {@code settle}
     * @param detail what, in a few words; never a card in a hidden zone or a seed
     */
    public record LogLine(long at, UUID actor, String action, String detail) {
    }

    void log(UUID actor, String action, String detail) {
        log.add(new LogLine(System.currentTimeMillis(), actor, action, detail == null ? "" : detail));
        while (log.size() > MOST_LOG_LINES) {
            log.remove(0);
        }
    }

    /**
     * Where a large event takes registrations, if its host marked one: registering then needs
     * the player to be there, as paying an entry at a store's counter does.
     */
    BlockPos registrationPoint;

    /** How close to the registration point counts as being at it. */
    static final int AT_REGISTRATION = 8;

    public java.util.Optional<BlockPos> registrationPoint() {
        return java.util.Optional.ofNullable(registrationPoint);
    }

    /** For a limited event: whether its packs have been handed to a sign-up at the home table. */
    boolean podOpened;

    /** Registered decks, by player: a constructed list, checked or locked. */
    final Map<UUID, DeckComponent> decks = new LinkedHashMap<>();

    /** A limited player's pool, registered when they said they were ready. */
    final Map<UUID, DraftedPool> pools = new LinkedHashMap<>();

    /** Prizes put up, by place. */
    final List<EventPrizes.Prize> prizes = new ArrayList<>();

    /** Prizes handed out to somebody who was not online, kept until they join. */
    final Map<UUID, List<net.minecraft.world.item.ItemStack>> waitingPrizes = new LinkedHashMap<>();

    /** "round:table" for every match a game was actually played at: what a rated result needs. */
    final java.util.Set<String> playedAtTable = new java.util.LinkedHashSet<>();

    /**
     * What the table last saw at each numbered table, for pre-filling a result: games won by the
     * first and second player. Not saved; the table's own match state is saved.
     */
    final Map<Integer, int[]> seen = new LinkedHashMap<>();

    EventState(Tournament tournament, String dimension) {
        this.tournament = tournament;
        this.dimension = dimension;
    }

    public Tournament tournament() {
        return tournament;
    }

    public List<BlockPos> tables() {
        return List.copyOf(tables);
    }

    /** The round clock in ticks' worth of real time. */
    public long roundTicks() {
        return roundMillis / 50;
    }

    public long buildTicks() {
        return buildMillis / 50;
    }

    public List<LogLine> log() {
        return List.copyOf(log);
    }

    /** The table with this number, from one, if the event has that many. */
    public java.util.Optional<BlockPos> table(int number) {
        return number >= 1 && number <= tables.size() ? java.util.Optional.of(tables.get(number - 1))
                : java.util.Optional.empty();
    }

    /** Which number this table is, or zero if it is not one of the event's. */
    public int numberOf(BlockPos origin) {
        int index = tables.indexOf(origin);
        return index < 0 ? 0 : index + 1;
    }

    /**
     * The pod name a limited event's pools carry: the event itself. Not its table - a pool
     * drafted at the same table for an earlier event carried the same coordinates, and was
     * accepted as this event's.
     */
    public String podName() {
        return PREFIX + tournament.id();
    }

    /** What every event's pod name starts with. */
    static final String PREFIX = "event:";

    public Map<UUID, DeckComponent> decks() {
        return java.util.Collections.unmodifiableMap(decks);
    }

    public Map<UUID, DraftedPool> pools() {
        return java.util.Collections.unmodifiableMap(pools);
    }
}
