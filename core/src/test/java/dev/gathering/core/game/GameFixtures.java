package dev.gathering.core.game;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small helpers so the game tests read as games rather than as setup. */
public final class GameFixtures {

    public static final SeatId ALICE = SeatId.of(0);
    public static final SeatId BOB = SeatId.of(1);
    public static final SeatId CHRIS = SeatId.of(2);

    /** A fixed seed, so a failing test fails the same way twice. */
    public static final SessionSeed FIXED_SEED = SessionSeed.fromBytes(
            "gathering-test-seed-0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private GameFixtures() {
    }

    public static CardIdentity card(int number) {
        return CardIdentity.ofPrinting(
                UUID.fromString(String.format("00000000-0000-4000-8000-%012d", number)));
    }

    public static List<CardIdentity> deck(int size) {
        List<CardIdentity> cards = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            cards.add(card(index));
        }
        return cards;
    }

    /** A two-player table with decks already loaded, which is where most tests want to start. */
    public static GameSession twoPlayerTable(int librarySize) {
        GameSession session = GameSession.create(
                List.of(ALICE, BOB), 40, FIXED_SEED, UndoMode.shippedDefault());
        session.submit(new GameEvent.SeatTaken(ALICE, new PlayerRef(UUID.randomUUID(), "Alice")));
        session.submit(new GameEvent.SeatTaken(BOB, new PlayerRef(UUID.randomUUID(), "Bob")));
        session.submit(new GameEvent.DeckLoaded(ALICE, deck(librarySize), List.of(card(900))));
        session.submit(new GameEvent.DeckLoaded(BOB, deck(librarySize), List.of(card(901))));
        return session;
    }

    /**
     * A table of this many seats, all sat in and all with a deck down.
     * <p>For the rules that only have a third case once there is a third player - showing one
     * opponent something and not the other, which is two seats' worth of nothing to check.
     */
    public static GameSession table(int seats, int librarySize) {
        List<SeatId> chairs = new ArrayList<>(seats);
        for (int index = 0; index < seats; index++) {
            chairs.add(SeatId.of(index));
        }
        GameSession session = GameSession.create(chairs, 40, FIXED_SEED, UndoMode.shippedDefault());
        for (SeatId seat : chairs) {
            session.submit(new GameEvent.SeatTaken(
                    seat, new PlayerRef(UUID.randomUUID(), "Player " + (seat.index() + 1))));
            session.submit(new GameEvent.DeckLoaded(
                    seat, deck(librarySize), List.of(card(900 + seat.index()))));
        }
        return session;
    }

    /** The first card of a seat's library, which tests reach for constantly. */
    public static CardInstanceId topOfLibrary(GameSession session, SeatId seat) {
        return session.state().contents(seat, Zone.LIBRARY).get(0);
    }

    public static CardInstanceId firstInHand(GameSession session, SeatId seat) {
        return session.state().contents(seat, Zone.HAND).get(0);
    }
}
