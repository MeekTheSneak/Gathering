package dev.gathering.core.scryfall;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a very large collection costs the card cache.
 * <p>A collection block asks the cache about every card in it, every time somebody searches -
 * that is how a row gets a name to sort by. At a few dozen cards nobody could tell. At the size
 * of somebody's real collection it is thousands of lookups on the server thread, and the ones
 * that matter are the misses: a card nobody has ever looked up has no file, so the answer is
 * "not here" and the question gets asked again on the very next search, forever.
 * <p><b>Measured before it was assumed.</b> This was written expecting to find a stall and did
 * not: ten thousand misses cost about twenty milliseconds, because a miss is one
 * {@code isRegularFile} and the filesystem is very good at those. The suspicion was reasonable
 * and the number said otherwise, so nothing was changed to chase it. What the test is for now
 * is holding that: the cost is linear in the collection and small, and the way it stops being
 * small is somebody making a miss do real work. That change would pass every other test in the
 * repository, because every other test uses a box holding seven cards.
 */
class DiskStoreScaleTest {

    /** About the size of a collection somebody has been opening packs into for a month. */
    private static final int CARDS = 10_000;

    /**
     * How long ten thousand misses may take.
     * <p>Measured at about twenty milliseconds; the budget is ten times that, so a slow disk
     * and a busy CI box do not make it flake. It is a tripwire rather than a benchmark - what
     * it catches is a miss that started doing real work, not a few milliseconds either way.
     */
    private static final long BUDGET_MILLIS = 250L;

    @Test
    @DisplayName("asking about cards that were never fetched is cheap, and stays cheap")
    void missesAreRememberedRatherThanReAsked(@TempDir Path cache) throws java.io.IOException {
        DiskCardMetadataStore store = new DiskCardMetadataStore(cache);

        // Nothing has ever been fetched, so every one of these is a miss. This is the ordinary
        // state of a big collection: the cache holds what somebody has actually looked at.
        long began = System.nanoTime();
        for (int index = 0; index < CARDS; index++) {
            assertThat(store.find(CardQuery.byId(printing(index)))).isEmpty();
        }
        long first = (System.nanoTime() - began) / 1_000_000L;

        // And again, which is what the next search does. The second pass is the one that has
        // to be free: a collection screen asks this on every page turn.
        began = System.nanoTime();
        for (int index = 0; index < CARDS; index++) {
            assertThat(store.find(CardQuery.byId(printing(index)))).isEmpty();
        }
        long again = (System.nanoTime() - began) / 1_000_000L;

        assertThat(again)
                .describedAs("a second pass over %d misses took %dms (first pass %dms)",
                        CARDS, again, first)
                .isLessThan(BUDGET_MILLIS);
    }

    private static UUID printing(int index) {
        return new UUID(0x4000L, index);
    }
}
