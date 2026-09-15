package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The how-to-play screen at every size a player can pick and every GUI scale. */
class GuideLayoutTest {

    private static final int FONT_LINE = 9;
    private static final int TOPICS = 7;

    @Test
    @DisplayName("everything inside the panel and the window, nothing on top of anything else")
    void fitsEverywhere() {
        int[][] windows = {{1708, 960}, {854, 480}, {569, 320}, {427, 240}};
        for (int[] window : windows) {
            for (float text : new float[] {0.75f, 1f, 1.5f, 2f}) {
                for (int controls : new int[] {75, 100, 150, 200}) {
                    GuideLayout layout = GuideLayout.of(window[0], window[1], controls, text, FONT_LINE,
                            TOPICS, Math.round(96 * text), Math.round(24 * text));
                    String at = window[0] + "x" + window[1] + " text " + text + " controls " + controls;
                    List<Rect> all = new ArrayList<>(layout.topics());
                    if (layout.oneAtATime()) {
                        all.add(layout.previous());
                        all.add(layout.current());
                        all.add(layout.next());
                    }
                    all.add(layout.done());
                    all.add(layout.page());
                    for (Rect rect : all) {
                        assertThat(rect.x()).as(at).isGreaterThanOrEqualTo(layout.panel().x());
                        assertThat(rect.right()).as(at).isLessThanOrEqualTo(layout.panel().right());
                        assertThat(rect.y()).as(at).isGreaterThanOrEqualTo(layout.panel().y());
                        assertThat(rect.bottom()).as(at).isLessThanOrEqualTo(layout.panel().bottom());
                    }
                    assertThat(layout.panel().right()).as(at).isLessThanOrEqualTo(window[0]);
                    assertThat(layout.panel().bottom()).as(at).isLessThanOrEqualTo(window[1]);
                    for (int one = 0; one < all.size(); one++) {
                        for (int two = one + 1; two < all.size(); two++) {
                            assertThat(overlap(all.get(one), all.get(two)))
                                    .as(at + ": " + all.get(one) + " and " + all.get(two)).isFalse();
                        }
                    }
                    // Room for at least two rows of the page, and topics never shorter than their text.
                    assertThat(layout.page().height()).as(at).isGreaterThanOrEqualTo(layout.row() * 2);
                    assertThat(layout.row()).as(at).isGreaterThan(Math.round(FONT_LINE * Math.max(1f, text)));
                }
            }
        }
    }

    @Test
    @DisplayName("a roomy window keeps the topics in a column beside the page; a small one puts them above it")
    void topicsMoveAboveWhenNarrow() {
        GuideLayout roomy = GuideLayout.of(854, 480, 100, 1f, FONT_LINE, TOPICS, 96, 24);
        assertThat(roomy.topicsAcross()).isFalse();
        assertThat(roomy.page().x()).isGreaterThan(roomy.topics().get(0).right());

        GuideLayout narrow = GuideLayout.of(569, 480, 100, 1f, FONT_LINE, TOPICS, 300, 24);
        assertThat(narrow.topicsAcross()).isTrue();
        assertThat(narrow.oneAtATime()).isFalse();
        assertThat(narrow.page().y()).isGreaterThan(narrow.topics().get(TOPICS - 1).bottom());

        GuideLayout tiny = GuideLayout.of(427, 240, 200, 2f, FONT_LINE, TOPICS, 192, 48);
        assertThat(tiny.oneAtATime()).isTrue();
        assertThat(tiny.page().y()).isGreaterThan(tiny.current().bottom());
    }

    @Test
    @DisplayName("buttons grow with the control size")
    void controlsGrow() {
        assertThat(GuideLayout.of(1708, 960, 200, 1f, FONT_LINE, TOPICS, 96, 24).row())
                .isEqualTo(2 * GuideLayout.of(1708, 960, 100, 1f, FONT_LINE, TOPICS, 96, 24).row());
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }
}
