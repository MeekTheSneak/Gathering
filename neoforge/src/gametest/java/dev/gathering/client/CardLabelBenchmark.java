package dev.gathering.client;

import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.*;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.ui.CounterText;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Allocation experiment invoked only by ScreenRefactorProbe. Excludes layout and painting. */
public final class CardLabelBenchmark {
    private static volatile Object namesSink;
    private static volatile Object countsSink;

    private CardLabelBenchmark() { }

    public static void run() {
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            System.out.println("[screen-refactor] allocation measurement unavailable");
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        CardView[] cards = new CardView[64];
        Map<String, Integer> counters = new LinkedHashMap<>();
        counters.put("+1/+1", 12);
        counters.put("quest", 1);
        counters.put("charge", 37);
        for (int i = 0; i < cards.length; i++) {
            cards[i] = new CardView.Visible(CardInstanceId.of(i), PaperStock.BLANK.identity(),
                    SeatId.of(0), Facing.FACE_UP, false, counters, null, false, null,
                    null, false, null, false);
        }
        var warm = new CardCounterLabels(512);
        var churn = new CardCounterLabels(1);
        Runnable legacy = () -> {
            for (CardView card : cards) {
                var names = new ArrayList<Component>();
                var counts = new ArrayList<Component>();
                for (CounterText.Line line : CounterText.linesOn(card)) {
                    names.add(Component.literal(line.name()));
                    counts.add(line.count() == null ? null : Component.literal(line.count()));
                }
                namesSink = names;
                countsSink = counts;
            }
        };
        Runnable steady = () -> { for (CardView card : cards) namesSink = warm.forCard(card); };
        Runnable misses = () -> { for (CardView card : cards) namesSink = churn.forCard(card); };
        for (int i = 0; i < 300; i++) { legacy.run(); steady.run(); misses.run(); }
        for (int sample = 0; sample < 3; sample++) {
            System.out.println("[screen-refactor] label allocation bytes/card sample=" + sample
                    + " legacy=" + allocated(bean, legacy)
                    + " cached=" + allocated(bean, steady)
                    + " all_misses=" + allocated(bean, misses));
        }
        namesSink = null;
        countsSink = null;
    }

    private static long allocated(com.sun.management.ThreadMXBean bean, Runnable work) {
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1000; i++) work.run();
        return (bean.getThreadAllocatedBytes(thread) - before) / 64000;
    }
}
