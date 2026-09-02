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

/** Cutting a cube into packs, which is the whole of what a cube draft needs. */
class CubePacksTest {

    private static final byte[] SEED = "a cube draft".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /** A big cube gets the usual fifteen-card packs. */
    @Test
    void aBigCubeIsCutIntoTheUsualPacks() {
        assertThat(CubePacks.packSizeFor(540, 8)).isEqualTo(CubePacks.USUAL_PACK);
        assertThat(CubePacks.packSizeFor(360, 8)).isEqualTo(CubePacks.USUAL_PACK);
    }

    /**
     * A smaller one gets thinner packs rather than a refusal.
     * <p>Eight drafters want three hundred and sixty cards for full packs, which most groups'
     * cubes are not. Refusing would make the answer to "draft our cube" be "build a bigger
     * one", so the packs get thinner instead.
     */
    @Test
    void aSmallerCubeGetsThinnerPacksRatherThanARefusal() {
        assertThat(CubePacks.packSizeFor(240, 8)).isEqualTo(10);
        assertThat(CubePacks.packSizeFor(180, 4)).isEqualTo(15);
        assertThat(CubePacks.isBigEnough(240, 8)).isTrue();
    }

    /** Until the pack stops being worth passing, and then it says so with a number. */
    @Test
    void aCubeTooThinToMakePacksIsRefusedWithTheNumberItNeeds() {
        assertThat(CubePacks.isBigEnough(90, 8)).isFalse();
        assertThat(CubePacks.smallestCubeFor(8)).isEqualTo(96);

        assertThatThrownBy(() -> CubePacks.deal(cubeOf(90), 8, SEED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("96");
    }

    /** Every card dealt is a different card: no printing opens in two packs. */
    @Test
    void noCardIsDealtTwice() {
        List<CardIdentity> cube = cubeOf(360);

        List<List<DraftPack>> rounds = CubePacks.deal(cube, 8, SEED);

        List<CardIdentity> dealt = new ArrayList<>();
        for (List<DraftPack> round : rounds) {
            assertThat(round).hasSize(8);
            for (DraftPack pack : round) {
                assertThat(pack.size()).isEqualTo(15);
                dealt.addAll(pack.cards());
            }
        }
        Set<CardIdentity> distinct = new LinkedHashSet<>(dealt);
        assertThat(dealt).hasSize(360);
        assertThat(distinct).describedAs("a card opened in two packs").hasSize(360);
        assertThat(distinct).containsExactlyInAnyOrderElementsOf(cube);
    }

    /** The same cube and the same seed deal the same packs, which is what replay needs. */
    @Test
    void theSameSeedDealsTheSamePacks() {
        List<CardIdentity> cube = cubeOf(240);

        assertThat(CubePacks.deal(cube, 6, SEED)).isEqualTo(CubePacks.deal(cube, 6, SEED));
    }

    /** And a different one deals different packs, or the shuffle is not a shuffle. */
    @Test
    void aDifferentSeedDealsDifferentPacks() {
        List<CardIdentity> cube = cubeOf(240);
        byte[] other = "another cube draft".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(CubePacks.deal(cube, 6, SEED))
                .isNotEqualTo(CubePacks.deal(cube, 6, other));
    }

    /** And what it deals is a pod that plays to the end. */
    @Test
    void whatItDealsIsAPodThatDraftsToTheEnd() {
        // A hundred and eighty between six is ten a pack, three packs each.
        DraftState pod = DraftState.opening(6, CubePacks.deal(cubeOf(180), 6, SEED));

        while (!pod.isFinished()) {
            for (int index = 0; index < pod.drafters(); index++) {
                DrafterId drafter = DrafterId.of(index);
                int due = pod.picksDueFrom(drafter);
                if (due > 0) {
                    pod = pod.declare(drafter, List.of(0));
                }
            }
        }

        int drafted = 0;
        for (int index = 0; index < 6; index++) {
            drafted += pod.poolOf(DrafterId.of(index)).size();
        }
        assertThat(drafted).isEqualTo(6 * 3 * 10);
    }

    private static List<CardIdentity> cubeOf(int size) {
        List<CardIdentity> cube = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            cube.add(CardIdentity.ofPrinting(UUID.nameUUIDFromBytes(
                    ("cube-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        return cube;
    }
}
