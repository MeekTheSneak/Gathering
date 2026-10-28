package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A frozen card does not untap")
class FrozenTest {

    @Test
    @DisplayName("untapping everything leaves a frozen card tapped")
    void frozenSurvivesTheUntapStep() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        CardInstanceId frozen = ontoTheTable(session, me);
        CardInstanceId ordinary = ontoTheTable(session, me);

        session.submit(new GameEvent.CardTapSet(me, frozen, true));
        session.submit(new GameEvent.CardTapSet(me, ordinary, true));
        session.submit(new GameEvent.CardFrozen(me, frozen, true));

        session.submit(new GameEvent.SeatUntappedAll(me, me));

        // This is the whole feature. Untapping is one press made every turn without looking,
        // so it is the press that quietly undoes what an opponent spent a card on.
        assertThat(session.state().requireCard(frozen).tapped()).isTrue();
        assertThat(session.state().requireCard(ordinary).tapped()).isFalse();
    }

    @Test
    @DisplayName("thawing it puts it back in the untap step")
    void thawingWorks() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        CardInstanceId card = ontoTheTable(session, me);

        session.submit(new GameEvent.CardTapSet(me, card, true));
        session.submit(new GameEvent.CardFrozen(me, card, true));
        session.submit(new GameEvent.SeatUntappedAll(me, me));
        assertThat(session.state().requireCard(card).tapped()).isTrue();

        session.submit(new GameEvent.CardFrozen(me, card, false));
        session.submit(new GameEvent.SeatUntappedAll(me, me));

        assertThat(session.state().requireCard(card).tapped()).isFalse();
    }

    @Test
    @DisplayName("freezing does not tap, and untapping by hand still works")
    void freezingIsNotTapping() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        SeatId me = session.state().seats().get(0);
        CardInstanceId card = ontoTheTable(session, me);

        session.submit(new GameEvent.CardFrozen(me, card, true));
        assertThat(session.state().requireCard(card).tapped()).isFalse();

        // Frozen stops the untap-everything press, not a player. There is no rules engine, so
        // a table that decided the freeze was over can simply untap the card.
        session.submit(new GameEvent.CardTapSet(me, card, true));
        session.submit(new GameEvent.CardTapSet(me, card, false));
        assertThat(session.state().requireCard(card).tapped()).isFalse();
        assertThat(session.state().requireCard(card).frozen()).isTrue();
    }

    @Test
    @DisplayName("freezing the same card twice is the same card")
    void freezingIsIdempotent() {
        CardInstance card = CardInstance.faceUp(
                new CardInstanceId(1),
                dev.gathering.core.card.CardIdentity.ofPrinting(java.util.UUID.randomUUID()),
                new SeatId(0));

        CardInstance frozen = card.frozen(true);
        assertThat(frozen.frozen()).isTrue();
        // Nothing to walk back through, so undo does not spend a step on a card that did not
        // change.
        assertThat(frozen.frozen(true)).isSameAs(frozen);
        assertThat(card.frozen(false)).isSameAs(card);
    }

    private static CardInstanceId ontoTheTable(GameSession session, SeatId seat) {
        session.submit(new GameEvent.CardsDrawn(seat, seat, 1));
        List<CardInstanceId> hand = session.state().contents(seat, Zone.HAND);
        CardInstanceId card = hand.get(hand.size() - 1);
        session.submit(new GameEvent.CardMoved(
                seat, card, ZoneRef.of(seat, Zone.BATTLEFIELD), Placement.BOTTOM));
        return card;
    }
}
