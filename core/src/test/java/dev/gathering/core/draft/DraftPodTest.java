package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The pod with people in it, and the one thing it says no to. */
class DraftPodTest {

    private static final PlayerRef ALICE = who("Alice");
    private static final PlayerRef BOB = who("Bob");
    private static final PlayerRef CHRIS = who("Chris");
    private static final PlayerRef DANA = who("Dana");

    /**
     * A drafter can only pick for themselves.
     *
     * <p>The security rule, and the only one. Declaring somebody else's pick would empty a
     * pack this client is not allowed to read, and what came back afterwards would say what
     * had been in it - so a client that could sign a pick with another place could read the
     * whole pod one pack at a time.
     */
    @Test
    void aDrafterCanOnlyPickForThemselves() {
        DraftPod pod = pod();

        assertThat(pod.denialFor(ALICE.id(), DrafterId.of(1), List.of(0, 1)))
                .contains("A drafter can only pick for themselves.");
        assertThatThrownBy(() -> pod.declare(ALICE.id(), DrafterId.of(1), List.of(0, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only pick for themselves");
    }

    /** And somebody who is not in the pod cannot pick at all. */
    @Test
    void somebodyOutsideThePodCannotPick() {
        DraftPod pod = pod();

        assertThat(pod.denialFor(who("Nobody").id(), DrafterId.of(0), List.of(0, 1)))
                .contains("Only drafters in this pod can pick.");
        assertThat(pod.denialFor(null, null, List.of(0, 1)))
                .contains("Only drafters in this pod can pick.");
    }

    /**
     * A pick naming a card that is not in the pack is refused, not thrown.
     *
     * <p>{@code denialFor} is what the network handler asks before it does anything, and for
     * a long time it counted the picks without looking at them. So a client naming card 999
     * of an eight-card pack, or the same card twice, was told it could go ahead and then
     * {@code declare} threw - out of a payload handler, on the server thread. On one loader
     * that disconnects the client and on the other it reaches the server's task queue. The
     * ordinary way to send one of these is not even malice: it is a drafter whose screen is a
     * moment behind the pod, which the refusal path was written for.
     */
    @Test
    void aPickAtACardThatIsNotThereIsRefusedRatherThanThrown() {
        DraftPod pod = pod();

        assertThat(pod.denialFor(ALICE.id(), DrafterId.of(0), List.of(0, 999)))
                .describedAs("a position past the end of the pack")
                .isPresent();
        assertThat(pod.denialFor(ALICE.id(), DrafterId.of(0), List.of(0, -1)))
                .describedAs("a negative position")
                .isPresent();
        assertThat(pod.denialFor(ALICE.id(), DrafterId.of(0), List.of(3, 3)))
                .describedAs("the same card picked twice")
                .isPresent();

        // And the refusal is the whole gate: anything denialFor lets past must not throw.
        assertThat(pod.denialFor(ALICE.id(), DrafterId.of(0), List.of(0, 1))).isEmpty();
        pod.declare(ALICE.id(), DrafterId.of(0), List.of(0, 1));
    }

    /** Signing with your own place, or not naming one at all, both work. */
    @Test
    void aDrafterPickingAsThemselvesIsAllowed() {
        DraftPod pod = pod();

        assertThat(pod.denialFor(ALICE.id(), DrafterId.of(0), List.of(0, 1))).isEmpty();
        assertThat(pod.denialFor(ALICE.id(), null, List.of(0, 1))).isEmpty();

        DraftPod after = pod.declare(ALICE.id(), DrafterId.of(0), List.of(0, 1));
        assertThat(after.state().hasDeclared(DrafterId.of(0))).isTrue();
    }

    /** Picking twice from one pack is refused with a sentence, not an exception nobody reads. */
    @Test
    void pickingTwiceFromOnePackIsRefused() {
        DraftPod pod = pod().declare(ALICE.id(), null, List.of(0, 1));

        assertThat(pod.denialFor(ALICE.id(), null, List.of(2, 3)))
                .contains("You have already picked from this pack.");
    }

    /** As is picking the wrong number, which is what a stale screen would send. */
    @Test
    void pickingTheWrongNumberIsRefusedWithTheRightOne() {
        assertThat(pod().denialFor(ALICE.id(), null, List.of(0)))
                .contains("Pick 2 from this pack.");
        assertThat(pod().denialFor(ALICE.id(), null, List.of()))
                .contains("Pick 2 from this pack.");
        assertThat(pod().denialFor(ALICE.id(), null, null))
                .contains("Pick 2 from this pack.");
    }

    /** And picking at all once the pod is done. */
    @Test
    void aFinishedPodRefusesEverything() {
        DraftPod pod = draftToTheEnd(pod());

        assertThat(pod.isFinished()).isTrue();
        assertThat(pod.denialFor(ALICE.id(), null, List.of(0, 1)))
                .contains("This pod has finished drafting.");
    }

    /** One player cannot hold two places, which would be one client reading two packs. */
    @Test
    void onePlayerCannotHoldTwoPlaces() {
        assertThatThrownBy(() -> DraftPod.opening(
                List.of(ALICE, BOB, CHRIS, ALICE), openingFor(4, 8), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two places");
    }

    /** A pod needs a person for every pack it deals. */
    @Test
    void aPodNeedsAPersonForEveryPack() {
        assertThatThrownBy(() -> DraftPod.opening(List.of(ALICE, BOB, CHRIS), openingFor(4, 8), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Drafters keep what they drafted, unless the sponsor said otherwise at the start. */
    @Test
    void drafterskeepTheirPoolsUnlessTheSponsorSaidOtherwise() {
        DraftPod kept = draftToTheEnd(pod(true));
        DraftPod returned = draftToTheEnd(pod(false));

        assertThat(kept.pooledAway().get(0))
                .isEqualTo(kept.state().poolOf(DrafterId.of(0)))
                .isNotEmpty();
        assertThat(returned.pooledAway())
                .describedAs("a sponsored pod that keeps its cards hands nothing out")
                .allSatisfy(pool -> assertThat(pool).isEmpty());
        assertThat(returned.state().poolOf(DrafterId.of(0)))
                .describedAs("but the draft still happened and its record stands")
                .isNotEmpty();
    }

    // --- helpers ---

    private static PlayerRef who(String name) {
        return new PlayerRef(UUID.nameUUIDFromBytes(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name);
    }

    private static DraftPod pod() {
        return pod(true);
    }

    private static DraftPod pod(boolean poolsAreKept) {
        return DraftPod.opening(List.of(ALICE, BOB, CHRIS, DANA), openingFor(4, 8), poolsAreKept);
    }

    private static List<List<DraftPack>> openingFor(int drafters, int packSize) {
        List<List<DraftPack>> rounds = new ArrayList<>(DraftRules.ROUNDS);
        for (int round = 0; round < DraftRules.ROUNDS; round++) {
            List<DraftPack> packs = new ArrayList<>(drafters);
            for (int drafter = 0; drafter < drafters; drafter++) {
                List<CardIdentity> cards = new ArrayList<>(packSize);
                for (int card = 0; card < packSize; card++) {
                    cards.add(CardIdentity.ofPrinting(UUID.nameUUIDFromBytes(
                            ("r" + round + "d" + drafter + "c" + card)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                }
                packs.add(DraftPack.of(cards));
            }
            rounds.add(packs);
        }
        return rounds;
    }

    private static DraftPod draftToTheEnd(DraftPod pod) {
        DraftPod after = pod;
        while (!after.isFinished()) {
            for (int index = 0; index < after.drafters().size(); index++) {
                DrafterId place = DrafterId.of(index);
                int due = after.state().picksDueFrom(place);
                if (due == 0) {
                    continue;
                }
                List<Integer> front = new ArrayList<>(due);
                for (int position = 0; position < due; position++) {
                    front.add(position);
                }
                after = after.declare(after.drafterAt(place).orElseThrow().id(), place, front);
            }
        }
        return after;
    }
}
