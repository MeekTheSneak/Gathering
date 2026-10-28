package dev.gathering.core.ui;

import java.util.Arrays;
import java.util.Comparator;

/**
 * How a limit on the cards drawn at a table is shared out between its seats.
 * <p>A table in the world is drawn for everybody in sight of it, every frame, so one seen from across the room has a
 * ceiling on how many cards it draws. That ceiling was spent in seat order: one seat's heap of tokens used all of it,
 * and every seat after it showed an empty mat with cards on it. Shared, a seat asking for no more than an even share
 * gets all of it, and what such seats leave goes to the seats that ask for more.
 * <p>Pure.
 */
public final class SeatShares {

    private SeatShares() {
    }

    /**
     * How many cards each seat may draw, out of this many in all, by seat.
     * <p>No seat is given more than it asks for, the whole is never more than the budget nor less than it while
     * somebody still wants more, and a seat that is given less than it asked for is given at least as many as any
     * other seat, give or take the one card an uneven split leaves over.
     *
     * @param wanted how many cards each seat has to draw, by seat
     * @param budget how many may be drawn at the table altogether
     */
    public static int[] of(int[] wanted, int budget) {
        int seats = wanted.length;
        int[] given = new int[seats];
        // The smallest asks first, so whatever they leave is shared among the seats still asking.
        Integer[] order = new Integer[seats];
        for (int seat = 0; seat < seats; seat++) {
            order[seat] = seat;
        }
        Arrays.sort(order, Comparator.comparingInt(seat -> Math.max(0, wanted[seat])));
        int left = Math.max(0, budget);
        for (int rank = 0; rank < seats; rank++) {
            int seat = order[rank];
            int share = left / (seats - rank);
            given[seat] = Math.min(Math.max(0, wanted[seat]), share);
            left -= given[seat];
        }
        return given;
    }
}
