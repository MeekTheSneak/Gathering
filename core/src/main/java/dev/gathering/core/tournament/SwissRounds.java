package dev.gathering.core.tournament;

/** How many Swiss rounds an event of a size plays, when the host has not said. */
public final class SwissRounds {

    private SwissRounds() {
    }

    /**
     * Enough rounds that one undefeated player is likely to be left: two for up to four, then
     * one more each time the field doubles - three for five to eight, four for nine to sixteen -
     * up to seven for 128. Past that the Magic Tournament Rules' table (Appendix E) stops
     * doubling: eight rounds to 226 players, nine to 409, and ten beyond.
     */
    public static int forPlayers(int players) {
        if (players <= 1) {
            return 0;
        }
        if (players <= 4) {
            return 2;
        }
        if (players > 128) {
            return players <= 226 ? 8 : players <= 409 ? 9 : 10;
        }
        int rounds = 0;
        int covered = 1;
        while (covered < players) {
            covered *= 2;
            rounds++;
        }
        return rounds;
    }
}
