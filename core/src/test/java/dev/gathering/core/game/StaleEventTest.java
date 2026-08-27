package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Events naming cards that are not where the event claims, which is what lag and what a
 * hostile client both look like by the time the fold sees them.
 *
 * <p>Each one used to corrupt the board a different way: a phantom id in a public zone broke
 * every broadcast at the table for good, a crafted surveil walked another player's card off
 * their battlefield with a log line naming no card, and a reorder racing a draw put one card
 * in two zones at once.
 */
class StaleEventTest {

    @Test
    @DisplayName("a surveil naming a card the library never held moves nothing")
    void aPhantomIdCannotBeSurveiledIntoTheGraveyard() {
        GameSession session = GameFixtures.twoPlayerTable(10);

        GameSession.Result result = session.submit(new GameEvent.Surveiled(
                GameFixtures.ALICE, GameFixtures.ALICE,
                List.of(), List.of(new CardInstanceId(999_999))));

        assertThat(result.isAccepted()).isTrue();
        assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD))).isZero();
        assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY))).isEqualTo(10);
    }

    @Test
    @DisplayName("a surveil cannot walk another player's card into your graveyard")
    void aSurveilCannotTakeSomebodyElsesCard() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.BOB, GameFixtures.BOB, 1));
        CardInstanceId bobs = session.state()
                .contents(GameFixtures.BOB, Zone.HAND).get(0);

        session.submit(new GameEvent.Surveiled(
                GameFixtures.ALICE, GameFixtures.ALICE, List.of(), List.of(bobs)));

        assertThat(session.state().contents(GameFixtures.BOB, Zone.HAND)).containsExactly(bobs);
        assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.GRAVEYARD))).isZero();
    }

    @Test
    @DisplayName("a reorder racing a draw keeps the drawn card in the hand and only there")
    void aReorderRacingADrawCannotDuplicateTheCard() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        CardInstanceId top = GameFixtures.topOfLibrary(session, GameFixtures.ALICE);
        // The decision names the old top; the draw lands first.
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));

        session.submit(new GameEvent.LibraryReordered(
                GameFixtures.ALICE, GameFixtures.ALICE, List.of(top), List.of()));

        assertThat(session.state().contents(GameFixtures.ALICE, Zone.HAND)).containsExactly(top);
        assertThat(session.state().contents(GameFixtures.ALICE, Zone.LIBRARY)).doesNotContain(top);
        assertThat(session.state().count(ZoneRef.of(GameFixtures.ALICE, Zone.LIBRARY))).isEqualTo(9);
    }

    @Test
    @DisplayName("a phantom id can never enter a zone list at all")
    void placeRefusesAnUnknownCard() {
        GameSession session = GameFixtures.twoPlayerTable(3);

        GameState after = session.state().place(new CardInstanceId(999_999),
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.TOP);

        assertThat(after.count(ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD))).isZero();
    }

    @Test
    @DisplayName("a token event asking for two billion tokens makes thirty-two")
    void tokenCountIsBoundedOnTheEventItself() {
        // The typed payload clamps, but an event also arrives through the raw codec, and
        // each token copies the whole board - an unbounded count hung the server inside
        // one fold.
        GameEvent.TokenCreated event = new GameEvent.TokenCreated(
                GameFixtures.ALICE, GameFixtures.ALICE,
                dev.gathering.core.card.CardIdentity.ofPrinting(java.util.UUID.randomUUID()),
                Integer.MAX_VALUE);

        assertThat(event.count()).isEqualTo(GameEvent.TokenCreated.MOST_AT_ONCE);
    }

    @Test
    @DisplayName("a restored log never hands out a sequence number an undo already holds")
    void restoreCountsUndoRecordsToo() {
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 2));
        session.undo(GameFixtures.ALICE, 1, List.of());
        long undoSequence = session.records().get(session.records().size() - 1).sequence();

        GameSession restored = GameSession.restore(
                List.of(GameFixtures.ALICE, GameFixtures.BOB), 40, GameFixtures.FIXED_SEED,
                UndoMode.shippedDefault(), session.records());
        GameSession.Result next = restored.submit(
                new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));

        assertThat(next.isAccepted()).isTrue();
        long newest = restored.records().get(restored.records().size() - 1).sequence();
        assertThat(newest).isGreaterThan(undoSequence);
    }
}
