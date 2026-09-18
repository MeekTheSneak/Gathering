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
    @DisplayName("thins nothing at all for an hour of ordinary play")
    void anhourOfPlayingNeverMeetsIt() {
        // What LootYieldTest says an hour of exploring actually hands over. A player having a very
        // good hour, or a first evening on a server, must never notice this exists.
        assertThat(FindingPace.PACKS.oneIn(8)).isEqualTo(1);
        assertThat(FindingPace.COINS.oneIn(7)).isEqualTo(1);
        // And a whole end city raid's worth of archive luck in one go.
        assertThat(FindingPace.ARCHIVE.oneIn(FindingPace.ARCHIVE.burst())).isEqualTo(1);
    }

    @Test
    @DisplayName("thins further the further past the burst somebody is")
    void afarmMeetsLongerAndLongerOdds() {
        assertThat(FindingPace.PACKS.oneIn(FindingPace.PACKS.burst() + 1)).isEqualTo(4);
        assertThat(FindingPace.PACKS.oneIn(FindingPace.PACKS.burst() + 4)).isEqualTo(256);
        assertThat(FindingPace.PACKS.oneIn(FindingPace.PACKS.burst() + 8)).isEqualTo(65_536);
        // And it stops there rather than running off the end of an int.
        assertThat(FindingPace.PACKS.oneIn(FindingPace.PACKS.burst() + 1000)).isEqualTo(1 << 20);
    }

    @Test
    @DisplayName("drains at its own pace, so time away is what gives it back")
    void thebucketDrainsOverTime() {
        assertThat(FindingPace.PACKS.drainedIn(3_600_000L)).isEqualTo(FindingPace.PACKS.anHour());
        assertThat(FindingPace.ARCHIVE.drainedIn(2 * 3_600_000L)).isEqualTo(1.0);
        assertThat(FindingPace.PACKS.drained(20, 3_600_000L)).isEqualTo(8.0);
        // Never below empty: an afternoon away does not bank a fortnight of finds.
        assertThat(FindingPace.PACKS.drained(3, 100 * 3_600_000L)).isEqualTo(0.0);
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
        assertThat(FindingPace.ARCHIVE.anHour()).isLessThan(FindingPace.PACKS.anHour());
        // And tighter than exploring actually pays: two chests worth a trip an hour, at one archive
        // pack in thirty, is one about every fifteen hours. A machine settles well above a player
        // and nowhere near what its own odds would give it.
        assertThat(FindingPace.ARCHIVE.anHour()).isLessThan(1.0);
        assertThat(FindingPace.ARCHIVE.burst()).isLessThan(FindingPace.PACKS.burst());
    }
}
