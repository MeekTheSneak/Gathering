package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The deck screen, laid out at every window size anybody could have.
 * <p>A GUI is normally only ever checked at the size its author was running, and everything
 * that goes wrong at other sizes - a panel wider than the screen, a button under another
 * button, a card box overlapping the list it is meant to sit beside - goes wrong silently
 * and is found by a player. Doing the arithmetic in the pure module means it can be checked
 * across the whole range instead.
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
    }

    @Property(tries = 2000)
    void theScrollbarSitsOnTheTaperedEdgeAllTheWayDown(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        // The bar is drawn as an upright rectangle under a shear, so what has to stay on the
        // edge is its sheared position, not the rectangle. Anchoring it to the bottom of the
        // taper and shearing it as well tapers it twice and walks it off the panel, which is
        // exactly the bug this pins down.
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);
        Rect bar = layout.scrollbar();

        for (int y = bar.y(); y <= bar.bottom(); y++) {
            int left = Math.round(bar.x() + layout.taperSlope() * y);
            assertThat(left + bar.width()).isLessThanOrEqualTo(layout.edgeAt(y));
            assertThat(left).isGreaterThanOrEqualTo(layout.rows().right());
        }
    }

    @Property(tries = 3000)
    void theListStackNeverOverlapsItself(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        DeckScreenLayout layout = DeckScreenLayout.of(width, height, LINE_HEIGHT);

        assertThat(layout.title().bottom()).isLessThanOrEqualTo(layout.rows().y());
        assertThat(layout.rows().bottom()).isLessThanOrEqualTo(layout.hint().y());
        assertThat(layout.hint().bottom()).isLessThanOrEqualTo(layout.done().y());

        // One hint line per mouse button. Squeezing both onto one line is what made the hint
        // shrink to the smallest readable size and then get cut off on top of that.
        assertThat(layout.hint().height()).isEqualTo(DeckScreenLayout.HINT_LINES * LINE_HEIGHT);
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
        // Wide enough that "Right-click: make commander" shrinks a little rather than being
        // trimmed. This module cannot measure text, so it guards the room instead.
        assertThat(layout.hint().width()).isGreaterThan(85);
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

    /**
     * All five basic lands have a button, inside the panel, at every window size.
     * <p>A drafted pool is forty-five spells, so without these there is no legal deck to
     * build from one at all - and a button drawn off the taper is a land nobody can add.
     */
    @Property(tries = 400)
    void everyBasicLandHasSomewhereToBeClicked(
            @ForAll @IntRange(min = 200, max = 3840) int width,
            @ForAll @IntRange(min = 160, max = 2160) int height,
            @ForAll @IntRange(min = 6, max = 20) int lineHeight) {
        DeckScreenLayout laid = DeckScreenLayout.of(width, height, lineHeight);

        Rect previous = null;
        for (int index = 0; index < DeckScreenLayout.LAND_BUTTONS; index++) {
            Rect button = laid.landButton(index);
            assertThat(button.isEmpty())
                    .describedAs("basic land %s has no button at %sx%s", index, width, height)
                    .isFalse();
            assertThat(button.x()).isGreaterThanOrEqualTo(laid.lands().x());
            assertThat(button.right())
                    .describedAs("basic land %s runs past the strip at %sx%s", index, width, height)
                    .isLessThanOrEqualTo(laid.lands().right());
            if (previous != null) {
                assertThat(button.x())
                        .describedAs("basic land %s overlaps the one before it", index)
                        .isGreaterThanOrEqualTo(previous.right());
            }
            previous = button;
        }
    }

    /** And the strip they sit in does not eat the rows above it. */
    @Property(tries = 400)
    void theLandStripDoesNotOverlapAnythingElse(
            @ForAll @IntRange(min = 200, max = 3840) int width,
            @ForAll @IntRange(min = 160, max = 2160) int height,
            @ForAll @IntRange(min = 6, max = 20) int lineHeight) {
        DeckScreenLayout laid = DeckScreenLayout.of(width, height, lineHeight);

        assertThat(laid.lands().y())
                .describedAs("the land strip is drawn over the card rows at %sx%s", width, height)
                .isGreaterThanOrEqualTo(laid.rows().bottom());
        assertThat(laid.lands().bottom())
                .describedAs("the land strip is drawn over the hint at %sx%s", width, height)
                .isLessThanOrEqualTo(laid.hint().y());
    }

    /**
     * The row that opens the pockets builder sits between the hint and the buttons, or is
     * given up altogether.
     * <p>It is the one row on this panel that may go: everything above it is the deck, and a
     * short window that kept this and squeezed the cards would be trading the screen for a
     * button that has another way in.
     */
    @Property(tries = 2000)
    void theGatherRowGivesWayRatherThanOverlapping(
            @ForAll @IntRange(min = 200, max = 3840) int width,
            @ForAll @IntRange(min = 160, max = 2160) int height,
            @ForAll @IntRange(min = 6, max = 20) int lineHeight) {
        DeckScreenLayout laid = DeckScreenLayout.of(width, height, lineHeight);

        if (laid.gather().isEmpty()) {
            return;
        }
        assertThat(laid.hint().bottom())
                .describedAs("the hint runs into the gather row at %sx%s", width, height)
                .isLessThanOrEqualTo(laid.gather().y());
        assertThat(laid.gather().bottom())
                .describedAs("the gather row runs into the buttons at %sx%s", width, height)
                .isLessThanOrEqualTo(laid.done().y());
        assertThat(laid.gather().right())
                .describedAs("the gather row runs off the taper at %sx%s", width, height)
                .isLessThanOrEqualTo(laid.edgeAt(laid.gather().bottom()));
    }

    /** Asking for a land that does not exist gets nothing rather than a rectangle. */
    @Test
    void thereIsNoSixthBasicLand() {
        DeckScreenLayout laid = DeckScreenLayout.of(854, 480, 9);

        assertThat(laid.landButton(-1).isEmpty()).isTrue();
        assertThat(laid.landButton(DeckScreenLayout.LAND_BUTTONS).isEmpty()).isTrue();
    }
}
