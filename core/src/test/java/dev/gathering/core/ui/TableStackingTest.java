package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Telling a pile of cards apart from one card.
 * <p>Nothing here has thickness, so two cards on the same spot draw in the same place and the
 * lower one stops existing as far as anybody looking can tell. That is the failure this is
 * about: not a cosmetic one, because a permanent nobody can see is a permanent that is not in
 * the game.
 */
class TableStackingTest {

    /** A card's width in whatever the caller is measuring in; pixels, for these. */
    private static final int CARD = 60;

    private static final TablePosition SPOT = TablePosition.of(4000, 3000);

    @Nested
    @DisplayName("what counts as stacked")
    class WhatCountsAsStacked {

        @Test
        @DisplayName("two cards on the same spot are a stack")
        void sameSpotIsAStack() {
            assertThat(TableStacking.depths(List.of(SPOT, SPOT))).containsExactly(0, 1);
        }

        @Test
        @DisplayName("a card half-covering another is beside it, not on it")
        void partialOverlapIsNotAStack() {
            // Overlapping deliberately is how you show an aura on a creature, and it reads
            // correctly on its own. Staggering it would move a card somebody placed by hand.
            TablePosition beside = TablePosition.of(SPOT.x() + TableStacking.TIGHT * 4, SPOT.y());

            assertThat(TableStacking.depths(List.of(SPOT, beside))).containsExactly(0, 0);
        }

        @Test
        @DisplayName("a card a hair off is still a stack, because a hand is not that steady")
        void nearlyTheSameSpotIsAStack() {
            TablePosition nudged = TablePosition.of(
                    SPOT.x() + TableStacking.TIGHT, SPOT.y() - TableStacking.TIGHT);

            assertThat(TableStacking.isStackedOn(nudged, SPOT)).isTrue();
        }

        @Test
        @DisplayName("a card cannot be sitting on one that is on top of it")
        void stackingOnlyLooksDownwards() {
            List<TablePosition> pile = List.of(SPOT, SPOT, SPOT);

            assertThat(TableStacking.depths(pile)).containsExactly(0, 1, 2);
        }

        @Test
        @DisplayName("a card the game has not put down is not under anything")
        void cardsWithNoPlaceAreNotInPiles() {
            List<TablePosition> mixed = Arrays.asList(null, SPOT, null);

            assertThat(TableStacking.depths(mixed)).containsExactly(0, 0, 0);
            assertThat(TableStacking.pileSizeAt(mixed, 0)).isZero();
        }

        @Test
        @DisplayName("a card put on the top of a stack is the next card of that stack, not a new one")
        void aStackIsWhole() {
            // The owner's failure: the top card a hair off the bottom one, and a card put on the top
            // card a hair further. Asked pair by pair, the third card was on the second and not on
            // the first - a new stack of two made out of the old stack's top card.
            TablePosition second = TablePosition.of(SPOT.x() + TableStacking.TIGHT, SPOT.y());
            TablePosition third = TablePosition.of(SPOT.x() + TableStacking.TIGHT * 2, SPOT.y());
            List<TablePosition> pile = List.of(SPOT, second, third);

            TableStacking.Piles piles = TableStacking.piles(pile);
            assertThat(piles.depths()).containsExactly(0, 1, 2);
            for (int index = 0; index < pile.size(); index++) {
                assertThat(piles.pileSize(index)).as("stack size from card %d", index).isEqualTo(3);
                assertThat(piles.baseOf(index)).isZero();
            }
            assertThat(piles.isBuried(1)).isTrue();
        }

        @Test
        @DisplayName("angle does not decide whether two cards are stacked")
        void turningACardDoesNotUnstackIt() {
            // A tapped card lying across the one it is on is still on it.
            TablePosition turned = TablePosition.of(SPOT.x(), SPOT.y(), 90);

            assertThat(TableStacking.isStackedOn(turned, SPOT)).isTrue();
        }
    }

    @Nested
    @DisplayName("how a stack is drawn")
    class HowAStackIsDrawn {

        @Test
        @DisplayName("the bottom card of a pile is drawn where it actually is")
        void theBottomCardDoesNotMove() {
            assertThat(TableStacking.offsetFor(0, CARD)).isZero();
        }

        @Test
        @DisplayName("each card above leans further, so the edges of the ones below show")
        void eachCardLeansFurther() {
            assertThat(TableStacking.offsetFor(1, CARD)).isLessThan(TableStacking.offsetFor(0, CARD));
            assertThat(TableStacking.offsetFor(2, CARD)).isLessThan(TableStacking.offsetFor(1, CARD));
        }

        @Test
        @DisplayName("a very tall pile stops leaning rather than walking off the table")
        void theStaggerRunsOut() {
            assertThat(TableStacking.offsetFor(40, CARD)).isEqualTo(TableStacking.offsetFor(TableStacking.MAX_DEPTH, CARD));
        }

        @Test
        @DisplayName("a pile says how many are in it, and a lone card says nothing")
        void pilesAreCounted() {
            assertThat(TableStacking.pileSizeAt(List.of(SPOT, SPOT, SPOT), 2)).isEqualTo(3);
            assertThat(TableStacking.pileSizeAt(List.of(SPOT), 0)).isZero();
        }

        @Test
        @DisplayName("every card in a pile agrees how big the pile is")
        void theCountIsTheWholePileFromAnywhereInIt() {
            List<TablePosition> pile = List.of(SPOT, SPOT, SPOT, SPOT);

            for (int index = 0; index < pile.size(); index++) {
                assertThat(TableStacking.pileSizeAt(pile, index))
                        .describedAs("pile size seen from card %s", index)
                        .isEqualTo(4);
            }
        }

        @Test
        @DisplayName("a card with something on top of it knows it is buried")
        void buriedCardsKnowIt() {
            List<TablePosition> pile = List.of(SPOT, SPOT);

            assertThat(TableStacking.isBuriedAt(pile, 0)).isTrue();
            assertThat(TableStacking.isBuriedAt(pile, 1)).isFalse();
        }
    }

    @Property(tries = 2000)
    void aCardIsNeverStackedOnItself(
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int x,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int y,
            @ForAll @IntRange(min = 1, max = 12) int count) {
        // Every card in a pile counts everything under it and nothing else, so the depths of a
        // pile of n are 0..n-1 and the deepest one is n-1 rather than n.
        List<TablePosition> pile = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            pile.add(TablePosition.of(x, y));
        }

        assertThat(TableStacking.depths(pile)).hasSize(count);
        assertThat(TableStacking.depths(pile).get(count - 1)).isEqualTo(count - 1);
    }

    @Property(tries = 3000)
    void theStaggerIsTheSameShareOfTheCardWhateverTheCardIsMeasuredIn(
            @ForAll @IntRange(min = 1, max = 5) int depth) {
        // The two views measure a card in units that differ by a factor of thousands, and the
        // lean has to read the same in both. A fixed step passes every other test in this
        // class and makes every pile on the table in the world look like one card.
        int onScreen = 60;
        int onTheTable = 490;

        double screenShare = TableStacking.offsetFor(depth, onScreen) / (double) onScreen;
        double tableShare = TableStacking.offsetFor(depth, onTheTable) / (double) onTheTable;

        assertThat(tableShare).isCloseTo(screenShare, org.assertj.core.data.Offset.offset(0.01));
        assertThat(TableStacking.offsetFor(depth, onTheTable))
                .describedAs("a lean you could actually see on the table")
                .isLessThan(-1);
    }

    @Property(tries = 2000)
    void thestaggerNeverExceedsItsBound(@ForAll @IntRange(min = -5, max = 500) int depth) {
        assertThat(Math.abs(TableStacking.offsetFor(depth, CARD)))
                .isLessThanOrEqualTo((int) Math.ceil(TableStacking.MAX_DEPTH * TableStacking.STEP * CARD));
    }

    /**
     * How deep a card is drawn and how far it is offset are one answer, not two.
     * <p>They were two: the offset ran out at MAX_DEPTH and the height did not, so the board
     * drawn on the block lifted a card of depth nine nine steps while staggering it five -
     * and anything positioned to clear the deepest stack, like a card flying over one, went
     * underneath a pile of six.
     */
    @Test
    void aStackIsNeverDrawnDeeperThanItIsStaggered() {
        for (int depth = 0; depth < 40; depth++) {
            assertThat(TableStacking.shownDepth(depth))
                    .describedAs("depth %d", depth)
                    .isBetween(0, TableStacking.MAX_DEPTH);
            assertThat(TableStacking.offsetFor(depth, 100))
                    .isEqualTo(TableStacking.offsetFor(TableStacking.shownDepth(depth), 100));
        }
        assertThat(TableStacking.shownDepth(-4)).isZero();
    }

    @Property(tries = 500)
    void workingItOutInOnePassGivesTheSameAnswersAsAskingOneCardAtATime(
            @ForAll("boards") List<TablePosition> board) {
        // The grid only compares neighbouring cells, so the thing to prove is that nothing
        // stacked is ever two cells apart - including on a cell boundary, at the table's edges, and on
        // cards with no position. The walk below is the definition, written out plainly: each card
        // rests on the latest earlier card it lies on, and is one more card of that card's stack.
        TableStacking.Piles piles = TableStacking.piles(board);
        assertThat(piles.size()).isEqualTo(board.size());
        int[] stack = new int[board.size()];
        int[] depth = new int[board.size()];
        int[] base = new int[board.size()];
        for (int index = 0; index < board.size(); index++) {
            stack[index] = index;
            base[index] = index;
            if (board.get(index) == null) {
                continue;
            }
            for (int other = index - 1; other >= 0; other--) {
                if (board.get(other) != null && TableStacking.isStackedOn(board.get(index), board.get(other))) {
                    stack[index] = stack[other];
                    base[index] = base[other];
                    int under = 0;
                    for (int earlier = 0; earlier < index; earlier++) {
                        if (board.get(earlier) != null && stack[earlier] == stack[index]) {
                            under++;
                        }
                    }
                    depth[index] = under;
                    break;
                }
            }
        }
        for (int index = 0; index < board.size(); index++) {
            int size = 0;
            int top = -1;
            for (int other = 0; other < board.size(); other++) {
                if (board.get(other) != null && board.get(index) != null && stack[other] == stack[index]) {
                    size++;
                    top = other;
                }
            }
            boolean covered = board.get(index) != null && top != index;
            assertThat(piles.depth(index)).as("depth of %d", index).isEqualTo(board.get(index) == null ? 0 : depth[index]);
            assertThat(piles.pileSize(index)).as("pile at %d", index).isEqualTo(size > 1 ? size : 0);
            assertThat(piles.isBuried(index)).as("buried at %d", index).isEqualTo(covered);
            assertThat(piles.baseOf(index)).as("base of %d", index).isEqualTo(base[index]);
            assertThat(TableStacking.pileSizeAt(board, index)).isEqualTo(piles.pileSize(index));
            assertThat(TableStacking.isBuriedAt(board, index)).isEqualTo(piles.isBuried(index));
        }
    }

    /**
     * Boards worth checking the grid against: clustered on a few spots so piles actually form,
     * spread either side of cell edges and the table's own edges, with the odd card that has
     * no position.
     */
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<List<TablePosition>> boards() {
        net.jqwik.api.Arbitrary<Integer> anchor = net.jqwik.api.Arbitraries.of(
                0, TableStacking.TIGHT, TableStacking.TIGHT + 1, 2 * (TableStacking.TIGHT + 1) - 1,
                5000, TablePosition.SPAN);
        net.jqwik.api.Arbitrary<Integer> jitter =
                net.jqwik.api.Arbitraries.integers().between(-2 * TableStacking.TIGHT, 2 * TableStacking.TIGHT);
        net.jqwik.api.Arbitrary<TablePosition> spot = net.jqwik.api.Combinators
                .combine(anchor, jitter, anchor, jitter)
                .as((ax, jx, ay, jy) -> TablePosition.of(onTheTable(ax + jx), onTheTable(ay + jy)));
        net.jqwik.api.Arbitrary<TablePosition> maybe = net.jqwik.api.Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(9, spot),
                net.jqwik.api.Tuple.of(1, net.jqwik.api.Arbitraries.just((TablePosition) null)));
        return maybe.list().ofMaxSize(60);
    }

    private static int onTheTable(int value) {
        return Math.clamp(value, 0, TablePosition.SPAN);
    }

    @Test
    @DisplayName("a pile of two hundred on one spot is still counted right")
    void oneBigPile() {
        List<TablePosition> pile = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            pile.add(SPOT);
        }
        TableStacking.Piles piles = TableStacking.piles(pile);
        assertThat(piles.depth(199)).isEqualTo(199);
        assertThat(piles.pileSize(0)).isEqualTo(200);
        assertThat(piles.isBuried(0)).isTrue();
        assertThat(piles.isBuried(199)).isFalse();
    }
}
