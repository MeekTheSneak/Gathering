package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A tournament's screen at every text and control size a player can pick. */
class EventScreenLayoutTest {

    private static final int FONT_LINE = 9;
    private static final float[] TEXT = {0.75f, 1f, 1.5f, 2f};
    private static final int[] CONTROLS = {75, 100, 150, 200};

    @Test
    @DisplayName("buttons grow with the control size, as the settings screen's do")
    void controlsGrow() {
        EventScreenLayout ordinary = EventScreenLayout.of(854, 480, 100, 1f, FONT_LINE);
        EventScreenLayout larger = EventScreenLayout.of(854, 480, 150, 1f, FONT_LINE);
        EventScreenLayout largest = EventScreenLayout.of(854, 480, 200, 1f, FONT_LINE);
        assertThat(ordinary.row()).isEqualTo(18);
        assertThat(larger.row()).isEqualTo(27);
        assertThat(largest.row()).isEqualTo(36);
        assertThat(largest.tab(0, 4).height()).isGreaterThan(ordinary.tab(0, 4).height());
    }

    @Test
    @DisplayName("a line and a button are never shorter than their writing, whatever the control size")
    void neverShorterThanTheText() {
        for (float text : TEXT) {
            for (int controls : CONTROLS) {
                EventScreenLayout layout = EventScreenLayout.of(854, 480, controls, text, FONT_LINE);
                int writing = Math.round(FONT_LINE * Math.max(1f, text));
                assertThat(layout.line()).as("line at text %s controls %d", text, controls).isGreaterThan(writing);
                assertThat(layout.row()).as("row at text %s controls %d", text, controls).isGreaterThan(writing);
                assertThat(layout.tab(0, 4).height()).as("tab at text %s controls %d", text, controls).isGreaterThan(writing);
            }
        }
    }

    @Test
    @DisplayName("the title, header, tabs, body and bottom row come in order and stay inside the panel")
    void inOrderInside() {
        for (int height : new int[] {240, 300, 480, 1080}) {
            for (float text : TEXT) {
                for (int controls : CONTROLS) {
                    EventScreenLayout layout = EventScreenLayout.of(854, height, controls, text, FONT_LINE);
                    String at = "text " + text + " controls " + controls + " height " + height;
                    // Enlarged writing is drawn centered on its line, so half of what it gained
                    // sits above where it is placed: the title's top must still be in the panel.
                    float writing = FONT_LINE * Math.max(1f, text);
                    assertThat(layout.titleY() - (writing - FONT_LINE) / 2f).as(at)
                            .isGreaterThanOrEqualTo(layout.panel().y() + 1);
                    assertThat(layout.headerY()).as(at).isGreaterThanOrEqualTo(layout.titleY() + layout.line());
                    assertThat(layout.tab(0, 4).y()).as(at).isGreaterThanOrEqualTo(layout.headerY() + layout.line());
                    assertThat(layout.bodyTop()).as(at).isGreaterThan(layout.tab(0, 4).bottom());
                    assertThat(layout.bottomRow() + layout.row()).as(at).isLessThanOrEqualTo(layout.panel().bottom());
                    assertThat(layout.panel().bottom()).as(at).isLessThanOrEqualTo(height);
                    Rect lastTab = layout.tab(3, 4);
                    assertThat(lastTab.right()).as(at).isLessThanOrEqualTo(layout.panel().right());
                }
            }
        }
    }

    @Test
    @DisplayName("the panel grows with the sizes asked, until the window stops it")
    void panelGrows() {
        EventScreenLayout ordinary = EventScreenLayout.of(854, 480, 100, 1f, FONT_LINE);
        EventScreenLayout larger = EventScreenLayout.of(854, 480, 200, 2f, FONT_LINE);
        assertThat(ordinary.panel().width()).isEqualTo(EventScreenLayout.PANEL_WIDTH);
        assertThat(larger.panel().width()).isGreaterThan(ordinary.panel().width()).isLessThanOrEqualTo(854 - 8);
        assertThat(larger.panel().height()).isGreaterThan(ordinary.panel().height()).isLessThanOrEqualTo(480 - 4);
    }

    @Test
    @DisplayName("buttons share rows evenly, and never overrun the body's width")
    void buttonRows() {
        EventScreenLayout layout = EventScreenLayout.of(854, 480, 200, 2f, FONT_LINE);
        for (int count : new int[] {1, 6, 16, 18}) {
            int perRow = layout.perRow(count, 80);
            int each = (layout.bodyWidth() - layout.gap() * (perRow - 1)) / perRow;
            assertThat(each).as("%d buttons", count).isGreaterThanOrEqualTo(80);
            int rows = layout.rowsFor(count, 80);
            assertThat(rows * perRow).isGreaterThanOrEqualTo(count);
            assertThat((rows - 1) * perRow).isLessThan(count);
        }
    }
}
