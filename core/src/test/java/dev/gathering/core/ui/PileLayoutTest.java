package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A pile that scrolls must not answer for the space under it.
 * <p>The box lays every card out, including the ones below the fold, and the slots below the
 * fold are taller than the gap between the grid and the Done button. A screen that asked
 * whether the pointer was inside any slot therefore got yes for an invisible card sitting
 * across its own way out - and the only way to leave the box was Escape.
 */
class PileLayoutTest {

    /** The pile box's own numbers, so this asks about the shape that ships. */
    private static final int CARD_HEIGHT = 84;
    private static final int GAP = 4;
    private static final int MARGIN = 14;
    private static final int FOOTER = 40;
    private static final int BUTTON_HEIGHT = 16;

    @Test
    @DisplayName("a card scrolled out of the box cannot be clicked")
    void aCardBelowTheFoldIsNotUnderThePointer() {
        PileLayout pile = pile(0);
        // Twelve cards in a three-wide box two rows tall: the last two rows are below it.
        int belowTheFold = pile.grid().bottom() + 10;
        assertThat(pile.slotAt(12, pile.grid().x() + 5, belowTheFold))
                .describedAs("a point under the grid picked out a card")
                .isEqualTo(-1);
        // And the ordinary case still works.
        assertThat(pile.slotAt(12, pile.grid().x() + 5, pile.grid().y() + 5)).isEqualTo(0);
    }

    @Test
    @DisplayName("no card slot ever answers for the Done button, at any scroll")
    void nothingCoversTheWayOut() {
        Rect done = new Rect(
                panel().x() + (panel().width() - 60) / 2,
                panel().bottom() - MARGIN - BUTTON_HEIGHT, 60, BUTTON_HEIGHT);

        for (int scroll = 0; scroll <= 400; scroll++) {
            PileLayout pile = pile(scroll);
            for (int x = done.x(); x < done.right(); x += 7) {
                for (int y = done.y(); y < done.bottom(); y += 5) {
                    assertThat(pile.slotAt(40, x, y))
                            .describedAs("at scroll %s, the point (%s, %s) on the Done button"
                                    + " belongs to card %s", scroll, x, y, pile.slotAt(40, x, y))
                            .isEqualTo(-1);
                }
            }
        }
    }

    private static Rect panel() {
        return new Rect(40, 30, 260, 300);
    }

    private static PileLayout pile(int scroll) {
        Rect panel = panel();
        int cardWidth = 60;
        int columns = 3;
        int across = columns * (cardWidth + GAP) - GAP;
        int header = 20;
        Rect grid = new Rect(
                panel.x() + (panel.width() - across) / 2,
                panel.y() + MARGIN + header,
                across,
                panel.height() - MARGIN * 2 - header - FOOTER);
        return new PileLayout(grid, columns, cardWidth, CARD_HEIGHT, GAP, scroll);
    }

    /** A row laid out the way the pile screen lays one: one line of cards, no scroll. */
    private static PileLayout aRowOf(int cards) {
        return new PileLayout(new Rect(20, 40, cards * 34, 60), cards, 30, 42, 4, 0);
    }

    @Test
    @DisplayName("the far end of the row can be reached")
    void theFarEndIsReachable() {
        // Every point over the last card meaning "before it" leaves no way to say "after
        // everything", so the one place a card most often goes is the one place unreachable.
        PileLayout row = aRowOf(4);
        Rect last = row.slot(3);

        assertThat(row.gapAt(4, last.x() + 1, last.y() + 2)).isEqualTo(3);
        assertThat(row.gapAt(4, last.right() - 1, last.y() + 2)).isEqualTo(4);
    }

    @Test
    @DisplayName("a drag onto an empty row lands at the front")
    void anEmptyRow() {
        assertThat(aRowOf(1).gapAt(0, 500, 500)).isZero();
    }

    @Property
    @Label("every gap in the row is reachable from somewhere, and no other value ever comes out")
    void everyGapIsReachable(@ForAll @IntRange(min = 1, max = 8) int cards) {
        PileLayout row = aRowOf(cards);
        java.util.Set<Integer> reached = new java.util.HashSet<>();
        for (int index = 0; index < cards; index++) {
            Rect slot = row.slot(index);
            for (int x = slot.x(); x < slot.right(); x++) {
                int gap = row.gapAt(cards, x, slot.y() + 2);
                assertThat(gap).isBetween(0, cards);
                reached.add(gap);
            }
        }
        for (int gap = 0; gap <= cards; gap++) {
            assertThat(reached).contains(gap);
        }
    }

    @Property
    @Label("the bar drawn during a drag and the release agree, because they are one rule")
    void theBarAndTheReleaseAgree(
            @ForAll @IntRange(min = 1, max = 8) int cards,
            @ForAll @IntRange(min = -50, max = 400) int x,
            @ForAll @IntRange(min = -50, max = 200) int y) {
        PileLayout row = aRowOf(cards);

        assertThat(row.gapAt(cards, x, y)).isEqualTo(row.gapAt(cards, x, y));
        assertThat(row.gapAt(cards, x, y)).isBetween(0, cards);
    }

    @Property
    @Label("a drag always lands somewhere, however far outside the grid it is let go")
    void aDragAlwaysLandsSomewhere(
            @ForAll @IntRange(min = -500, max = 900) int x,
            @ForAll @IntRange(min = -500, max = 900) int y) {
        PileLayout row = aRowOf(5);

        assertThat(row.nearestSlot(x, y)).isGreaterThanOrEqualTo(0);
        assertThat(row.gapAt(5, x, y)).isBetween(0, 5);
    }
}
