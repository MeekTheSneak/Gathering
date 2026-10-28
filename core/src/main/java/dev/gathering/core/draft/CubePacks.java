package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.DeterministicRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Deals a pod's packs out of a cube.
 * <p>The draft path that needs nothing else in the mod. A cube is a decklist somebody
 * imported, so this works on a server with no collection, no economy and no booster data at
 * all - which is why the design brief puts it first: a group can have a draft night before
 * any of the rest of phase three exists.
 * <p>Dealt from a shuffle of the whole cube rather than pack by pack, because a cube is one
 * pile of cards being cut into packs, and cutting it any other way would let the same
 * printing land in two packs of the same round. The shuffle is the same seeded stream the
 * table shuffles libraries with, so the same cube and the same seed always cut the same
 * packs - which is what makes the dealing checkable rather than something to be believed.
 * <p>A pod running at a table is kept whole rather than replayed from its seed. Replaying
 * would need a second implementation of the passing rules, and a bug in that second
 * implementation would hand somebody else's cards back after a restart.
 */
public final class CubePacks {

    /** What a pack is, when nothing says otherwise. The size paper cube draft settled on. */
    public static final int USUAL_PACK = 15;

    /** Below this a pack is not worth passing; a pod this thin should draft a bigger cube. */
    public static final int SMALLEST_PACK = 4;

    private CubePacks() {
    }

    /**
     * The largest pack this cube can fill for this pod, up to the usual fifteen.
     * <p>A cube that cannot fill fifteen-card packs is far more common than one that can -
     * eight drafters want three hundred and sixty cards - and refusing those outright would
     * turn "draft the group's cube" into "go and build a bigger cube first". So the packs
     * get thinner instead, down to a floor where a pack stops being one.
     */
    public static int packSizeFor(int cubeSize, int drafters) {
        if (!DraftRules.isAPodSize(drafters)) {
            throw new IllegalArgumentException("Not a pod size: " + drafters);
        }
        return Math.min(USUAL_PACK, cubeSize / (drafters * DraftRules.ROUNDS));
    }

    /** Whether this cube can seat this pod at all. */
    public static boolean isBigEnough(int cubeSize, int drafters) {
        return DraftRules.isAPodSize(drafters)
                && packSizeFor(cubeSize, drafters) >= SMALLEST_PACK;
    }

    /** The smallest cube that would seat this pod, for the message that says why not. */
    public static int smallestCubeFor(int drafters) {
        return SMALLEST_PACK * drafters * DraftRules.ROUNDS;
    }

    /**
     * Cuts a shuffled cube into one pack per drafter per round.
     *
     * @param cube    every card in the cube, one entry per physical card
     * @param seed    the pod's own shuffle seed, fresh per pod; never logged, never sent
     * @throws IllegalArgumentException if the cube cannot fill packs worth passing
     */
    public static List<List<DraftPack>> deal(List<CardIdentity> cube, int drafters, byte[] seed) {
        if (cube == null || !isBigEnough(cube.size(), drafters)) {
            int had = cube == null ? 0 : cube.size();
            throw new IllegalArgumentException(
                    "A pod of " + drafters + " needs at least " + smallestCubeFor(drafters)
                            + " cards in the cube, not " + had);
        }
        int packSize = packSizeFor(cube.size(), drafters);
        // One shuffle for the whole cube, then cut. Shuffling per pack would draw each pack
        // from the whole cube independently, which is how a cube draft comes to open with
        // the same card in two packs.
        List<CardIdentity> shuffled =
                DeterministicRandom.forLabel(seed, "draft-cube").shuffled(cube);

        List<List<DraftPack>> rounds = new ArrayList<>(DraftRules.ROUNDS);
        int from = 0;
        for (int round = 0; round < DraftRules.ROUNDS; round++) {
            List<DraftPack> packs = new ArrayList<>(drafters);
            for (int drafter = 0; drafter < drafters; drafter++) {
                packs.add(DraftPack.of(shuffled.subList(from, from + packSize)));
                from += packSize;
            }
            rounds.add(List.copyOf(packs));
        }
        return List.copyOf(rounds);
    }
}
