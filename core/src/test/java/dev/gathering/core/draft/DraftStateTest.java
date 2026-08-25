package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The pod, run by hand through the situations that decide whether it is a draft. */
class DraftStateTest {

    /** A pod is four to eight; anything else is a different format with different rules. */
    @Test
    void aPodIsFourToEightDrafters() {
        for (int size = 0; size < 12; size++) {
            assertThat(DraftRules.isAPodSize(size))
                    .describedAs("a pod of %s", size)
                    .isEqualTo(size >= 4 && size <= 8);
        }
    }

    /** Small pods pick two, because otherwise the back half of every pack is chaff. */
    @Test
    void smallPodsPickTwoAndLargeOnesPickOne() {
        assertThat(DraftRules.picksPerTurn(4)).isEqualTo(2);
        assertThat(DraftRules.picksPerTurn(5)).isEqualTo(2);
        assertThat(DraftRules.picksPerTurn(6)).isEqualTo(1);
        assertThat(DraftRules.picksPerTurn(8)).isEqualTo(1);
    }

    /** And the packs change direction each round, the way paper does. */
    @Test
    void thePacksChangeDirectionEachRound() {
        assertThat(DraftRules.passing(0)).isEqualTo(1);
        assertThat(DraftRules.passing(1)).isEqualTo(-1);
        assertThat(DraftRules.passing(2)).isEqualTo(1);
    }

    /**
     * Nothing moves until everybody has picked.
     *
     * <p>Real drafting is simultaneous. Resolving each pick as it arrived would hand the
     * pack on while somebody was still reading it, and the fastest drafter in the pod would
     * see packs the slowest one never got.
     */
    @Test
    void thePacksDoNotMoveUntilEverybodyHasPicked() {
        DraftState pod = podOf(4, 8);
        DraftPack first = pod.packHeldBy(DrafterId.of(1));

        DraftState afterOne = pod.declare(DrafterId.of(0), List.of(0, 1));

        assertThat(afterOne.packHeldBy(DrafterId.of(1)))
                .describedAs("a neighbour's pack has not moved")
                .isEqualTo(first);
        assertThat(afterOne.poolOf(DrafterId.of(0)))
                .describedAs("and nothing has reached a pool yet either")
                .isEmpty();
        assertThat(afterOne.stillToPick())
                .containsExactly(DrafterId.of(1), DrafterId.of(2), DrafterId.of(3));
    }

    /**
     * A pick-two takes both of the cards the drafter pointed at.
     *
     * <p>The trap this exists for: removing the first pick shifts everything after it along,
     * so a second index applied afterwards means a different card than the one on the screen.
     * Both are named against the pack as it was, and both come out together.
     */
    @Test
    void aPickOfTwoTakesBothCardsThatWerePointedAt() {
        DraftState pod = podOf(4, 8);
        DraftPack pack = pod.packHeldBy(DrafterId.of(0));
        CardIdentity second = pack.at(1);
        CardIdentity third = pack.at(2);

        DraftState after = everybodyPicks(pod, List.of(1, 2));

        assertThat(after.poolOf(DrafterId.of(0))).containsExactly(second, third);
        assertThat(after.packHeldBy(DrafterId.of(1)).cards())
                .describedAs("and the pack passed on is missing exactly those two")
                .doesNotContain(second, third)
                .hasSize(6);
    }

    /** Round nought passes to the left, so the pack you were holding lands on your left. */
    @Test
    void aPackGoesToTheDrafterOnTheLeftInTheFirstRound() {
        DraftState pod = podOf(4, 8);
        List<CardIdentity> mineAfterPicking =
                pod.packHeldBy(DrafterId.of(0)).without(List.of(0, 1)).cards();

        DraftState after = everybodyPicks(pod, List.of(0, 1));

        assertThat(after.packHeldBy(DrafterId.of(1)).cards()).isEqualTo(mineAfterPicking);
    }

    /** And the other way in the second, which is what alternating means on the table. */
    @Test
    void aPackGoesToTheDrafterOnTheRightInTheSecondRound() {
        DraftState pod = drainRound(podOf(4, 4));
        assertThat(pod.round()).isEqualTo(1);
        List<CardIdentity> mineAfterPicking =
                pod.packHeldBy(DrafterId.of(1)).without(List.of(0, 1)).cards();

        DraftState after = everybodyPicks(pod, List.of(0, 1));

        assertThat(after.packHeldBy(DrafterId.of(0)).cards()).isEqualTo(mineAfterPicking);
    }

    /**
     * The last card of a pack is picked on its own, even in a pod that picks two.
     *
     * <p>A pack with an odd number of cards ends on a single, and asking for two would have
     * stalled the pod one card from the end of every round.
     */
    @Test
    void anOddPackEndsOnASinglePick() {
        DraftState pod = podOf(4, 3);

        assertThat(pod.picksDueFrom(DrafterId.of(0))).isEqualTo(2);
        DraftState after = everybodyPicks(pod, List.of(0, 1));
        assertThat(after.picksDueFrom(DrafterId.of(0))).isEqualTo(1);

        DraftState empty = everybodyPicks(after, List.of(0));
        assertThat(empty.round()).describedAs("and that empties the round").isEqualTo(1);
    }

    /** A round ends the moment its packs are empty, not a turn of empty passing later. */
    @Test
    void aRoundEndsAsSoonAsThePacksAreEmpty() {
        DraftState pod = everybodyPicks(podOf(4, 2), List.of(0, 1));

        assertThat(pod.round()).isEqualTo(1);
        assertThat(pod.stillToPick()).hasSize(4);
        assertThat(pod.packHeldBy(DrafterId.of(0)).size())
                .describedAs("holding the next round's pack, not an empty one")
                .isEqualTo(2);
    }

    /** Three rounds, and then the pod is done and holding nothing. */
    @Test
    void aPodFinishesAfterItsLastRound() {
        DraftState pod = podOf(4, 2);
        for (int round = 0; round < 3; round++) {
            pod = everybodyPicks(pod, List.of(0, 1));
        }

        assertThat(pod.isFinished()).isTrue();
        assertThat(pod.stillToPick()).isEmpty();
        assertThat(pod.picksDueFrom(DrafterId.of(0))).isZero();
        assertThat(pod.packHeldBy(DrafterId.of(0)).isEmpty()).isTrue();
    }

    /**
     * Every card opened ends up in exactly one pool, and nowhere else.
     *
     * <p>The conservation property. A pack passing bug does not announce itself - it drops a
     * card, or hands the same one to two drafters - and either way the pod plays to the end
     * looking fine.
     */
    @Test
    void everyCardOpenedEndsInExactlyOnePool() {
        for (int drafters = 4; drafters <= 8; drafters++) {
            DraftState pod = podOf(drafters, 6);
            Set<CardIdentity> opened = new LinkedHashSet<>();
            int openedCount = 0;
            for (List<DraftPack> round : pod.opening()) {
                for (DraftPack pack : round) {
                    opened.addAll(pack.cards());
                    openedCount += pack.size();
                }
            }

            DraftState finished = draftToTheEnd(pod);

            List<CardIdentity> drafted = new ArrayList<>();
            for (int index = 0; index < drafters; index++) {
                drafted.addAll(finished.poolOf(DrafterId.of(index)));
            }
            assertThat(drafted)
                    .describedAs("a pod of %s drafted every card exactly once", drafters)
                    .hasSize(openedCount)
                    .containsExactlyInAnyOrderElementsOf(opened);
        }
    }

    /** Picking again before the packs move is picking twice from one pack. */
    @Test
    void aDrafterCannotPickTwiceInOneTurn() {
        DraftState pod = podOf(4, 8).declare(DrafterId.of(0), List.of(0, 1));

        assertThatThrownBy(() -> pod.declare(DrafterId.of(0), List.of(2, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already picked");
    }

    /** And the same card cannot be taken twice in one pick, which is a pool out of nothing. */
    @Test
    void aDrafterCannotTakeTheSameCardTwice() {
        DraftState pod = podOf(4, 8);

        assertThatThrownBy(() -> pod.declare(DrafterId.of(0), List.of(2, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
    }

    /** Nor a card that is not in the pack in front of them. */
    @Test
    void aDrafterCannotTakeACardThatIsNotThere() {
        DraftState pod = podOf(4, 8);

        assertThatThrownBy(() -> pod.declare(DrafterId.of(0), List.of(0, 99)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pod.declare(DrafterId.of(0), List.of(0, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Nor fewer or more cards than the pod's size says. */
    @Test
    void aDrafterPicksExactlyAsManyAsThePodSizeSays() {
        DraftState podOfFour = podOf(4, 8);
        assertThatThrownBy(() -> podOfFour.declare(DrafterId.of(0), List.of(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("picks 2");

        DraftState podOfSix = podOf(6, 8);
        assertThatThrownBy(() -> podOfSix.declare(DrafterId.of(0), List.of(0, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("picks 1");
    }

    /** A finished pod takes no more picks at all. */
    @Test
    void aFinishedPodTakesNoMorePicks() {
        DraftState pod = draftToTheEnd(podOf(4, 4));

        assertThatThrownBy(() -> pod.declare(DrafterId.of(0), List.of(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finished");
    }

    /**
     * The same opening packs and the same picks give back the same pools.
     *
     * <p>Which is what makes a pod something the server can log and replay rather than
     * something it has to keep in memory and hope survives a restart.
     */
    @Test
    void thePodIsTheSameWhenItIsPlayedAgain() {
        List<List<DraftPack>> opening = openingFor(5, 7);

        assertThat(draftToTheEnd(DraftState.opening(5, opening)))
                .isEqualTo(draftToTheEnd(DraftState.opening(5, opening)));
    }

    /** A pod needs one pack per drafter in every round, or somebody sits out. */
    @Test
    void everyRoundNeedsOnePackPerDrafter() {
        List<List<DraftPack>> short0 = List.of(List.of(packOf(3), packOf(3)));

        assertThatThrownBy(() -> DraftState.opening(4, short0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one pack per drafter");
    }

    // --- helpers ---

    private static DraftState podOf(int drafters, int packSize) {
        return DraftState.opening(drafters, openingFor(drafters, packSize));
    }

    private static List<List<DraftPack>> openingFor(int drafters, int packSize) {
        List<List<DraftPack>> rounds = new ArrayList<>(DraftRules.ROUNDS);
        for (int round = 0; round < DraftRules.ROUNDS; round++) {
            List<DraftPack> packs = new ArrayList<>(drafters);
            for (int drafter = 0; drafter < drafters; drafter++) {
                packs.add(packOf(packSize, "r" + round + "d" + drafter));
            }
            rounds.add(packs);
        }
        return rounds;
    }

    private static DraftPack packOf(int size) {
        return packOf(size, "pack");
    }

    private static DraftPack packOf(int size, String tag) {
        List<CardIdentity> cards = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            cards.add(CardIdentity.ofPrinting(UUID.nameUUIDFromBytes(
                    (tag + "-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        return DraftPack.of(cards);
    }

    /** Everybody takes the cards at these places, so the turn resolves in one call. */
    private static DraftState everybodyPicks(DraftState pod, List<Integer> positions) {
        DraftState after = pod;
        for (int index = 0; index < pod.drafters(); index++) {
            after = after.declare(DrafterId.of(index), positions);
        }
        return after;
    }

    /** One round emptied by everybody always taking from the front. */
    private static DraftState drainRound(DraftState pod) {
        int round = pod.round();
        DraftState after = pod;
        while (!after.isFinished() && after.round() == round) {
            after = takeFromTheFront(after);
        }
        return after;
    }

    private static DraftState draftToTheEnd(DraftState pod) {
        DraftState after = pod;
        while (!after.isFinished()) {
            after = takeFromTheFront(after);
        }
        return after;
    }

    private static DraftState takeFromTheFront(DraftState pod) {
        DraftState after = pod;
        for (int index = 0; index < pod.drafters(); index++) {
            DrafterId drafter = DrafterId.of(index);
            int due = after.picksDueFrom(drafter);
            List<Integer> front = new ArrayList<>(due);
            for (int position = 0; position < due; position++) {
                front.add(position);
            }
            after = after.declare(drafter, front);
        }
        return after;
    }
}
