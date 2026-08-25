package dev.gathering.core.draft;

/**
 * One drafter's place in a pod.
 *
 * <p>A place at the table rather than a person, for the same reason a seat is: the pod is a
 * ring, and everything the engine does - who passes to whom, which way the packs go this
 * round - is arithmetic on positions. Who is sitting in each is the caller's business.
 *
 * <p>Not a {@code SeatId}. A pod is not a table: it forms at a block, it can be larger than
 * any table seats, and when it ends the drafters scatter to whatever tables they like. Using
 * the table's seat type would have tied the two together for no reason beyond both being
 * small integers.
 */
public record DrafterId(int index) implements Comparable<DrafterId> {

    public DrafterId {
        if (index < 0) {
            throw new IllegalArgumentException("A drafter's place must not be negative: " + index);
        }
    }

    public static DrafterId of(int index) {
        return new DrafterId(index);
    }

    @Override
    public int compareTo(DrafterId other) {
        return Integer.compare(index, other.index);
    }

    @Override
    public String toString() {
        return "drafter" + index;
    }
}
