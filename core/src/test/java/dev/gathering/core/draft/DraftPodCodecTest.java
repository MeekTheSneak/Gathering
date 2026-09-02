package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.PlayerRef;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * A pod, saved and loaded.
 * <p>A draft runs twenty minutes or more and a server restart in the middle of one must not
 * eat it - or, worse, hand it back subtly wrong, with somebody holding a pack that is not
 * theirs. So every field gets a round trip, at every point in a pod's life.
 */
class DraftPodCodecTest {

    /** A pod nobody has picked from yet comes back untouched. */
    @Test
    void aFreshPodComesBackTheSame() throws IOException {
        DraftPod pod = pod(6, 9);

        assertThat(DraftPodCodec.read(DraftPodCodec.write(pod))).isEqualTo(pod);
    }

    /**
     * And one caught mid-turn, with some drafters declared and some not.
     * <p>The moment a restart is most likely to be noticed and the state hardest to rebuild:
     * half the pod has said what they are taking and the packs have not moved.
     */
    @Test
    void aPodCaughtHalfwayThroughATurnComesBackHalfwayThroughIt() throws IOException {
        DraftPod pod = someWayIn(pod(6, 9), 2);
        pod = pod.declare(pod.drafterAt(DrafterId.of(0)).orElseThrow().id(), null, List.of(1));
        pod = pod.declare(pod.drafterAt(DrafterId.of(3)).orElseThrow().id(), null, List.of(0));

        DraftPod restored = DraftPodCodec.read(DraftPodCodec.write(pod));

        assertThat(restored).isEqualTo(pod);
        assertThat(restored.state().hasDeclared(DrafterId.of(0))).isTrue();
        assertThat(restored.state().hasDeclared(DrafterId.of(1))).isFalse();
        assertThat(restored.state().stillToPick())
                .containsExactly(DrafterId.of(1), DrafterId.of(2), DrafterId.of(4), DrafterId.of(5));
    }

    /** And a finished one, holding nothing but its pools. */
    @Test
    void aFinishedPodComesBackFinished() throws IOException {
        DraftPod pod = draftToTheEnd(pod(4, 4));

        DraftPod restored = DraftPodCodec.read(DraftPodCodec.write(pod));

        assertThat(restored).isEqualTo(pod);
        assertThat(restored.isFinished()).isTrue();
        assertThat(restored.state().poolOf(DrafterId.of(0))).isNotEmpty();
    }

    /** A restored pod carries on drafting exactly where it left off. */
    @Test
    void aRestoredPodCarriesOnWhereItLeftOff() throws IOException {
        DraftPod pod = someWayIn(pod(4, 8), 1);

        DraftPod straightThrough = draftToTheEnd(pod);
        DraftPod throughARestart = draftToTheEnd(DraftPodCodec.read(DraftPodCodec.write(pod)));

        assertThat(throughARestart).isEqualTo(straightThrough);
    }

    /** Whether the sponsor keeps the cards is part of what is saved, not re-decided on load. */
    @Test
    void whoKeepsThePoolsSurvivesARestart() throws IOException {
        DraftPod sponsored = pod(4, 8, false);

        assertThat(DraftPodCodec.read(DraftPodCodec.write(sponsored)).poolsAreKept()).isFalse();
        assertThat(DraftPodCodec.read(DraftPodCodec.write(pod(4, 8))).poolsAreKept()).isTrue();
    }

    /** A version this does not read is refused rather than misread as something else. */
    @Test
    void anUnknownVersionIsRefused() {
        byte[] written = DraftPodCodec.write(pod(4, 4));
        written[3] = (byte) (written[3] + 1);

        assertThatThrownBy(() -> DraftPodCodec.read(written))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("version");
    }

    /**
     * And a pod that does not add up comes back as a refusal rather than a crash.
     * <p>A corrupt save is a real thing that happens to real servers. The caller has a
     * decision to make about it - tell the drafters, keep the world loading - and it can only
     * make that decision if this fails in a way it can catch.
     */
    @Test
    void aPodThatDoesNotAddUpIsRefusedRatherThanThrown() {
        byte[] written = DraftPodCodec.write(pod(4, 4));
        // The drafter count, immediately after the version and the keep-pools flag.
        written[5] = 3;

        assertThatThrownBy(() -> DraftPodCodec.read(written))
                .isInstanceOf(IOException.class);
    }

    /** And over arbitrary pods at arbitrary points in their lives. */
    @Property(tries = 200)
    void everyPodComesBackTheSame(@ForAll("pods") DraftPod pod) throws IOException {
        assertThat(DraftPodCodec.read(DraftPodCodec.write(pod))).isEqualTo(pod);
    }

    @Provide
    Arbitrary<DraftPod> pods() {
        return Arbitraries.integers().between(4, 8).flatMap(drafters ->
                Arbitraries.integers().between(1, 9).flatMap(packSize ->
                        Arbitraries.integers().between(0, 6).flatMap(turns ->
                                Arbitraries.of(true, false).map(kept ->
                                        someWayIn(pod(drafters, packSize, kept), turns)))));
    }

    // --- helpers ---

    private static DraftPod pod(int drafters, int packSize) {
        return pod(drafters, packSize, true);
    }

    private static DraftPod pod(int drafters, int packSize, boolean poolsAreKept) {
        List<PlayerRef> people = new ArrayList<>(drafters);
        for (int index = 0; index < drafters; index++) {
            String name = "Drafter " + index;
            people.add(new PlayerRef(UUID.nameUUIDFromBytes(
                    name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        }
        List<List<DraftPack>> rounds = new ArrayList<>(DraftRules.ROUNDS);
        for (int round = 0; round < DraftRules.ROUNDS; round++) {
            List<DraftPack> packs = new ArrayList<>(drafters);
            for (int drafter = 0; drafter < drafters; drafter++) {
                List<CardIdentity> cards = new ArrayList<>(packSize);
                for (int card = 0; card < packSize; card++) {
                    cards.add(CardIdentity.ofPrinting(UUID.nameUUIDFromBytes(
                            ("r" + round + "d" + drafter + "c" + card)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            card % 5 == 0));
                }
                packs.add(DraftPack.of(cards));
            }
            rounds.add(packs);
        }
        return DraftPod.opening(people, rounds, poolsAreKept);
    }

    private static DraftPod someWayIn(DraftPod pod, int turns) {
        DraftPod after = pod;
        for (int turn = 0; turn < turns && !after.isFinished(); turn++) {
            after = oneTurn(after);
        }
        return after;
    }

    private static DraftPod draftToTheEnd(DraftPod pod) {
        DraftPod after = pod;
        while (!after.isFinished()) {
            after = oneTurn(after);
        }
        return after;
    }

    private static DraftPod oneTurn(DraftPod pod) {
        DraftPod after = pod;
        for (int index = 0; index < pod.drafters().size(); index++) {
            DrafterId place = DrafterId.of(index);
            int due = after.state().picksDueFrom(place);
            if (due == 0 || after.state().hasDeclared(place)) {
                continue;
            }
            List<Integer> front = new ArrayList<>(due);
            for (int position = 0; position < due; position++) {
                front.add(position);
            }
            after = after.declare(after.drafterAt(place).orElseThrow().id(), place, front);
        }
        return after;
    }
}
