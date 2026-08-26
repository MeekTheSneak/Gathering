package dev.gathering.client;

import dev.gathering.core.game.visibility.GameView;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/**
 * The boards this client has been told about, by table.
 *
 * <p>More than one, because a player sits at one table and can see several: the table you are
 * playing at sends you your own view, and every table in sight sends you the public one for
 * the miniature on its surface. Keyed by table so those cannot be confused for each other -
 * the seated view and the spectator view of the same game are different objects and only one
 * of them has your hand in it.
 *
 * <p>Each is replaced wholesale rather than patched. The server decides what this client may
 * know; a client that merged updates into a board it was keeping would eventually hold
 * something it was told once and is no longer entitled to.
 *
 * <p>Client-only.
 */
public final class ClientTableState {

    /**
     * How many tables a client remembers at once.
     *
     * <p>A shop full of tables is the case this is for, and forgetting the oldest is the right
     * failure: a miniature that stops updating is a table you walked away from.
     */
    private static final int MAX_TABLES = 32;

    private static final Map<BlockPos, GameView> BOARDS = new ConcurrentHashMap<>();

    /**
     * What is in each table's pot.
     *
     * <p>Beside the board rather than inside it, exactly as it is on the server: the board is
     * a view built for one pair of eyes and the pot is face up to the room, so it is not
     * something the visibility rules have an opinion about.
     */
    private static final Map<BlockPos, java.util.List<dev.gathering.item.CardComponent>> POTS =
            new ConcurrentHashMap<>();

    /** The table this player is seated at, whose view is theirs rather than the public one. */
    private static volatile BlockPos seatedAt;

    private ClientTableState() {
    }

    public static void accept(BlockPos table, GameView board, boolean seated) {
        if (BOARDS.size() >= MAX_TABLES && !BOARDS.containsKey(table)) {
            BOARDS.keySet().stream().findFirst().ifPresent(forgotten -> {
                BOARDS.remove(forgotten);
                ClientCardFlights.forget(forgotten);
                ClientTableNews.forget(forgotten);
            });
        }
        // Before the board is put down, because what is wanted is the difference between the
        // one that was here and the one that has arrived - which is every card that moved.
        long now = ClientCardFlights.now();
        ClientCardFlights.arrived(table, board, now);
        // And the one move that leaves no trace on the board at all: a shuffle changes no
        // zone and no count, so it has to be read off the log or it is not seen.
        ClientTableNews.arrived(table, board, now);
        BOARDS.put(table.immutable(), board);
        if (seated) {
            seatedAt = table.immutable();
        }
    }

    /** The pot at this table, which is empty at almost every table there will ever be. */
    public static java.util.List<dev.gathering.item.CardComponent> potOf(BlockPos table) {
        return table == null ? java.util.List.of() : POTS.getOrDefault(table, java.util.List.of());
    }

    /** What the server says is in the pot here. */
    public static void acceptPot(BlockPos table, java.util.List<dev.gathering.item.CardComponent> cards) {
        if (table == null) {
            return;
        }
        if (cards == null || cards.isEmpty()) {
            POTS.remove(table);
        } else {
            POTS.put(table, java.util.List.copyOf(cards));
        }
    }

    public static Optional<GameView> viewOf(BlockPos table) {
        return Optional.ofNullable(BOARDS.get(table));
    }

    /** The board of the table this player is seated at, which is the one the screen draws. */
    /**
     * The seat this client holds at a table, if any.
     *
     * <p>Comes from the view the server sent, not from anything the client decided: the
     * viewer stamped on a {@code GameView} is the only account of who this client is that
     * the server would agree with.
     */
    public static Optional<dev.gathering.core.game.SeatId> seatAt(BlockPos table) {
        return viewOf(table)
                .map(dev.gathering.core.game.visibility.GameView::viewer)
                .filter(dev.gathering.core.game.visibility.Viewer.Seated.class::isInstance)
                .map(dev.gathering.core.game.visibility.Viewer.Seated.class::cast)
                .map(dev.gathering.core.game.visibility.Viewer.Seated::seat);
    }

    public static Optional<GameView> view() {
        return Optional.ofNullable(seatedAt).flatMap(ClientTableState::viewOf);
    }

    public static Optional<BlockPos> table() {
        return Optional.ofNullable(seatedAt);
    }

    /** Stops watching one table, without forgetting the rest of the room. */
    public static void forget(BlockPos table) {
        POTS.remove(table);
        BOARDS.remove(table);
        ClientCardFlights.forget(table);
        ClientTableNews.forget(table);
        if (table.equals(seatedAt)) {
            seatedAt = null;
        }
    }

    /** The seat is given up, but the tables in sight are still in sight. */
    public static void leaveSeat() {
        seatedAt = null;
    }

    /** On disconnect: what one server's tables showed is not true of the next. */
    public static void clear() {
        POTS.clear();
        BOARDS.clear();
        ClientCardFlights.clear();
        ClientTableNews.clear();
        seatedAt = null;
    }
}
