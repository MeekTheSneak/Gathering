package dev.gathering.core.ante;

import dev.gathering.core.game.SeatId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Who has agreed to play for keeps, and who has not been asked yet.
 * <p>Ante is the one thing in this mod that takes a card off somebody permanently, so it is
 * the one thing that asks first. Everybody at the table, every game, by name - not a server
 * setting somebody turned on months ago and a player who never saw it.
 * <p>Three answers rather than two. Waiting is not the same as no: a table where one seat has
 * not looked at the question yet is a table still deciding, and a table where somebody has
 * said no is a table that is not playing for keeps tonight. Collapsing them would make a slow
 * reader into a refusal.
 * <p>Silence is never agreement. A seat that has not answered blocks the game, which is the
 * whole point: the failure this prevents is somebody's card going into a pot because they
 * were making tea.
 */
public record AnteConsent(Set<SeatId> seats, Map<SeatId, Answer> answers) {

    /** What one seat has said. */
    public enum Answer {

        /** Asked, and has not answered. Not a no, and never treated as a yes. */
        WAITING,

        /** Playing for keeps. */
        IN,

        /** Not tonight. One of these is enough to stop the game. */
        OUT
    }

    public AnteConsent {
        Set<SeatId> atTheTable = seats == null ? Set.of() : new LinkedHashSet<>(seats);
        Map<SeatId, Answer> given = new LinkedHashMap<>();
        if (answers != null) {
            // Only seats at the table count. An answer from a seat that has since been given
            // up is not a vote, and keeping it would let somebody leave a yes behind them.
            for (Map.Entry<SeatId, Answer> entry : answers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && atTheTable.contains(entry.getKey())) {
                    given.put(entry.getKey(), entry.getValue());
                }
            }
        }
        seats = java.util.Collections.unmodifiableSet(atTheTable);
        answers = java.util.Collections.unmodifiableMap(given);
    }

    /** Nobody asked yet, at a table with these seats. */
    public static AnteConsent asking(Set<SeatId> seats) {
        return new AnteConsent(seats, Map.of());
    }

    public Answer answerFrom(SeatId seat) {
        return answers.getOrDefault(seat, Answer.WAITING);
    }

    /** That seat's answer, replacing whatever it said before. */
    public AnteConsent from(SeatId seat, Answer answer) {
        if (seat == null || answer == null || !seats.contains(seat)) {
            return this;
        }
        Map<SeatId, Answer> next = new LinkedHashMap<>(answers);
        next.put(seat, answer);
        return new AnteConsent(seats, next);
    }

    /** Whether anybody has refused, which is enough on its own. */
    public boolean refused() {
        return answers.containsValue(Answer.OUT);
    }

    /** The seats still to answer, for telling the table who it is waiting on. */
    public Set<SeatId> waitingOn() {
        Set<SeatId> waiting = new LinkedHashSet<>();
        for (SeatId seat : seats) {
            if (answerFrom(seat) == Answer.WAITING) {
                waiting.add(seat);
            }
        }
        return java.util.Collections.unmodifiableSet(waiting);
    }

    /**
     * Whether the game may start for keeps.
     * <p>Every seat in, and at least one seat at the table. An empty table agreeing to
     * anything unanimously is how a rule like this gets accidentally satisfied.
     */
    public boolean settled() {
        if (seats.isEmpty() || refused()) {
            return false;
        }
        return waitingOn().isEmpty();
    }
}
