package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The deck screen, laid out at every window size anybody could have.
 *
 * <p>A GUI is normally only ever checked at the size its author was running, and everything
 * that goes wrong at other sizes - a panel wider than the screen, a button under another
 * button, a card box overlapping the list it is meant to sit beside - goes wrong silently
 * and is found by a player. Doing the arithmetic in the pure module means it can be checked
 * across the whole range instead.
 *
 * <p>The range: 320x240 is the smallest GUI-scaled screen Minecraft produces, and a 4K
 * screen at GUI scale 1 is a little under 3840x2160.
 */
class DeckScreenLayoutTest {

    private static final int LINE_HEIGHT = 9;

    @Property(tries = 3000)
    void nothingEverLeavesTheScreen(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);

        for (Rect rect : all(layout)) {
            if (rect.isEmpty()) {
                continue;
            }
            assertThat(rect.x()).isGreaterThanOrEqualTo(0);
            assertThat(rect.y()).isGreaterThanOrEqualTo(0);
            assertThat(rect.right()).isLessThanOrEqualTo(width);
            assertThat(rect.bottom()).isLessThanOrEqualTo(height);
        }
    }

    @Property(tries = 3000)
    void theListPanelIsAlwaysThere(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        // Whatever else has to go, the decklist is the screen.
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);

        assertThat(layout.panel().isEmpty()).isFalse();
        assertThat(layout.rows().isEmpty()).isFalse();
        assertThat(layout.done().isEmpty()).isFalse();
    }

    @Property(tries = 3000)
    void everythingInsideTheListFitsTheNarrowEndOfTheTaper(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        // The panel is narrowest at the bottom, so content sized to the top would run out
        // through the tapered edge somewhere down the screen.
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);
        int narrow = layout.edgeAt(layout.panel().height());

        for (Rect rect : new Rect[] {layout.title(), layout.rows(), layout.hint(), layout.done()}) {
            assertThat(rect.right()).isLessThanOrEqualTo(narrow);
        }
        assertThat(layout.scrollbar().right()).isLessThanOrEqualTo(narrow);
    }

    @Property(tries = 3000)
    void theListStackNeverOverlapsItself(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);

        assertThat(layout.title().bottom()).isLessThanOrEqualTo(layout.rows().y());
        assertThat(layout.rows().bottom()).isLessThanOrEqualTo(layout.hint().y());
        assertThat(layout.hint().bottom()).isLessThanOrEqualTo(layout.done().y());
    }

    @Property(tries = 3000)
    void theCardAndItsTextNeverCoverTheList(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);

        assertThat(layout.card().overlaps(layout.panel())).isFalse();
        assertThat(layout.info().overlaps(layout.panel())).isFalse();
        assertThat(layout.card().overlaps(layout.info())).isFalse();
    }

    @Property(tries = 3000)
    void thereIsNeverTextWithoutTheCardItDescribes(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);

        if (!layout.info().isEmpty()) {
            assertThat(layout.card().isEmpty()).isFalse();
            assertThat(layout.info().x()).isGreaterThanOrEqualTo(layout.card().right());
        }
    }

    @Property(tries = 3000)
    void theCardKeepsItsPrintedProportions(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        // A card box that is not card-shaped either stretches the art or wastes the space
        // around it, and both look like a bug rather than a layout choice.
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);
        Rect card = layout.card();
        if (card.isEmpty()) {
            return;
        }

        int artWidth = card.width() - DeckScreenLayout.FRAME * 2;
        int artHeight = card.height() - DeckScreenLayout.FRAME * 2;
        assertThat(artWidth).isPositive();
        assertThat(artHeight).isPositive();
        assertThat((double) artWidth / artHeight).isCloseTo(488d / 680d, org.assertj.core.data.Offset.offset(0.03));
    }

    @Test
    @DisplayName("the smallest window Minecraft allows still shows a list and a card")
    void theSmallestWindowStillWorks() {
        DeckScreenLayout layout = DeckScreenLayout.of(320, 240, LINE_HEIGHT);

        assertThat(layout.rows().width()).isGreaterThan(60);
        assertThat(layout.rows().height()).isGreaterThan(LINE_HEIGHT * 4);
        assertThat(layout.card().isEmpty()).isFalse();
    }

    @Test
    @DisplayName("a wide window shows the card and its text side by side")
    void aWideWindowShowsEverything() {
        DeckScreenLayout layout = DeckScreenLayout.of(854, 480, LINE_HEIGHT);

        assertThat(layout.card().isEmpty()).isFalse();
        assertThat(layout.info().isEmpty()).isFalse();
    }

    private static Rect[] all(DeckScreenLayout layout) {
        return new Rect[] {
            layout.panel(), layout.title(), layout.rows(), layout.scrollbar(),
            layout.hint(), layout.done(), layout.card(), layout.info(),
        };
    }
}
