package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListScreenLayoutTest {

    /** The smallest window Minecraft will give you, in GUI-scaled units. */
    private static final int NARROWEST = 320;
    private static final int SHORTEST = 240;

    /** The two lists that have this shape: set progress, and the cards missing from a set. */
    private static final int TALL_ROW = 26;
    private static final int SHORT_ROW = 12;

    @Test
    @DisplayName("the hint gives way to the count rather than being drawn under it")
    void theHintGivesWay() {
        // Laid out from halves of the screen these two overlapped below about 380 units wide,
        // which is every window at GUI scale 4. The hint is the half worth losing: the count
        // is the only thing that says the list goes on past the bottom of the window.
        ListScreenLayout wide = ListScreenLayout.of(1280, 720, TALL_ROW, 60);
        ListScreenLayout narrow = ListScreenLayout.of(NARROWEST, SHORTEST, TALL_ROW, 60);

        assertThat(wide.hint().overlaps(wide.more())).isFalse();
        assertThat(narrow.hint().overlaps(narrow.more())).isFalse();
        assertThat(narrow.hint().width()).isLessThan(wide.hint().width());
        assertThat(narrow.more().width()).isEqualTo(wide.more().width());
    }

    @Test
    @DisplayName("nothing is drawn where the count would be when nothing is hidden")
    void nothingHidden() {
        ListScreenLayout layout = ListScreenLayout.of(NARROWEST, SHORTEST, SHORT_ROW, 0);

        assertThat(layout.more()).isEqualTo(Rect.NONE);
        assertThat(layout.hint().right()).isLessThanOrEqualTo(layout.done().x());
    }

    @Property
    @Label("the footer never overlaps itself, at any window a player can have")
    void footerNeverOverlaps(
            @ForAll @IntRange(min = NARROWEST, max = 3840) int width,
            @ForAll @IntRange(min = SHORTEST, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 200) int moreWidth) {
        ListScreenLayout layout = ListScreenLayout.of(width, height, TALL_ROW, moreWidth);

        assertThat(layout.hint().overlaps(layout.more())).isFalse();
        assertThat(layout.hint().overlaps(layout.done())).isFalse();
        assertThat(layout.more().overlaps(layout.done())).isFalse();
    }

    @Property
    @Label("every piece of the footer is on the screen")
    void footerStaysOnScreen(
            @ForAll @IntRange(min = NARROWEST, max = 3840) int width,
            @ForAll @IntRange(min = SHORTEST, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 200) int moreWidth) {
        ListScreenLayout layout = ListScreenLayout.of(width, height, SHORT_ROW, moreWidth);

        for (Rect part : new Rect[] {layout.done(), layout.more(), layout.hint()}) {
            if (part.isEmpty()) {
                continue;
            }
            assertThat(part.x()).isGreaterThanOrEqualTo(0);
            assertThat(part.right()).isLessThanOrEqualTo(width);
            assertThat(part.bottom()).isLessThanOrEqualTo(height);
        }
    }

    @Property
    @Label("the rows fill the space between the heading and the footer, and no more")
    void rowsStayInTheirArea(
            @ForAll @IntRange(min = SHORTEST, max = 2160) int height,
            @ForAll @IntRange(min = 8, max = 40) int rowHeight) {
        ListScreenLayout layout = ListScreenLayout.of(NARROWEST, height, rowHeight, 40);

        assertThat(layout.rowsThatFit()).isGreaterThanOrEqualTo(1);
        assertThat(layout.rows().y()).isGreaterThanOrEqualTo(ListScreenLayout.topBar());
        Rect last = layout.rowAt(layout.rowsThatFit() - 1);
        assertThat(last.bottom()).isLessThanOrEqualTo(layout.rows().bottom());
        assertThat(layout.rowAt(layout.rowsThatFit())).isEqualTo(Rect.NONE);
        assertThat(layout.rowAt(-1)).isEqualTo(Rect.NONE);
    }
}
