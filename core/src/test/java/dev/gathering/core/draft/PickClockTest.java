package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** When the pick clock runs out, the late drafters take the first cards in their packs. */
class PickClockTest {

    private static final PlayerRef ALICE = new PlayerRef(new UUID(3L, 1L), "Alice");
    private static final PlayerRef BOB = new PlayerRef(new UUID(3L, 2L), "Bob");
    private static final PlayerRef CHRIS = new PlayerRef(new UUID(3L, 3L), "Chris");
    private static final PlayerRef DANA = new PlayerRef(new UUID(3L, 4L), "Dana");

    private static DraftPod pod() {
        List<List<DraftPack>> rounds = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            List<DraftPack> packs = new ArrayList<>();
            for (int drafter = 0; drafter < 4; drafter++) {
                List<CardIdentity> cards = new ArrayList<>();
                for (int card = 0; card < 4; card++) {
                    cards.add(CardIdentity.ofPrinting(new UUID(round * 100L + drafter, card), false));
                }
                packs.add(new DraftPack(cards));
            }
            rounds.add(packs);
        }
        return DraftPod.opening(List.of(ALICE, BOB, CHRIS, DANA), rounds, true);
    }

    @Test
    void onlyTheLatePickAndTheyTakeTheFirstCards() {
        DraftPod pod = pod();
        DrafterId bob = pod.placeOf(BOB.id()).orElseThrow();
        DraftPack bobsPack = pod.state().packHeldBy(bob);
        // Alice has picked her last two; everybody else is late.
        DrafterId alice = pod.placeOf(ALICE.id()).orElseThrow();
        List<CardIdentity> aliceChose = pod.state().packHeldBy(alice).at(List.of(2, 3));
        pod = pod.declare(ALICE.id(), alice, List.of(2, 3));
        assertThat(PickClock.late(pod)).containsExactly(BOB, CHRIS, DANA);

        DraftPod after = PickClock.pickForTheLate(pod);

        assertThat(after.state().poolOf(alice)).isEqualTo(aliceChose);
        assertThat(after.state().poolOf(bob)).isEqualTo(bobsPack.at(List.of(0, 1)));
        assertThat(PickClock.late(after)).containsExactly(ALICE, BOB, CHRIS, DANA);
        assertThat(PickClock.turnOf(after.state())).isNotEqualTo(PickClock.turnOf(pod.state()));
    }

    @Test
    void declaringDoesNotRestartTheClockAndTheLastLateOneMovesThePacks() {
        DraftPod pod = pod();
        long turn = PickClock.turnOf(pod.state());
        for (PlayerRef drafter : List.of(ALICE, BOB, CHRIS)) {
            pod = pod.declare(drafter.id(), pod.placeOf(drafter.id()).orElseThrow(), List.of(0, 1));
        }
        assertThat(PickClock.turnOf(pod.state())).isEqualTo(turn);
        assertThat(PickClock.late(pod)).containsExactly(DANA);
        DraftPod after = PickClock.pickForTheLate(pod);
        assertThat(PickClock.turnOf(after.state())).isNotEqualTo(turn);
        assertThat(after.state().poolOf(DrafterId.of(0))).hasSize(2);
    }

    @Test
    void aFinishedPodHasNobodyLate() {
        DraftPod pod = pod();
        while (!pod.isFinished()) {
            pod = PickClock.pickForTheLate(pod);
        }
        assertThat(PickClock.late(pod)).isEmpty();
        assertThat(PickClock.pickForTheLate(pod)).isSameAs(pod);
    }

    @Test
    void aPodLeftAloneRunsOutOnTheClock() {
        DraftPod pod = pod();
        int turns = 0;
        while (!pod.isFinished()) {
            pod = PickClock.pickForTheLate(pod);
            turns++;
        }
        assertThat(turns).isEqualTo(4);
        assertThat(pod.state().poolOf(DrafterId.of(0))).hasSize(8);
    }

    /** MTR Appendix B's draft timing, read by how many cards are left in the pack. */
    @Test
    void tournamentTimingFollowsTheTournamentRules() {
        int[] fifteen = {40, 40, 35, 30, 25, 25, 20, 20, 15, 10, 10, 5, 5, 5, 5};
        for (int pick = 0; pick < fifteen.length; pick++) {
            assertThat(PickClock.tournamentSecondsFor(15 - pick)).as("pick %d of 15", pick + 1).isEqualTo(fifteen[pick]);
        }
        assertThat(PickClock.secondsFor(PodSettings.TOURNAMENT_TIMING, 11)).isEqualTo(25);
        assertThat(PickClock.secondsFor(45, 11)).isEqualTo(45);
        assertThat(PickClock.isOn(PodSettings.TOURNAMENT_TIMING)).isTrue();
        assertThat(PickClock.isOn(0)).isFalse();
        assertThat(PodSettings.usual(PodSettings.Kind.DRAFT).problem()).isEmpty();
        assertThat(new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.EACH_BRINGS, PodSettings.SetRule.ANY, 3, 0,
                PodSettings.CardsGo.PLAYERS_KEEP, PodSettings.TOURNAMENT_TIMING).problem()).isEmpty();
        assertThat(new PodSettings(PodSettings.Kind.SEALED, PodSettings.Source.EACH_BRINGS, PodSettings.SetRule.ANY, 6, 0,
                PodSettings.CardsGo.PLAYERS_KEEP, PodSettings.TOURNAMENT_TIMING).problem()).isPresent();
        assertThat(new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.EACH_BRINGS, PodSettings.SetRule.ANY, 3, 0,
                PodSettings.CardsGo.PLAYERS_KEEP, -2).problem()).isPresent();
    }

    @Test
    void theClockCountsWholeSecondsFromTheTurn() {
        assertThat(PickClock.secondsLeft(45, 100, 100)).isEqualTo(45);
        assertThat(PickClock.secondsLeft(45, 100, 101)).isEqualTo(45);
        assertThat(PickClock.secondsLeft(45, 100, 120)).isEqualTo(44);
        assertThat(PickClock.secondsLeft(45, 100, 100 + 45 * 20)).isZero();
        assertThat(PickClock.isUp(45, 100, 100 + 45 * 20 - 1)).isFalse();
        assertThat(PickClock.isUp(45, 100, 100 + 45 * 20)).isTrue();
        assertThat(PickClock.isUp(0, 0, 1_000_000)).isFalse();
    }
}
