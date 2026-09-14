package dev.gathering.client;

import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.ui.CounterText;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Prepared text for immutable card views, owned by one screen. No font or layout is cached.
 * Identity keys avoid hashing every counter on each frame. A new view, even with the same
 * instance id, prepares fresh labels; a bounded ring releases old replay frames. */
final class CardCounterLabels {
    record Label(Component name, Component count) { }

    // These limit retention, not what a card may carry or what the renderer will draw.
    // A supported view can carry thousands of counters; keeping 512 such old views would
    // be a large history even though the number of cache entries itself was bounded.
    private static final int MAX_CACHED_COUNTERS = 64;
    private static final int MAX_CACHED_NAME_CHARS = 4096;

    private final Map<CardView, List<Label>> prepared = new IdentityHashMap<>();
    private final CardView[] ring;
    private int next;

    CardCounterLabels(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        ring = new CardView[capacity];
    }

    List<Label> forCard(CardView card) {
        if (card.counters().isEmpty()) return List.of();
        List<Label> found = prepared.get(card);
        if (found != null) return found;
        List<CounterText.Line> text = CounterText.linesOn(card);
        List<Label> rows = new ArrayList<>(text.size());
        for (CounterText.Line line : text) {
            rows.add(new Label(Component.literal(line.name()),
                    line.count() == null ? null : Component.literal(line.count())));
        }
        List<Label> made = List.copyOf(rows);
        if (!worthKeeping(card)) return made;
        prepared.remove(ring[next]);
        ring[next] = card;
        next = (next + 1) % ring.length;
        prepared.put(card, made);
        return made;
    }

    private static boolean worthKeeping(CardView card) {
        if (card.counters().size() > MAX_CACHED_COUNTERS) return false;
        int room = MAX_CACHED_NAME_CHARS;
        for (String name : card.counters().keySet()) {
            if (name.length() > room) return false;
            room -= name.length();
        }
        return true;
    }
}
