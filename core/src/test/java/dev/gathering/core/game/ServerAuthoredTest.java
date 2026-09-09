package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which events a client may send, decided once and checked against the events themselves.
 * <p>The list of what the server writes was a denylist, and a denylist of event types is a
 * list that falls behind the events. Dice, coins and the planar die were added after it was
 * written and were not in it, so a client could send a finished roll and the table's log -
 * its only evidence - agreed with whatever number arrived.
 * <p>This test is what makes the list keep up: it walks the sealed hierarchy, so an event
 * added tomorrow fails here until somebody has said which side of the wire writes it.
 */
class ServerAuthoredTest {

    /** Everything a client is allowed to author, named on purpose rather than by default. */
    private static final Set<String> A_CLIENT_MAY_SEND = Set.of(
            "SeatReleased", "CardMoved", "ZoneMoved", "CardNoted", "CardStrengthSet", "HandSorted",
            "CardFrozen", "CardTurnedOver", "CardTapSet", "CardRotated", "CardAttached",
            "SeatUntappedAll", "CardFacingSet", "CardsDrawn", "Mulliganed", "LibraryShuffled",
            "LibrarySearched", "LibraryClosed", "LibraryMilled", "LibraryExiled", "LibraryRevealed",
            "LibraryLooked", "LibraryReordered", "Surveiled", "CounterChanged", "TokenCreated",
            "TokenCopyCreated", "TokenRemoved", "PaperCardCreated", "HandShown", "SeatCounterChanged",
            "LifeChanged", "CommanderDamageChanged", "CommanderTaxChanged", "Conceded", "TurnPassed",
            "CardPinged");

    /** And everything the server writes, for the same reason. */
    private static final Set<String> THE_SERVER_WRITES = Set.of(
            "SeatTaken", "DeckLoaded", "SessionEnded", "DiceRolled", "CoinFlipped", "PlanarRolled");

    @Test
    @DisplayName("every event is on exactly one side of the wire")
    void everyEventIsClassified() {
        List<String> unclassified = new ArrayList<>();
        for (Class<?> type : GameEvent.class.getPermittedSubclasses()) {
            String name = type.getSimpleName();
            boolean client = A_CLIENT_MAY_SEND.contains(name);
            boolean server = THE_SERVER_WRITES.contains(name);
            if (client == server) {
                unclassified.add(name);
            }
        }
        assertThat(unclassified)
                .describedAs("an event nobody has said which side of the wire writes it")
                .isEmpty();
    }

    @Test
    @DisplayName("a result the server rolled is never a client's to report")
    void chanceIsTheServersToWrite() {
        assertThat(ServerAuthored.isTheServersToWrite(
                new GameEvent.DiceRolled(GameFixtures.ALICE, 20, 20))).isTrue();
        assertThat(ServerAuthored.isTheServersToWrite(
                new GameEvent.CoinFlipped(GameFixtures.ALICE, true))).isTrue();
        assertThat(ServerAuthored.isTheServersToWrite(new GameEvent.PlanarRolled(
                GameFixtures.ALICE, dev.gathering.core.game.event.PlanarFace.CHAOS))).isTrue();
        assertThat(ServerAuthored.isTheServersToWrite(
                new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -1))).isFalse();
    }

    /** The classification is the wire's, not the fold's: the server still writes them down. */
    @Test
    @DisplayName("the server's own roll is still folded and logged")
    void theServersOwnRollStillLands() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        int logged = session.log().size();

        assertThat(session.submit(new GameEvent.DiceRolled(GameFixtures.ALICE, 20, 17)).isAccepted())
                .isTrue();
        assertThat(session.log()).hasSize(logged + 1);
    }
}
