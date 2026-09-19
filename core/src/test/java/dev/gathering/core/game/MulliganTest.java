package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A mulligan: the hand goes back, the library is shuffled, a new hand comes out, and the count
 * goes up.
 * <p>This was {@code LondonMulliganTest}, and it checked what rule 103.5 asks for - a card to the
 * bottom per mulligan taken, with the first one free in a multiplayer game (103.5c). The mod
 * counted that and printed it under the button. The owner had it taken out (2026-09-19): the mod
 * cannot know whether this table plays free mulligans or whether something on the board says
 * otherwise, so a count shown as an instruction is the mod telling a player what the rules are.
 * <p>What is left is what actually happened, which is all a log should ever say.
 */
class MulliganTest {

    private static GameSession sitting(int players) {
        List<SeatId> seats = new ArrayList<>();
        for (int index = 0; index < players; index++) {
            seats.add(SeatId.of(index));
        }
        GameSession session = GameSession.create(seats, 20, SessionSeed.random(), UndoMode.shippedDefault());
        for (SeatId seat : seats) {
            session.submit(new GameEvent.SeatTaken(seat, new PlayerRef(UUID.randomUUID(), "P" + seat.index())));
            session.submit(new GameEvent.DeckLoaded(seat, GameFixtures.deck(40), List.of()));
            session.submit(new GameEvent.CardsDrawn(seat, seat, 7));
        }
        return session;
    }

    @Test
    @DisplayName("a mulligan draws a fresh hand and counts itself")
    void aMulliganRedrawsAndCounts() {
        GameSession session = sitting(2);
        SeatId me = SeatId.of(0);

        session.submit(new GameEvent.Mulliganed(me, me, 7));

        assertThat(session.state().seatState(me).mulligans()).isEqualTo(1);
        assertThat(session.state().count(ZoneRef.of(me, Zone.HAND))).isEqualTo(7);
    }

    @Test
    @DisplayName("the count keeps going up, however many are taken")
    void mulligansAccumulate() {
        GameSession session = sitting(4);
        SeatId me = SeatId.of(2);

        for (int taken = 1; taken <= 3; taken++) {
            session.submit(new GameEvent.Mulliganed(me, me, 7));
            assertThat(session.state().seatState(me).mulligans()).isEqualTo(taken);
        }
    }

    @Test
    @DisplayName("the log says what happened and not what to do about it")
    void theLogSaysOnlyWhatHappened() {
        GameSession session = sitting(2);
        SeatId me = SeatId.of(0);

        session.submit(new GameEvent.Mulliganed(me, me, 7));

        var entry = session.log().get(session.log().size() - 1);
        assertThat(entry.key()).isEqualTo("log.gathering.mulliganed");
        // Three: who, whose seat, and how big the new hand is. A fourth was how many cards to put
        // on the bottom, which is the sentence this mod is not in a position to write.
        assertThat(entry.args()).hasSize(3);
    }

    /**
     * Putting a card under your own library is an ordinary move, with nothing counting it.
     * <p>It used to pay off a mulligan's debt, which meant the mod was watching where cards went
     * and deciding what that meant. A move is a move.
     */
    @Test
    @DisplayName("a card from the hand to the bottom is just a card going to the bottom")
    void bottomingACardIsAnOrdinaryMove() {
        GameSession session = sitting(2);
        SeatId me = SeatId.of(0);
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        List<CardInstanceId> hand = session.state().contents(me, Zone.HAND);

        session.submit(new GameEvent.CardMoved(me, hand.get(0), ZoneRef.of(me, Zone.LIBRARY), Placement.BOTTOM));

        assertThat(session.state().seatState(me).mulligans()).isEqualTo(1);
        assertThat(session.state().count(ZoneRef.of(me, Zone.HAND))).isEqualTo(6);
    }
}
