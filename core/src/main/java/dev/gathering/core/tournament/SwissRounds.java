package dev.gathering.core.tournament;

/** How many Swiss rounds an event of a size plays, when the host has not said. */
public final class SwissRounds {

    private SwissRounds() {
    }

    /**
     * Enough rounds that one undefeated player is likely to be left: two for up to four, then
     * one more each time the field doubles - three for five to eight, four for nine to sixteen.
     */
    public static int forPlayers(int players) {
        if (players <= 1) {
            return 0;
        }
        if (players <= 4) {
            return 2;
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
