package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The builder's list column, with a deck and a sideboard in it. */
class BuilderListTest {

    private static final BuilderList WINDOW = new BuilderList(11, 40, 150);

    @Test
    @DisplayName("a section costs a heading as well as its rows")
    void aHeadingIsALineToo() {
        assertThat(BuilderList.contentHeight(List.of(3), 11)).isEqualTo(44);
        // Two sections, so two headings.
        assertThat(BuilderList.contentHeight(List.of(3, 2), 11)).isEqualTo(77);
    }

    @Test
    @DisplayName("an empty section takes no room, heading included")
    void emptySectionsAreNotLaidOut() {
        assertThat(BuilderList.contentHeight(List.of(0, 2), 11))
                .isEqualTo(BuilderList.contentHeight(List.of(2), 11));
        assertThat(WINDOW.lines(List.of(0, 2), 0)).allMatch(line -> line.section() == 1);
    }

    @Test
    @DisplayName("the sideboard's lines follow the deck's, one row apart")
    void twoSectionsRunOnFromEachOther() {
        List<BuilderList.Line> lines = WINDOW.lines(List.of(2, 1), 0);

        assertThat(lines).hasSize(5);
        assertThat(lines.get(0).isHeading()).isTrue();
        assertThat(lines.get(0).y()).isEqualTo(40);
        assertThat(lines.get(1).y()).isEqualTo(51);
        assertThat(lines.get(2).y()).isEqualTo(62);
        // The second section's heading, then its one row.
        assertThat(lines.get(3).section()).isEqualTo(1);
        assertThat(lines.get(3).isHeading()).isTrue();
        assertThat(lines.get(3).y()).isEqualTo(73);
        assertThat(lines.get(4).row()).isZero();
        assertThat(lines.get(4).y()).isEqualTo(84);
    }

    @Test
    @DisplayName("scrolling moves every line by the same amount")
    void scrollingMovesTheWholeColumn() {
        List<BuilderList.Line> still = WINDOW.lines(List.of(4, 4), 0);
        List<BuilderList.Line> moved = WINDOW.lines(List.of(4, 4), 22);

        for (int at = 0; at < still.size(); at++) {
            assertThat(moved.get(at).y()).isEqualTo(still.get(at).y() - 22);
        }
    }

    @Test
    @DisplayName("the last row of the sideboard can be scrolled to and no further")
    void theLastLineIsReachable() {
        // Ten lines of eleven is a hundred and ten, in a window of a hundred and ten.
        List<Integer> sections = List.of(4, 4);
        int deepest = WINDOW.deepestScroll(sections);

        assertThat(deepest).isZero();

        // One more row, and the list is one row taller than its window.
        List<Integer> longer = List.of(5, 4);
        assertThat(WINDOW.deepestScroll(longer)).isEqualTo(11);
        List<BuilderList.Line> lines = WINDOW.lines(longer, WINDOW.deepestScroll(longer));
        assertThat(WINDOW.shows(lines.get(lines.size() - 1).y())).isTrue();
    }

    @Test
    @DisplayName("a list that fits cannot be scrolled at all")
    void aShortListDoesNotScroll() {
        assertThat(WINDOW.deepestScroll(List.of(1))).isZero();
        assertThat(WINDOW.scrollWithin(80, List.of(1))).isZero();
        assertThat(WINDOW.scrollWithin(-40, List.of(9, 9))).isZero();
    }

    @Test
    @DisplayName("a line half out of the window is not drawn")
    void onlyWholeLinesShow() {
        assertThat(WINDOW.shows(40)).isTrue();
        assertThat(WINDOW.shows(39)).isFalse();
        // The last line that fits ends exactly on the foot of the window.
        assertThat(WINDOW.shows(139)).isTrue();
        assertThat(WINDOW.shows(140)).isFalse();
    }

    @Test
    @DisplayName("a window with no height shows nothing and scrolls nowhere")
    void aCollapsedWindowIsSafe() {
        BuilderList none = new BuilderList(11, 60, 60);

        assertThat(none.height()).isZero();
        assertThat(none.shows(60)).isFalse();
        assertThat(none.deepestScroll(List.of(3))).isEqualTo(44);
        assertThat(none.lines(null, 0)).isEmpty();
    }
}
