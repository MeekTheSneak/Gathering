package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which sets a counter holds, turnover by turnover.
 * <p>A server drawing from every set ever printed has more of them than one shop could read,
 * so the shelf holds a window and the window moves. What matters is that the window really
 * moves, that this month's set is always on it, and that everything comes round eventually.
 */
class ShopWindowTest {

    private static final int COUNTER = 6;

    @Test
    @DisplayName("a server with few enough sets stocks all of them, every turnover")
    void aSmallServerStocksEverything() {
        List<String> few = List.of("blb", "dsk", "otj");
        for (long turnover = 0; turnover < 8; turnover++) {
            assertThat(ShopCounter.behindTheCounter(few, turnover, COUNTER))
                    .containsExactlyElementsOf(few);
        }
    }

    @Test
    @DisplayName("the newest set is behind the counter whatever turnover it is")
    void theNewestSetIsAlwaysThere() {
        List<String> many = sets(140);
        for (long turnover = 0; turnover < 200; turnover++) {
            assertThat(ShopCounter.behindTheCounter(many, turnover, COUNTER))
                    .as("turnover %d", turnover)
                    .hasSize(COUNTER)
                    .doesNotHaveDuplicates()
                    .startsWith("set-0");
        }
    }

    @Test
    @DisplayName("every set a server draws from reaches the counter eventually")
    void everySetComesRound() {
        List<String> many = sets(140);
        Set<String> seen = new LinkedHashSet<>();
        // Five slots move each turnover, so a hundred and forty sets take twenty-eight of
        // them. A season of a running server, not a lifetime of one.
        for (long turnover = 0; turnover < 28; turnover++) {
            seen.addAll(ShopCounter.behindTheCounter(many, turnover, COUNTER));
        }
        assertThat(seen).containsAll(many);
    }

    @Test
    @DisplayName("a brand new world's first shop is not just this year's product")
    void theFirstShopIsNotAllRecent() {
        // Reported from a real session: villagers sell nothing but the current set. A turnover
        // is hours of world time, so every new world is on turnover zero for its first
        // evening - and a window that walked forward from the newest set put the six newest
        // sets on that counter and left them there.
        List<String> many = sets(140);
        List<String> first = ShopCounter.behindTheCounter(many, 0, COUNTER);
        assertThat(first).hasSize(COUNTER).doesNotHaveDuplicates().contains("set-0");

        int oldest = first.stream().mapToInt(ShopWindowTest::indexOf).max().orElse(0);
        assertThat(oldest)
                .as("the oldest set on a new world's counter, out of %d", many.size())
                .isGreaterThan(many.size() / 2);
    }

    @Test
    @DisplayName("the shelf really turns over rather than standing still")
    void theWindowMoves() {
        List<String> many = sets(140);
        assertThat(ShopCounter.behindTheCounter(many, 0, COUNTER))
                .isNotEqualTo(ShopCounter.behindTheCounter(many, 1, COUNTER));
    }

    @Test
    @DisplayName("nothing to stock and nowhere to put it are both answers, not failures")
    void theEmptyCasesAreAnswers() {
        assertThat(ShopCounter.behindTheCounter(List.of(), 3, COUNTER)).isEmpty();
        assertThat(ShopCounter.behindTheCounter(null, 3, COUNTER)).isEmpty();
        assertThat(ShopCounter.behindTheCounter(sets(10), 3, 0)).isEmpty();
    }

    private static int indexOf(String code) {
        return Integer.parseInt(code.substring("set-".length()));
    }

    private static List<String> sets(int howMany) {
        List<String> codes = new ArrayList<>(howMany);
        for (int one = 0; one < howMany; one++) {
            codes.add("set-" + one);
        }
        return List.copyOf(codes);
    }
}
