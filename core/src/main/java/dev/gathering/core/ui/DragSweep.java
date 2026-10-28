package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A drag that clicks everything it passes over that will take the click: the rule behind sweeping a
 * deck over cards with the right button held.
 * <p>Slots by number, and whether each would take the click decided by whoever asks. Pure, so the
 * rules are checked without a screen: a drag is not a sweep until it reaches something that takes a
 * click, and from then on every such slot is clicked once as the cursor arrives on it - the slot the
 * button went down on included, since the press alone clicks nothing - and the rest of the drag,
 * release and all, is the sweep's.
 */
public final class DragSweep {

    /** No slot: the cursor is between slots, or off the grid. */
    public static final int NONE = -1;

    private final int armedOn;
    private int last;
    private boolean sweeping;

    private DragSweep(int armedOn) {
        this.armedOn = armedOn;
        this.last = armedOn;
    }

    /** A sweep armed by the button going down over this slot, or over none. */
    public static DragSweep armedOn(int slot) {
        return new DragSweep(slot);
    }

    /** What to do as the cursor moves: which slots to click, in order, and whether the drag is the sweep's. */
    public record Step(List<Integer> clicks, boolean standDown, boolean ours) {
    }

    /** Whether a slot would take a click now. */
    public interface Takes {
        boolean takes(int slot);
    }

    /**
     * The cursor is over {@code slot}.
     *
     * @return the clicks to make, whether vanilla's own drag must be stood down now, and whether the
     *         drag is the sweep's and nothing else should see it
     */
    public Step movedTo(int slot, Takes takes) {
        if (slot == NONE || slot == last) {
            return new Step(List.of(), false, sweeping);
        }
        last = slot;
        List<Integer> clicks = new ArrayList<>(2);
        boolean standDown = false;
        if (!sweeping) {
            if (!takes.takes(slot)) {
                return new Step(List.of(), false, false);
            }
            sweeping = true;
            standDown = true;
            if (armedOn != NONE && armedOn != slot && takes.takes(armedOn)) {
                clicks.add(armedOn);
            }
        }
        if (takes.takes(slot)) {
            clicks.add(slot);
        }
        return new Step(List.copyOf(clicks), standDown, true);
    }

    public boolean sweeping() {
        return sweeping;
    }
}
