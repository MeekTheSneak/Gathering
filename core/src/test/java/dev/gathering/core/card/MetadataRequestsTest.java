package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one thing that must not go wrong here is asking twice.
 * <p>This runs from a tick handler, so a mistake is twenty requests a second at somebody
 * else's server rather than a visual glitch.
 */
class MetadataRequestsTest {

    private static final UUID ONE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID TWO = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    @DisplayName("a printing is asked about once, however many times the tick runs")
    void asksOnce() {
        MetadataRequests requests = new MetadataRequests();
        Set<UUID> known = new HashSet<>();

        assertThat(requests.next(List.of(ONE, TWO), known::contains, 0L, 64)).containsExactly(ONE, TWO);
        for (long tick = 1; tick < 100; tick++) {
            assertThat(requests.next(List.of(ONE, TWO), known::contains, tick * 50L, 64)).isEmpty();
        }
    }

    @Test
    @DisplayName("an unanswered printing is asked about again, but not soon")
    void retriesSlowly() {
        // The server may simply not have been able to reach Scryfall that second.
        MetadataRequests requests = new MetadataRequests();
        Set<UUID> known = new HashSet<>();

        requests.next(List.of(ONE), known::contains, 0L, 64);
        assertThat(requests.next(List.of(ONE), known::contains, MetadataRequests.RETRY_AFTER_MILLIS - 1, 64))
                .isEmpty();
        assertThat(requests.next(List.of(ONE), known::contains, MetadataRequests.RETRY_AFTER_MILLIS, 64))
                .containsExactly(ONE);
    }

    @Test
    @DisplayName("a printing that has been answered is never asked about again")
    void neverAsksForWhatItAlreadyKnows() {
        MetadataRequests requests = new MetadataRequests();
        Set<UUID> known = new HashSet<>();

        requests.next(List.of(ONE), known::contains, 0L, 64);
        known.add(ONE);

        assertThat(requests.next(List.of(ONE), known::contains, MetadataRequests.RETRY_AFTER_MILLIS * 10, 64))
                .isEmpty();
    }

    @Test
    @DisplayName("disconnecting forgets everything, because the next server is a different one")
    void clearingForgets() {
        MetadataRequests requests = new MetadataRequests();
        requests.next(List.of(ONE), printing -> false, 0L, 64);

        requests.clear();

        assertThat(requests.next(List.of(ONE), printing -> false, 1L, 64)).containsExactly(ONE);
    }

    @Property(tries = 1000)
    void neverAsksForMoreThanOneRequestHolds(
            @ForAll @IntRange(min = 0, max = 40) int held,
            @ForAll @IntRange(min = 1, max = 16) int maximum) {
        MetadataRequests requests = new MetadataRequests();
        List<UUID> printings = new java.util.ArrayList<>();
        for (int index = 0; index < held; index++) {
            printings.add(new UUID(0L, index));
        }

        List<UUID> batch = requests.next(printings, printing -> false, 0L, maximum);

        assertThat(batch).hasSizeLessThanOrEqualTo(maximum);
        assertThat(batch).doesNotHaveDuplicates();
    }

    @Property(tries = 1000)
    void everythingHeldIsEventuallyAskedAbout(@ForAll @IntRange(min = 1, max = 40) int held) {
        // Batching must not lose the tail: a big inventory takes several ticks, not never.
        MetadataRequests requests = new MetadataRequests();
        List<UUID> printings = new java.util.ArrayList<>();
        for (int index = 0; index < held; index++) {
            printings.add(new UUID(0L, index));
        }

        Set<UUID> seen = new HashSet<>();
        for (int round = 0; round < held + 2; round++) {
            seen.addAll(requests.next(printings, seen::contains, round, 4));
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(printings);
    }
}
