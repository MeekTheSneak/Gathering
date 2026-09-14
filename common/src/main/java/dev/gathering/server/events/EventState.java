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

    /** Ticks the current round's clock has run. Counted only while the server runs. */
    long roundTicks;

    /** Ticks the build clock has run. */
    long buildTicks;

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

    public long roundTicks() {
        return roundTicks;
    }

    public long buildTicks() {
        return buildTicks;
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

    /** The pod name a limited event's pools carry: its home table. */
    public String podName() {
        return tables.isEmpty() ? "" : tables.get(0).toShortString();
    }

    public Map<UUID, DeckComponent> decks() {
        return java.util.Collections.unmodifiableMap(decks);
    }

    public Map<UUID, DraftedPool> pools() {
        return java.util.Collections.unmodifiableMap(pools);
    }
}
