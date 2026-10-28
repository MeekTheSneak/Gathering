package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
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

    /**
     * Several cards moving at once are matched one for one, not merged into a crowd.
     * <p>Six cards discarded and six milled in the same update is two players tidying up,
     * and the pairing has to keep them apart - a leftover paired across the table would draw
     * somebody's discard flying into somebody else's graveyard.
     */
    @Test
    void severalMovesAtOnceStayWithTheirOwnSeats() {
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.HAND, 6);
        put(before, ME, Zone.GRAVEYARD, 0);
        put(before, THEM, Zone.LIBRARY, 40);
        put(before, THEM, Zone.GRAVEYARD, 0);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.HAND, 0);
        put(after, ME, Zone.GRAVEYARD, 6);
        put(after, THEM, Zone.LIBRARY, 34);
        put(after, THEM, Zone.GRAVEYARD, 6);

        List<CardTravel.Move> moves = CardTravel.between(before, after);

        assertThat(moves).hasSize(12);
        assertThat(moves).allSatisfy(move ->
                assertThat(move.from().seat()).isEqualTo(move.to().seat()));
    }

    /** A zone one board mentions and the other does not must not become a phantom move. */
    @Test
    void aSeatThatArrivesBringsNoCardsWithIt() {
        Map<CardTravel.Place, CardTravel.Held> before = board();
        put(before, ME, Zone.LIBRARY, 40);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.LIBRARY, 40);
        put(after, THEM, Zone.LIBRARY, 40);
        put(after, THEM, Zone.HAND, 7);

        // Somebody sat down. Nothing moved: their board simply was not there before.
        assertThat(CardTravel.between(before, after)).allSatisfy(move ->
                assertThat(move.from().seat()).isEqualTo(move.to().seat()));
    }

    /** However much moves at once, working out what moved has to finish. */
    @Test
    void aBoardWhereEverythingChangesStillSettles() {
        Map<CardTravel.Place, CardTravel.Held> before = board();
        Map<CardTravel.Place, CardTravel.Held> after = board();
        for (SeatId seat : new SeatId[] {ME, THEM}) {
            for (Zone zone : Zone.values()) {
                put(before, seat, zone, 9);
                put(after, seat, zone, zone == Zone.GRAVEYARD ? 60 : 0);
            }
        }
        assertThat(CardTravel.between(before, after)).isNotEmpty();
    }

    /**
     * A board nobody has looked at for a while is a first sighting, not a difference.
     * <p>A table stops sending boards when it goes out of range, and the last one it sent
     * stays in memory. Walking back to a game half an hour later and comparing against that
     * would set off every card that moved in the meantime at once - the exact flock of cards
     * the first-sighting rule exists to prevent, arriving by the other door.
     */
    @Test
    void twoSightingsFarApartAreNotADifference() {
        assertThat(CardTravel.worthComparing(0)).isTrue();
        assertThat(CardTravel.worthComparing(2_000)).isTrue();
        assertThat(CardTravel.worthComparing(CardTravel.WORTH_COMPARING)).isTrue();
        assertThat(CardTravel.worthComparing(CardTravel.WORTH_COMPARING + 1)).isFalse();
        assertThat(CardTravel.worthComparing(30 * 60 * 1000L)).isFalse();
    }

    /** A clock that has gone backwards is not a board seen in the future. */
    @Test
    void aGapThatRunsBackwardsIsNotADifferenceEither() {
        assertThat(CardTravel.worthComparing(-1)).isFalse();
    }

    /**
     * A creature pushed forward to attack has not changed zones and has certainly moved.
     * <p>The most common movement in the game, and the one that stayed a teleport longest:
     * for everybody except the player whose hand did it, the card was here and then it was
     * there with nothing in between.
     */
    @Test
    void aCardSlidAcrossItsOwnMatIsAMove() {
        CardInstanceId bear = card();
        Map<CardTravel.Place, CardTravel.Held> before = board();
        at(before, ME, Zone.BATTLEFIELD, bear, TablePosition.of(1000, 1000));
        Map<CardTravel.Place, CardTravel.Held> after = board();
        at(after, ME, Zone.BATTLEFIELD, bear, TablePosition.of(6000, 2000));

        List<CardTravel.Move> moves = CardTravel.between(before, after);

        assertThat(moves).hasSize(1);
        CardTravel.Move move = moves.get(0);
        assertThat(move.card()).contains(bear);
        assertThat(move.from()).isEqualTo(move.to());
        assertThat(move.fromSpot()).contains(TablePosition.of(1000, 1000));
        assertThat(move.toSpot()).contains(TablePosition.of(6000, 2000));
    }

    /**
     * Turning a card is not moving it.
     * <p>A position carries an angle as well as a place, so comparing whole positions would
     * make untapping a board a board's worth of cards flying from where they are to where
     * they already are.
     */
    @Test
    void tappingACardDoesNotSendItAnywhere() {
        CardInstanceId bear = card();
        Map<CardTravel.Place, CardTravel.Held> before = board();
        at(before, ME, Zone.BATTLEFIELD, bear, TablePosition.of(1000, 1000, 0));
        Map<CardTravel.Place, CardTravel.Held> after = board();
        at(after, ME, Zone.BATTLEFIELD, bear, TablePosition.of(1000, 1000, 90));

        assertThat(CardTravel.between(before, after)).isEmpty();
    }

    /** A card leaving a mat flies from where it sat, not from the middle of the mat. */
    @Test
    void aCardLeavingAMatRemembersWhereItSat() {
        CardInstanceId bear = card();
        Map<CardTravel.Place, CardTravel.Held> before = board();
        at(before, ME, Zone.BATTLEFIELD, bear, TablePosition.of(7500, 3000));
        put(before, ME, Zone.GRAVEYARD, 0);
        Map<CardTravel.Place, CardTravel.Held> after = board();
        put(after, ME, Zone.BATTLEFIELD, 0);
        put(after, ME, Zone.GRAVEYARD, 1, bear);

        List<CardTravel.Move> moves = CardTravel.between(before, after);

        assertThat(moves).hasSize(1);
        assertThat(moves.get(0).fromSpot()).contains(TablePosition.of(7500, 3000));
        assertThat(moves.get(0).toSpot()).isEmpty();
    }

    private static void at(
            Map<CardTravel.Place, CardTravel.Held> board,
            SeatId seat, Zone zone, CardInstanceId card, TablePosition spot) {
        board.put(new CardTravel.Place(seat, zone),
                new CardTravel.Held(1, List.of(card), Map.of(card, spot)));
    }
}
