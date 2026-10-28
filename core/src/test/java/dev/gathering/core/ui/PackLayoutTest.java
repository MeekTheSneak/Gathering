package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/** Every card in a pack is on screen, at every window size anybody plays at. */
class PackLayoutTest {

    private static final int GAP = 4;
    private static final int MOST_TALL = 84;

    /** The whole pack fits in the room it was given, always. */
    @Property(tries = 500)
    void everyCardFitsInTheRoomItWasGiven(
            @ForAll @IntRange(min = 1, max = 15) int cards,
            @ForAll @IntRange(min = 80, max = 1400) int roomWidth,
            @ForAll @IntRange(min = 60, max = 900) int roomHeight) {
        PackLayout laid = PackLayout.fit(cards, roomWidth, roomHeight, GAP, MOST_TALL);

        assertThat(laid.columns() * laid.rows())
                .describedAs("a %s-card pack laid out as %sx%s leaves cards nowhere to go",
                        cards, laid.columns(), laid.rows())
                .isGreaterThanOrEqualTo(cards);
        assertThat(laid.width(GAP))
                .describedAs("%s cards in %s wide came out %s wide",
                        cards, roomWidth, laid.width(GAP))
                .isLessThanOrEqualTo(roomWidth);
        assertThat(laid.height(GAP))
                .describedAs("%s cards in %s tall came out %s tall",
                        cards, roomHeight, laid.height(GAP))
                .isLessThanOrEqualTo(roomHeight);
    }

    /** And the cards keep a card's shape, whatever shape the box is. */
    @Property(tries = 300)
    void theCardsAreStillCardShaped(
            @ForAll @IntRange(min = 1, max = 15) int cards,
            @ForAll @IntRange(min = 120, max = 1400) int roomWidth,
            @ForAll @IntRange(min = 100, max = 900) int roomHeight) {
        PackLayout laid = PackLayout.fit(cards, roomWidth, roomHeight, GAP, MOST_TALL);

        assertThat(laid.cardWidth())
                .isCloseTo(CardShape.widthFor(laid.cardHeight()),
                        org.assertj.core.data.Offset.offset(1));
    }

    /** A small pack is not blown up to fill the window. */
    @Test
    void aSmallPackIsNotBlownUp() {
        PackLayout laid = PackLayout.fit(4, 1200, 800, GAP, MOST_TALL);

        assertThat(laid.cardHeight()).isEqualTo(MOST_TALL);
    }

    /**
     * A fifteen-card pack in a shallow box shrinks rather than losing its last row.
     * <p>The case this exists for. At two rows of five in the room a small window leaves, the
     * third row was drawn under the edge of the panel - which is not a cramped pack, it is
     * five cards nobody can click.
     */
    @Test
    void aFullPackInAShallowBoxKeepsEveryCard() {
        PackLayout laid = PackLayout.fit(15, 360, 190, GAP, MOST_TALL);

        assertThat(laid.columns() * laid.rows()).isGreaterThanOrEqualTo(15);
        assertThat(laid.height(GAP)).isLessThanOrEqualTo(190);
        assertThat(laid.width(GAP)).isLessThanOrEqualTo(360);
        assertThat(laid.cardHeight()).isLessThan(MOST_TALL);
    }

    /** Two rows of five beats five rows of two when the cards would be the same size. */
    @Test
    void aPackIsLaidOutSquareRatherThanAsAList() {
        PackLayout laid = PackLayout.fit(10, 1200, 800, GAP, MOST_TALL);

        assertThat(Math.abs(laid.columns() - laid.rows())).isLessThanOrEqualTo(3);
    }
}
