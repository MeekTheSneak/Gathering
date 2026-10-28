package dev.gathering.client;

import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.ui.CounterText;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * The text of a card's counter bands, prepared once per card view rather than once per frame.
 * <p>A board that has not changed draws the same counters every frame, and building their
 * labels allocated about 2.5 KB per card each time. Views are immutable and a new board arrives
 * as new objects, so a label prepared for one view object is right for as long as that object
 * is drawn.
 * <p>Keyed by the view object, never the card's id. The same card in the next board can carry
 * different counters, or a written strength that moves its loyalty from the stack to the corner;
 * and a face-down card has no id at all.
 * <p>Only text is kept - no widths, no wrapping. Those are measured on every draw, so a zoom or
 * a resource pack changing the font never meets a remembered measurement.
 * <p>Bounded twice: by how many views are kept, oldest out first, which is what lets old replay
 * frames go; and by how big one card's labels may be to be kept at all, so a card carrying
 * thousands of custom counters is still drawn in full but is not held on to. One per screen,
 * client thread only.
 */
final class CardCounterLabels {

    /** One band: the counter's name, and its count when it has one to show. */
    record Label(Component name, Component count) {
    }

    /**
     * Past these a card's labels are prepared and drawn but not kept. A limit on what is
     * remembered, not on what a card may carry or what is drawn.
     */
    private static final int MAX_CACHED_COUNTERS = 64;
    private static final int MAX_CACHED_NAME_CHARS = 4096;

    private final Map<CardView, List<Label>> prepared = new IdentityHashMap<>();
    private final CardView[] ring;
    private int next;

    CardCounterLabels(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        ring = new CardView[capacity];
    }

    /** The labels for this card view, prepared now only if they were not already. */
    List<Label> forCard(CardView card) {
        if (card.counters().isEmpty()) {
            return List.of();
        }
        List<Label> found = prepared.get(card);
        if (found != null) {
            return found;
        }
        List<CounterText.Line> text = CounterText.linesOn(card);
        List<Label> rows = new ArrayList<>(text.size());
        for (CounterText.Line line : text) {
            rows.add(new Label(Component.literal(line.name()),
                    line.count() == null ? null : Component.literal(line.count())));
        }
        List<Label> made = List.copyOf(rows);
        if (!worthKeeping(card)) {
            return made;
        }
        prepared.remove(ring[next]);
        ring[next] = card;
        next = (next + 1) % ring.length;
        prepared.put(card, made);
        return made;
    }

    private static boolean worthKeeping(CardView card) {
        if (card.counters().size() > MAX_CACHED_COUNTERS) {
            return false;
        }
        int room = MAX_CACHED_NAME_CHARS;
        for (String name : card.counters().keySet()) {
            if (name.length() > room) {
                return false;
            }
            room -= name.length();
        }
        return true;
    }
}
