package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The London mulligan, counted and never enforced: rule 103.5, and 103.5c's free first mulligan
 * in a multiplayer game.
 */
class LondonMulliganTest {

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
    @DisplayName("two players: each mulligan owes one more card to the bottom")
    void inATwoPlayerGameEveryMulliganCounts() {
        GameSession session = sitting(2);
        SeatId me = SeatId.of(0);
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        assertThat(session.state().seatState(me).mulligans()).isEqualTo(1);
        assertThat(session.state().seatState(me).owedToBottom()).isEqualTo(1);
        assertThat(session.state().count(ZoneRef.of(me, Zone.HAND))).isEqualTo(7);
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        assertThat(session.state().seatState(me).owedToBottom()).isEqualTo(2);
    }

    @Test
    @DisplayName("three or more players: the first mulligan is free")
    void inAMultiplayerGameTheFirstMulliganIsFree() {
        GameSession session = sitting(4);
        SeatId me = SeatId.of(2);
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        assertThat(session.state().seatState(me).owedToBottom()).isZero();
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        assertThat(session.state().seatState(me).owedToBottom()).isEqualTo(1);
    }

    @Test
    @DisplayName("somebody who sat down and left before their deck went down is not a third player")
    void aChairWithANameAndNoDeckIsNotAPlayer() {
        GameSession session = sitting(2);
        SeatId passerBy = SeatId.of(2);
        List<SeatId> seats = new ArrayList<>(session.state().seats());
        if (!seats.contains(passerBy)) {
            session = GameSession.create(List.of(SeatId.of(0), SeatId.of(1), passerBy), 20, SessionSeed.random(),
                    UndoMode.shippedDefault());
            for (SeatId seat : List.of(SeatId.of(0), SeatId.of(1))) {
                session.submit(new GameEvent.SeatTaken(seat, new PlayerRef(UUID.randomUUID(), "P" + seat.index())));
                session.submit(new GameEvent.DeckLoaded(seat, GameFixtures.deck(40), List.of()));
                session.submit(new GameEvent.CardsDrawn(seat, seat, 7));
            }
        }
        session.submit(new GameEvent.SeatTaken(passerBy, new PlayerRef(UUID.randomUUID(), "Passer-by")));
        session.submit(new GameEvent.SeatReleased(passerBy));
        SeatId me = SeatId.of(0);
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        assertThat(session.state().seatState(me).owedToBottom()).isEqualTo(1);
    }

    @Test
    @DisplayName("a card from the hand to the bottom pays one off; to the top, or somebody else's, does not")
    void onlyTheBottomOfYourOwnLibraryPaysItOff() {
        GameSession session = sitting(2);
        SeatId me = SeatId.of(0);
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        List<CardInstanceId> hand = session.state().contents(me, Zone.HAND);

        session.submit(new GameEvent.CardMoved(me, hand.get(0), ZoneRef.of(me, Zone.LIBRARY), Placement.TOP));
        assertThat(session.state().seatState(me).owedToBottom()).isEqualTo(2);
        session.submit(new GameEvent.CardMoved(me, hand.get(1), ZoneRef.of(me, Zone.LIBRARY), Placement.BOTTOM));
        assertThat(session.state().seatState(me).owedToBottom()).isEqualTo(1);
        session.submit(new GameEvent.CardMoved(me, hand.get(2), ZoneRef.of(me, Zone.LIBRARY), Placement.BOTTOM));
        session.submit(new GameEvent.CardMoved(me, hand.get(3), ZoneRef.of(me, Zone.LIBRARY), Placement.BOTTOM));
        assertThat(session.state().seatState(me).owedToBottom()).isZero();
    }

    @Test
    @DisplayName("the log says how many go to the bottom")
    void theLogSaysWhatIsOwed() {
        GameSession session = sitting(2);
        SeatId me = SeatId.of(0);
        session.submit(new GameEvent.Mulliganed(me, me, 7));
        var entry = session.log().get(session.log().size() - 1);
        assertThat(entry.key()).isEqualTo("log.gathering.mulliganed");
        assertThat(entry.args()).hasSize(4);
    }
}
