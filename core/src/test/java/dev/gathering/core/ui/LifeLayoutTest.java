package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LifeLayoutTest {

    /** The smallest window Minecraft will give you, in GUI-scaled units. */
    private static final int NARROWEST = 320;
    private static final int SHORTEST = 240;

    @Test
    @DisplayName("a table with no commanders is a life total and nothing else")
    void justLife() {
        LifeLayout layout = LifeLayout.of(640, 480, 0);

        assertThat(layout.damageRows()).isZero();
        assertThat(layout.damage()).isEqualTo(Rect.NONE);
        assertThat(layout.damageRow(0)).isEqualTo(Rect.NONE);
        assertThat(layout.life().bottom()).isLessThanOrEqualTo(layout.done().y());
    }

    @Test
    @DisplayName("a roomy window shows every commander")
    void roomy() {
        LifeLayout layout = LifeLayout.of(640, 480, 6);

        assertThat(layout.damageRows()).isEqualTo(6);
        assertThat(layout.damageRow(5).bottom()).isLessThanOrEqualTo(layout.done().y());
    }

    @Test
    @DisplayName("a crowded Commander table on a small window still has its way out")
    void crowdedAndSmall() {
        // Three opponents fielding partners: six commanders to record damage from, which is
        // more than the panel has room for at this size. Laying them all out anyway is what
        // pushes the Done button off the bottom of the screen, which is the defect the panel
        // this one is modeled on was built around.
        LifeLayout layout = LifeLayout.of(NARROWEST, SHORTEST, 6);

        assertThat(layout.done().bottom()).isLessThanOrEqualTo(SHORTEST);
        assertThat(layout.damageRow(layout.damageRows() - 1).bottom())
                .isLessThanOrEqualTo(layout.done().y());
        assertThat(layout.damageRows()).isGreaterThanOrEqualTo(1);
    }

    @Property
    @Label("the life total is always on the panel, whatever else had to go")
    void lifeNeverGivesWay(
            @ForAll @IntRange(min = SHORTEST, max = 720) int height,
            @ForAll @IntRange(min = 0, max = 10) int commanders) {
        LifeLayout layout = LifeLayout.of(NARROWEST, height, commanders);

        assertThat(layout.life().isEmpty()).isFalse();
        assertThat(layout.life().bottom()).isLessThanOrEqualTo(layout.done().y());
        // And a table with commanders keeps a way to record their damage, however short the
        // window: one row and a heading that says how many are missing.
        if (commanders > 0) {
            assertThat(layout.damageRows()).isGreaterThanOrEqualTo(1);
        }
    }

    @Property
    @Label("nothing is drawn past the bottom of the panel")
    void everythingFitsInside(
            @ForAll @IntRange(min = SHORTEST, max = 720) int height,
            @ForAll @IntRange(min = 0, max = 10) int commanders) {
        LifeLayout layout = LifeLayout.of(NARROWEST, height, commanders);

        assertThat(layout.done().bottom()).isLessThanOrEqualTo(layout.panel().bottom());
        for (int row = 0; row < layout.damageRows(); row++) {
            assertThat(layout.damageRow(row).bottom()).isLessThanOrEqualTo(layout.done().y());
        }
    }

    @Property
    @Label("commander rows never overlap each other or the life total")
    void rowsStandApart(@ForAll @IntRange(min = 1, max = 8) int commanders) {
        LifeLayout layout = LifeLayout.of(640, 480, commanders);

        assertThat(layout.damageRow(0).overlaps(layout.life())).isFalse();
        for (int row = 1; row < layout.damageRows(); row++) {
            assertThat(layout.damageRow(row).overlaps(layout.damageRow(row - 1))).isFalse();
        }
    }
}
