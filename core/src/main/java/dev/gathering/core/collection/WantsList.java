package dev.gathering.core.collection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The cards somebody is chasing.
 *
 * <p>A collection screen says what is in a box and {@link SetCompletion} says how much of a set
 * is, but neither answers the question a collector actually carries around: which of the
 * thousands of cards that exist do <em>I</em> want. Without somewhere to write that down, a
 * list of three hundred and seventy-two missing cards is something to read once and forget,
 * and a pack opened a week later is a pile of names nobody recognizes.
 *
 * <p><b>Printings, not names.</b> This grew out of set completion, where the thing being chased
 * is a particular printing - the Duskmourn one, not any Lightning Bolt. Finish is deliberately
 * not part of it: a foil and a plain copy of one printing fill the same slot in a set, so
 * wanting one is wanting either, which is the rule {@link SetCompletion} already counts by.
 *
 * <p>Ordered by when each was added, because it is a list somebody works down and a set that
 * reshuffled itself every restart would be a different list every time it was opened.
 *
 * <p>Immutable, and bounded: this comes off a socket one entry at a time and is written to
 * disk, so there is a number past which it stops being a list of cards to find and starts
 * being a way to fill somebody's disk.
 *
 * <p>Pure.
 */
public record WantsList(List<UUID> printings) {

    /**
     * How many cards one player may be chasing.
     *
     * <p>Comfortably more than the largest set ever printed, so somebody can want a whole set
     * and then some, and far short of a number that costs anything to hold or to send.
     */
    public static final int MOST = 2048;

    public static final WantsList EMPTY = new WantsList(List.of());

    public WantsList {
        Set<UUID> kept = new LinkedHashSet<>();
        if (printings != null) {
            for (UUID printing : printings) {
                if (printing != null && kept.size() < MOST) {
                    kept.add(printing);
                }
            }
        }
        printings = List.copyOf(kept);
    }

    /** Whether this card is one being chased. */
    public boolean wants(UUID printing) {
        return printing != null && printings.contains(printing);
    }

    public int size() {
        return printings.size();
    }

    public boolean isEmpty() {
        return printings.isEmpty();
    }

    /** Whether there is room for another. A full list is a full list, said rather than silent. */
    public boolean isFull() {
        return printings.size() >= MOST;
    }

    /**
     * The same list with this card on it, or the same list.
     *
     * <p>Adding one already on it changes nothing rather than moving it to the end: the order
     * is when somebody first wanted a card, and a second press should not reorder the list
     * under them.
     */
    public WantsList with(UUID printing) {
        if (printing == null || wants(printing) || isFull()) {
            return this;
        }
        List<UUID> more = new java.util.ArrayList<>(printings);
        more.add(printing);
        return new WantsList(more);
    }

    /** The same list without this card, or the same list. */
    public WantsList without(UUID printing) {
        if (printing == null || !wants(printing)) {
            return this;
        }
        List<UUID> fewer = new java.util.ArrayList<>(printings);
        fewer.remove(printing);
        return new WantsList(fewer);
    }

    /** On if it was off and off if it was on, which is what one button does. */
    public WantsList toggled(UUID printing, boolean wanted) {
        return wanted ? with(printing) : without(printing);
    }

    /** Everything on the list that is also in this pile, in the order they were wanted. */
    public List<UUID> foundAmong(Collection<UUID> pile) {
        if (pile == null || pile.isEmpty() || printings.isEmpty()) {
            return List.of();
        }
        Set<UUID> here = new java.util.HashSet<>(pile);
        List<UUID> found = new java.util.ArrayList<>();
        for (UUID printing : printings) {
            if (here.contains(printing)) {
                found.add(printing);
            }
        }
        return List.copyOf(found);
    }

    /** The list as it reads on disk: one printing to a line. */
    public static WantsList read(List<String> lines) {
        if (lines == null) {
            return EMPTY;
        }
        List<UUID> printings = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String said = line.trim();
            if (said.isEmpty() || said.startsWith("#")) {
                continue;
            }
            try {
                printings.add(UUID.fromString(said));
            } catch (IllegalArgumentException notAnId) {
                // Somebody's file, edited by hand or written by an older version. A line that
                // is not a card is a line to skip, never a reason to lose the rest of it.
                continue;
            }
        }
        return new WantsList(printings);
    }

    /** And back again. */
    public List<String> lines() {
        List<String> said = new java.util.ArrayList<>(printings.size());
        for (UUID printing : printings) {
            said.add(printing.toString());
        }
        return List.copyOf(said);
    }
}
