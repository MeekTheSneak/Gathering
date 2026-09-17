package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wrapper as a sheet that tears where it is pulled.
 * <p>A simulation is the kind of thing that is usually checked by looking at it, which is why most of them
 * are wrong in ways nobody notices. The things that matter here can all be asked without a window: that it
 * stays still when nothing touches it, that it comes apart when it is pulled and only then, that the tear
 * ends up where the hand was rather than somewhere a line was drawn, and - the one everything else rests
 * on - that the same pack pulled the same way tears the same way twice.
 */
class PackClothTest {

    /** Runs a second of cloth, the way a screen at sixty frames a second would. */
    private static void secondsOf(PackCloth cloth, float seconds) {
        for (int frame = 0; frame < Math.round(seconds * 60); frame++) {
            cloth.advance(1f / 60f);
        }
    }

    @Test
    @DisplayName("a wrapper nobody touches stays whole")
    void nothingHappensOnItsOwn() {
        PackCloth cloth = new PackCloth(1234L);
        int before = cloth.linksLeft();

        secondsOf(cloth, 5f);

        assertThat(cloth.linksLeft()).isEqualTo(before);
        assertThat(cloth.torn()).isZero();
        assertThat(cloth.isOpen()).isFalse();
        assertThat(cloth.isUntouched()).isTrue();
    }

    @Test
    @DisplayName("pulling the crimp far enough tears it off, and the pack is open")
    void pullingItOpensIt() {
        PackCloth cloth = new PackCloth(99L);
        // Take hold of the middle of the strip and drag it away, the way a hand does.
        assertThat(cloth.grab(0.5f, 0.05f)).isTrue();
        for (int frame = 0; frame < 240 && !cloth.isOpen(); frame++) {
            float along = frame / 240f;
            cloth.dragTo(0.5f + along * 2.5f, 0.05f - along * 1.2f);
            cloth.advance(1f / 60f);
        }

        assertThat(cloth.isOpen()).isTrue();
        // Most of the seam, not a link or two of it: "open" has to mean the strip has actually come away.
        assertThat(cloth.torn()).isGreaterThan(0.5f);
        assertThat(cloth.isUntouched()).isFalse();
    }

    @Test
    @DisplayName("a hand that never pulls hard never tears anything")
    void agentleHandTearsNothing() {
        PackCloth cloth = new PackCloth(7L);
        cloth.grab(0.5f, 0.05f);
        int before = cloth.linksLeft();

        // Waggled about within its own reach: nowhere near the stretch a link gives way at.
        for (int frame = 0; frame < 300; frame++) {
            cloth.dragTo(0.5f + (float) Math.sin(frame / 12.0) * 0.03f, 0.05f);
            cloth.advance(1f / 60f);
        }

        assertThat(cloth.linksLeft()).isEqualTo(before);
        assertThat(cloth.isOpen()).isFalse();
    }

    @Test
    @DisplayName("a short pull at the crimp moves the foil and does not open the pack")
    void aShortPullDoesNotOpenIt() {
        // The scripted run's gentle pull: from the middle of the crimp, a seventh of the pack's width across
        // and a fourteenth of its height up, over forty frames. When the seam's diagonals were weakened from
        // the start, this tore the whole strip off.
        for (long seed : new long[] {1L, 42L, 1234L}) {
            PackCloth cloth = new PackCloth(seed);
            cloth.grab(0.5f, 1f / 12f, 1.7f);
            for (int step = 1; step <= 40; step++) {
                float along = 0.07f * step / 40f;
                cloth.dragTo(0.5f + along * 2.2f, 1f / 12f - along);
                cloth.advance(1f / 60f);
            }
            assertThat(cloth.isOpen()).as("seed %s, torn %s", seed, cloth.torn()).isFalse();
        }
    }

    /**
     * The one everything else rests on. Two people watching one screen see one wrapper, and a picture of a
     * torn pack is only worth taking if the same pull gives the same tear.
     */
    @Property
    @Label("the same pack pulled the same way tears the same way")
    void theSamePullGivesTheSameTear(@ForAll @LongRange(min = -9999, max = 9999) long seed) {
        PackCloth one = torn(seed);
        PackCloth two = torn(seed);

        assertThat(one.linksLeft()).isEqualTo(two.linksLeft());
        assertThat(one.torn()).isEqualTo(two.torn());
        for (int at = 0; at < PackCloth.ACROSS * PackCloth.DOWN; at++) {
            assertThat(one.xOf(at)).isEqualTo(two.xOf(at));
            assertThat(one.yOf(at)).isEqualTo(two.yOf(at));
        }
    }

    /** And two different packs do not tear identically, or the seed is doing nothing. */
    @Test
    @DisplayName("two packs do not tear the same way")
    void twoPacksTearDifferently() {
        PackCloth one = torn(1L);
        PackCloth other = torn(2L);

        boolean same = true;
        for (int at = 0; at < PackCloth.ACROSS * PackCloth.DOWN && same; at++) {
            same = one.xOf(at) == other.xOf(at) && one.yOf(at) == other.yOf(at);
        }
        assertThat(same).isFalse();
    }

    @Property
    @Label("however long a frame took, the sheet never runs away")
    void alongFrameDoesNotFlingItAway(@ForAll @LongRange(min = 0, max = 4000) long millis) {
        PackCloth cloth = new PackCloth(5L);
        // A window that stalled for four seconds hands the next frame four seconds. The sheet may not
        // answer that by running four seconds of gravity in one go and throwing itself off the screen.
        cloth.advance(millis / 1000f);
        for (int at = 0; at < PackCloth.ACROSS * PackCloth.DOWN; at++) {
            assertThat(cloth.yOf(at)).isBetween(-2f, 3f);
            assertThat(cloth.xOf(at)).isBetween(-2f, 3f);
        }
    }

    @Test
    @DisplayName("a square with a side torn off it is no longer drawn")
    void tornSquaresStopBeingDrawn() {
        PackCloth whole = new PackCloth(3L);
        int before = 0;
        for (int down = 0; down + 1 < PackCloth.DOWN; down++) {
            for (int across = 0; across + 1 < PackCloth.ACROSS; across++) {
                if (whole.stillThere(across, down)) {
                    before++;
                }
            }
        }
        assertThat(before).isEqualTo((PackCloth.ACROSS - 1) * (PackCloth.DOWN - 1));

        PackCloth pulled = torn(3L);
        int after = 0;
        for (int down = 0; down + 1 < PackCloth.DOWN; down++) {
            for (int across = 0; across + 1 < PackCloth.ACROSS; across++) {
                if (pulled.stillThere(across, down)) {
                    after++;
                }
            }
        }
        assertThat(after).isLessThan(before);
    }

    /**
     * The tear takes the crimp and stops, leaving every row of the printed pack where it was.
     * <p>A square of the sheet carries the row of the picture above its lower edge, so tearing along
     * the links below the crimp's last row destroyed the pack's <em>first</em> row instead of the
     * crimp's last one. The strip still came away whole and the pack underneath was a pixel short at
     * the top - which is the tear reading as one pixel too low, which is what the owner saw.
     * <p>Asked of the whole width, because a tear that is right in the middle and a row low at one
     * end is the same fault wearing a disguise.
     */
    @Test
    @DisplayName("the tear takes the crimp and none of the pack")
    void thetearStopsAtTheCrimp() {
        // Where the printing stops being crimp and starts being pack, in rows of this sheet.
        int firstBodyRow = PackWrapper.CRIMP_ROWS * (PackCloth.DOWN - 1) / PackWrapper.PIXELS;

        PackCloth pulled = torn(11L);
        assertThat(pulled.isOpen()).isTrue();
        for (int down = firstBodyRow; down + 1 < PackCloth.DOWN; down++) {
            for (int across = 0; across + 1 < PackCloth.ACROSS; across++) {
                assertThat(pulled.stillThere(across, down))
                        .withFailMessage("the tear took row %d of the pack's own artwork, at %d across",
                                down, across)
                        .isTrue();
            }
        }
        // And it did tear: the crimp's own last row is the one that goes.
        boolean someCrimpGone = false;
        for (int across = 0; across + 1 < PackCloth.ACROSS; across++) {
            someCrimpGone |= !pulled.stillThere(across, firstBodyRow - 1);
        }
        assertThat(someCrimpGone)
                .withFailMessage("the pack opened and no row of the crimp came away")
                .isTrue();
    }

    /** A wrapper taken hold of in the middle of the crimp and dragged off. */
    private static PackCloth torn(long seed) {
        PackCloth cloth = new PackCloth(seed);
        cloth.grab(0.5f, 0.05f);
        for (int frame = 0; frame < 240; frame++) {
            float along = frame / 240f;
            cloth.dragTo(0.5f + along * 2.5f, 0.05f - along * 1.2f);
            cloth.advance(1f / 60f);
        }
        cloth.letGo();
        secondsOf(cloth, 1f);
        return cloth;
    }
}
