package dev.gathering.core.tournament;

/** How many Swiss rounds an event of a size plays, when the host has not said. */
public final class SwissRounds {

    private SwissRounds() {
    }

    /**
     * The Magic Tournament Rules' table (Appendix E): two rounds for up to four, three for five to eight, five
     * for nine to sixteen, then one more each time the field doubles - six to 64, seven to 128 - and past that
     * eight rounds to 226 players, nine to 409, and ten beyond.
     * <p>Nine to sixteen is five rounds, going to a top 4, unless the playoff is a booster draft, which this mod
     * does not run; it was four once, one short. Five to eight players is where this parts from the table on
     * purpose: Appendix E runs those as single elimination, and here they play three Swiss rounds with no cut,
     * so nobody's event is over after one game.
     */
    public static int forPlayers(int players) {
        if (players <= 1) {
            return 0;
        }
        if (players <= 4) {
            return 2;
        }
        if (players <= 16 && players >= 9) {
            return 5;
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
