package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a machine gets out of the world, against what a player does.
 * <p>Every rate the mod has is set against an hour of somebody playing. None of them is set against
 * a wither farm, a re-triggered shrieker or a weighted fishing rod, and at those rates any of them
 * is the rarest thing in the mod on tap. What separates a farm from a good afternoon is not what it
 * does but how long it keeps doing it, so that is what the pace measures.
 */
@DisplayName("The pace the world gives one player things at")
class FindingPaceTest {

    @Test
    @DisplayName("is only ever asked about things that can be left running")
    void chestsAreNeverThinned() {
        // The owner's line: no farms, and no limit on exploring. A chest has to be walked to and a
        // suspicious block has to be brushed, so neither is ever kept to a pace however many
        // somebody opens - a weekend of raiding end cities has earned every pack in them.
        assertThat(LootSource.STRUCTURES.canBeFarmed()).isFalse();
        assertThat(LootSource.DIGGING.canBeFarmed()).isFalse();
        assertThat(ArchiveDrops.EXPEDITION.canBeFarmed()).isFalse();

        // And the ones that can: a mob farm kills for you, a rod can be held down by a weight, and
        // three of the four bosses are repeatable.
        assertThat(LootSource.MOBS.canBeFarmed()).isTrue();
        assertThat(LootSource.FISHING.canBeFarmed()).isTrue();
        assertThat(ArchiveDrops.BOSS.canBeFarmed()).isTrue();
        assertThat(ArchiveDrops.TREASURE.canBeFarmed()).isTrue();
    }

    @Test
    @DisplayName("thins nothing at all for somebody actually fishing or actually caving")
    void anhourOfPlayingNeverMeetsIt() {
        // An hour of real fishing is a pack or two, and an hour of caving is about half of one.
        assertThat(FindingPace.FARMED_PACKS.oneIn(FindingPace.FARMED_PACKS.burst())).isEqualTo(1);
        assertThat(FindingPace.FARMED_ARCHIVE.oneIn(FindingPace.FARMED_ARCHIVE.burst())).isEqualTo(1);
    }

    @Test
    @DisplayName("thins further the further past the burst somebody is")
    void afarmMeetsLongerAndLongerOdds() {
        assertThat(FindingPace.FARMED_PACKS.oneIn(FindingPace.FARMED_PACKS.burst() + 1)).isEqualTo(4);
        assertThat(FindingPace.FARMED_PACKS.oneIn(FindingPace.FARMED_PACKS.burst() + 4)).isEqualTo(256);
        assertThat(FindingPace.FARMED_PACKS.oneIn(FindingPace.FARMED_PACKS.burst() + 8)).isEqualTo(65_536);
        // And it stops there rather than running off the end of an int.
        assertThat(FindingPace.FARMED_PACKS.oneIn(FindingPace.FARMED_PACKS.burst() + 1000)).isEqualTo(1 << 20);
    }

    @Test
    @DisplayName("drains at its own pace, so time away is what gives it back")
    void thebucketDrainsOverTime() {
        assertThat(FindingPace.FARMED_PACKS.drainedIn(3_600_000L)).isEqualTo(FindingPace.FARMED_PACKS.anHour());
        assertThat(FindingPace.FARMED_ARCHIVE.drainedIn(8 * 3_600_000L)).isEqualTo(1.0);
        assertThat(FindingPace.FARMED_PACKS.drained(20, 3_600_000L)).isEqualTo(18.0);
        // Never below empty: an afternoon away does not bank a fortnight of finds.
        assertThat(FindingPace.FARMED_PACKS.drained(3, 100 * 3_600_000L)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("settles a machine onto the pace, however long it runs")
    void amachineSettlesOntoThePace() {
        // A faucet rolling every second for eight hours, taking whatever the odds give it. What it
        // ends up with should be about what the pace allows and not what the faucet could produce.
        for (FindingPace kind : FindingPace.values()) {
            double had = 0;
            long everySecond = 1000L;
            int given = 0;
            int rolls = 8 * 60 * 60;
            for (int second = 0; second < rolls; second++) {
                had = kind.drained(had, everySecond);
                // The luckiest possible machine: it wins every roll it is offered.
                if (kind.oneIn(had) <= 1 || second % kind.oneIn(had) == 0) {
                    had += 1;
                    given++;
                }
            }
            // The pace for those eight hours, the brim it was allowed to start with, and the
            // climb - every step of which hands over one more find on the way from everything
            // arriving to almost nothing. Ten steps is the room the climb is allowed to cost.
            double allowed = kind.anHour() * 8 + kind.burst() + 10;
            assertThat((double) given)
                    .as("%s handed out in eight hours of a machine running flat out", kind)
                    .isLessThan(allowed);
        }
    }

    @Test
    @DisplayName("keeps the archive tightest, because it is the one thing nobody can buy")
    void thearchiveIsTheTightest() {
        assertThat(FindingPace.FARMED_ARCHIVE.anHour()).isLessThan(FindingPace.FARMED_PACKS.anHour());
        // Tighter than exploring pays, rather than merely tighter than the boss odds: two chests
        // worth a trip an hour, at one archive pack in thirty, is one about every fifteen hours.
        assertThat(FindingPace.FARMED_ARCHIVE.anHour()).isLessThan(0.2);
        // And a night of a machine running is worth about two hours of going out and looking.
        assertThat(FindingPace.FARMED_PACKS.anHour() * 8).isLessThan(20.0);
        assertThat(FindingPace.FARMED_ARCHIVE.burst()).isLessThan(FindingPace.FARMED_PACKS.burst());
    }
}
