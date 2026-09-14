package dev.gathering.core.tournament;

import java.util.UUID;

/**
 * Two players at a numbered table for one round, and where their result stands.
 * <p>A result is confirmed when both players have said the same thing, when the host settles
 * a disagreement, when the clock ends the match at time, or when somebody drops. Until then it
 * is either nothing, one player's report, or two reports that do not agree.
 *
 * @param table        the table number, from one; zero for a bye
 * @param a            the first player
 * @param b            the second player, or null for a bye
 * @param reportA      what the first player reported, or null
 * @param reportB      what the second player reported, seen from the first player's chair, or null
 * @param result       the confirmed result, from the first player's chair, or null
 * @param turnsAfterTime turns passed at this table since time was called; -1 before time
 */
public record Pairing(
        int table, UUID a, UUID b, MatchResult reportA, MatchResult reportB, MatchResult result,
        int turnsAfterTime) {

    public Pairing {
        if (a == null) {
            throw new IllegalArgumentException("A pairing needs a player");
        }
        if (b != null && a.equals(b)) {
            throw new IllegalArgumentException("A player cannot be paired with themselves");
        }
    }

    public static Pairing of(int table, UUID a, UUID b) {
        return b == null
                ? new Pairing(0, a, null, null, null, MatchResult.BYE, -1)
                : new Pairing(table, a, b, null, null, null, -1);
    }

    public boolean isBye() {
        return b == null;
    }

    public boolean isConfirmed() {
        return result != null;
    }

    /** Both players have reported, and they disagree. */
    public boolean isDisputed() {
        return result == null && reportA != null && reportB != null && !reportA.equals(reportB);
    }

    public boolean has(UUID player) {
        return a.equals(player) || (b != null && b.equals(player));
    }

    public UUID opponentOf(UUID player) {
        return a.equals(player) ? b : a;
    }

    /** The result as this player sees it: their games first. */
    public MatchResult resultFor(UUID player) {
        return result == null ? null : a.equals(player) ? result : result.flipped();
    }

    Pairing withReport(UUID player, MatchResult asThatPlayerSeesIt) {
        MatchResult fromA = a.equals(player) ? asThatPlayerSeesIt : asThatPlayerSeesIt.flipped();
        MatchResult newA = a.equals(player) ? fromA : reportA;
        MatchResult newB = a.equals(player) ? reportB : fromA;
        MatchResult agreed = newA != null && newA.equals(newB) ? newA : null;
        return new Pairing(table, a, b, newA, newB, agreed, turnsAfterTime);
    }

    Pairing settled(MatchResult fromA) {
        return new Pairing(table, a, b, reportA, reportB, fromA, turnsAfterTime);
    }

    Pairing withTurnsAfterTime(int turns) {
        return new Pairing(table, a, b, reportA, reportB, result, turns);
    }
}
