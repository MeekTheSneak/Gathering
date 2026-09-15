package dev.gathering.core.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/** A tournament played through, from sign-up to final places, without a world. */
class TournamentTest {

    private static final UUID HOST = new UUID(0L, 0L);

    private static UUID player(int index) {
        return new UUID(1L, index);
    }

    /** A tournament with this many players registered, seeded best first, ready to play. */
    private static Tournament readyWith(int players, EventSettings settings) {
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Friday", HOST, settings);
        for (int index = 0; index < players; index++) {
            tournament = tournament.register(Entrant.registering(player(index), "P" + index, 2000 - index * 10));
        }
        tournament = tournament.beginPreparing();
        for (int index = 0; index < players; index++) {
            tournament = tournament.markReady(player(index));
        }
        return tournament;
    }

    private static EventSettings constructed() {
        return EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern");
    }

    /** Both players report the same result for every unconfirmed match: the first player wins 2-1. */
    private static Tournament playRound(Tournament tournament, Random random) {
        Round round = tournament.currentRound().orElseThrow();
        for (Pairing pairing : round.pairings()) {
            if (pairing.isConfirmed()) {
                continue;
            }
            int roll = random.nextInt(round.elimination() ? 2 : 3);
            MatchResult result = switch (roll) {
                case 0 -> new MatchResult(2, 1, 0);
                case 1 -> new MatchResult(0, 2, 0);
                default -> new MatchResult(1, 1, 1);
            };
            tournament = tournament.report(pairing.a(), result);
            tournament = tournament.report(pairing.b(), result.flipped());
        }
        return tournament;
    }

    @Test
    void roundOneFoldsTheSeeds() {
        Tournament tournament = readyWith(8, constructed()).startSwiss();
        List<Pairing> pairings = tournament.currentRound().orElseThrow().pairings();
        assertThat(pairings).hasSize(4);
        for (int index = 0; index < 4; index++) {
            assertThat(pairings.get(index).a()).isEqualTo(player(index));
            assertThat(pairings.get(index).b()).isEqualTo(player(index + 4));
        }
    }

    /**
     * Throwing matches to lower a seed backfires: the lowest seed is paired against a top-half
     * seed in round one, never with another weak player.
     */
    @Test
    void theLowestSeedMeetsATopHalfSeedFirst() {
        Tournament tournament = readyWith(8, constructed()).startSwiss();
        Pairing lowest = tournament.currentRound().orElseThrow().pairingOf(player(7)).orElseThrow();
        assertThat(lowest.opponentOf(player(7))).isEqualTo(player(3));
    }

    /**
     * Every result a player is offered is one the event takes, and the ones a real match ends in
     * are all there: a win at time a game up, a drawn match, and a draw agreed before playing.
     */
    @Test
    void everyOfferedResultIsAcceptedAndTheCommonOnesAreOffered() {
        for (int bestOf : new int[] {1, 3, 5}) {
            EventSettings base = constructed();
            EventSettings settings = new EventSettings(base.kind(), base.formatId(), base.pod(), bestOf,
                    base.roundMinutes(), base.buildMinutes(), base.extraTurns(), base.rounds(), base.topCut(),
                    base.decks(), base.largeEvent());
            Tournament tournament = readyWith(2, settings).startSwiss();
            Pairing pairing = tournament.currentRound().orElseThrow().pairings().get(0);
            List<MatchResult> swiss = MatchResult.offered(bestOf, false);
            assertThat(swiss.stream().map(MatchResult::label).distinct().count()).isEqualTo(swiss.size());
            for (MatchResult result : swiss) {
                Tournament reported = tournament.report(pairing.a(), result).report(pairing.b(), result.flipped());
                assertThat(reported.currentRound().orElseThrow().atTable(1).orElseThrow().result()).isEqualTo(result);
                // Offered from both chairs: whatever one player can say, the other can say back.
                assertThat(swiss).contains(result.flipped());
            }
            assertThat(swiss).contains(new MatchResult(0, 0, 0));
            // A concession is a result of this length too, or settling one refuses it.
            assertThat(MatchResult.conceded(true, bestOf).fits(bestOf)).isTrue();
            assertThat(MatchResult.offered(bestOf, true)).contains(MatchResult.conceded(false, bestOf));
            assertThat(MatchResult.offered(bestOf, true))
                    .isNotEmpty()
                    .isEqualTo(swiss.stream().filter(result -> !result.isDraw()).toList());
        }
        assertThat(MatchResult.offered(3, false))
                .contains(new MatchResult(1, 0, 0), new MatchResult(1, 1, 0), new MatchResult(1, 0, 1));
        // A result reads back from how it is written, and nothing else does.
        for (MatchResult result : List.of(new MatchResult(2, 1, 0), new MatchResult(1, 0, 2), new MatchResult(0, 0, 0))) {
            assertThat(MatchResult.parse(result.label())).contains(result);
        }
        assertThat(MatchResult.parse("")).isEmpty();
        assertThat(MatchResult.parse("9-9-9")).isEmpty();
        assertThat(MatchResult.parse("2-x")).isEmpty();
    }

    @Test
    void agreeingReportsConfirmAndDisagreeingOnesAreSettledByTheHost() {
        Tournament tournament = readyWith(4, constructed()).startSwiss();
        Pairing first = tournament.currentRound().orElseThrow().pairings().get(0);

        Tournament agreed = tournament.report(first.a(), new MatchResult(2, 0, 0))
                .report(first.b(), new MatchResult(0, 2, 0));
        assertThat(agreed.currentRound().orElseThrow().atTable(1).orElseThrow().isConfirmed()).isTrue();

        Tournament disputed = tournament.report(first.a(), new MatchResult(2, 0, 0))
                .report(first.b(), new MatchResult(2, 1, 0));
        Pairing argued = disputed.currentRound().orElseThrow().atTable(1).orElseThrow();
        assertThat(argued.isDisputed()).isTrue();
        assertThat(argued.isConfirmed()).isFalse();

        Tournament settled = disputed.settle(1, new MatchResult(1, 2, 0));
        assertThat(settled.currentRound().orElseThrow().atTable(1).orElseThrow().result())
                .isEqualTo(new MatchResult(1, 2, 0));
    }

    @Test
    void aNextRoundWaitsForEveryResult() {
        Tournament tournament = readyWith(4, constructed()).startSwiss();
        assertThatThrownBy(tournament::nextRound).hasMessage("message.gathering.event.round_not_complete");
    }

    /**
     * Time: the turn in progress and five more, then the match is decided on games won with the
     * unfinished game a draw - up one game to none is a match win.
     */
    @Test
    void aMatchAtTimeIsDecidedOnGamesWon() {
        Tournament tournament = readyWith(4, constructed()).startSwiss().callTime();
        for (int pass = 0; pass <= EventSettings.USUAL_EXTRA_TURNS; pass++) {
            assertThat(tournament.extraTurnsAreOver(1)).as("after " + pass + " turns").isFalse();
            tournament = tournament.turnPassed(1);
        }
        assertThat(tournament.extraTurnsAreOver(1)).isTrue();

        tournament = tournament.endAtTime(1, 1, 0, true);
        Pairing pairing = tournament.currentRound().orElseThrow().atTable(1).orElseThrow();
        assertThat(pairing.result()).isEqualTo(new MatchResult(1, 0, 1));
        assertThat(pairing.result().firstWon()).isTrue();
        Tournament tied = readyWith(4, constructed()).startSwiss().callTime().endAtTime(1, 1, 1, true);
        assertThat(tied.currentRound().orElseThrow().atTable(1).orElseThrow().result().isDraw()).isTrue();
    }

    @Test
    void turnsBeforeTimeAreNotCounted() {
        Tournament tournament = readyWith(4, constructed()).startSwiss();
        for (int pass = 0; pass < 20; pass++) {
            tournament = tournament.turnPassed(1);
        }
        assertThat(tournament.extraTurnsAreOver(1)).isFalse();
    }

    @Test
    void droppingMidRoundConcedesAndIsNeverPairedAgain() {
        Tournament tournament = readyWith(6, constructed()).startSwiss();
        Pairing pairing = tournament.currentRound().orElseThrow().pairingOf(player(0)).orElseThrow();
        UUID opponent = pairing.opponentOf(player(0));
        tournament = tournament.drop(player(0));

        Pairing after = tournament.currentRound().orElseThrow().pairingOf(player(0)).orElseThrow();
        assertThat(after.resultFor(opponent).firstWon()).isTrue();
        assertThat(tournament.entrant(player(0)).orElseThrow().isDropped()).isTrue();

        tournament = playRound(tournament, new Random(1)).nextRound();
        assertThat(tournament.currentRound().orElseThrow().pairingOf(player(0))).isEmpty();
        assertThat(tournament.standings()).extracting(row -> row.player().id()).contains(player(0));
    }

    /** Nine players and a top eight: three Swiss rounds, then a bracket seeded 1v8, 4v5, 2v7, 3v6. */
    @Test
    void aTopEightIsSeededFromTheStandingsAndDecidesTheTopPlaces() {
        EventSettings withCut = new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 8,
                EventSettings.DeckRegistration.OFF, false);
        Tournament tournament = readyWith(9, withCut).startSwiss();
        Random random = new Random(7);
        for (int round = 0; round < 4; round++) {
            tournament = playRound(tournament, random).nextRound();
        }
        assertThat(tournament.phase()).isEqualTo(Tournament.Phase.CUT);
        List<UUID> seeded = tournament.standings().stream().map(row -> row.player().id()).toList();
        List<Pairing> quarter = tournament.currentRound().orElseThrow().pairings();
        assertThat(quarter.get(0).a()).isEqualTo(seeded.get(0));
        assertThat(quarter.get(0).b()).isEqualTo(seeded.get(7));
        assertThat(quarter.get(1).a()).isEqualTo(seeded.get(3));
        assertThat(quarter.get(1).b()).isEqualTo(seeded.get(4));

        Tournament cut = tournament;
        assertThatThrownBy(() -> cut.report(quarter.get(0).a(), new MatchResult(1, 1, 1)))
                .hasMessage("message.gathering.event.cut_needs_a_winner");

        while (tournament.phase() == Tournament.Phase.CUT) {
            tournament = playRound(tournament, random).nextRound();
        }
        assertThat(tournament.phase()).isEqualTo(Tournament.Phase.FINISHED);
        Round last = tournament.rounds().get(tournament.rounds().size() - 1);
        Pairing finalMatch = last.pairings().get(0);
        UUID champion = finalMatch.result().firstWon() ? finalMatch.a() : finalMatch.b();
        assertThat(tournament.finalPlaces().get(0)).isEqualTo(champion);
        assertThat(tournament.finalPlaces()).hasSize(9).doesNotHaveDuplicates();
    }

    /**
     * The higher Swiss seed chooses in the first game of a cut match (MTR 2.2) - and after an
     * upset that is not always the first player of the pairing: the eighth seed who beat the first
     * is paired ahead of the fourth in the semifinal.
     */
    @Test
    void theHigherSwissSeedIsKnownForEveryCutMatch() {
        EventSettings withCut = new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 8,
                EventSettings.DeckRegistration.OFF, false);
        Tournament tournament = readyWith(9, withCut).startSwiss();
        Random random = new Random(7);
        for (int round = 0; round < 4; round++) {
            tournament = playRound(tournament, random).nextRound();
        }
        List<UUID> seeded = tournament.standings().stream().map(row -> row.player().id()).toList();
        for (Pairing pairing : tournament.currentRound().orElseThrow().pairings()) {
            assertThat(tournament.firstSeededHigher(pairing)).isTrue();
            // The upset at the top table, and the favorite everywhere else.
            MatchResult result = pairing.table() == 1 ? new MatchResult(0, 2, 0) : new MatchResult(2, 0, 0);
            tournament = tournament.report(pairing.a(), result).report(pairing.b(), result.flipped());
        }
        tournament = tournament.nextRound();
        Pairing semifinal = tournament.currentRound().orElseThrow().pairings().get(0);
        assertThat(semifinal.a()).isEqualTo(seeded.get(7));
        assertThat(semifinal.b()).isEqualTo(seeded.get(3));
        assertThat(tournament.firstSeededHigher(semifinal)).isFalse();
    }

    /**
     * Time in a cut match tied on games: the higher life total wins the game in progress (MTR 2.4).
     * Tied on life as well, it is left for the host; in the Swiss rounds life never decides it.
     */
    @Test
    void aCutMatchTiedAtTimeGoesToTheHigherLifeTotal() {
        EventSettings withCut = new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 8,
                EventSettings.DeckRegistration.OFF, false);
        Tournament tournament = readyWith(9, withCut).startSwiss();
        Random random = new Random(7);
        for (int round = 0; round < 4; round++) {
            tournament = playRound(tournament, random).nextRound();
        }
        Tournament cut = tournament.callTime();
        assertThat(cut.endAtTime(1, 1, 1, true, 7, 12).currentRound().orElseThrow().atTable(1).orElseThrow().result())
                .isEqualTo(new MatchResult(1, 2, 0));
        assertThat(cut.endAtTime(1, 1, 1, true, 9, 9).currentRound().orElseThrow().atTable(1).orElseThrow().isConfirmed())
                .isFalse();
        // A game up is still decided on games, whatever the life totals say.
        assertThat(cut.endAtTime(1, 1, 0, true, 1, 20).currentRound().orElseThrow().atTable(1).orElseThrow().result())
                .isEqualTo(new MatchResult(1, 0, 1));

        Tournament swiss = readyWith(4, constructed()).startSwiss().callTime();
        assertThat(swiss.endAtTime(1, 1, 1, true, 20, 3).currentRound().orElseThrow().atTable(1).orElseThrow().result())
                .isEqualTo(new MatchResult(1, 1, 1));
        // Games already drawn in the match count among its games.
        assertThat(swiss.endAtTime(1, 1, 0, 1, true, 20, 3).currentRound().orElseThrow().atTable(1).orElseThrow().result())
                .isEqualTo(new MatchResult(1, 0, 2));
    }

    /** Appendix B's building times: 25 minutes after a draft, 30 for sealed. */
    @Test
    void buildingTimeFollowsTheTournamentRules() {
        assertThat(EventSettings.usual(EventSettings.Kind.DRAFT, "").buildMinutes()).isEqualTo(25);
        assertThat(EventSettings.usual(EventSettings.Kind.SEALED, "").buildMinutes()).isEqualTo(30);
        assertThat(EventSettings.usual(EventSettings.Kind.DRAFT, "").problem()).isEmpty();
    }

    /** No cut below nine players, whatever the host set. */
    @Test
    void anEventOfEightHasNoCut() {
        EventSettings withCut = new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 8,
                EventSettings.DeckRegistration.OFF, false);
        Tournament tournament = readyWith(8, withCut).startSwiss();
        Random random = new Random(3);
        for (int round = 0; round < 3; round++) {
            tournament = playRound(tournament, random).nextRound();
        }
        assertThat(tournament.phase()).isEqualTo(Tournament.Phase.FINISHED);
    }

    /**
     * Any field, any results: every player still in is paired exactly once a round, nobody has a
     * second bye while somebody has had none, and no two players meet twice where the field is big
     * enough to avoid it.
     */
    @Property(tries = 150)
    void everyRoundPairsEveryPlayerOnce(
            @ForAll @IntRange(min = 5, max = 32) int players, @ForAll long seed) {
        Random random = new Random(seed);
        Tournament tournament = readyWith(players, constructed()).startSwiss();
        Set<String> met = new HashSet<>();
        Set<UUID> byes = new HashSet<>();
        int rounds = 0;
        while (tournament.phase() == Tournament.Phase.SWISS) {
            Round round = tournament.currentRound().orElseThrow();
            List<UUID> seen = new ArrayList<>();
            for (Pairing pairing : round.pairings()) {
                seen.add(pairing.a());
                if (pairing.isBye()) {
                    assertThat(byes.add(pairing.a())).as("a second bye in a field of " + players).isTrue();
                } else {
                    seen.add(pairing.b());
                    assertThat(met.add(SwissPairer.key(pairing.a(), pairing.b())))
                            .as("a rematch in round " + round.number() + " of " + players).isTrue();
                }
            }
            assertThat(seen).doesNotHaveDuplicates().hasSize(tournament.stillIn().size());
            tournament = playRound(tournament, random).nextRound();
            rounds++;
        }
        assertThat(rounds).isEqualTo(SwissRounds.forPlayers(players));
        assertThat(tournament.standings()).hasSize(players);
    }

    @Test
    void standingsCountPointsAndFloorTheTiebreakers() {
        Tournament tournament = readyWith(4, constructed()).startSwiss();
        Round round = tournament.currentRound().orElseThrow();
        for (Pairing pairing : round.pairings()) {
            tournament = tournament.settle(pairing.table(), new MatchResult(2, 0, 0));
        }
        List<Standings.Row> rows = tournament.standings();
        Standings.Row winner = rows.get(0);
        assertThat(winner.matchPoints()).isEqualTo(3);
        // Beat somebody with no wins: their match-win percentage counts as a third, not zero.
        assertThat(winner.opponentsMatchWin()).isEqualTo(Standings.FLOOR);
        assertThat(rows.get(3).gameWin()).isEqualTo(Standings.FLOOR);
    }

    @Test
    void aTournamentRoundTripsThroughItsBytesMidEvent() throws Exception {
        EventSettings draft = EventSettings.usual(EventSettings.Kind.DRAFT, "");
        Tournament tournament = readyWith(6, draft).startSwiss();
        Pairing pairing = tournament.currentRound().orElseThrow().pairings().get(0);
        tournament = tournament.report(pairing.a(), new MatchResult(2, 1, 0)).callTime().turnPassed(2).drop(player(5));
        assertThat(TournamentCodec.read(TournamentCodec.write(tournament))).isEqualTo(tournament);
    }

    /** An event saved before pack settings had a clock still loads, with no clock. */
    @Test
    void aTournamentSavedBeforeThePickClockStillReads() throws Exception {
        Tournament tournament = readyWith(6, EventSettings.usual(EventSettings.Kind.DRAFT, "")).startSwiss();
        byte[] now = TournamentCodec.write(tournament);
        // The clock is the last thing written for the pack settings, just before the phase.
        String phase = tournament.phase().name();
        int phaseAt = indexOf(now, phase.getBytes(java.nio.charset.StandardCharsets.UTF_8)) - 2;
        byte[] old = new byte[now.length - 4];
        System.arraycopy(now, 0, old, 0, phaseAt - 4);
        System.arraycopy(now, phaseAt, old, phaseAt - 4, now.length - phaseAt);
        old[3] = 1;
        assertThat(TournamentCodec.read(old)).isEqualTo(tournament);
        assertThat(TournamentCodec.read(old).settings().pod().pickSeconds()).isZero();
    }

    private static int indexOf(byte[] in, byte[] wanted) {
        for (int at = 0; at + wanted.length <= in.length; at++) {
            if (java.util.Arrays.equals(in, at, at + wanted.length, wanted, 0, wanted.length)) {
                return at;
            }
        }
        throw new AssertionError("not found");
    }

    @Test
    void checkInLeavesOutPlayersWhoDidNotCome() {
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Big", HOST,
                new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 0,
                        EventSettings.DeckRegistration.LOCKED, true));
        for (int index = 0; index < 5; index++) {
            tournament = tournament.register(Entrant.registering(player(index), "P" + index, 1500));
        }
        tournament = tournament.openCheckIn();
        for (int index = 0; index < 3; index++) {
            tournament = tournament.checkIn(player(index));
        }
        Tournament playing = tournament.beginPreparing();
        assertThat(playing.entrants()).extracting(Entrant::id).containsExactly(player(0), player(1), player(2));
        Tournament closed = tournament;
        assertThatThrownBy(() -> closed.register(Entrant.registering(player(9), "Late", 1500)))
                .hasMessage("message.gathering.event.registration_closed");
    }

    @Test
    void settingsThatCannotRunAreRefused() {
        assertThat(EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "").problem())
                .contains("message.gathering.event.needs_a_format");
        assertThat(new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 2, 50, 30, 5, 0, 0, null, false)
                .problem()).contains("message.gathering.event.best_of");
        assertThat(new EventSettings(EventSettings.Kind.CONSTRUCTED, "modern", null, 3, 50, 30, 5, 0, 6, null, false)
                .problem()).contains("message.gathering.event.top_cut");
        assertThat(EventSettings.usual(EventSettings.Kind.SEALED, "").problem()).isEmpty();
    }

    /** Counts are bounded one by one, so no three of them add up past the top of an int and through the check. */
    @Test
    void aResultIsBoundedCountByCount() {
        assertThat(MatchResult.isAMatch(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 0)).isFalse();
        assertThat(MatchResult.isAMatch(9, 0, 0)).isTrue();
        assertThat(MatchResult.isAMatch(9, 1, 0)).isFalse();
        assertThat(MatchResult.isAMatch(-1, 2, 0)).isFalse();
        assertThat(new MatchResult(2, 1, 0).fits(3)).isTrue();
        assertThat(new MatchResult(3, 0, 0).fits(3)).isFalse();
        assertThat(new MatchResult(2, 2, 0).fits(3)).isFalse();
        assertThat(new MatchResult(1, 1, 1).fits(3)).isTrue();
        assertThat(new MatchResult(1, 0, 0).fits(1)).isTrue();
        assertThat(new MatchResult(2, 0, 0).fits(1)).isFalse();
        assertThat(new MatchResult(3, 2, 0).fits(5)).isTrue();
    }

    /** A report longer than the match is refused, from a player and from the host. */
    @Test
    void aResultLongerThanTheMatchIsRefused() {
        Tournament tournament = readyWith(4, constructed()).startSwiss();
        Pairing pairing = tournament.currentRound().orElseThrow().pairings().get(0);
        Tournament playing = tournament;
        assertThatThrownBy(() -> playing.report(pairing.a(), new MatchResult(3, 0, 0)))
                .hasMessage("message.gathering.event.not_a_result");
        assertThatThrownBy(() -> playing.settle(pairing.table(), new MatchResult(2, 2, 0)))
                .hasMessage("message.gathering.event.not_a_result");
    }

    /** A save from before counts were bounded one by one loads with the impossible report gone, not refused whole. */
    @Test
    void aSavedReportThatIsNotAMatchLoadsAsNoReport() throws Exception {
        Tournament tournament = readyWith(4, constructed()).startSwiss();
        Pairing pairing = tournament.currentRound().orElseThrow().pairings().get(0);
        tournament = tournament.report(pairing.a(), new MatchResult(2, 1, 0));
        byte[] bytes = TournamentCodec.write(tournament);
        // The report is written as a present flag and three ints: 2, 1, 0. Overwrite them with
        // counts whose total overflows.
        byte[] pattern = {1, 0, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 0};
        int at = -1;
        for (int index = 0; index + pattern.length <= bytes.length && at < 0; index++) {
            if (java.util.Arrays.equals(bytes, index, index + pattern.length, pattern, 0, pattern.length)) {
                at = index;
            }
        }
        assertThat(at).isNotNegative();
        java.nio.ByteBuffer.wrap(bytes, at + 1, 12).putInt(Integer.MAX_VALUE).putInt(Integer.MAX_VALUE - 1).putInt(0);
        Tournament loaded = TournamentCodec.read(bytes);
        Pairing back = loaded.currentRound().orElseThrow().pairings().get(0);
        assertThat(back.reportA()).isNull();
        assertThat(loaded.entrants()).isEqualTo(tournament.entrants());
    }

    /** A tournament's players keep their pools; a pod's other card policies are for pods on their own. */
    @Test
    void aTournamentsPlayersKeepTheirPools() {
        var sponsored = new dev.gathering.core.draft.PodSettings(dev.gathering.core.draft.PodSettings.Kind.DRAFT,
                dev.gathering.core.draft.PodSettings.Source.SPONSORED, dev.gathering.core.draft.PodSettings.SetRule.ANY,
                3, 0, dev.gathering.core.draft.PodSettings.CardsGo.TO_SPONSOR);
        assertThat(sponsored.problem()).isEmpty();
        assertThat(new EventSettings(EventSettings.Kind.DRAFT, "", sponsored, 3, 50, 30, 5, 0, 0, null, false).problem())
                .contains("message.gathering.event.players_keep_pools");
    }

    /** Dropping the second player of a finished match leaves the first player's win counted for pairing. */
    @Test
    void aWinOverAnOpponentWhoDroppedStillCountsFromEitherChair() {
        Entrant a = Entrant.registering(new UUID(0, 1), "A", 1400);
        Entrant b = Entrant.registering(new UUID(0, 2), "B", 1600);
        Entrant c = Entrant.registering(new UUID(0, 3), "C", 1500);
        Entrant d = Entrant.registering(new UUID(0, 4), "D", 1700);
        Round previous = new Round(1, false, List.of(
                Pairing.of(1, b.id(), a.id()).settled(new MatchResult(2, 0, 0)),
                Pairing.of(2, c.id(), d.id()).settled(new MatchResult(2, 0, 0))), false);
        List<Pairing> next = SwissPairer.pair(List.of(b, c, d), List.of(previous), 2);
        assertThat(next.stream().filter(Pairing::isBye).findFirst().orElseThrow().a()).isEqualTo(d.id());
        assertThat(Standings.of(List.of(b, c, d), List.of(previous)).get(2).player()).isEqualTo(d);
    }

    @Test
    void roundsGrowWithTheField() {
        assertThat(SwissRounds.forPlayers(4)).isEqualTo(2);
        assertThat(SwissRounds.forPlayers(5)).isEqualTo(3);
        assertThat(SwissRounds.forPlayers(8)).isEqualTo(3);
        assertThat(SwissRounds.forPlayers(9)).isEqualTo(4);
        assertThat(SwissRounds.forPlayers(32)).isEqualTo(5);
        assertThat(SwissRounds.forPlayers(33)).isEqualTo(6);
        // Appendix E's large-event rows, which do not fall on powers of two.
        assertThat(SwissRounds.forPlayers(128)).isEqualTo(7);
        assertThat(SwissRounds.forPlayers(129)).isEqualTo(8);
        assertThat(SwissRounds.forPlayers(226)).isEqualTo(8);
        assertThat(SwissRounds.forPlayers(227)).isEqualTo(9);
        assertThat(SwissRounds.forPlayers(409)).isEqualTo(9);
        assertThat(SwissRounds.forPlayers(410)).isEqualTo(10);
        assertThat(SwissRounds.forPlayers(2_000)).isEqualTo(10);
    }
}
