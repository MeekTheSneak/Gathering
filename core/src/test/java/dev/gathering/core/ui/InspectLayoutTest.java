package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The full-window read, as arithmetic.
 *
 * <p>Drawn over a running game at whatever size the player's window happens to be, which is
 * the case a screenshot never covers: everything looks right at the one size somebody
 * photographed it at, and the text column is off the edge at 4:3.
 */
class InspectLayoutTest {

    @Test
    @DisplayName("the card is card-shaped and the words are beside it")
    void theOrdinaryCase() {
        InspectLayout layout = InspectLayout.of(854, 480);

        assertThat(layout.card().height()).isGreaterThan(layout.card().width());
        assertThat(layout.text().x()).isGreaterThanOrEqualTo(layout.card().right());
        assertThat(layout.text().width()).isGreaterThanOrEqualTo(InspectLayout.NARROWEST_TEXT);
    }

    @Property
    @Label("neither box ever leaves the window or lands on the other")
    void nothingLeavesTheWindow(
            @ForAll @IntRange(min = 200, max = 3840) int width,
            @ForAll @IntRange(min = 120, max = 2160) int height) {
        InspectLayout layout = InspectLayout.of(width, height);

        assertThat(layout.card().x()).isGreaterThanOrEqualTo(0);
        assertThat(layout.card().y()).isGreaterThanOrEqualTo(0);
        assertThat(layout.card().right()).isLessThanOrEqualTo(width);
        assertThat(layout.card().bottom()).isLessThanOrEqualTo(height);
        assertThat(layout.text().right()).isLessThanOrEqualTo(width);
        assertThat(layout.text().bottom()).isLessThanOrEqualTo(height);
        assertThat(layout.card().overlaps(layout.text()))
                .describedAs("the words were drawn over the card")
                .isFalse();
    }

    @Test
    @DisplayName("a window with no room gives what there is to the card")
    void averyNarrowWindow() {
        InspectLayout layout = InspectLayout.of(220, 300);

        assertThat(layout.card().width()).isGreaterThan(0);
        assertThat(layout.card().right()).isLessThanOrEqualTo(220);
    }

    @Test
    @DisplayName("nothing at all is drawn for a window of no size")
    void noWindow() {
        assertThat(InspectLayout.of(0, 0).card()).isEqualTo(Rect.NONE);
        assertThat(InspectLayout.of(-4, 900).text()).isEqualTo(Rect.NONE);
    }
}
