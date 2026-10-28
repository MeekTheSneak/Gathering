package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a table seen from across the room shares out the cards it will draw.
 * <p>The fault this exists for: the ceiling was spent in seat order, so a four-seat table where the first seat made
 * nine lots of thirty-two tokens drew those and nothing else - three mats with cards on them, drawn empty.
 */
class SeatSharesTest {

    /** The table at the ceiling's size: two hundred and fifty-six cards. */
    private static final int CEILING = 256;

    @Test
    @DisplayName("one seat's heap of tokens leaves every other seat its cards")
    void aHeapOfTokensStarvesNobody() {
        int[] given = SeatShares.of(new int[] {288, 1, 3, 7}, CEILING);

        assertThat(given[1]).isEqualTo(1);
        assertThat(given[2]).isEqualTo(3);
        assertThat(given[3]).isEqualTo(7);
        // And the heap has what the rest leave, the whole ceiling between them.
        assertThat(given[0]).isEqualTo(CEILING - 11);
    }

    @Test
    @DisplayName("a table under the ceiling draws everything")
    void roomForEverything() {
        assertThat(SeatShares.of(new int[] {40, 12, 0, 60}, CEILING)).containsExactly(40, 12, 0, 60);
    }

    @Test
    @DisplayName("seats that all want more than an even share split it evenly")
    void anEvenSplit() {
        int[] given = SeatShares.of(new int[] {300, 300, 300}, CEILING);

        assertThat(Arrays.stream(given).sum()).isEqualTo(CEILING);
        assertThat(Arrays.stream(given).max().getAsInt() - Arrays.stream(given).min().getAsInt()).isLessThanOrEqualTo(1);
    }

    @Property(tries = 400)
    @Label("no seat is given more than it asks, the ceiling is spent while anybody wants more, and nobody short is starved")
    void sharesAreFair(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 0, max = 400) Integer> asked,
            @ForAll @IntRange(min = 0, max = 600) int budget) {
        int[] wanted = asked.stream().mapToInt(Integer::intValue).toArray();
        int[] given = SeatShares.of(wanted, budget);

        int total = 0;
        for (int seat = 0; seat < wanted.length; seat++) {
            assertThat(given[seat]).as("seat %s of %s", seat, Arrays.toString(wanted))
                    .isBetween(0, wanted[seat]);
            total += given[seat];
        }
        assertThat(total).as("%s with %s", Arrays.toString(wanted), budget)
                .isEqualTo(Math.min(budget, Arrays.stream(wanted).sum()));
        for (int short_ = 0; short_ < wanted.length; short_++) {
            if (given[short_] >= wanted[short_]) {
                continue;
            }
            for (int other = 0; other < wanted.length; other++) {
                assertThat(given[other])
                        .as("%s with %s gave %s: seat %s was short and seat %s had more", Arrays.toString(wanted),
                                budget, Arrays.toString(given), short_, other)
                        .isLessThanOrEqualTo(given[short_] + 1);
            }
        }
    }
}
