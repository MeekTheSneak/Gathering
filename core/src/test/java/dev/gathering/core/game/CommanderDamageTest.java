package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Commander damage is per commander, not per seat")
class CommanderDamageTest {

    @Test
    @DisplayName("two partners deal two separate twenty-ones")
    void partnersAreTrackedApart() {
        // The rule is 21 from the SAME commander, and a partner deck fields two. One number
        // per enemy seat could not tell Halana's damage from Tevesh's - the exact pair this
        // project's flagship decklist is named after.
        GameSession session = GameFixtures.twoPlayerTable(40);
        SeatId them = session.state().seats().get(0);
        SeatId me = session.state().seats().get(1);
        session.submit(new GameEvent.DeckLoaded(
                them,
                List.of(CardIdentity.ofPrinting(UUID.randomUUID())),
                List.of(CardIdentity.ofPrinting(UUID.randomUUID()),
                        CardIdentity.ofPrinting(UUID.randomUUID()))));

        List<CardInstanceId> partners = session.state().seatState(them).commanders();
        assertThat(partners).hasSize(2);

        session.submit(new GameEvent.CommanderDamageChanged(me, me, partners.get(0), 20));
        session.submit(new GameEvent.CommanderDamageChanged(me, me, partners.get(1), 3));

        var taken = session.state().seatState(me).commanderDamage();
        assertThat(taken.get(partners.get(0))).isEqualTo(20);
        assertThat(taken.get(partners.get(1))).isEqualTo(3);
    }

    @Test
    @DisplayName("the deck going down names the seat's commanders, wherever they go next")
    void commandersAreNamedAtLoad() {
        GameSession session = GameFixtures.twoPlayerTable(40);
        SeatId seat = session.state().seats().get(0);
        session.submit(new GameEvent.DeckLoaded(
                seat,
                List.of(CardIdentity.ofPrinting(UUID.randomUUID())),
                List.of(CardIdentity.ofPrinting(UUID.randomUUID()))));

        List<CardInstanceId> commanders = session.state().seatState(seat).commanders();
        assertThat(commanders).hasSize(1);

        // Onto the battlefield, where a commander spends most of a real game. Still the
        // commander: nothing about its zone says so, which is why the seat has to.
        session.submit(new GameEvent.CardMoved(
                seat, commanders.get(0), ZoneRef.of(seat, Zone.BATTLEFIELD), Placement.TOP));

        assertThat(session.state().seatState(seat).commanders()).isEqualTo(commanders);
    }

    @Test
    @DisplayName("damage wound back to nothing leaves no row behind")
    void zeroRowsDisappear() {
        GameSession session = GameFixtures.twoPlayerTable(40);
        SeatId me = session.state().seats().get(0);
        CardInstanceId commander = new CardInstanceId(7);

        // The map drops a zero rather than keeping a row of nothing, the same as every
        // other counter here - a grid of zeroes is the thing the rows exist to avoid.
        var state = SeatState.startingAt(me, 40)
                .withCommanderDamage(commander, 5)
                .withCommanderDamage(commander, -5);
        assertThat(state.commanderDamage()).isEmpty();
    }
}
