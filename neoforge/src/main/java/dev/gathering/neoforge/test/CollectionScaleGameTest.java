package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import dev.gathering.core.collection.CollectionSearch;
import dev.gathering.item.GatheringContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A collection the size of somebody's actual collection.
 * <p>Every other test in this repository, every screenshot and every scripted run works
 * against a box holding a few dozen cards. A player who has been opening packs for a month has
 * thousands, and the difference is the kind that is invisible until it is not: a search that
 * walks every row, a page that carries a hundred and sixty summaries, a card cache that holds
 * two hundred and fifty-six textures. None of that is obviously wrong at seven cards and none
 * of it had ever been run at ten thousand.
 * <p><b>Nothing here was broken.</b> These were written expecting to find something and did
 * not: a ten-thousand-card box searches, saves and gives cards up without trouble. They are
 * kept because that is worth holding - every other test in this repository uses a box of seven
 * cards, so a change that makes any of this quadratic would pass all of them.
 * <p>These are the shape of the check rather than a benchmark. The timings are deliberately
 * loose - a game test runs on whatever machine CI gave it, under software rendering, next to
 * two hundred other tests - so they are set where they catch an algorithm that got quadratic
 * and nothing tighter. A search that takes a second here is broken; one that takes ten
 * milliseconds and one that takes forty are the same answer.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionScaleGameTest {

    /** Distinct cards in the box. Past a serious paper collection, and under a cube-shop's. */
    private static final int DISTINCT = 10_000;

    /** Copies of each, so the totals are a real number rather than a set. */
    private static final int COPIES = 3;

    /**
     * How long one search may take over the whole box.
     * <p>Generous on purpose. What this catches is a pass that became a pass per row, which at
     * ten thousand rows is not slower by a factor - it is a screen that never opens.
     */
    private static final long SEARCH_BUDGET_MILLIS = 1_500L;

    /**
     * How much dearer the big box may be than the small one for the same hundred gestures.
     * <p>Ten. A map lookup does not care how large the map is, so the honest answer is about
     * one; ten leaves room for cache behaviour and a garbage collection landing in the middle
     * of one of the two measurements. A pass per row would be three thousand.
     */
    private static final int A_HONEST_DIFFERENCE = 10;

    /** Added so two measurements that are both essentially nothing do not divide badly. */
    private static final long A_MILLISECOND_OF_NOISE = 1_000_000L;

    /** A hundred puts and takes of one card, in nanoseconds. */
    private static long putAndTake(CollectionBlockEntity box, CardIdentity card) {
        long began = System.nanoTime();
        for (int again = 0; again < 100; again++) {
            box.put(card, 1);
            box.take(card, 1);
        }
        return System.nanoTime() - began;
    }

    /** A box the size every other test in this repository uses. */
    private static CardTally aFewCards() {
        CardTally.Builder few = CardTally.builder();
        for (int index = 0; index < 8; index++) {
            few.add(CardIdentity.ofPrinting(printingNumber(index), false), COPIES);
        }
        return few.build();
    }

    @GameTest(template = "empty")
    public static void aRealSizedCollectionStillSearches(GameTestHelper helper) {
        BlockPos where = collection(helper);
        CollectionBlockEntity box = boxAt(helper, where);
        box.putAll(manyCards());

        if (box.cards().distinct() != DISTINCT) {
            helper.fail("the box holds " + box.cards().distinct() + " distinct cards, not " + DISTINCT);
            return;
        }
        if (box.cards().total() != DISTINCT * COPIES) {
            helper.fail("the box totals " + box.cards().total() + " cards");
            return;
        }

        // Rows with nothing known about them, which is the honest state of a box this size:
        // the card cache holds what somebody has actually looked at, not ten thousand cards.
        List<CollectionSearch.Row> rows = new ArrayList<>(DISTINCT);
        box.cards().counts().forEach((card, count) ->
                rows.add(new CollectionSearch.Row(card, count, null)));

        long began = System.nanoTime();
        List<CollectionSearch.Row> found =
                CollectionSearch.run(rows, CollectionSearch.Query.everything());
        long took = (System.nanoTime() - began) / 1_000_000L;

        if (found.size() != DISTINCT) {
            helper.fail("an empty search over " + DISTINCT + " cards returned " + found.size());
            return;
        }
        if (took > SEARCH_BUDGET_MILLIS) {
            helper.fail("searching " + DISTINCT + " cards took " + took + "ms");
            return;
        }
        helper.succeed();
    }

    /**
     * The same box, saved and loaded.
     * <p>A collection lives on a block entity and every block entity is written into the chunk
     * it sits in. Ten thousand distinct cards is a tag with ten thousand entries in it, written
     * whenever the chunk saves - so what this is really asking is whether somebody's collection
     * is something their world can carry.
     */
    @GameTest(template = "empty")
    public static void aRealSizedCollectionSurvivesBeingSaved(GameTestHelper helper) {
        BlockPos where = collection(helper);
        CollectionBlockEntity box = boxAt(helper, where);
        box.putAll(manyCards());

        var registries = helper.getLevel().registryAccess();
        long began = System.nanoTime();
        net.minecraft.nbt.CompoundTag written = box.saveWithoutMetadata(registries);
        long took = (System.nanoTime() - began) / 1_000_000L;

        CollectionBlockEntity reopened =
                new CollectionBlockEntity(where, GatheringContent.COLLECTION.get().defaultBlockState());
        reopened.loadWithComponents(written, registries);

        if (reopened.cards().distinct() != DISTINCT) {
            helper.fail("a saved collection came back with " + reopened.cards().distinct()
                    + " distinct cards instead of " + DISTINCT);
            return;
        }
        if (reopened.cards().total() != DISTINCT * COPIES) {
            helper.fail("a saved collection came back holding " + reopened.cards().total() + " cards");
            return;
        }
        if (took > SEARCH_BUDGET_MILLIS) {
            helper.fail("writing " + DISTINCT + " distinct cards took " + took + "ms");
            return;
        }
        helper.succeed();
    }

    /**
     * Taking one card out of a very large box.
     * <p>The gesture a player makes most, and the one that would hurt most if it walked the
     * collection: a click that is instant at fifty cards and takes a second at ten thousand is
     * a screen that feels broken exactly when somebody has enough cards to care.
     */
    @GameTest(template = "empty")
    public static void takingOneCardOutOfALargeBoxIsCheap(GameTestHelper helper) {
        BlockPos where = collection(helper);
        CollectionBlockEntity box = boxAt(helper, where);
        box.putAll(manyCards());
        CardIdentity wanted = CardIdentity.ofPrinting(printingNumber(DISTINCT / 2), false);

        // Against a small box rather than against a clock. What this is for is the shape of
        // the cost - a click that is instant at fifty cards and takes a second at ten thousand
        // - and that is a ratio. Written as an absolute it measured the machine instead: this
        // sat just under its budget and started failing whenever anything else in the run
        // allocated, which is a test reporting on its neighbours.
        BlockPos small = collection(helper, 3, 2, 1);
        CollectionBlockEntity tiny = boxAt(helper, small);
        tiny.putAll(aFewCards());
        CardIdentity oneOfTheFew = CardIdentity.ofPrinting(printingNumber(0), false);

        long smallNanos = putAndTake(tiny, oneOfTheFew);
        long largeNanos = putAndTake(box, wanted);

        if (box.cards().of(wanted) != COPIES) {
            helper.fail("a hundred puts and takes left " + box.cards().of(wanted) + " copies");
            return;
        }
        System.out.println("[collection] a hundred puts and takes: " + (smallNanos / 1000)
                + "us at " + tiny.cards().distinct() + " rows, " + (largeNanos / 1000)
                + "us at " + box.cards().distinct() + " rows");
        // A pass that became a pass per row would be three thousand times dearer here. Ten is
        // far past any honest difference in cache behaviour and far short of that.
        if (largeNanos > smallNanos * A_HONEST_DIFFERENCE + A_MILLISECOND_OF_NOISE) {
            helper.fail("a hundred puts and takes cost " + (largeNanos / 1000) + "us on a box of "
                    + box.cards().distinct() + " against " + (smallNanos / 1000)
                    + "us on a box of " + tiny.cards().distinct()
                    + ", so the cost is following the size of the box");
            return;
        }
        helper.succeed();
    }

    private static CardTally manyCards() {
        Map<CardIdentity, Integer> counts = new LinkedHashMap<>(DISTINCT * 2);
        for (int index = 0; index < DISTINCT; index++) {
            counts.put(CardIdentity.ofPrinting(printingNumber(index), false), COPIES);
        }
        return new CardTally(counts);
    }

    /** Distinct, stable printings, so a failure fails the same way twice. */
    private static UUID printingNumber(int index) {
        return new UUID(0x4000L, index);
    }

    private static CollectionBlockEntity boxAt(GameTestHelper helper, BlockPos where) {
        return (CollectionBlockEntity) helper.getLevel().getBlockEntity(where);
    }

    private static BlockPos collection(GameTestHelper helper) {
        return collection(helper, 1, 2, 1);
    }

    /** A second box, for a test that compares one size against another. */
    private static BlockPos collection(GameTestHelper helper, int x, int y, int z) {
        BlockPos where = helper.absolutePos(new BlockPos(x, y, z));
        helper.getLevel().setBlock(
                where, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
        return where;
    }
}
