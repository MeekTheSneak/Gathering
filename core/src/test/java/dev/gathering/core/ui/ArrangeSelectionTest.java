package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tidying a handful of cards, worked out before anything moves. */
class ArrangeSelectionTest {

    private static ArrangeSelection.Card at(int id, int x, int y) {
        return new ArrangeSelection.Card(CardInstanceId.of(id), new TablePosition(x, y, 0), null);
    }

    private static ArrangeSelection.Card turned(int id, int x, int y, int rotation) {
        return new ArrangeSelection.Card(
                CardInstanceId.of(id), new TablePosition(x, y, rotation), null);
    }

    private static ArrangeSelection.Card onto(int id, int x, int y, int host) {
        return new ArrangeSelection.Card(
                CardInstanceId.of(id), new TablePosition(x, y, 0), CardInstanceId.of(host));
    }

    @Nested
    @DisplayName("a plan")
    class APlan {

        @Test
        @DisplayName("gives every loose card exactly one spot")
        void oneSpotEach() {
            List<ArrangeSelection.Spot> plan =
                    ArrangeSelection.plan(List.of(at(1, 100, 100), at(2, 500, 100), at(3, 900, 800)));
            assertThat(plan).hasSize(3);
            assertThat(plan.stream().map(ArrangeSelection.Spot::id).distinct()).hasSize(3);
        }

        @Test
        @DisplayName("never names a card it was not given")
        void onlyWhatWasChosen() {
            List<ArrangeSelection.Card> chosen = List.of(at(1, 100, 100), at(2, 500, 100));
            List<CardInstanceId> ids = ArrangeSelection.plan(chosen).stream()
                    .map(ArrangeSelection.Spot::id).toList();
            assertThat(ids).containsExactlyInAnyOrder(CardInstanceId.of(1), CardInstanceId.of(2));
        }

        @Test
        @DisplayName("leaves an attached card to its host")
        void attachedCardsAreNotPlaced() {
            // An aura given a spot of its own is an aura torn off the creature it is on.
            List<ArrangeSelection.Spot> plan = ArrangeSelection.plan(
                    List.of(at(1, 100, 100), at(2, 500, 100), onto(3, 120, 110, 1)));
            assertThat(plan.stream().map(ArrangeSelection.Spot::id))
                    .containsExactlyInAnyOrder(CardInstanceId.of(1), CardInstanceId.of(2))
                    .doesNotContain(CardInstanceId.of(3));
        }

        @Test
        @DisplayName("keeps every card's own angle")
        void rotationIsNotTouched() {
            // A card turned sideways is tapped and a card turned because somebody likes it is
            // theirs. Neither is untidiness.
            List<ArrangeSelection.Spot> plan =
                    ArrangeSelection.plan(List.of(turned(1, 100, 100, 90), turned(2, 500, 100, 17)));
            assertThat(plan.get(0).to().rotation()).isEqualTo(90);
            assertThat(plan.get(1).to().rotation()).isEqualTo(17);
        }

        @Test
        @DisplayName("keeps the order the board was already in")
        void readingOrderIsKept() {
            // Somebody who grouped their board still has it grouped afterwards: what changes
            // is the spacing, not the arrangement.
            List<ArrangeSelection.Spot> plan = ArrangeSelection.plan(List.of(
                    at(7, 8000, 200),
                    at(3, 100, 200),
                    at(9, 400, 9000)));
            assertThat(plan.stream().map(ArrangeSelection.Spot::id))
                    .containsExactly(CardInstanceId.of(3), CardInstanceId.of(7),
                            CardInstanceId.of(9));
        }

        @Test
        @DisplayName("is the same plan twice")
        void deterministic() {
            // What lets a preview be trusted: the board drawn as a promise is the board that
            // arrives.
            List<ArrangeSelection.Card> cards =
                    List.of(at(4, 900, 100), at(1, 100, 100), at(9, 500, 500));
            assertThat(ArrangeSelection.plan(cards)).isEqualTo(ArrangeSelection.plan(cards));
        }

        @Test
        @DisplayName("settles cards that share a spot rather than leaving it to chance")
        void tiesAreBroken() {
            List<ArrangeSelection.Card> one = List.of(at(2, 300, 300), at(1, 300, 300));
            List<ArrangeSelection.Card> other = List.of(at(1, 300, 300), at(2, 300, 300));
            assertThat(ArrangeSelection.plan(one)).isEqualTo(ArrangeSelection.plan(other));
        }

        @Test
        @DisplayName("stays on the mat")
        void everySpotIsOnTheTable() {
            List<ArrangeSelection.Card> many = new ArrayList<>();
            for (int card = 0; card < 40; card++) {
                many.add(at(card + 1, card * 137 % TablePosition.SPAN,
                        card * 311 % TablePosition.SPAN));
            }
            for (ArrangeSelection.Spot spot : ArrangeSelection.plan(many)) {
                assertThat(spot.to().x())
                        .isBetween(ArrangeSelection.MARGIN, TablePosition.SPAN - ArrangeSelection.MARGIN);
                assertThat(spot.to().y())
                        .isBetween(ArrangeSelection.MARGIN, TablePosition.SPAN - ArrangeSelection.MARGIN);
            }
        }

        @Test
        @DisplayName("does nothing for one card, which is already tidy")
        void oneCardIsAlreadyTidy() {
            assertThat(ArrangeSelection.plan(List.of(at(1, 4000, 4000)))).isEmpty();
        }

        @Test
        @DisplayName("does nothing for nothing")
        void nothingToDo() {
            assertThat(ArrangeSelection.plan(List.of())).isEmpty();
            assertThat(ArrangeSelection.plan(null)).isEmpty();
        }

        @Test
        @DisplayName("does nothing when everything chosen is attached")
        void allAttachedIsNothingToPlace() {
            assertThat(ArrangeSelection.plan(List.of(onto(2, 100, 100, 1), onto(3, 120, 110, 1))))
                    .isEmpty();
        }

        @Test
        @DisplayName("gives no two cards the same spot")
        void nothingLandsOnAnythingElse() {
            List<ArrangeSelection.Card> many = new ArrayList<>();
            for (int card = 0; card < 12; card++) {
                many.add(at(card + 1, card * 700, card * 500));
            }
            List<TablePosition> spots = ArrangeSelection.plan(many).stream()
                    .map(ArrangeSelection.Spot::to)
                    .map(where -> new TablePosition(where.x(), where.y(), 0))
                    .toList();
            assertThat(spots).doesNotHaveDuplicates();
        }
    }
    @Nested
    @DisplayName("a plan that has gone stale")
    class Stale {

        private static java.util.Set<CardInstanceId> onTheBattlefield(int... ids) {
            java.util.Set<CardInstanceId> here = new java.util.HashSet<>();
            for (int id : ids) {
                here.add(CardInstanceId.of(id));
            }
            return here;
        }

        @Test
        @DisplayName("is noticed when one of its cards has left the battlefield")
        void noticesACardThatLeft() {
            // The audit's case: two cards planned, one sent to the graveyard before the
            // preview was agreed to. Applying it as drawn would put that card back.
            List<ArrangeSelection.Spot> plan =
                    ArrangeSelection.plan(List.of(at(1, 100, 100), at(2, 500, 100)));
            assertThat(ArrangeSelection.isStale(plan, onTheBattlefield(1, 2))).isFalse();
            assertThat(ArrangeSelection.isStale(plan, onTheBattlefield(1))).isTrue();
        }

        @Test
        @DisplayName("moves only the cards that are still there")
        void movesOnlyWhatIsStillThere() {
            List<ArrangeSelection.Spot> plan =
                    ArrangeSelection.plan(List.of(at(1, 100, 100), at(2, 500, 100)));
            List<ArrangeSelection.Spot> kept =
                    ArrangeSelection.stillStanding(plan, onTheBattlefield(1));
            assertThat(kept).hasSize(1);
            assertThat(kept.getFirst().id()).isEqualTo(CardInstanceId.of(1));
        }

        @Test
        @DisplayName("moves nothing at all when the board is unknown")
        void nothingWhenTheBoardIsUnknown() {
            // A screen with no board must not fall back to "move everything where the old plan
            // said", which is the failure this whole pair exists to prevent.
            List<ArrangeSelection.Spot> plan =
                    ArrangeSelection.plan(List.of(at(1, 100, 100), at(2, 500, 100)));
            assertThat(ArrangeSelection.stillStanding(plan, null)).isEmpty();
            assertThat(ArrangeSelection.isStale(plan, null)).isTrue();
        }

        @Test
        @DisplayName("is not stale when there was no plan to begin with")
        void noPlanIsNotStale() {
            assertThat(ArrangeSelection.isStale(List.of(), onTheBattlefield())).isFalse();
            assertThat(ArrangeSelection.isStale(null, onTheBattlefield())).isFalse();
        }

        @Test
        @DisplayName("keeps every card when nothing has moved")
        void unchangedBoardKeepsEverything() {
            List<ArrangeSelection.Spot> plan = ArrangeSelection.plan(
                    List.of(at(1, 100, 100), at(2, 500, 100), at(3, 900, 800)));
            assertThat(ArrangeSelection.stillStanding(plan, onTheBattlefield(1, 2, 3)))
                    .isEqualTo(plan);
        }
    }
}