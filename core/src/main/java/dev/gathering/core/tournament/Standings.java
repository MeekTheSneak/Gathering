package dev.gathering.core.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is ahead after the Swiss rounds played so far, and by how much.
 * <p>Counted the way paper Magic counts: three match points for a win and one for a draw, and
 * ties broken by opponents' match-win percentage, then game-win percentage, then opponents'
 * game-win percentage - each percentage floored at a third, so one opponent who lost every
 * match does not sink a player who happened to be paired with them. A bye counts as a match
 * won two games to none for the player who had it, and is left out of everybody's opponents.
 * <p>Top cut rounds are not counted here: a cut decides a winner, not a standing.
 */
public final class Standings {

    /** The floor on every percentage in a tiebreaker. */
    public static final double FLOOR = 1.0 / 3.0;

    private Standings() {
    }

    /**
     * One player's line on the standings.
     *
     * @param rank                 from one
     * @param matchPoints          three a win, one a draw
     * @param opponentsMatchWin    average of opponents' match-win percentages, floored
     * @param gameWin              this player's game-win percentage, floored
     * @param opponentsGameWin     average of opponents' game-win percentages, floored
     */
    public record Row(
            int rank, Entrant player, int matchPoints, int wins, int losses, int draws,
            double opponentsMatchWin, double gameWin, double opponentsGameWin) {

        public int matches() {
            return wins + losses + draws;
        }
    }

    /** Standings over the confirmed matches of these Swiss rounds, best first. */
    public static List<Row> of(List<Entrant> players, List<Round> rounds) {
        Map<UUID, Tally> tallies = new LinkedHashMap<>();
        for (Entrant player : players) {
            tallies.put(player.id(), new Tally());
        }
        for (Round round : rounds) {
            if (round.elimination()) {
                continue;
            }
            for (Pairing pairing : round.pairings()) {
                if (!pairing.isConfirmed()) {
                    continue;
                }
                Tally a = tallies.get(pairing.a());
                if (a == null) {
                    continue;
                }
                MatchResult result = pairing.result();
                a.record(result);
                if (!pairing.isBye()) {
                    Tally b = tallies.get(pairing.b());
                    if (b != null) {
                        b.record(result.flipped());
                        a.opponents.add(pairing.b());
                        b.opponents.add(pairing.a());
                    }
                }
            }
        }
        Map<UUID, Double> matchWin = new HashMap<>();
        Map<UUID, Double> gameWin = new HashMap<>();
        tallies.forEach((id, tally) -> {
            matchWin.put(id, tally.matchWin());
            gameWin.put(id, tally.gameWin());
        });

        List<Row> rows = new ArrayList<>();
        for (Entrant player : players) {
            Tally tally = tallies.get(player.id());
            rows.add(new Row(0, player, tally.points(), tally.wins, tally.losses, tally.draws,
                    average(tally.opponents, matchWin), gameWin.get(player.id()), average(tally.opponents, gameWin)));
        }
        rows.sort(Comparator.comparingInt(Row::matchPoints).reversed()
                .thenComparing(Comparator.comparingDouble(Row::opponentsMatchWin).reversed())
                .thenComparing(Comparator.comparingDouble(Row::gameWin).reversed())
                .thenComparing(Comparator.comparingDouble(Row::opponentsGameWin).reversed())
                .thenComparing(Comparator.comparingDouble((Row row) -> row.player().seed()).reversed())
                .thenComparing(row -> row.player().id()));
        List<Row> ranked = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            ranked.add(new Row(index + 1, row.player(), row.matchPoints(), row.wins(), row.losses(), row.draws(),
                    row.opponentsMatchWin(), row.gameWin(), row.opponentsGameWin()));
        }
        return List.copyOf(ranked);
    }

    private static double average(List<UUID> opponents, Map<UUID, Double> percentages) {
        if (opponents.isEmpty()) {
            return FLOOR;
        }
        double sum = 0;
        for (UUID opponent : opponents) {
            sum += percentages.getOrDefault(opponent, FLOOR);
        }
        return sum / opponents.size();
    }

    private static final class Tally {
        int wins;
        int losses;
        int draws;
        int gamesWon;
        int gamesDrawn;
        int games;
        final List<UUID> opponents = new ArrayList<>();

        void record(MatchResult result) {
            if (result.firstWon()) {
                wins++;
            } else if (result.secondWon()) {
                losses++;
            } else {
                draws++;
            }
            gamesWon += result.winsA();
            gamesDrawn += result.draws();
            games += result.games();
        }

        int points() {
            return wins * 3 + draws;
        }

        double matchWin() {
            int matches = wins + losses + draws;
            return matches == 0 ? FLOOR : Math.max(FLOOR, points() / (3.0 * matches));
        }

        double gameWin() {
            return games == 0 ? FLOOR : Math.max(FLOOR, (gamesWon * 3.0 + gamesDrawn) / (3.0 * games));
        }
    }
}
