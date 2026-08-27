package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * A drafter learns nothing about anybody else's cards.
 *
 * <p>The same property the table holds for hands, and it matters here for the same reason:
 * a draft where you can see what the pack is about to hand you is not a draft. The check is
 * blunt on purpose - take everything a view carries, gather every card identity in it, and
 * insist none of them is a card another drafter is holding or has taken.
 */
class DraftPrivacyTest {

    /** No view names a card in another drafter's pack. */
    @Test
    void aRivalsPackIsNeverNamedInSomebodyElsesView() {
        DraftState pod = pod(6, 9);

        for (int index = 0; index < pod.drafters(); index++) {
            DrafterId me = DrafterId.of(index);
            Set<CardIdentity> named = everythingNamedIn(DraftVisibility.viewFor(pod, me));
            for (int other = 0; other < pod.drafters(); other++) {
                if (other == index) {
                    continue;
                }
                assertThat(named)
                        .describedAs("%s can name a card in %s's pack", me, DrafterId.of(other))
                        .doesNotContainAnyElementsOf(pod.packHeldBy(DrafterId.of(other)).cards());
            }
        }
    }

    /** Nor a card another drafter has already taken. */
    @Test
    void aRivalsPoolIsNeverNamedInSomebodyElsesView() {
        DraftState pod = someWayIn(pod(6, 9), 4);

        for (int index = 0; index < pod.drafters(); index++) {
            DrafterId me = DrafterId.of(index);
            Set<CardIdentity> named = everythingNamedIn(DraftVisibility.viewFor(pod, me));
            for (int other = 0; other < pod.drafters(); other++) {
                if (other == index) {
                    continue;
                }
                List<CardIdentity> theirs = pod.poolOf(DrafterId.of(other));
                assertThat(theirs).describedAs("the check needs somebody to have picked")
                        .isNotEmpty();
                assertThat(named)
                        .describedAs("%s can name a card %s took", me, DrafterId.of(other))
                        .doesNotContainAnyElementsOf(theirs);
            }
        }
    }

    /** What a drafter is entitled to, they get: their own pack and their own pool, in full. */
    @Test
    void aDrafterSeesTheirOwnPackAndPool() {
        DraftState pod = someWayIn(pod(6, 9), 3);
        DrafterId me = DrafterId.of(2);

        DraftView seen = DraftVisibility.viewFor(pod, me);

        assertThat(seen.myPack()).isEqualTo(pod.packHeldBy(me));
        assertThat(seen.myPool()).isEqualTo(pod.poolOf(me));
        assertThat(seen.others().get(DrafterId.of(3)))
                .describedAs("and a count of what everybody else has, which is public")
                .isEqualTo(pod.poolOf(DrafterId.of(3)).size());
    }

    /**
     * A declared pick does not leak either, before the packs move.
     *
     * <p>The window that would be easiest to get wrong: between one drafter saying what they
     * are taking and the last one saying it, the pod knows something nobody else may know.
     */
    @Test
    void aPickSaidOutLoudIsStillNobodyElsesBusiness() {
        DraftState pod = pod(6, 9);
        DraftPack mine = pod.packHeldBy(DrafterId.of(0));
        DraftState waiting = pod.declare(DrafterId.of(0), List.of(3));

        DraftView neighbor = DraftVisibility.viewFor(waiting, DrafterId.of(1));

        assertThat(waiting.isFinished()).isFalse();
        assertThat(everythingNamedIn(neighbor))
                .describedAs("a neighbor can see which card was taken")
                .doesNotContainAnyElementsOf(mine.cards());
        assertThat(neighbor.waitingOn())
                .describedAs("only that the pod is no longer waiting on them")
                .doesNotContain(DrafterId.of(0));
    }

    /**
     * And over thousands of pods nobody wrote down.
     *
     * <p>The examples above cover the moments somebody thought of. This plays arbitrary pods
     * of arbitrary sizes with arbitrary picks and checks the property after every single
     * declaration, which is where an interesting sequence would hide.
     */
    @Property(tries = 300)
    void noDrafterEverHoldsSomebodyElsesCard(@ForAll("pods") DraftState opening,
            @ForAll long picking) {
        java.util.Random choices = new java.util.Random(picking);
        DraftState pod = opening;
        while (!pod.isFinished()) {
            for (int index = 0; index < pod.drafters(); index++) {
                DrafterId drafter = DrafterId.of(index);
                int due = pod.picksDueFrom(drafter);
                if (due == 0 || pod.hasDeclared(drafter)) {
                    continue;
                }
                List<Integer> taking = new ArrayList<>(due);
                List<Integer> room = new ArrayList<>();
                for (int position = 0; position < pod.packHeldBy(drafter).size(); position++) {
                    room.add(position);
                }
                java.util.Collections.shuffle(room, choices);
                taking.addAll(room.subList(0, due));
                pod = pod.declare(drafter, taking);
                nobodySeesAnybodyElsesCards(pod);
            }
        }
    }

    @Provide
    Arbitrary<DraftState> pods() {
        return Arbitraries.integers().between(4, 8).flatMap(drafters ->
                Arbitraries.integers().between(1, 9).map(packSize ->
                        pod(drafters, packSize)));
    }

    private static void nobodySeesAnybodyElsesCards(DraftState pod) {
        for (int index = 0; index < pod.drafters(); index++) {
            DrafterId me = DrafterId.of(index);
            Set<CardIdentity> named = everythingNamedIn(DraftVisibility.viewFor(pod, me));
            for (int other = 0; other < pod.drafters(); other++) {
                if (other == index) {
                    continue;
                }
                DrafterId them = DrafterId.of(other);
                Set<CardIdentity> theirs = new LinkedHashSet<>(pod.packHeldBy(them).cards());
                theirs.addAll(pod.poolOf(them));
                // Only what is theirs alone: two drafters can hold the same printing, and a
                // pod is built from distinct printings here precisely so this stays exact.
                theirs.removeAll(pod.packHeldBy(me).cards());
                theirs.removeAll(pod.poolOf(me));
                assertThat(named)
                        .describedAs("%s can name a card belonging to %s", me, them)
                        .doesNotContainAnyElementsOf(theirs);
            }
        }
    }

    /** Everything a view carries that could possibly be a card. */
    private static Set<CardIdentity> everythingNamedIn(DraftView view) {
        Set<CardIdentity> named = new LinkedHashSet<>(view.myPack().cards());
        named.addAll(view.myPool());
        return named;
    }

    private static DraftState pod(int drafters, int packSize) {
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
        return DraftState.opening(drafters, rounds);
    }

    /** A few turns in, so pools are not empty and the packs have moved. */
    private static DraftState someWayIn(DraftState pod, int turns) {
        DraftState after = pod;
        for (int turn = 0; turn < turns && !after.isFinished(); turn++) {
            for (int index = 0; index < after.drafters(); index++) {
                DrafterId drafter = DrafterId.of(index);
                int due = after.picksDueFrom(drafter);
                List<Integer> front = new ArrayList<>(due);
                for (int position = 0; position < due; position++) {
                    front.add(position);
                }
                if (due > 0) {
                    after = after.declare(drafter, front);
                }
            }
        }
        return after;
    }
}
