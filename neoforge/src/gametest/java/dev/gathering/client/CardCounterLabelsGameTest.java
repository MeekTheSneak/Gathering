package dev.gathering.client;

import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.*;
import dev.gathering.core.game.visibility.CardView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Content invalidation and bounded retention for the table renderer's preparation cache. */
@GameTestHolder("gathering")
@PrefixGameTestTemplate(false)
public final class CardCounterLabelsGameTest {
    private static CardView card(Map<String, Integer> counters, String strength) {
        return new CardView.Visible(CardInstanceId.of(1), PaperStock.BLANK.identity(), SeatId.of(0),
                Facing.FACE_UP, false, counters, null, false, null, null, false, strength, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void steadyFramesReusePreparedText(GameTestHelper helper) {
        var cache = new CardCounterLabels(2);
        var view = card(Map.of("quest", 3), null);
        var first = cache.forCard(view);
        for (int i = 0; i < 1000; i++) {
            require(cache.forCard(view) == first, "A stable frame prepared text again");
        }
        require(first.getFirst().name().getString().equals("Quest"), "Wrong counter name");
        require(first.getFirst().count().getString().equals("x3"), "Wrong amount");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sameIdDoesNotKeepOldCounters(GameTestHelper helper) {
        var cache = new CardCounterLabels(2);
        var old = card(Map.of("quest", 3), null);
        var changed = card(Map.of("quest", 9), null);
        cache.forCard(old);
        require(cache.forCard(changed).getFirst().count().getString().equals("x9"),
                "Reusing a card id retained the previous count");
        require(cache.forCard(card(Map.of(), null)).isEmpty(), "Removed counters stayed visible");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void writtenStrengthChangesLoyaltyPlacement(GameTestHelper helper) {
        var cache = new CardCounterLabels(2);
        var counters = Map.of("loyalty", 4);
        require(cache.forCard(card(counters, null)).isEmpty(), "Corner loyalty repeated below");
        var written = cache.forCard(card(counters, "7/9"));
        require(written.size() == 1 && written.getFirst().name().getString().equals("Loyalty"),
                "Writing strength failed to restore the loyalty label");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insertionOrderAndUnitCountsSurvive(GameTestHelper helper) {
        var counters = new LinkedHashMap<String, Integer>();
        counters.put("quest", 1);
        counters.put("charge", 7);
        var cache = new CardCounterLabels(2);
        var labels = cache.forCard(card(counters, null));
        require(labels.stream().map(line -> line.name().getString()).toList()
                .equals(List.of("Quest", "Charge")), "Label order changed");
        require(labels.getFirst().count() == null, "Unit count should not draw x1");
        counters.put("quest", 8);
        require(labels.getFirst().count() == null, "A caller's mutable map leaked into the view");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anonymousCardsNeedNoIdentity(GameTestHelper helper) {
        var cache = new CardCounterLabels(2);
        var anonymous = new CardView.Anonymous(new MarkerId("opaque"), false,
                Map.of("quest", 2), null, null, null, null, false);
        require(cache.forCard(anonymous).getFirst().count().getString().equals("x2"),
                "Anonymous public counters were not prepared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldViewsAreEvictedAndRecomputed(GameTestHelper helper) {
        var cache = new CardCounterLabels(2);
        var first = card(Map.of("quest", 2), null);
        var old = cache.forCard(first);
        cache.forCard(card(Map.of("quest", 3), null));
        cache.forCard(card(Map.of("quest", 4), null));
        var rebuilt = cache.forCard(first);
        require(rebuilt != old, "The cache retained a view beyond capacity");
        require(rebuilt.getFirst().count().getString().equals("x2"), "Eviction changed content");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyCardsDoNotEvictUsefulLabels(GameTestHelper helper) {
        var cache = new CardCounterLabels(1);
        var view = card(Map.of("quest", 2), null);
        var kept = cache.forCard(view);
        for (int i = 0; i < 100; i++) cache.forCard(card(Map.of(), null));
        require(cache.forCard(view) == kept, "Unmarked cards displaced prepared counters");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hugeCounterListsRenderWithoutRetainingHistory(GameTestHelper helper) {
        var cache = new CardCounterLabels(1);
        var small = card(Map.of("quest", 2), null);
        var retained = cache.forCard(small);
        Map<String, Integer> counters = new LinkedHashMap<>();
        for (int i = 0; i < 65; i++) counters.put("counter " + i, 2);
        var large = card(counters, null);
        var first = cache.forCard(large);
        require(first.size() == 65, "The cache budget removed visible counters");
        require(first != cache.forCard(large), "An oversized card was retained");
        require(cache.forCard(small) == retained, "An oversized card evicted ordinary labels");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void longNamesDoNotConsumeAHistoryOfLargeStrings(GameTestHelper helper) {
        var cache = new CardCounterLabels(1);
        String name = "a".repeat(4097);
        var large = card(Map.of(name, 1), null);
        var first = cache.forCard(large);
        require(first.size() == 1 && first.getFirst().name().getString().length() == name.length(),
                "The cache budget truncated the counter text");
        require(first != cache.forCard(large), "An oversized name was retained");
        helper.succeed();
    }
}
