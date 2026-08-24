package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CardTravelTest {

    private static final SeatId ME = SeatId.of(0);
    private static final SeatId THEM = SeatId.of(1);

    @Test
    void aCardTheViewerCanNameIsFollowedByName() {
        CardInstanceId elf = card();
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.HAND, 1, elf);
        put(before, ME, Zone.BATTLEFIELD, 0);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.HAND, 0);
        put(after, ME, Zone.BATTLEFIELD, 1, elf);

        List<CardTravel.Move> moves = CardTravel.between(before, after);

        assertThat(moves).hasSize(1);
        assertThat(moves.get(0).card()).contains(elf);
        assertThat(moves.get(0).from().zone()).isEqualTo(Zone.HAND);
        assertThat(moves.get(0).to().zone()).isEqualTo(Zone.BATTLEFIELD);
    }

    /**
     * The case the whole thing exists for.
     *
     * <p>Nobody is sent a library's cards, and only its owner is sent their hand - so to
     * everybody else at the table a draw is two numbers changing and nothing else. That is
     * still a card going from there to here, and the table should see it go.
     */
    @Test
    void aDrawNobodyMaySeeIsStillACardLeavingTheLibraryForTheHand() {
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, THEM, Zone.LIBRARY, 40);
        put(before, THEM, Zone.HAND, 7);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, THEM, Zone.LIBRARY, 39);
        put(after, THEM, Zone.HAND, 8);

        List<CardTravel.Move> moves = CardTravel.between(before, after);

        assertThat(moves).hasSize(1);
        assertThat(moves.get(0).card()).isEmpty();
        assertThat(moves.get(0).from()).isEqualTo(new CardTravel.Place(THEM, Zone.LIBRARY));
        assertThat(moves.get(0).to()).isEqualTo(new CardTravel.Place(THEM, Zone.HAND));
    }

    @Test
    void aMoveWithinOneSeatIsPreferredToOneAcrossTheTable() {
        // Both players draw at once. Neither draw should be drawn as a card crossing over.
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.LIBRARY, 40);
        put(before, ME, Zone.HAND, 7);
        put(before, THEM, Zone.LIBRARY, 40);
        put(before, THEM, Zone.HAND, 7);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.LIBRARY, 39);
        put(after, ME, Zone.HAND, 8);
        put(after, THEM, Zone.LIBRARY, 39);
        put(after, THEM, Zone.HAND, 8);

        List<CardTravel.Move> moves = CardTravel.between(before, after);

        assertThat(moves).hasSize(2);
        assertThat(moves).allSatisfy(move ->
                assertThat(move.from().seat()).isEqualTo(move.to().seat()));
    }

    @Test
    void millingThreeIsThreeCardsGoingToTheGraveyard() {
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.LIBRARY, 40);
        put(before, ME, Zone.GRAVEYARD, 0);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.LIBRARY, 37);
        put(after, ME, Zone.GRAVEYARD, 3);

        List<CardTravel.Move> moves = CardTravel.between(before, after);

        assertThat(moves).hasSize(3);
        assertThat(moves).allSatisfy(move -> {
            assertThat(move.from().zone()).isEqualTo(Zone.LIBRARY);
            assertThat(move.to().zone()).isEqualTo(Zone.GRAVEYARD);
        });
    }

    /** A shuffle moves nothing between zones, so nothing should be drawn flying anywhere. */
    @Test
    void aShuffleIsNotAMove() {
        CardInstanceId one = card();
        CardInstanceId two = card();
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.GRAVEYARD, 2, one, two);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.GRAVEYARD, 2, two, one);

        assertThat(CardTravel.between(before, after)).isEmpty();
    }

    @Test
    void aBoardThatDidNotChangeProducesNothing() {
        Map<CardTravel.Place, CardTravel.Held> board = board();
        put(board, ME, Zone.LIBRARY, 40);
        put(board, ME, Zone.HAND, 7);

        assertThat(CardTravel.between(board, board)).isEmpty();
    }

    /**
     * A token appearing on the battlefield came from nowhere, and should fly from nowhere.
     *
     * <p>There is no zone that lost a card, so there is nothing to pair the gain with. A
     * guess here would be a card flying out of a library that never held it.
     */
    @Test
    void aCardAppearingFromNowhereIsNotGivenAnOrigin() {
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.BATTLEFIELD, 0);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.BATTLEFIELD, 1, card());

        assertThat(CardTravel.between(before, after)).isEmpty();
    }

    @Test
    void aCardLeavingTheTableAltogetherIsNotGivenADestination() {
        CardInstanceId token = card();
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.BATTLEFIELD, 1, token);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.BATTLEFIELD, 0);

        assertThat(CardTravel.between(before, after)).isEmpty();
    }

    private static Map<CardTravel.Place, CardTravel.Held> board() {
        return new LinkedHashMap<>();
    }

    private static void put(
            Map<CardTravel.Place, CardTravel.Held> board,
            SeatId seat, Zone zone, int count, CardInstanceId... seen) {
        board.put(new CardTravel.Place(seat, zone), new CardTravel.Held(count, List.of(seen)));
    }

    private static int nextCard = 1;

    private static CardInstanceId card() {
        return CardInstanceId.of(nextCard++);
    }
}
